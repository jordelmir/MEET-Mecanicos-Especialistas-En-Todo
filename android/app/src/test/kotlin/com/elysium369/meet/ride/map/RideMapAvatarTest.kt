package com.elysium369.meet.ride.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RideMapAvatarTest {
    @Test
    fun `unknown persisted values use safe defaults`() {
        assertEquals(
            RideDriverAvatar.CRIMSON_DRAGON,
            RideDriverAvatar.fromStorage("retired-avatar"),
        )
        assertEquals(
            RidePassengerAvatar.NEON_PERSON,
            RidePassengerAvatar.fromStorage(null),
        )
    }

    @Test
    fun `storage identifiers are stable and unique per catalog`() {
        assertEquals(
            RideDriverAvatar.entries.size,
            RideDriverAvatar.entries.map { it.storageId }.distinct().size,
        )
        assertEquals(
            RidePassengerAvatar.entries.size,
            RidePassengerAvatar.entries.map { it.storageId }.distinct().size,
        )
    }

    @Test
    fun `catalog uses original Elysium identities`() {
        val protectedCharacterNames = listOf("goku", "charizard", "pokemon")
        val catalogText = (
            RideDriverAvatar.entries.flatMap { listOf(it.storageId, it.displayName, it.description) } +
                RidePassengerAvatar.entries.flatMap { listOf(it.storageId, it.displayName, it.description) }
            ).joinToString(" ").lowercase()

        protectedCharacterNames.forEach { protectedName ->
            assertFalse(catalogText.contains(protectedName))
        }
    }
}
