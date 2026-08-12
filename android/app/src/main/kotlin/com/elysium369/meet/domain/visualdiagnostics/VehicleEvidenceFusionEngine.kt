package com.elysium369.meet.domain.visualdiagnostics

enum class VehicleTopologyField {
    COMBUSTION_TYPE,
    CYLINDER_LAYOUT,
    CYLINDER_COUNT,
    DISPLACEMENT_CC,
    ELECTRIFICATION,
    FORCED_INDUCTION,
    TRANSMISSION,
    DRIVE_LAYOUT,
    VOLTAGE_ARCHITECTURE,
}

enum class VehicleTopologyEvidenceSource {
    VIN,
    GARAGE,
    OBD,
    OEM,
    USER,
    CLOUD_HISTORY,
}

data class VehicleTopologyEvidenceClaim(
    val evidenceId: String,
    val field: VehicleTopologyField,
    val canonicalValue: String,
    val source: VehicleTopologyEvidenceSource,
    val strength: Double,
    val observedAt: Long,
    val vehicleBindingId: String?,
) {
    init {
        require(evidenceId.isNotBlank())
        require(canonicalValue.isNotBlank())
        require(strength in 0.0..1.0)
        require(observedAt > 0)
    }
}

data class VehicleTopologyFieldEvidence(
    val field: VehicleTopologyField,
    val claims: List<VehicleTopologyEvidenceClaim>,
    val rejectedEvidenceIds: List<String>,
)

data class FusedVehiclePowertrainTopology(
    val topology: VehiclePowertrainTopology,
    val evidenceSets: Map<VehicleTopologyField, VehicleTopologyFieldEvidence>,
    val hasConflicts: Boolean,
)

/**
 * Deterministic per-field fusion. New input never overwrites old input: incompatible
 * values produce EvidenceValue.Conflicted and remain unavailable to exact consumers.
 */
object VehicleEvidenceFusionEngine {
    fun fuse(
        claims: List<VehicleTopologyEvidenceClaim>,
        requiredVehicleBindingId: String?,
    ): FusedVehiclePowertrainTopology {
        val accepted = claims.filter {
            requiredVehicleBindingId == null || it.vehicleBindingId == requiredVehicleBindingId
        }
        val rejected = claims - accepted.toSet()
        val sets = VehicleTopologyField.entries.associateWith { field ->
            VehicleTopologyFieldEvidence(
                field = field,
                claims = accepted.filter { it.field == field }
                    .distinctBy(VehicleTopologyEvidenceClaim::evidenceId)
                    .sortedWith(compareByDescending<VehicleTopologyEvidenceClaim> { it.strength }
                        .thenByDescending { it.observedAt }.thenBy { it.evidenceId }),
                rejectedEvidenceIds = rejected.filter { it.field == field }.map { it.evidenceId }.sorted(),
            )
        }

        fun <T> resolve(
            field: VehicleTopologyField,
            parser: (String) -> T?,
        ): EvidenceValue<T> {
            val parsed = sets.getValue(field).claims.mapNotNull { claim ->
                parser(claim.canonicalValue.trim())?.let { value -> claim to value }
            }
            if (parsed.isEmpty()) return EvidenceValue.Unknown("Dato no capturado con evidencia válida para ${field.name}.")
            val groups = parsed.groupBy { it.second }
            if (groups.size > 1) {
                return EvidenceValue.Conflicted(
                    candidates = groups.values.map { group ->
                        val strongest = group.maxWith(
                            compareBy<Pair<VehicleTopologyEvidenceClaim, T>> { it.first.strength }
                                .thenBy { it.first.observedAt },
                        )
                        EvidenceValue.Known(
                            strongest.second,
                            strongest.first.source.toProvenance(),
                            strongest.first.strength,
                        )
                    },
                    reason = "Fuentes verificables discrepan para ${field.name}; no se eligió una silenciosamente.",
                )
            }
            val strongest = parsed.maxWith(
                compareBy<Pair<VehicleTopologyEvidenceClaim, T>> { it.first.strength }
                    .thenBy { it.first.observedAt },
            )
            return EvidenceValue.Known(
                strongest.second,
                strongest.first.source.toProvenance(),
                strongest.first.strength,
            )
        }

        val topology = VehiclePowertrainTopology(
            combustionType = resolve(VehicleTopologyField.COMBUSTION_TYPE) { enumValue<CombustionType>(it) },
            cylinderLayout = resolve(VehicleTopologyField.CYLINDER_LAYOUT) { enumValue<CylinderLayout>(it) },
            cylinderCount = resolve(VehicleTopologyField.CYLINDER_COUNT) { it.toIntOrNull()?.takeIf { n -> n in 1..24 } },
            electrification = resolve(VehicleTopologyField.ELECTRIFICATION) { enumValue<PowertrainElectrification>(it) },
            forcedInduction = resolve(VehicleTopologyField.FORCED_INDUCTION) { enumValue<ForcedInduction>(it) },
            displacementCc = resolve(VehicleTopologyField.DISPLACEMENT_CC) { it.toIntOrNull()?.takeIf { cc -> cc in 100..20_000 } },
            transmission = resolve(VehicleTopologyField.TRANSMISSION) { enumValue<TransmissionArchitecture>(it) },
            driveLayout = resolve(VehicleTopologyField.DRIVE_LAYOUT) { enumValue<DriveLayout>(it) },
            voltageArchitecture = resolve(VehicleTopologyField.VOLTAGE_ARCHITECTURE) { enumValue<VoltageArchitecture>(it) },
        )
        return FusedVehiclePowertrainTopology(
            topology = topology,
            evidenceSets = sets,
            hasConflicts = listOf(
                topology.combustionType,
                topology.cylinderLayout,
                topology.cylinderCount,
                topology.electrification,
                topology.forcedInduction,
                topology.displacementCc,
                topology.transmission,
                topology.driveLayout,
                topology.voltageArchitecture,
            ).any { it is EvidenceValue.Conflicted<*> },
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T? =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun VehicleTopologyEvidenceSource.toProvenance(): VehicleDataProvenance = when (this) {
        VehicleTopologyEvidenceSource.VIN -> VehicleDataProvenance.VIN_DECODED
        VehicleTopologyEvidenceSource.OBD -> VehicleDataProvenance.OBD_REPORTED
        VehicleTopologyEvidenceSource.OEM -> VehicleDataProvenance.OEM_DATA
        VehicleTopologyEvidenceSource.GARAGE,
        VehicleTopologyEvidenceSource.USER -> VehicleDataProvenance.USER_CONFIRMED
        VehicleTopologyEvidenceSource.CLOUD_HISTORY -> VehicleDataProvenance.INFERRED
    }
}
