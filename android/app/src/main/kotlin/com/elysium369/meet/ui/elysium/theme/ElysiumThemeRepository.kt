package com.elysium369.meet.ui.elysium.theme

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val Context.elysiumThemeStore by preferencesDataStore(
    name = "elysium_visual_theme",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "meet_system_theme_prefs")) },
)

/** Application-owned theme persistence; preview is never written to disk. */
object ElysiumThemeRepository {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("theme_config_v1")
    private val mutableConfig = MutableStateFlow(ElysiumThemeConfig())
    private val mutablePreview = MutableStateFlow<ElysiumThemeConfig?>(null)
    private val mutableFailure = MutableStateFlow<String?>(null)
    val config: StateFlow<ElysiumThemeConfig> = mutableConfig
    val preview: StateFlow<ElysiumThemeConfig?> = mutablePreview
    val failure: StateFlow<String?> = mutableFailure
    private var application: Context? = null

    @Synchronized
    fun initialize(context: Context) {
        if (application != null) return
        val app = context.applicationContext
        application = app
        val old = app.getSharedPreferences("meet_system_theme_prefs", Context.MODE_PRIVATE)
        val names = listOf("neonGreen", "electricBlue", "cyberCyan", "hotMagenta")
        var legacy = ElysiumPaletteOverride()
        names.forEachIndexed { index, name ->
            if (old.contains(name)) legacy = legacy.withChannel(index, old.getInt(name, 0).toLong() and 0xFFFFFFFFL)
        }
        mutableConfig.value = ElysiumThemeConfig(global = legacy)
        io.launch {
            app.elysiumThemeStore.data.catch { error ->
                if (error is IOException) mutableFailure.value = "No se pudo leer el tema guardado" else throw error
            }.collect { preferences -> mutableConfig.value = decode(preferences) }
        }
    }

    private fun decode(preferences: Preferences): ElysiumThemeConfig {
        preferences[key]?.let { encoded ->
            return runCatching { json.decodeFromString<ElysiumThemeConfig>(encoded) }
                .getOrElse { mutableFailure.value = "Configuración de tema inválida"; ElysiumThemeConfig() }
        }
        var migrated = ElysiumPaletteOverride()
        listOf("neonGreen", "electricBlue", "cyberCyan", "hotMagenta").forEachIndexed { index, name ->
            preferences[intPreferencesKey(name)]?.let { value ->
                migrated = migrated.withChannel(index, value.toLong() and 0xFFFFFFFFL)
            }
        }
        return ElysiumThemeConfig(global = migrated)
    }

    fun preview(value: ElysiumThemeConfig?) { mutablePreview.value = value }

    /** Acknowledges success only after DataStore has durably committed the entire draft. */
    suspend fun save(scope: ThemeScope, target: String, draft: ElysiumPaletteOverride): Boolean {
        val app = application ?: return false
        return try {
            val committed = app.elysiumThemeStore.edit { prefs ->
                val next = decode(prefs).withOverride(scope, target, draft)
                prefs[key] = json.encodeToString(ElysiumThemeConfig.serializer(), next)
            }
            mutableConfig.value = decode(committed)
            mutableFailure.value = null
            mutablePreview.value = null
            true
        } catch (_: IOException) {
            mutableFailure.value = "No se pudo guardar el tema. Inténtalo otra vez."
            false
        }
    }
}
