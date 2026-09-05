package com.elysium369.meet.fulfillment.domain

import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ProviderType
import com.elysium369.meet.core.services.kernel.ServiceVertical
import java.util.UUID

/**
 * Universal reference to any domain aggregate managed under the Elysium Fulfillment OS.
 * Guarantees that the vertical and domain aggregate ID are strictly anchored.
 */
data class FulfillmentReference(
    val vertical: ServiceVertical,
    val aggregateId: UUID,
    val correlationId: String = UUID.randomUUID().toString(),
) {
    init {
        require(vertical != ServiceVertical.UNKNOWN) { "Fulfillment vertical cannot be UNKNOWN" }
    }
}

/**
 * Fulfillment execution modalities supported across MEET.
 */
enum class FulfillmentMode(val displayName: String) {
    ON_DEMAND_MOBILE("A Domicilio / Inmediato"),
    SCHEDULED_MOBILE("Programado en Sitio"),
    PICKUP_AND_DELIVERY("Recolección y Entrega"),
    DROP_OFF("En Taller / Centro de Servicio"),
    REMOTE("Digital / Telemétrico"),
    HYBRID("Híbrido"),
}

/**
 * Canonical lifecycle phases of any fulfillment operation.
 * Maps 1-to-1 with underlying domain state machines (Ride, Tow, Repair, Parts)
 * without replacing their domain-specific authorities.
 */
sealed interface FulfillmentPhase {
    val displayName: String
    val stepOrder: Int
    val isTerminal: Boolean
        get() = false

    object Configuring : FulfillmentPhase {
        override val displayName: String = "Configurando Servicio"
        override val stepOrder: Int = 0
    }

    object Searching : FulfillmentPhase {
        override val displayName: String = "Buscando Proveedor"
        override val stepOrder: Int = 1
    }

    object Offered : FulfillmentPhase {
        override val displayName: String = "Ofertas Disponibles"
        override val stepOrder: Int = 2
    }

    object Matched : FulfillmentPhase {
        override val displayName: String = "Proveedor Confirmado"
        override val stepOrder: Int = 3
    }

    object ProviderEnRoute : FulfillmentPhase {
        override val displayName: String = "En Camino"
        override val stepOrder: Int = 4
    }

    object ProviderArrived : FulfillmentPhase {
        override val displayName: String = "En el Lugar"
        override val stepOrder: Int = 5
    }

    object InProgress : FulfillmentPhase {
        override val displayName: String = "Servicio en Curso"
        override val stepOrder: Int = 6
    }

    object Completing : FulfillmentPhase {
        override val displayName: String = "Finalizando y Liquidando"
        override val stepOrder: Int = 7
    }

    object Completed : FulfillmentPhase {
        override val displayName: String = "Completado"
        override val stepOrder: Int = 8
        override val isTerminal: Boolean = true
    }

    data class Cancelled(val reason: String = "Cancelado por el usuario") : FulfillmentPhase {
        override val displayName: String = "Cancelado"
        override val stepOrder: Int = 99
        override val isTerminal: Boolean = true
    }

    data class Disputed(val reason: String = "Operación en disputa") : FulfillmentPhase {
        override val displayName: String = "En Disputa"
        override val stepOrder: Int = 98
        override val isTerminal: Boolean = true
    }

    data class Failed(val message: String = "Error en la ejecución") : FulfillmentPhase {
        override val displayName: String = "Error de Servicio"
        override val stepOrder: Int = 100
        override val isTerminal: Boolean = true
    }
}

/**
 * Minor-unit safe pricing representations across the fulfillment lifecycle.
 */
sealed interface FulfillmentPricing {
    data class EstimatedRange(
        val min: Money,
        val max: Money,
    ) : FulfillmentPricing {
        init {
            require(min.currency == max.currency) { "Currencies must match in range" }
            require(min.amountMinor <= max.amountMinor) { "Min amount must be <= max amount" }
        }
    }

    data class Quote(
        val amount: Money,
        val breakdown: List<PricingItem> = emptyList(),
        val expiresAtEpochMs: Long? = null,
    ) : FulfillmentPricing

