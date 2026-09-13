// Supabase Edge Function: sinpe-email-webhook
// Ingests bank transfer notification emails sent to jordelmir@gmail.com
// (BAC Credomatic, BNCR, BCR, etc.) to automatically credit driver wallets.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const WEBHOOK_SECRET = Deno.env.get("SINPE_WEBHOOK_SECRET") ?? "";

interface InboundEmailPayload {
  from?: string;
  to?: string;
  subject?: string;
  text?: string;
  html?: string;
  secret?: string;
}

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    const body: InboundEmailPayload = await req.json();

    // Check optional security token if configured
    if (WEBHOOK_SECRET && body.secret !== WEBHOOK_SECRET) {
      const authHeader = req.headers.get("Authorization");
      if (authHeader !== `Bearer ${WEBHOOK_SECRET}`) {
        return new Response(JSON.stringify({ error: "Unauthorized" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      }
    }

    const fullContent = `${body.subject ?? ""} \n ${body.text ?? ""} \n ${body.html ?? ""}`;

    // Parsing logic for Costa Rican banks
    const parsed = parseSinpeEmail(fullContent);
    if (!parsed) {
      return new Response(
        JSON.stringify({ success: false, reason: "No SINPE transfer pattern recognized" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    const { data, error } = await supabase.rpc("sinpe_ingest_email_receipt_v1", {
      p_bank_name: parsed.bank,
      p_reference_number: parsed.referenceNumber,
      p_amount_crc: parsed.amountCrc,
      p_sender_phone: parsed.senderPhone,
      p_sender_name: parsed.senderName,
      p_recipient_email: "jordelmir@gmail.com",
      p_raw_content: fullContent.substring(0, 2000),
    });

    if (error) {
      return new Response(JSON.stringify({ success: false, error: error.message }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ success: true, result: data }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ success: false, error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});

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

  if (ref && amount > 0) {
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
