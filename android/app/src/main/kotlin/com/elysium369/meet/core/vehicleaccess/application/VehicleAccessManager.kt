package com.elysium369.meet.core.vehicleaccess.application

import android.content.Context
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.vehicleaccess.domain.*
import com.elysium369.meet.data.local.dao.VehicleAccessDao
import com.elysium369.meet.data.local.entities.AccessAuditEventEntity
import com.elysium369.meet.data.local.entities.AccessGrantEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleAccessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleAccessDao: VehicleAccessDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observationJob: Job? = null

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

        val vCaps = VehicleAccessCapabilities(
            vehicleId = vehicleId,
            digitalKeySupport = CapabilityState.UNKNOWN,
            nfcSupport = CapabilityState.UNKNOWN,
            bleSupport = CapabilityState.UNKNOWN,
            uwbSupport = CapabilityState.UNKNOWN,
            oemCloudSupport = CapabilityState.UNKNOWN,
            walletProvisioningSupport = CapabilityState.UNKNOWN,
            immoProtocol = "No verificado por hardware, OEM o lectura BCM",
            bcmStatus = "No verificado por hardware / OBD",
            authoritySource = CredentialAuthority.UNKNOWN,
        )
        _vehicleCapabilities.value = vCaps
        observationJob?.cancel()
        observationJob = scope.launch {
            launch {
                vehicleAccessDao.getCredentialsForVehicle(vehicleId).collect { rows ->
                    _credentials.value = rows.map { row ->
                        VehicleAccessCredential(
                            credentialId = row.credentialId,
                            vehicleId = row.vehicleId,
                            slotNumber = row.slotNumber,
                            label = row.label,
                            type = enumValueOrDefault(row.credentialType, CredentialType.MECHANICAL),
                            authority = enumValueOrDefault(row.authority, CredentialAuthority.UNKNOWN),
                            status = enumValueOrDefault(row.status, CredentialStatus.UNKNOWN),
                            permissions = decodePermissions(row.permissionsJson),
                            transponderFamily = row.transponderFamily,
                            remoteFrequency = row.remoteFrequency,
                            batteryHealthPercent = row.batteryHealthPercent,
                            isPrimaryOwner = row.isPrimaryOwner,
                            validFromEpochMs = row.validFromEpochMs,
                            validUntilEpochMs = row.validUntilEpochMs,
                            lastVerifiedAtEpochMs = row.lastVerifiedAtEpochMs,
                            proofHash = row.proofHash,
                        )
                    }
                }
            }
            launch {
                vehicleAccessDao.getGrantsForVehicle(vehicleId).collect { rows ->
                    _grants.value = rows.map { row ->
                        AccessGrant(
                            grantId = row.grantId,
                            vehicleId = row.vehicleId,
                            recipientName = row.recipientName,
                            recipientRole = row.recipientRole,
                            permissions = decodePermissions(row.permissionsJson),
                            validFromEpochMs = row.validFromEpochMs,
                            validUntilEpochMs = row.validUntilEpochMs,
                            isVehicleEnforced = row.isVehicleEnforced,
                            status = enumValueOrDefault(row.status, CredentialStatus.UNKNOWN),
                            revocationReason = row.revocationReason,
                        )
                    }
                }
            }
            launch {
                vehicleAccessDao.getAuditEventsForVehicle(vehicleId).collect { rows ->
                    _auditTimeline.value = rows.map { row ->
                        AccessAuditEvent(
                            eventId = row.eventId,
                            vehicleId = row.vehicleId,
                            timestampEpochMs = row.timestampEpochMs,
                            action = row.action,
                            actor = row.actor,
                            credentialType = enumValueOrDefault(row.credentialType, CredentialType.MECHANICAL),
                            outcome = row.outcome,
                            evidenceHash = row.evidenceHash,
                        )
                    }
                }
            }
        }
    }

    fun addGrant(grant: AccessGrant) {
        scope.launch {
            vehicleAccessDao.insertGrant(
                AccessGrantEntity(
                    grantId = grant.grantId,
                    vehicleId = grant.vehicleId,
                    recipientName = grant.recipientName,
                    recipientRole = grant.recipientRole,
                    permissionsJson = encodePermissions(grant.permissions),
                    validFromEpochMs = grant.validFromEpochMs,
                    validUntilEpochMs = grant.validUntilEpochMs,
                    isVehicleEnforced = grant.isVehicleEnforced,
                    status = grant.status.name,
                    revocationReason = grant.revocationReason,
                )
            )
        }
        recordAuditEvent(
            vehicleId = grant.vehicleId,
            action = "CREACIÓN DE ACCESO TEMPORAL (${grant.recipientRole})",
            actor = "Propietario Principal",
            type = CredentialType.DIGITAL_KEY,
            outcome = "AUTORIZADO"
        )
    }

    fun revokeGrant(grantId: String, reason: String) {
        val grant = _grants.value.firstOrNull { it.grantId == grantId } ?: return
        scope.launch {
            vehicleAccessDao.updateGrant(
                AccessGrantEntity(
                    grantId = grant.grantId,
                    vehicleId = grant.vehicleId,
                    recipientName = grant.recipientName,
                    recipientRole = grant.recipientRole,
                    permissionsJson = encodePermissions(grant.permissions),
                    validFromEpochMs = grant.validFromEpochMs,
                    validUntilEpochMs = grant.validUntilEpochMs,
                    isVehicleEnforced = grant.isVehicleEnforced,
                    status = CredentialStatus.REVOKED.name,
                    revocationReason = reason,
                )
            )
        }
        recordAuditEvent(
            vehicleId = grant.vehicleId,
            action = "REVOCACIÓN DE ACCESO (${grant.recipientName})",
            actor = "Propietario Principal",
            type = CredentialType.DIGITAL_KEY,
            outcome = "REVOCADO"
        )
    }

    fun markKeyLost(credentialId: String) {
        val credential = _credentials.value.firstOrNull { it.credentialId == credentialId } ?: return
        scope.launch {
            vehicleAccessDao.updateCredential(
                com.elysium369.meet.data.local.entities.VehicleAccessCredentialEntity(
                    credentialId = credential.credentialId,
                    vehicleId = credential.vehicleId,
                    slotNumber = credential.slotNumber,
                    label = credential.label,
                    credentialType = credential.type.name,
                    authority = credential.authority.name,
                    status = CredentialStatus.LOST.name,
                    permissionsJson = encodePermissions(credential.permissions),
                    transponderFamily = credential.transponderFamily,
                    remoteFrequency = credential.remoteFrequency,
                    batteryHealthPercent = credential.batteryHealthPercent,
                    isPrimaryOwner = credential.isPrimaryOwner,
                    validFromEpochMs = credential.validFromEpochMs,
                    validUntilEpochMs = credential.validUntilEpochMs,
                    lastVerifiedAtEpochMs = credential.lastVerifiedAtEpochMs,
                    proofHash = credential.proofHash,
                )
            )
        }
        recordAuditEvent(
            vehicleId = _vehicleCapabilities.value?.vehicleId ?: "",
            action = "DECLARACIÓN DE LLAVE PERDIDA / BLOQUEADA",
            actor = "Propietario Principal",
            type = CredentialType.TRANSPONDER,
            outcome = "MEET_MARKED_LOST (Registrado en app; requiere prueba/reprogramación física de BCM)"
        )
    }

    fun executeQuickAccessCommand(actionName: String, onResult: (Boolean, String) -> Unit) {
        val phone = _phoneCapabilities.value
        val vehicle = _vehicleCapabilities.value

        if (vehicle == null) {
            onResult(false, "Bloqueado: Ningún vehículo activo seleccionado para autorizar comandos.")
            return
        }

        if (!phone.hasSecureScreenLock) {
            onResult(false, "Bloqueado: Se requiere bloqueo de pantalla seguro en tu teléfono para autorizar comandos.")
            return
        }

        val activeCredential = _credentials.value.firstOrNull {
            it.status == CredentialStatus.ACTIVE && AccessPermission.REMOTE_COMMANDS in it.permissions
        }
        val hardwareConfirmed = listOf(
            vehicle.digitalKeySupport,
            vehicle.oemCloudSupport,
            vehicle.bleSupport,
            vehicle.nfcSupport,
            vehicle.uwbSupport,
        ).any { it == CapabilityState.SUPPORTED }
        if (activeCredential == null || !hardwareConfirmed) {
            recordAuditEvent(
                vehicleId = vehicle.vehicleId,
                action = "COMANDO NO DESPACHADO: $actionName",
                actor = "Usuario local",
                type = activeCredential?.type ?: CredentialType.DIGITAL_KEY,
                outcome = "BLOQUEADO_SIN_CREDENCIAL_O_CANAL_VERIFICADO",
            )
            onResult(false, "No despachado: falta una credencial activa y un canal OEM/hardware verificado.")
            return
        }

        recordAuditEvent(
            vehicleId = vehicle.vehicleId,
            action = "COMANDO NO DESPACHADO: $actionName",
            actor = "Usuario local",
            type = activeCredential.type,
            outcome = "AUTORIZADO_SIN_TRANSPORTE_DE_EJECUCION"
        )
        onResult(false, "Autorización local válida, pero no existe transporte OEM de ejecución; no se envió el comando.")
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
        scope.launch {
            vehicleAccessDao.insertAuditEvent(
                AccessAuditEventEntity(
                    eventId = event.eventId,
                    vehicleId = event.vehicleId,
                    timestampEpochMs = event.timestampEpochMs,
                    action = event.action,
                    actor = event.actor,
                    credentialType = event.credentialType.name,
                    outcome = event.outcome,
                    evidenceHash = event.evidenceHash,
                )
            )
        }
    }

    private fun encodePermissions(permissions: Set<AccessPermission>): String =
        permissions.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"") { it.name }

    private fun decodePermissions(raw: String): Set<AccessPermission> = Regex("[A-Z_]+")
        .findAll(raw)
        .mapNotNull { match -> runCatching { AccessPermission.valueOf(match.value) }.getOrNull() }
        .toSet()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, default: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
}
