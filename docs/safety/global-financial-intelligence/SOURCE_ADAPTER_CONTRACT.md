# Elysium Safety — Contrato genérico de adaptadores documentales
## Fuentes públicas y documentos recibidos legítimamente

> Integridad no equivale a veracidad. El hash del documento original y el digest de un registro normalizado son mediciones distintas y nunca deben etiquetarse como si fueran la misma cosa.

## 1. Estado actual

PublicProcurementRecordNormalizer.kt existe como normalizador de dominio de registros documentales. Valida los campos requeridos, URL HTTP(S), moneda/cantidad y calcula SHA-256 de una representación de campos normalizados. El archivo no descarga fuentes, no programa sincronizaciones, no demuestra autenticidad de una fuente ni constituye una ingesta operativa.

Este contrato describe la arquitectura requerida para construir adaptadores legales reales. No declara activo ningún proveedor externo.

## 2. Frontera de adaptación

Interfaz conceptual:

- adapterId y versión de parser.
- jurisdicción y responsable de los datos.
- autoridad/fuente declarada.
- base legal y propósitos autorizados.
- autenticación requerida y política de rate limit.
- recuperación de un registro por identificador y consulta incremental con cursor.
- hash byte-exacto del payload original antes de transformarlo.
- normalización versionada en un objeto diferente.
- resultado tipado para éxito, reintento, cambio de esquema, falta de metadatos y denegación legal.
- métricas de cobertura y fecha de última recuperación.

La interfaz debe ajustarse a los patrones reales de la aplicación y backend; no debe implementarse como otro subsistema de sincronización paralelo.

## 3. Registro de fuente normalizado

Campos mínimos cuando la fuente los proporciona:
- sourceRecordId estable.
- adapterId, adapterVersion, parserVersion.
- sourceSystem y autoridad emisora.
- jurisdiction.
- externalRecordId.
- canonicalUrl o referencia de archivo.
- publicationTimestamp y retrievedAt separados.
- rawPayloadSha256: digest de los bytes originales realmente obtenidos.
- normalizedRecordSha256: digest de una serialización canónica y versionada.
- originalMimeType y byteCount, si aplica.
- legalBasis, permittedPurpose y accessDecision.
- provenanceStatus y rationale.
- parserWarnings, missingFields y coverageLimitations.
- supersedes/correction linkage.
- retentionDisposition.

Si el sistema no retiene los bytes por una restricción legítima de privacidad, licencia o retención, debe preservar la referencia y el motivo permitido para no retenerlos; no afirmar una verificación byte-exacta sobre un archivo que no se capturó.

## 4. Procedencia y autoridad

- Nunca aceptar sourceVerified, lawfullyObtained, organizationId, actorId o review state como datos autoritativos enviados por Android.
- La autoridad de procedencia se asigna a partir de credenciales, catálogo confiable de origen, validación del servidor y evidencia registrada.
- La existencia de una URL o la validez de JSON no demuestra que la fuente sea auténtica.
- Una copia, extracto de buscador o republicación no es automáticamente una fuente independiente.
- Si no se puede determinar legalidad, autoridad o independencia, el registro permanece desconocido/no verificado y no produce señal analítica positiva.

## 5. Ingesta y resiliencia

- límites de respuesta y tiempos de espera;
- paginación y reanudación mediante cursor;
- reintento idempotente con límites y backoff;
- rechazo de tipos/tamaños no permitidos;
- versionado del parser;
- detección de duplicados sin fusionar entidades por nombre;
- cuarentena ante hash incorrecto o cambio inesperado de esquema;
- clasificación explícita de error;
- métricas de cobertura y frescura;
- control de retención y eliminación legítima;
- registro auditable de recuperación/corrección.

No se deben rellenar campos desconocidos por suposición. La cobertura incompleta se registra como limitación y limita los resultados derivados.

## 6. Resolución de entidades

Dos organizaciones no se fusionan por similitud de nombres, dirección, representante común o proximidad. Los identificadores fuertes deben interpretarse dentro de su jurisdicción y acompañarse de documento fuente. Las coincidencias débiles se guardan como candidatos no resueltos para revisión humana.

## 7. Reglas analíticas

Cada regla de dominio debe definir:
- identificador y versión;
- precondiciones y cobertura mínima;
- fórmula y ventana temporal;
- orígenes de entrada y huellas de los documentos;
- grupos de independencia;
- alternativas legítimas;
- modos conocidos de falso positivo;
- resultado reproducible y estado de revisión.

Una señal describe un patrón dentro de datos documentados; no establece corrupción ni culpabilidad. Si falta cobertura o procedencia, devuelve INSUFFICIENT_DATA.

## 8. Pruebas obligatorias

- payload original alterado cambia rawPayloadSha256;
- una representación normalizada distinta cambia su digest canónico;
- el digest canónico no se anuncia como hash de bytes originales;
- URL no válida, payload demasiado grande o tipo prohibido se rechaza;
- parser sin campos esenciales devuelve falta de información;
- retry no duplica el registro;
- copia de una fuente no se cuenta como corroboración independiente;
- empresa con nombre similar y otro identificador no se fusiona;
- revocación de acceso detiene la ingesta;
- cada resultado puede reproducirse a partir de fuente, versión y parámetros;
- sin autoridad o legalidad verificadas no se escala una señal;
- el conector solo se declara operativo después de pruebas de extremo a extremo en entorno autorizado.
