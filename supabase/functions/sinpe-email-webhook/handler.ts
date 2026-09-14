interface InboundEmailPayload {
  from?: string;
  to?: string;
  subject?: string;
  text?: string;
  html?: string;
  secret?: string;
}

interface WebhookConfig {
  secret: string;
  recipientEmail: string;
  configured: boolean;
}

type Ingest = (parameters: Record<string, unknown>) => Promise<{ data: unknown; error: unknown }>;

const json = (body: unknown, status: number) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json" },
});

/** Authenticate the trusted mail adapter before invoking privileged receipt ingestion. */
export function createSinpeWebhookHandler(config: WebhookConfig, ingest: Ingest) {
  return async (req: Request): Promise<Response> => {
    if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);
    if (!config.configured || !config.secret.trim() || !config.recipientEmail.trim()) {
      return json({ error: "WEBHOOK_NOT_CONFIGURED" }, 503);
    }
    let body: InboundEmailPayload;
    try {
      const raw = await req.text();
      if (raw.length > 65536) return json({ error: "PAYLOAD_TOO_LARGE" }, 413);
      const value: unknown = JSON.parse(raw);
      if (!value || typeof value !== "object" || Array.isArray(value)) {
        return json({ error: "INVALID_PAYLOAD" }, 400);
      }
      body = value as InboundEmailPayload;
      for (const key of ["from", "to", "subject", "text", "html", "secret"] as const) {
        if (body[key] !== undefined && typeof body[key] !== "string") {
          return json({ error: "INVALID_PAYLOAD" }, 400);
        }
      }
    } catch {
      return json({ error: "INVALID_JSON" }, 400);
    }
    const token = req.headers.get("Authorization")?.replace(/^Bearer /, "") ?? body.secret ?? "";
    const encoder = new TextEncoder();
    const [expected, provided] = await Promise.all([
      crypto.subtle.digest("SHA-256", encoder.encode(config.secret)),
      crypto.subtle.digest("SHA-256", encoder.encode(token)),
    ]);
    const a = new Uint8Array(expected);
    const b = new Uint8Array(provided);
    let difference = 0;
    for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
    if (difference !== 0) return json({ error: "UNAUTHORIZED" }, 401);

    const fullContent = `${body.subject ?? ""} \n ${body.text ?? ""} \n ${body.html ?? ""}`;
    const parsed = parseSinpeEmail(fullContent);
    if (!parsed) return json({ success: false, error: "UNRECOGNIZED_RECEIPT" }, 400);
    try {
      const { data, error } = await ingest({
        p_bank_name: parsed.bank,
        p_reference_number: parsed.referenceNumber,
        p_amount_crc: parsed.amountCrc,
        p_sender_phone: parsed.senderPhone,
        p_sender_name: parsed.senderName,
        p_recipient_email: config.recipientEmail,
        p_raw_content: fullContent.substring(0, 2000),
      });
      if (error || !data || typeof data !== "object" || !("success" in data) || data.success !== true) {
        return json({ success: false, error: "RECEIPT_INGESTION_FAILED" }, 502);
      }
      return json({ success: true, result: data }, 200);
    } catch {
      return json({ success: false, error: "RECEIPT_INGESTION_FAILED" }, 502);
    }
  };
}

function parseSinpeEmail(content: string) {
  const lower = content.toLowerCase();

  let bank = "GENERIC";
  if (lower.includes("bac") || lower.includes("credomatic")) bank = "BAC";
  else if (lower.includes("nacional") || lower.includes("bncr")) bank = "BNCR";
  else if (lower.includes("bcr") || lower.includes("costa rica")) bank = "BCR";
  else if (lower.includes("promerica")) bank = "PROMERICA";

  // Reference extraction
  const refMatch = content.match(/(?:referencia|comprobante|número|num|ref)\s*[:#]?\s*([0-9A-Za-z]{6,32})/i);
  let ref = refMatch ? refMatch[1].trim() : null;
  if (!ref) {
    const digitMatch = content.match(/\b([0-9]{8,24})\b/);
    if (digitMatch && !digitMatch[1].startsWith("506")) {
      ref = digitMatch[1];
    }
  }

  // Amount extraction
  const amountMatch = content.match(/(?:monto|total|transferido|por)\s*[:#]?\s*(?:₡|CRC)?\s*([0-9.,]+)/i);
  let amount = 0;
  if (amountMatch) {
    const raw = amountMatch[1].replace(/,/g, "");
    amount = parseFloat(raw);
  }

  // Sender extraction
  const senderMatch = content.match(/(?:origen|de|remitente|teléfono|desde|cliente)\s*[:#]?\s*([0-9\- ]{8,12}|[A-Za-zÀ-ÿ ]{3,35})/i);
  const sender = senderMatch ? senderMatch[1].trim() : null;

  if (ref && Number.isFinite(amount) && amount > 0) {
    return {
      bank,
      referenceNumber: ref.toUpperCase(),
      amountCrc: amount,
      senderPhone: sender && /^[0-9\- ]+$/.test(sender) ? sender : null,
      senderName: sender && !/^[0-9\- ]+$/.test(sender) ? sender : null,
    };
  }

  return null;
}
