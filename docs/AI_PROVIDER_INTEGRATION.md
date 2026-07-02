# MEET AI Provider Integration

MEET Android now supports real user-configured AI providers from inside the APK. The app no longer depends on a hardcoded local placeholder when a provider is configured.

## Supported Providers

- `gemini`: Google Gemini native `generateContent` format.
- `openai`: OpenAI Chat Completions compatible format.
- `anthropic`: Anthropic Messages API format.
- `ollama`: Local OpenAI-compatible endpoint, default `http://localhost:11434/v1/chat/completions`.
- `mavis`: configurable OpenAI-compatible endpoint for a Mavis-style vendor/API gateway.
- `custom`: any provider exposing an OpenAI-compatible chat endpoint.

## User Flow

1. Open the APK.
2. Go to `Ajustes > Motor de Inteligencia Artificial`.
3. Select provider.
4. Paste the user-owned API key.
5. Set endpoint URL for non-Gemini providers.
6. Set model name if the provider requires one.
7. Save.

The DTC AI flow, oscilloscope analysis, network topology analysis and active-test analysis read this configuration immediately.

## Safety Rules

- Real API keys are never committed.
- `.env`, `.env.local`, `android/local.properties`, Vercel metadata, keystores and Supabase service-role secrets are ignored by git.
- If a remote provider is configured, DTC AI bypasses stale local cache so the user receives a fresh API result.
- If no remote provider is configured or the call fails, the app falls back to the local expert engine and labels that path clearly.

## Provider Contract

For `mavis` and `custom`, MEET expects a POST endpoint compatible with:

```json
{
  "model": "provider-model-name",
  "messages": [
    { "role": "user", "content": "diagnostic prompt" }
  ],
  "max_tokens": 4096,
  "temperature": 0.35
}
```

The response should expose `choices[0].message.content`. Anthropic uses `content[0].text`; Gemini uses `candidates[0].content.parts[0].text`.
