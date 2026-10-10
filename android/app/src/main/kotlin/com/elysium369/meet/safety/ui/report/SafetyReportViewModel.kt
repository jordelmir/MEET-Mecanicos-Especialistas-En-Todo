package com.elysium369.meet.safety.ui.report

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.safety.data.SafetyRepository
import com.elysium369.meet.safety.evidence.SafetyEvidenceEntity
import com.elysium369.meet.safety.evidence.SafetyEvidenceRepository
import com.elysium369.meet.safety.evidence.SafetyEvidencePolicy
import com.elysium369.meet.safety.location.FusedSafetyLocationProvider
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.ride.map.RidePlaceSuggestion
import com.elysium369.meet.ride.map.resilientRidePlaceSearchProvider
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.TimeoutCancellationException
import com.elysium369.meet.safety.domain.CreateSafetyReportPayload
import com.elysium369.meet.safety.domain.LocationSource
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.SourceRelation
import com.elysium369.meet.safety.domain.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.elysium369.meet.safety.science.data.SafetyScienceDao
import com.elysium369.meet.safety.science.application.SafetyReportEvidenceInput
import com.elysium369.meet.safety.science.application.SafetyReportScientificProjectionFactory
import com.elysium369.meet.safety.science.application.SafetyReportScientificProjectionInput
import com.elysium369.meet.safety.science.data.SciEntityEntity
import kotlinx.coroutines.delay
import javax.inject.Inject

data class SafetyDraftLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val source: LocationSource = LocationSource.DEVICE,
)

data class SafetyReportUiState(
    val step: Int = 0,
    val totalSteps: Int = 7,
    val category: SafetyReportCategory? = null,
    val sourceRelation: SourceRelation? = null,
    val narrative: String = "",
    val occurredAtIso: String? = null,
    val location: SafetyDraftLocation? = null,
    val locating: Boolean = false,
    val staging: Boolean = false,
    val evidence: List<SafetyEvidenceEntity> = emptyList(),
    val submitting: Boolean = false,
    val createdReportId: String? = null,
    val error: String? = null,
    val locationQuery: String = "",
    val locationSuggestions: List<RidePlaceSuggestion> = emptyList(),
    val searchingLocation: Boolean = false,
    // V2 — Victim demographics (optional, shown only for HOMICIDE)
    val victimCount: Int? = null,
    val victimFemale: Int? = null,
    val victimMale: Int? = null,
    // V3 — External video links
    val videoUrls: List<String> = emptyList(),
    val videoInputText: String = "",
    // V4 — Detailed participant registration
    val participantDetails: String = "",
    // V5 — Scientific Hypotheses & Popperian Analysis
    val enableScientificAnalysis: Boolean = false,
    val scientificHypothesis: String = "",
    val nullHypothesis: String = "",
    val falsificationCriteria: String = "",
    val factualClaim: String = "",
    // V6 — Financial Intelligence, SICOP & Corporate Integrity
    val sicopProcedureNumber: String = "",
    val economicEntityName: String = "",
    val economicEntityTaxId: String = "",
    val contractAmountMajor: String = "",
    val contractCurrency: String = "CRC",
    val officialDocumentSource: String = "",
) {
    /** Whether victim demographics step should be shown (only for homicide). */
    val showVictimStep: Boolean get() = category == SafetyReportCategory.HOMICIDE

    /** Whether financial intelligence & SICOP investigation section is active for this category. */
    val isFinancialCategory: Boolean get() = category in setOf(
        SafetyReportCategory.CORRUPTION_PUBLIC_PROCUREMENT,
        SafetyReportCategory.CORPORATE_OPACITY_CONFLICT,
        SafetyReportCategory.FINANCIAL_FRAUD,
    )
}

