import { createClient } from "npm:@supabase/supabase-js@2";

type TriageRequest = {
  narrative?: string;
  consent?: boolean;
  jurisdiction?: string;
};
type Category = { code: string; taxonomy_version_id: string };
type ModelResult = {
  primaryCategoryCode: string;
  alternativeCategoryCodes: string[];
  confidence: number;
  urgency: "NORMAL" | "HUMAN_REVIEW" | "TIME_CRITICAL";
  followUpQuestions: string[];
  riskFlags: string[];
  rationaleCode: string;
};

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function response(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

function env(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`MISSING_${name}`);
  return value;
}

function firstEnv(...names: string[]): string {
  for (const name of names) {
    const value = Deno.env.get(name)?.trim();
    if (value) return value;
  }
  throw new Error(`MISSING_${names.join("_OR_")}`);
}

function minimizeNarrative(value: string): string {
  return value
    .normalize("NFKC")
    .slice(0, 4_000)
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[EMAIL_REDACTED]")
    .replace(/\b(?:\+?506[- ]?)?[2678]\d{3}[- ]?\d{4}\b/g, "[PHONE_REDACTED]")
    .replace(/\b[A-HJ-NPR-Z0-9]{17}\b/gi, "[VIN_REDACTED]")
    .replace(/\b\d{1,2}-\d{3,4}-\d{3,4}\b/g, "[ID_REDACTED]")
    .trim();
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function parseModelResult(
  raw: unknown,
  allowed: Set<string>,
): ModelResult | null {
  if (!raw || typeof raw !== "object") return null;
  const value = raw as Record<string, unknown>;
  const primary =
    typeof value.primaryCategoryCode === "string"
      ? value.primaryCategoryCode
      : "";
  const alternatives = Array.isArray(value.alternativeCategoryCodes)
    ? value.alternativeCategoryCodes.filter(
        (item): item is string => typeof item === "string",
      )
    : [];
  const confidence =
    typeof value.confidence === "number" ? value.confidence : Number.NaN;
  const urgency = value.urgency;
  if (
    !allowed.has(primary) ||
    alternatives.some((code) => !allowed.has(code)) ||
    !Number.isFinite(confidence) ||
    confidence < 0 ||
    confidence > 1 ||
    !["NORMAL", "HUMAN_REVIEW", "TIME_CRITICAL"].includes(String(urgency))
  )
    return null;
  return {
    primaryCategoryCode: primary,
    alternativeCategoryCodes: [
      ...new Set(alternatives.filter((code) => code !== primary)),
    ].slice(0, 3),
    confidence,
    urgency: urgency as ModelResult["urgency"],
    followUpQuestions: Array.isArray(value.followUpQuestions)
      ? value.followUpQuestions
          .filter((item): item is string => typeof item === "string")
          .slice(0, 5)
      : [],
    riskFlags: Array.isArray(value.riskFlags)
      ? value.riskFlags
          .filter(
            (item): item is string =>
              typeof item === "string" && /^[A-Z0-9_]{2,60}$/.test(item),
          )
          .slice(0, 8)
      : [],
    rationaleCode:
      typeof value.rationaleCode === "string" &&
      /^[A-Z0-9_]{2,60}$/.test(value.rationaleCode)
        ? value.rationaleCode
        : "MODEL_CLASSIFICATION",
  };
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS")
    return new Response("ok", { headers: cors });
  if (request.method !== "POST")
    return response(405, { error: "METHOD_NOT_ALLOWED" });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer "))
    return response(401, { error: "AUTHENTICATION_REQUIRED" });

  let input: TriageRequest;
  try {
    input = await request.json();
  } catch {
    return response(400, { error: "INVALID_JSON" });
  }
  const narrative =
    typeof input.narrative === "string"
      ? minimizeNarrative(input.narrative)
      : "";
  if (input.consent !== true)
    return response(409, { error: "LEGAL_AI_CONSENT_REQUIRED" });
  if (input.jurisdiction !== "CR" || narrative.length < 12)
    return response(400, { error: "INVALID_TRIAGE_INPUT" });

  try {
    const supabaseUrl = env("SUPABASE_URL");
    const userClient = createClient(supabaseUrl, env("SUPABASE_ANON_KEY"), {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const admin = createClient(supabaseUrl, env("SUPABASE_SERVICE_ROLE_KEY"), {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: authData } = await userClient.auth.getUser();
    if (!authData.user)
      return response(401, { error: "AUTHENTICATION_REQUIRED" });

    const { data: taxonomy } = await admin
      .from("market_taxonomy_versions")
      .select("taxonomy_version_id,version")
      .eq("vertical", "LEGAL")
      .eq("jurisdiction", "CR")
      .not("published_at", "is", null)
      .order("version", { ascending: false })
      .limit(1)
      .single();
    if (!taxonomy)
      return response(503, { error: "LEGAL_TAXONOMY_UNAVAILABLE" });
    const { data: categories } = await admin
      .from("market_service_categories")
      .select("code,taxonomy_version_id")
      .eq("taxonomy_version_id", taxonomy.taxonomy_version_id)
      .eq("active", true);
    const codes = new Set(
      (categories as Category[] | null)?.map((item) => item.code) ?? [],
    );
    if (codes.size === 0)
      return response(503, { error: "LEGAL_TAXONOMY_UNAVAILABLE" });

    const modelName = Deno.env.get("LEGAL_AI_MODEL")?.trim() || "MiniMax-M1";
    const modelBaseUrl = firstEnv("LEGAL_AI_BASE_URL", "MARVIRUS_BASE_URL");
    const modelApiKey = firstEnv("LEGAL_AI_API_KEY", "MARVIRUS_API_KEY");
    const modelResponse = await fetch(
      `${modelBaseUrl.replace(/\/$/, "")}/chat/completions`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${modelApiKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: modelName,
          temperature: 0,
          response_format: { type: "json_object" },
          messages: [
            {
              role: "system",
              content: `Classify a Costa Rica legal triage request. Use only these category codes: ${[...codes].join(",")}. Return JSON keys primaryCategoryCode, alternativeCategoryCodes, confidence, urgency, followUpQuestions, riskFlags, rationaleCode. This is triage, never legal advice. Never invent a deadline or outcome.`,
            },
            { role: "user", content: narrative },
          ],
        }),
      },
    );
    if (!modelResponse.ok)
      return response(503, { error: "LEGAL_TRIAGE_MODEL_UNAVAILABLE" });
    const envelope = (await modelResponse.json()) as {
      choices?: Array<{ message?: { content?: string } }>;
    };
    const content = envelope.choices?.[0]?.message?.content;
    const result = parseModelResult(
      content ? JSON.parse(content) : null,
      codes,
    );
    if (!result)
      return response(502, { error: "LEGAL_TRIAGE_INVALID_MODEL_OUTPUT" });

    const consentId = crypto.randomUUID();
    const triageId = crypto.randomUUID();
    const consentInsert = await admin.from("principal_consents").insert({
      consent_id: consentId,
      principal_id: authData.user.id,
      consent_type: "LEGAL_AI_TRIAGE",
      policy_version: "2026-08-28",
      granted: true,
    });
    if (consentInsert.error)
      return response(503, { error: "LEGAL_TRIAGE_PERSISTENCE_FAILED" });
    const stored = await admin
      .from("legal_triage_results")
      .insert({
        triage_id: triageId,
        principal_id: authData.user.id,
        primary_category_code: result.primaryCategoryCode,
        alternative_category_codes: result.alternativeCategoryCodes,
        confidence: result.confidence,
        urgency: result.urgency,
        jurisdiction_hint: "CR",
        follow_up_questions: result.followUpQuestions,
        risk_flags: result.riskFlags,
        rationale_code: result.rationaleCode,
        taxonomy_version_id: taxonomy.taxonomy_version_id,
        model_provider: "OPENAI_COMPATIBLE",
        model_name: modelName,
        model_version: modelName,
        prompt_version: "MEET_LEGAL_TRIAGE_V1",
        state: "AI_SUGGESTED",
        consent_id: consentId,
        narrative_digest: await sha256(narrative),
      })
      .select(
        "triage_id,primary_category_code,alternative_category_codes,confidence,urgency,follow_up_questions,risk_flags,rationale_code,state,created_at",
      )
      .single();
    if (stored.error || !stored.data)
      return response(503, { error: "LEGAL_TRIAGE_PERSISTENCE_FAILED" });
    return response(200, {
      ...stored.data,
      taxonomyVersion: taxonomy.version,
      disclaimer: "AI_SUGGESTED_NOT_LEGAL_ADVICE",
    });
  } catch {
    return response(503, { error: "LEGAL_TRIAGE_UNAVAILABLE" });
  }
});
