package com.elysium.nexus.fabric.infrared

/**
 * §6.5 Protocol Repeat Policy enum.
 */
enum class RepeatPolicy {
    NONE,
    FULL_FRAME,
    SPECIAL_REPEAT_FRAME,
    TOGGLE_PER_NEW_PRESS,
    STATE_FRAME
}

/**
 * §6.3 Codec Verification Status — honest states per dictamen §7.
 *
 * Only UNIT_SHAPE_VALIDATED is claimed until decoder round-trip and HIL proof exist.
 */
enum class CodecVerificationStatus {
    EXPERIMENTAL,
    UNIT_SHAPE_VALIDATED,
    GOLDEN_VECTOR_VERIFIED,
    DECODER_ROUNDTRIP_VERIFIED,
    HIL_VERIFIED,
    CODEC_BLOCKED
}

/**
 * §6.3 Protocol variant — preserves SIRC12/15/20, NEC variants, RC5x, etc.
 */
data class ProtocolVariant(
    val variantId: String,
    val bits: Int,
    val addressBits: Int,
    val commandBits: Int,
    val description: String
)

/**
 * §6.3 Codec Specification Metadata — now includes variants.
 */
data class CodecSpec(
    val codecId: String,
    val protocol: IrProtocol,
    val aliases: Set<String>,
    val defaultCarrierHz: Int,
    val carrierHzRange: IntRange,
    val repeatPolicy: RepeatPolicy,
    val status: CodecVerificationStatus,
    val goldenVectorCount: Int,
    val variants: List<ProtocolVariant> = emptyList()
)

/**
 * Result of resolving a codec from the catalog.
 */
sealed interface CodecResolution {
    data class Resolved(val codec: CodecSpec, val variant: ProtocolVariant? = null) : CodecResolution
    /** P0.5: Multiple variants exist but no hint was provided — caller must disambiguate. */
    data class VariantAmbiguous(
        val codec: CodecSpec,
        val candidates: List<ProtocolVariant>,
        val reason: String
    ) : CodecResolution
    /** V06.2 Phase 4: Caller requested a specific variant that does not exist in the codec spec. */
    data class VariantUnsupported(
        val codec: CodecSpec,
        val requestedVariant: String,
        val availableVariants: List<String>
    ) : CodecResolution
    data class Unsupported(val codecId: String, val reason: String) : CodecResolution
    data class Blocked(val codec: CodecSpec, val reason: String) : CodecResolution
}

/**
 * §6.3 Protocol Codec Registry.
 *
 * Authoritative registry of supported IR protocol codecs.
 * Honest verification states: only UNIT_SHAPE_VALIDATED until
 * decoder round-trip and HIL are proven.
 */
object ProtocolCodecRegistry {
    private val registeredCodecs: Map<String, CodecSpec>

