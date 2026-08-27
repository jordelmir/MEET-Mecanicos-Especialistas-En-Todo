# Elysium Vanguard Market Operating System

**Estado:** foundation ejecutable; no equivale a producción validada
**Corte de fuentes:** 2026-08-26
**Verticales:** Legal Vanguard + Elysium Properties + Fuel Rewards / Station OS

## Decisiones no negociables

- PostgreSQL es autoridad para estados observables globalmente; Room es proyección local durable.
- Toda mutación crítica deriva el actor de `auth.uid()`, usa idempotencia y, cuando muta un agregado existente, versión esperada y bloqueo transaccional.
- `UNKNOWN` jamás equivale a verificado.
- CAAB y DNN son credenciales distintas. La habilitación notarial se evalúa por fecha y vigencia, no con un booleano permanente.
- El contenido privilegiado no se indexa globalmente. El conflict packet expone fingerprints y roles mínimos, no el expediente.
- Un Property Passport muestra claims separados; una declaración o documento observado no acredita titularidad.
- El domicilio exacto no aparece en la proyección pública.
- Una campaña publicada es inmutable. Un cupón mantiene la versión que lo originó.
- El QR público contiene un token opaco; la redención usa bloqueo y unicidad para que una sola operación gane.
- Beneficios comerciales se mantienen separados de la tarifa regulada de combustible, salvo aprobación regulatoria explícita.

## Investigación primaria aplicada

