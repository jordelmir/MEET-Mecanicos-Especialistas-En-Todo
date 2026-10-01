# EVAIR LIVING COMPANION INTEGRATION SPECIFICATION

**Status:** IMPLEMENTED — PHYSICAL AND COMPLETE UI COVERAGE VERIFICATION PENDING
**Architecture Layer:** Android Client (Jetpack Compose) + Coroutine Voice Bus
**Reference Implementations:**
- `ElysiumLivingCompanionOverlay.kt`
- `EvairInteractionOrchestrator.kt`
- `CompanionMotionPort.kt`
- `VoiceInteractionBus.kt`
- `VoiceFormBinder.kt`

---

## 1. Architectural Overview

EVAIR is the unified living companion of Elysium Vanguard. Rather than a static chatbot, EVAIR maintains persistent visual and spatial presence across all application flows.

```
       [ Human Voice / Audio ]
                  │
                  ▼
       [ VoiceCommandManager ]  (Single OS Microphone Channel)
                  │
                  ▼ (TranscriptStream)
       [ VoiceInteractionBus.default ]
                  │
                  ├──► [ EvairInteractionOrchestrator ]
                  │          │
                  │          ├──► Deterministic Fast-Path: "SECCIÓN" / "SELECCIONA"
                  │          │          │
                  │          │          ├──► UiTargetResolver
                  │          │          └──► CompanionMotionPort (Teleport & Particle Burst)
                  │          │
                  │          └──► Dynamic Laya Decision Engine (Complex Intent & Form Binding)
                  │                     │
                  │                     └──► VoiceFormBinder (Direct Semantic Field Input)
                  │
                  └──► [ UI State & Companion Overlay ] (Zero Dual-Mic Leaks)
```

---

## 2. Companion Motion & Instant Shift Protocol

When the user gives a directional command ("SECCIÓN <nombre>" or "SELECCIONA <nombre>"):

1. **Target Identification:** `UiTargetResolver` locates the physical screen coordinates (`centerX`, `centerY`, `bounds`) of the target element.
2. **Phase 1: Quantum Collapse (0–120ms):**
   - Scale compresses from `1.0f` to `0.18f` (`FastOutSlowInEasing`).
   - Alpha transitions to hyper-dense luminance (`0.95f`).
   - `EvairQuantumBurstFx` emits 24 particle trails radiating outward.
3. **Phase 2: Instant Shift Teleport (120–220ms):**
   - Avatar coordinates glide instantly to `(targetCenterX, targetCenterY)`.
   - `EvairTargetHighlightFx` pulses a cyan/magenta beacon over the button bounding box.
4. **Phase 3: Activation & Return (220–400ms):**
   - The button's registered callback `activate()` is executed on the main UI thread.
   - Avatar smoothly glides back to its default rest anchor (bottom-right floating HUD).
   - Scale recovers to `1.0f` with an elastic spring animation.

---

## 3. Single-Microphone Operating Charter

- **Violation Prevented:** `SpeechRecognizer.ERROR_RECOGNIZER_BUSY` (Error code 8).
- **Rule:** Under no circumstance may any overlay, widget, or background service instantiate a secondary `SpeechRecognizer`.
- **Implementation:** The companion overlay registers as a purely passive listener on `VoiceInteractionBus.default.transcripts`. All speech recognition is owned exclusively by `VoiceCommandManager`.

---

## 4. Voice Form Binding & Secret Shielding

1. When dictating addresses into `RideServiceScreen` or search bars, `VoiceFormBinder` maps transcripts directly into the corresponding text fields via `writeText`.
2. **Secret Shielding:** If a field has `AgentUiSensitivity.SECRET` or `role = AgentTextFieldRole.SECRET` (such as passenger boarding PINs or payment passwords), `VoiceFormBinder` immediately rejects the operation with `VoiceBindingResult.DeniedSecretField`. The automated agent cannot inspect or inject text into shielded fields.
