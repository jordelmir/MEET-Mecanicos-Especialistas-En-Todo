# Elysium Safety — Source Adapter Contract
## Ingestion of Lawful Public Records & Procurement Data

> **Integrity Invariant:**  
> **A hash proves correspondence to specific bytes. It does not prove that claims inside the document are true.**  
> **Raw original bytes must never be mutated or discarded.**

---

## 1. Adapter Interface Specification

All source adapters must implement the strict boundary contract:

```kotlin
interface SourceAdapter<TRawRecord> {
    val adapterId: String
    val jurisdiction: String
    val legalBasis: LegalProvenanceBasis
    val authorityName: String

    suspend fun fetchRecord(recordIdentifier: String): AdapterResult<NormalizedSourceRecord>
    suspend fun pollUpdates(since: Instant, limit: Int): AdapterResult<List<NormalizedSourceRecord>>
    fun computeDigest(rawBytes: ByteArray): String
    fun validateLegalAccess(context: IngestionContext): AccessAuthorizationDecision
}
```

### Core Requirements for Every Adapter
1. **Legal Access Basis:** Must declare statutory access foundation (e.g., Costa Rica *Ley General de Contratación Pública N.° 9986*, Art. 16 sobre Principio de Transparencia).
2. **Byte-Exact Hash:** SHA-256 computed on unmodified incoming payload prior to transformation.
3. **Idempotent Ingestion:** The deduplication key is derived as:
   $$\text{dedupKey} = \text{SHA-256}(\text{adapterId} \parallel \text{jurisdiction} \parallel \text{externalRecordId})$$
4. **Error Classification:**
   - `NETWORK_TRANSIENT_RETRYABLE`: Backoff with jitter (max 5 retries).
   - `SCHEMA_DRIFT_DETECTED`: Ingestion halts; alert issued; original raw payload saved for audit.
   - `AUTHORITY_REVOKED`: Access terminated immediately.
   - `INSUFFICIENT_METADATA`: Ingested record marked `UNKNOWN` and excluded from signal generation.

---

## 2. Reference Implementation: Costa Rica SICOP Adapter

### Source Authority
- **System:** Sistema Integrado de Compras Públicas (SICOP), Costa Rica.
- **Governing Law:** Ley N.° 9986 y Reglamento a la Ley General de Contratación Pública.
- **Public Access Endpoint:** Módulo de Consulta Pública de Procedimientos y Contratos.

### Data Normalization Schema

| SICOP Field | Normalized Field | Canonical Type | Validation Rule |
|---|---|---|---|
| `num_expediente` | `externalRecordId` | String | Format: `^\d{4}[A-Z]{2}-\d{6}-\d{10}$` |
| `tipo_procedimiento` | `procurementType` | Enum | `LICITACION_MAYOR`, `LICITACION_MENOR`, `LICITACION_REDUCIDA`, `CONTRATACION_DIRECTA` |
| `institucion_compradora` | `buyerInstitution` | String | Non-empty registered entity name |
| `adjudicatario_cedula` | `vendorTaxId` | String | Format: `^\d{1}-\d{3}-\d{6}$` (Cédula Jurídica) |
| `adjudicatario_nombre` | `vendorName` | String | Exact registered company name |
| `monto_adjudicado` | `amountMinorUnits` | Long | Expressed in integer Colones or Cents |
| `moneda` | `currencyCode` | ISO-4217 | `CRC` or `USD` |
| `fecha_adjudicacion` | `awardDate` | Instant | UTC timestamp |
| `enlace_expediente` | `canonicalUrl` | URL | Official SICOP portal URL |

### Deduplication and Identity Resolution
- The vendor is registered as an `EconomicEntity` with `jurisdiction = "CR"` and `canonicalTaxId = vendorTaxId`.
- If two records share the exact same `vendorTaxId`, they link to the same entity.
- If two records share similar `vendorName` but differ in `vendorTaxId`, **they are treated as distinct entities** with `resolutionConfidence = UNRESOLVED_CANDIDATE`. Automatic merging is strictly prohibited.
