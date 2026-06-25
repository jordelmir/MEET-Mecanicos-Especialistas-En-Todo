package com.elysium369.meet.ui.components.gauges

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All available gauge visual styles for the Scanner dashboard.
 * Each style completely replaces the gauge rendering across the entire dashboard.
 * New styles can be added here — the cycle button will automatically include them.
 */
enum class GaugeStyleSet(
    val displayName: String,
    val icon: String,
    val description: String
) {
    ELITE(
        displayName = "Elite Tactical",
        icon = "🎯",
        description = "Estilo militar táctico con aguja analógica y CRT scanlines"
    ),
    CLASSIC(
        displayName = "Classic V2.5",
        icon = "⚡",
        description = "Arco limpio y minimalista con valor digital grande"
    ),
    CYBER(
        displayName = "Cyber HUD",
        icon = "🔵",
        description = "Display holográfico tipo videojuego con barras neón"
    ),
    RACING(
        displayName = "Racing F1",
        icon = "🏎️",
        description = "Segmentos LED tipo F1 con shift lights"
    ),
    RADIAL(
        displayName = "Radial Smartwatch",
        icon = "💎",
        description = "Anillo grueso con gradiente estilo smartwatch"
    ),
    THERMO(
        displayName = "Industrial",
        icon = "🌡️",
        description = "Barra vertical tipo termómetro industrial"
    ),
    HOLOGRAM(
        displayName = "Holographic",
        icon = "🔮",
        description = "Anillos concéntricos holográficos estilo Sci-Fi"
    ),
    NEON_RETRO(
        displayName = "Neon Retrowave",
        icon = "🌆",
        description = "Estética synthwave 80s con glow extremo"
    ),
    LAMBO(
        displayName = "Lamborghini",
        icon = "🔶",
        description = "Cockpit angular estilo Reventón con naranja eléctrico"
    ),
    PLASMA(
        displayName = "Plasma Energy",
        icon = "⚡",
        description = "Campo de plasma azul/violeta con arcos de energía"
    ),
    AURORA(
        displayName = "Midnight Aurora",
        icon = "🌌",
        description = "Aurora boreal púrpura/teal con partículas flotantes"
    ),
    FERRARI(
        displayName = "Ferrari Rosso",
        icon = "🏁",
        description = "Rosso Corsa con shift lights y aguja roja italiana"
    ),
    TOKYO(
        displayName = "Tokyo Drift",
        icon = "🗼",
        description = "JDM midnight blue con rosa sakura y kanji"
    ),
    MILITARY(
        displayName = "Night Ops",
        icon = "🪖",
        description = "Visión nocturna verde fósforo con radar táctico"
    ),
    DIAMOND(
        displayName = "Diamond Luxury",
        icon = "💠",
        description = "Bugatti chrome/plata con azul hielo y joya dorada"
    ),
    COCKPIT(
        displayName = "Cockpit Avión",
        icon = "✈️",
        description = "Panel de avión con ámbar/verde y aguja triangular"
    ),
    LIGHTNING(
        displayName = "Tormenta Eléctrica",
        icon = "⚡",
        description = "Rayos y tormentas eléctricas con destellos neón"
    ),
    RAIN(
        displayName = "Lluvia Tropical",
        icon = "🌧️",
        description = "Gotas de lluvia cayendo y ondas en la base"
    ),
    SNOW(
        displayName = "Nevada Ártica",
        icon = "❄️",
        description = "Copos de nieve flotantes acumulándose en el fondo"
    ),
    TORNADO(
        displayName = "Tornado F5",
        icon = "🌪️",
        description = "Vórtice giratorio de viento en el centro del dial"
    ),
    SANDSTORM(
        displayName = "Tormenta de Arena",
        icon = "🏜️",
        description = "Tormenta de arena desértica con ráfagas horizontales"
    ),
    VOLCANO(
        displayName = "Lava Volcánica",
        icon = "🌋",
        description = "Flujo de lava y chispas ascendentes en el dial"
    ),
    TSUNAMI(
        displayName = "Marea Tsunami",
        icon = "🌊",
        description = "Olas gigantes animadas subiendo por el dial"
    ),
    BLIZZARD(
        displayName = "Ventisca Polar",
        icon = "🌬️",
        description = "Ventisca polar con ráfagas rápidas y escarcha"
    ),
    AURORA_AUTO(
        displayName = "Aurora Boreal Pro",
        icon = "🌌",
        description = "Cortinas de aurora boreal dinámicas e hipnóticas"
    ),
    SOLAR_FLARE(
        displayName = "Llamarada Solar",
        icon = "☀️",
        description = "Llamaradas solares y plasma saliendo del centro"
    ),
    COSMIC_DUST(
        displayName = "Polvo Cósmico",
        icon = "☄️",
        description = "Nebulosa de polvo estelar giratoria y estrellas"
    ),
    EARTHQUAKE(
        displayName = "Falla Tectónica",
        icon = "🪨",
        description = "Efecto de vibración y grietas tectónicas activas"
    ),
    METEOR_SHOWER(
        displayName = "Lluvia de Meteoros",
        icon = "🌠",
        description = "Lluvia de meteoros veloces cruzando el fondo"
    ),
    HURRICANE(
        displayName = "Ciclón Cat 5",
        icon = "🌀",
        description = "Espiral ciclónica de viento con ojo central"
    ),
    FOGGY_MIST(
        displayName = "Niebla Densa",
        icon = "🌫️",
        description = "Nubes de niebla densa flotando a la deriva"
    ),
    WILD_FIRE(
        displayName = "Fuego Feroz",
        icon = "🔥",
        description = "Llamas de fuego danzantes en la base del dial"
    ),
    OCEAN_DEPTH(
        displayName = "Abismo Abisal",
        icon = "⚓",
        description = "Barrido de sonar abisal y burbujas ascendentes"
    ),
    ECLIPSE(
        displayName = "Eclipse Corona",
        icon = "🌑",
        description = "Corona solar asomándose tras la luna negra"
    ),
    RAINBOW_RAIN(
        displayName = "Lluvia Arcoiris",
        icon = "🌈",
        description = "Gotas de lluvia arcoíris neón multicolor"
    ),
    SAND_GLOW(
        displayName = "Espejismo del Desierto",
        icon = "🐪",
        description = "Espejismo de calor sobre dunas desérticas"
    ),
    THUNDER_CLOUD(
        displayName = "Nube Tormentosa",
        icon = "☁️",
        description = "Nubes oscuras con rayos internos parpadeantes"
    ),
    ICY_FROST(
        displayName = "Escarcha Helada",
        icon = "🧊",
        description = "Crecimiento de cristales de hielo en el borde"
    ),
    CYBER_STORM(
        displayName = "Tormenta Matrix",
        icon = "📟",
        description = "Lluvia digital de matriz verde neón cayendo"
    ),
    BLACK_HOLE(
        displayName = "Agujero Negro",
        icon = "🕳️",
        description = "Disco de acreción deformando el espacio-tiempo"
    ),
    MONSOON(
        displayName = "Monzón Torrencial",
        icon = "🌧️",
        description = "Hojas densas de lluvia monzónica inclinada"
    ),
    COMET_TAIL(
        displayName = "Cola de Cometa",
        icon = "💫",
        description = "Aguja con cola de cometa y estela de polvo"
    ),
    GALAXY_CORE(
        displayName = "Núcleo de Galaxia",
        icon = "✨",
        description = "Púlsares y ondas gravitacionales en el centro"
    ),
    ACID_RAIN(
        displayName = "Lluvia Ácida",
        icon = "🧪",
        description = "Lluvia ácida verde neón con salpicaduras"
    ),
    SUPERNOVA(
        displayName = "Supernova Estelar",
        icon = "💥",
        description = "Expansión de ondas de choque estelares"
    ),
    WIND_TUNNEL(
        displayName = "Túnel de Viento",
        icon = "🌪️",
        description = "Líneas aerodinámicas y flujo de aire turbulento"
    ),
    CUSTOM_DIY(
        displayName = "Creador DIY",
        icon = "🛠️",
        description = "Diseña tu propio reloj con agujas, bordes e imágenes de fondo"
    );

    fun next(): GaugeStyleSet {
        val values = entries
        val nextIndex = (ordinal + 1) % values.size
        return values[nextIndex]
    }

    fun previous(): GaugeStyleSet {
        val values = entries
        val prevIndex = if (ordinal == 0) values.size - 1 else ordinal - 1
        return values[prevIndex]
    }
}

