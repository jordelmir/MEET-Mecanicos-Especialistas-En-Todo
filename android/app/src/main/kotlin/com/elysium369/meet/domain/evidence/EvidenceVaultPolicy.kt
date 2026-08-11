package com.elysium369.meet.domain.evidence

enum class EvidenceKeyCustody { ANDROID_KEYSTORE_HARDWARE_BACKED, ANDROID_KEYSTORE_DEVICE_BOUND }
enum class EvidenceBackupPolicy { EXCLUDED_FROM_CLOUD_BACKUP }
enum class EvidenceExportPolicy { SIGNED_ENCRYPTED_PACKAGE_ONLY }

data class EvidenceVaultPolicy(
    val envelopeVersion: Int = 2,
    val activeKeyVersion: Int = 2,
    val keyCustody: EvidenceKeyCustody = EvidenceKeyCustody.ANDROID_KEYSTORE_DEVICE_BOUND,
    val backupPolicy: EvidenceBackupPolicy = EvidenceBackupPolicy.EXCLUDED_FROM_CLOUD_BACKUP,
    val exportPolicy: EvidenceExportPolicy = EvidenceExportPolicy.SIGNED_ENCRYPTED_PACKAGE_ONLY,
    val associatedDataRequired: Boolean = true,
    val plaintextCleanupRequired: Boolean = true,
)

sealed interface EvidenceKeyMigrationDecision {
    data class Current(val keyVersion: Int) : EvidenceKeyMigrationDecision
    data class RewrapRequired(val fromVersion: Int, val toVersion: Int) : EvidenceKeyMigrationDecision
    data class Unsupported(val reason: String) : EvidenceKeyMigrationDecision
}

/** Explicit rotation contract; migration never decrypts into a persistent plaintext file. */
object EvidenceKeyRotationPolicy {
    fun decide(envelopeVersion: Int, keyVersion: Int, policy: EvidenceVaultPolicy): EvidenceKeyMigrationDecision = when {
        envelopeVersion !in 1..policy.envelopeVersion ->
            EvidenceKeyMigrationDecision.Unsupported("Versión de sobre no soportada: $envelopeVersion")
        keyVersion <= 0 || keyVersion > policy.activeKeyVersion ->
            EvidenceKeyMigrationDecision.Unsupported("Versión de llave no soportada: $keyVersion")
        keyVersion < policy.activeKeyVersion ->
            EvidenceKeyMigrationDecision.RewrapRequired(keyVersion, policy.activeKeyVersion)
        else -> EvidenceKeyMigrationDecision.Current(keyVersion)
    }
}
