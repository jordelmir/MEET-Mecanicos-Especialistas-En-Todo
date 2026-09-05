package com.elysium369.meet.presence

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PresenceKernel — Singleton authority for generalized location sharing.
 *
 * One kernel, multiple consumers: Rides, Circles, Safe Journey, Emergency.
 * Never creates duplicate authorities.
 */
@Singleton
class PresenceKernel @Inject constructor() {

    private val _snapshots = MutableStateFlow<Map<String, PresenceSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, PresenceSnapshot>> = _snapshots.asStateFlow()

    private val _grants = MutableStateFlow<Map<String, PresenceGrant>>(emptyMap())
    val grants: StateFlow<Map<String, PresenceGrant>> = _grants.asStateFlow()

    /** Start sharing location for a principal. */
    fun startSharing(
        principalId: String,
        location: PresenceLocation,
        granularity: SharingGranularity = SharingGranularity.PRECISE,
    ) {
        _snapshots.update { current ->
            val existing = current[principalId]
            current + (principalId to PresenceSnapshot(
                principalId = principalId,
                state = PresenceState.SHARING,
                lastLocation = location,
                lastUpdatedAtEpochMs = System.currentTimeMillis(),
                activeGrants = existing?.activeGrants ?: emptyList(),
                sharingGranularity = granularity,
            ))
        }
        Log.i("PresenceKernel", "Started sharing for $principalId (${granularity})")
    }

    /** Update location for a principal who is already sharing. */
    fun updateLocation(principalId: String, location: PresenceLocation) {
        _snapshots.update { current ->
            val snapshot = current[principalId] ?: return@update current
            if (snapshot.state != PresenceState.SHARING) return@update current
            current + (principalId to snapshot.copy(
                lastLocation = location,
                lastUpdatedAtEpochMs = System.currentTimeMillis(),
            ))
        }
    }

    /** Stop sharing location. */
    fun stopSharing(principalId: String) {
        _snapshots.update { current ->
            val snapshot = current[principalId] ?: return@update current
            current + (principalId to snapshot.copy(
                state = PresenceState.INACTIVE,
                lastUpdatedAtEpochMs = System.currentTimeMillis(),
            ))
        }
        Log.i("PresenceKernel", "Stopped sharing for $principalId")
    }

    /** Pause sharing (user-initiated, can resume). */
    fun pauseSharing(principalId: String) {
        _snapshots.update { current ->
            val snapshot = current[principalId] ?: return@update current
            if (snapshot.state != PresenceState.SHARING) return@update current
            current + (principalId to snapshot.copy(
                state = PresenceState.PAUSED,
                lastUpdatedAtEpochMs = System.currentTimeMillis(),
            ))
        }
    }

    /** Resume sharing after pause. */
    fun resumeSharing(principalId: String) {
        _snapshots.update { current ->
            val snapshot = current[principalId] ?: return@update current
            if (snapshot.state != PresenceState.PAUSED) return@update current
            current + (principalId to snapshot.copy(
                state = PresenceState.SHARING,
                lastUpdatedAtEpochMs = System.currentTimeMillis(),
            ))
        }
    }

