package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.elysium369.meet.data.local.entities.MarketplaceLedgerEntryEntity
import com.elysium369.meet.data.local.entities.VanguardEventEntity
import com.elysium369.meet.data.local.entities.VanguardOutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Elysium Vanguard Commerce Trust Core entities.
 *
 * Implements the Outbox Pattern: every commerce event is written first to the
 * local DB (event + outbox message) with idempotency keys, then a sync worker
 * delivers the outbox payloads to Supabase. The unique indices on
 * idempotency_key make duplicate enqueue safe across retries.
 */
@Dao
interface VanguardCommerceDao {

    // ----- Events -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: VanguardEventEntity): Long

    @Query("SELECT * FROM vanguard_events WHERE eventId = :eventId")
    suspend fun getEvent(eventId: String): VanguardEventEntity?

    @Query("SELECT * FROM vanguard_events WHERE aggregateType = :type AND aggregateId = :id ORDER BY occurredAt ASC")
    fun getEventsForAggregate(type: String, id: String): Flow<List<VanguardEventEntity>>

    @Query("SELECT * FROM vanguard_events WHERE synced = 0 ORDER BY occurredAt ASC LIMIT :limit")
    suspend fun getUnsyncedEvents(limit: Int = 100): List<VanguardEventEntity>

    @Query("UPDATE vanguard_events SET synced = 1 WHERE eventId IN (:eventIds)")
    suspend fun markEventsSynced(eventIds: List<String>)

    // ----- Outbox -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutbox(message: VanguardOutboxEntity): Long

    @Query("SELECT * FROM vanguard_outbox WHERE status = :status AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC LIMIT :limit")
    suspend fun getOutboxByStatus(status: String, now: Long, limit: Int = 50): List<VanguardOutboxEntity>

    @Query("UPDATE vanguard_outbox SET status = :status, attemptCount = attemptCount + 1, lastError = :error, updatedAt = :now WHERE outboxId = :outboxId")
    suspend fun updateOutboxStatus(outboxId: String, status: String, error: String?, now: Long)

    @Query("DELETE FROM vanguard_outbox WHERE status = 'DELIVERED' AND updatedAt < :olderThan")
    suspend fun pruneDelivered(olderThan: Long): Int

    // ----- Ledger -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLedgerEntries(entries: List<MarketplaceLedgerEntryEntity>): List<Long>

    @Query("SELECT * FROM marketplace_ledger_entries WHERE orderType = :type AND orderId = :orderId ORDER BY createdAt ASC")
    fun getLedgerForOrder(type: String, orderId: String): Flow<List<MarketplaceLedgerEntryEntity>>

    @Query("SELECT * FROM marketplace_ledger_entries WHERE transactionId = :txnId ORDER BY createdAt ASC")
    suspend fun getLedgerByTransaction(txnId: String): List<MarketplaceLedgerEntryEntity>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM marketplace_ledger_entries WHERE participantId = :participantId AND direction = 'CREDIT' AND status = 'POSTED'")
    suspend fun sumCreditsForParticipant(participantId: String): Long

    @Query("UPDATE marketplace_ledger_entries SET status = :newStatus, settledAt = :settledAt WHERE ledgerEntryId IN (:ids)")
    suspend fun updateLedgerStatus(ids: List<String>, newStatus: String, settledAt: Long)

    @Query("SELECT * FROM marketplace_ledger_entries WHERE synced = 0 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getUnsyncedLedger(limit: Int = 100): List<MarketplaceLedgerEntryEntity>

    @Query("UPDATE marketplace_ledger_entries SET synced = 1 WHERE ledgerEntryId IN (:ids)")
    suspend fun markLedgerSynced(ids: List<String>)

    // ----- Atomic composite ops (Outbox pattern) -----

    /**
     * Atomically records a commerce event and its corresponding outbox message.
     * Caller is responsible for providing a stable idempotencyKey so duplicate
     * invocations are no-ops (unique index on idempotencyKey).
     */
    @Transaction
    suspend fun recordCommerceEvent(
        event: VanguardEventEntity,
        outbox: VanguardOutboxEntity
    ): Boolean {
        val eventResult = insertEvent(event)
        val outboxResult = insertOutbox(outbox)
        return eventResult > 0L && outboxResult > 0L
    }
}