package com.elysium369.meet.ecu.lease

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Section 43 Single Programmer Lease.
 * Guarantees that exactly one programming executor owns exclusive hardware authority over an ECU.
 * Concurrent second attempts are strictly rejected with typed conflict.
 */
data class ProgrammingLease(
    val leaseId: String,
    val vehicleId: String,
    val ecuPhysicalAddress: String,
    val adapterFingerprint: String,
    val executorId: String,
    val principalId: String,
    val sessionId: String,
    val grantedAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long = System.currentTimeMillis() + DEFAULT_LEASE_DURATION_MS,
) {
    companion object {
        const val DEFAULT_LEASE_DURATION_MS = 60_000L // 60s lease, extended via heartbeat
    }

    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAtMs
}

sealed interface LeaseAcquisitionResult {
    data class Granted(val lease: ProgrammingLease) : LeaseAcquisitionResult
    data class Rejected(val activeLease: ProgrammingLease, val reason: String) : LeaseAcquisitionResult
}

object ProgrammingLeaseManager {
    private val activeLeases = ConcurrentHashMap<String, ProgrammingLease>()
    private val lock = ReentrantLock()

    private fun leaseKey(vehicleId: String, ecuPhysicalAddress: String) =
        "$vehicleId:$ecuPhysicalAddress".uppercase()

    fun acquire(
        vehicleId: String,
        ecuPhysicalAddress: String,
        adapterFingerprint: String,
        executorId: String,
        principalId: String,
        sessionId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): LeaseAcquisitionResult = lock.withLock {
        val key = leaseKey(vehicleId, ecuPhysicalAddress)
        val current = activeLeases[key]

        if (current != null && current.expiresAtMs > nowMs) {
            if (current.sessionId == sessionId && current.executorId == executorId) {
                // Idempotent renewal by same session
                val renewed = current.copy(expiresAtMs = nowMs + ProgrammingLease.DEFAULT_LEASE_DURATION_MS)
                activeLeases[key] = renewed
                return@withLock LeaseAcquisitionResult.Granted(renewed)
            }
            return@withLock LeaseAcquisitionResult.Rejected(
                activeLease = current,
                reason = "ECU $ecuPhysicalAddress is actively leased to executor=${current.executorId} until ${current.expiresAtMs}."
            )
        }

        val newLease = ProgrammingLease(
            leaseId = "lease_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}",
            vehicleId = vehicleId,
            ecuPhysicalAddress = ecuPhysicalAddress,
            adapterFingerprint = adapterFingerprint,
            executorId = executorId,
            principalId = principalId,
            sessionId = sessionId,
            grantedAtMs = nowMs,
            expiresAtMs = nowMs + ProgrammingLease.DEFAULT_LEASE_DURATION_MS,
        )
        activeLeases[key] = newLease
        LeaseAcquisitionResult.Granted(newLease)
    }

    fun release(sessionId: String, vehicleId: String, ecuPhysicalAddress: String): Boolean = lock.withLock {
        val key = leaseKey(vehicleId, ecuPhysicalAddress)
        val current = activeLeases[key] ?: return@withLock false
        if (current.sessionId == sessionId) {
            activeLeases.remove(key)
            return@withLock true
        }
        false
    }

    fun clearAllForTest() = lock.withLock {
        activeLeases.clear()
    }
}