    init {
        // §6.3 Normalize all keys and aliases to UPPERCASE for case-insensitive lookup
        val raw = mapOf(
            "NEC" to CodecSpec(
                codecId = "NEC",
                protocol = IrProtocol.Nec,
                aliases = setOf("NEC1", "NEC_38"),
                defaultCarrierHz = 38000,
                carrierHzRange = 36000..40000,
                repeatPolicy = RepeatPolicy.SPECIAL_REPEAT_FRAME,
                status = CodecVerificationStatus.UNIT_SHAPE_VALIDATED,
                goldenVectorCount = 12,
                variants = listOf(
                    ProtocolVariant("NEC_32", 32, 8, 8, "Standard NEC 32-bit (address + inverse + command + inverse)"),
                    ProtocolVariant("NEC_32_EXT", 32, 8, 8, "NEC with extended address via sub-device")
                )
            ),
            "NECX" to CodecSpec(
                codecId = "NECx",
                protocol = IrProtocol.NecExtended,
                aliases = setOf("NECEXT", "NEC_EXTENDED", "NECExtended"),
                defaultCarrierHz = 38000,
                carrierHzRange = 36000..40000,
                repeatPolicy = RepeatPolicy.SPECIAL_REPEAT_FRAME,
                status = CodecVerificationStatus.UNIT_SHAPE_VALIDATED,
                goldenVectorCount = 10,
                variants = listOf(
                    ProtocolVariant("NECx_32", 32, 16, 8, "NEC Extended 16-bit address")
                )
            ),
            "SAMSUNG" to CodecSpec(
                codecId = "SAMSUNG",
                protocol = IrProtocol.Samsung,
                aliases = setOf("SAMSUNG32", "Samsung32"),
                defaultCarrierHz = 38000,
                carrierHzRange = 36000..40000,
                repeatPolicy = RepeatPolicy.FULL_FRAME,
                status = CodecVerificationStatus.UNIT_SHAPE_VALIDATED,
                goldenVectorCount = 8,
                variants = listOf(
                    ProtocolVariant("SAMSUNG_32", 32, 8, 8, "Samsung 32-bit standard"),
                    ProtocolVariant("SAMSUNG_20", 20, 5, 8, "Samsung 20-bit short")
                )
            ),
            "SIRC" to CodecSpec(
                codecId = "SIRC",
                protocol = IrProtocol.SonySirc,
                // RC-12: SIRC15/SIRC20 are catalog family names for the
                // SIRC_15 / SIRC_20 variants — the encoder supports them
                // (addressBits=8/13), so the codec must resolve them.
                aliases = setOf("SONY", "SONYSIRC", "SIRC15", "SIRC20"),
                defaultCarrierHz = 40000,
                carrierHzRange = 38000..42000,
                repeatPolicy = RepeatPolicy.FULL_FRAME,
                status = CodecVerificationStatus.UNIT_SHAPE_VALIDATED,
                goldenVectorCount = 6,
                variants = listOf(
                    ProtocolVariant("SIRC_12", 12, 5, 7, "SIRC 12-bit (address 5, command 7)"),
                    ProtocolVariant("SIRC_15", 15, 8, 7, "SIRC 15-bit (address 8, command 7)"),
                    ProtocolVariant("SIRC_20", 20, 5, 7, "SIRC 20-bit extended (5-bit address, 8-bit sub, 7-bit command)")
                )
            ),
            "RC5" to CodecSpec(
                codecId = "RC5",
                protocol = IrProtocol.Rc5,
                aliases = setOf("RC5X"),
                defaultCarrierHz = 36000,
                carrierHzRange = 35000..38000,
                repeatPolicy = RepeatPolicy.TOGGLE_PER_NEW_PRESS,
                status = CodecVerificationStatus.EXPERIMENTAL,
                goldenVectorCount = 5,
                variants = listOf(
                    ProtocolVariant("RC5_14", 14, 5, 6, "RC5 standard 14-bit (1 start + 1 toggle + 5 addr + 6 cmd)"),
                    ProtocolVariant("RC5X_16", 16, 5, 8, "RC5X extended 16-bit")
                )
            ),
            "RC6" to CodecSpec(
                codecId = "RC6",
                protocol = IrProtocol.Rc6,
                aliases = setOf("RC6_0", "RC6MODE0"),
                defaultCarrierHz = 36000,
                carrierHzRange = 35000..38000,
                repeatPolicy = RepeatPolicy.TOGGLE_PER_NEW_PRESS,
                status = CodecVerificationStatus.EXPERIMENTAL,
                goldenVectorCount = 4,
                variants = listOf(
                    ProtocolVariant("RC6_16", 16, 8, 8, "RC6 Mode 0 standard 16-bit"),
                    ProtocolVariant("RC6_20", 20, 16, 8, "RC6 Mode 0 extended 20-bit")
                )
            ),
            "KASEIKYO" to CodecSpec(
                codecId = "KASEIKYO",
                protocol = IrProtocol.Kaseikyo,
                aliases = setOf("PANASONIC"),
                defaultCarrierHz = 38000,
                carrierHzRange = 36000..40000,
                repeatPolicy = RepeatPolicy.FULL_FRAME,
                status = CodecVerificationStatus.EXPERIMENTAL,
                goldenVectorCount = 4,
                variants = listOf(
                    ProtocolVariant("KASEIKYO_48", 48, 16, 8, "Kaseikyo/Panasonic 48-bit standard")
                )
            ),
            "AIWA" to CodecSpec(
                codecId = "AIWA",
                protocol = IrProtocol.Aiwa,
                aliases = setOf("AIWA_RC501"),
                defaultCarrierHz = 38123,
                carrierHzRange = 36000..40000,
                repeatPolicy = RepeatPolicy.FULL_FRAME,
                status = CodecVerificationStatus.UNIT_SHAPE_VALIDATED,
                goldenVectorCount = 3,
                variants = listOf(
                    ProtocolVariant("AIWA_42", 42, 8, 8, "Aiwa RC501 42-bit (D:8, S:5, ~D:8, ~S:5, F:8, ~F:8) — used by Konka/Telstar rebadges")
                )
            )
        )
        // Build normalized map: all keys UPPERCASE, aliases UPPERCASE
        registeredCodecs = mutableMapOf<String, CodecSpec>().apply {
            for ((_, spec) in raw) {
                val upperKey = spec.codecId.uppercase()
                put(upperKey, spec)
                for (alias in spec.aliases) {
                    put(alias.uppercase(), spec)
                }
            }
        }
    }

