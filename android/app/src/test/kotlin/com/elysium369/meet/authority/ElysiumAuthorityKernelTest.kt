package com.elysium369.meet.authority

import com.elysium369.meet.identity.ActivePrincipal
import com.elysium369.meet.identity.ActivePrincipalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ElysiumAuthorityKernelTest {

    private var activeUser = ActivePrincipal.authenticated("user_alice_123")
    private val fakePrincipalProvider = object : ActivePrincipalProvider {
        override fun current(): ActivePrincipal = activeUser
    }

    private lateinit var kernel: ElysiumAuthorityKernel

    @Before
    fun setUp() {
        activeUser = ActivePrincipal.authenticated("user_alice_123")
        kernel = ElysiumAuthorityKernel(fakePrincipalProvider)
    }

    @Test
    fun `admitFact allows monotonic progression from CLIENT_RECORDED to PHYSICALLY_VERIFIED to SERVER_AUTHORITATIVE`() {
        val subjectId = "vehicle:101:vin"

        // 1. Initial client recorded fact
        val fact1 = FactEnvelope(
            domain = "vehicle",
            subjectId = subjectId,
            payload = "1HGCR2F83HA000001",
            authority = AuthorityOwner.Principal("user_alice_123"),
            provenance = "manual_user_input",
            freshness = Freshness(timestampEpochMs = 1000L, monotonicSequence = 1L),
            confidence = 60.0,
            verificationLevel = VerificationLevel.CLIENT_RECORDED,
            policy = MutationPolicy.OWNER_ONLY,
        )

        val result1 = kernel.admitFact(fact1)
        assertTrue(result1 is FactAdmissionResult.Accepted)
        assertEquals(VerificationLevel.CLIENT_RECORDED, (result1 as FactAdmissionResult.Accepted).level)

        // 2. Hardware scan verifies the VIN physically
        val fact2 = FactEnvelope(
            domain = "vehicle",
            subjectId = subjectId,
            payload = "1HGCR2F83HA000001",
            authority = AuthorityOwner.Principal("user_alice_123"),
            provenance = "obd_did_F190",
            freshness = Freshness(timestampEpochMs = 2000L, monotonicSequence = 2L),
            confidence = 98.0,
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            policy = MutationPolicy.OWNER_ONLY,
        )

        val result2 = kernel.admitFact(fact2)
        assertTrue(result2 is FactAdmissionResult.Accepted)
        assertEquals(VerificationLevel.PHYSICALLY_VERIFIED, (result2 as FactAdmissionResult.Accepted).level)

        // 3. Backend verifies registration
        val fact3 = FactEnvelope(
            domain = "vehicle",
            subjectId = subjectId,
            payload = "1HGCR2F83HA000001",
            authority = AuthorityOwner.Principal("user_alice_123"),
            provenance = "dmv_registry_rpc",
            freshness = Freshness(timestampEpochMs = 3000L, monotonicSequence = 3L),
            confidence = 100.0,
            verificationLevel = VerificationLevel.SERVER_AUTHORITATIVE,
            policy = MutationPolicy.OWNER_ONLY,
        )

        val result3 = kernel.admitFact(fact3)
        assertTrue(result3 is FactAdmissionResult.Accepted)
        assertEquals(VerificationLevel.SERVER_AUTHORITATIVE, (result3 as FactAdmissionResult.Accepted).level)

        // Verify stored level
        val stored = kernel.getFact<String>(subjectId)
        assertNotNull(stored)
        assertEquals(VerificationLevel.SERVER_AUTHORITATIVE, stored!!.verificationLevel)
    }

    @Test
    fun `admitFact rejects downgrade of verification level`() {
        val subjectId = "vehicle:101:odometer"

        // Hardware physical reading: 125,000 km
        val verifiedFact = FactEnvelope(
            domain = "vehicle",
            subjectId = subjectId,
            payload = 125000,
            authority = AuthorityOwner.DiagnosticTool("elm327", "CAN"),
            provenance = "ecu_did_01A6",
            freshness = Freshness(timestampEpochMs = 1000L, monotonicSequence = 1L),
            confidence = 99.0,
            verificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
            policy = MutationPolicy.OWNER_ONLY,
        )
        val res1 = kernel.admitFact(verifiedFact)
        assertTrue(res1 is FactAdmissionResult.Accepted)

        // Untrusted claim trying to lower odometer to 80,000 km
        val fraudulentClaim = FactEnvelope(
            domain = "vehicle",
            subjectId = subjectId,
            payload = 80000,
            authority = AuthorityOwner.DiagnosticTool("elm327", "CAN"),
            provenance = "manual_override",
            freshness = Freshness(timestampEpochMs = 2000L, monotonicSequence = 2L),
            confidence = 20.0,
            verificationLevel = VerificationLevel.UNVERIFIED_CLAIM,
            policy = MutationPolicy.OWNER_ONLY,
        )
        val res2 = kernel.admitFact(fraudulentClaim)
        assertTrue(res2 is FactAdmissionResult.Rejected)
        assertEquals("REJECTED_VERIFICATION_DOWNGRADE", (res2 as FactAdmissionResult.Rejected).rejectionCode)

        // Value remains the verified odometer
        assertEquals(125000, kernel.getFact<Int>(subjectId)!!.payload)
    }

    @Test
    fun `admitFact rejects corrupted or tampered integrity hash`() {
        val subjectId = "ride:202:fare"
        val fact = FactEnvelope(
            domain = "ride",
            subjectId = subjectId,
            payload = "150.00",
            authority = AuthorityOwner.SystemAuthority,
            provenance = "metered_fare_kernel",
            freshness = Freshness(timestampEpochMs = 5000L, monotonicSequence = 1L),
            confidence = 100.0,
            verificationLevel = VerificationLevel.SERVER_AUTHORITATIVE,
            policy = MutationPolicy.SYSTEM_AUTHORITATIVE,
            integrityHash = "bad_forged_hash_12345",
        )

        val result = kernel.admitFact(fact)
        assertTrue(result is FactAdmissionResult.Rejected)
        assertEquals("CORRUPTED_INTEGRITY_HASH", (result as FactAdmissionResult.Rejected).rejectionCode)
    }

    @Test
    fun `admitFact rejects unauthorized self mutation`() {
        val subjectId = "driver:profile:rating"
        val fact = FactEnvelope(
            domain = "identity",
            subjectId = subjectId,
            payload = "5.0",
            authority = AuthorityOwner.Principal("user_bob_456"),
            provenance = "user_edit",
            freshness = Freshness(timestampEpochMs = 1000L, monotonicSequence = 1L),
            confidence = 50.0,
            verificationLevel = VerificationLevel.CLIENT_RECORDED,
            policy = MutationPolicy.SELF_ONLY,
        )
        kernel.admitFact(fact)

        // Current active user is "user_alice_123", who tries to mutate Bob's fact
        val attackerMutation = FactEnvelope(
            domain = "identity",
            subjectId = subjectId,
            payload = "1.0",
            authority = AuthorityOwner.Principal("user_bob_456"),
            provenance = "user_edit",
            freshness = Freshness(timestampEpochMs = 2000L, monotonicSequence = 2L),
            confidence = 50.0,
            verificationLevel = VerificationLevel.CLIENT_RECORDED,
            policy = MutationPolicy.SELF_ONLY,
        )

        val result = kernel.admitFact(attackerMutation)
        assertTrue(result is FactAdmissionResult.Rejected)
        assertEquals("UNAUTHORIZED_SELF_MUTATION", (result as FactAdmissionResult.Rejected).rejectionCode)
    }

    @Test
    fun `admitFact rejects overwrite on IMMUTABLE_ONCE_WRITTEN fact`() {
        val subjectId = "certificate:999:hash"
        val immutableFact = FactEnvelope(
            domain = "legal",
            subjectId = subjectId,
            payload = "sha256_e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            authority = AuthorityOwner.ForensicVerifier("dekra_auditor_1", "cert_key_9"),
            provenance = "forensic_signed_pdf",
            freshness = Freshness(timestampEpochMs = 1000L, monotonicSequence = 1L),
            confidence = 100.0,
            verificationLevel = VerificationLevel.FORENSICALLY_CERTIFIED,
            policy = MutationPolicy.IMMUTABLE_ONCE_WRITTEN,
        )

        val res1 = kernel.admitFact(immutableFact)
        assertTrue(res1 is FactAdmissionResult.Accepted)

        val overwriteAttempt = FactEnvelope(
            domain = "legal",
            subjectId = subjectId,
            payload = "sha256_tampered",
            authority = AuthorityOwner.ForensicVerifier("dekra_auditor_1", "cert_key_9"),
            provenance = "forensic_signed_pdf",
            freshness = Freshness(timestampEpochMs = 2000L, monotonicSequence = 2L),
            confidence = 100.0,
            verificationLevel = VerificationLevel.FORENSICALLY_CERTIFIED,
            policy = MutationPolicy.IMMUTABLE_ONCE_WRITTEN,
        )

        val res2 = kernel.admitFact(overwriteAttempt)
        assertTrue(res2 is FactAdmissionResult.Rejected)
        assertEquals("IMMUTABLE_FACT_OVERWRITE_DENIED", (res2 as FactAdmissionResult.Rejected).rejectionCode)
    }
}
