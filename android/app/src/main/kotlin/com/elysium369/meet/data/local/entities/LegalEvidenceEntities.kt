package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "legal_cases_local",
    primaryKeys = ["ownerPrincipalId", "caseId"],
    indices = [
        Index(value = ["ownerPrincipalId", "updatedAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "vehicleId"]),
        Index(value = ["ownerPrincipalId", "rideId"]),
        Index(value = ["ownerPrincipalId", "propertyId"]),
    ],
)
data class LegalCaseEntity(
    val ownerPrincipalId: String,
    val caseId: String,
    val titleCiphertextBase64: String,
    val titleNonceBase64: String,
    val state: String,
    val vehicleId: String? = null,
    val rideId: String? = null,
    val propertyId: String? = null,
    val revision: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "legal_journal_events",
    indices = [
        Index(value = ["ownerPrincipalId", "occurredAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "caseId", "occurredAtEpochMs"]),
        Index(value = ["revisionOfEventId"]),
    ],
)
data class LegalJournalEventEntity(
    @androidx.room.PrimaryKey val eventId: String,
    val ownerPrincipalId: String,
    val caseId: String? = null,
    val eventType: String,
    val narrativeCiphertextBase64: String,
    val narrativeNonceBase64: String,
    val source: String,
    val truthState: String,
    val occurredAtEpochMs: Long,
    val capturedAtEpochMs: Long,
    val revisionOfEventId: String? = null,
    val tombstonedAtEpochMs: Long? = null,
)

@Entity(
    tableName = "legal_evidence_items",
    indices = [
        Index(value = ["ownerPrincipalId", "caseId", "capturedAtEpochMs"]),
        Index(value = ["eventId"]),
        Index(value = ["contentSha256"]),
        Index(value = ["derivativeOfEvidenceId"]),
    ],
)
data class LegalEvidenceItemEntity(
    @androidx.room.PrimaryKey val evidenceId: String,
    val ownerPrincipalId: String,
    val caseId: String? = null,
    val eventId: String? = null,
    val mediaType: String,
    val privateUri: String,
    val contentSha256: String,
    val truthState: String,
    val custodyState: String,
    val capturedAtEpochMs: Long,
    val derivativeOfEvidenceId: String? = null,
)

@Entity(
    tableName = "legal_custody_events",
    indices = [
        Index(value = ["ownerPrincipalId", "evidenceId", "occurredAtEpochMs"]),
        Index(value = ["eventHash"], unique = true),
    ],
)
data class LegalCustodyEventEntity(
    @androidx.room.PrimaryKey val custodyEventId: String,
    val ownerPrincipalId: String,
    val evidenceId: String,
    val action: String,
    val actorPrincipalId: String,
    val occurredAtEpochMs: Long,
    val previousEventHash: String? = null,
    val eventHash: String,
)

@Entity(
    tableName = "legal_reminders_local",
    indices = [Index(value = ["ownerPrincipalId", "dueAtEpochMs", "state"])],
)
data class LegalReminderEntity(
    @androidx.room.PrimaryKey val reminderId: String,
    val ownerPrincipalId: String,
    val caseId: String? = null,
    val labelCiphertextBase64: String,
    val labelNonceBase64: String,
    val dueAtEpochMs: Long,
    val state: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "legal_expenses_local",
    indices = [Index(value = ["ownerPrincipalId", "caseId", "incurredAtEpochMs"])],
)
data class LegalExpenseEntity(
    @androidx.room.PrimaryKey val expenseId: String,
    val ownerPrincipalId: String,
    val caseId: String? = null,
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val noteCiphertextBase64: String,
    val noteNonceBase64: String,
    val incurredAtEpochMs: Long,
)
