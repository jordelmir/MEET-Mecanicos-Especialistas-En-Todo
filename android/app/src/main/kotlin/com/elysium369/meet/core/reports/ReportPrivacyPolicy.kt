package com.elysium369.meet.core.reports

/** Explicit disclosure contract; public sharing is never inferred from a URI. */
enum class ReportDisclosureAudience {
    VEHICLE_OWNER,
    AUTHORIZED_WORKSHOP,
    PUBLIC_VERIFIER,
}

data class ReportPrivacyPolicy(
    val audience: ReportDisclosureAudience,
    val revealFullVin: Boolean,
    val revealPlate: Boolean,
    val revealExactLocation: Boolean,
) {
    init {
        if (audience == ReportDisclosureAudience.PUBLIC_VERIFIER) {
            require(!revealFullVin && !revealPlate && !revealExactLocation) {
                "Public verifier policy cannot expose VIN, plate or exact location"
            }
        }
    }

    fun displayVin(vin: String?): String? = vin?.let {
        if (revealFullVin) it else "•••••••••••••${it.takeLast(4)}"
    }

    fun displayPlate(plate: String?): String? = plate?.let {
        if (revealPlate) it else "PLACA OCULTA"
    }

    companion object {
        val OWNER_COPY = ReportPrivacyPolicy(
            audience = ReportDisclosureAudience.VEHICLE_OWNER,
            revealFullVin = true,
            revealPlate = true,
            revealExactLocation = true,
        )
        val PUBLIC_VERIFIER = ReportPrivacyPolicy(
            audience = ReportDisclosureAudience.PUBLIC_VERIFIER,
            revealFullVin = false,
            revealPlate = false,
            revealExactLocation = false,
        )
    }
}
