package com.elysium369.meet.safety.science.publication

import com.elysium369.meet.safety.science.provenance.MerkleTree
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

// ═══════════════════════════════════════════════════════════════════
// §65 — Research Package Export
//
// A reproducible, verifiable research package that any university
// can download and verify WITHOUT trusting Elysium.
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class ResearchManifest(
    val schemaVersion: String = "1.0",
    val datasetVersion: String,
    val methodologyVersion: String,
    val codeCommit: String,
    val datasetSha256: String,
    val evidenceManifestSha256: String,
    val provenanceSha256: String,
    val claimsSha256: String,
    val eventsSha256: String,
    val entitiesSha256: String,
    val generatedAt: String,
    val generatedBy: String = "Elysium Safety Scientific Platform",
)

private val json = Json { prettyPrint = true }

/**
 * Generates a research package directory.
 *
 * Structure:
 * ```
 * research-package/
 * ├── manifest.json
 * ├── dataset.jsonl
 * ├── entities.jsonl
 * ├── claims.jsonl
 * ├── events.jsonl
 * ├── evidence-manifest.jsonl
 * ├── provenance.jsonl
 * ├── methodology.md
 * ├── analysis/
 * ├── replication/
 * ├── checksums.txt
 * └── README.md
 * ```
 */
class ResearchPackageExporter {

    fun export(
        outputDir: File,
        datasetVersion: String,
        methodologyVersion: String,
        codeCommit: String,
        entities: List<String>,       // JSON lines
        claims: List<String>,         // JSON lines
        events: List<String>,         // JSON lines
        evidenceManifest: List<String>,
        provenance: List<String>,
        methodology: String,
        analysisNotes: String = "",
        replicationNotes: String = "",
    ): ResearchManifest {
        outputDir.mkdirs()
        File(outputDir, "analysis").mkdirs()
        File(outputDir, "replication").mkdirs()

        // Write JSONL files
        val datasetFile = writeJsonl(outputDir, "dataset.jsonl", entities + claims + events)
        val entitiesFile = writeJsonl(outputDir, "entities.jsonl", entities)
        val claimsFile = writeJsonl(outputDir, "claims.jsonl", claims)
        val eventsFile = writeJsonl(outputDir, "events.jsonl", events)
        val evidenceFile = writeJsonl(outputDir, "evidence-manifest.jsonl", evidenceManifest)
        val provenanceFile = writeJsonl(outputDir, "provenance.jsonl", provenance)

        // Write methodology
        File(outputDir, "methodology.md").writeText(methodology)

        // Write analysis/replication notes
        if (analysisNotes.isNotBlank()) {
            File(outputDir, "analysis/notes.md").writeText(analysisNotes)
        }
        if (replicationNotes.isNotBlank()) {
            File(outputDir, "replication/notes.md").writeText(replicationNotes)
        }

        // Generate checksums
        val hashes = mapOf(
            "dataset.jsonl" to sha256(datasetFile),
            "entities.jsonl" to sha256(entitiesFile),
            "claims.jsonl" to sha256(claimsFile),
            "events.jsonl" to sha256(eventsFile),
            "evidence-manifest.jsonl" to sha256(evidenceFile),
            "provenance.jsonl" to sha256(provenanceFile),
        )

        val checksums = hashes.entries.joinToString("\n") { "${it.value}  ${it.key}" }
        File(outputDir, "checksums.txt").writeText(checksums + "\n")

        // Build manifest
        val manifest = ResearchManifest(
            datasetVersion = datasetVersion,
            methodologyVersion = methodologyVersion,
            codeCommit = codeCommit,
            datasetSha256 = hashes["dataset.jsonl"]!!,
            evidenceManifestSha256 = hashes["evidence-manifest.jsonl"]!!,
            provenanceSha256 = hashes["provenance.jsonl"]!!,
            claimsSha256 = hashes["claims.jsonl"]!!,
            eventsSha256 = hashes["events.jsonl"]!!,
            entitiesSha256 = hashes["entities.jsonl"]!!,
            generatedAt = java.time.Instant.now().toString(),
        )

        File(outputDir, "manifest.json").writeText(json.encodeToString(manifest))

        // README
        File(outputDir, "README.md").writeText(
            """
            |# Research Package — $datasetVersion
            |
            |## Verification
            |
            |1. Verify checksums: `sha256sum -c checksums.txt`
            |2. Verify manifest hashes match checksums.txt
            |3. Verify Merkle root against published checkpoint
            |
            |## Contents
            |
            |- `manifest.json` — Package metadata + integrity hashes
            |- `dataset.jsonl` — Complete dataset (entities + claims + events)
            |- `entities.jsonl` — Scientific entities
            |- `claims.jsonl` — Scientific claims
            |- `events.jsonl` — Scientific events
            |- `evidence-manifest.jsonl` — Evidence metadata (not raw files)
            |- `provenance.jsonl` — Provenance graph
            |- `methodology.md` — Research methodology
            |- `checksums.txt` — SHA-256 checksums for all data files
            |
            |## Mandato
            |
            |```
            |EVIDENCE ≠ GUILT
            |CLAIM ≠ CONVICTION
            |CORRELATION ≠ CAUSATION
            |AI OUTPUT ≠ FACT
            |```
            """.trimMargin(),
        )

        return manifest
    }

