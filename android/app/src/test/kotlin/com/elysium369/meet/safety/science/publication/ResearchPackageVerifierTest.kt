package com.elysium369.meet.safety.science.publication

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * §66 — Verify research packages offline.
 * §94 — Adversarial integrity tests (tampered files, missing files, etc.)
 */
class ResearchPackageVerifierTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private val exporter = ResearchPackageExporter()
    private val verifier = ResearchPackageVerifier()

    private fun createValidPackage(): File {
        val dir = tmpDir.newFolder("research-pkg")
        exporter.export(
            outputDir = dir,
            datasetVersion = "2026.10.04.1",
            methodologyVersion = "safety-science-v1",
            codeCommit = "abc123",
            entities = listOf("""{"id":"e1","type":"PERSON"}"""),
            claims = listOf("""{"id":"c1","proposition":"test"}"""),
            events = listOf("""{"id":"ev1","type":"INCIDENT"}"""),
            evidenceManifest = listOf("""{"id":"x1","hash":"sha256:..."}"""),
            provenance = listOf("""{"from":"e1","to":"c1","rel":"DERIVED_FROM"}"""),
            methodology = "# Test methodology\nControlled experiment.",
        )
        return dir
    }

    @Test
    fun `valid package passes verification`() {
        val dir = createValidPackage()
        val result = verifier.verify(dir)
        assertTrue("Valid package should pass", result is ResearchPackageVerifier.Result.Valid)
    }

    @Test
    fun `missing manifest fails verification`() {
        val dir = tmpDir.newFolder("empty-pkg")
        val result = verifier.verify(dir)
        assertTrue(result is ResearchPackageVerifier.Result.Invalid)
        assertEquals("manifest.json", (result as ResearchPackageVerifier.Result.Invalid).file)
        assertEquals("MANIFEST_MISSING", result.reason)
    }

    @Test
    fun `tampered dataset file fails verification`() {
        val dir = createValidPackage()

        // §94 Dataset E — alter one byte
        val dataset = File(dir, "dataset.jsonl")
        dataset.writeText(dataset.readText() + "TAMPERED")

        val result = verifier.verify(dir)
        assertTrue("Tampered file must fail", result is ResearchPackageVerifier.Result.Invalid)
        assertEquals("dataset.jsonl", (result as ResearchPackageVerifier.Result.Invalid).file)
        assertTrue(result.reason.contains("HASH_MISMATCH"))
    }

    @Test
    fun `tampered claims file fails verification`() {
        val dir = createValidPackage()
        File(dir, "claims.jsonl").writeText("FABRICATED CLAIM")

        val result = verifier.verify(dir)
        assertTrue(result is ResearchPackageVerifier.Result.Invalid)
        assertEquals("claims.jsonl", (result as ResearchPackageVerifier.Result.Invalid).file)
    }

    @Test
    fun `tampered events file fails verification`() {
        val dir = createValidPackage()
        File(dir, "events.jsonl").writeText("ALTERED EVENT TIMELINE")

        val result = verifier.verify(dir)
        assertTrue(result is ResearchPackageVerifier.Result.Invalid)
        assertEquals("events.jsonl", (result as ResearchPackageVerifier.Result.Invalid).file)
    }

    @Test
    fun `missing evidence manifest fails verification`() {
        val dir = createValidPackage()
        File(dir, "evidence-manifest.jsonl").delete()

        val result = verifier.verify(dir)
        assertTrue(result is ResearchPackageVerifier.Result.Invalid)
        assertEquals("evidence-manifest.jsonl", (result as ResearchPackageVerifier.Result.Invalid).file)
        assertEquals("FILE_MISSING", result.reason)
    }

    @Test
    fun `tampered provenance fails verification`() {
        val dir = createValidPackage()
        File(dir, "provenance.jsonl").writeText("FABRICATED PROVENANCE")

        val result = verifier.verify(dir)
        assertTrue(result is ResearchPackageVerifier.Result.Invalid)
        assertEquals("provenance.jsonl", (result as ResearchPackageVerifier.Result.Invalid).file)
    }

    @Test
    fun `corrupted manifest json fails verification`() {
        val dir = createValidPackage()
        File(dir, "manifest.json").writeText("{broken json!!")

        val result = verifier.verify(dir)
        assertTrue(result is ResearchPackageVerifier.Result.Invalid)
        assertTrue(
            (result as ResearchPackageVerifier.Result.Invalid)
                .reason.contains("MANIFEST_PARSE_ERROR"),
        )
    }
}
