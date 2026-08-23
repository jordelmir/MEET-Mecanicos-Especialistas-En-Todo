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
}
