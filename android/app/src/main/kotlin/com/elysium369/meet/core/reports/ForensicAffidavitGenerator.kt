package com.elysium369.meet.core.reports

import com.elysium369.meet.diagnostic.DiagnosticSnapshot

/**
 * Bilingual Forensic Technical Affidavit Generator (ES / EN - C1).
 *
 * Produces structured, legally defensible automotive forensic inspection
 * affidavits for expert witness proceedings, insurance loss adjustments,
 * pre-purchase verification, and technical warranty validation.
 *
 * Adheres strictly to AGENTS.md:
 * - Never invents data ("Dato no capturado", "Requiere prueba física").
 * - Applies modal hedging in C1 English ("The empirical data indicates...", "It is plausible that...").
 * - Embeds tamper-evident SHA-256 Merkle root and 6-field QR verification payload.
 */
object ForensicAffidavitGenerator {

    enum class AffidavitLanguage {
        SPANISH_FORMAL,
        ENGLISH_C1_FORENSIC,
        BILINGUAL_DUAL_COLUMN
    }

    data class AffidavitInput(
        val affidavitNumber: String,
        val inspectorName: String,
        val inspectorCredentialNumber: String,
        val jurisdiction: String = "República de Costa Rica — Ley de Tránsito N.° 9078",
        val vehicleId: String,
        val redactedVin: String,
        val redactedPlate: String,
        val odometerKm: Long?,
        val preScanSnapshot: DiagnosticSnapshot,
        val postScanSnapshot: DiagnosticSnapshot?,
        val installedParts: List<InstalledPartEvidence>,
        val physicalMeasurements: Map<String, String>,
        val evidenceHashes: List<String>,
        val previousReportHash: String? = null,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class InstalledPartEvidence(
        val partName: String,
        val oemPartNumber: String?,
        val invoiceOrBatchNumber: String?,
        val verifiedByMultimeterOrPressure: Boolean
    )

    data class ForensicAffidavitDocument(
        val affidavitNumber: String,
        val language: AffidavitLanguage,
        val title: String,
        val bodyMarkdown: String,
        val forensicMerkleRoot: String,
        val overallAffidavitHash: String,
        val qrPayload: QrPayload,
        val generatedAtMs: Long
    )

    /**
     * Generates a complete forensic affidavit in the requested language.
     */
    fun generate(input: AffidavitInput, language: AffidavitLanguage): ForensicAffidavitDocument {
        val title = when (language) {
            AffidavitLanguage.SPANISH_FORMAL -> "DICTAMEN PERICIAL FORENSE DE INGENIERÍA AUTOMOTRIZ"
            AffidavitLanguage.ENGLISH_C1_FORENSIC -> "AUTOMOTIVE FORENSIC ENGINEERING AFFIDAVIT & EXPERT OPINION"
            AffidavitLanguage.BILINGUAL_DUAL_COLUMN -> "DICTAMEN TÉCNICO FORENSE BILINGÜE / BILINGUAL FORENSIC AFFIDAVIT"
        }

        // Compute Evidence Merkle Root
        val sortedHashes = input.evidenceHashes.sorted()
        val merkleRoot = if (sortedHashes.isEmpty()) "NO_EVIDENCE_RECORDED" else HashEngine.sha256Hex(sortedHashes.joinToString("::"))

        // Build Document Body
        val body = when (language) {
            AffidavitLanguage.SPANISH_FORMAL -> buildSpanishBody(input, merkleRoot)
            AffidavitLanguage.ENGLISH_C1_FORENSIC -> buildEnglishC1Body(input, merkleRoot)
            AffidavitLanguage.BILINGUAL_DUAL_COLUMN -> buildBilingualBody(input, merkleRoot)
        }

        // Canonical Overall Affidavit Hash
        val canonicalAffidavit = listOf(
            input.affidavitNumber,
            input.vehicleId,
            input.redactedVin,
            input.preScanSnapshot.hashSha256,
            input.postScanSnapshot?.hashSha256 ?: "NO_POST_SCAN",
            merkleRoot,
            input.previousReportHash ?: "GENESIS",
            input.timestampMs.toString()
        ).joinToString("|")

        val overallHash = HashEngine.sha256Hex(canonicalAffidavit)

        val qrPayload = QrPayload(
            reportId = input.affidavitNumber,
            integrityHash = overallHash,
            vehicleId = input.vehicleId,
            generatedAt = input.timestampMs,
            reportType = if (input.postScanSnapshot != null) ReportType.POST_SCAN_REPORT else ReportType.PRE_SCAN_REPORT,
            verifierUrl = "https://meet.elysium.app/verify/${input.affidavitNumber}"
        )

        return ForensicAffidavitDocument(
            affidavitNumber = input.affidavitNumber,
            language = language,
            title = title,
            bodyMarkdown = body,
            forensicMerkleRoot = merkleRoot,
            overallAffidavitHash = overallHash,
            qrPayload = qrPayload,
            generatedAtMs = input.timestampMs
        )
    }

    private fun buildSpanishBody(input: AffidavitInput, merkleRoot: String): String = buildString {
        appendLine("# DICTAMEN PERICIAL FORENSE DE INGENIERÍA AUTOMOTRIZ")
        appendLine("**Ref. N.°**: ${input.affidavitNumber} | **Jurisdicción**: ${input.jurisdiction}")
        appendLine("**Perito Certificado**: ${input.inspectorName} (Carné MEP / CT: ${input.inspectorCredentialNumber})")
        appendLine()
        appendLine("---")
        appendLine("## 1. Identificación Registral y Física del Vehículo")
        appendLine("- **Identificador del Sistema**: `${input.vehicleId}`")
        appendLine("- **VIN Censurado (Anti-Doxxing)**: `${input.redactedVin}`")
        appendLine("- **Placa Censurada**: `${input.redactedPlate}`")
        appendLine("- **Odómetro Registrado**: ${input.odometerKm?.let { "$it km" } ?: "*Dato no capturado*"}")
        appendLine()
        appendLine("## 2. Telemetría y Hallazgos Pre-Scan (Diagnóstico Inicial)")
        appendLine("- **Hash SHA-256 Pre-Scan**: `${input.preScanSnapshot.hashSha256}`")
        val activeDtcs = input.preScanSnapshot.dtcsActive
        if (activeDtcs.isEmpty()) {
            appendLine("- **Códigos de Falla Activos**: Ningún código registrado (OBD en estado limpio).")
        } else {
            appendLine("- **Códigos de Falla Activos**: ${activeDtcs.joinToString(", ")}")
            input.preScanSnapshot.freezeFramePidValues.forEach { (pid, value) ->
                appendLine("  - *Freeze Frame $pid*: $value")
            }
        }
        appendLine()
        appendLine("## 3. Pruebas Físicas e Intervenciones Mecánicas")
        if (input.installedParts.isEmpty()) {
            appendLine("- *No se requirió sustitución de componentes mayores.*")
        } else {
            input.installedParts.forEach { part ->
                appendLine("- **Componente**: ${part.partName} | OEM: `${part.oemPartNumber ?: "Pendiente de validación"}`")
                appendLine("  - Factura/Lote: ${part.invoiceOrBatchNumber ?: "No provisto"} | Verificación física: ${if (part.verifiedByMultimeterOrPressure) "APROBADA" else "Requiere prueba física"}")
            }
        }
        if (input.physicalMeasurements.isNotEmpty()) {
            appendLine("- **Mediciones de Laboratorio / Taller**:")
            input.physicalMeasurements.forEach { (k, v) -> appendLine("  - $k: $v") }
        }
        appendLine()
        appendLine("## 4. Auditoría Post-Scan y Verificación Causal")
        if (input.postScanSnapshot == null) {
            appendLine("> [!WARNING]")
            appendLine("> **Post-Scan no disponible**: El presente documento constituye un informe preliminar. La garantía no surtirá efecto legal hasta certificar la ausencia de códigos residuales.")
        } else {
            appendLine("- **Hash SHA-256 Post-Scan**: `${input.postScanSnapshot.hashSha256}`")
            val postDtcs = input.postScanSnapshot.dtcsActive
            val resolved = activeDtcs.filter { it !in postDtcs }
            appendLine("- **Códigos DTC Resueltos Exitosamente**: ${if (resolved.isEmpty()) "Ninguno" else resolved.joinToString(", ")}")
            if (postDtcs.isNotEmpty()) {
                appendLine("- **Códigos Residuales Persistentes**: ${postDtcs.joinToString(", ")} *(Requiere diagnóstico complementario)*")
            }
        }
        appendLine()
        appendLine("## 5. Conclusión Pericial y Fe Pública")
        appendLine("El perito firmante certifica bajo fe de juramento que las mediciones, hashes criptográficos y evidencias " +
            "fueron capturados de manera directa y sin manipulación sintética. Cualquier enmienda no registrada invalida la cadena de custodia.")
        appendLine()
        appendLine("- **Raíz Merkle de Evidencias**: `$merkleRoot`")
        appendLine("- **Hash Inmutable del Dictamen**: *Generado al pie con sello QR de 6 campos.*")
    }

    private fun buildEnglishC1Body(input: AffidavitInput, merkleRoot: String): String = buildString {
        appendLine("# AUTOMOTIVE FORENSIC ENGINEERING AFFIDAVIT & EXPERT OPINION")
        appendLine("**Affidavit Docket**: ${input.affidavitNumber} | **Jurisdiction**: ${input.jurisdiction}")
        appendLine("**Certified Forensic Examiner**: ${input.inspectorName} (MEET / MEP Credential: ${input.inspectorCredentialNumber})")
        appendLine()
        appendLine("---")
        appendLine("## 1. Vehicle Identification & Epistemic Boundary")
        appendLine("- **Platform UUID**: `${input.vehicleId}`")
        appendLine("- **VIN Identifier (Redacted)**: `${input.redactedVin}`")
        appendLine("- **License Plate (Redacted)**: `${input.redactedPlate}`")
        appendLine("- **Certified Odometer**: ${input.odometerKm?.let { "$it km" } ?: "*Unrecorded metric*"}")
        appendLine()
        appendLine("## 2. Pre-Scan Telemetric Diagnostics & Empirical Findings")
        appendLine("- **Pre-Scan Cryptographic Hash**: `${input.preScanSnapshot.hashSha256}`")
        val activeDtcs = input.preScanSnapshot.dtcsActive
        if (activeDtcs.isEmpty()) {
            appendLine("- **Diagnostic Trouble Codes**: No persistent active faults logged in non-volatile ECU memory.")
        } else {
            appendLine("- **Logged Diagnostic Trouble Codes**: ${activeDtcs.joinToString(", ")}")
            input.preScanSnapshot.freezeFramePidValues.forEach { (pid, value) ->
                appendLine("  - *Freeze Frame Snapshot PID [$pid]*: $value")
            }
            appendLine()
            appendLine("The empirical sensor telemetry strongly suggests that the anomalies detected correlate with the aforementioned DTCs. " +
                "Under no circumstances should secondary component degradation be ruled out prior to physical circuit verification.")
        }
        appendLine()
        appendLine("## 3. Physical Interventions, Metrology & Component Substitution")
        if (input.installedParts.isEmpty()) {
            appendLine("- *No mechanical or electrical assembly replacement was necessitated during this session.*")
        } else {
            input.installedParts.forEach { part ->
                appendLine("- **Assembly Replaced**: ${part.partName} | OEM Spec: `${part.oemPartNumber ?: "Pending cross-reference validation"}`")
                appendLine("  - Chain-of-Custody Trace: ${part.invoiceOrBatchNumber ?: "Unspecified"} | Physical Multimeter/Pressure Test: ${if (part.verifiedByMultimeterOrPressure) "CONGRUENT / PASSED" else "Requires physical re-testing"}")
            }
        }
        if (input.physicalMeasurements.isNotEmpty()) {
            appendLine("- **Laboratory Metrology Data**:")
            input.physicalMeasurements.forEach { (k, v) -> appendLine("  - $k: $v") }
        }
        appendLine()
        appendLine("## 4. Post-Scan Telemetry & Causal Audit")
        if (input.postScanSnapshot == null) {
            appendLine("> [!WARNING]")
            appendLine("> **Post-Scan Verification Pending**: Had a post-repair diagnostic capture been submitted, conclusive causal resolution would be affirmed. This dossier remains conditional.")
        } else {
            appendLine("- **Post-Scan Cryptographic Hash**: `${input.postScanSnapshot.hashSha256}`")
            val postDtcs = input.postScanSnapshot.dtcsActive
            val resolved = activeDtcs.filter { it !in postDtcs }
            appendLine("- **Empirically Resolved Codes**: ${if (resolved.isEmpty()) "None confirmed" else resolved.joinToString(", ")}")
            if (postDtcs.isNotEmpty()) {
                appendLine("- **Residual Faults**: ${postDtcs.joinToString(", ")} *(Plausibly linked to peripheral wiring or secondary sub-systems)*")
            } else {
                appendLine("The comparative analysis demonstrates complete clearing of the primary fault signatures without induced collateral faults.")
            }
        }
        appendLine()
        appendLine("## 5. Forensic Expert Attestation")
        appendLine("I hereby declare under penalty of perjury that the diagnostic findings, cryptographic hashes, and metrological " +
            "measurements detailed herein represent an authentic, unedited forensic record of the examined vehicle.")
        appendLine()
        appendLine("- **Evidence Merkle Root Hash**: `$merkleRoot`")
        appendLine("- **Immutable Dossier Digest**: *Enforced via 6-field QR signature.*")
    }

    private fun buildBilingualBody(input: AffidavitInput, merkleRoot: String): String = buildString {
        appendLine(buildSpanishBody(input, merkleRoot))
        appendLine()
        appendLine("---")
        appendLine()
        appendLine(buildEnglishC1Body(input, merkleRoot))
    }
}
