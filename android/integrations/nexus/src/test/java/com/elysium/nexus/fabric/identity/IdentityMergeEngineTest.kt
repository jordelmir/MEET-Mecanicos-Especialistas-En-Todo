package com.elysium.nexus.fabric.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityMergeEngineTest {

    private fun obs(
        source: String,
        evidence: List<PeerIdentityEvidence> = emptyList(),
        manufacturer: String? = null,
        model: String? = null,
        displayName: String? = null,
        ip: String? = null
    ) = PeerObservation(
        source = source,
        manufacturer = manufacturer,
        model = model,
        displayName = displayName,
        ipAddress = ip,
        evidence = evidence
    )

    // ── SAME ─────────────────────────────────────────────────────────────

    @Test
    fun `same UDN from ssdp and mdns is the same physical device`() {
        val mdns = obs("mdns", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        val ssdp = obs("ssdp", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        assertEquals(IdentityMergeResult.SamePhysicalDevice, IdentityMergeEngine.merge(mdns, ssdp))
    }

    @Test
    fun `pairing identity match is the same device`() {
        val a = obs("pairing", listOf(PeerIdentityEvidence(IdentityEvidenceKind.PairingIdentity, "paired-42")))
        val b = obs("webos", listOf(PeerIdentityEvidence(IdentityEvidenceKind.PairingIdentity, "paired-42")))
        assertEquals(IdentityMergeResult.SamePhysicalDevice, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `certificate fingerprint match is the same device`() {
        val a = obs("tls", listOf(PeerIdentityEvidence(IdentityEvidenceKind.CertificateFingerprint, "fp-1")))
        val b = obs("tls", listOf(PeerIdentityEvidence(IdentityEvidenceKind.CertificateFingerprint, "fp-1")))
        assertEquals(IdentityMergeResult.SamePhysicalDevice, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `manufacturer stable uuid match is the same device even with different ips`() {
        val a = obs("webos", listOf(PeerIdentityEvidence(IdentityEvidenceKind.ManufacturerStableUuid, "svc-uuid-9")), ip = "192.168.1.7")
        val b = obs("webos", listOf(PeerIdentityEvidence(IdentityEvidenceKind.ManufacturerStableUuid, "svc-uuid-9")), ip = "192.168.1.44")
        assertEquals(IdentityMergeResult.SamePhysicalDevice, IdentityMergeEngine.merge(a, b))
    }

    // ── DIFFERENT ────────────────────────────────────────────────────────

    @Test
    fun `different UDNs are different devices`() {
        val a = obs("ssdp", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        val b = obs("ssdp", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:xyz")))
        assertEquals(IdentityMergeResult.DifferentPhysicalDevice, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `same model and same name but different certificates are different devices`() {
        val a = obs("tls", listOf(PeerIdentityEvidence(IdentityEvidenceKind.CertificateFingerprint, "fp-1")), manufacturer = "LG", model = "OLED55C3", displayName = "LG TV")
        val b = obs("tls", listOf(PeerIdentityEvidence(IdentityEvidenceKind.CertificateFingerprint, "fp-2")), manufacturer = "LG", model = "OLED55C3", displayName = "LG TV")
        assertEquals(IdentityMergeResult.DifferentPhysicalDevice, IdentityMergeEngine.merge(a, b))
    }

    // ── AMBIGUOUS (never merge/invent) ───────────────────────────────────

    @Test
    fun `same ip alone is ambiguous — ip is never identity`() {
        val a = obs("webos", emptyList(), ip = "192.168.1.7", manufacturer = "LG", model = "OLED55C3")
        val b = obs("webos", emptyList(), ip = "192.168.1.7", manufacturer = "LG", model = "OLED55C3")
        assertEquals(IdentityMergeResult.Ambiguous, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `same model and same name alone are ambiguous`() {
        val a = obs("mdns", emptyList(), manufacturer = "LG", model = "OLED55C3", displayName = "LG TV")
        val b = obs("ssdp", emptyList(), manufacturer = "LG", model = "OLED55C3", displayName = "LG TV")
        assertEquals(IdentityMergeResult.Ambiguous, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `one side without strong evidence is ambiguous even when other side has it`() {
        val a = obs("webos", listOf(PeerIdentityEvidence(IdentityEvidenceKind.ManufacturerStableUuid, "svc-uuid-9")))
        val b = obs("ir-profile", emptyList(), manufacturer = "LG", model = "OLED55C3")
        assertEquals(IdentityMergeResult.Ambiguous, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `same value under different kinds is different — kind must match, false merges never happen`() {
        val a = obs("ssdp", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "abc-123")))
        val b = obs("pairing", listOf(PeerIdentityEvidence(IdentityEvidenceKind.PairingIdentity, "abc-123")))
        assertEquals(IdentityMergeResult.DifferentPhysicalDevice, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `deterministic composite alone never merges`() {
        val a = obs("mdns", listOf(PeerIdentityEvidence(IdentityEvidenceKind.DeterministicComposite, "composite:a")))
        val b = obs("ssdp", listOf(PeerIdentityEvidence(IdentityEvidenceKind.DeterministicComposite, "composite:a")))
        assertEquals(IdentityMergeResult.Ambiguous, IdentityMergeEngine.merge(a, b))
    }

    @Test
    fun `empty observations are ambiguous`() {
        assertEquals(
            IdentityMergeResult.Ambiguous,
            IdentityMergeEngine.merge(obs("mdns"), obs("ssdp"))
        )
    }

    // ── mergeAll ─────────────────────────────────────────────────────────

    @Test
    fun `mergeAll agrees same when every pair agrees`() {
        val o1 = obs("mdns", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        val o2 = obs("ssdp", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        val o3 = obs("webos", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        assertEquals(IdentityMergeResult.SamePhysicalDevice, IdentityMergeEngine.mergeAll(listOf(o1, o2, o3)))
    }

    @Test
    fun `mergeAll is different when any pair contradicts`() {
        val o1 = obs("mdns", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        val o2 = obs("ssdp", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        val o3 = obs("webos", listOf(PeerIdentityEvidence(IdentityEvidenceKind.PairingIdentity, "paired-42")))
        assertEquals(IdentityMergeResult.DifferentPhysicalDevice, IdentityMergeEngine.mergeAll(listOf(o1, o2, o3)))
    }

    @Test
    fun `mergeAll is ambiguous when any pair lacks evidence`() {
        val o1 = obs("mdns", listOf(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")))
        val o2 = obs("ssdp", emptyList(), ip = "192.168.1.7")
        assertEquals(IdentityMergeResult.Ambiguous, IdentityMergeEngine.mergeAll(listOf(o1, o2)))
        assertEquals(IdentityMergeResult.Ambiguous, IdentityMergeEngine.mergeAll(listOf(o1)))
    }

    // ── resolveIdentity ──────────────────────────────────────────────────

    @Test
    fun `resolveIdentity picks highest priority strong evidence as stable id`() {
        val observation = obs(
            "mdns",
            evidence = listOf(
                PeerIdentityEvidence(IdentityEvidenceKind.VendorDeviceId, "vendor-7"),
                PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, "uuid:abc")
            ),
            displayName = "LG TV"
        )
        val identity = observation.resolveIdentity()
        assertEquals("uuid:abc", identity.stableId)
        assertFalse(identity.compositeFallback)
        assertEquals("LG TV", identity.label)
    }

    @Test
    fun `resolveIdentity falls back to deterministic composite without strong evidence`() {
        val observation = obs(
            "mdns",
            manufacturer = "LG",
            model = "OLED55C3",
            displayName = "LG TV"
        )
        val identity = observation.resolveIdentity()
        assertTrue(identity.stableId.startsWith("composite:"))
        assertTrue(identity.compositeFallback)
    }

    @Test
    fun `resolveIdentity never uses ip as identity`() {
        val observation = obs("webos", ip = "192.168.1.7", displayName = "LG TV")
        val identity = observation.resolveIdentity()
        assertFalse(identity.stableId.contains("192.168.1.7"))
        assertTrue(identity.compositeFallback)
    }

    @Test
    fun `composite is deterministic for identical hints`() {
        val a = PeerIdentity.composite("LG", "OLED55C3", null)
        val b = PeerIdentity.composite("LG", "OLED55C3", null)
        assertEquals(a, b)
        assertTrue(a.startsWith("composite:"))
    }
}