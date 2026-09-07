package com.elysium369.meet.core.services.kernel

import java.util.UUID

/**
 * ASTRA V6 §37-38 — Universal Service Intent & Job Primitives.
 *
 * Extends the existing ServicePlatformKernel with generalized
 * ServiceIntent → ServiceJob → ServiceOutcome models that work
 * across ALL verticals (automotive, plumbing, electrical, digital, etc.)
 *
 * Reuses existing: [Money], [CurrencyCode], [ServiceRole], [ServiceVertical]
 * from ServicePlatformKernel.kt — no duplication.
 *
 * Generalization Rule (§39): These primitives are proven in at least TWO
 * domains (automotive tow/repair + ride). Adding plumbing further validates.
 */

// ─── Extended Service Domain Taxonomy ───

/**
 * Extends the platform verticals with fine-grained service domains
 * for the Universal Service Catalog (§36).
 */
enum class UniversalServiceDomain(val displayName: String, val parentVertical: ServiceVertical) {
    // Automotive (maps to ServiceVertical.REPAIR, TOW, PARTS)
    AUTO_MECHANICAL("Mecánica y diagnóstico", ServiceVertical.REPAIR),
    AUTO_TOW("Grúa y rescate", ServiceVertical.TOW),
    AUTO_DETAILING("Lavado y detailing", ServiceVertical.REPAIR),

    // Home / Property
    PLUMBING("Plomería", ServiceVertical.UNIVERSAL),
    ELECTRICAL("Electricidad residencial", ServiceVertical.UNIVERSAL),
    CARPENTRY("Carpintería", ServiceVertical.UNIVERSAL),
    PAINTING("Pintura y acabados", ServiceVertical.UNIVERSAL),
    HVAC("Aire acondicionado", ServiceVertical.UNIVERSAL),
    APPLIANCE_REPAIR("Reparación electrodomésticos", ServiceVertical.UNIVERSAL),
    CLEANING("Limpieza", ServiceVertical.UNIVERSAL),

    // Logistics
    MOVING("Mudanzas", ServiceVertical.UNIVERSAL),
    COURIER("Mensajería", ServiceVertical.UNIVERSAL),

    // Mobility (maps to ServiceVertical.RIDE)
    PASSENGER_RIDE("Transporte de personas", ServiceVertical.RIDE),
    ROADSIDE("Asistencia vial", ServiceVertical.TOW),

    // Professional / Digital
    TUTORING("Tutorías", ServiceVertical.UNIVERSAL),
    SOFTWARE("Software", ServiceVertical.UNIVERSAL),
    GRAPHIC_DESIGN("Diseño gráfico", ServiceVertical.UNIVERSAL),
    TRANSLATION("Traducción", ServiceVertical.UNIVERSAL),
    ACCOUNTING("Contabilidad", ServiceVertical.UNIVERSAL),
    LEGAL("Orientación legal", ServiceVertical.UNIVERSAL),
    PHOTOGRAPHY("Fotografía y video", ServiceVertical.UNIVERSAL),
    EVENTS("Eventos", ServiceVertical.UNIVERSAL),
    SECURITY("Seguridad", ServiceVertical.UNIVERSAL),
    CUSTOM("Otro servicio", ServiceVertical.UNIVERSAL),
}

enum class ServiceModality { ON_SITE, REMOTE, HYBRID }
enum class ServiceUrgency { EMERGENCY, URGENT, STANDARD, FLEXIBLE }

// ─── Service Intent (§37) ───

enum class ServiceIntentState {
    CREATED, MATCHING, QUOTED, ACCEPTED, EXPIRED, CANCELLED,
}

/**
 * A structured representation of what a human needs.
 * This is the universal entry point for the economic OS.
 *
 * Example flows:
 * - "Se está saliendo agua de la pared" → ServiceIntent(domain=PLUMBING, ...)
 * - "Necesito diagnosticar mi carro" → ServiceIntent(domain=AUTO_MECHANICAL, ...)
 * - "Quiero una página web" → ServiceIntent(domain=SOFTWARE, modality=REMOTE, ...)
 */
