package com.elysium369.meet.domain.visualdiagnostics

enum class VehicleDataProvenance {
    USER_CONFIRMED, VIN_DECODED, OBD_REPORTED, OEM_DATA, INFERRED, UNKNOWN,
}

sealed interface EvidenceValue<out T> {
    data class Known<T>(
        val value: T,
        val provenance: VehicleDataProvenance,
        val evidenceStrength: Double,
    ) : EvidenceValue<T> {
        init {
            require(provenance != VehicleDataProvenance.UNKNOWN)
            require(evidenceStrength in 0.0..1.0)
        }
    }

    data class Unknown(val reason: String) : EvidenceValue<Nothing>

    data class Conflicted<T>(
        val candidates: List<Known<T>>,
        val reason: String,
    ) : EvidenceValue<T> {
        init { require(candidates.size >= 2) }
    }
}

val <T> EvidenceValue<T>.valueOrNull: T?
    get() = (this as? EvidenceValue.Known<T>)?.value

enum class CombustionType { GASOLINE, DIESEL, FLEX_FUEL, HYDROGEN, NONE, UNKNOWN }
enum class CylinderLayout { INLINE, V, BOXER, ROTARY, NONE, UNKNOWN }
enum class PowertrainElectrification { NONE, MHEV, HEV, PHEV, BEV, UNKNOWN }
enum class ForcedInduction { NATURALLY_ASPIRATED, TURBO, SUPERCHARGED, TWINCHARGED, UNKNOWN }
enum class DriveLayout { FWD, RWD, AWD, FOUR_WD, UNKNOWN }
enum class TransmissionArchitecture { AT, MT, CVT, DCT, ECVT, UNKNOWN }
enum class VoltageArchitecture { V12, V24, V48, HIGH_VOLTAGE, UNKNOWN }

data class VehiclePowertrainTopology(
    val combustionType: EvidenceValue<CombustionType>,
    val cylinderLayout: EvidenceValue<CylinderLayout>,
    val cylinderCount: EvidenceValue<Int>,
    val electrification: EvidenceValue<PowertrainElectrification>,
    val forcedInduction: EvidenceValue<ForcedInduction>,
    val displacementCc: EvidenceValue<Int>,
    val transmission: EvidenceValue<TransmissionArchitecture>,
    val driveLayout: EvidenceValue<DriveLayout>,
    val voltageArchitecture: EvidenceValue<VoltageArchitecture>,
)

data class PowertrainFieldProvenance(
    val engine: VehicleDataProvenance = VehicleDataProvenance.UNKNOWN,
    val fuel: VehicleDataProvenance = VehicleDataProvenance.UNKNOWN,
    val transmission: VehicleDataProvenance = VehicleDataProvenance.UNKNOWN,
    val displacement: VehicleDataProvenance = VehicleDataProvenance.UNKNOWN,
)

