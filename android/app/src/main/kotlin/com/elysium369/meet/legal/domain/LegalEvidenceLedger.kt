package com.elysium369.meet.legal.domain

import android.content.Context
import android.net.Uri
import com.elysium369.meet.communications.DeviceMessageCipher
import com.elysium369.meet.communications.LocalCipherPayload
import com.elysium369.meet.data.local.dao.LegalEvidenceDao
import com.elysium369.meet.data.local.entities.LegalCaseEntity
import com.elysium369.meet.data.local.entities.LegalCustodyEventEntity
import com.elysium369.meet.data.local.entities.LegalEvidenceItemEntity
import com.elysium369.meet.data.local.entities.LegalExpenseEntity
import com.elysium369.meet.data.local.entities.LegalJournalEventEntity
import com.elysium369.meet.data.local.entities.LegalReminderEntity
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.observability.MeetTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.Currency
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class LegalJournalTruthState { DECLARED, OBSERVED, DOCUMENTED, VERIFIED_EXTERNALLY }

enum class LegalJournalSource { USER_ENTRY, VEHICLE_EVENT, RIDE_EVENT, PROPERTY_EVENT, COMMUNICATION_REFERENCE }

data class LegalTimelineEntry(
    val eventId: String,
    val caseId: String?,
    val eventType: String,
    val narrative: String,
    val source: LegalJournalSource,
    val truthState: LegalJournalTruthState,
    val occurredAtEpochMs: Long,
    val capturedAtEpochMs: Long,
)

data class LegalCaseSummary(
    val caseId: String,
    val title: String,
    val state: String,
    val vehicleId: String?,
    val rideId: String?,
    val propertyId: String?,
    val updatedAtEpochMs: Long,
)

data class LegalEvidenceSummary(
    val evidenceId: String,
    val caseId: String?,
    val eventId: String?,
    val mediaType: String,
    val sha256: String,
    val truthState: String,
    val derivativeOfEvidenceId: String?,
    val capturedAtEpochMs: Long,
)

