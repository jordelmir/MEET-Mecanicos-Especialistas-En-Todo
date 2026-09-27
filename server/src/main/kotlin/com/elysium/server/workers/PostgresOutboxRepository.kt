package com.elysium.server.workers

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

/**
 * ══════════════════════════════════════════════════════════════════════
 *  P O S T G R E S   O U T B O X   R E P O S I T O R Y
 *  ──────────────────────────────────────────────────────────────
 *  Production implementation of OutboxRepository backed by PostgreSQL.
 *  Adheres strictly to Master Order Genesis §6, §7, §8:
 *  - Short DB transactions.
 *  - Atomic claim using FOR UPDATE SKIP LOCKED.
 *  - Lease tokens and timeout fences preventing duplicate workers.
 *  - Dead Letter Queue (DLQ) state preservation.
 * ══════════════════════════════════════════════════════════════════════
 */
class PostgresOutboxRepository(
    private val dataSource: DataSource,
) : OutboxRepository {

    private val logger = LoggerFactory.getLogger(PostgresOutboxRepository::class.java)

    init {
        ensureSchema()
    }

    private fun ensureSchema() {
        val ddl = """
            CREATE TABLE IF NOT EXISTS elysium_outbox_events (
                outbox_id BIGSERIAL PRIMARY KEY,
                event_id VARCHAR(64) NOT NULL UNIQUE,
                source_domain VARCHAR(64) NOT NULL,
                source_type VARCHAR(64) NOT NULL DEFAULT 'DOMAIN_ENTITY',
                source_id VARCHAR(128) NOT NULL,
                aggregate_type VARCHAR(64) NOT NULL,
                aggregate_id VARCHAR(128) NOT NULL,
                aggregate_version BIGINT NOT NULL DEFAULT 1,
                event_type VARCHAR(128) NOT NULL,
                payload TEXT NOT NULL DEFAULT '{}',
                target_principal_id VARCHAR(128),
                correlation_id VARCHAR(128),
                publish_attempts INT NOT NULL DEFAULT 0,
                next_attempt_at_epoch_ms BIGINT NOT NULL DEFAULT 0,
                lease_owner VARCHAR(128),
                lease_token VARCHAR(128),
                lease_until_epoch_ms BIGINT,
                published_at_epoch_ms BIGINT,
                dead_lettered_at_epoch_ms BIGINT,
                last_error_code TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );

            CREATE INDEX IF NOT EXISTS idx_elysium_outbox_pending 
                ON elysium_outbox_events (next_attempt_at_epoch_ms, outbox_id)
                WHERE published_at_epoch_ms IS NULL AND dead_lettered_at_epoch_ms IS NULL;
        """.trimIndent()

        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(ddl)
                }
            }
            logger.info("Verified PostgreSQL outbox table [elysium_outbox_events].")
        } catch (e: Exception) {
            logger.warn("Schema initialization notice (table might already exist or need migrations): ${e.message}")
        }
    }

    override suspend fun claimPendingEvents(
        workerId: String,
        leaseToken: String,
        leaseUntilEpochMs: Long,
        batchSize: Int,
        nowEpochMs: Long,
    ): List<OutboxEventRecord> {
        val selectSql = """
            SELECT outbox_id, event_id, source_domain, source_type, source_id,
                   aggregate_type, aggregate_id, aggregate_version, event_type,
                   payload, target_principal_id, correlation_id, publish_attempts,
                   next_attempt_at_epoch_ms, lease_owner, lease_token, lease_until_epoch_ms,
                   published_at_epoch_ms, dead_lettered_at_epoch_ms, last_error_code
            FROM elysium_outbox_events
            WHERE published_at_epoch_ms IS NULL
              AND dead_lettered_at_epoch_ms IS NULL
              AND next_attempt_at_epoch_ms <= ?
              AND (lease_until_epoch_ms IS NULL OR lease_until_epoch_ms < ?)
            ORDER BY outbox_id ASC
            LIMIT ?
            FOR UPDATE SKIP LOCKED
        """.trimIndent()

        val updateSql = """
            UPDATE elysium_outbox_events
            SET lease_owner = ?, lease_token = ?, lease_until_epoch_ms = ?
            WHERE outbox_id = ?
        """.trimIndent()

        return try {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val claimed = mutableListOf<OutboxEventRecord>()
                    val claimedIds = mutableListOf<Long>()

                    conn.prepareStatement(selectSql).use { stmt ->
                        stmt.setLong(1, nowEpochMs)
                        stmt.setLong(2, nowEpochMs)
                        stmt.setInt(3, batchSize)

                        stmt.executeQuery().use { rs ->
                            while (rs.next()) {
                                val record = mapRow(rs).copy(
                                    leaseOwner = workerId,
                                    leaseToken = leaseToken,
                                    leaseUntilEpochMs = leaseUntilEpochMs,
                                )
                                claimed.add(record)
                                claimedIds.add(record.outboxId)
                            }
                        }
                    }

                    if (claimedIds.isNotEmpty()) {
                        conn.prepareStatement(updateSql).use { updateStmt ->
                            for (id in claimedIds) {
                                updateStmt.setString(1, workerId)
                                updateStmt.setString(2, leaseToken)
                                updateStmt.setLong(3, leaseUntilEpochMs)
                                updateStmt.setLong(4, id)
                                updateStmt.addBatch()
                            }
                            updateStmt.executeBatch()
                        }
                    }

                    conn.commit()
                    claimed
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to claim pending outbox events from PostgreSQL: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun markPublished(outboxId: Long, leaseToken: String, publishedAtEpochMs: Long): Boolean {
        val sql = """
            UPDATE elysium_outbox_events
            SET published_at_epoch_ms = ?,
                lease_token = NULL,
                lease_owner = NULL,
                lease_until_epoch_ms = NULL,
                last_error_code = NULL
            WHERE outbox_id = ? AND lease_token = ?
        """.trimIndent()

        return executeUpdate(sql, publishedAtEpochMs, outboxId, leaseToken) > 0
    }

    override suspend fun markFailed(
        outboxId: Long,
        leaseToken: String,
        errorCode: String,
        nextAttemptEpochMs: Long,
        attempts: Int,
    ): Boolean {
        val sql = """
            UPDATE elysium_outbox_events
            SET publish_attempts = ?,
                next_attempt_at_epoch_ms = ?,
                lease_token = NULL,
                lease_owner = NULL,
                lease_until_epoch_ms = NULL,
                last_error_code = ?
            WHERE outbox_id = ? AND lease_token = ?
        """.trimIndent()

        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, attempts)
                    stmt.setLong(2, nextAttemptEpochMs)
                    stmt.setString(3, errorCode)
                    stmt.setLong(4, outboxId)
                    stmt.setString(5, leaseToken)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            logger.error("Error marking outbox event $outboxId as failed: ${e.message}", e)
            false
        }
    }

    override suspend fun markDeadLettered(outboxId: Long, leaseToken: String, errorCode: String): Boolean {
        val now = System.currentTimeMillis()
        val sql = """
            UPDATE elysium_outbox_events
            SET dead_lettered_at_epoch_ms = ?,
                lease_token = NULL,
                lease_owner = NULL,
                lease_until_epoch_ms = NULL,
                last_error_code = ?
            WHERE outbox_id = ? AND lease_token = ?
        """.trimIndent()

        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, now)
                    stmt.setString(2, errorCode)
                    stmt.setLong(3, outboxId)
                    stmt.setString(4, leaseToken)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            logger.error("Error marking outbox event $outboxId as dead-lettered: ${e.message}", e)
            false
        }
    }

    override suspend fun insertEvent(event: OutboxEventRecord): Long {
        val sql = """
            INSERT INTO elysium_outbox_events (
                event_id, source_domain, source_type, source_id,
                aggregate_type, aggregate_id, aggregate_version,
                event_type, payload, target_principal_id, correlation_id,
                publish_attempts, next_attempt_at_epoch_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING outbox_id
        """.trimIndent()

        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, event.eventId)
                    stmt.setString(2, event.sourceDomain)
                    stmt.setString(3, event.sourceType)
                    stmt.setString(4, event.sourceId)
                    stmt.setString(5, event.aggregateType)
                    stmt.setString(6, event.aggregateId)
                    stmt.setLong(7, event.aggregateVersion)
                    stmt.setString(8, event.eventType)
                    stmt.setString(9, event.payload)
                    stmt.setString(10, event.targetPrincipalId)
                    stmt.setString(11, event.correlationId)
                    stmt.setInt(12, event.publishAttempts)
                    stmt.setLong(13, event.nextAttemptAtEpochMs)

                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getLong(1) else -1L
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error inserting outbox event ${event.eventId}: ${e.message}", e)
            -1L
        }
    }

    override suspend fun getEvent(outboxId: Long): OutboxEventRecord? {
        val sql = """
            SELECT outbox_id, event_id, source_domain, source_type, source_id,
                   aggregate_type, aggregate_id, aggregate_version, event_type,
                   payload, target_principal_id, correlation_id, publish_attempts,
                   next_attempt_at_epoch_ms, lease_owner, lease_token, lease_until_epoch_ms,
                   published_at_epoch_ms, dead_lettered_at_epoch_ms, last_error_code
            FROM elysium_outbox_events
            WHERE outbox_id = ?
        """.trimIndent()

        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, outboxId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) mapRow(rs) else null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error fetching outbox event $outboxId: ${e.message}", e)
            null
        }
    }

    private fun executeUpdate(sql: String, param1: Long, param2: Long, param3: String): Int {
        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, param1)
                    stmt.setLong(2, param2)
                    stmt.setString(3, param3)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing outbox update: ${e.message}", e)
            0
        }
    }

    private fun mapRow(rs: ResultSet): OutboxEventRecord {
        return OutboxEventRecord(
            outboxId = rs.getLong("outbox_id"),
            eventId = rs.getString("event_id"),
            sourceDomain = rs.getString("source_domain"),
            sourceType = rs.getString("source_type"),
            sourceId = rs.getString("source_id"),
            aggregateType = rs.getString("aggregate_type"),
            aggregateId = rs.getString("aggregate_id"),
            aggregateVersion = rs.getLong("aggregate_version"),
            eventType = rs.getString("event_type"),
            payload = rs.getString("payload") ?: "{}",
            targetPrincipalId = rs.getString("target_principal_id"),
            correlationId = rs.getString("correlation_id"),
            publishAttempts = rs.getInt("publish_attempts"),
            nextAttemptAtEpochMs = rs.getLong("next_attempt_at_epoch_ms"),
            leaseOwner = rs.getString("lease_owner"),
            leaseToken = rs.getString("lease_token"),
            leaseUntilEpochMs = rs.getObject("lease_until_epoch_ms") as? Long,
            publishedAtEpochMs = rs.getObject("published_at_epoch_ms") as? Long,
            deadLetteredAtEpochMs = rs.getObject("dead_lettered_at_epoch_ms") as? Long,
            lastErrorCode = rs.getString("last_error_code"),
        )
    }
}
