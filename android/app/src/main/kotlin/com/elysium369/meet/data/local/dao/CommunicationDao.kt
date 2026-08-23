package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.elysium369.meet.data.local.entities.CommunicationCallEntity
import com.elysium369.meet.data.local.entities.CommunicationConversationEntity
import com.elysium369.meet.data.local.entities.CommunicationEventEntity
import com.elysium369.meet.data.local.entities.CommunicationParticipantEntity
import com.elysium369.meet.data.local.entities.CommunicationReceiptEntity
import com.elysium369.meet.data.local.entities.CommunicationIdentityProfileEntity
import com.elysium369.meet.data.local.entities.CommunicationLocalBlockEntity
import com.elysium369.meet.data.local.entities.CommunicationMeshOutboxEntity
import com.elysium369.meet.data.local.entities.CommunicationMeshPeerEntity
import com.elysium369.meet.data.local.entities.CommunicationPresenceLeaseEntity
import com.elysium369.meet.data.local.entities.CommunicationPrivacySettingsEntity
import com.elysium369.meet.data.local.entities.CommunicationRelationshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunicationDao {
    @Query(
        "SELECT * FROM communication_conversations " +
            "WHERE ownerPrincipalId = :ownerPrincipalId " +
            "ORDER BY COALESCE(lastEventAtEpochMs, createdAtEpochMs) DESC",
    )
    fun observeConversations(ownerPrincipalId: String): Flow<List<CommunicationConversationEntity>>

    @Query(
        "SELECT * FROM communication_conversations " +
            "WHERE conversationId = :conversationId AND ownerPrincipalId = :ownerPrincipalId LIMIT 1",
    )
    fun observeConversation(
        conversationId: String,
        ownerPrincipalId: String,
    ): Flow<CommunicationConversationEntity?>

    @Query(
        "SELECT * FROM communication_conversations " +
            "WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND serviceVertical = :serviceVertical AND serviceReferenceId = :serviceReferenceId LIMIT 1",
    )
    suspend fun findServiceConversation(
        ownerPrincipalId: String,
        serviceVertical: String,
        serviceReferenceId: String,
    ): CommunicationConversationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversation(conversation: CommunicationConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertParticipant(participant: CommunicationParticipantEntity)

    @Query(
        "SELECT * FROM communication_participants " +
            "WHERE conversationId = :conversationId AND ownerPrincipalId = :ownerPrincipalId " +
            "ORDER BY joinedAtEpochMs ASC",
    )
    fun observeParticipants(
        conversationId: String,
        ownerPrincipalId: String,
    ): Flow<List<CommunicationParticipantEntity>>

    @Query(
        "SELECT COUNT(*) FROM communication_participants " +
            "WHERE conversationId = :conversationId AND ownerPrincipalId = :ownerPrincipalId " +
            "AND membershipState = 'ACTIVE'",
    )
    suspend fun activeParticipantCount(conversationId: String, ownerPrincipalId: String): Int

    @Query(
        "SELECT participantPrincipalId FROM communication_participants " +
            "WHERE conversationId = :conversationId AND ownerPrincipalId = :ownerPrincipalId " +
            "AND participantPrincipalId != :ownerPrincipalId AND membershipState = 'ACTIVE'",
    )
    suspend fun activePeerPrincipalIds(
        conversationId: String,
        ownerPrincipalId: String,
    ): List<String>

    @Query(
        "SELECT * FROM communication_events " +
            "WHERE conversationId = :conversationId AND ownerPrincipalId = :ownerPrincipalId " +
            "ORDER BY createdAtEpochMs ASC, eventId ASC",
    )
    fun observeEvents(
        conversationId: String,
        ownerPrincipalId: String,
    ): Flow<List<CommunicationEventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: CommunicationEventEntity): Long

    @Query(
        "UPDATE communication_conversations SET lastEventAtEpochMs = :eventAt, updatedAtEpochMs = :eventAt " +
            "WHERE conversationId = :conversationId AND ownerPrincipalId = :ownerPrincipalId",
    )
    suspend fun touchConversation(conversationId: String, ownerPrincipalId: String, eventAt: Long)

    @Transaction
    suspend fun appendEvent(event: CommunicationEventEntity): Boolean {
        val inserted = insertEvent(event) != -1L
        if (inserted) {
            touchConversation(event.conversationId, event.ownerPrincipalId, event.createdAtEpochMs)
        }
        return inserted
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceipt(receipt: CommunicationReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCall(call: CommunicationCallEntity)

    @Query(
        "SELECT * FROM communication_calls WHERE ownerPrincipalId = :ownerPrincipalId " +
            "ORDER BY startedAtEpochMs DESC LIMIT :limit",
    )
    fun observeCalls(ownerPrincipalId: String, limit: Int = 100): Flow<List<CommunicationCallEntity>>

    @Query("SELECT * FROM communication_identity_profiles WHERE ownerPrincipalId = :ownerPrincipalId LIMIT 1")
    fun observeIdentityProfile(ownerPrincipalId: String): Flow<CommunicationIdentityProfileEntity?>

    @Query("SELECT * FROM communication_identity_profiles WHERE ownerPrincipalId = :ownerPrincipalId LIMIT 1")
    suspend fun getIdentityProfile(ownerPrincipalId: String): CommunicationIdentityProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIdentityProfile(profile: CommunicationIdentityProfileEntity)

    @Query("SELECT * FROM communication_privacy_settings WHERE ownerPrincipalId = :ownerPrincipalId LIMIT 1")
    fun observePrivacySettings(ownerPrincipalId: String): Flow<CommunicationPrivacySettingsEntity?>

    @Query("SELECT * FROM communication_privacy_settings WHERE ownerPrincipalId = :ownerPrincipalId LIMIT 1")
    suspend fun getPrivacySettings(ownerPrincipalId: String): CommunicationPrivacySettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrivacySettings(settings: CommunicationPrivacySettingsEntity)

    @Query(
        "SELECT * FROM communication_relationships WHERE ownerPrincipalId = :ownerPrincipalId " +
            "ORDER BY updatedAtEpochMs DESC",
    )
    fun observeRelationships(ownerPrincipalId: String): Flow<List<CommunicationRelationshipEntity>>

    @Query(
        "SELECT * FROM communication_relationships WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND peerElysiumId = :elysiumId COLLATE NOCASE LIMIT 1",
    )
    suspend fun findRelationshipByElysiumId(
        ownerPrincipalId: String,
        elysiumId: String,
    ): CommunicationRelationshipEntity?

    @Query(
        "SELECT * FROM communication_relationships WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND emailLookupToken = :lookupToken LIMIT 1",
    )
    suspend fun findRelationshipByEmailToken(
        ownerPrincipalId: String,
        lookupToken: String,
    ): CommunicationRelationshipEntity?

    @Query(
        "SELECT * FROM communication_relationships WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND phoneLookupToken = :lookupToken LIMIT 1",
    )
    suspend fun findRelationshipByPhoneToken(
        ownerPrincipalId: String,
        lookupToken: String,
    ): CommunicationRelationshipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelationship(relationship: CommunicationRelationshipEntity)

    @Query(
        "SELECT * FROM communication_local_blocks WHERE ownerPrincipalId = :ownerPrincipalId " +
            "ORDER BY createdAtEpochMs DESC",
    )
    fun observeBlocks(ownerPrincipalId: String): Flow<List<CommunicationLocalBlockEntity>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM communication_local_blocks " +
            "WHERE ownerPrincipalId = :ownerPrincipalId AND blockedPrincipalId = :peerPrincipalId)",
    )
    suspend fun isBlocked(ownerPrincipalId: String, peerPrincipalId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlock(block: CommunicationLocalBlockEntity)

    @Query(
        "DELETE FROM communication_local_blocks " +
            "WHERE ownerPrincipalId = :ownerPrincipalId AND blockedPrincipalId = :peerPrincipalId",
    )
    suspend fun deleteBlock(ownerPrincipalId: String, peerPrincipalId: String)

    @Query(
        "SELECT * FROM communication_presence_leases WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND expiresAtEpochMs > :nowEpochMs ORDER BY lastSeenAtEpochMs DESC",
    )
    fun observeActivePresence(
        ownerPrincipalId: String,
        nowEpochMs: Long,
    ): Flow<List<CommunicationPresenceLeaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPresenceLease(lease: CommunicationPresenceLeaseEntity)

    @Query(
        "SELECT * FROM communication_mesh_peers WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND expiresAtEpochMs > :nowEpochMs ORDER BY lastSeenAtEpochMs DESC",
    )
    fun observeMeshPeers(
        ownerPrincipalId: String,
        nowEpochMs: Long,
    ): Flow<List<CommunicationMeshPeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeshPeer(peer: CommunicationMeshPeerEntity)

    @Query(
        "DELETE FROM communication_mesh_peers WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND expiresAtEpochMs <= :nowEpochMs",
    )
    suspend fun deleteExpiredMeshPeers(ownerPrincipalId: String, nowEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeshEnvelope(envelope: CommunicationMeshOutboxEntity): Long

    @Query(
        "SELECT * FROM communication_mesh_outbox WHERE ownerPrincipalId = :ownerPrincipalId " +
            "AND custodyState IN ('QUEUED', 'RELAYING') AND expiresAtEpochMs > :nowEpochMs " +
            "ORDER BY priority DESC, createdAtEpochMs ASC",
    )
    fun observePendingMeshOutbox(
        ownerPrincipalId: String,
        nowEpochMs: Long,
    ): Flow<List<CommunicationMeshOutboxEntity>>

    @Query(
        "UPDATE communication_mesh_outbox SET custodyState = :custodyState, " +
            "attemptCount = :attemptCount, nextAttemptAtEpochMs = :nextAttemptAtEpochMs " +
            "WHERE meshEnvelopeId = :meshEnvelopeId AND ownerPrincipalId = :ownerPrincipalId",
    )
    suspend fun updateMeshCustody(
        ownerPrincipalId: String,
        meshEnvelopeId: String,
        custodyState: String,
        attemptCount: Int,
        nextAttemptAtEpochMs: Long,
    )
}
