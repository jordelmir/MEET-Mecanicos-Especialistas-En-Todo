// Only a configured, authenticated mail adapter may submit bank receipts.
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.39.0";
import { createSinpeWebhookHandler } from "./handler.ts";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

serve(createSinpeWebhookHandler({
  secret: Deno.env.get("SINPE_WEBHOOK_SECRET") ?? "",
  recipientEmail: Deno.env.get("SINPE_RECIPIENT_EMAIL") ?? "",
  configured: Boolean(url && serviceKey),
}, async (parameters) => createClient(url, serviceKey).rpc("sinpe_ingest_email_receipt_v1", parameters)));
