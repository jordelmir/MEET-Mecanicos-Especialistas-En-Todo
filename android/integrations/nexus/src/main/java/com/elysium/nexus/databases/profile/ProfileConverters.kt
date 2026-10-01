package com.elysium.nexus.databases.profile

import androidx.room.TypeConverter
import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.profile.CanonicalBinding
import com.elysium.nexus.core.profile.ControlType
import com.elysium.nexus.core.profile.NormalizedRect

/**
 * Room TypeConverters for the profile database.
 *
 * Phase 1.2's profile schema is two tables:
 *
 *  - `profile`: id, name, author, version, createdAt,
 *    updatedAt.
 *  - `profile_control`: profileId (FK), controlId,
 *    type, binding, visualBounds, hitBounds, zIndex,
 *    rotation, opacity, ordering.
 *
 * The converters handle the non-primitive fields:
 *  - [ControlType] ↔ String (the enum's name).
 *  - [CanonicalBinding] ↔ String (a compact textual
 *    form, parsed in `parseBinding`).
 *  - [NormalizedRect] ↔ String (4 floats separated by
 *    commas).
 *
 * The `binding` format is documented at the converter.
 * Adding a new binding variant requires adding a parse
 * case; the compiler will flag missing branches via
 * the `when` exhaustiveness check.
 */
object ProfileConverters {

    // ---- ControlType ----

    @TypeConverter
    @JvmStatic
    fun fromControlType(type: ControlType): String = type.name

    @TypeConverter
    @JvmStatic
    fun toControlType(name: String): ControlType = ControlType.valueOf(name)

    // ---- NormalizedRect ----

    /** The delimiter used inside a NormalizedRect's serialized form. */
    private const val RECT_DELIMITER: String = ","

    @TypeConverter
    @JvmStatic
    fun fromRect(rect: NormalizedRect): String =
        "${rect.x}$RECT_DELIMITER${rect.y}$RECT_DELIMITER" +
            "${rect.width}$RECT_DELIMITER${rect.height}"

    @TypeConverter
    @JvmStatic
    fun toRect(serialized: String): NormalizedRect {
        val parts = serialized.split(RECT_DELIMITER)
        require(parts.size == 4) {
            "NormalizedRect expects 4 comma-separated parts, got $serialized"
        }
        return NormalizedRect(
            x = parts[0].toFloat(),
            y = parts[1].toFloat(),
            width = parts[2].toFloat(),
            height = parts[3].toFloat()
        )
    }

    // ---- CanonicalBinding ----
    //
    // The format is one of:
    //   "Button:<buttonOrdinal>"  e.g. "Button:0" for South
    //   "Stick:<Left|Right>"      e.g. "Stick:Left"
    //   "Trigger:<Left|Right>"    e.g. "Trigger:Left"
    //   "Neutralize"               the §38 button
    //
    // Parsing is total over the closed set of bindings
    // the schema knows about. A future contributor who
    // adds a new binding variant must add a new branch
    // to the `parseBinding` `when`.

    @TypeConverter
    @JvmStatic
    fun fromBinding(binding: CanonicalBinding): String = when (binding) {
        is CanonicalBinding.Button -> "Button:${binding.button.ordinal}"
        is CanonicalBinding.Stick -> "Stick:${binding.side.name}"
        is CanonicalBinding.Trigger -> "Trigger:${binding.side.name}"
        CanonicalBinding.Neutralize -> "Neutralize"
    }

    @TypeConverter
    @JvmStatic
    fun toBinding(serialized: String): CanonicalBinding {
        val colon = serialized.indexOf(':')
        val tag: String
        val arg: String?
        if (colon < 0) {
            tag = serialized
            arg = null
        } else {
            tag = serialized.substring(0, colon)
            arg = serialized.substring(colon + 1)
        }
        return parseBinding(tag, arg)
    }

    private fun parseBinding(tag: String, arg: String?): CanonicalBinding = when (tag) {
        "Neutralize" -> {
            require(arg == null) { "Neutralize takes no argument (got '$arg')." }
            CanonicalBinding.Neutralize
        }
        "Button" -> {
            requireNotNull(arg) { "Button binding requires a button ordinal." }
            val ordinal = arg.toInt()
            require(ordinal in CanonicalButton.values().indices) {
                "Button ordinal $ordinal out of range."
            }
            CanonicalBinding.Button(CanonicalButton.values()[ordinal])
        }
        "Stick" -> {
            requireNotNull(arg) { "Stick binding requires a side." }
            val side = StickSide.valueOf(arg)
            CanonicalBinding.Stick(side)
        }
        "Trigger" -> {
            requireNotNull(arg) { "Trigger binding requires a side." }
            val side = StickSide.valueOf(arg)
            CanonicalBinding.Trigger(side)
        }
        else -> throw IllegalArgumentException(
            "Unknown binding tag '$tag' (full: '${if (arg == null) tag else "$tag:$arg"}')."
        )
    }
}
