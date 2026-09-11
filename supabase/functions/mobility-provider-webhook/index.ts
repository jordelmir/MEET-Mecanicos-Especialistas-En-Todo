import { handleWebhook } from "./handler.ts";

Deno.serve(handleWebhook);