| Decisión | Fuente primaria | Impacto en el código |
|---|---|---|
| Taxonomía jurídica por bloques | [Colegio de Abogados — Comisiones](https://www.abogados.or.cr/comisiones/) | Taxonomía Legal CR v1 versionada en servidor; no strings permanentes en Compose. |
| Habilitación notarial por periodos | [Dirección Nacional de Notariado — consulta de habilitación](https://www.dnn.go.cr/preguntas-frecuentes/como-puedo-determinar-si-un-notario-estuvo-habilitado-o-inhabilitado-en-una) | `ProfessionalCredentialProof` conserva estado, consulta, expiración, evidencia y versión de fuente. |
| Póliza obligatoria y posible inhabilitación | [DNN — responsabilidad civil profesional](https://www.dnn.go.cr/noticias/aviso-sobre-acciones-por-incumplimiento-de-renovacion-de-poliza-de-responsabilidad-civil) | Notariado exige prueba DNN activa y no hereda la condición CAAB. |
| Consentimiento, finalidad, destinatarios y seguridad | [Ley 8968, texto vigente en SCIJ](https://pgrweb.go.cr/scij/Busqueda/Normativa/Normas/nrm_texto_completo.aspx?nValor1=1&nValor2=70975&param1=NRTC) | Acceso mínimo, datos cifrados/referenciados, auditoría y consentimiento explícito para CRM. |
| Registro y Catastro como claims distintos | [Registro Nacional — Registro Inmobiliario](https://www.rnp.go.cr/registro_inmobiliario/) | `property_proofs` separa titular, identidad registral, catastro, municipal, notarial e inspección. |
| Preventa / ejecución futura | [MEIC — alcance de ventas a plazo inmobiliarias](https://www.meic.go.cr/wp-content/uploads/2024/10/Charla_reforma_reglamento_bienes_inmuebles23-09.pdf) | `PRESALE` no publica sin `compliance_approved_at`. |
| Promociones deben informar beneficio | [Ley 7472, artículo 41](https://www.meic.go.cr/wp-content/uploads/2024/10/Ley-7472.pdf) | Campaign version exige vigencia, beneficio, elegibilidad, restricciones, redención, versión y hash. |
| Combustible: precio, calidad y cantidad regulados | [ARESEP — servicio de combustible](https://aresep.go.cr/combustible/) y [tarifas vigentes](https://aresep.go.cr/combustible/tarifas/) | `FUEL_PRICE_CREDIT` falla cerrado sin aprobación; rewards se contabilizan aparte. |
| Estaciones y evidencia regulatoria oficial | [ARESEP — consulta de estaciones](https://aresep.go.cr/combustible/consulta-estaciones-servicios/) | Estación conserva referencia, truth state y fecha oficial; no se atribuye a MEET la verificación ARESEP. |
| RLS y seguridad de funciones | [Supabase — Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security) | RLS en todas las tablas, grants explícitos, funciones privadas y `search_path = ''`. |
| Offline-first | [Android — offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first) | UI lee Room; outbox durable espera sincronización autoritativa. |
| Migraciones Room | [Android — migrar Room](https://developer.android.com/training/data-storage/room/migrating-db-versions) | Migraciones 60→61 y 61→62, esquema exportado y prueba de conformance. |
| Scanner cliente sin permiso | [Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner) | `play-services-code-scanner:16.1.0`, QR-only y auto-zoom; resultado aún requiere servidor. |
| Scanner profesional configurable | [ML Kit Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android) | Se conserva ML Kit para futura UI CameraX profesional QR-only. |
| Exclusión mutua e idempotencia | [PostgreSQL — INSERT / ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html) | Unicidad de idempotency keys, tokens y redenciones; `FOR UPDATE` en redención. |

## Entrega implementada en este corte

### Shared Platform

- Contratos de organización, membresía, RBAC, credencial, truth claims, Money reutilizado y comando con SHA-256 canónico.
- PostgreSQL: organizaciones, membresías, credenciales, taxonomías, commands y auditoría.
- Room 62: organizaciones, legal matters, property listings, fuel coupons y command outbox, todos owner-scoped.
- Reparación forward-only de la migración Room 60→61 faltante.

### Legal Vanguard

- Dominio de elegibilidad CAAB/DNN y política de acceso tras conflict clearance.
- Taxonomía Costa Rica v1 con 72 categorías/bloques jurídicos y servicios versionados.
- Matter, parties mínimos, conflict checks, offers, engagements, Legal Vault documents y deadlines.
- RPCs para crear matter, obtener conflict packet mínimo y registrar el conflict check.
- Hub cliente con triage privado que nunca afirma diagnóstico o envío si sólo está preparado localmente.

### Properties

- Dominio Property Passport, truth claims, dirección pública aproximada y `PropertyTrustEngine`.
- Assets, proofs, listings, offers, due diligence, transactions y leases.
- RPCs de alta de activo y publicación; preventa falla sin compliance.
- Hub con claims separados e integración directa hacia Legal Vanguard.

### Fuel Rewards

- Campaign terms/version, reward policy exacta en minor units, QR opaco y cupón.
- Stations, campaigns/versiones, purchases, coupons, redemptions, customer profiles y CRM events.
- RPCs de publicación, compra, emisión y redención. La redención bloquea el cupón y tiene unicidad por cupón e idempotency key.
- Google Code Scanner QR-only sin permiso de cámara; el UI rotula que la validación servidor sigue pendiente.

## Matriz honesta de evidencia

| Capacidad | Estado máximo demostrado en este corte |
|---|---|
| Shared domain + Room + SQL authority | `SERVER_AUTHORITATIVE` para los comandos implementados |
| Legal triage/conflict foundation | `CLIENT_IMPLEMENTED` + `SERVER_AUTHORITATIVE` en create/conflict RPC |
| Legal engagement/firm billing completo | `MODEL_EXISTS` |
| Property passport/listing gate | `CLIENT_IMPLEMENTED` + `SERVER_AUTHORITATIVE` en create/publish RPC |
| Property closing/rental operations completas | `MODEL_EXISTS` |
| Fuel purchase/issue/redeem kernel | `SERVER_AUTHORITATIVE` |
| Fuel wallet scanner | `CLIENT_IMPLEMENTED` |
| Station professional CameraX scanner | `MODEL_EXISTS` (dependencia ML Kit presente; UI dedicada pendiente) |
| Dispositivo físico | No demostrado |
| Integración con CAAB, DNN, RNP, ARESEP o POS real | No demostrada; requiere acuerdos/API o verificación supervisada |
| Producción | No validada |

No se debe promover ninguna fila a `DEVICE_VERIFIED`, `PHYSICALLY_VERIFIED` o `PRODUCTION_VALIDATED` sin la evidencia correspondiente.
