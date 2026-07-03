// ai-copilot — Edge Function proxy hacia Mavis API.
//
// Por que existe:
// - La APK NO debe contener la API key (los APK son decompilables).
// - Esta function vive server-side en Supabase. La key queda en MARVIRUS_API_KEY.
// - La APK llama aca con el JWT del user. La function valida, forward, devuelve.
//
// Endpoint expuesto:
//   POST {SUPABASE_URL}/functions/v1/ai-copilot
//   Body: OpenAI-compatible { messages, model?, temperature?, max_tokens? }
//
// Setup:
//   supabase secrets set MARVIRUS_API_KEY=sk-cp-...
//   supabase secrets set MARVIRUS_BASE_URL=https://api.mavis.example/v1
//   supabase functions deploy ai-copilot
//
// En la APK, configurar:
//   provider:    "mavis"
//   endpoint:    {SUPABASE_URL}/functions/v1/ai-copilot
//   apiKey:      (cualquier placeholder, se ignora)
//   model:       (default gpt-4o-mini)

interface ChatMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

interface ChatRequest {
  messages: ChatMessage[];
  model?: string;
  temperature?: number;
  max_tokens?: number;
  stream?: boolean;
  // Passthrough para contexto del vehículo que la APK ya arma en su system prompt.
  vehicleContext?: string;
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function env(name: string, fallback?: string): string {
  const value = Deno.env.get(name) ?? fallback;
  if (value === undefined) {
    throw new Error(`Missing required env: ${name}`);
  }
  return value;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function errorResponse(message: string, status = 400, code = "bad_request"): Response {
  return jsonResponse(
    { error: { code, message } },
    status,
  );
}

async function callMavis(
  apiKey: string,
  baseUrl: string,
  body: ChatRequest,
): Promise<Response> {
  const url = `${baseUrl.replace(/\/$/, "")}/chat/completions`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model: body.model ?? "gpt-4o-mini",
      messages: body.messages,
      temperature: body.temperature ?? 0.35,
      max_tokens: body.max_tokens ?? 4096,
      stream: false,
    }),
  });
  return response;
}

Deno.serve(async (req: Request) => {
  // Preflight CORS
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return errorResponse("Method not allowed. Use POST.", 405, "method_not_allowed");
  }

  // 1) Auth: validar JWT de Supabase.
  //    La APK manda Authorization: Bearer <jwt>.
  //    Para service-to-service, Supabase manda apikey header.
  const authHeader = req.headers.get("Authorization");
  const apikeyHeader = req.headers.get("apikey");
  if (!authHeader && !apikeyHeader) {
    return errorResponse(
      "Missing Authorization or apikey header.",
      401,
      "unauthorized",
    );
  }

  // 2) Parsear body.
  let body: ChatRequest;
  try {
    body = await req.json();
  } catch (_e) {
    return errorResponse("Invalid JSON body.", 400, "invalid_json");
  }

  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    return errorResponse("Body must include non-empty 'messages' array.", 400);
  }

  // 3) Si el cliente mando vehicleContext, lo agregamos como system message
  //    (util cuando la APK no construye el system prompt en el cliente).
  if (body.vehicleContext && body.vehicleContext.trim().length > 0) {
    const hasSystem = body.messages.some((m) => m.role === "system");
    if (!hasSystem) {
      body.messages = [
        { role: "system", content: body.vehicleContext },
        ...body.messages,
      ];
    }
  }

  // 4) Sanity: limitar max_tokens para evitar abuse.
  if (body.max_tokens && body.max_tokens > 8192) {
    body.max_tokens = 8192;
  }

  // 5) Llamar a Mavis con la key del env.
  const apiKey = env("MARVIRUS_API_KEY");
  const baseUrl = env("MARVIRUS_BASE_URL", "https://api.mavis.example/v1");

  try {
    const upstream = await callMavis(apiKey, baseUrl, body);
    const status = upstream.status;
    const text = await upstream.text();

    // Si el upstream fallo, propagamos el body para debugging.
    if (status >= 400) {
      return new Response(text, {
        status,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // 6) Si el cliente espera stream, devolvemos el body tal cual.
    //    Si no, parseamos y devolvemos solo el content para que la APK
    //    no tenga que conocer el formato upstream.
    let responseBody: unknown = text;
    try {
      const parsed = JSON.parse(text);
      if (parsed?.choices?.[0]?.message?.content) {
        responseBody = {
          id: parsed.id,
          model: parsed.model,
          content: parsed.choices[0].message.content,
          finish_reason: parsed.choices[0].finish_reason,
          usage: parsed.usage,
        };
      }
    } catch (_e) {
      // No es JSON, devolver el texto crudo.
    }

    return new Response(JSON.stringify(responseBody), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    return errorResponse(`Upstream call failed: ${message}`, 502, "upstream_error");
  }
});