    /** Grant location sharing to another principal. */
    fun grantSharing(
        ownerPrincipalId: String,
        granteePrincipalId: String,
        granularity: SharingGranularity = SharingGranularity.PRECISE,
        durationMs: Long = 60 * 60 * 1000L,
        purpose: String = "GENERAL",
    ): PresenceGrantValidation {
        val existingGrants = _grants.value.values.filter {
            it.ownerPrincipalId == ownerPrincipalId && it.isActive
        }.size

        val validation = AntiStalkingPolicy.validateGrant(
            owner = ownerPrincipalId,
            grantee = granteePrincipalId,
            granularity = granularity,
            durationMs = durationMs,
            existingGrantCount = existingGrants,
        )

        if (validation is PresenceGrantValidation.DENIED) return validation

        val grant = PresenceGrant(
            grantId = UUID.randomUUID().toString(),
            ownerPrincipalId = ownerPrincipalId,
            granteePrincipalId = granteePrincipalId,
            granularity = granularity,
            startedAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = System.currentTimeMillis() + durationMs,
            purpose = purpose,
        )

        _grants.update { it + (grant.grantId to grant) }

        // Update snapshot with new grant
        _snapshots.update { current ->
            val snapshot = current[ownerPrincipalId] ?: return@update current
            current + (ownerPrincipalId to snapshot.copy(
                activeGrants = snapshot.activeGrants + grant,
            ))
        }

        Log.i("PresenceKernel", "Granted sharing: $ownerPrincipalId → $granteePrincipalId ($granularity, ${durationMs}ms)")
        return PresenceGrantValidation.ALLOWED
    }

    /** Revoke a sharing grant. */
    fun revokeGrant(grantId: String) {
        _grants.update { current ->
            val grant = current[grantId] ?: return@update current
            current + (grantId to grant.copy(isActive = false))
        }
    }

    /** Get the location of a principal for an authorized consumer. */
    fun getLocationForConsumer(
        ownerPrincipalId: String,
        consumerPrincipalId: String,
    ): PresenceLocation? {
        val snapshot = _snapshots.value[ownerPrincipalId] ?: return null
        if (snapshot.state != PresenceState.SHARING) return null
        if (!snapshot.isSharedWith(consumerPrincipalId)) return null

        val grant = snapshot.activeGrants.firstOrNull {
            it.granteePrincipalId == consumerPrincipalId && it.isActive
        } ?: return null

        val location = snapshot.lastLocation ?: return null

        return when (grant.granularity) {
            SharingGranularity.PRECISE -> location
            SharingGranularity.APPROXIMATE,
            SharingGranularity.COARSE -> location.copy(
                latitude = kotlin.math.round(location.latitude * 10.0) / 10.0,
                longitude = kotlin.math.round(location.longitude * 10.0) / 10.0,
                accuracyMeters = 1000f,
            )
            SharingGranularity.CITY -> location.copy(
                latitude = kotlin.math.round(location.latitude),
                longitude = kotlin.math.round(location.longitude),
                accuracyMeters = 10_000f,
            )
            SharingGranularity.PLACE_ONLY,
            SharingGranularity.STATE_ONLY -> null
        }
    }

    /** Get all principals sharing with a specific consumer. */
    fun getSharedWithConsumer(consumerPrincipalId: String): List<PresenceSnapshot> {
        return _snapshots.value.values.filter {
            it.state == PresenceState.SHARING && it.isSharedWith(consumerPrincipalId)
        }
    }

    /** Cleanup expired grants (call periodically). */
    fun cleanupExpiredGrants() {
        val now = System.currentTimeMillis()
        _grants.update { current ->
            current.filterValues { grant ->
                if (grant.isExpired(now) && grant.isActive) {
                    Log.i("PresenceKernel", "Expired grant: ${grant.grantId}")
                    false
                } else true
            }
        }

        // Update snapshots to remove expired grants
        _snapshots.update { snapshots ->
            snapshots.mapValues { (_, snapshot) ->
                val validGrants = snapshot.activeGrants.filter { !it.isExpired(now) }
                if (validGrants.size != snapshot.activeGrants.size) {
                    snapshot.copy(activeGrants = validGrants)
                } else snapshot
            }
        }
    }

    /** Mark stale locations. */
    fun markStaleLocations(staleThresholdMs: Long = 300_000L) {
        val now = System.currentTimeMillis()
        _snapshots.update { current ->
            current.mapValues { (_, snapshot) ->
                if (snapshot.state == PresenceState.SHARING && snapshot.isStale(now, staleThresholdMs)) {
                    snapshot.copy(state = PresenceState.STALE)
                } else snapshot
            }
        }
    }
}
