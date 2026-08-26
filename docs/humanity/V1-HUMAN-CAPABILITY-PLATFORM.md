# 🏛️ Human Capability Platform (Humanity OS Foundation)

---

## 1. Doctrinal Foundation

> **"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."**

La **Human Capability Platform** es una capacidad transversal de MEET que convierte el conocimiento técnico automotriz y clínico en aprendizaje estructurado, simulación determinista, práctica segura, evidencia criptográfica inmutable y oportunidades reales de trabajo.

```
                    MEET / ELYSIUM VANGUARD
                              │
         ┌────────────────────┴────────────────────┐
         │                                         │
    VEHICLE TRUTH                           HUMAN CAPABILITY
         │                                         │
         ↓                                         ↓
    Observation                               Knowledge Nodes & Signed Packs
         ↓                                         ↓
    Diagnosis                                  Learning Runtime (SM-2)
         ↓                                         ↓
    Repair                                    Simulation (Multimeter Lab)
         ↓                                         ↓
    Verification                               Practice (Mission Player)
         ↓                                         ↓
    Evidence                                   Evidence Ledger (SHA-256)
         └─────────────────┬───────────────────────┘
                           ↓
                   CAPABILITY PASSPORT
                           ↓
                Repair Network / Marketplace
                           ↓
             [ 🎯 Prepararme para este trabajo ]
                           ↓
                   Real Work Opportunities
                           ↓
                 Knowledge Capture Engine
                           ↓
               Verified Diagnostic Knowledge
```

---

## 2. Modelos Canónicos de Dominio

- **`TruthState`**: Jerarquía epistemológica (`AUTHORITATIVE`, `OBSERVED`, `PEER_REVIEWED`, `DERIVED`, `EXPERT_CONSENSUS`, `ESTIMATED`, `ANECDOTAL`, `DISPUTED`, `HYPOTHESIS`, `UNKNOWN`).
- **`ExecutionTruthState`**: Separación de contextos (`NOT_EXECUTED`, `SIMULATED`, `GUIDED`, `OBSERVED`, `PHYSICALLY_VERIFIED`). Las actividades virtuales jamás contaminan el historial físico del vehículo.
- **`SafetyLevel`**: Clasificación determinista (`KNOWLEDGE_ONLY`, `SIMULATION_SAFE`, `LOW_RISK_PRACTICE`, `SUPERVISED_REQUIRED`, `LICENSE_REQUIRED`, `PROHIBITED_UNSUPERVISED`).
- **`CapabilityLevel`**: Escala de maestría demostrable de 9 niveles (`L0_UNKNOWN` a `L8_TEACHER`).
- **`EvidenceItem` & `CapabilityRecord`**: Registro con firmas y hashes SHA-256 inmutables.

---

## 3. Componentes Principales

1. **Learning Runtime Engine (`LearningRuntimeEngine.kt`)**: Scheduler con algoritmo de repetición espaciada SM-2 adaptativo. Preserva el Teórico Oficial de Manejo 2026.
2. **Safety Kernel (`SafetyKernel.kt`)**: Veto determinista ineludible por IA que bloquea procedimientos pirotécnicos (airbags) y alto voltaje EV (>60V) no supervisado.
3. **Reproductor de Misiones (`MissionDetailScreen.kt`)**: Misiones clínicas paso a paso con seguimiento visual.
4. **Multimeter Lab (`MultimeterSimulationScreen.kt`)**: Simulador interactivo con escalas 20V/200V/Ohm/Continuidad, sondas dinámicas y cálculo de caída de tensión.
5. **Pasaporte de Capacidades (`CapabilityPassportScreen.kt`)**: Credenciales técnicas con ID de pasaporte, historial de evidencias SHA-256 y aval de peritos.
6. **Puertas en Red de Reparación (`RepairNetworkScreen.kt`)**: Requisitos de nivel de habilidad en servicios y botón directo `[ PREPARARME ]`.
7. **Motor de Captura de Conocimiento (`KnowledgeCaptureEngine.kt`)**: Transformación de casos reales en nodos de conocimiento con revisión obligatoria por perito humano (`PENDING_EXPERT_REVIEW`).
8. **Paquetes Offline Firmados (`KnowledgePackManager.kt`)**: Formato de distribución `.pack` con firma digital de raíz y comprobación de integridad SHA-256.
9. **Guardián de Verificación Óptica (`VisualComponentVerifier.kt`)**: Fuerza `IDENTIFICATION_NOT_VERIFIED` ante IA sin cotejo con VIN/catálogo OEM.

---

## 4. Paridad Cross-Runtime (TypeScript ≡ Kotlin)

Los contratos de serialización e integridad son idénticos byte a byte:
- TypeScript: `lib/humanity/types.ts` & `lib/humanity/hash.ts`
- Kotlin: `HumanityDomainModels.kt` & `HumanityParityEngine.kt`
- Verificación: `bash tests/parity/ci-verify.sh`
