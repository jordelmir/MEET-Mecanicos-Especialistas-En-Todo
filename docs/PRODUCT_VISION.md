# MEET — Visión de Producto

**Status:** Active principle (Jor, 2026-07-04)
**One-liner:** *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."*

---

## El principio rector

MEET es **un solo producto**, no un catálogo de features opcionales. Cada
sección existe porque sirve a las demás:

```
Onboarding
   ↓
Vehículo activo + perfil (usuario / mecánico / taller / flota)
   ↓
Scanner OBD → DTCs / telemetría / salud predictiva
   ↓
Guía de reparación → mecánico / taller
   ↓
Repuesto compatible (Parts Marketplace) ← cross-check con VIN/DTC
   ↓
Cotización → antifraude → aceptación
   ↓
Reparación ejecutada → evidencia antes/después
   ↓
Pre-Scan + Post-Scan → Reporte PDF Certificado
   ↓
Historial técnico del vehículo → garantía → verificación con QR + hash
   ↓
Share: cliente / taller / flotilla / compra-venta / aseguradora
   ↓
[loop] DVIR / mantenimiento / siguiente servicio
```

**Cada paso bloquea al siguiente si le falta evidencia.** No se puede
pedir un repuesto sin DTC o sin pieza identificada. No se puede firmar
un Post-Scan sin foto-antes/foto-después. No se puede exportar un PDF
sin hash verificable. Eso es lo que hace que MEET sea serio, no un
scanner bonito.

---

## "Todo en uno" significa

1. **Las dos specs V2 conviven.** No son A vs B, son A + B + sync:
   - `docs/reports/V2-CERTIFIED-PDF-AND-HISTORY.md` — reportes
     certificados, hash chain, QR, historial del vehículo, evidencia
     fotográfica, firma.
   - `docs/parts-marketplace/V2-TECHNICAL-MARKETPLACE.md` — marketplace
     de repuestos con compatibilidad VIN-DTC, ranking no-por-precio,
     antifraude, panel de repuestera.
   - El reporte PDF **referencia** las cotizaciones aceptadas. La
     cotización aceptada **referencia** el reporte que la cerró. La
     compra queda en el historial. El historial es el flujo entero
     cerrado.

2. **Siempre a más.** Cuando llegue una nueva sección, no se reemplaza
   una vieja. Se suma, se integra, se hace coherente. Los agentes
   (Codex + Mavis + Google Antigravity) **agregan al mismo árbol**, no
   compiten por el mismo slot.

3. **Nunca a menos.** Cuando algo ya funciona (e.g. `ReportIntegrityCard`
   mostrando MATCH byte-exact), no se quita. Se amplía. Si la nueva
   sección necesita más espacio en pantalla, se reorganiza la
   navegación — pero la card sigue ahí.

4. **El máximo nivel de la humanidad** es el techo, no el suelo.
   "Bueno para un MVP" no es un argumento para MEET. El estándar
   acá es: si un perito forense independiente puede verificar el
   reporte en 30 segundos con el QR, está al nivel. Si no, falta.

---

## Implicaciones prácticas para cualquier sesión de trabajo

| Situación | Qué hacer |
|---|---|
| Codex, Mavis o Antigravity traen cambios en paralelo | Todos los avances se auditan y, si hay unión real pendiente, se integran en `sync/codex-mavis-antigravity-*` antes de APK. Nunca se descarta uno. |
| Una sección parece "más importante" que otra | Falso. Son parte del mismo flujo comercial. Ver el diagrama arriba. |
| Hay que elegir entre specs (A o B) | Mal planteada. La pregunta correcta es "¿cómo viven A y B juntas?" |
| Hay que reducir scope para llegar a release | Solo reducir features nuevas, nunca quitar las ya integradas. |
| Una integración "no es urgente" | Si está en el diagrama, es urgente. Si no está, no se construye. |

---

## Lo que ya está ship-ready esta noche (2026-07-04)

| Componente | Tag / commit | Estado |
|---|---|---|
| HashEngine + DiagnosticSnapshot byte-exact con TS | PR-7 (merged main) | ✅ |
| ReportHashingService Hilt-wired | `e1076723` | ✅ local commit |
| ReportIntegrityCard visible en ReportScreen | `e1076723` | ✅ local commit |
| ci-verify.sh green end-to-end | `91662aa2` | ✅ local commit |
| Tag `v0.6.0-report-hashing` con paridad verificada | tag anotado | ✅ local tag |
| Skill `codex-mavis-sync` para Codex, Mavis y Antigravity | `~/.mavis/skills/` + `~/.gemini/config/skills/` | ✅ instalado y validado |
| Spec V2 Reportes Certificados + Historial | `896eea09` | ✅ local commit |
| Spec V2 Parts Marketplace | `b6bb99f2` | ✅ local commit |

---

## Cómo se ve "al máximo nivel de la humanidad" en términos concretos

- **Antifraude**: una pieza usada sin foto se rechaza. Una cotización
  EXACT sin VIN se downgradea a HIGH con advertencia. Un reporte
  firmado se invalida si alguien intenta editarlo.
- **Compatibilidad**: nunca "compatible garantizado", siempre
  "compatibilidad probable, requiere confirmación por VIN/OEM/foto".
- **Offline-first**: el mecánico en un sótano sin señal puede firmar
  un reporte. Cuando llegue la señal, sincroniza.
- **Trazabilidad**: cada DTC tiene un viaje completo — desde que el
  escáner lo leyó hasta que la pieza que lo arregló fue comprada y
  el post-scan confirmó que el código desapareció.
- **Independencia verificable**: un perito externo con el QR puede
  confirmar el hash sin tener cuenta en MEET, sin internet, sin
  instalar nada. Solo necesita el algoritmo SHA-256 y el hash en la
  base de datos pública (cuando haya backend) o el algoritmo
  re-ejecutable localmente.

Eso es el techo. Cualquier feature nueva se mide contra eso.

---

## Refs

- `docs/PRODUCT_OS_ROADMAP.md` — reglas de producto (no inventar
  datos reales, modo guiado vs denso, etc.). Vive junto a este doc.
- `docs/reports/V2-CERTIFIED-PDF-AND-HISTORY.md`
- `docs/parts-marketplace/V2-TECHNICAL-MARKETPLACE.md`
- `docs/adr/0004-report-hashing-service.md`
- `~/.mavis/skills/codex-mavis-sync/SKILL.md`
- `~/.gemini/config/skills/codex-mavis-sync` (Google Antigravity,
  enlace a la implementacion canonica)
