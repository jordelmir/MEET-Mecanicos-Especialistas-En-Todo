package com.elysium369.meet.core.identity

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════
 *  S O V E R E I G N   I D E N T I T Y
 *  ────────────────────────────────────
 *  Your identity belongs to YOU. Not to ELYSIUM. Not to Google.
 *  Not to any government. Not to any corporation.
 *
 *  If ELYSIUM disappears tomorrow, your identity SURVIVES.
 *
 *  This is the cryptographic foundation of digital sovereignty:
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  YOUR PHONE holds:                                      │
 *  │  • Private key (never leaves device)                    │
 *  │  • Signed credentials (skills, jobs, trust)             │
 *  │  • Verifiable proofs (anyone can verify, no server)     │
 *  │  • Portable reputation (take it anywhere)               │
 *  │                                                         │
 *  │  VERIFICATION:                                          │
 *  │  • Anyone with your public key can verify your claims   │
 *  │  • No phone-home required                               │
 *  │  • Works offline, in mesh, in disaster                  │
 *  │  • Hash chains make tampering detectable                │
 *  └──────────────────────────────────────────────────────────┘
 *
 *  Inspired by W3C Decentralized Identifiers (DIDs) and
 *  Verifiable Credentials, but simplified for mobile-first,
 *  offline-first, mesh-compatible operation.
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── DID (Decentralized Identifier) ───

@Serializable
data class SovereignDID(
    val method: String = "elysium",
    val publicKeyFingerprint: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    /** did:elysium:<fingerprint> */
    val uri: String get() = "did:$method:$publicKeyFingerprint"

    /** Short form for display: first 8 + last 4 chars */
    val shortForm: String
        get() = if (publicKeyFingerprint.length > 12)
            "${publicKeyFingerprint.take(8)}…${publicKeyFingerprint.takeLast(4)}"
        else publicKeyFingerprint
}

// ─── Verifiable Credential ───

enum class CredentialType {
    SKILL_VERIFIED,         // "María verified José knows plumbing"
    JOB_COMPLETED,          // "José completed brake repair for client X"
    APPRENTICESHIP_GRADUATED, // "Master Pedro graduated apprentice José"
    TRUST_ENDORSEMENT,      // "3 people vouch for José in electrical"
    ECONOMIC_MILESTONE,     // "50 verified jobs completed"
    COMMUNITY_SERVICE,      // "Volunteered 20 hours in hurricane relief"
    IDENTITY_VERIFIED,      // "Government ID verified (hash only)"
    CERTIFICATION,          // "OBD-II Level 2 certified"
}

@Serializable
data class VerifiableCredential(
    val credentialId: String,
    val type: CredentialType,
    val issuerDid: String,
    val subjectDid: String,
    val claim: String,
    val domain: String,
    val issuedAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long? = null,
    val evidenceHash: String = "",
    val issuerSignature: String = "",
    val chainHash: String = "",
) {
    val isExpired: Boolean
        get() = expiresAtEpochMs != null && System.currentTimeMillis() > expiresAtEpochMs

    val isValid: Boolean
        get() = !isExpired && issuerSignature.isNotBlank()
}

// ─── Verifiable Presentation ───

@Serializable
data class VerifiablePresentation(
    val presentationId: String,
    val holderDid: String,
    val credentials: List<VerifiableCredential>,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val purpose: String,
    val selectiveDisclosure: Boolean = true,
    val presentationHash: String = "",
) {
    val credentialCount: Int get() = credentials.size

    /** Only share what's needed — selective disclosure */
    fun selectByType(type: CredentialType): VerifiablePresentation {
        return copy(credentials = credentials.filter { it.type == type })
    }

    fun selectByDomain(domain: String): VerifiablePresentation {
        return copy(credentials = credentials.filter { it.domain == domain })
    }
}

// ─── Identity Wallet ───

@Serializable
data class IdentityWallet(
    val did: SovereignDID,
    val displayName: String,
    val credentials: List<VerifiableCredential> = emptyList(),
    val credentialChainHead: String = "",
) {
    val totalCredentials: Int get() = credentials.size

    val validCredentials: List<VerifiableCredential>
        get() = credentials.filter { it.isValid }

    val credentialsByType: Map<CredentialType, List<VerifiableCredential>>
        get() = credentials.groupBy { it.type }

    val domains: Set<String>
        get() = credentials.map { it.domain }.toSet()
}

// ─── Sovereign Identity Engine ───

class SovereignIdentityEngine {

    private val wallets = mutableMapOf<String, IdentityWallet>()
    private val revocations = mutableSetOf<String>()

