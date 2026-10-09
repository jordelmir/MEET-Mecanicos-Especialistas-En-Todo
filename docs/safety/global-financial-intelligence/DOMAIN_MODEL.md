# Elysium Safety — Modelo de dominio y contratos epistemológicos
## Documento vivo que distingue código existente de arquitectura objetivo

**SHA de línea base revisada:** 32f4854de1152f008e641770d706eac882ee4952

> Invariantes: CLAIM ≠ CONVICTION · REPORT ≠ VERIFIED FACT · ANOMALY ≠ CRIME · CORRELATION ≠ CAUSATION · AI OUTPUT ≠ FACT.

## 1. Entidades existentes que deben reutilizarse

### 1.1 Reporte de seguridad
El modelo Room SafetyReportEntity contiene identificador estable, propietario, categoría, payloadId, occurredAt, localState, serverState, serverVersion, syncState y timestamps de creación/actualización. El payload y los metadatos asociados se gestionan desde el repositorio y el contrato remoto existentes.

Requisito: distinguir al menos:
- cuándo ocurrió el acontecimiento, si se conoce;
- cuándo se registró o conoció la información;
- fuente/relación de la persona con la información;
- ubicación, precisión y origen de ubicación;
- estado local, estado remoto y versión remota;
- relato, adjuntos y enlaces relacionados;
- incertidumbres, correcciones y contradicciones.

No crear una segunda tabla de reportes si los contratos vigentes pueden ampliarse aditivamente.

### 1.2 Evidencia
SafetyEvidenceEntity conserva ID, reportId, ownerUserId, ruta local cifrada, contentSha256, mimeType, byteCount, stagedAt, uploadState, attemptCount, lastErrorCode y serverReceipt.

Requisitos objetivo:
- hash de bytes originales, no únicamente del nombre o de un objeto normalizado;
- vínculo explícito con reporte y, cuando proceda, afirmación;
- registro de incorporación, verificación, descarga, derivación y exportación;
- separación entre original y cualquier thumbnail, transcripción, resumen u otro derivado;
- retención por política y base jurídica; no prometer retención infinita en todos los casos;
- cualquier corrección debe ser atribuible, conservando el historial autorizado.

### 1.3 Núcleo científico existente
El repo contiene SciEntityEntity, SciClaimEntity, SciHypothesisEntity y SciEventEntity en los modelos de la capa científica. ScientificAuthorityEntities.kt define entidades para comandos/outbox y registros de autoridad. SafetyScienceRepository usa AssertionStateMachine; SupabaseScientificGateway encapsula operaciones RPC.

Las entidades de dominio ScientificEntity, ScientificClaim, ScientificClaimRelation y TemporalScope expresan identificadores, afirmaciones, evidencias que apoyan/contradicen, relaciones entre claims y dimensiones temporales.

No duplicar estos objetos en un segundo sistema de investigación. Mapear firmas y consumidores reales antes de cambiar esquema.

### 1.4 Registro de fuente y procedencia
Modelo objetivo, que debe mapearse a las tablas reales antes de implementarlo:

- Identificador estable de fuente.
- Tipo, autoridad emisora, jurisdicción y URL/identificador original.
- Fecha de publicación y de recuperación.
- Método/versión de extracción.
- Hash de bytes originales y hash de la representación normalizada como propiedades distintas.
- Base legal y permisos de uso.
- Estado de autenticación/verificación con fundamento explícito.
- Cobertura, caducidad, corrección o supersesión.
- Limitaciones, error de parser y datos desconocidos.

La presencia de un archivo o URL no autoriza a marcar sourceVerified o lawfullyObtained como true. Esos valores deben venir de una autoridad de procedencia confiable.

### 1.5 Entidad y relación
ScientificEntity tiene tipo, nombre canónico, alias, identificadores externos y estado de aserción. EntityRelation mantiene sujetos/objetos, tipo, intervalo de vigencia, evidencia de apoyo y estado de aserción.

Reglas:
- no fusionar dos entidades por similitud de nombre, dirección, apellido o proximidad;
- los identificadores ausentes o en conflicto dejan la relación sin resolver y requieren revisión;
- cada relación documentada debe tener fuente verificable;
- las relaciones inferidas deben quedar marcadas como hipótesis, incluyendo alternativas;
- conservar el valor original de cada documento junto a cualquier valor normalizado.

### 1.6 Señal analítica
Las reglas deterministas deben producir entradas, versión, fórmula, ventana temporal, muestra, cobertura, referencias de fuente, alternativas, datos faltantes y resultado reproducible.

Una señal significa que se cumple una regla estadística/de documentación, no que ocurrió un delito. No debe haber puntuación pública de criminalidad de personas.

## 2. Estados: dominios distintos, sin mezclarlos

### 2.1 Estados de conocimiento
El OS mantiene TruthState con valores tales como OBSERVED, AUTHORITATIVE, DERIVED, ESTIMATED, SIMULATED, UNKNOWN, NOT_INTEGRATED y NOT_EXECUTED. La capa científica usa EvidenceAssertionState con OBSERVED, DOCUMENTED, AUTHORITATIVE, CORROBORATED, DERIVED, estados analíticos y estados negativos/indeterminados.