@HiltViewModel
class SafetyReportViewModel @Inject constructor(
    private val repository: SafetyRepository,
    private val locationProvider: FusedSafetyLocationProvider,
    private val evidenceRepository: SafetyEvidenceRepository,
    private val safetyScienceDao: SafetyScienceDao,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val placeSearch = resilientRidePlaceSearchProvider(BuildConfig.RIDE_GEOCODER_URL, BuildConfig.RIDE_GEOCODER_FALLBACK_URL)
    private var searchJob: Job? = null

    private val draftId: String = savedState.get<String>("safetyDraftId") ?: UUID.randomUUID().toString().also { savedState["safetyDraftId"] = it }
    private val _state = MutableStateFlow(SafetyReportUiState(
        category = savedState.get<String>("category")?.let { runCatching { SafetyReportCategory.valueOf(it) }.getOrNull() },
        sourceRelation = savedState.get<String>("relation")?.let { runCatching { SourceRelation.valueOf(it) }.getOrNull() },
        occurredAtIso = savedState["occurredAt"],
    ))
    val state: StateFlow<SafetyReportUiState> = _state.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val createdReport: StateFlow<com.elysium369.meet.safety.data.local.SafetyReportEntity?> = _state
        .flatMapLatest { s ->
            val id = s.createdReportId
            if (id != null) repository.observeReport(id) else kotlinx.coroutines.flow.flowOf(null)
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { evidenceRepository.observe(draftId).collect { evidence -> _state.update { it.copy(evidence = evidence) } } }
        viewModelScope.launch { repository.observeMyReports().collect { reports ->
            if (reports.any { it.reportId == draftId }) _state.update { it.copy(createdReportId = draftId) }
        } }
    }

    fun requestLocation() {
        if (_state.value.locating) return
        viewModelScope.launch {
            _state.update { it.copy(locating = true, error = null) }
            try {
                val sample = locationProvider.currentLocation()
                updateLocation(SafetyDraftLocation(sample.latitude, sample.longitude, sample.accuracyMeters))
            } catch (_: TimeoutCancellationException) {
                locationPermissionError("La ubicación tardó demasiado. Puedes reintentar o continuar sin ubicación.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { locationPermissionError("Ubicación no disponible. Activa la ubicación y revisa los permisos, o continúa sin ella.") }
            finally { _state.update { it.copy(locating = false) } }
        }
    }

    fun locationPermissionError(message: String = "Permiso de ubicación no concedido. Puedes continuar sin ubicación.") {
        _state.update { it.copy(error = message) }
    }

    fun attachEvidence(uri: Uri) {
        if (_state.value.staging || _state.value.submitting) return
        if (_state.value.evidence.size >= SafetyEvidencePolicy.MAX_ATTACHMENTS) {
            _state.update { it.copy(error = "Puedes adjuntar hasta 5 archivos.") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(staging = true, error = null) }
            try { evidenceRepository.stage(draftId, uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(error = if (error is IllegalArgumentException) error.message else "No se pudo guardar el archivo de forma segura.") } }
            finally { _state.update { it.copy(staging = false) } }
        }
    }

    fun removeEvidence(id: String) {
        if (_state.value.submitting || _state.value.staging) return
        viewModelScope.launch {
            try { evidenceRepository.removeDraft(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(error = "No se pudo quitar el archivo.") } }
        }
    }

    fun selectCategory(category: SafetyReportCategory) {
        savedState["category"] = category.name
        _state.update { it.copy(category = category, error = null) }
    }

    fun selectSourceRelation(relation: SourceRelation) {
        savedState["relation"] = relation.name
        _state.update { it.copy(sourceRelation = relation, error = null) }
    }

    fun updateNarrative(text: String) {
        // Narrative remains only in memory until the encrypted transactional save.
        _state.update { it.copy(narrative = text.take(30_000), error = null) }
    }

    fun updateOccurredAt(iso: String?) {
        savedState["occurredAt"] = iso
        _state.update { it.copy(occurredAtIso = iso, error = null) }
    }

    fun addVideoUrl(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        val formatted = if (!clean.startsWith("http://") && !clean.startsWith("https://")) "https://$clean" else clean
        _state.update { current ->
            if (current.videoUrls.contains(formatted)) current
            else current.copy(videoUrls = current.videoUrls + formatted, error = null)
        }
    }

    fun removeVideoUrl(url: String) {
        _state.update { current ->
            current.copy(videoUrls = current.videoUrls.filter { it != url }, error = null)
        }
    }

    fun updateVideoInputText(text: String) {
        _state.update { it.copy(videoInputText = text, error = null) }
    }

    fun commitVideoInput() {
        val text = _state.value.videoInputText.trim()
        if (text.isNotBlank()) {
            addVideoUrl(text)
            _state.update { it.copy(videoInputText = "") }
        }
    }

    fun updateVictimCount(count: Int?) {
        _state.update { it.copy(victimCount = count, error = null) }
    }

    fun updateVictimFemale(count: Int?) {
        _state.update { it.copy(victimFemale = count, error = null) }
    }

    fun updateVictimMale(count: Int?) {
        _state.update { it.copy(victimMale = count, error = null) }
    }

    fun updateLocation(location: SafetyDraftLocation?) {
        _state.update { it.copy(location = location, error = null) }
    }

    fun updateLocationQuery(query: String) {
        _state.update { it.copy(locationQuery = query.take(180), locationSuggestions = if (query.length < 3) emptyList() else it.locationSuggestions) }
        searchJob?.cancel()
        if (query.trim().length < 3) return
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searchingLocation = true) }
            try {
                val current = _state.value.location
                val results = placeSearch.search(query, current?.latitude, current?.longitude, 6)
                _state.update { it.copy(locationSuggestions = results, searchingLocation = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(searchingLocation = false, error = "No se pudo buscar esa ubicación. Reintenta o selecciónala en el mapa.") } }
        }
    }

    fun selectPlace(place: RidePlaceSuggestion) {
        _state.update { it.copy(location = SafetyDraftLocation(place.latitude, place.longitude, null, LocationSource.USER_DESCRIPTION), locationQuery = place.displayLabel, locationSuggestions = emptyList(), error = null) }
    }

    fun selectMapPoint(latitude: Double, longitude: Double) {
        _state.update { it.copy(location = SafetyDraftLocation(latitude, longitude, null, LocationSource.MAP_SELECTION), locationSuggestions = emptyList(), error = null) }
    }

    fun nextStep() {
        commitVideoInput()
        val s = _state.value
        when {
            s.step == 0 && s.sourceRelation == null -> return
            s.step == 2 && s.category == null -> return
            // Step 3 = VictimDemographics: skip if not HOMICIDE
            s.step == 2 && s.category != null && !s.showVictimStep -> {
                _state.update { it.copy(step = 4) }; return
            }
            s.step == 4 && s.narrative.trim().length < 10 -> return
            s.step == 5 && s.occurredAtIso != null && runCatching { Instant.parse(s.occurredAtIso) }.isFailure -> {
                _state.update { it.copy(error = "Indica una fecha ISO con zona horaria, por ejemplo 2026-09-19T14:30:00-06:00.") }; return
            }
            s.staging || s.locating -> return
            s.step < s.totalSteps -> _state.update { it.copy(step = s.step + 1) }
        }
    }

    fun previousStep() {
        val s = _state.value
        if (s.step > 0) {
            // Skip victim step (3) when going back from narrative (4) if not HOMICIDE
            val target = if (s.step == 4 && !s.showVictimStep) 2 else s.step - 1
            _state.update { it.copy(step = target) }
        }
    }

    fun goToStep(step: Int) {
        if (step in 0.._state.value.totalSteps) {
            _state.update { it.copy(step = step) }
        }
    }

    fun updateParticipantDetails(details: String) {
        _state.update { it.copy(participantDetails = details) }
    }

    fun toggleScientificAnalysis(enabled: Boolean) {
        _state.update { it.copy(enableScientificAnalysis = enabled) }
    }

    fun updateScientificHypothesis(value: String) {
        _state.update { it.copy(scientificHypothesis = value) }
    }

    fun updateNullHypothesis(value: String) {
        _state.update { it.copy(nullHypothesis = value) }
    }

    fun updateFalsificationCriteria(value: String) {
        _state.update { it.copy(falsificationCriteria = value) }
    }

    fun updateFactualClaim(value: String) {
        _state.update { it.copy(factualClaim = value) }
    }

    fun updateSicopProcedureNumber(value: String) {
        _state.update { it.copy(sicopProcedureNumber = value.take(80)) }
    }

    fun updateEconomicEntityName(value: String) {
        _state.update { it.copy(economicEntityName = value.take(120)) }
    }

    fun updateEconomicEntityTaxId(value: String) {
        _state.update { it.copy(economicEntityTaxId = value.take(40)) }
    }

    fun updateContractAmountMajor(value: String) {
        _state.update { it.copy(contractAmountMajor = value.filter { c -> c.isDigit() || c == '.' }.take(20)) }
    }

    fun updateContractCurrency(value: String) {
        _state.update { it.copy(contractCurrency = value.take(10)) }
    }

    fun updateOfficialDocumentSource(value: String) {
        _state.update { it.copy(officialDocumentSource = value.take(300)) }
    }

    fun submit() {
        commitVideoInput()
        val snapshot = _state.value
        if (snapshot.submitting || snapshot.staging || snapshot.locating) return
        if (snapshot.category == null || snapshot.sourceRelation == null) return
        if (snapshot.narrative.trim().length < 10) return
        if (snapshot.enableScientificAnalysis) {
            val missingScientificFields =
                snapshot.scientificHypothesis.trim().length < 10 ||
                    snapshot.nullHypothesis.trim().length < 10 ||
                    snapshot.falsificationCriteria.trim().length < 10
            if (missingScientificFields) {
                _state.update {
                    it.copy(
                        error = "Para crear análisis científico, completa hipótesis, hipótesis nula y criterio de falsación (mínimo 10 caracteres cada uno), o desactiva esa opción.",
                    )
                }
                return
            }
        }

        // Extract any URLs typed or pasted directly into the narrative
        val narrativeUrls = Regex("""(https?://[^\s]+)""").findAll(snapshot.narrative)
            .map { it.value.trimEnd('.', ',', ';', ')', ']', '>') }
            .toList()
        val allVideoUrls = (snapshot.videoUrls + narrativeUrls)
            .filter { it.isNotBlank() }
            .distinct()

        val baseNarrative = if (snapshot.participantDetails.isNotBlank()) {
            "[Registro de Fuente: ${snapshot.sourceRelation?.label() ?: "Reporte"} - ${snapshot.participantDetails.trim()}]\n\n" + snapshot.narrative.trim()
        } else {
            snapshot.narrative.trim()
        }

        val financialSummary = buildString {
            if (snapshot.sicopProcedureNumber.isNotBlank()) append("\n- Procedimiento SICOP: ").append(snapshot.sicopProcedureNumber.trim())
            if (snapshot.economicEntityName.isNotBlank()) append("\n- Entidad / Empresa: ").append(snapshot.economicEntityName.trim())
            if (snapshot.economicEntityTaxId.isNotBlank()) append("\n- Cédula Jurídica / ID Fiscal: ").append(snapshot.economicEntityTaxId.trim())
            if (snapshot.contractAmountMajor.isNotBlank()) append("\n- Monto Referencial: ").append(snapshot.contractCurrency).append(" ").append(snapshot.contractAmountMajor.trim())
            if (snapshot.officialDocumentSource.isNotBlank()) append("\n- Fuente Oficial / Expediente: ").append(snapshot.officialDocumentSource.trim())
        }

        val narrativeWithFinancial = if (financialSummary.isNotBlank()) {
            "[INTELIGENCIA FINANCIERA & CONTRATACIÓN PÚBLICA]\n" +
            "Aviso de Salvaguarda: La tenencia de vehículos de alta gama o bienes de alto valor no constituye delito por sí misma.$financialSummary\n\n" +
            baseNarrative
        } else {
            baseNarrative
        }

        // Ensure video URLs are permanently preserved in narrative for worldwide read
        val finalNarrative = if (allVideoUrls.isNotEmpty()) {
            val missingInNarrative = allVideoUrls.filterNot { narrativeWithFinancial.contains(it) }
            if (missingInNarrative.isNotEmpty()) {
                narrativeWithFinancial + "\n\n[VIDEOS ADJUNTOS]\n" + missingInNarrative.joinToString("\n")
            } else {
                narrativeWithFinancial
            }
        } else {
            narrativeWithFinancial
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }

            try {
                val reportId = repository.createReport(
                    reportId = draftId,
                    evidenceIds = snapshot.evidence.map { it.evidenceId },
                    payload = CreateSafetyReportPayload(
                        category = snapshot.category,
                        narrative = finalNarrative,
                        sourceRelation = snapshot.sourceRelation,
                        occurredAtIso = snapshot.occurredAtIso,
                        latitude = snapshot.location?.latitude,
                        longitude = snapshot.location?.longitude,
                        accuracyMeters = snapshot.location?.accuracyMeters,
                        locationSource = snapshot.location?.source ?: LocationSource.NONE,
                        reportedVictimCount = snapshot.victimCount,
                        reportedVictimFemale = snapshot.victimFemale,
                        reportedVictimMale = snapshot.victimMale,
                        videoUrls = allVideoUrls,
                    ),
                )

                // The report is saved by SafetyRepository first. Its scientific projection is
                // a separate LOCAL transaction: failure must not mislabel the report save.
                val projectionWarning = try {
                    val now = System.currentTimeMillis()
                    val occurredMs = snapshot.occurredAtIso?.let {
                        runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
                    }
                    if (snapshot.economicEntityName.isNotBlank()) {
                        val entityId = "ent_" + UUID.randomUUID().toString().take(12)
                        val extIds = mutableListOf<String>()
                        if (snapshot.economicEntityTaxId.isNotBlank()) extIds.add("tax_id:${snapshot.economicEntityTaxId.trim()}")
                        if (snapshot.sicopProcedureNumber.isNotBlank()) extIds.add("sicop:${snapshot.sicopProcedureNumber.trim()}")
                        val extIdsJson = if (extIds.isNotEmpty()) {
                            extIds.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]") { it.replace("\"", "\\\"") }
                        } else "[]"

                        safetyScienceDao.upsertEntity(
                            SciEntityEntity(
                                id = entityId,
                                entityType = if (snapshot.category == SafetyReportCategory.CORRUPTION_PUBLIC_PROCUREMENT) "PUBLIC_CONTRACTOR" else "ECONOMIC_ENTITY",
                                canonicalName = snapshot.economicEntityName.trim(),
                                aliasesJson = "[]",
                                externalIdentifiersJson = extIdsJson,
                                assertionState = "OBSERVED",
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                    }

                    val projection = SafetyReportScientificProjectionFactory.build(
                        SafetyReportScientificProjectionInput(
                            reportId = reportId,
                            category = requireNotNull(snapshot.category),
                            narrative = snapshot.narrative,
                            factualClaim = snapshot.factualClaim,
                            sourceRelation = requireNotNull(snapshot.sourceRelation),
                            scientificHypothesis = snapshot.scientificHypothesis,
                            nullHypothesis = snapshot.nullHypothesis,
                            falsificationCriteria = snapshot.falsificationCriteria,
                            enableScientificAnalysis = snapshot.enableScientificAnalysis,
                            occurredAt = occurredMs,
                            recordedAt = now,
                            evidence = snapshot.evidence.map {
                                SafetyReportEvidenceInput(
                                    evidenceId = it.evidenceId,
                                    contentSha256 = it.contentSha256,
                                )
                            },
                        ),
                    )
                    safetyScienceDao.persistReportProjection(
                        claim = projection.claim,
                        hypothesis = projection.hypothesis,
                        event = projection.event,
                        evidenceLinks = projection.claimEvidenceLinks,
                        provenanceNodes = projection.provenanceNodes,
                        provenanceEdges = projection.provenanceEdges,
                    )
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (projectionError: Exception) {
                    "El reporte se registró en Safety, pero no se pudo completar el índice científico local. La relación de evidencia queda pendiente de reparación; esto no confirma el hecho reportado."
                }

                _state.update {
                    it.copy(
                        submitting = false,
                        createdReportId = reportId,
                        step = it.totalSteps,
                        error = projectionWarning,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        submitting = false,
                        error = error.message ?: "No se pudo guardar el reporte",
                    )
                }
            }
        }
    }
}