    private fun writeJsonl(dir: File, name: String, lines: List<String>): File {
        val file = File(dir, name)
        file.writeText(lines.joinToString("\n") + "\n")
        return file
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §66 — Offline Verifier
//
// A university can download the package and verify it
// WITHOUT trusting Elysium.
// ═══════════════════════════════════════════════════════════════════

class ResearchPackageVerifier {

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val file: String, val reason: String) : Result
    }

    fun verify(packageDirectory: File): Result {
        // 1. Read manifest
        val manifestFile = File(packageDirectory, "manifest.json")
        if (!manifestFile.exists()) {
            return Result.Invalid("manifest.json", "MANIFEST_MISSING")
        }

        val manifest = try {
            json.decodeFromString<ResearchManifest>(manifestFile.readText())
        } catch (e: Exception) {
            return Result.Invalid("manifest.json", "MANIFEST_PARSE_ERROR: ${e.message}")
        }

        // 2. Verify each file hash against manifest
        val fileHashes = mapOf(
            "dataset.jsonl" to manifest.datasetSha256,
            "entities.jsonl" to manifest.entitiesSha256,
            "claims.jsonl" to manifest.claimsSha256,
            "events.jsonl" to manifest.eventsSha256,
            "evidence-manifest.jsonl" to manifest.evidenceManifestSha256,
            "provenance.jsonl" to manifest.provenanceSha256,
        )

        for ((fileName, expectedHash) in fileHashes) {
            val file = File(packageDirectory, fileName)
            if (!file.exists()) {
                return Result.Invalid(fileName, "FILE_MISSING")
            }

            val actualHash = sha256(file)
            if (actualHash != expectedHash) {
                return Result.Invalid(
                    fileName,
                    "HASH_MISMATCH expected=$expectedHash actual=$actualHash",
                )
            }
        }

        // 3. Verify checksums.txt consistency
        val checksumsFile = File(packageDirectory, "checksums.txt")
        if (checksumsFile.exists()) {
            for (line in checksumsFile.readLines()) {
                if (line.isBlank()) continue
                val parts = line.split("  ", limit = 2)
                if (parts.size != 2) continue
                val (hash, name) = parts
                val file = File(packageDirectory, name)
                if (file.exists()) {
                    val actual = sha256(file)
                    if (actual != hash) {
                        return Result.Invalid(name, "CHECKSUM_MISMATCH")
                    }
                }
            }
        }

        return Result.Valid
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

// ═══════════════════════════════════════════════════════════════════
// §83 — Legal Referral Package
//
// When an investigation is mature:
//   Research → Research Package → Legal Referral Package
//
// The package contains facts, claims, evidence, provenance,
// timeline, authority, duty, actions, contradictions,
// limitations, replications, publication.
//
// NEVER: "arrest this person"
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class LegalReferralPackage(
    val id: String,
    val researchPackageId: String,

    val facts: List<String>,
    val claims: List<String>,
    val evidenceSummary: List<String>,
    val provenanceSummary: String,
    val timeline: List<String>,
    val authorityAssertions: List<String>,
    val dutyAssertions: List<String>,
    val documentedActions: List<String>,
    val documentedNonActions: List<String>,
    val contradictions: List<String>,
    val limitations: List<String>,
    val replications: List<String>,
    val publicationId: String?,

    val disclaimer: String = LEGAL_REFERRAL_DISCLAIMER,

    val generatedAt: String,
) {
    companion object {
        const val LEGAL_REFERRAL_DISCLAIMER =
            "This referral package presents documented evidence, " +
                "scientific claims, and analysis results. It does NOT " +
                "constitute a legal determination of guilt, liability, " +
                "or criminal responsibility. EVIDENCE ≠ GUILT. " +
                "CLAIM ≠ CONVICTION. AI OUTPUT ≠ FACT. " +
                "Legal qualification and judicial determination are " +
                "the exclusive province of competent legal authorities."
    }
}
