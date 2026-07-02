package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * Marketplace gating — ensures the marketplace never offers a part
 * before the user has the minimum evidence to consider replacing it.
 *
 * Rules (per spec):
 *  - "No mostrar 'comprar bomba' primero sin pruebas"
 *  - "Compra recomendada solo después de confirmar la prueba indicada"
 *  - Each cause has a list of "pending tests" that must be completed
 *    before the marketplace shows the part as buyable.
 */
@Serializable
data class MarketplaceOffer(
    val partName: String,
    val associatedCause: String,
    val associatedComponent: String,
    val requiredTests: List<String> = emptyList(),
    val estimatedCostRange: String = "",
    val notes: String = ""
)

@Serializable
data class MarketplaceGatingResult(
    val offers: List<MarketplaceOffer>,
    val blockedOffers: List<MarketplaceOffer>,
    val disclaimer: String
) {
    fun hasActionableOffers(): Boolean = offers.isNotEmpty()
    fun allBlockedReason(): String = blockedOffers.joinToString("\n") {
        "${it.partName}: faltan pruebas ${it.requiredTests.joinToString(", ")}"
    }
}

class MarketplaceGating {

    /**
     * Filter the offers based on completed tests.
     * An offer becomes visible (actionable) ONLY when ALL its required
     * tests appear in completedTests.
     */
    fun gate(
        offers: List<MarketplaceOffer>,
        completedTests: List<String>
    ): MarketplaceGatingResult {
        val (ready, blocked) = offers.partition { offer ->
            offer.requiredTests.all { it in completedTests }
        }
        return MarketplaceGatingResult(
            offers = ready,
            blockedOffers = blocked,
            disclaimer = "Compra recomendada solo después de confirmar la prueba indicada."
        )
    }

    /**
     * Generate the educational disclaimer text per spec — the marketplace
     * never pushes parts. The text is shown next to any blocked offer.
     */
    companion object {
        const val DEFAULT_DISCLAIMER =
            "Compra recomendada solo después de confirmar la prueba indicada."

        fun pumpOfferExample(
            dtcCode: String,
            vehicle: String
        ): MarketplaceOffer = MarketplaceOffer(
            partName = "Bomba de combustible",
            associatedCause = "Falla de bomba electrica",
            associatedComponent = "fuel_pump",
            requiredTests = listOf(
                "battery_check",
                "fuse_check",
                "relay_check",
                "wiring_check",
                "pump_voltage_check"
            ),
            estimatedCostRange = "$80-$250 USD",
            notes = "Solo despues de verificar voltaje al conector de la bomba y tierra < 0.1V."
        )
    }
}
