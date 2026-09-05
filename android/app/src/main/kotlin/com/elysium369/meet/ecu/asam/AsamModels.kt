package com.elysium369.meet.ecu.asam

/**
 * Section 22-24: ASAM ODX (MCD-2 D) and ASAM A2L (MCD-2 MC) Normalized Architecture.
 *
 * DOCTRINE:
 * ODX/PDX describes diagnostic communications, memory programming, parameters, and variant coding.
 * A2L describes calibration parameters, 2D curves, 3D maps, dimensions, memory addresses, and conversions.
 *
 * This provides the clean-room normalized representation used by the capability compiler.
 */

// ═════════════════════════════════════════════════════════════════════════════
// ASAM ODX (MCD-2 D) DIAGNOSTIC MODEL
// ═════════════════════════════════════════════════════════════════════════════

data class OdxParameter(
    val shortName: String,
    val longName: String?,
    val bytePosition: Int,
    val bitLength: Int,
    val semantic: String? = null,
    val physicalType: String = "A_UINT32",
)

data class OdxService(
    val id: String,
    val shortName: String,
    val serviceIdHex: String,
    val requestParameters: List<OdxParameter>,
    val positiveResponseServiceIdHex: String,
    val isSessionControl: Boolean = false,
    val isSecurityAccess: Boolean = false,
    val isRoutineControl: Boolean = false,
    val isMemoryOperation: Boolean = false,
) {
    val isWriteOrStateAltering: Boolean
        get() = isSessionControl || isSecurityAccess || isRoutineControl || isMemoryOperation
}

data class OdxVariant(
    val variantId: String,
    val shortName: String,
    val ecuHardwareNumber: String?,
    val ecuSoftwareNumber: String?,
    val services: List<OdxService>,
)

data class OdxDiagnosticModel(
    val modelId: String,
    val schemaVersion: String,
    val manufacturer: String,
    val variants: List<OdxVariant>,
    val sha256SourceHash: String,
) {
    init {
        require(modelId.isNotBlank())
        require(variants.isNotEmpty())
        require(sha256SourceHash.matches(Regex("^[0-9a-fA-F]{64}$")))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ASAM A2L (MCD-2 MC) CALIBRATION MODEL
// ═════════════════════════════════════════════════════════════════════════════

enum class A2lCharacteristicType {
    VALUE,       // Single scalar parameter
    VAL_BLK,     // 1D array of values
    CURVE,       // 2D lookup curve (1 input axis -> 1 output)
    MAP,         // 3D lookup map (2 input axes -> 1 output table)
    CUBOID,      // 4D lookup grid (3 input axes -> 1 output)
}

data class A2lComputationMethod(
    val name: String,
    val formulaOrCoefficients: String,
    val unit: String,
)

data class A2lAxisDescription(
    val axisIdentifier: String,
    val inputQuantityName: String,
    val computationMethodName: String,
    val pointCount: Int,
    val lowerLimit: Double,
    val upperLimit: Double,
)

data class A2lCharacteristic(
    val identifier: String,
    val longIdentifier: String?,
    val type: A2lCharacteristicType,
    val address: Long,
    val recordLayoutName: String,
    val maxDifference: Double = 0.0,
    val computationMethodName: String,
    val lowerLimit: Double,
    val upperLimit: Double,
    val axes: List<A2lAxisDescription> = emptyList(),
) {
    init {
        require(identifier.isNotBlank()) { "Characteristic identifier required" }
        require(address >= 0) { "Address must be non-negative" }
        require(upperLimit >= lowerLimit) { "Upper limit must be >= lower limit" }
        when (type) {
            A2lCharacteristicType.CURVE -> require(axes.size == 1) { "CURVE requires exactly 1 axis" }
            A2lCharacteristicType.MAP -> require(axes.size == 2) { "MAP requires exactly 2 axes (X and Y)" }
            A2lCharacteristicType.CUBOID -> require(axes.size == 3) { "CUBOID requires exactly 3 axes" }
            else -> {}
        }
    }
}

data class A2lCalibrationModel(
    val modelId: String,
    val projectName: String,
    val ecuHardwareNumber: String,
    val epkSoftwareVersion: String,
    val characteristics: List<A2lCharacteristic>,
    val computationMethods: Map<String, A2lComputationMethod>,
    val sourceHash: String,
) {
    init {
        require(modelId.isNotBlank())
        require(characteristics.isNotEmpty())
        require(sourceHash.matches(Regex("^[0-9a-fA-F]{64}$")))
    }

    fun findCharacteristic(name: String): A2lCharacteristic? =
        characteristics.firstOrNull { it.identifier.equals(name, ignoreCase = true) }
}