No se debe crear un enum duplicado con semántica supuestamente idéntica. El mapeo entre ambos debe estar documentado y probado.

### 2.2 Brecha semántica que debe solucionarse
TruthStateMapping actualmente mapea ESTIMATED, SIMULATED, NOT_INTEGRATED y NOT_EXECUTED a INSUFFICIENT_EVIDENCE. Es conservador para impedir una elevación, pero pierde cuál fue el estado original.

Solución objetivo: conservar el estado original o un metadato de procedencia junto al resultado de elegibilidad. El gate puede negar revisión, pero el registro debe permitir saber si el origen fue estimado, simulado, no integrado, no ejecutado o desconocido. Cualquier cambio de contrato requiere actualización coordinada de Kotlin, TypeScript y SQL, con migración aditiva y pruebas de paridad.

### 2.3 Estados de flujo que son diferentes
No mezclar estas categorías:

- Epistemología: qué se conoce y con qué fundamento.
- Sincronización: LOCAL/PENDING/IN_FLIGHT/ACKNOWLEDGED/CONFLICT/ERROR según el contrato vigente.
- Verificación criptográfica: MATCH/MISMATCH/QUARANTINED/ERROR.
- Publicación: borrador, pendiente de revisión, autorizado, retirado o corregido.
- Permisos: permitido/denegado para actor, organización, finalidad y recurso.
- Integración: disponible, pendiente, no integrada o no ejecutada.

Un envío local nunca se traduce automáticamente en recepción remota. Una verificación MATCH tampoco significa que el contenido sea verdadero.

## 3. Ciclo de vida de una afirmación

- Crear: estado inicial OBSERVED según el contrato existente.
- Adjuntar referencias a evidencia y contraevidencia.
- Registrar el método y la justificación de transición.
- Cambiar de estado únicamente mediante la máquina de estados y autoridad correspondiente.
- Conservar quién, cuándo, por qué y qué fuentes soportaron la transición.
- Permitir hipótesis alternativas y refutación.
- Registrar corrección o retirada como evento nuevo cuando lo exige la política de inmutabilidad.
- Bloquear la promoción automática por IA o por un reportante.

No introducir una nueva máquina paralela sin demostrar que AssertionStateMachine no satisface el caso.

## 4. Modelo de divulgación

Clases conceptuales:
- PRIVADO: información aportada por una fuente o reporte privado.
- RESTRINGIDO: expediente accesible a un grupo autorizado.
- PUBLICABLE: registro revisado para una finalidad y público definidos.
- AGREGADO: proyección territorial con granularidad y retraso autorizados.
- FUENTE DOCUMENTAL PÚBLICA: registro externo cuyo uso y retención están permitidos.
- RETIRADO/CORREGIDO: publicación cambiada, con historial conforme a las obligaciones de retención.

Estos niveles conceptuales no sustituyen los permisos reales de RLS, Storage o RPC. La autorización debe imponerse en el servidor.

## 5. Dominio de inteligencia financiera separado

Modelos objetivo:
- SourceRecord
- EconomicEntity
- EntityRelationship
- FinancialObservation
- InvestigativeSignal
- ReviewDecision
- AccessAuditEvent

Estos nombres son conceptos de trabajo, no autorización para crear nuevas tablas inmediatamente. Primero se comparan con safety_scientific_entities, safety_scientific_entity_relations, safety_scientific_claim_evidence, safety_scientific_evidence_references, safety_scientific_cases, safety_scientific_case_items y el resto del esquema real.

La línea financiera solo procesa documentación lícita y pertinente. Riqueza visible por sí sola no cuenta como evidencia de ilegalidad. Las reglas son reproducibles, se limitan a relaciones documentadas y requieren revisión humana. No hay ingreso de datos externo activo hasta probar un adaptador real.

## 6. Relaciones y custodia

Modelo conceptual:
Evento → Afirmación → Fuente → Evidencia → Hipótesis → Análisis → Revisión.

Cada flecha requiere referencia estable, relación con semántica precisa, estado y evidencia de respaldo o una marca explícita de hipótesis. Debe ser posible recorrer la relación hasta el origen y ver contraevidencia.

Los hashes prueban igualdad de bytes comparados. Una firma relaciona contenido con una clave. Un árbol de Merkle prueba las propiedades de inclusión implementadas sobre una raíz. Ninguno de estos mecanismos valida por sí solo la verdad del documento, la identidad real del autor o la admisibilidad judicial.

## 7. Cambios de datos permitidos

Antes de cada migración:
1. fijar SHA y esquema de partida;
2. identificar consultas, vistas, funciones y consumidores;
3. definir rollback/forward-fix y compatibilidad;
4. agregar constraints e índices solo a partir de uso real;
5. probar permisos y aislamiento de tenants;
6. verificar aplicación desde esquemas soportados;
7. preservar contratos con clientes de versiones anteriores.

No se autoriza crear un bridge, segundo outbox, fuente de verdad o entidad de evidencia duplicada sin un ADR basado en código y consultas reales.