    /**
     * Get codec by ID — resolves case-insensitively via normalized UPPERCASE map.
     */
    fun getCodec(codecId: String): CodecSpec? {
        val key = codecId.trim().uppercase()
        return registeredCodecs[key]
    }

    /**
     * Resolve a codec with variant detection. Returns [CodecResolution] sealed type.
     * Zero silent NEC fallback. V06.2 Phase 4: returns VariantUnsupported instead of
     * silently falling through to firstOrNull().
     */
    fun resolve(codecId: String, variantHint: String? = null): CodecResolution {
        val spec = getCodec(codecId)
            ?: return CodecResolution.Unsupported(codecId, "Codec '$codecId' is not registered.")

        if (spec.status == CodecVerificationStatus.CODEC_BLOCKED) {
            return CodecResolution.Blocked(spec, "Codec '${spec.codecId}' is blocked for production use.")
        }

        // P0.5: No silent firstOrNull() fallback. If multiple variants exist,
        // the caller MUST provide a variantHint to disambiguate.
        val variant = variantHint?.let { hint ->
            spec.variants.firstOrNull { v ->
                v.variantId.equals(hint, ignoreCase = true) ||
                v.description.contains(hint, ignoreCase = true)
            }
        }

        if (variant == null && spec.variants.size > 1 && variantHint == null) {
            return CodecResolution.VariantAmbiguous(
                codec = spec,
                candidates = spec.variants,
                reason = "Codec '${spec.codecId}' has ${spec.variants.size} variants but no variantHint was provided. " +
                    "Available: ${spec.variants.joinToString { it.variantId }}"
            )
        }

        // V06.2 Phase 4: If a variantHint was provided but no match was found,
        // return VariantUnsupported instead of silently passing null.
        if (variant == null && variantHint != null) {
            return CodecResolution.VariantUnsupported(
                codec = spec,
                requestedVariant = variantHint,
                availableVariants = spec.variants.map { it.variantId }
            )
        }

        // Single-variant codec with no hint: auto-select the only variant.
        return CodecResolution.Resolved(spec, variant ?: spec.variants.firstOrNull())
    }

    /**
     * Check if a codec can be used in production transmission.
     * P0-7: EXPERIMENTAL and CODEC_BLOCKED are both forbidden in production.
     * Only UNIT_SHAPE_VALIDATED, GOLDEN_VECTOR_VERIFIED, DECODER_ROUNDTRIP_VERIFIED,
     * and HIL_VERIFIED are allowed.
     */
    fun isCodecTransmittable(codecId: String): Boolean {
        val codec = getCodec(codecId) ?: return false
        return codec.status != CodecVerificationStatus.CODEC_BLOCKED &&
               codec.status != CodecVerificationStatus.EXPERIMENTAL
    }

    fun allRegistered(): List<CodecSpec> = registeredCodecs.values.toList()
}
