package com.elysium369.meet.core.services.kernel

import java.util.UUID

/**
 * Universal Proof-Carrying State contract for auditability and release truth.
 */
enum class FeatureProofState {
    MODEL_EXISTS,
    CLIENT_IMPLEMENTED,
    SERVER_AUTHORITATIVE,
    PHYSICALLY_VERIFIED,
}

/**
 * Exception thrown when a monetary operation encounters an unsupported or unmapped currency.
 * Fails closed to prevent financial contamination.
 */
class UnsupportedCurrencyException(currencyRaw: String) :
    IllegalArgumentException("Unsupported currency code: '$currencyRaw'. Financial operations must fail closed.")

/**
 * Strict ISO 4217 Currency Code representation.
 */
enum class CurrencyCode(val standardSymbol: String, val decimalPlaces: Int) {
    CRC("₡", 0),
    USD("$", 2);

    companion object {
        fun fromString(value: String): CurrencyCode = when (value.trim().uppercase()) {
            "CRC", "COLONES", "COLÓN", "₡" -> CRC
            "USD", "DOLLARS", "DÓLARES", "$" -> USD
            else -> throw UnsupportedCurrencyException(value)
        }

        fun fromStringOrNull(value: String?): CurrencyCode? = when (value?.trim()?.uppercase()) {
            "CRC", "COLONES", "COLÓN", "₡" -> CRC
            "USD", "DOLLARS", "DÓLARES", "$" -> USD
            else -> null
        }
    }
}

/**
 * Precise, overflow-safe integer money representation using minor units (e.g. cents/colones).
 */
data class Money(
    val amountMinor: Long,
    val currency: CurrencyCode,
) {
    init {
        require(amountMinor >= 0) { "Monetary amounts cannot be negative: $amountMinor" }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add distinct currencies: $currency vs ${other.currency}" }
        return Money(Math.addExact(amountMinor, other.amountMinor), currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract distinct currencies: $currency vs ${other.currency}" }
        val result = amountMinor - other.amountMinor
        require(result >= 0) { "Monetary subtraction underflow: $amountMinor - ${other.amountMinor}" }
        return Money(result, currency)
    }

    fun formatted(): String = when (currency) {
        CurrencyCode.CRC -> String.format(java.util.Locale.US, "${currency.standardSymbol}%,d", amountMinor)
        CurrencyCode.USD -> String.format(java.util.Locale.US, "${currency.standardSymbol}%,.2f", amountMinor / 100.0)
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0L, currency)
        fun ofCrc(colones: Long): Money = Money(colones, CurrencyCode.CRC)
        fun ofUsdCents(cents: Long): Money = Money(cents, CurrencyCode.USD)
    }
}

/**
 * Service vertical classification for MEET/Elysium Vanguard.
 */
enum class ServiceVertical(val code: String, val displayName: String) {
    REPAIR("repair", "Red de Reparación Mecánica"),
    TOW("tow", "Servicio de Grúas y Rescate"),
    PARTS("parts", "Marketplace Técnico de Repuestos"),
    RIDE("ride", "Movilidad y Viajes"),
    INSPECTION("inspection", "Inspección Pre-Compra Forense"),
    UNIVERSAL("universal", "Elysium Vanguard Universal"),
    UNKNOWN("unknown", "Vertical Desconocido");

    companion object {
        fun fromCode(code: String): ServiceVertical =
            values().firstOrNull { it.code.equals(code.trim(), ignoreCase = true) } ?: UNKNOWN

        fun fromCodeStrict(code: String): ServiceVertical {
            val res = fromCode(code)
            if (res == UNKNOWN) {
                throw UnsupportedServiceVerticalException("Vertical '$code' no es reconocido por la plataforma")
            }
            return res
        }
    }
}

class UnsupportedServiceVerticalException(message: String) : IllegalArgumentException(message)
class UnsupportedProviderTypeException(message: String) : IllegalArgumentException(message)

/**
 * Canonical provider types aligned across UI, ViewModel, Repository and PostgreSQL provider_profiles.
 */
enum class ProviderType(val dbValue: String, val displayName: String) {
    MECHANIC("mechanic", "Mecánico Profesional"),
    WORKSHOP("workshop", "Taller Mecánico Establecido"),
    PARTS_STORE("parts_store", "Venta de Repuestos"),
    TOW_PROVIDER("tow_provider", "Operador de Grúas"),
    RIDE_DRIVER("ride_driver", "Conductor de Movilidad"),
    UNKNOWN("unknown", "Tipo de Proveedor Desconocido");

