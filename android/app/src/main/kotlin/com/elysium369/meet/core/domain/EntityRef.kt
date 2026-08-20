package com.elysium369.meet.core.domain

/**
 * MEET Vehicle Life OS — Universal Stable Entity References.
 * Provides type-safe cross-domain linking between Timeline, Proof Graph, Action Center, Passport, and Search.
 */
sealed interface EntityRef {
    val id: String
    val uri: String

    data class VehicleRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://vehicle/$id"
    }

    data class FindingRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://finding/$id"
    }

    data class DiagnosticSessionRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://diagnostic-session/$id"
    }

    data class RepairRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://repair/$id"
    }

    data class WorkOrderRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://work-order/$id"
    }

    data class PartRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://part/$id"
    }

    data class ComponentRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://component/$id"
    }

    data class DocumentRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://document/$id"
    }

    data class TripRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://trip/$id"
    }

    data class IncidentRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://incident/$id"
    }

    data class InspectionRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://inspection/$id"
    }

    data class EvidenceRef(override val id: String, val hashSha256: String? = null) : EntityRef {
        override val uri: String get() = "meet://evidence/$id"
    }

    data class CampaignRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://campaign/$id"
    }

    data class CostRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://cost/$id"
    }

    data class AccessGrantRef(override val id: String) : EntityRef {
        override val uri: String get() = "meet://access-grant/$id"
    }
}