data class ServiceIntent(
    val id: UUID = UUID.randomUUID(),
    val requesterId: String,
    val domain: UniversalServiceDomain,
    val modality: ServiceModality = ServiceModality.ON_SITE,
    val subject: String? = null,
    val desiredOutcome: String,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val evidenceRefs: List<String> = emptyList(),
    val urgency: ServiceUrgency = ServiceUrgency.STANDARD,
    val budgetMax: Money? = null,
    val requestedTimeEpochMs: Long? = null,
    val qualificationsRequired: List<String> = emptyList(),
    val constraints: Map<String, String> = emptyMap(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val state: ServiceIntentState = ServiceIntentState.CREATED,
) {
    val parentVertical: ServiceVertical get() = domain.parentVertical
}

// ─── Service Job (§38) ───

enum class ServiceJobState {
    PENDING, CONFIRMED, PROVIDER_EN_ROUTE, IN_PROGRESS,
    EVIDENCE_PENDING, COMPLETED, VERIFIED, DISPUTED, CANCELLED,
}

enum class PaymentTerms { ON_COMPLETION, MILESTONE_BASED, UPFRONT, ESCROW }
enum class MaterialProvider { CUSTOMER, PROVIDER, PLATFORM }

data class ServiceMaterial(
    val description: String,
    val partId: String? = null,
    val quantity: Int = 1,
    val estimatedCost: Money? = null,
    val providedBy: MaterialProvider = MaterialProvider.PROVIDER,
)

data class ServiceMilestone(
    val id: String,
    val description: String,
    val orderIndex: Int,
    val isCompleted: Boolean = false,
    val evidenceRef: String? = null,
)

/**
 * A job is created when a ServiceIntent is matched to a provider and
 * both parties agree on scope, quote, and terms.
 */
data class ServiceJob(
    val id: UUID = UUID.randomUUID(),
    val intentId: UUID,
    val providerId: String,
    val customerId: String,
    val domain: UniversalServiceDomain,
    val agreedScope: String,
    val quote: Money,
    val materials: List<ServiceMaterial> = emptyList(),
    val milestones: List<ServiceMilestone> = emptyList(),
    val evidenceRequirements: List<String> = emptyList(),
    val paymentTerms: PaymentTerms = PaymentTerms.ON_COMPLETION,
    val scheduledAtEpochMs: Long? = null,
    val state: ServiceJobState = ServiceJobState.PENDING,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null,
    val outcome: ServiceOutcome? = null,
)

// ─── Verified Outcome (§36) ───

enum class OutcomeSatisfaction {
    EXCEEDED_EXPECTATIONS, SATISFIED, PARTIALLY_RESOLVED, UNRESOLVED, DISPUTED,
}

/**
 * The outcome of a completed service job.
 * Reputation is derived from verified work, not stars (§46).
 */
data class ServiceOutcome(
    val jobId: UUID,
    val isSuccessful: Boolean,
    val customerConfirmed: Boolean = false,
    val evidenceRefs: List<String> = emptyList(),
    val outcomeDescription: String? = null,
    val completionDurationMinutes: Int? = null,
    val warrantyDays: Int? = null,
    val customerSatisfaction: OutcomeSatisfaction? = null,
    val verifiedAtEpochMs: Long? = null,
)

// ─── Provider Capability (§48) ───

data class ProviderCredential(
    val name: String,
    val issuedBy: String,
    val isExternal: Boolean,
    val verifiedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
)

data class ProviderCapability(
    val providerId: String,
    val domain: UniversalServiceDomain,
    val skills: List<String>,
    val credentials: List<ProviderCredential> = emptyList(),
    val tools: List<String> = emptyList(),
    val serviceRadiusKm: Double? = null,
    val availableNow: Boolean = false,
    val completedJobsCount: Int = 0,
    val verifiedOutcomeRate: Double = 0.0,
    val repeatCustomerRate: Double = 0.0,
)
