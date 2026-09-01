package com.elysium369.meet.ui.screens.marketos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.legal.data.LegalTriageGateway
import com.elysium369.meet.legal.data.LegalTriageSuggestion
import com.elysium369.meet.legal.domain.LegalEvidenceLedger
import com.elysium369.meet.fuel.domain.FuelLedger
import com.elysium369.meet.vehicle.ActiveVehicleKernel
import com.elysium369.meet.observability.MeetTelemetry
import com.elysium369.meet.platform.marketos.data.MarketCatalogCategory
import com.elysium369.meet.platform.marketos.data.MarketEnqueueResult
import com.elysium369.meet.platform.marketos.data.MarketOsRepository
import com.elysium369.meet.platform.marketos.data.MarketRefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class MarketConnectionState { LOCAL_ONLY, CONNECTING, LIVE, RECOVERING, AUTHENTICATION_REQUIRED }

@HiltViewModel
class MarketOsViewModel @Inject constructor(
    private val repository: MarketOsRepository,
    private val principalKernel: ActivePrincipalKernel,
    private val legalEvidenceLedger: LegalEvidenceLedger,
    private val fuelLedger: FuelLedger,
    private val activeVehicleKernel: ActiveVehicleKernel,
) : ViewModel() {
    val organizations = repository.organizations.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val legalMatters = repository.legalMatters.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val propertyListings = repository.propertyListings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val fuelCoupons = repository.fuelCoupons.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val pendingCommands = repository.pendingCommands.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0,
    )
    val legalTimeline = legalEvidenceLedger.timeline.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val legalCases = legalEvidenceLedger.cases.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val legalEvidence = legalEvidenceLedger.evidence.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val fuelTransactions = fuelLedger.transactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val confirmedFuelRewards = fuelLedger.confirmedRewards.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )

    private val _legalCatalog = MutableStateFlow<List<MarketCatalogCategory>>(emptyList())
    val legalCatalog: StateFlow<List<MarketCatalogCategory>> = _legalCatalog.asStateFlow()
    private val _legalTriage = MutableStateFlow<LegalTriageSuggestion?>(null)
    val legalTriage: StateFlow<LegalTriageSuggestion?> = _legalTriage.asStateFlow()
    private val _connectionState = MutableStateFlow(MarketConnectionState.LOCAL_ONLY)
    val connectionState: StateFlow<MarketConnectionState> = _connectionState.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private var projectionJob: Job? = null

    init {
        viewModelScope.launch {
            principalKernel.activePrincipal.collect { principal ->
                projectionJob?.cancel()
                if (!principal.canSyncToCloud) {
                    _connectionState.value = MarketConnectionState.AUTHENTICATION_REQUIRED
                } else {
                    startProjectionSync()
                }
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    fun createLegalMatter(categoryCode: String, summary: String, urgency: String = "NORMAL") {
        viewModelScope.launch {
            if (_legalCatalog.value.none { it.code == categoryCode }) {
                _notice.value = "Selecciona una categoría vigente del catálogo oficial."
                return@launch
            }
            val result = repository.enqueue(
                aggregateType = "LEGAL_MATTER",
                commandType = "CREATE_LEGAL_MATTER",
                expectedVersion = 0,
                payload = buildJsonObject {
                    put("p_category_code", categoryCode)
                    put("p_subcategory_code", JsonNull)
                    put("p_human_summary", summary.trim())
                    put("p_privileged_detail_ciphertext", JsonNull)
                    put("p_jurisdiction_code", "CR")
                    put("p_urgency", urgency)
                    put("p_parties", JsonArray(emptyList()))
                },
            )
            _notice.value = result.userMessage("Solicitud jurídica")
        }
    }

    fun recordLegalJournal(narrative: String) {
        viewModelScope.launch {
            runCatching { legalEvidenceLedger.recordQuickJournal(narrative) }
                .onSuccess { _notice.value = "Entrada cifrada guardada en tu línea de tiempo local." }
                .onFailure { _notice.value = "No se guardó la entrada: revisa que tenga al menos 3 caracteres." }
        }
    }

    fun createLegalCase(title: String) {
        viewModelScope.launch {
            runCatching {
                legalEvidenceLedger.createCase(
                    title = title,
                    vehicleId = activeVehicleKernel.activeVehicle.value?.id,
                )
            }.onSuccess {
                _notice.value = "Caso local cifrado creado; la vinculación al vehículo activo quedó preservada."
            }.onFailure {
                _notice.value = "No se creó el caso: usa un título de al menos 3 caracteres."
            }
        }
    }

    fun attachLegalEvidence(uri: Uri, mediaType: String, eventId: String?) {
        viewModelScope.launch {
            runCatching {
                legalEvidenceLedger.attachOriginalEvidence(
                    sourceUri = uri,
                    mediaType = mediaType,
                    eventId = eventId,
                )
            }.onSuccess {
                _notice.value = "Original copiado al almacenamiento privado y registrado con SHA-256."
            }.onFailure {
                _notice.value = "No se pudo preservar el archivo original; no se creó evidencia parcial."
            }
        }
    }

    fun requestLegalTriage(narrative: String, consent: Boolean) {
        viewModelScope.launch {
            _notice.value = "Analizando con privacidad y taxonomía vigente…"
            LegalTriageGateway.triage(narrative, consent)
                .onSuccess { suggestion ->
                    if (_legalCatalog.value.none { it.code == suggestion.primaryCategoryCode }) {
                        _notice.value = "La sugerencia no coincide con el catálogo vigente; no se aplicó."
                        return@onSuccess
                    }
                    _legalTriage.value = suggestion
                    _notice.value = "Sugerencia IA disponible. Tú decides si confirmarla. No es asesoría legal."
                    MeetTelemetry.event(
                        "legal.triage.completed",
                        mapOf(
                            "vertical" to "LEGAL",
                            "resultCode" to "AI_SUGGESTED",
                            "taxonomyVersion" to suggestion.taxonomyVersion,
                            "urgency" to suggestion.urgency,
                        ),
                    )
                }
                .onFailure {
                    _notice.value = "El triage IA no está disponible. No se creó ninguna clasificación automática."
                    MeetTelemetry.event(
                        "legal.triage.failed",
                        mapOf("vertical" to "LEGAL", "resultCode" to "UNAVAILABLE"),
                    )
                }
        }
    }

    fun redeemFuelCoupon(token: String, stationId: String, purchaseId: String?) {
        viewModelScope.launch {
            val result = repository.enqueue(
                aggregateType = "FUEL_COUPON",
                commandType = "REDEEM_FUEL_COUPON",
                expectedVersion = 0,
                payload = buildJsonObject {
                    put("p_opaque_token", token)
                    put("p_station_id", stationId)
                    if (purchaseId.isNullOrBlank()) put("p_purchase_id", JsonNull)
                    else put("p_purchase_id", purchaseId)
                },
            )
            _notice.value = result.userMessage("Redención")
        }
    }

    fun claimFuelPurchase(token: String) {
        viewModelScope.launch {
            val result = repository.enqueue(
                aggregateType = "FUEL_PURCHASE",
                commandType = "CLAIM_FUEL_PURCHASE",
                expectedVersion = 0,
                payload = buildJsonObject {
                    put("p_opaque_token", token)
                    put("p_campaign_version_id", JsonNull)
                },
            )
            _notice.value = result.userMessage("Reclamo de compra")
        }
    }

    fun recordFuelPurchase(amountCrc: String, liters: String, stationId: String, odometerKm: String) {
        viewModelScope.launch {
            val amountMinor = runCatching {
                java.math.BigDecimal(amountCrc.trim().replace(',', '.'))
                    .movePointRight(2).longValueExact()
            }.getOrNull()
            val volumeMilliLiters = runCatching {
                java.math.BigDecimal(liters.trim().replace(',', '.'))
                    .movePointRight(3).longValueExact()
            }.getOrNull()
            val odometerMeters = odometerKm.trim().takeIf(String::isNotBlank)?.let { raw ->
                runCatching {
                    java.math.BigDecimal(raw.replace(',', '.')).movePointRight(3).longValueExact()
                }.getOrNull()
            }
            if (amountMinor == null || volumeMilliLiters == null || (odometerKm.isNotBlank() && odometerMeters == null)) {
                _notice.value = "Monto o volumen inválido; no se guardó la compra."
                return@launch
            }
            runCatching {
                fuelLedger.recordDeclaredPurchase(
                    amountMinor = amountMinor,
                    currency = "CRC",
                    volumeMilliLiters = volumeMilliLiters,
                    vehicleId = activeVehicleKernel.activeVehicle.value?.id,
                    stationId = stationId,
                    odometerMeters = odometerMeters,
                )
            }.onSuccess {
                _notice.value = "Compra declarada guardada. No genera recompensa hasta confirmación del servidor."
            }.onFailure {
                _notice.value = "No se guardó la compra: revisa monto y litros."
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    private fun startProjectionSync() {
        if (projectionJob?.isActive == true) return
        projectionJob = viewModelScope.launch {
            _connectionState.value = MarketConnectionState.CONNECTING
            val realtime = repository.realtimeWakeUps()
                .retryWhen { _, attempt ->
                    if (!principalKernel.current().canSyncToCloud) {
                        _connectionState.value = MarketConnectionState.AUTHENTICATION_REQUIRED
                        false
                    } else {
                        _connectionState.value = MarketConnectionState.RECOVERING
                        delay((2_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(60_000L))
                        true
                    }
                }
            val heartbeat = kotlinx.coroutines.flow.flow {
                while (currentCoroutineContext().isActive) {
                    delay(60_000L)
                    emit(Unit)
                }
            }
            merge(realtime, heartbeat).onStart { emit(Unit) }.conflate().collect {
                refresh()
            }
        }
    }

    private suspend fun refresh() {
        when (val result = repository.refresh()) {
            is MarketRefreshResult.Refreshed -> {
                _connectionState.value = MarketConnectionState.LIVE
                repository.fetchCatalog("LEGAL").onSuccess { _legalCatalog.value = it }
            }
            MarketRefreshResult.AuthenticationRequired -> {
                _connectionState.value = MarketConnectionState.AUTHENTICATION_REQUIRED
            }
            is MarketRefreshResult.Failed -> {
                _connectionState.value = MarketConnectionState.RECOVERING
                _notice.value = "Sincronización pendiente: ${result.reason}"
            }
        }
    }
}

private fun MarketEnqueueResult.userMessage(noun: String): String = when (this) {
    is MarketEnqueueResult.Queued -> "$noun guardada de forma durable; esperando confirmación del servidor."
    MarketEnqueueResult.AuthenticationRequired -> "Inicia sesión para enviar esta operación al servidor."
    MarketEnqueueResult.Duplicate -> "La misma operación ya estaba en cola."
    is MarketEnqueueResult.Rejected -> "$noun rechazada localmente: $reason"
}