/** Local-first encrypted daily evidence timeline shared by all MEET verticals. */
@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LegalEvidenceLedger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LegalEvidenceDao,
    private val principalKernel: ActivePrincipalKernel,
    private val cipher: DeviceMessageCipher,
) {
    val cases: Flow<List<LegalCaseSummary>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeCases(principal.id).map { rows -> rows.mapNotNull { it.toSummaryOrNull() } }
        }

    val timeline: Flow<List<LegalTimelineEntry>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeTimeline(principal.id).map { rows ->
                rows.mapNotNull { row -> row.toTimelineEntryOrNull() }
            }
        }

    val evidence: Flow<List<LegalEvidenceSummary>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeEvidence(principal.id).map { rows ->
                rows.map { row ->
                    LegalEvidenceSummary(
                        evidenceId = row.evidenceId,
                        caseId = row.caseId,
                        eventId = row.eventId,
                        mediaType = row.mediaType,
                        sha256 = row.contentSha256,
                        truthState = row.truthState,
                        derivativeOfEvidenceId = row.derivativeOfEvidenceId,
                        capturedAtEpochMs = row.capturedAtEpochMs,
                    )
                }
            }
        }

    suspend fun createCase(
        title: String,
        vehicleId: String? = null,
        rideId: String? = null,
        propertyId: String? = null,
    ): String {
        val normalized = title.trim()
        require(normalized.length in 3..240) { "LEGAL_CASE_TITLE" }
        val principal = principalKernel.current()
        val caseId = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(normalized, caseAad(principal.id, caseId))
        val now = System.currentTimeMillis()
        dao.insertCase(
            LegalCaseEntity(
                ownerPrincipalId = principal.id,
                caseId = caseId,
                titleCiphertextBase64 = encrypted.ciphertextBase64,
                titleNonceBase64 = encrypted.nonceBase64,
                state = "OPEN",
                vehicleId = vehicleId,
                rideId = rideId,
                propertyId = propertyId,
                revision = 1,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        telemetry("legal.case.created", "LOCAL_SAVED")
        return caseId
    }

    suspend fun recordQuickJournal(
        narrative: String,
        eventType: String = "GENERAL_NOTE",
        occurredAtEpochMs: Long = System.currentTimeMillis(),
        caseId: String? = null,
        source: LegalJournalSource = LegalJournalSource.USER_ENTRY,
        truthState: LegalJournalTruthState = LegalJournalTruthState.DECLARED,
    ): String {
        val normalized = narrative.trim()
        require(normalized.length in 3..20_000) { "LEGAL_NARRATIVE_LENGTH" }
        require(eventType.matches(Regex("[A-Z0-9_]{3,64}"))) { "LEGAL_EVENT_TYPE" }
        require(truthState != LegalJournalTruthState.VERIFIED_EXTERNALLY) {
            "Quick journal cannot self-assert external verification"
        }
        val principal = principalKernel.current()
        val eventId = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(normalized, eventAad(principal.id, eventId))
        dao.insertJournalEvent(
            LegalJournalEventEntity(
                eventId = eventId,
                ownerPrincipalId = principal.id,
                caseId = caseId,
                eventType = eventType,
                narrativeCiphertextBase64 = encrypted.ciphertextBase64,
                narrativeNonceBase64 = encrypted.nonceBase64,
                source = source.name,
                truthState = truthState.name,
                occurredAtEpochMs = occurredAtEpochMs,
                capturedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        telemetry("legal.event.created", "LOCAL_SAVED")
        return eventId
    }

    suspend fun reviseJournal(eventId: String, correctedNarrative: String): String {
        val principal = principalKernel.current()
        val original = requireNotNull(dao.getJournalEvent(principal.id, eventId)) { "LEGAL_EVENT_NOT_FOUND" }
        require(original.tombstonedAtEpochMs == null) { "LEGAL_EVENT_DELETED" }
        val normalized = correctedNarrative.trim()
        require(normalized.length in 3..20_000) { "LEGAL_NARRATIVE_LENGTH" }
        val revisionId = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(normalized, eventAad(principal.id, revisionId))
        dao.insertJournalEvent(
            original.copy(
                eventId = revisionId,
                narrativeCiphertextBase64 = encrypted.ciphertextBase64,
                narrativeNonceBase64 = encrypted.nonceBase64,
                revisionOfEventId = eventId,
                capturedAtEpochMs = System.currentTimeMillis(),
                tombstonedAtEpochMs = null,
            ),
        )
        telemetry("legal.event.revised", "REVISION_APPENDED")
        return revisionId
    }

    suspend fun deleteJournal(eventId: String): Boolean {
        val owner = principalKernel.current()
        val changed = dao.tombstoneJournalEvent(owner.id, eventId, System.currentTimeMillis()) == 1
        if (changed) telemetry("legal.event.tombstoned", "LOCAL_TOMBSTONE")
        return changed
    }

    suspend fun attachOriginalEvidence(
        sourceUri: Uri,
        mediaType: String,
        eventId: String? = null,
        caseId: String? = null,
        capturedAtEpochMs: Long = System.currentTimeMillis(),
    ): String = attachEvidence(
        sourceUri = sourceUri,
        mediaType = mediaType,
        eventId = eventId,
        caseId = caseId,
        capturedAtEpochMs = capturedAtEpochMs,
        derivativeOfEvidenceId = null,
    )

    suspend fun attachDerivedEvidence(
        sourceUri: Uri,
        mediaType: String,
        originalEvidenceId: String,
        eventId: String? = null,
        caseId: String? = null,
    ): String {
        val principal = principalKernel.current()
        requireNotNull(dao.getEvidence(principal.id, originalEvidenceId)) { "LEGAL_ORIGINAL_NOT_FOUND" }
        return attachEvidence(
            sourceUri = sourceUri,
            mediaType = mediaType,
            eventId = eventId,
            caseId = caseId,
            capturedAtEpochMs = System.currentTimeMillis(),
            derivativeOfEvidenceId = originalEvidenceId,
        )
    }

    suspend fun recordExpense(
        amountMinor: Long,
        currency: String,
        category: String,
        note: String,
        caseId: String? = null,
        incurredAtEpochMs: Long = System.currentTimeMillis(),
    ): String {
        require(amountMinor > 0) { "LEGAL_EXPENSE_AMOUNT" }
        Currency.getInstance(currency)
        require(category.matches(Regex("[A-Z0-9_]{3,64}"))) { "LEGAL_EXPENSE_CATEGORY" }
        val owner = principalKernel.current()
        val id = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(note.trim().take(4_000), expenseAad(owner.id, id))
        dao.insertExpense(
            LegalExpenseEntity(
                expenseId = id,
                ownerPrincipalId = owner.id,
                caseId = caseId,
                amountMinor = amountMinor,
                currency = currency,
                category = category,
                noteCiphertextBase64 = encrypted.ciphertextBase64,
                noteNonceBase64 = encrypted.nonceBase64,
                incurredAtEpochMs = incurredAtEpochMs,
            ),
        )
        telemetry("legal.expense.created", "AMOUNT_RECORDED")
        return id
    }

    suspend fun createReminder(label: String, dueAtEpochMs: Long, caseId: String? = null): String {
        require(label.trim().length in 3..500) { "LEGAL_REMINDER_LABEL" }
        val owner = principalKernel.current()
        val id = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(label.trim(), reminderAad(owner.id, id))
        dao.upsertReminder(
            LegalReminderEntity(
                reminderId = id,
                ownerPrincipalId = owner.id,
                caseId = caseId,
                labelCiphertextBase64 = encrypted.ciphertextBase64,
                labelNonceBase64 = encrypted.nonceBase64,
                dueAtEpochMs = dueAtEpochMs,
                state = "OPEN",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        telemetry("legal.reminder.created", "LOCAL_SAVED")
        return id
    }

    private suspend fun attachEvidence(
        sourceUri: Uri,
        mediaType: String,
        eventId: String?,
        caseId: String?,
        capturedAtEpochMs: Long,
        derivativeOfEvidenceId: String?,
    ): String = withContext(Dispatchers.IO) {
        require(mediaType.matches(Regex("[A-Z0-9_]{3,64}"))) { "LEGAL_EVIDENCE_TYPE" }
        val owner = principalKernel.current()
        if (eventId != null) requireNotNull(dao.getJournalEvent(owner.id, eventId)) { "LEGAL_EVENT_NOT_FOUND" }
        val previousCustodyHash = derivativeOfEvidenceId?.let { originalId ->
            requireNotNull(dao.getEvidence(owner.id, originalId)) { "LEGAL_ORIGINAL_NOT_FOUND" }
            dao.latestCustodyEvent(owner.id, originalId)?.eventHash
        }
        val evidenceId = UUID.randomUUID().toString()
        val ownerDirectory = File(context.filesDir, "legal_evidence/${owner.id.hashCode().toUInt()}").apply { mkdirs() }
        val destination = File(ownerDirectory, "$evidenceId.original")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "LEGAL_EVIDENCE_UNREADABLE" }
                DigestOutputStream(FileOutputStream(destination), digest).use { output -> input.copyTo(output) }
            }
            require(destination.length() > 0L) { "LEGAL_EVIDENCE_EMPTY" }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            val now = System.currentTimeMillis()
            val custody = custodyEvent(
                ownerId = owner.id,
                evidenceId = evidenceId,
                action = if (derivativeOfEvidenceId == null) "IMPORTED" else "DERIVED",
                occurredAt = now,
                previousHash = previousCustodyHash,
                contentHash = sha,
            )
            dao.attachOriginalEvidence(
                LegalEvidenceItemEntity(
                    evidenceId = evidenceId,
                    ownerPrincipalId = owner.id,
                    caseId = caseId,
                    eventId = eventId,
                    mediaType = mediaType,
                    privateUri = destination.absolutePath,
                    contentSha256 = sha,
                    truthState = "DOCUMENTED",
                    custodyState = if (derivativeOfEvidenceId == null) {
                        "LOCAL_PRIVATE_ORIGINAL"
                    } else {
                        "LOCAL_PRIVATE_DERIVED"
                    },
                    capturedAtEpochMs = capturedAtEpochMs,
                    derivativeOfEvidenceId = derivativeOfEvidenceId,
                ),
                custody,
            )
            telemetry("legal.evidence.imported", if (derivativeOfEvidenceId == null) "ORIGINAL_PRESERVED" else "DERIVATIVE_LINKED")
            evidenceId
        } catch (error: Throwable) {
            destination.delete()
            telemetry("legal.evidence.import.failed", "LOCAL_COPY_FAILED")
            throw error
        }
    }

    private fun custodyEvent(
        ownerId: String,
        evidenceId: String,
        action: String,
        occurredAt: Long,
        previousHash: String?,
        contentHash: String,
    ): LegalCustodyEventEntity {
        val id = UUID.randomUUID().toString()
        val canonical = listOf(id, ownerId, evidenceId, action, occurredAt.toString(), previousHash.orEmpty(), contentHash)
            .joinToString("\u001f")
        val eventHash = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return LegalCustodyEventEntity(id, ownerId, evidenceId, action, ownerId, occurredAt, previousHash, eventHash)
    }

    private fun LegalJournalEventEntity.toTimelineEntryOrNull(): LegalTimelineEntry? = runCatching {
        LegalTimelineEntry(
            eventId = eventId,
            caseId = caseId,
            eventType = eventType,
            narrative = cipher.decrypt(
                LocalCipherPayload(narrativeCiphertextBase64, narrativeNonceBase64),
                eventAad(ownerPrincipalId, eventId),
            ),
            source = enumValueOf(source),
            truthState = enumValueOf(truthState),
            occurredAtEpochMs = occurredAtEpochMs,
            capturedAtEpochMs = capturedAtEpochMs,
        )
    }.getOrNull()

    private fun eventAad(ownerId: String, eventId: String) =
        "legal-journal:v1:$ownerId:$eventId"

    private fun caseAad(ownerId: String, caseId: String) = "legal-case:v1:$ownerId:$caseId"
    private fun expenseAad(ownerId: String, expenseId: String) = "legal-expense:v1:$ownerId:$expenseId"
    private fun reminderAad(ownerId: String, reminderId: String) = "legal-reminder:v1:$ownerId:$reminderId"

    private fun LegalCaseEntity.toSummaryOrNull(): LegalCaseSummary? = runCatching {
        LegalCaseSummary(
            caseId = caseId,
            title = cipher.decrypt(
                LocalCipherPayload(titleCiphertextBase64, titleNonceBase64),
                caseAad(ownerPrincipalId, caseId),
            ),
            state = state,
            vehicleId = vehicleId,
            rideId = rideId,
            propertyId = propertyId,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }.getOrNull()

    private fun telemetry(name: String, resultCode: String) {
        MeetTelemetry.event(name, mapOf("vertical" to "LEGAL", "resultCode" to resultCode))
    }
}
