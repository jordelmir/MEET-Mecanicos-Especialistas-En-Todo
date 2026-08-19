package com.elysium369.meet.core.vehicleaccess.security

import com.elysium369.meet.core.vehicleaccess.domain.AccessGrant
import com.elysium369.meet.core.vehicleaccess.domain.AccessPermission
import com.elysium369.meet.core.vehicleaccess.domain.CredentialStatus

/**
 * Policy & Role Engine for temporary access delegation.
 */
object AccessGrantEngine {

    sealed class AuthorizationResult {
        object Granted : AuthorizationResult()
        data class Denied(val reason: String) : AuthorizationResult()
    }

    /**
     * Evaluates if a given grant is authorized to execute a requested permission right now.
     */
    fun evaluate(grant: AccessGrant, requestedPermission: AccessPermission, currentEpochMs: Long = System.currentTimeMillis()): AuthorizationResult {
        // 1. Status check
        if (grant.status == CredentialStatus.REVOKED) {
            return AuthorizationResult.Denied("Acceso revocado por el propietario: ${grant.revocationReason ?: "Sin motivo especificado"}")
        }
        if (grant.status == CredentialStatus.SUSPENDED) {
            return AuthorizationResult.Denied("Acceso suspendido temporalmente.")
        }
        if (grant.status == CredentialStatus.LOST) {
            return AuthorizationResult.Denied("Credencial declarada perdida.")
        }

        // 2. Time-to-Live (TTL) Validity
        if (currentEpochMs < grant.validFromEpochMs) {
            return AuthorizationResult.Denied("El acceso aún no está activo (inicia en el horario programado).")
        }
        if (currentEpochMs > grant.validUntilEpochMs) {
            return AuthorizationResult.Denied("El permiso de acceso ha expirado.")
        }

        // 3. Permission inclusion
        if (!grant.permissions.contains(requestedPermission)) {
            return AuthorizationResult.Denied("Permiso denegado: El rol '${grant.recipientRole}' no tiene autorización para '${requestedPermission.displayName}'.")
        }

        // 4. Role-based least-privilege security constraint
        if (grant.recipientRole.equals("Valet Parking", ignoreCase = true) && 
            (requestedPermission == AccessPermission.TRUNK || requestedPermission == AccessPermission.REMOTE_COMMANDS)) {
            return AuthorizationResult.Denied("Política de seguridad Valet: Prohibido acceso a maletero o comandos remotos.")
        }

        return AuthorizationResult.Granted
    }
}