data class GaugeColorScheme(
    val bezelColor: Color,
    val internalColor: Color,
    val textColor: Color,
    val needleColor: Color,
    val specialColor: Color,
    val labelColor: Color = textColor,
    val unitColor: Color = internalColor
)

val LocalGaugeColorScheme = staticCompositionLocalOf<GaugeColorScheme> {
    GaugeColorScheme(
        bezelColor = Color(0xFF3E3E3E),
        internalColor = Color(0xFF00E5FF),
        textColor = Color(0xFF00E5FF),
        needleColor = Color(0xFF00E5FF),
        specialColor = Color(0xFF00E5FF)
    )
}

/**
 * Manages the currently selected gauge style with SharedPreferences persistence.
 * Injected as a singleton via Hilt so all screens share the same style state.
 */
class GaugeStyleManager(context: Context) {

    private val prefs = context.getSharedPreferences("meet_gauge_prefs", Context.MODE_PRIVATE)
    private val KEY_STYLE = "selected_gauge_style"

    private val _currentStyle = MutableStateFlow(loadStyle())
    val currentStyle: StateFlow<GaugeStyleSet> = _currentStyle.asStateFlow()

    private fun loadStyle(): GaugeStyleSet {
        val saved = prefs.getString(KEY_STYLE, null)
        return try {
            if (saved != null) GaugeStyleSet.valueOf(saved) else GaugeStyleSet.ELITE
        } catch (e: Exception) {
            GaugeStyleSet.ELITE
        }
    }

