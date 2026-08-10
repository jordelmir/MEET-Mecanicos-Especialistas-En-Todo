package com.elysium369.meet.domain.visualdiagnostics

enum class VehicleDataProvenance {
    USER_CONFIRMED,
    VIN_DECODED,
    OBD_REPORTED,
    OEM_DATA,
    INFERRED,
    UNKNOWN,
}

data class EvidencedVehicleValue<T>(
    val value: T?,
    val provenance: VehicleDataProvenance,
    val confidence: Double,
) {
    val isEstablished: Boolean
        get() = value != null && provenance != VehicleDataProvenance.UNKNOWN
}

enum class CombustionType { GASOLINE, DIESEL, FLEX_FUEL, HYDROGEN, NONE, UNKNOWN }
enum class CylinderLayout { INLINE, V, BOXER, ROTARY, NONE, UNKNOWN }
enum class PowertrainElectrification { NONE, MHEV, HEV, PHEV, BEV, UNKNOWN }
enum class ForcedInduction { NATURALLY_ASPIRATED, TURBO, SUPERCHARGED, TWINCHARGED, UNKNOWN }
enum class DriveLayout { FWD, RWD, AWD, FOUR_WD, UNKNOWN }
enum class TransmissionArchitecture { AT, MT, CVT, DCT, ECVT, UNKNOWN }
enum class VoltageArchitecture { V12, V24, V48, HIGH_VOLTAGE, UNKNOWN }

data class VehiclePowertrainTopology(
    val combustionType: EvidencedVehicleValue<CombustionType>,
    val cylinderLayout: EvidencedVehicleValue<CylinderLayout>,
    val cylinderCount: EvidencedVehicleValue<Int>,
    val electrification: EvidencedVehicleValue<PowertrainElectrification>,
    val forcedInduction: EvidencedVehicleValue<ForcedInduction>,
    val displacementCc: EvidencedVehicleValue<Int>,
    val transmission: EvidencedVehicleValue<TransmissionArchitecture>,
    val driveLayout: EvidencedVehicleValue<DriveLayout>,
    val voltageArchitecture: EvidencedVehicleValue<VoltageArchitecture>,
)

object VehiclePowertrainTopologyResolver {
    fun resolve(
        engineDescription: String?,
        fuelDescription: String?,
        transmissionDescription: String?,
        displacementCc: Int? = null,
        provenance: VehicleDataProvenance = VehicleDataProvenance.USER_CONFIRMED,
    ): VehiclePowertrainTopology {
        val engine = engineDescription.orEmpty().trim().lowercase()
        val fuel = fuelDescription.orEmpty().trim().lowercase()
        val transmission = transmissionDescription.orEmpty().trim().lowercase()

        // Specific electrified categories must be evaluated before generic "EV" tokens.
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
        val count = sequenceOf(12, 10, 8, 6, 5, 4, 3, 2)
            .firstOrNull {
                engine.contains("$it cil") ||
                    Regex("(^|[^a-z0-9])[vilh]\\s*$it([^0-9]|$)").containsMatchIn(engine)
            }
        val layout = when {
            combustion == CombustionType.NONE -> CylinderLayout.NONE
            engine.contains("rotary") || engine.contains("wankel") -> CylinderLayout.ROTARY
            engine.contains("boxer") || engine.contains("flat") || engine.contains("h4") || engine.contains("h6") -> CylinderLayout.BOXER
            Regex("(^|[^a-z])v(6|8|10|12)([^0-9]|$)").containsMatchIn(engine) -> CylinderLayout.V
            engine.contains("inline") || engine.contains("straight") || engine.contains("l3") ||
                engine.contains("l4") || engine.contains("l5") || engine.contains("l6") ||
                engine.contains("i3") || engine.contains("i4") || engine.contains("i5") || engine.contains("i6") -> CylinderLayout.INLINE
            else -> CylinderLayout.UNKNOWN
        }
        val forced = when {
            engine.contains("twincharge") -> ForcedInduction.TWINCHARGED
            engine.contains("supercharg") || engine.contains("compresor") -> ForcedInduction.SUPERCHARGED
            engine.contains("turbo") -> ForcedInduction.TURBO
            engine.isNotBlank() -> ForcedInduction.UNKNOWN
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
        val inferred = VehicleDataProvenance.INFERRED
        fun <T> evidenced(value: T, confidence: Double = 0.92) = EvidencedVehicleValue(value, provenance, confidence)

        return VehiclePowertrainTopology(
            combustionType = evidenced(combustion),
            cylinderLayout = EvidencedVehicleValue(layout, if (layout == CylinderLayout.UNKNOWN) inferred else provenance, if (layout == CylinderLayout.UNKNOWN) 0.25 else 0.9),
            cylinderCount = EvidencedVehicleValue(count, if (count == null) VehicleDataProvenance.UNKNOWN else provenance, if (count == null) 0.0 else 0.9),
            electrification = evidenced(electrification),
            forcedInduction = EvidencedVehicleValue(forced, if (forced == ForcedInduction.UNKNOWN) VehicleDataProvenance.UNKNOWN else provenance, if (forced == ForcedInduction.UNKNOWN) 0.0 else 0.85),
            displacementCc = EvidencedVehicleValue(displacementCc?.takeIf { it > 0 }, if (displacementCc != null && displacementCc > 0) provenance else VehicleDataProvenance.UNKNOWN, if (displacementCc != null && displacementCc > 0) 0.95 else 0.0),
            transmission = evidenced(transmissionType),
            driveLayout = EvidencedVehicleValue(DriveLayout.UNKNOWN, VehicleDataProvenance.UNKNOWN, 0.0),
            voltageArchitecture = EvidencedVehicleValue(
                if (electrification in setOf(PowertrainElectrification.PHEV, PowertrainElectrification.BEV)) VoltageArchitecture.HIGH_VOLTAGE else VoltageArchitecture.UNKNOWN,
                if (electrification in setOf(PowertrainElectrification.PHEV, PowertrainElectrification.BEV)) inferred else VehicleDataProvenance.UNKNOWN,
                if (electrification in setOf(PowertrainElectrification.PHEV, PowertrainElectrification.BEV)) 0.6 else 0.0,
            ),
        )
    }
}
