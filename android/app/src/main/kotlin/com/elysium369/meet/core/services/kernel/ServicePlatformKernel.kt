package com.elysium369.meet.core.services.kernel

import java.util.UUID

/**
 * Feature proof states for strict release truth verification.
 */
enum class FeatureProofState {
    MODEL_ONLY,
    RUNTIME_INTEGRATED,
    UNIT_VERIFIED,
    INTEGRATION_VERIFIED,
    CI_VERIFIED,
    PHYSICAL_VERIFIED,
    CANARY_VERIFIED,
    PRODUCTION,
}

/**
 * Exception thrown when encountering unsupported or unmapped currency.
 * Fails closed instead of defaulting to USD.
 */
class UnsupportedCurrencyException(message: String) : IllegalArgumentException(message)

/**
 * Currency codes supported by MEET Service Platform (ISO-4217).
 */
enum class CurrencyCode(val standardDecimals: Int, val symbol: String) {
    CRC(0, "₡"),
    USD(2, "$"),
    EUR(2, "€");

    companion object {
        fun fromString(code: String): CurrencyCode = when (code.trim().uppercase()) {
            "CRC" -> CRC
            "USD" -> USD
            "EUR" -> EUR
            else -> throw UnsupportedCurrencyException("Unknown or unsupported currency code: '$code'")
        }

        fun fromStringOrNull(code: String?): CurrencyCode? {
            if (code.isNullOrBlank()) return null
            return when (code.trim().uppercase()) {
                "CRC" -> CRC
                "USD" -> USD
                "EUR" -> EUR
                else -> null
            }
        }
    }
}

/**
 * Universal monetary representation in minor units (e.g., cents, colones).
 * Eliminates floating point rounding issues and currency mismatches.
 */
data class Money(
    val amountMinor: Long,
    val currency: CurrencyCode,
) {
    init {
        require(amountMinor >= 0) { "Monetary amount cannot be negative" }
    }

    val formattedString: String
        get() = when (currency) {
            CurrencyCode.CRC -> "${currency.symbol}${String.format(java.util.Locale.US, "%,d", amountMinor)}"
            CurrencyCode.USD, CurrencyCode.EUR -> {
                val major = amountMinor / 100
                val minor = amountMinor % 100
                "${currency.symbol}${String.format(java.util.Locale.US, "%,d.%02d", major, minor)}"
            }
        }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies: $currency vs ${other.currency}" }
        return Money(amountMinor + other.amountMinor, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract different currencies: $currency vs ${other.currency}" }
        require(amountMinor >= other.amountMinor) { "Monetary subtraction underflow" }
        return Money(amountMinor - other.amountMinor, currency)
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0L, currency)
        fun fromCents(cents: Long, currency: CurrencyCode = CurrencyCode.USD): Money = Money(cents, currency)
        fun fromColones(colones: Long): Money = Money(colones, CurrencyCode.CRC)
    }
}

/**
 * Canonical provider types aligned with PostgreSQL database enum.
 */
enum class ProviderType(val dbValue: String) {
    MECHANIC("mechanic"),
    WORKSHOP("workshop"),
    PARTS_STORE("parts_store"),
    TOW_PROVIDER("tow_provider"),
    RIDE_DRIVER("ride_driver");

    companion object {
        fun fromDbValue(value: String): ProviderType = values().firstOrNull {
            it.dbValue.equals(value.trim(), ignoreCase = true) ||
                    it.name.equals(value.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unknown ProviderType: '$value'")

        fun fromDbValueOrNull(value: String?): ProviderType? {
            if (value.isNullOrBlank()) return null
            return values().firstOrNull {
                it.dbValue.equals(value.trim(), ignoreCase = true) ||
                        it.name.equals(value.trim(), ignoreCase = true)
            }
        }
    }
}

/**
 * Service verticals supported across MEET and Elysium Services.
 */
enum class ServiceVertical(val displayName: String, val category: String) {
    MOBILE_MECHANIC("Mecánico Móvil", "AUTOMOTIVE"),
    WORKSHOP("Taller Mecánico", "AUTOMOTIVE"),
    TOWING("Servicio de Grúa", "ROADSIDE"),
    PARTS_DELIVERY("Entrega de Repuestos", "PARTS"),
    PARTS_PICKUP("Retiro de Repuestos", "PARTS"),
    ROADSIDE_ASSISTANCE("Asistencia en Carretera", "ROADSIDE"),
    DIAGNOSTIC_SPECIALIST("Especialista en Diagnóstico", "AUTOMOTIVE"),
    ELECTRICIAN("Electricidad Automotriz", "AUTOMOTIVE"),
    TIRE_SERVICE("Servicio de Llantas", "ROADSIDE"),
    BATTERY_SERVICE("Servicio de Baterías", "ROADSIDE"),
    LOCKSMITH("Cerrajería Vial", "ROADSIDE"),
    UNIVERSAL_SERVICE("Servicio Elysium", "UNIVERSAL");

    companion object {
        fun fromString(value: String): ServiceVertical = values().firstOrNull {
            it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true)
        } ?: UNIVERSAL_SERVICE
    }
}

/**
 * Roles assignable to an actor within a service operation.
 */
enum class ServiceRole {
    CUSTOMER,
    TECHNICIAN,
    WORKSHOP_ADMIN,
    TOW_OPERATOR,
    PARTS_STORE_AGENT,
    RIDE_DRIVER,
    PLATFORM_ADMIN,
    COMMUNITY_CONTRIBUTOR,
}

/**
 * Verification state within Trust Center authority.
 */
enum class VerificationState {
    PENDING,
    APPROVED,
    REJECTED,
    REVOKED,
    EXPIRED,
}

/**
 * Server-authoritative snapshot of provider verification and trust.
 */
data class ProviderTrustSnapshot(
    val providerProfileId: UUID,
    val verificationState: VerificationState,
    val isActive: Boolean,
    val trustVersion: Long,
    val verifiedAtEpochMs: Long?,
    val expiresAtEpochMs: Long?,
    val revokedAtEpochMs: Long?,
    val eligibleServiceVerticals: Set<ServiceVertical>,
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
        get() = trustSnapshot?.isAuthorizedToWork == true

    fun hasPermission(permission: ServicePermission): Boolean =
        roles.contains(ServiceRole.PLATFORM_ADMIN) || permissions.contains(permission)

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
    val isVerified: Boolean,
    val isActive: Boolean,
    val status: String,
    val ratingScore: Double,
    val totalJobsCompleted: Int,
    val specialties: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val phone: String? = null,
) {
    val isEligibleToWork: Boolean
        get() = isVerified && isActive && status.equals("active", ignoreCase = true)
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