    fun selectStyle(style: GaugeStyleSet) {
        _currentStyle.value = style
        prefs.edit().putString(KEY_STYLE, style.name).apply()
    }

    fun cycleNext() {
        selectStyle(_currentStyle.value.next())
    }

    fun cyclePrevious() {
        selectStyle(_currentStyle.value.previous())
    }

    // ── CUSTOM COLOR SCHEME METHODS ──

    fun getDefaultColorScheme(style: GaugeStyleSet): GaugeColorScheme {
        val dominant = when (style) {
            GaugeStyleSet.ELITE      -> Color(0xFF00E5FF) // Cyan
            GaugeStyleSet.CLASSIC    -> Color(0xFF4CAF50) // Green
            GaugeStyleSet.CYBER      -> Color(0xFF00FFFF) // Electric cyan
            GaugeStyleSet.RACING     -> Color(0xFFFF1744) // Racing red
            GaugeStyleSet.RADIAL     -> Color(0xFF448AFF) // Blue
            GaugeStyleSet.THERMO     -> Color(0xFFFF6D00) // Orange
            GaugeStyleSet.HOLOGRAM   -> Color(0xFF00E5FF) // Holo cyan
            GaugeStyleSet.NEON_RETRO -> Color(0xFFFF00FF) // Magenta
            GaugeStyleSet.LAMBO      -> Color(0xFFFF9100) // Lambo orange
            GaugeStyleSet.PLASMA     -> Color(0xFF7C4DFF) // Violet
            GaugeStyleSet.AURORA     -> Color(0xFF1DE9B6) // Teal
            GaugeStyleSet.FERRARI    -> Color(0xFFD50000) // Rosso Corsa
            GaugeStyleSet.TOKYO      -> Color(0xFFFF4081) // Hot pink
            GaugeStyleSet.MILITARY   -> Color(0xFF76FF03) // Phosphor green
            GaugeStyleSet.DIAMOND    -> Color(0xFF80D8FF) // Ice blue
            GaugeStyleSet.COCKPIT    -> Color(0xFFFFAB00) // Amber
            GaugeStyleSet.LIGHTNING  -> Color(0xFFE0E0FF) // bright bluish white
            GaugeStyleSet.RAIN       -> Color(0xFF00B0FF) // sky blue
            GaugeStyleSet.SNOW       -> Color(0xFFE0F7FA) // ice white
            GaugeStyleSet.TORNADO    -> Color(0xFF90A4AE) // storm grey
            GaugeStyleSet.SANDSTORM  -> Color(0xFFFFB74D) // sand orange
            GaugeStyleSet.VOLCANO    -> Color(0xFFFF3D00) // lava red
            GaugeStyleSet.TSUNAMI    -> Color(0xFF00E5FF) // cyan ocean
            GaugeStyleSet.BLIZZARD   -> Color(0xFF80DEEA) // blizzard blue
            GaugeStyleSet.AURORA_AUTO -> Color(0xFF64FFDA) // aurora teal
            GaugeStyleSet.SOLAR_FLARE -> Color(0xFFFFEA00) // solar yellow
            GaugeStyleSet.COSMIC_DUST -> Color(0xFFE040FB) // cosmic violet
            GaugeStyleSet.EARTHQUAKE -> Color(0xFF8D6E63) // tectonic brown
            GaugeStyleSet.METEOR_SHOWER -> Color(0xFFFFB300) // meteor orange
            GaugeStyleSet.HURRICANE  -> Color(0xFF2979FF) // hurricane blue
            GaugeStyleSet.FOGGY_MIST -> Color(0xFFB0BEC5) // mist grey
            GaugeStyleSet.WILD_FIRE  -> Color(0xFFFF5722) // fire orange
            GaugeStyleSet.OCEAN_DEPTH -> Color(0xFF0D47A1) // deep blue
            GaugeStyleSet.ECLIPSE    -> Color(0xFFFFAB00) // corona amber
            GaugeStyleSet.RAINBOW_RAIN -> Color(0xFFFF4081) // pinkish neon
            GaugeStyleSet.SAND_GLOW  -> Color(0xFFFF8F00) // dune gold
            GaugeStyleSet.THUNDER_CLOUD -> Color(0xFF7E57C2) // purple storm
            GaugeStyleSet.ICY_FROST  -> Color(0xFFB2EBF2) // frost blue
            GaugeStyleSet.CYBER_STORM -> Color(0xFF00E676) // matrix green
            GaugeStyleSet.BLACK_HOLE -> Color(0xFF3F51B5) // gravity indigo
            GaugeStyleSet.MONSOON    -> Color(0xFF0288D1) // monsoon blue
            GaugeStyleSet.COMET_TAIL -> Color(0xFF00E5FF) // comet cyan
            GaugeStyleSet.GALAXY_CORE -> Color(0xFFD500F9) // galaxy magenta
            GaugeStyleSet.ACID_RAIN  -> Color(0xFFCCFF00) // acid lime green
            GaugeStyleSet.SUPERNOVA  -> Color(0xFFFF1744) // supernova red
            GaugeStyleSet.WIND_TUNNEL -> Color(0xFF26A69A) // wind teal
            GaugeStyleSet.CUSTOM_DIY -> Color(0xFF00FFCC) // custom diy cyan
        }
        val needle = when (style) {
            GaugeStyleSet.FERRARI -> Color(0xFFD50000)
            GaugeStyleSet.RACING -> Color(0xFFFF1744)
            GaugeStyleSet.VOLCANO -> Color(0xFFFF9100)
            GaugeStyleSet.WILD_FIRE -> Color(0xFFFFD54F)
            else -> dominant
        }
        val special = when (style) {
            GaugeStyleSet.CLASSIC -> Color(0xFFFFD700) // yellow warning
            GaugeStyleSet.LIGHTNING -> Color(0xFFFFFFFF)
            GaugeStyleSet.THUNDER_CLOUD -> Color(0xFF00FFFF)
            GaugeStyleSet.ACID_RAIN -> Color(0xFF00FF00)
            else -> dominant
        }
        return GaugeColorScheme(
            bezelColor = Color(0xFF3E3E3E),
            internalColor = dominant,
            textColor = dominant,
            needleColor = needle,
            specialColor = special,
            labelColor = dominant,
            unitColor = dominant
        )
    }