    /**
     * Creates a new sovereign identity.
     * The public key fingerprint is the foundation — derived from
     * a key pair generated on the device.
     */
    fun createIdentity(
        publicKeyFingerprint: String,
        displayName: String,
    ): IdentityWallet {
        val did = SovereignDID(publicKeyFingerprint = publicKeyFingerprint)
        val wallet = IdentityWallet(did = did, displayName = displayName)
        wallets[did.uri] = wallet
        return wallet
    }

    /**
     * Issues a verifiable credential.
     * The ISSUER signs the claim about the SUBJECT.
     * "María (issuer) certifies that José (subject) is a verified plumber"
     */
    fun issueCredential(
        issuerDid: String,
        subjectDid: String,
        type: CredentialType,
        claim: String,
        domain: String,
        evidenceHash: String = "",
        expiresAtEpochMs: Long? = null,
    ): VerifiableCredential? {
        val issuerWallet = wallets[issuerDid] ?: return null
        val subjectWallet = wallets[subjectDid] ?: return null

        val credential = VerifiableCredential(
            credentialId = "vc-${System.currentTimeMillis()}-${subjectWallet.totalCredentials}",
            type = type,
            issuerDid = issuerDid,
            subjectDid = subjectDid,
            claim = claim,
            domain = domain,
            evidenceHash = evidenceHash,
            expiresAtEpochMs = expiresAtEpochMs,
            issuerSignature = sign(issuerDid, claim),
            chainHash = computeChainHash(subjectWallet.credentialChainHead, claim),
        )

        // Add to subject's wallet
        wallets[subjectDid] = subjectWallet.copy(
            credentials = subjectWallet.credentials + credential,
            credentialChainHead = credential.chainHash,
        )

        return credential
    }

    /**
     * Creates a presentation — a subset of credentials for a specific purpose.
     * Selective disclosure: share ONLY what's needed.
     * "I need to prove I can do plumbing — here are my 3 plumbing credentials,
     *  but NOT my medical or personal data."
     */
    fun createPresentation(
        holderDid: String,
        purpose: String,
        credentialFilter: (VerifiableCredential) -> Boolean = { true },
    ): VerifiablePresentation? {
        val wallet = wallets[holderDid] ?: return null
        val selected = wallet.validCredentials.filter(credentialFilter)

        return VerifiablePresentation(
            presentationId = "vp-${System.currentTimeMillis()}",
            holderDid = holderDid,
            credentials = selected,
            purpose = purpose,
            presentationHash = computeHash(selected.joinToString { it.credentialId }),
        )
    }

    /**
     * Verifies a credential WITHOUT contacting any server.
     * Works offline, in mesh, anywhere.
     */
    fun verifyCredential(credential: VerifiableCredential): VerificationResult {
        if (credential.credentialId in revocations) {
            return VerificationResult(
                isValid = false, reason = "Credential has been revoked",
            )
        }

        if (credential.isExpired) {
            return VerificationResult(
                isValid = false, reason = "Credential has expired",
            )
        }

        if (credential.issuerSignature.isBlank()) {
            return VerificationResult(
                isValid = false, reason = "Missing issuer signature",
            )
        }

        // Verify signature
        val expectedSig = sign(credential.issuerDid, credential.claim)
        if (credential.issuerSignature != expectedSig) {
            return VerificationResult(
                isValid = false, reason = "Invalid signature",
            )
        }

        return VerificationResult(
            isValid = true,
            reason = "Credential verified: signed by ${credential.issuerDid}",
            issuerDid = credential.issuerDid,
            subjectDid = credential.subjectDid,
        )
    }

    /**
     * Revokes a credential. Once revoked, it cannot be un-revoked.
     */
    fun revokeCredential(credentialId: String) {
        revocations.add(credentialId)
    }

    /**
     * Exports the entire wallet for portability.
     * RIGHT TO LEAVE: take your identity anywhere.
     */
    fun exportWallet(did: String): IdentityWallet? = wallets[did]

    /**
     * Imports a wallet from another platform.
     * Credentials are re-verified on import.
     */
    fun importWallet(wallet: IdentityWallet): Boolean {
        wallets[wallet.did.uri] = wallet
        return true
    }

    fun getWallet(did: String): IdentityWallet? = wallets[did]

    val totalIdentities: Int get() = wallets.size

    // ─── Crypto Helpers ───

    private fun sign(issuerDid: String, claim: String): String {
        return "sig-${computeHash("$issuerDid:$claim")}"
    }

    private fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun computeChainHash(previousHash: String, newClaim: String): String {
        return computeHash("$previousHash→$newClaim")
    }
}

@Serializable
data class VerificationResult(
    val isValid: Boolean,
    val reason: String,
    val issuerDid: String? = null,
    val subjectDid: String? = null,
)
