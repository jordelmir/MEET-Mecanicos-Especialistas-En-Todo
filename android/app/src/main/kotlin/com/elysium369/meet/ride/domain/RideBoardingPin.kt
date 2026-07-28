package com.elysium369.meet.ride.domain

import java.security.MessageDigest
import java.security.SecureRandom

data class RideBoardingChallenge(
    val salt: ByteArray,
    val pinHash: ByteArray,
    val expiresAtEpochMs: Long,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long? = null,
    val verifiedAtEpochMs: Long? = null,
) {
    init {
        require(salt.isNotEmpty())
        require(pinHash.isNotEmpty())
        require(failedAttempts in 0..20)
    }
}

enum class RidePinVerificationStatus {
    VERIFIED,
    INVALID,
    LOCKED,
    EXPIRED_OR_USED,
}

data class RidePinVerification(
    val status: RidePinVerificationStatus,
    val challenge: RideBoardingChallenge,
)

object RideBoardingPinPolicy {
    private const val MAX_ATTEMPTS_BEFORE_LOCK = 5
    private const val LOCK_MS = 5 * 60 * 1000L
    private const val LIFETIME_MS = 30 * 60 * 1000L

    fun issue(nowEpochMs: Long, random: SecureRandom = SecureRandom()): Pair<String, RideBoardingChallenge> {
        val pin = random.nextInt(10_000).toString().padStart(4, '0')
        val salt = ByteArray(16).also(random::nextBytes)
        return pin to RideBoardingChallenge(
            salt = salt,
            pinHash = hash(salt, pin),
            expiresAtEpochMs = nowEpochMs + LIFETIME_MS,
        )
    }

    fun verify(
        challenge: RideBoardingChallenge,
        candidate: String,
        nowEpochMs: Long,
    ): RidePinVerification {
        if (
            challenge.verifiedAtEpochMs != null ||
            nowEpochMs >= challenge.expiresAtEpochMs
        ) {
            return RidePinVerification(RidePinVerificationStatus.EXPIRED_OR_USED, challenge)
        }
        if ((challenge.lockedUntilEpochMs ?: 0L) > nowEpochMs) {
            return RidePinVerification(RidePinVerificationStatus.LOCKED, challenge)
        }
        val valid = candidate.matches(Regex("^[0-9]{4}$")) &&
            MessageDigest.isEqual(challenge.pinHash, hash(challenge.salt, candidate))
        if (valid) {
            return RidePinVerification(
                RidePinVerificationStatus.VERIFIED,
                challenge.copy(verifiedAtEpochMs = nowEpochMs),
            )
        }
        val failures = challenge.failedAttempts + 1
        return RidePinVerification(
            RidePinVerificationStatus.INVALID,
            challenge.copy(
                failedAttempts = failures,
                lockedUntilEpochMs = if (failures >= MAX_ATTEMPTS_BEFORE_LOCK) {
                    nowEpochMs + LOCK_MS
                } else {
                    null
                },
            ),
        )
    }

    private fun hash(salt: ByteArray, pin: String): ByteArray {
        var value = salt + pin.toByteArray(Charsets.UTF_8)
        repeat(12_000) {
            value = MessageDigest.getInstance("SHA-256").digest(value + salt)
        }
        return value
    }
}

