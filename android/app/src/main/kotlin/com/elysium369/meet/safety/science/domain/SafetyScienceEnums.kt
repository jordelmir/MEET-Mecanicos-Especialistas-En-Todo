package com.elysium369.meet.safety.science.domain

/**
 * Evidence lifecycle state — scientific evidence is NEVER deleted.
 *
 * A public withdrawal does NOT eliminate the preserved original.
 * Destruction requires who/when/why/policy/authorization/hash audit.
 */
enum class EvidenceRetentionState {
    ACTIVE,
    PRESERVED,
    LEGAL_HOLD,
    WITHDRAWN_PUBLICLY,
    EXPIRED_BY_POLICY,
    DESTROYED_WITH_AUDIT,
}

/**
 * Tombstone-only deletion semantics for scientific evidence.
 * True DELETE operations are prohibited.
 */
enum class EvidenceTombstoneReason {
    SUPERSEDED,
    WITHDRAWN,
    TOMBSTONED,
    DESTROYED_BY_RETENTION_POLICY,
}

/**
 * Privacy-aware location exposure levels.
 *
 * EXACT_COORDINATE must never automatically become PUBLIC_COORDINATE.
 * For sensitive cases use H3/geocell discretization.
 */
enum class LocationExposure {
    PRIVATE_EXACT,
    RESEARCH_APPROXIMATE,
    PUBLIC_GRID,
    PUBLIC_COUNTRY,
    HIDDEN,
}

/**
 * Extended source provenance classification — supplements the existing
 * `SourceRelation` types in the base Safety layer.
 */
enum class SourceProvenanceType {
    FIRST_HAND,
    SECOND_HAND,
    DERIVED,
    OFFICIAL_RECORD,
    PUBLIC_DATABASE,
    ARCHIVAL,
    JOURNALISTIC,
    SCIENTIFIC_PUBLICATION,
    SENSOR,
    SYSTEM_LOG,
    UNKNOWN,
}

/**
 * Research sensitivity levels — HIGH_IMPACT and above require dual review.
 */
enum class ResearchSensitivity {
    NORMAL,
    HIGH_IMPACT,
    PUBLIC_OFFICIAL,
    MASS_CASUALTY,
    ACTIVE_CRIMINAL_MATTER,
}

/**
 * Four-layer epistemological separation.
 *
 * ```
 * FACT → SCIENTIFIC_INFERENCE → LEGAL_QUALIFICATION → JUDICIAL_DETERMINATION
 * ```
 *
 * These MUST NEVER be fused.
 */
enum class EpistemicLayer {
    FACT,
    SCIENTIFIC_INFERENCE,
    LEGAL_QUALIFICATION,
    JUDICIAL_DETERMINATION,
}
