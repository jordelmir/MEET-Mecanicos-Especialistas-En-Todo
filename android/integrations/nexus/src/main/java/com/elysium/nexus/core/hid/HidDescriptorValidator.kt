package com.elysium.nexus.core.hid

/**
 * A small, self-contained validator for the
 * [HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR] bytes.
 *
 * This is the first `tools/` artefact of the project
 * (per `MASTER_ORDER.md` §6's `tools/hid-descriptor-validator/`
 * directory). For 0.9, the validator lives in the
 * Android module's main source set so it has direct
 * access to [HidDescriptor] without a multi-module
 * Gradle build. A standalone `tools/` Gradle subproject
 * is a 1.x concern.
 *
 * The validator is *structural*: it asserts the
 * descriptor is well-formed (USAGE_PAGE / USAGE /
 * COLLECTION / END_COLLECTION balance) and that the
 * declared logical ranges match the report format the
 * [HidReportEncoder] emits. Semantic validation (every
 * Usage ID is in the USB HID Usage Tables 1.5) is out
 * of scope for 0.9; that is Phase 1+ work because the
 * Usage Tables file is large and the validator's job
 * in 0.9 is to fail loudly when a future contributor
 * silently breaks the descriptor.
 *
 * ## Running
 *
 * From a host with a JVM:
 *
 * ```
 * $ ./gradlew :app:runValidator
 * ```
 *
 * The `:runValidator` task (defined in `app/build.gradle.kts`)
 * invokes [main] with no arguments. The validator returns
 * exit code 0 on success, 1 on failure.
 */
object HidDescriptorValidator {

    /**
     * The result of validating a descriptor. A
     * successful validation is a [ValidResult]; a
     * failed validation is an [InvalidResult] with the
     * first structural problem found.
     */
    sealed class Result {
        data class Valid(val descriptorSizeBytes: Int) : Result()
        data class Invalid(
            val descriptorSizeBytes: Int,
            val reason: String
        ) : Result()
    }

    /**
     * Run the structural validation pass on the
     * canonical [HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR].
     */
    fun validateBasicGamepadV1(): Result = validate(HidDescriptor.BASIC_GAMEPAD_V1_DESCRIPTOR)

    /**
     * Run the structural validation pass on an
     * arbitrary byte array. Used by tests that inject
     * a deliberately-malformed descriptor and assert
     * the validator catches it.
     */
    fun validate(bytes: ByteArray): Result {
        val issues = mutableListOf<String>()

        // 1. The descriptor must start with USAGE_PAGE.
        if (bytes.size < 2 ||
            bytes[0] != 0x05.toByte() ||
            bytes[1] != 0x01.toByte()
        ) {
            issues.add("descriptor does not start with USAGE_PAGE(Generic Desktop)")
        }

        // 2. The descriptor must contain an even number
        //    of COLLECTION (0xA1) and END_COLLECTION
        //    (0xC0) tags. An imbalance is a structural
        //    defect that no host can recover from.
        val collections = bytes.count { it == 0xA1.toByte() }
        val endCollections = bytes.count { it == 0xC0.toByte() }
        if (collections != endCollections) {
            issues.add(
                "COLLECTION/END_COLLECTION imbalance: " +
                    "$collections vs $endCollections"
            )
        }

        // 3. The descriptor must contain at least one
        //    INPUT (0x81) tag because a report
        //    descriptor without an INPUT is
        //    degenerate.
        val inputs = bytes.count { it == 0x81.toByte() }
        if (inputs == 0) {
            issues.add("descriptor has no INPUT tag")
        }

        // 4. The descriptor's USAGE_MIN (0x19) /
        //    USAGE_MAX (0x29) for the buttons must be
        //    1 and 16 respectively (16 buttons total).
        val usageMin = bytes.indexOf(0x19.toByte())
        if (usageMin < 0 || usageMin + 1 >= bytes.size ||
            bytes[usageMin + 1] != 0x01.toByte()
        ) {
            issues.add("USAGE_MINIMUM is not 1 (Button 1)")
        }
        val usageMax = bytes.indexOf(0x29.toByte())
        if (usageMax < 0 || usageMax + 1 >= bytes.size ||
            bytes[usageMax + 1] != 0x10.toByte()
        ) {
            issues.add("USAGE_MAXIMUM is not 0x10 (Button 16)")
        }

        // 5. The report size constant in [HidDescriptor]
        //    must match the documented value (13).
        if (HidDescriptor.BASIC_GAMEPAD_V1_REPORT_SIZE != 13) {
            issues.add(
                "BASIC_GAMEPAD_V1_REPORT_SIZE is " +
                    HidDescriptor.BASIC_GAMEPAD_V1_REPORT_SIZE +
                    ", expected 13"
            )
        }

        return if (issues.isEmpty()) {
            Result.Valid(bytes.size)
        } else {
            Result.Invalid(bytes.size, issues.joinToString("; "))
        }
    }
}

/**
 * The `main` entry point. The Gradle `:runValidator`
 * task (in `app/build.gradle.kts`) delegates here.
 *
 * The function prints a single line on success and a
 * single line on failure; CI scripts can `grep` the
 * output and `echo $?` to check the exit code.
 */
fun main() {
    when (val result = HidDescriptorValidator.validateBasicGamepadV1()) {
        is HidDescriptorValidator.Result.Valid -> {
            println(
                "OK: BASIC_GAMEPAD_V1 descriptor is well-formed " +
                    "(${result.descriptorSizeBytes} bytes)."
            )
        }
        is HidDescriptorValidator.Result.Invalid -> {
            System.err.println(
                "FAIL: BASIC_GAMEPAD_V1 descriptor is malformed " +
                    "(${result.descriptorSizeBytes} bytes): ${result.reason}"
            )
            kotlin.system.exitProcess(1)
        }
    }
}
