package com.elysium.nexus.fabric.infrared.database

import org.json.JSONObject

/**
 * V06-PTG-01 §2/§9 + V06.1 Phase 0.1: Catalog manifest identity — pure, JVM-testable.
 *
 * The packaged `ir_catalog.manifest.json` is the SINGLE authority for what a
 * catalog build is. No duplicated hash constants are allowed in code: the
 * installed database must match the manifest's `databaseSha256`, and every
 * artifact (db, manifest, stats, rejections) carries the same `catalogBuildId`.
 *
 * Parser: `org.json.JSONObject` (platform class on Android, reference
 * implementation on local JVM tests). The real manifest carries nested
 * objects (`counts`, allowed future extensions such as `stats`); a security
 * manifest is not a place for a proprietary JSON parser. Every identity field
 * is type-checked explicitly — fail-closed on any missing, blank, or
 * wrong-typed value.
 */
object CatalogManifest {

    const val MIN_SCHEMA_VERSION = 5

    /** Policy version participating in catalogBuildId (kept in sync with the Python builder). */
    const val BUILD_ID_POLICY_VERSION = "v0.6-ptg-1"
    const val BUILD_ID_PREFIX = "ptg-v1"

    /** Required top-level identity keys; a manifest missing any of them is not authoritative. */
    val REQUIRED_KEYS: List<String> = listOf(
        "catalogBuildId",
        "schemaVersion",
        "databaseSha256",
        "canonicalContentSha256",
        "sourceLockSha256",
        "rejectionManifestSha256",
        "licenseManifestSha256",
        "policyVersion"
    )

    data class CatalogMetadata(
        val catalogBuildId: String,
        val schemaVersion: Int,
        val databaseSha256: String,
        val canonicalContentSha256: String,
        val sourceLockSha256: String,
        val rejectionManifestSha256: String,
        val licenseManifestSha256: String,
        val policyVersion: String
    )

    sealed interface ParseResult {
        data class Success(val metadata: CatalogMetadata) : ParseResult
        data class Failure(val reason: String) : ParseResult
    }

    /**
     * Strict schema gate (§2 metadata compatibility): the manifest is the
     * authority, therefore a missing or too-old schema is a hard rejection —
     * there is no legacy hash fallback anymore.
     */
    fun isSchemaVersionAccepted(declaredSchemaVersion: Int?): Boolean =
        declaredSchemaVersion != null && declaredSchemaVersion >= MIN_SCHEMA_VERSION

    fun parse(jsonText: String): ParseResult {
        val root = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return ParseResult.Failure("manifest is not valid JSON: ${e.javaClass.simpleName}")
        }

        val missing = REQUIRED_KEYS.filter { !root.has(it) }
        if (missing.isNotEmpty()) {
            return ParseResult.Failure("manifest missing required identity fields: ${missing.joinToString()}")
        }

        val buildId = root.optString("catalogBuildId").trim()
        if (buildId.isEmpty()) return ParseResult.Failure("catalogBuildId is empty")

        val schemaVersion: Int = root.opt("schemaVersion") as? Int
            ?: return ParseResult.Failure("schemaVersion is not an integer")

        fun nonBlankString(key: String): String {
            val raw = root.optString(key).trim()
            if (raw.isEmpty()) {
                throw IllegalArgumentException("$key is blank")
            }
            return raw
        }

        val fields = try {
            mapOf(
                "databaseSha256" to nonBlankString("databaseSha256"),
                "canonicalContentSha256" to nonBlankString("canonicalContentSha256"),
                "sourceLockSha256" to nonBlankString("sourceLockSha256"),
                "rejectionManifestSha256" to nonBlankString("rejectionManifestSha256"),
                "licenseManifestSha256" to nonBlankString("licenseManifestSha256"),
                "policyVersion" to nonBlankString("policyVersion")
            )
        } catch (e: IllegalArgumentException) {
            return ParseResult.Failure("manifest hashes must be non-blank: ${e.message}")
        }

        return ParseResult.Success(
            CatalogMetadata(
                catalogBuildId = buildId,
                schemaVersion = schemaVersion,
                databaseSha256 = fields.getValue("databaseSha256"),
                canonicalContentSha256 = fields.getValue("canonicalContentSha256"),
                sourceLockSha256 = fields.getValue("sourceLockSha256"),
                rejectionManifestSha256 = fields.getValue("rejectionManifestSha256"),
                licenseManifestSha256 = fields.getValue("licenseManifestSha256"),
                policyVersion = fields.getValue("policyVersion")
            )
        )
    }
}