    companion object {
        fun fromDbValue(value: String): ProviderType = when (value.trim().lowercase()) {
            "mechanic" -> MECHANIC
            "workshop" -> WORKSHOP
            "parts_store", "part_store", "store" -> PARTS_STORE
            "tow_provider", "tow_truck", "tow", "tow_driver" -> TOW_PROVIDER
            "ride_driver", "driver", "ride" -> RIDE_DRIVER
            else -> values().firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: UNKNOWN
        }

        fun fromDbValueStrict(value: String): ProviderType {
            val res = fromDbValue(value)
            if (res == UNKNOWN) {
                throw UnsupportedProviderTypeException("Tipo de proveedor '$value' no es reconocido por la plataforma")
            }
            return res
        }
    }
}

/**
 * Service role hierarchy for role-based access control.
 */
enum class ServiceRole {
    CUSTOMER,
    TECHNICIAN,
    WORKSHOP_ADMIN,
    PARTS_STORE_AGENT,
    TOW_OPERATOR,
    RIDE_DRIVER,
    PLATFORM_ADMIN,
    SYSTEM_AUTOMATION,
}

/**
 * Verification state within Trust Center.
 */
enum class VerificationState {
    UNVERIFIED,
    PENDING_REVIEW,
    APPROVED,
    SUSPENDED,
    REVOKED,
}

/**
 * Cryptographically verifiable or server-signed Provider Trust Snapshot.
 */
data class ProviderTrustSnapshot(
    val providerProfileId: UUID,
    val verificationState: VerificationState,
    val trustVersion: Int,
    val verifiedAtEpochMs: Long?,
    val expiresAtEpochMs: Long?,
    val revokedAtEpochMs: Long?,
    val verifiedByAdminId: UUID?,
    val eligibleServiceVerticals: Set<ServiceVertical>,
    val maxConcurrentJobs: Int = 3,
    val isActive: Boolean = true,
) {
    val isAuthorizedToWork: Boolean
        get() = verificationState == VerificationState.APPROVED &&
                isActive &&
                revokedAtEpochMs == null &&
                (expiresAtEpochMs == null || expiresAtEpochMs > System.currentTimeMillis())
}

/**
 * Granular permissions required for mutating service state.
 */
enum class ServicePermission {
    CREATE_REQUEST,
    SUBMIT_OFFER,
    ACCEPT_OFFER,
    START_ROUTE,
    CONFIRM_ARRIVAL,
    START_INSPECTION,
    CONFIRM_DIAGNOSIS,
    REQUEST_PARTS,
    DISPATCH_PARTS,
    CONFIRM_PARTS_RECEIPT,
    PERFORM_REPAIR,
    COMPLETE_WORK,
    SUBMIT_POST_SCAN,
    FINALIZE_INVOICE,
    CONFIRM_SATISFACTION,
    OPEN_DISPUTE,
    SUBMIT_RATING,
}

/**
 * Comprehensive service actor model ensuring end-to-end identity chain:
 * AuthPrincipal -> UserProfile -> ProviderProfile -> ProviderTrustSnapshot -> TechnicianIdentity
 */