    fun getColorScheme(style: GaugeStyleSet): GaugeColorScheme {
        val default = getDefaultColorScheme(style)
        val bezel = prefs.getInt("color_${style.name}_bezel", default.bezelColor.toArgb())
        val internal = prefs.getInt("color_${style.name}_internal", default.internalColor.toArgb())
        val text = prefs.getInt("color_${style.name}_text", default.textColor.toArgb())
        val needle = prefs.getInt("color_${style.name}_needle", default.needleColor.toArgb())
        val special = prefs.getInt("color_${style.name}_special", default.specialColor.toArgb())
        val label = prefs.getInt("color_${style.name}_label", default.labelColor.toArgb())
        val unit = prefs.getInt("color_${style.name}_unit", default.unitColor.toArgb())
        return GaugeColorScheme(
            bezelColor = Color(bezel),
            internalColor = Color(internal),
            textColor = Color(text),
            needleColor = Color(needle),
            specialColor = Color(special),
            labelColor = Color(label),
            unitColor = Color(unit)
        )
    }

    /** Shared across ALL instances so every observer recomposes immediately */
    companion object {
        var colorSchemeUpdateTrigger by mutableStateOf(0)
            private set
        var diyUpdateTrigger by mutableStateOf(0)
    }

    fun saveColorScheme(style: GaugeStyleSet, scheme: GaugeColorScheme) {
        prefs.edit()
            .putInt("color_${style.name}_bezel", scheme.bezelColor.toArgb())
            .putInt("color_${style.name}_internal", scheme.internalColor.toArgb())
            .putInt("color_${style.name}_text", scheme.textColor.toArgb())
            .putInt("color_${style.name}_needle", scheme.needleColor.toArgb())
            .putInt("color_${style.name}_special", scheme.specialColor.toArgb())
            .putInt("color_${style.name}_label", scheme.labelColor.toArgb())
            .putInt("color_${style.name}_unit", scheme.unitColor.toArgb())
            .apply()
        colorSchemeUpdateTrigger++
    }

