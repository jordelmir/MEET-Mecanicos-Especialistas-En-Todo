package com.elysium369.meet.core.vehicleaccess.domain

import java.util.UUID

/**
 * Supported credential archetypes for vehicle access.
 */
enum class CredentialType(val displayName: String) {
    DIGITAL_KEY("Llave Digital (NFC / BLE / UWB)"),
    NFC("NFC Tap-to-Unlock / Start"),
    BLE("Bluetooth LE Proximity Access"),
    UWB("UWB Secure Passive Entry"),
    OEM_CLOUD("Acceso Remoto por Nube OEM"),
    TRANSPONDER("Transponder Inmovilizador Físico"),
    REMOTE("Control Remoto RKE (Radiofrecuencia)"),
    MECHANICAL("Llave Mecánica Tallada"),
    SMART_KEY("Smart Key / Llave de Proximidad Física")
}

/**
 * Root authority managing or issuing the access credential.
 */
enum class CredentialAuthority(val displayName: String) {
    OEM("Fabricante del Vehículo (OEM)"),
    GOOGLE_WALLET("Google Wallet Digital Car Key"),
    OEM_APP("Aplicación Oficial del Fabricante"),
    CCC_PARTNER("Car Connectivity Consortium (CCC)"),
    MEET_NATIVE("MEET Vanguard Security Engine"),
    CERTIFIED_LOCKSMITH("Cerrajería Automotriz Certificada")
}

/**
 * Lifecycle status of a vehicle access credential.
 */
enum class CredentialStatus(val displayName: String) {
    ACTIVE("Activa y Verificada"),
    PROVISIONING("En Proceso de Emparejamiento"),
    SUSPENDED("Suspendida Temporalmente"),
    LOST("Declarada Perdida"),
    REVOKED("Revocada del Sistema"),
    EXPIRED("Vencida por Política de Tiempo"),
    UNKNOWN("Estado No Verificado")
}

/**
 * Granular permissions that can be granted to an access credential.
 */
enum class AccessPermission(val code: String, val displayName: String) {
    ENTRY("entry", "Apertura y Cierre de Puertas"),
    DRIVE("drive", "Autorización de Arranque y Conducción"),
    TRUNK("trunk", "Apertura de Maletero / Baúl"),
    CHARGE("charge", "Control de Puerto de Carga / Tapa"),
    REMOTE_COMMANDS("remote_commands", "Comandos Remotos (Luces, Claxon, Clima)"),
    DIAGNOSTICS("diagnostics", "Acceso a Diagnóstico y Telemetría")
}

/**
 * Capability evaluation states (honest fail-closed model).
 */
enum class CapabilityState(val displayName: String) {
    SUPPORTED("Soportado Oficialmente"),
    UNSUPPORTED("No Soportado por Hardware"),
    CONDITIONAL("Requiere Confirmación / Flujo OEM"),
    UNKNOWN("No Determinado (Bloqueado)"),
    BLOCKED("Bloqueado por Política de Seguridad")
}

/**
 * Phone physical & radio capability snapshot evaluated at runtime.
 */
data class PhoneAccessCapabilities(
    val hasNfc: Boolean,
    val hasHce: Boolean,
    val hasBle: Boolean,
    val canAdvertiseBle: Boolean,
    val hasUwb: Boolean,
    val hasSecureScreenLock: Boolean,
    val androidVersion: Int,
    val walletAvailability: Boolean
)

/**
 * Vehicle access capabilities matrix evaluated for the active vehicle.
 */
data class VehicleAccessCapabilities(
    val vehicleId: String,
    val digitalKeySupport: CapabilityState,
    val nfcSupport: CapabilityState,
    val bleSupport: CapabilityState,
    val uwbSupport: CapabilityState,
    val oemCloudSupport: CapabilityState,
    val walletProvisioningSupport: CapabilityState,
    val immoProtocol: String = "ISO 9141-2 / K-Line (Transponder ID46/Megamos)",
    val bcmStatus: String = "Enlace BCM Disponible vía OBD",
    val authoritySource: CredentialAuthority = CredentialAuthority.MEET_NATIVE,
    val lastVerifiedAt: Long = System.currentTimeMillis()
)

/**
 * Digital Twin representation of a registered vehicle access credential.
 * Notice: Cryptographic private keys and raw secrets are NEVER persisted.
 */
data class VehicleAccessCredential(
    val credentialId: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val slotNumber: Int,
    val label: String,
    val type: CredentialType,
    val authority: CredentialAuthority,
    val status: CredentialStatus,
    val permissions: Set<AccessPermission>,
    val transponderFamily: String? = null,
    val remoteFrequency: String? = null,
    val batteryHealthPercent: Int? = null,
    val isPrimaryOwner: Boolean = false,
    val validFromEpochMs: Long = System.currentTimeMillis(),
    val validUntilEpochMs: Long? = null,
    val lastVerifiedAtEpochMs: Long? = null,
    val proofHash: String? = null
)

/**
 * Temporary or restricted access delegation (e.g. Valet, Family, Workshop).
 */
data class AccessGrant(
    val grantId: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val recipientName: String,
    val recipientRole: String, // "Familiar", "Valet Parking", "Taller Mecánico", "Conductor Flota"
    val permissions: Set<AccessPermission>,
    val validFromEpochMs: Long,
    val validUntilEpochMs: Long,
    val isVehicleEnforced: Boolean, // true if OEM cloud/hardware enforces it, false if MEET policy only
    val status: CredentialStatus = CredentialStatus.ACTIVE,
    val revocationReason: String? = null
)

/**
 * Immutable audit event for the Access Security Timeline.
 */
data class AccessAuditEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val action: String,
    val actor: String,
    val credentialType: CredentialType,
    val outcome: String, // "AUTORIZADO", "BLOQUEADO", "REVOCADO", "EMPAREJADO"
    val evidenceHash: String
)
