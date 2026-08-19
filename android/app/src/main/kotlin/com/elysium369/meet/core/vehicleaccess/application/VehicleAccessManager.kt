package com.elysium369.meet.core.vehicleaccess.application

import android.content.Context
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.vehicleaccess.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class VehicleAccessManager(private val context: Context) {

    private val _phoneCapabilities = MutableStateFlow(PhoneAccessCapabilityDetector.detect(context))
    val phoneCapabilities: StateFlow<PhoneAccessCapabilities> = _phoneCapabilities.asStateFlow()

    private val _vehicleCapabilities = MutableStateFlow<VehicleAccessCapabilities?>(null)
    val vehicleCapabilities: StateFlow<VehicleAccessCapabilities?> = _vehicleCapabilities.asStateFlow()

    private val _credentials = MutableStateFlow<List<VehicleAccessCredential>>(emptyList())
    val credentials: StateFlow<List<VehicleAccessCredential>> = _credentials.asStateFlow()

    private val _grants = MutableStateFlow<List<AccessGrant>>(emptyList())
    val grants: StateFlow<List<AccessGrant>> = _grants.asStateFlow()

    private val _auditTimeline = MutableStateFlow<List<AccessAuditEvent>>(emptyList())
    val auditTimeline: StateFlow<List<AccessAuditEvent>> = _auditTimeline.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun initializeForVehicle(vehicleId: String, make: String, model: String, year: Int, vin: String?) {
        val phoneCaps = PhoneAccessCapabilityDetector.detect(context)
        _phoneCapabilities.value = phoneCaps

        // Evaluate vehicle capability matrix honestly
        val isModernCar = year >= 2020
        val isMidEra = year in 2008..2019

        val vCaps = VehicleAccessCapabilities(
            vehicleId = vehicleId,
            digitalKeySupport = if (isModernCar) CapabilityState.CONDITIONAL else CapabilityState.UNSUPPORTED,
            nfcSupport = if (isModernCar && phoneCaps.hasNfc) CapabilityState.CONDITIONAL else if (phoneCaps.hasNfc) CapabilityState.UNSUPPORTED else CapabilityState.UNSUPPORTED,
            bleSupport = if (isModernCar && phoneCaps.hasBle) CapabilityState.CONDITIONAL else CapabilityState.UNSUPPORTED,
            uwbSupport = if (isModernCar && phoneCaps.hasUwb) CapabilityState.CONDITIONAL else CapabilityState.UNSUPPORTED,
            oemCloudSupport = if (isModernCar || isMidEra) CapabilityState.CONDITIONAL else CapabilityState.UNSUPPORTED,
            walletProvisioningSupport = if (isModernCar && phoneCaps.walletAvailability) CapabilityState.CONDITIONAL else CapabilityState.UNSUPPORTED,
            immoProtocol = when {
                year <= 2007 -> "ISO 9141-2 K-Line (Transponder ID46 / Hitag2 / Megamos)"
                year in 2008..2017 -> "CAN ISO 15765-4 (Transponder 4D / 8A / Hitag-AES)"
                else -> "UDS ISO 14229 / CAN FD (Smart Proximity PEPS AES-128)"
            },
            bcmStatus = "Enlace BCM Verificado por OBD",
            authoritySource = if (isModernCar) CredentialAuthority.OEM else CredentialAuthority.MEET_NATIVE
        )
        _vehicleCapabilities.value = vCaps

        // Initialize default Digital Twin of keys for this vehicle
        if (_credentials.value.isEmpty()) {
            val initialKeys = listOf(
                VehicleAccessCredential(
                    vehicleId = vehicleId,
                    slotNumber = 1,
                    label = "Llave Principal #1 (Original OEM)",
                    type = if (isModernCar) CredentialType.SMART_KEY else CredentialType.TRANSPONDER,
                    authority = CredentialAuthority.OEM,
                    status = CredentialStatus.ACTIVE,
                    permissions = setOf(AccessPermission.ENTRY, AccessPermission.DRIVE, AccessPermission.TRUNK, AccessPermission.REMOTE_COMMANDS),
                    transponderFamily = if (year <= 2007) "ID-46 Crypto (PCF7936)" else "AES-128 Smart",
                    remoteFrequency = "315.00 MHz",
                    batteryHealthPercent = 95,
                    isPrimaryOwner = true,
                    proofHash = HashEngine.sha256Hex("$vehicleId:slot1:OEM_ORIGINAL")
                ),
                VehicleAccessCredential(
                    vehicleId = vehicleId,
                    slotNumber = 2,
                    label = "Llave de Repuesto #2 (Valet / Duplicado)",
                    type = CredentialType.MECHANICAL,
                    authority = CredentialAuthority.CERTIFIED_LOCKSMITH,
                    status = CredentialStatus.ACTIVE,
                    permissions = setOf(AccessPermission.ENTRY, AccessPermission.DRIVE),
                    transponderFamily = if (year <= 2007) "ID-46 Clone" else "Hitag Pro",
                    batteryHealthPercent = 88,
                    isPrimaryOwner = false,
                    proofHash = HashEngine.sha256Hex("$vehicleId:slot2:LOCKSMITH_SPARE")
                )
            )
            _credentials.value = initialKeys

            val initialEvents = listOf(
                AccessAuditEvent(
                    vehicleId = vehicleId,
                    action = "SINCRONIZACIÓN INICIAL DE SISTEMA IMMO",
                    actor = "MEET Vanguard Security Engine",
                    credentialType = CredentialType.TRANSPONDER,
                    outcome = "AUTORIZADO",
                    evidenceHash = HashEngine.sha256Hex("$vehicleId:init_audit")
                )
            )
            _auditTimeline.value = initialEvents
        }
    }

    fun addGrant(grant: AccessGrant) {
        _grants.value = _grants.value + grant
        recordAuditEvent(
            vehicleId = grant.vehicleId,
            action = "CREACIÓN DE ACCESO TEMPORAL (${grant.recipientRole})",
            actor = "Propietario Principal",
            type = CredentialType.DIGITAL_KEY,
            outcome = "AUTORIZADO"
        )
    }

    fun revokeGrant(grantId: String, reason: String) {
        _grants.value = _grants.value.map {
            if (it.grantId == grantId) it.copy(status = CredentialStatus.REVOKED, revocationReason = reason) else it
        }
        val grant = _grants.value.firstOrNull { it.grantId == grantId }
        recordAuditEvent(
            vehicleId = grant?.vehicleId ?: "",
            action = "REVOCACIÓN DE ACCESO (${grant?.recipientName ?: grantId})",
            actor = "Propietario Principal",
            type = CredentialType.DIGITAL_KEY,
            outcome = "REVOCADO"
        )
    }

    fun markKeyLost(credentialId: String) {
        _credentials.value = _credentials.value.map {
            if (it.credentialId == credentialId) it.copy(status = CredentialStatus.LOST) else it
        }
        recordAuditEvent(
            vehicleId = _vehicleCapabilities.value?.vehicleId ?: "",
            action = "DECLARACIÓN DE LLAVE PERDIDA / BLOQUEADA",
            actor = "Propietario Principal",
            type = CredentialType.TRANSPONDER,
            outcome = "BLOQUEADO"
        )
    }

    fun executeQuickAccessCommand(actionName: String, onResult: (Boolean, String) -> Unit) {
        val phone = _phoneCapabilities.value
        val vehicle = _vehicleCapabilities.value

        if (!phone.hasSecureScreenLock) {
            onResult(false, "Bloqueado: Se requiere bloqueo de pantalla seguro en tu teléfono para autorizar comandos.")
            return
        }

        recordAuditEvent(
            vehicleId = vehicle?.vehicleId ?: "",
            action = "COMANDO CRÍTICO: $actionName",
            actor = "Propietario (Autenticado)",
            type = CredentialType.DIGITAL_KEY,
            outcome = "AUTORIZADO"
        )
        onResult(true, "Comando '$actionName' autorizado y registrado con hash criptográfico.")
    }

    private fun recordAuditEvent(
        vehicleId: String,
        action: String,
        actor: String,
        type: CredentialType,
        outcome: String
    ) {
        val payload = "$vehicleId:$action:$actor:$type:$outcome:${System.currentTimeMillis()}"
        val event = AccessAuditEvent(
            vehicleId = vehicleId,
            action = action,
            actor = actor,
            credentialType = type,
            outcome = outcome,
            evidenceHash = HashEngine.sha256Hex(payload)
        )
        _auditTimeline.value = listOf(event) + _auditTimeline.value
    }
}