    data class AuthorizedAmount(
        val amount: Money,
        val authorizationId: String? = null,
    ) : FulfillmentPricing

    data class FinalSettlement(
        val base: Money,
        val extras: Money,
        val taxes: Money,
        val total: Money,
        val ledgerAttestationHash: String? = null,
    ) : FulfillmentPricing {
        init {
            require(base.currency == extras.currency && extras.currency == taxes.currency && taxes.currency == total.currency) {
                "All settlement currencies must match: base=${base.currency}, extras=${extras.currency}, taxes=${taxes.currency}, total=${total.currency}"
            }
            val expectedTotal = (base + extras) + taxes
            require(total == expectedTotal) {
                "Settlement total (${total.amountMinor} ${total.currency}) must equal (base + extras) + taxes (${expectedTotal.amountMinor} ${expectedTotal.currency})"
            }
        }
    }
}

data class PricingItem(
    val label: String,
    val amount: Money,
)

/**
 * Provider identity and vehicle profile projected into the visual shell.
 */
data class FulfillmentProviderInfo(
    val id: String,
    val name: String,
    val rating: Double? = null,
    val totalJobs: Int? = null,
    val avatarUrl: String? = null,
    val phone: String? = null,
    val vehicleDescription: String? = null,
    val licensePlate: String? = null,
    val providerType: ProviderType,
    val etaMinutes: Int? = null,
    val distanceMeters: Long? = null,
    val currentPoint: GeoPoint? = null,
)

/**
 * Historical or active milestone along the fulfillment execution.
 */
data class FulfillmentTimelineEvent(
    val phase: String,
    val title: String,
    val description: String,
    val timestampEpochMs: Long,
    val isCompleted: Boolean,
    val isCurrent: Boolean,
)

/**
 * Cryptographic or physical proof record attached to a fulfillment phase.
 */
data class FulfillmentEvidenceSnapshot(
    val evidenceId: String? = null,
    val label: String,
    val sha256Hash: String,
    val capturedAtEpochMs: Long,
    val uri: String? = null,
    val verificationLevel: String = "UNVERIFIED",
)

/**
 * User action intent dispatched from the unified shell.
 */
sealed interface FulfillmentUiAction {
    object Cancel : FulfillmentUiAction
    object ContactCall : FulfillmentUiAction
    object ContactMessage : FulfillmentUiAction
    object ContactPtt : FulfillmentUiAction
    object SafetyCenter : FulfillmentUiAction
    object ShareProgress : FulfillmentUiAction
    data class AuthorizeQuote(val quoteId: String) : FulfillmentUiAction
    data class SubmitEvidence(val label: String, val photoUri: String) : FulfillmentUiAction
    object ConfirmCompletion : FulfillmentUiAction
    data class OpenDispute(val reason: String) : FulfillmentUiAction
}

/**
 * Universal, read-only presentation projection consumed by the Fulfillment UI Shell.
 * Aggregates state from any underlying vertical (Ride, Tow, Repair, Parts).
 */
data class FulfillmentProjection(
    val reference: FulfillmentReference,
    val mode: FulfillmentMode,
    val phase: FulfillmentPhase,
    val vertical: ServiceVertical,
    val serviceName: String,
    val serviceDescription: String,
    val userLocation: GeoPoint? = null,
    val targetLocation: GeoPoint? = null,
    val destinationLocation: GeoPoint? = null,
    val provider: FulfillmentProviderInfo? = null,
    val pricing: FulfillmentPricing? = null,
    val timeline: List<FulfillmentTimelineEvent> = emptyList(),
    val evidenceSnapshots: List<FulfillmentEvidenceSnapshot> = emptyList(),
    val mapState: CommonMapState = CommonMapState(),
    val canCancel: Boolean = false,
    val canMessage: Boolean = false,
    val canCall: Boolean = false,
    val canPTT: Boolean = false,
    val disputeReason: String? = null,
)

/**
 * Contract for adapting vertical-specific domain models into the universal FulfillmentProjection.
 */
interface FulfillmentPresentationAdapter<T> {
    fun toFulfillmentProjection(source: T): FulfillmentProjection
}
