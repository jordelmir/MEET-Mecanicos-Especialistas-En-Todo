package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of `public.repair_actions`. Each row is one action taken
 * inside a `REPAIR_EVIDENCE_REPORT` or `POST_SCAN_REPORT`. The
 * `repairActionHashes` list fed to `HashEngine.hashReport` is computed
 * in `createdAt asc` order so the chain is stable across runtimes.
 *
 * `dtcRelated` is intentionally a single text column (not a join table)
 * because a repair action is almost always tied to a single DTC. If a
 * repair covers multiple codes, split into multiple rows.
 *
 * `currency` defaults to `CRC` (Costa Rican colón) to match the SQL
 * default and the project's primary market, but it is stored as a
 * 3-letter code so any ISO-4217 currency can be persisted without a
 * schema migration.
 */
@Entity(
    tableName = "repair_actions",
    foreignKeys = [
        ForeignKey(
            entity = CertifiedReportEntity::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["reportId"]),
        Index(value = ["dtcRelated"]),
        Index(value = ["supplier"]),
    ],
)
data class RepairActionEntity(
    @PrimaryKey val actionId: String,
    val reportId: String,
    val actionType: String,
    val component: String,
    val dtcRelated: String?,
    val description: String,
    val partUsed: String?,
    val supplier: String?,
    val mechanic: String?,
    val cost: Double?,
    val currency: String,
    val warrantyDays: Int?,
    val createdAt: Long,
)