    fun resetColorScheme(style: GaugeStyleSet) {
        prefs.edit()
            .remove("color_${style.name}_bezel")
            .remove("color_${style.name}_internal")
            .remove("color_${style.name}_text")
            .remove("color_${style.name}_needle")
            .remove("color_${style.name}_special")
            .remove("color_${style.name}_label")
            .remove("color_${style.name}_unit")
            .apply()
        colorSchemeUpdateTrigger++
    }

    // ── DIY STYLING CONFIG GETTERS & SETTERS ──

    fun getDiyBgType(): Int = prefs.getInt("diy_bg_type", 0)
    fun saveDiyBgType(value: Int) {
        prefs.edit().putInt("diy_bg_type", value).apply()
        diyUpdateTrigger++
    }

    fun getDiyBgPresetIndex(): Int = prefs.getInt("diy_bg_preset_index", 0)
    fun saveDiyBgPresetIndex(value: Int) {
        prefs.edit().putInt("diy_bg_preset_index", value).apply()
        diyUpdateTrigger++
    }

    fun getDiyBgImageUri(): String = prefs.getString("diy_bg_image_uri", "") ?: ""
    fun saveDiyBgImageUri(value: String) {
        prefs.edit().putString("diy_bg_image_uri", value).apply()
        diyUpdateTrigger++
    }

