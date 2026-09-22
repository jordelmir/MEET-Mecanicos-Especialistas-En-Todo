package com.elysium369.meet.core.domain

/**
 * MEET Vehicle Life OS — Universal Source Authority.
 * Explicitly records who or what authored an assertion.
 */
enum class SourceAuthority(val displayName: String) {
    VEHICLE_ECU("ECU / Bus de Datos Vehicular (OBD2/CAN/K-Line)"),
    OEM("Manual de Servicio y Especificación Oficial OEM"),
    REGULATORY("Normativa y Homologación Gubernamental / Ambiental"),
    SERVICE_PROVIDER("Taller Mecánico Certificado / Perito"),
    USER("Propietario / Conductor Registrado"),
    MEET_DERIVED("Motor Algorítmico Elysium Vanguard AI OS"),
    MEET_PREDICTION("Modelo Predictivo y Machine Learning Elysium"),
    THIRD_PARTY("Base de Datos Técnica Homologada (NHTSA, Mitchell, Autodata)")
}