object VehiclePowertrainTopologyResolver {
    fun resolve(
        engineDescription: String?,
        fuelDescription: String?,
        transmissionDescription: String?,
        displacementCc: Int? = null,
        provenance: PowertrainFieldProvenance = PowertrainFieldProvenance(),
    ): VehiclePowertrainTopology {
        val engine = engineDescription.orEmpty().trim().lowercase()
        val fuel = fuelDescription.orEmpty().trim().lowercase()
        val transmission = transmissionDescription.orEmpty().trim().lowercase()

        val electrification = when {
            fuel.contains("phev") || fuel.contains("plug-in") || fuel.contains("enchufable") -> PowertrainElectrification.PHEV
            fuel.contains("mhev") || fuel.contains("mild hybrid") || fuel.contains("microhíbrido") -> PowertrainElectrification.MHEV
            fuel.contains("hybrid") || fuel.contains("híbrido") -> PowertrainElectrification.HEV
            fuel == "ev" || fuel.contains("bev") || fuel.contains("electric") || fuel.contains("eléctrico") -> PowertrainElectrification.BEV
            fuel.isNotBlank() -> PowertrainElectrification.NONE
            else -> PowertrainElectrification.UNKNOWN
        }
        val combustion = when {
            electrification == PowertrainElectrification.BEV -> CombustionType.NONE
            fuel.contains("diesel") || fuel.contains("diésel") -> CombustionType.DIESEL
            fuel.contains("flex") || fuel.contains("e85") -> CombustionType.FLEX_FUEL
            fuel.contains("hydrogen") || fuel.contains("hidrógeno") -> CombustionType.HYDROGEN
            fuel.contains("gas") || fuel.contains("petrol") || fuel.contains("gasolina") -> CombustionType.GASOLINE
            else -> CombustionType.UNKNOWN
        }
        val count = sequenceOf(12, 10, 8, 6, 5, 4, 3, 2).firstOrNull {
            engine.contains("$it cil") || Regex("(^|[^a-z0-9])[vilh]\\s*$it([^0-9]|$)").containsMatchIn(engine)
        }
        val layout = when {
            combustion == CombustionType.NONE -> CylinderLayout.NONE
            engine.contains("rotary") || engine.contains("wankel") -> CylinderLayout.ROTARY
            engine.contains("boxer") || engine.contains("flat") || engine.contains("h4") || engine.contains("h6") -> CylinderLayout.BOXER
            Regex("(^|[^a-z])v(6|8|10|12)([^0-9]|$)").containsMatchIn(engine) -> CylinderLayout.V
            listOf("inline", "straight", "l3", "l4", "l5", "l6", "i3", "i4", "i5", "i6").any(engine::contains) -> CylinderLayout.INLINE
            else -> CylinderLayout.UNKNOWN
        }
        val forced = when {
            engine.contains("twincharge") -> ForcedInduction.TWINCHARGED
            engine.contains("supercharg") || engine.contains("compresor") -> ForcedInduction.SUPERCHARGED
            engine.contains("turbo") -> ForcedInduction.TURBO
            else -> ForcedInduction.UNKNOWN
        }
        val transmissionType = when {
            transmission.contains("ecvt") || transmission.contains("e-cvt") -> TransmissionArchitecture.ECVT
            transmission.contains("dct") || transmission.contains("dsg") || transmission.contains("doble embrague") -> TransmissionArchitecture.DCT
            transmission.contains("cvt") -> TransmissionArchitecture.CVT
            transmission.contains("manual") || transmission == "mt" -> TransmissionArchitecture.MT
            transmission.contains("automatic") || transmission.contains("automát") || transmission == "at" -> TransmissionArchitecture.AT
            else -> TransmissionArchitecture.UNKNOWN
        }

        fun <T> evidence(value: T?, source: VehicleDataProvenance, strength: Double, unknown: (T) -> Boolean): EvidenceValue<T> =
            if (value == null || source == VehicleDataProvenance.UNKNOWN || unknown(value)) {
                EvidenceValue.Unknown("Dato no establecido con procedencia verificable.")
            } else {
                EvidenceValue.Known(value, source, strength)
            }

        return VehiclePowertrainTopology(
            combustionType = evidence(combustion, provenance.fuel, 0.9) { it == CombustionType.UNKNOWN },
            cylinderLayout = evidence(layout, provenance.engine, 0.85) { it == CylinderLayout.UNKNOWN },
            cylinderCount = evidence(count, provenance.engine, 0.85) { false },
            electrification = evidence(electrification, provenance.fuel, 0.9) { it == PowertrainElectrification.UNKNOWN },
            forcedInduction = evidence(forced, provenance.engine, 0.8) { it == ForcedInduction.UNKNOWN },
            displacementCc = evidence(displacementCc?.takeIf { it > 0 }, provenance.displacement, 0.95) { false },
            transmission = evidence(transmissionType, provenance.transmission, 0.9) { it == TransmissionArchitecture.UNKNOWN },
            driveLayout = EvidenceValue.Unknown("Disposición de tracción no capturada."),
            voltageArchitecture = if (electrification in setOf(PowertrainElectrification.PHEV, PowertrainElectrification.BEV) && provenance.fuel != VehicleDataProvenance.UNKNOWN) {
                EvidenceValue.Known(VoltageArchitecture.HIGH_VOLTAGE, VehicleDataProvenance.INFERRED, 0.6)
            } else {
                EvidenceValue.Unknown("Arquitectura de voltaje no capturada.")
            },
        )
    }
}