data class ServiceActor(
    val authUserId: UUID,
    val userProfileId: UUID,
    val providerProfileId: UUID?,
    val organizationId: UUID?,
    val technicianId: UUID?,
    val roles: Set<ServiceRole>,
    val permissions: Set<ServicePermission>,
    val displayName: String,
    val phone: String?,
    val trustSnapshot: ProviderTrustSnapshot? = null,
) {
    init {
        require(displayName.isNotBlank()) { "Actor display name cannot be blank" }
    }

    val isVerifiedProvider: Boolean
        get() = trustSnapshot != null &&
                providerProfileId != null &&
                trustSnapshot.providerProfileId == providerProfileId &&
                trustSnapshot.isAuthorizedToWork

    fun hasPermission(permission: ServicePermission, requestedVertical: ServiceVertical? = null): Boolean {
        if (roles.contains(ServiceRole.PLATFORM_ADMIN)) return true
        if (!permissions.contains(permission)) return false

        val requiresProviderVerification = when (permission) {
            ServicePermission.SUBMIT_OFFER,
            ServicePermission.START_ROUTE,
            ServicePermission.CONFIRM_ARRIVAL,
            ServicePermission.START_INSPECTION,
            ServicePermission.CONFIRM_DIAGNOSIS,
            ServicePermission.REQUEST_PARTS,
            ServicePermission.DISPATCH_PARTS,
            ServicePermission.PERFORM_REPAIR,
            ServicePermission.COMPLETE_WORK,
            ServicePermission.SUBMIT_POST_SCAN,
            ServicePermission.FINALIZE_INVOICE -> true
            else -> false
        }

        if (requiresProviderVerification) {
            if (!isVerifiedProvider) return false
            if (requestedVertical != null && trustSnapshot?.eligibleServiceVerticals?.contains(requestedVertical) != true) {
                return false
            }
        }
        return true
    }

    companion object {
        fun customer(
            authUserId: UUID,
            userProfileId: UUID,
            displayName: String,
            phone: String? = null,
        ): ServiceActor = ServiceActor(
            authUserId = authUserId,
            userProfileId = userProfileId,
            providerProfileId = null,
            organizationId = null,
            technicianId = null,
            roles = setOf(ServiceRole.CUSTOMER),
            permissions = setOf(
                ServicePermission.CREATE_REQUEST,
                ServicePermission.ACCEPT_OFFER,
                ServicePermission.CONFIRM_PARTS_RECEIPT,
                ServicePermission.CONFIRM_SATISFACTION,
                ServicePermission.OPEN_DISPUTE,
                ServicePermission.SUBMIT_RATING,
            ),
            displayName = displayName,
            phone = phone,
            trustSnapshot = null,
        )

        fun provider(
            authUserId: UUID,
            userProfileId: UUID,
            providerProfileId: UUID,
            role: ServiceRole,
            displayName: String,
            phone: String? = null,
            organizationId: UUID? = null,
            technicianId: UUID? = null,
            trustSnapshot: ProviderTrustSnapshot? = null,
            additionalPermissions: Set<ServicePermission> = emptySet(),
        ): ServiceActor {
            val basePermissions = when (role) {
                ServiceRole.TECHNICIAN, ServiceRole.WORKSHOP_ADMIN -> setOf(
                    ServicePermission.SUBMIT_OFFER,
                    ServicePermission.START_ROUTE,
                    ServicePermission.CONFIRM_ARRIVAL,
                    ServicePermission.START_INSPECTION,
                    ServicePermission.CONFIRM_DIAGNOSIS,
                    ServicePermission.REQUEST_PARTS,
                    ServicePermission.PERFORM_REPAIR,
                    ServicePermission.COMPLETE_WORK,
                    ServicePermission.SUBMIT_POST_SCAN,
                    ServicePermission.FINALIZE_INVOICE,
                )
                ServiceRole.TOW_OPERATOR -> setOf(
                    ServicePermission.SUBMIT_OFFER,
                    ServicePermission.START_ROUTE,
                    ServicePermission.CONFIRM_ARRIVAL,
                    ServicePermission.COMPLETE_WORK,
                )
                ServiceRole.PARTS_STORE_AGENT -> setOf(
                    ServicePermission.SUBMIT_OFFER,
                    ServicePermission.DISPATCH_PARTS,
                )
                else -> emptySet()
            }
            return ServiceActor(
                authUserId = authUserId,
                userProfileId = userProfileId,
                providerProfileId = providerProfileId,
                organizationId = organizationId,
                technicianId = technicianId,
                roles = setOf(role),
                permissions = basePermissions + additionalPermissions,
                displayName = displayName,
                phone = phone,
                trustSnapshot = trustSnapshot,
            )
        }
    }
}

/**
 * Server-authoritative provider identity projection.
 */
data class ProviderIdentity(
    val providerProfileId: UUID,
    val authUserId: UUID,
    val businessName: String,
    val providerType: ProviderType,
    val trustSnapshot: ProviderTrustSnapshot? = null,
    val isVerifiedLegacy: Boolean = false,
    val isActiveLegacy: Boolean = true,
    val status: String = "active",
    val ratingScore: Double = 0.0,
    val totalJobsCompleted: Int = 0,
    val specialties: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val phone: String? = null,
) {
    val isVerified: Boolean
        get() = trustSnapshot?.isAuthorizedToWork ?: (isVerifiedLegacy && isActiveLegacy)

    val isEligibleToWork: Boolean
        get() = if (trustSnapshot != null) {
            trustSnapshot.isAuthorizedToWork && trustSnapshot.providerProfileId == providerProfileId
        } else {
            isVerifiedLegacy && isActiveLegacy && status.equals("active", ignoreCase = true)
        }
}

/**
 * Geographic privacy policy level for provider and customer tracking.
 */
enum class GeoPrivacyLevel {
    NONE,
    COARSE,
    EXACT_ON_REQUEST,
    EXACT_DURING_ACTIVE_SERVICE,
}

/**
 * Standard immutable command envelope for outbox and server-authoritative RPCs.
 */
data class ServiceCommandEnvelope(
    val commandId: UUID = UUID.randomUUID(),
    val aggregateId: UUID,
    val aggregateType: String,
    val commandType: String,
    val actorId: UUID,
    val expectedVersion: Int,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val payloadJson: String,
    val payloadVersion: Int = 1,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val signedPayloadHash: String? = null,
    val signature: String? = null,
    val keyId: String? = null,
    val algorithm: String? = null,
    val nonce: String? = null,
)

/**
 * Reference to a cryptographically validated piece of service evidence.
 */
data class ServiceEvidenceRef(
    val evidenceId: UUID = UUID.randomUUID(),
    val workOrderId: UUID,
    val evidenceType: String,
    val sha256Hash: String,
    val storagePath: String?,
    val actorId: UUID,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
)