    fun saveDiyBgImageUri(context: Context, uri: android.net.Uri): Boolean {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val filesDir = context.filesDir
            // Delete previous DIY background images
            filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("diy_bg_image_")) {
                    file.delete()
                }
            }

            // Write to local file with unique name
            val localFile = java.io.File(filesDir, "diy_bg_image_${System.currentTimeMillis()}.png")
            val outputStream = java.io.FileOutputStream(localFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            prefs.edit().putString("diy_bg_image_uri", localFile.absolutePath).apply()
            diyUpdateTrigger++
            return true
        } catch (e: Exception) {
            android.util.Log.e("GaugeStyleManager", "Error saving local background image copy: ${e.message}")
            return false
        }
    }

    fun clearDiyBgImage() {
        val currentUri = getDiyBgImageUri()
        if (currentUri.isNotEmpty() && currentUri.startsWith("/")) {
            try {
                val file = java.io.File(currentUri)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("GaugeStyleManager", "Error deleting local image file: ${e.message}")
            }
        }
        prefs.edit().putString("diy_bg_image_uri", "").apply()
        saveDiyBgType(0) // Reset to Default Gradient
        diyUpdateTrigger++
    }

    fun getDiyBezelStyle(): Int = prefs.getInt("diy_bezel_style", 0)
    fun saveDiyBezelStyle(value: Int) {
        prefs.edit().putInt("diy_bezel_style", value).apply()
        diyUpdateTrigger++
    }

    fun getDiyNeedleStyle(): Int = prefs.getInt("diy_needle_style", 0)
    fun saveDiyNeedleStyle(value: Int) {
        prefs.edit().putInt("diy_needle_style", value).apply()
        diyUpdateTrigger++
    }

    fun getDiyTicksStyle(): Int = prefs.getInt("diy_ticks_style", 0)
    fun saveDiyTicksStyle(value: Int) {
        prefs.edit().putInt("diy_ticks_style", value).apply()
        diyUpdateTrigger++
    }

    // ── DIY ACCENT COLOR ──
    fun getDiyAccentColor(): Int = prefs.getInt("diy_accent_color", android.graphics.Color.parseColor("#00FFCC"))
    fun saveDiyAccentColor(value: Int) {
        prefs.edit().putInt("diy_accent_color", value).apply()
        diyUpdateTrigger++
    }

    fun getDiyAccentColor2(): Int = prefs.getInt("diy_accent_color2", android.graphics.Color.parseColor("#7C4DFF"))
    fun saveDiyAccentColor2(value: Int) {
        prefs.edit().putInt("diy_accent_color2", value).apply()
        diyUpdateTrigger++
    }

    // ── DIY GLOW & OPACITY ──
    fun getDiyGlowIntensity(): Float = prefs.getFloat("diy_glow_intensity", 0.7f)
    fun saveDiyGlowIntensity(value: Float) {
        prefs.edit().putFloat("diy_glow_intensity", value.coerceIn(0f, 1f)).apply()
        diyUpdateTrigger++
    }

    fun getDiyImageOpacity(): Float = prefs.getFloat("diy_image_opacity", 1.0f)
    fun saveDiyImageOpacity(value: Float) {
        prefs.edit().putFloat("diy_image_opacity", value.coerceIn(0f, 1f)).apply()
        diyUpdateTrigger++
    }

    // ── DIY GAUGE NAME ──
    fun getDiyGaugeName(): String = prefs.getString("diy_gauge_name", "") ?: ""
    fun saveDiyGaugeName(value: String) {
        prefs.edit().putString("diy_gauge_name", value).apply()
        diyUpdateTrigger++
    }

    fun getDiyAnimation(): Int = prefs.getInt("diy_animation", 0)
    fun saveDiyAnimation(value: Int) {
        prefs.edit().putInt("diy_animation", value).apply()
        diyUpdateTrigger++
    }

    fun getDiyTypography(): Int = prefs.getInt("diy_typography", 0)
    fun saveDiyTypography(value: Int) {
        prefs.edit().putInt("diy_typography", value).apply()
        diyUpdateTrigger++
    }

    // ── DIY CONFIG EXPORT / IMPORT ──

    /** Serialize all DIY parameters into a shareable GaugeConfig */
    fun exportDiyConfig(): com.elysium369.meet.data.local.entities.GaugeConfig {
        return com.elysium369.meet.data.local.entities.GaugeConfig(
            name = getDiyGaugeName(),
            bgType = getDiyBgType(),
            bgPresetIndex = getDiyBgPresetIndex(),
            bezelStyle = getDiyBezelStyle(),
            needleStyle = getDiyNeedleStyle(),
            ticksStyle = getDiyTicksStyle(),
            accentColor = getDiyAccentColor(),
            accentColor2 = getDiyAccentColor2(),
            glowIntensity = getDiyGlowIntensity(),
            imageOpacity = getDiyImageOpacity(),
            animationIndex = getDiyAnimation(),
            typographyIndex = getDiyTypography()
        )
    }

    /** Apply an external GaugeConfig to the current DIY parameters */
    fun importDiyConfig(config: com.elysium369.meet.data.local.entities.GaugeConfig) {
        prefs.edit()
            .putString("diy_gauge_name", config.name)
            .putInt("diy_bg_type", config.bgType)
            .putInt("diy_bg_preset_index", config.bgPresetIndex)
            .putInt("diy_bezel_style", config.bezelStyle)
            .putInt("diy_needle_style", config.needleStyle)
            .putInt("diy_ticks_style", config.ticksStyle)
            .putInt("diy_accent_color", config.accentColor)
            .putInt("diy_accent_color2", config.accentColor2)
            .putFloat("diy_glow_intensity", config.glowIntensity)
            .putFloat("diy_image_opacity", config.imageOpacity)
            .putInt("diy_animation", config.animationIndex)
            .putInt("diy_typography", config.typographyIndex)
            .apply()
        diyUpdateTrigger++
    }

    /** Export current DIY to a SavedGaugeEntity ready for Room insertion */
    fun exportToSavedEntity(
        id: String = java.util.UUID.randomUUID().toString(),
        name: String? = null
    ): com.elysium369.meet.data.local.entities.SavedGaugeEntity {
        val now = System.currentTimeMillis()
        return com.elysium369.meet.data.local.entities.SavedGaugeEntity(
            id = id,
            name = name ?: getDiyGaugeName().ifEmpty { "Mi Gauge" },
            bgType = getDiyBgType(),
            bgPresetIndex = getDiyBgPresetIndex(),
            bgImageUri = getDiyBgImageUri(),
            bezelStyle = getDiyBezelStyle(),
            needleStyle = getDiyNeedleStyle(),
            ticksStyle = getDiyTicksStyle(),
            accentColor = getDiyAccentColor(),
            accentColor2 = getDiyAccentColor2(),
            glowIntensity = getDiyGlowIntensity(),
            imageOpacity = getDiyImageOpacity(),
            animationIndex = getDiyAnimation(),
            typographyIndex = getDiyTypography(),
            createdAt = now,
            updatedAt = now
        )
    }

    /** Import a saved gauge entity back into the active DIY config */
    fun importFromSavedEntity(entity: com.elysium369.meet.data.local.entities.SavedGaugeEntity) {
        prefs.edit()
            .putString("diy_gauge_name", entity.name)
            .putInt("diy_bg_type", entity.bgType)
            .putInt("diy_bg_preset_index", entity.bgPresetIndex)
            .putString("diy_bg_image_uri", entity.bgImageUri)
            .putInt("diy_bezel_style", entity.bezelStyle)
            .putInt("diy_needle_style", entity.needleStyle)
            .putInt("diy_ticks_style", entity.ticksStyle)
            .putInt("diy_accent_color", entity.accentColor)
            .putInt("diy_accent_color2", entity.accentColor2)
            .putFloat("diy_glow_intensity", entity.glowIntensity)
            .putFloat("diy_image_opacity", entity.imageOpacity)
            .putInt("diy_animation", entity.animationIndex)
            .putInt("diy_typography", entity.typographyIndex)
            .apply()
        diyUpdateTrigger++
    }
}
