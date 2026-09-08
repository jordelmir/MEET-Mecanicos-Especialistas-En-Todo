package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ── SafeJourney Persistence ──

@Entity(
    tableName = "safe_journeys",
    indices = [
        Index(value = ["principalId"]),
        Index(value = ["state"]),
        Index(value = ["principalId", "state"]),
    ],
)
data class SafeJourneyEntity(
    @PrimaryKey val journeyId: String,
    val principalId: String,
    val name: String,
    val originName: String,
    val destinationName: String?,
    val destinationLat: Double,
    val destinationLon: Double,
    val destinationRadiusMeters: Double,
    val estimatedArrivalEpochMs: Long,
    val state: String,
    val journeyState: String,
    val mode: String,
    val createdAtEpochMs: Long,
    val startedAtEpochMs: Long?,
    val lastCheckInAtEpochMs: Long?,
    val completedAtEpochMs: Long?,
    val sharedWithPrincipalIdsJson: String,
    val checkInIntervalMs: Long,
    val publisherDeviceId: String,
)

// ── PttChannel Persistence ──

@Entity(
    tableName = "ptt_channels",
    indices = [
        Index(value = ["ownerPrincipalId"]),
        Index(value = ["state"]),
    ],
)
data class PttChannelEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val type: String,
    val state: String,
    val ownerPrincipalId: String,
    val createdAtEpochMs: Long,
    val memberCount: Int,
    val maxMembers: Int,
    val isEncrypted: Boolean,
)

@Entity(
    tableName = "ptt_channel_members",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["principalId"]),
        Index(value = ["channelId", "principalId"], unique = true),
    ],
)
data class PttChannelMemberEntity(
    val channelId: String,
    val principalId: String,
    val role: String,
    val state: String,
    val joinedAtEpochMs: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

// ── ScheduledRide Persistence ──

@Entity(
    tableName = "scheduled_rides",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["status"]),
        Index(value = ["scheduledAtEpochMs"]),
        Index(value = ["userId", "status"]),
    ],
)
data class ScheduledRideEntity(
    @PrimaryKey val scheduleId: String,
    val userId: String,
    val stopsJson: String,
    val scheduledAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val fareMode: String,
    val estimatedFare: Long,
    val currency: String,
    val status: String,
    val recurrencePattern: String,
    val recurrenceConfigJson: String?,
    val notes: String,
    val matchedDriverId: String?,
    val rideId: String?,
    val dispatchAtEpochMs: Long?,
)

@Entity(
    tableName = "favorite_routes",
    indices = [
        Index(value = ["usageCount"]),
    ],
)
data class FavoriteRouteEntity(
    @PrimaryKey val routeId: String,
    val label: String,
    val icon: String,
    val stopsJson: String,
    val fareMode: String,
    val usageCount: Int,
    val lastUsedEpochMs: Long,
)
