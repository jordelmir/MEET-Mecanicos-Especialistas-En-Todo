package com.elysium369.meet.safety.drugimpunity

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class ImpunityClockType {
    DRUG_SALE,
    MISSING_PERSON,
    HOMICIDE,
}

@Serializable
data class DrugImpunityRecord(
    val pointId: String,
    val initialReportedAt: Long,
    val clockType: ImpunityClockType = ImpunityClockType.DRUG_SALE,
    val isIntervened: Boolean = false,
    val intervenedAt: Long? = null,
    val mediaName: String? = null,
    val mediaUrl: String? = null,
    val interventionHeadline: String? = null,
    val journalistName: String? = null,
    val isReactivated: Boolean = false,
    val reactivatedAt: Long? = null,
    val reactivationReason: String? = null,
    val cycleCount: Int = 1,
)

@Serializable
data class PressCredentialProfile(
    val isRegisteredPress: Boolean = false,
    val mediaOutlet: String = "",
    val pressCardId: String = "",
    val journalistFullName: String = "",
)

@Serializable
data class SafetyParticipantProfile(
    val role: String = "CIVILIAN_WITNESS",
    val fullName: String = "",
    val identityDocument: String = "",
    val phoneOrContact: String = "",
    val organizationOrMedia: String = "",
    val credentialNumber: String = "",
    val victimRelation: String = "",
    val isAnonymousProtected: Boolean = true,
    val registeredAt: Long = 0L,
)

@Singleton
class DrugMarketImpunityStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("meet_drug_impunity_store", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _recordsFlow = MutableStateFlow<Map<String, DrugImpunityRecord>>(emptyMap())
    val recordsFlow: StateFlow<Map<String, DrugImpunityRecord>> = _recordsFlow.asStateFlow()

    private val _pressProfile = MutableStateFlow(loadPressProfile())
    val pressProfile: StateFlow<PressCredentialProfile> = _pressProfile.asStateFlow()

    private val _participantProfile = MutableStateFlow(loadParticipantProfile())
    val participantProfile: StateFlow<SafetyParticipantProfile> = _participantProfile.asStateFlow()

    init {
        loadAllRecords()
    }

    private fun loadAllRecords() {
        val map = mutableMapOf<String, DrugImpunityRecord>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("point_") && value is String) {
                runCatching {
                    val record = json.decodeFromString<DrugImpunityRecord>(value)
                    map[record.pointId] = record
                }
            }
        }
        _recordsFlow.value = map
    }

    fun getRecord(
        pointId: String,
        defaultInitialReportedAt: Long = System.currentTimeMillis(),
        clockType: ImpunityClockType = ImpunityClockType.DRUG_SALE,
    ): DrugImpunityRecord {
        return _recordsFlow.value[pointId] ?: run {
            val key = "point_$pointId"
            val raw = prefs.getString(key, null)
            if (raw != null) {
                runCatching { json.decodeFromString<DrugImpunityRecord>(raw) }.getOrNull()
            } else null
        } ?: DrugImpunityRecord(pointId = pointId, initialReportedAt = defaultInitialReportedAt, clockType = clockType)
    }

    fun certifyIntervention(
        pointId: String,
        initialReportedAt: Long,
        mediaName: String,
        mediaUrl: String,
        headline: String,
        journalistName: String,
        clockType: ImpunityClockType = ImpunityClockType.DRUG_SALE,
        intervenedAt: Long = System.currentTimeMillis(),
    ): Result<DrugImpunityRecord> = runCatching {
        require(mediaName.isNotBlank()) { "El nombre del medio es obligatorio" }
        require(headline.isNotBlank()) { "El titular o resumen de la noticia es obligatorio" }

        val existing = getRecord(pointId, initialReportedAt, clockType)
        val updated = existing.copy(
            clockType = clockType,
            isIntervened = true,
            intervenedAt = intervenedAt,
            mediaName = mediaName.trim(),
            mediaUrl = mediaUrl.trim().takeIf { it.isNotBlank() },
            interventionHeadline = headline.trim(),
            journalistName = journalistName.trim().takeIf { it.isNotBlank() },
            isReactivated = false,
        )

        saveRecord(updated)
        updated
    }

    fun reportReactivation(
        pointId: String,
        initialReportedAt: Long,
        reason: String,
        reactivatedAt: Long = System.currentTimeMillis(),
    ): Result<DrugImpunityRecord> = runCatching {
        val existing = getRecord(pointId, initialReportedAt, ImpunityClockType.DRUG_SALE)
        val updated = existing.copy(
            isReactivated = true,
            reactivatedAt = reactivatedAt,
            reactivationReason = reason.trim().takeIf { it.isNotBlank() },
            cycleCount = existing.cycleCount + 1,
            isIntervened = false,
        )

        saveRecord(updated)
        updated
    }

    private fun saveRecord(record: DrugImpunityRecord) {
        val serialized = json.encodeToString(record)
        prefs.edit().putString("point_${record.pointId}", serialized).apply()
        _recordsFlow.update { current ->
            current + (record.pointId to record)
        }
    }

    fun registerPressProfile(
        mediaOutlet: String,
        journalistFullName: String,
        pressCardId: String,
    ) {
        val profile = PressCredentialProfile(
            isRegisteredPress = true,
            mediaOutlet = mediaOutlet.trim(),
            journalistFullName = journalistFullName.trim(),
            pressCardId = pressCardId.trim(),
        )
        val raw = json.encodeToString(profile)
        prefs.edit().putString("press_profile", raw).apply()
        _pressProfile.value = profile
    }

    private fun loadPressProfile(): PressCredentialProfile {
        val raw = prefs.getString("press_profile", null) ?: return PressCredentialProfile()
        return runCatching { json.decodeFromString<PressCredentialProfile>(raw) }.getOrDefault(PressCredentialProfile())
    }

    fun saveParticipantProfile(profile: SafetyParticipantProfile) {
        val raw = json.encodeToString(profile.copy(registeredAt = System.currentTimeMillis()))
        prefs.edit().putString("safety_participant_profile", raw).apply()
        _participantProfile.value = profile

        // If registered as press, also update press profile
        if (profile.role == "JOURNALIST" && profile.organizationOrMedia.isNotBlank()) {
            registerPressProfile(
                mediaOutlet = profile.organizationOrMedia,
                journalistFullName = profile.fullName,
                pressCardId = profile.credentialNumber,
            )
        }
    }

    fun registerCivilianProfile(
        aliasOrPseudonym: String = "Ciudadano Anónimo",
        isAnonymousProtected: Boolean = true,
    ) {
        val cleanAlias = aliasOrPseudonym.trim().ifEmpty { "Ciudadano Anónimo" }
        val profile = SafetyParticipantProfile(
            role = "CIVILIAN",
            fullName = cleanAlias,
            organizationOrMedia = "",
            phoneOrContact = "",
            isAnonymousProtected = isAnonymousProtected,
            registeredAt = System.currentTimeMillis(),
        )
        saveParticipantProfile(profile)
    }

    private fun loadParticipantProfile(): SafetyParticipantProfile {
        val raw = prefs.getString("safety_participant_profile", null) ?: return SafetyParticipantProfile()
        return runCatching { json.decodeFromString<SafetyParticipantProfile>(raw) }.getOrDefault(SafetyParticipantProfile())
    }
}
