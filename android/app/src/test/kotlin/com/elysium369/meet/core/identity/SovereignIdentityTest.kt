package com.elysium369.meet.core.identity

import org.junit.Assert.*
import org.junit.Test

class SovereignIdentityTest {

    private fun engine(): SovereignIdentityEngine {
        val e = SovereignIdentityEngine()
        e.createIdentity("fp-maria-abc123", "María López")
        e.createIdentity("fp-jose-def456", "José García")
        return e
    }

    @Test
    fun `create sovereign identity with DID`() {
        val e = SovereignIdentityEngine()
        val wallet = e.createIdentity("fp-test-123", "Test User")
        assertEquals("did:elysium:fp-test-123", wallet.did.uri)
    }

    @Test
    fun `DID short form truncates for display`() {
        val did = SovereignDID(publicKeyFingerprint = "abcdef1234567890xyz")
        assertEquals("abcdef12…0xyz", did.shortForm)
    }

    @Test
    fun `issue credential and add to subject wallet`() {
        val e = engine()
        val cred = e.issueCredential(
            "did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.SKILL_VERIFIED,
            "José es plomero verificado", "plumbing",
        )
        assertNotNull(cred)
        assertTrue(cred!!.isValid)
        val wallet = e.getWallet("did:elysium:fp-jose-def456")!!
        assertEquals(1, wallet.totalCredentials)
    }

    @Test
    fun `credential has chain hash for tamper detection`() {
        val e = engine()
        val cred1 = e.issueCredential(
            "did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.SKILL_VERIFIED, "Skill 1", "plumbing",
        )!!
        val cred2 = e.issueCredential(
            "did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.JOB_COMPLETED, "Job 1", "plumbing",
        )!!
        // Chain hashes should be different (chain builds)
        assertNotEquals(cred1.chainHash, cred2.chainHash)
    }

    @Test
    fun `verify credential offline without server`() {
        val e = engine()
        val cred = e.issueCredential(
            "did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.SKILL_VERIFIED, "Verified plumber", "plumbing",
        )!!
        val result = e.verifyCredential(cred)
        assertTrue(result.isValid)
        assertTrue(result.reason.contains("verified"))
    }

    @Test
    fun `revoked credential fails verification`() {
        val e = engine()
        val cred = e.issueCredential(
            "did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.SKILL_VERIFIED, "Skill", "domain",
        )!!
        e.revokeCredential(cred.credentialId)
        val result = e.verifyCredential(cred)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("revoked"))
    }

    @Test
    fun `selective disclosure presentation filters by domain`() {
        val e = engine()
        e.issueCredential("did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.SKILL_VERIFIED, "Plumbing", "plumbing")
        e.issueCredential("did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.SKILL_VERIFIED, "Electrical", "electrical")
        val presentation = e.createPresentation(
            "did:elysium:fp-jose-def456", "Job application",
        ) { it.domain == "plumbing" }
        assertNotNull(presentation)
        assertEquals(1, presentation!!.credentialCount)
    }

    @Test
    fun `export wallet for portability — RIGHT TO LEAVE`() {
        val e = engine()
        e.issueCredential("did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.JOB_COMPLETED, "Completed brake job", "mechanical")
        val exported = e.exportWallet("did:elysium:fp-jose-def456")
        assertNotNull(exported)
        assertEquals(1, exported!!.totalCredentials)
    }

    @Test
    fun `import wallet from another platform`() {
        val e1 = engine()
        e1.issueCredential("did:elysium:fp-maria-abc123", "did:elysium:fp-jose-def456",
            CredentialType.SKILL_VERIFIED, "Plumber", "plumbing")
        val exported = e1.exportWallet("did:elysium:fp-jose-def456")!!

        val e2 = SovereignIdentityEngine()
        assertTrue(e2.importWallet(exported))
        assertEquals(1, e2.getWallet(exported.did.uri)!!.totalCredentials)
    }

    @Test
    fun `all 8 credential types exist`() {
        assertEquals(8, CredentialType.entries.size)
    }
}
