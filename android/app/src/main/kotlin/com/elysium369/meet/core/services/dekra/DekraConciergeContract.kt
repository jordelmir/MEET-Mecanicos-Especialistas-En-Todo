package com.elysium369.meet.core.services.dekra

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class DekraAppointmentMode(val displayName: String) {
    CONFIRMED("Ya tengo cita DEKRA"),
    NEEDS_COORDINATION("Necesito apoyo para coordinarla"),
}

@Serializable
enum class DekraVehicleCondition(val displayName: String) {
    NORMAL("Circula normalmente"),
    SAFETY_CONCERN("Tiene una condición que debe evaluarse"),
    IMMOBILIZED("No circula / está inmovilizado"),
}

@Serializable
enum class DekraTransportPlan(val displayName: String) {
    DRIVE_AFTER_PRECHECK("Conducir únicamente si aprueba el prechequeo MEET"),
    TOW_ONLY("Traslado en grúa; no conducir"),
}

@Serializable
data class DekraConciergeRequest(
    val id: String = UUID.randomUUID().toString(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val vehicleId: String,
    val vehicleDisplayName: String,
    val maskedVin: String?,
    val maskedPlate: String,
    val appointmentMode: DekraAppointmentMode,
    val station: String,
    val appointmentDateTime: String,
    val reservationCode: String?,
    val pickupZone: String,
    val contactPhone: String,
    val vehicleCondition: DekraVehicleCondition,
    val transportPlan: DekraTransportPlan,
    val activeDtcs: List<String>,
    val notes: String,
    val precheckAuthorized: Boolean,
    val custodyAuthorized: Boolean,
    val independentResultAcknowledged: Boolean,
    val officialFeeAcknowledged: Boolean,
    val stationRulesAcknowledged: Boolean,
)

enum class DekraRequestValidationError {
    VEHICLE_REQUIRED,
    PLATE_REQUIRED,
    VIN_NOT_MASKED,
    PLATE_NOT_MASKED,
    STATION_REQUIRED,
    APPOINTMENT_DATE_REQUIRED,
    PICKUP_ZONE_REQUIRED,
    CONTACT_PHONE_INVALID,
    PRECHECK_AUTHORIZATION_REQUIRED,
    CUSTODY_AUTHORIZATION_REQUIRED,
    INDEPENDENT_RESULT_ACKNOWLEDGEMENT_REQUIRED,
    OFFICIAL_FEE_ACKNOWLEDGEMENT_REQUIRED,
    STATION_RULES_ACKNOWLEDGEMENT_REQUIRED,
    UNSAFE_TRANSPORT_PLAN,
}

object DekraConciergePolicy {
    fun transportPlanFor(condition: DekraVehicleCondition): DekraTransportPlan =
        when (condition) {
            DekraVehicleCondition.NORMAL -> DekraTransportPlan.DRIVE_AFTER_PRECHECK
            DekraVehicleCondition.SAFETY_CONCERN,
            DekraVehicleCondition.IMMOBILIZED,
            -> DekraTransportPlan.TOW_ONLY
        }

    fun validate(request: DekraConciergeRequest): Set<DekraRequestValidationError> = buildSet {
        if (request.vehicleId.isBlank()) add(DekraRequestValidationError.VEHICLE_REQUIRED)
        if (request.maskedPlate.isBlank()) add(DekraRequestValidationError.PLATE_REQUIRED)
        if (request.maskedVin?.length.orZero() > 4 && request.maskedVin?.contains('*') != true) {
            add(DekraRequestValidationError.VIN_NOT_MASKED)
        }
        if (request.maskedPlate.length > 2 && '*' !in request.maskedPlate) {
            add(DekraRequestValidationError.PLATE_NOT_MASKED)
        }
        if (request.station.isBlank()) add(DekraRequestValidationError.STATION_REQUIRED)
        if (request.appointmentDateTime.isBlank()) add(DekraRequestValidationError.APPOINTMENT_DATE_REQUIRED)
        if (request.pickupZone.isBlank()) add(DekraRequestValidationError.PICKUP_ZONE_REQUIRED)
        if (request.contactPhone.count(Char::isDigit) < 8) add(DekraRequestValidationError.CONTACT_PHONE_INVALID)
        if (!request.precheckAuthorized) add(DekraRequestValidationError.PRECHECK_AUTHORIZATION_REQUIRED)
        if (!request.custodyAuthorized) add(DekraRequestValidationError.CUSTODY_AUTHORIZATION_REQUIRED)
        if (!request.independentResultAcknowledged) {
            add(DekraRequestValidationError.INDEPENDENT_RESULT_ACKNOWLEDGEMENT_REQUIRED)
        }
        if (!request.officialFeeAcknowledged) add(DekraRequestValidationError.OFFICIAL_FEE_ACKNOWLEDGEMENT_REQUIRED)
        if (!request.stationRulesAcknowledged) add(DekraRequestValidationError.STATION_RULES_ACKNOWLEDGEMENT_REQUIRED)
        if (request.transportPlan != transportPlanFor(request.vehicleCondition)) {
            add(DekraRequestValidationError.UNSAFE_TRANSPORT_PLAN)
        }
    }

    fun maskVin(vin: String?): String? = vin
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { normalized ->
            if (normalized.length <= 4) normalized else "*".repeat(normalized.length - 4) + normalized.takeLast(4)
        }

    fun maskPlate(plate: String?): String = plate
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { normalized ->
            if (normalized.length <= 2) normalized else "*".repeat(normalized.length - 2) + normalized.takeLast(2)
        }
        .orEmpty()

    private fun Int?.orZero(): Int = this ?: 0
}

data class DekraProviderSnapshot(
    val providerType: String,
    val active: Boolean,
    val verified: Boolean,
    val licenseNumber: String,
)

object DekraProviderEligibilityPolicy {
    private val transportProviderTypes = setOf(
        "ride_driver",
        "tow_provider",
        "ride",
        "driver",
        "tow",
        "tow_truck",
    )

    fun isEligible(profile: DekraProviderSnapshot): Boolean =
        profile.active &&
            profile.verified &&
            profile.licenseNumber.isNotBlank() &&
            profile.providerType.trim().lowercase() in transportProviderTypes
}

data class DekraChecklistSection(
    val title: String,
    val summary: String,
)

object DekraInspectionKnowledge {
    const val VERIFIED_ON = "22 de agosto de 2026"
    const val OFFICIAL_BOOKING_URL = "https://booking.dekra.com/book/customer-retail/CR"
    const val OFFICIAL_FAQ_URL = "https://www.dekra.cr/es/preguntas-frecuentes/"
    const val OFFICIAL_APPOINTMENT_POLICY_URL = "https://www.dekra.cr/es/politica-de-citas/"
    const val OFFICIAL_FEES_URL = "https://www.dekra.cr/es/tarifas/"
    const val OFFICIAL_MANUAL_URL = "https://repositorio.mopt.go.cr/items/7e54c704-d6b3-4fbe-a4c8-172f68813321"

    val precheckSections = listOf(
        DekraChecklistSection("Identidad y documentos", "Placa, VIN/chasis, cita, licencia vigente y coincidencia del vehículo."),
        DekraChecklistSection("Luces y visibilidad", "Altas, bajas, direccionales, freno, reversa, placa, parabrisas, espejos, limpiaparabrisas y lavaparabrisas."),
        DekraChecklistSection("Llantas y ruedas", "Condición, desgaste visible, daños, fijación y presión de referencia; sin afirmar mediciones no realizadas."),
        DekraChecklistSection("Cabina y seguridad", "Puertas, asientos, cinturones, reposacabezas, bocina, testigos y elementos sueltos."),
        DekraChecklistSection("Frenos, dirección y suspensión", "Respuesta básica, freno de estacionamiento, holguras o ruidos evidentes y condición para traslado seguro."),
        DekraChecklistSection("Motor, fluidos y emisiones", "Fugas visibles, niveles comprobables sin desmontaje, escape, humo anormal y DTC/testigos disponibles."),
        DekraChecklistSection("Carrocería y chasis", "Daños, corrosión, fijaciones, partes salientes y condición exterior observable."),
        DekraChecklistSection("Evidencia de custodia", "Fotos de entrega, kilometraje, combustible, pertenencias declaradas, llaves y estado exterior antes de mover el vehículo."),
    )

    val officialInspectionSections = listOf(
        DekraChecklistSection("1. Identificación", "DEKRA comprueba matrícula y VIN o número de chasis contra la identificación oficial."),
        DekraChecklistSection("2. Exterior, carrocería y chasis", "Estado, fijaciones, corrosión, elementos exteriores y condiciones que afecten seguridad."),
        DekraChecklistSection("3. Interior", "Asientos, cinturones, reposacabezas, puertas, mandos y otros elementos de seguridad interior."),
        DekraChecklistSection("4. Alumbrado y señalización", "Luces, color, cantidad, ubicación, funcionamiento y señalización reglamentaria."),
        DekraChecklistSection("5. Emisiones y ruido", "Emisiones según combustible y categoría, escape, humo y ruido cuando corresponda."),
        DekraChecklistSection("6. Frenos", "Eficacia, equilibrio, freno de servicio y estacionamiento, circuitos y componentes visibles."),
        DekraChecklistSection("7. Dirección", "Deriva, holguras, fijación y estado de componentes accesibles."),
        DekraChecklistSection("8. Ejes y suspensión", "Ruedas, aros, ejes, amortiguadores, resortes, rótulas, brazos, fijaciones y holguras."),
        DekraChecklistSection("9. Motor y transmisión", "Fugas, anclajes, escape, transmisión y otros componentes aplicables, sin desmontar piezas."),
        DekraChecklistSection("10. Otros requisitos", "Se agregan controles según categoría: transporte público, carga, motocicleta, grúa, emergencia u otros."),
        DekraChecklistSection("11. Situaciones especiales", "El manual define cómo proceder ante servicios no finalizados y otras condiciones excepcionales."),
    )
}
