# AI Copilot — Backend proxy

## Por que

El APK NO contiene la API key de Mavis. Los APK son decompilables; cualquier key
embebida queda expuesta en Google Play, en cada telefono instalado, en backups
y en cualquier APK reempaquetado. La key vive solo server-side en Supabase
Secrets.

## Arquitectura

```
APK (Android)                     Supabase (Deno)                Mavis API
   │                                    │                             │
   │  POST /functions/v1/ai-copilot     │                             │
   │  Authorization: Bearer <jwt>       │                             │
   │  { messages, model, ... }          │                             │
   │ ─────────────────────────────────▶ │                             │
   │                                    │  POST /chat/completions     │
   │                                    │  Authorization: Bearer sk-… │
   │                                    │  { messages, model, ... }   │
   │                                    │ ──────────────────────────▶ │
   │                                    │ ◀────────────────────────── │
   │ ◀───────────────────────────────── │                             │
   { content, usage, ... }              │                             │
```

## Setup (one-time)

```bash
# 1. Setear la key (NO la pegues en el chat, hace esto en tu terminal local)
supabase secrets set MARVIRUS_API_KEY=sk-cp-...
supabase secrets set MARVIRUS_BASE_URL=https://api.mavis.example/v1

# 2. Deployar la function
supabase functions deploy ai-copilot

# 3. Verificar que responde
curl -X POST \
  "https://<PROJECT>.supabase.co/functions/v1/ai-copilot" \
  -H "Authorization: Bearer <USER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hola"}]}'
```

## Configuracion en la APK

En la pantalla de configuracion del AI Copilot, setear:

- **Provider**: `mavis` (ya esta en el enum de `GeminiDiagnostic.kt`)
- **Endpoint URL**: `https://<PROJECT>.supabase.co/functions/v1/ai-copilot`
- **API Key**: cualquier placeholder (ej. `placeholder`) — la APK la require
  para pasar la guarda de "tiene key", pero el edge function la ignora y
  usa la del env
- **Model**: vacio o `gpt-4o-mini`

## API reference

### Request

```http
POST /functions/v1/ai-copilot
Authorization: Bearer <jwt>
Content-Type: application/json
```

```json
{
  "messages": [
    {"role": "system", "content": "Eres un mecanico experto..."},
    {"role": "user", "content": "Analizar el sensor ECT..."}
  ],
  "model": "gpt-4o-mini",
  "temperature": 0.35,
  "max_tokens": 4096,
  "vehicleContext": "Hyundai Accent 2005 1.6 AT"
}
```

`vehicleContext` es opcional. Si la primera message no es `system` y viene
`vehicleContext`, se antepone como system message.

### Response (success)

```json
{
  "id": "chatcmpl-...",
  "model": "gpt-4o-mini",
  "content": "Para validar el termostato...",
  "finish_reason": "stop",
  "usage": {"prompt_tokens": 42, "completion_tokens": 380, "total_tokens": 422}
}
```

La APK espera el campo `content` (no `choices[0].message.content`).
El edge function aplana el formato upstream para que la APK no tenga
que conocer el formato interno de Mavis.

### Errors

| Status | code | descripcion |
|--------|------|-------------|
| 400 | bad_request | body invalido o messages vacio |
| 401 | unauthorized | falta Authorization o apikey |
| 405 | method_not_allowed | solo POST |
| 502 | upstream_error | Mavis API fallo (status, network, timeout) |

## Rotacion de key

```bash
supabase secrets unset MARVIRUS_API_KEY
supabase secrets set MARVIRUS_API_KEY=sk-cp-NEW_KEY
```

No requiere re-deploy. Las Edge Functions releen los secrets en cada
invocacion. La APK no se entera.

## Local dev

```bash
supabase functions serve ai-copilot \
  --env-file ./supabase/functions/.env.local
```

Con `.env.local`:
```
MARVIRUS_API_KEY=sk-cp-...
MARVIRUS_BASE_URL=https://api.mavis.example/v1
```

## Tests

Ver `supabase/functions/ai-copilot/_tests/` (TODO).

## Por que este patron

- **Key rotable** sin tocar la APK
- **Rate limit server-side** (agregar middleware de Supabase)
- **Audit log** (agregar tabla `ai_copilot_logs`)
- **A/B testing** de modelos sin rebuild
- **Cost control** (limites de tokens centralizados)
- **User attribution** (cada request lleva JWT del user)
