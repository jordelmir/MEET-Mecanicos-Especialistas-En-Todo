package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.TelemetrySample
import com.elysium369.meet.data.local.entities.DtcCauseEntity
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.data.local.entities.DtcRelatedPidEntity
import com.elysium369.meet.data.local.entities.DtcRepairCostEntity
import com.elysium369.meet.data.local.entities.DtcVerifiedFixEntity
import com.elysium369.meet.data.supabase.RepairCase
import com.elysium369.meet.domain.evidence.EvidenceMetric
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

data class RepairStep(
    val title: String,
    val description: String,
    val icon: String,
    val minutes: Int?,
    val difficulty: String?,
    val estimateProvenance: String? = null,
)

private data class DisplayRepairCause(
    val text: String,
    val source: DtcCauseEntity
)

private data class DtcSolutionCard(
    val source: String,
    val title: String,
    val description: String,
    val successMetric: EvidenceMetric<Float>? = null,
    val voteCount: Int = 0,
    val partRequired: String? = null,
    val estimatedCostUsd: Float? = null,
    val difficultyLevel: String = "medio",
    val verifiedFixId: Long? = null
)

private data class LivePidReading(
    val title: String,
    val command: String,
    val displayValue: String,
    val unit: String,
    val status: String,
    val statusColor: Color,
    val hasValue: Boolean,
    val helperText: String,
    val severity: Int,
    val diagnosis: String,
    val action: String,
    val normalRange: String? = null
)

private data class PidJudgement(
    val status: String,
    val color: Color,
    val severity: Int,
    val diagnosis: String,
    val action: String
)

private data class LiveSectionVerdict(
    val title: String,
    val detail: String,
    val color: Color,
    val severity: Int
)

object DtcRepairHelper {
    fun parseStepString(stepStr: String, stepNum: Int): RepairStep {
        val cleanStr = stepStr.replace(Regex("^(?i)(paso\\s*\\d+[:.]*|\\d+[:.]*)\\s*"), "")
        val parts = cleanStr.split(":", limit = 2)
        val title = if (parts.size > 1) parts[0].trim() else "Procedimiento Técnico $stepNum"
        val description = if (parts.size > 1) parts[1].trim() else cleanStr.trim()
        val icon = when {
            cleanStr.contains("volt", ignoreCase = true) || cleanStr.contains("multímetro", ignoreCase = true) || cleanStr.contains("resistencia", ignoreCase = true) -> "⚡"
            cleanStr.contains("limpiar", ignoreCase = true) || cleanStr.contains("limpieza", ignoreCase = true) || cleanStr.contains("aerosol", ignoreCase = true) -> "🧼"
            cleanStr.contains("combustible", ignoreCase = true) || cleanStr.contains("gasolina", ignoreCase = true) || cleanStr.contains("presión", ignoreCase = true) -> "⛽"
            cleanStr.contains("escáner", ignoreCase = true) || cleanStr.contains("escanear", ignoreCase = true) || cleanStr.contains("meet", ignoreCase = true) -> "📱"
            cleanStr.contains("conducir", ignoreCase = true) || cleanStr.contains("ciclo", ignoreCase = true) || cleanStr.contains("ruta", ignoreCase = true) -> "🚗"
            else -> "🔧"
        }

        return RepairStep(
            title = title,
            description = description,
            icon = icon,
            minutes = null,
            difficulty = null,
        )
    }
}

private fun buildDisplayRepairCauses(
    dtcCode: String,
    causes: List<DtcCauseEntity>
): List<DisplayRepairCause> {
    if (causes.isEmpty()) return emptyList()

    val hasSpecificSpanishCauses = causes.count { !isAggregateRepairCause(it.causeEs) } >= 2
    val display = mutableListOf<DisplayRepairCause>()
    val seen = mutableListOf<String>()

    causes.forEach causeLoop@{ cause ->
        val translated = DtcUtils.getSpanishPossibleCauses(dtcCode, cause.causeEs)
        val translatedParts = translated
            .split('|', '\n')
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotBlank() }
        val isAggregate = isAggregateRepairCause(cause.causeEs) || translatedParts.size > 3

        if (hasSpecificSpanishCauses && isAggregate) return@causeLoop

        val candidates = if (isAggregate) {
            translatedParts
        } else {
            listOf(translatedParts.firstOrNull() ?: translated.trim())
        }

        candidates.forEach candidateLoop@{ candidate ->
            val normalized = normalizeRepairCause(candidate)
            if (normalized.isBlank()) return@candidateLoop
            val duplicate = seen.any { previous ->
                previous == normalized ||
                    (previous.length > 12 && normalized.contains(previous)) ||
                    (normalized.length > 12 && previous.contains(normalized))
            }
            if (!duplicate) {
                seen.add(normalized)
                display.add(DisplayRepairCause(candidate, cause))
            }
        }
    }

    return display
}

private fun isAggregateRepairCause(text: String): Boolean {
    val value = text.trim()
    return value.length > 120 ||
        value.count { it == ',' } >= 3 ||
        value.contains("when applicable", ignoreCase = true) ||
        value.contains("i.e.", ignoreCase = true)
}

private fun normalizeRepairCause(text: String): String {
    return text
        .trim()
        .lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")
        .replace("ñ", "n")
        .replace(Regex("[^a-z0-9]+"), "")
}

private fun buildLivePidReadings(
    relatedPids: List<DtcRelatedPidEntity>,
    definition: DtcDefinitionEntity?,
    telemetrySamples: Map<String, TelemetrySample>,
    isConnected: Boolean,
    dtcCode: String
): List<LivePidReading> {
    val pidSpecs = if (relatedPids.isNotEmpty()) {
        relatedPids.map { pid ->
            LivePidSpec(
                title = pid.pidNameEs,
                command = pid.pidCommand,
                unit = pid.unit.orEmpty(),
                normalRange = pid.normalRange
            )
        }
    } else {
        buildFallbackLivePidSpecs(definition, dtcCode)
    }

    return pidSpecs
        .distinctBy { normalizePidCommand(it.command) + it.title.lowercase() }
        .map { spec ->
            val parts = spec.command.split("/", "|", ",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val values = parts.mapNotNull { command ->
                telemetrySamples.resolveVerifiedPidValue(command)?.let { command to it }
            }
            val firstCore = normalizePidCommand(parts.firstOrNull() ?: spec.command)
            val unit = spec.unit.ifBlank { inferPidUnit(firstCore, spec.title) }
            val hasValue = values.isNotEmpty()
            val displayValue = if (hasValue) {
                values.joinToString(" / ") { (_, value) -> formatPidValue(value, unit) }
            } else {
                "--"
            }
            val judgement = if (hasValue) {
                evaluatePidJudgement(firstCore, values.first().second, values.map { it.second }, spec.normalRange, telemetrySamples)
            } else if (isConnected) {
                PidJudgement(
                    status = "ESPERANDO PID",
                    color = MeetColors.textSecondary,
                    severity = 0,
                    diagnosis = "El scanner esta conectado, pero este parametro aun no llega o el ECU no lo soporta.",
                    action = "Mantén el motor encendido unos segundos; si nunca aparece, este vehículo no expone ese PID genérico."
                )
            } else {
                PidJudgement(
                    status = "SIN ENLACE",
                    color = MeetColors.textSecondary,
                    severity = 0,
                    diagnosis = "No hay enlace OBD activo, por eso no se puede juzgar este valor.",
                    action = "Conecta el scanner, switch en ON y motor encendido si el procedimiento lo requiere."
                )
            }
            LivePidReading(
                title = spec.title,
                command = spec.command,
                displayValue = displayValue,
                unit = unit,
                status = judgement.status,
                statusColor = judgement.color,
                hasValue = hasValue,
                helperText = buildPidHelperText(firstCore, judgement.status, hasValue, isConnected),
                severity = judgement.severity,
                diagnosis = judgement.diagnosis,
                action = judgement.action,
                normalRange = spec.normalRange ?: researchBasedRangeText(firstCore)
            )
        }
}

private data class LivePidSpec(
    val title: String,
    val command: String,
    val unit: String = "",
    val normalRange: String? = null
)

private fun buildFallbackLivePidSpecs(
    definition: DtcDefinitionEntity?,
    dtcCode: String
): List<LivePidSpec> {
    val raw = definition?.freezeFramePIDs.orEmpty()
    val parsed = raw.split("|", ",", "\n", ";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { token ->
            val command = Regex("(\\$?01\\s*)?[0-9A-Fa-f]{2}").find(token)?.value ?: token
            LivePidSpec(title = inferPidTitle(normalizePidCommand(command)), command = command)
        }
    if (parsed.isNotEmpty()) return parsed

    val code = dtcCode.uppercase()
    return when {
        code.startsWith("P030") -> listOf(
            LivePidSpec("RPM del motor", "$01 0C"),
            LivePidSpec("Carga calculada del motor", "$01 04"),
            LivePidSpec("STFT/LTFT Banco 1", "$01 06/$01 07"),
            LivePidSpec("Avance de encendido", "$01 0E"),
            LivePidSpec("Temperatura de refrigerante", "$01 05")
        )
        code == "P0171" || code == "P0174" || code == "P0172" || code == "P0175" -> listOf(
            LivePidSpec("STFT/LTFT Banco 1", "$01 06/$01 07"),
            LivePidSpec("STFT/LTFT Banco 2", "$01 08/$01 09"),
            LivePidSpec("MAF", "$01 10"),
            LivePidSpec("MAP", "$01 0B"),
            LivePidSpec("RPM del motor", "$01 0C")
        )
        code == "P0420" || code == "P0430" -> listOf(
            LivePidSpec("O2 Banco 1 Sensor 1", "$01 14"),
            LivePidSpec("O2 Banco 1 Sensor 2", "$01 15"),
            LivePidSpec("Temperatura catalizador", "$01 3C"),
            LivePidSpec("RPM del motor", "$01 0C"),
            LivePidSpec("Carga calculada del motor", "$01 04")
        )
        else -> listOf(
            LivePidSpec("RPM del motor", "$01 0C"),
            LivePidSpec("Velocidad del vehículo", "$01 0D"),
            LivePidSpec("Temperatura de refrigerante", "$01 05"),
            LivePidSpec("Carga calculada del motor", "$01 04"),
            LivePidSpec("Voltaje módulo OBD", "$01 42")
        )
    }
}

private fun Map<String, TelemetrySample>.resolveVerifiedPidValue(command: String): Float? {
    val compact = normalizePidCommand(command)
    val core = compact.removePrefix("01")
    val aliases = buildList {
        add(command)
        add(compact)
        add(core)
        if (core.length == 2) add("01$core")
        when (core) {
            "03" -> add("FUEL_STATUS")
            "04" -> addAll(listOf("ENGINE_LOAD", "LOAD", "load"))
            "05" -> addAll(listOf("COOLANT", "coolant", "ECT"))
            "06" -> addAll(listOf("STFT_B1", "stft_b1"))
            "07" -> addAll(listOf("LTFT_B1", "ltft_b1"))
            "08" -> addAll(listOf("STFT_B2", "stft_b2"))
            "09" -> addAll(listOf("LTFT_B2", "ltft_b2"))
            "0A" -> add("FUEL_PRESSURE")
            "0B" -> addAll(listOf("MAP", "map"))
            "0C" -> addAll(listOf("RPM", "rpm"))
            "0D" -> addAll(listOf("SPEED", "speed", "VELOCIDAD"))
            "0E" -> add("TIMING_ADVANCE")
            "0F" -> add("IAT")
            "10" -> addAll(listOf("MAF", "maf"))
            "11" -> addAll(listOf("THROTTLE", "throttle"))
            "14" -> addAll(listOf("O2_B1S1", "o2_b1s1"))
            "15" -> addAll(listOf("O2_B1S2", "o2_b1s2"))
            "16" -> addAll(listOf("O2_B2S1", "o2_b2s1"))
            "17" -> addAll(listOf("O2_B2S2", "o2_b2s2"))
            "1F" -> add("RUN_TIME")
            "2C" -> add("EGR_COMMANDED")
            "2F" -> addAll(listOf("FUEL_LEVEL", "fuel_level"))
            "33" -> addAll(listOf("BARO", "baro"))
            "3C" -> add("CAT_TEMP_B1S1")
            "42" -> addAll(listOf("VOLTAGE", "voltage", "CTRL_VOLTAGE", "AT RV", "ATRV", "ELM_VOLTAGE"))
            "5C" -> add("OIL_TEMP")
            "5E" -> add("TRANS_TEMP")
        }
    }
    val sample = aliases.firstNotNullOfOrNull { this[it] }
        ?: values.firstOrNull { candidate ->
            aliases.any { alias ->
                candidate.pid.equals(alias, ignoreCase = true) ||
                    candidate.name.equals(alias, ignoreCase = true)
            }
        }
    return sample?.takeIf(TelemetrySample::hasRealValue)?.value?.toFloat()
}

private fun normalizePidCommand(command: String): String {
    return command.uppercase()
        .replace("$", "")
        .replace("MODE", "")
        .replace("PID", "")
        .replace(Regex("[^0-9A-F]"), "")
        .let { compact ->
            when {
                compact.length == 2 -> "01$compact"
                compact.startsWith("01") && compact.length >= 4 -> compact.take(4)
                compact.length >= 4 -> compact.takeLast(4)
                else -> compact
            }
        }
}

private fun inferPidTitle(normalized: String): String {
    return when (normalized.removePrefix("01")) {
        "03" -> "Estado del sistema de combustible"
        "04" -> "Carga calculada del motor"
        "05" -> "Temperatura de refrigerante"
        "06" -> "STFT Banco 1"
        "07" -> "LTFT Banco 1"
        "08" -> "STFT Banco 2"
        "09" -> "LTFT Banco 2"
        "0A" -> "Presión de combustible"
        "0B" -> "Presión MAP"
        "0C" -> "RPM del motor"
        "0D" -> "Velocidad del vehículo"
        "0E" -> "Avance de encendido"
        "0F" -> "Temperatura de admisión"
        "10" -> "Flujo MAF"
        "11" -> "Posición del acelerador"
        "14" -> "O2 Banco 1 Sensor 1"
        "15" -> "O2 Banco 1 Sensor 2"
        "2C" -> "EGR comandado"
        "42" -> "Voltaje módulo OBD"
        else -> "PID $normalized"
    }
}

private fun inferPidUnit(normalized: String, title: String): String {
    val core = normalized.removePrefix("01")
    val lower = title.lowercase()
    return when {
        core == "0C" || lower.contains("rpm") -> "rpm"
        core == "0D" || lower.contains("velocidad") -> "km/h"
        core in listOf("04", "06", "07", "08", "09", "11", "2C", "2F") || lower.contains("%") -> "%"
        core in listOf("05", "0F", "3C", "5C", "5E") || lower.contains("temperatura") -> "°C"
        core in listOf("0A", "0B", "33") || lower.contains("presión") -> "kPa"
        core == "10" || lower.contains("maf") -> "g/s"
        core == "42" || lower.contains("volt") -> "V"
        core.startsWith("1") -> "V"
        else -> ""
    }
}

private fun formatPidValue(value: Float, unit: String): String {
    return when (unit) {
        "rpm", "km/h", "°C", "kPa" -> value.toInt().toString()
        "%" -> String.format("%.1f", value)
        "V", "g/s" -> String.format("%.2f", value)
        else -> String.format("%.2f", value)
    }
}

private fun researchBasedRangeText(normalized: String): String? {
    return when (normalized.removePrefix("01")) {
        "03" -> "Guía: closed loop esperado con motor caliente; open loop puede ser normal en frío"
        "04" -> "Guía: 15-45% en ralentí suele ser sano; alto detenido exige revisar carga/aire"
        "05" -> "Guía: 88-107°C caliente; 108-115°C vigilar; 116°C+ alto"
        "06", "07", "08", "09" -> "Guía: ideal ±5%; sano ±10%; ±20%+ apunta a mezcla fuera de control"
        "0A" -> "Guía: varía por sistema; si carga sube y presión cae, validar con manómetro"
        "0B" -> "Guía NA: 18-45 kPa en ralentí; KOEO debe acercarse al BARO"
        "0C" -> "Guía: >400 rpm confirma motor encendido; 0 rpm limita diagnóstico dinámico"
        "0D" -> "Guía: usar para reproducir falla por condición de ruta/carga"
        "0E" -> "Guía: avance debe moverse con RPM/carga; valores extremos requieren cruce con knock"
        "0F" -> "Guía KOEO frío: IAT cerca de ambiente y ECT; corriendo sube por calor de motor"
        "10" -> "Guía gasolina: 2-7 g/s en ralentí; 15-25 g/s cerca de 2500 rpm, depende de motor"
        "11" -> "Guía: 0-20% suele ser normal en ralentí; debe subir suave sin saltos"
        "14", "15", "16", "17" -> "Guía narrowband: 0.1-0.9 V oscilando caliente en closed loop"
        "1F" -> "Guía: confirma si la prueba fue en frío o con motor estabilizado"
        "2C" -> "Guía: EGR comandado debe coincidir con cambio MAP/MAF si aplica"
        "2F" -> "Guía: <5% combustible puede sesgar pruebas de presión/mezcla"
        "33" -> "Guía: 70-110 kPa según altitud; debe ser referencia para MAP"
        "3C" -> "Guía: catalizador alto sostenido sugiere misfire/mezcla rica/escape restringido"
        "42" -> "Guía: 14.0-14.5 V típico cargando; smart alternator puede variar, >16.5 V crítico"
        "5C" -> "Guía: aceite normalmente caliente pero estable; 125°C+ exige bajar carga"
        "5E" -> "Guía: transmisión 115°C+ requiere revisar fluido/enfriador/carga"
        else -> null
    }
}

private fun evaluatePidJudgement(
    normalized: String,
    value: Float,
    values: List<Float>,
    normalRange: String?,
    telemetrySamples: Map<String, TelemetrySample>
): PidJudgement {
    parseRange(normalRange)?.let { (min, max) ->
        return if (value in min..max) {
            goodJudgement("BUENO", "Dentro del rango definido para este DTC.", "Continua validando bajo las condiciones del freeze frame.")
        } else {
            badJudgement("FUERA DE RANGO", "El valor real esta fuera del rango esperado ($min-$max).", "Prioriza este PID y compara contra especificacion OEM si este vehiculo usa calibracion especial.")
        }
    }
    val core = normalized.removePrefix("01")
    val rpm = telemetrySamples.resolveVerifiedPidValue("$01 0C")
    val load = telemetrySamples.resolveVerifiedPidValue("$01 04")
    val ect = telemetrySamples.resolveVerifiedPidValue("$01 05")
    val speed = telemetrySamples.resolveVerifiedPidValue("$01 0D")
    val runTime = telemetrySamples.resolveVerifiedPidValue("$01 1F")
    val baro = telemetrySamples.resolveVerifiedPidValue("$01 33")
    val isEngineOff = rpm?.let { it <= 100f } == true
    val isIdle = rpm?.let { it in 600f..1100f } == true && speed?.let { it < 8f } == true

    fun trimJudgement(v: Float, label: String): PidJudgement {
        val absTrim = kotlin.math.abs(v)
        return when {
            absTrim <= 5f -> goodJudgement("IDEAL", "$label esta practicamente centrado; mezcla en zona ideal.", "Usa esta condicion como referencia sana y compara cuando aparezca la falla.")
            absTrim <= 10f -> goodJudgement("BUENO", "$label esta dentro de correccion normal (${String.format("%.1f", v)}%).", "Sigue observando en ralenti, 1500 y 2500 RPM antes de condenar piezas.")
            absTrim <= 15f -> watchJudgement("VIGILAR", "$label esta algo corregido (${String.format("%.1f", v)}%).", "Positivo alto apunta a aire falso/combustible bajo; negativo alto a exceso de combustible.")
            absTrim <= 25f -> badJudgement("MALO", "$label esta salido (${String.format("%.1f", v)}%).", "Con mezcla positiva revisa humo/vacio/MAF/presion; con negativa revisa inyector goteando, MAF sesgado, presion alta o EVAP.")
            else -> criticalJudgement("CRITICO", "$label esta extremo (${String.format("%.1f", v)}%); el ECU esta compensando demasiado.", "Evita diagnostico por piezas. Haz prueba de humo, combustible y MAF/O2 antes de seguir conduciendo fuerte.")
        }
    }

    val base = when (core) {
        "03" -> when (value.toInt()) {
            1, 2 -> goodJudgement("BUENO", "Estado de combustible plausible para operacion normal.", "Confirma que el motor caliente entre a closed loop si aplica.")
            else -> watchJudgement("VIGILAR", "Estado de combustible no comun o dependiente del fabricante.", "Cruza con trims, O2/AFR y temperatura antes de concluir.")
        }
        "04" -> when {
            value !in 0f..100f -> badJudgement("MALO", "Carga calculada fuera de escala OBD (${String.format("%.1f", value)}%).", "Valida PID/decoder y revisa comunicacion antes de diagnosticar componentes.")
            isIdle && value in 15f..45f -> goodJudgement("BUENO", "Carga calculada coherente para ralenti caliente.", "Usa MAP/MAF y trims para confirmar que la carga responde al acelerar.")
            isIdle && value > 65f -> badJudgement("MALO", "Carga muy alta casi detenido; puede indicar esfuerzo, aire/combustible incorrecto o dato sesgado.", "Revisa MAP/MAF, admision obstruida, cuerpo de aceleracion y frenos/compresor si aplica.")
            value < 5f && rpm?.let { it > 600f } == true -> watchJudgement("VIGILAR", "Carga muy baja con motor encendido; puede ser ralenti estable o lectura sesgada.", "Acelera suave: la carga debe subir. Si no cambia, revisa MAF/MAP/TPS.")
            value > 85f && speed?.let { it < 5f } == true -> watchJudgement("VIGILAR", "Carga alta detenido; puede ser A/C, direccion, alternador cargando o dato sesgado.", "Cruza con RPM, MAP/MAF y voltaje antes de condenar.")
            else -> goodJudgement("BUENO", "Carga calculada coherente para una lectura general.", "Valida bajo la condicion exacta en que aparece el DTC.")
        }
        "05" -> when {
            value <= -30f -> badJudgement("MALO", "ECT marca extremo frio; puede ser circuito abierto o sensor desconectado.", "Compara con IAT en KOEO y revisa conector, masa, 5V y resistencia del sensor.")
            value >= 125f -> criticalJudgement("CRITICO", "Temperatura de refrigerante peligrosa (${value.toInt()}°C).", "Apaga o baja carga si es real; revisa nivel, abanicos, termostato, tapa, bomba y purga.")
            value >= 116f -> badJudgement("MALO", "Temperatura alta (${value.toInt()}°C).", "Verifica abanicos, radiador, termostato, aire en sistema y fugas antes de seguir probando.")
            value > 108f -> watchJudgement("VIGILAR", "Temperatura por encima de zona caliente típica.", "Observa si abanicos bajan la temperatura; confirma con termometro si sospechas sensor.")
            rpm?.let { it > 700f } == true && runTime?.let { it > 600f } == true && value < 75f -> badJudgement("MALO", "Motor sigue frio tras varios minutos; posible termostato abierto o ECT sesgado.", "Revisa termostato, nivel/purga y compara ECT contra temperatura real.")
            value < 85f && rpm?.let { it > 700f } == true -> watchJudgement("VIGILAR", "Motor aun frio para diagnostico fino de mezcla.", "Espera temperatura de operacion antes de juzgar trims, O2 y catalizador.")
            else -> goodJudgement("BUENO", "Temperatura en zona de trabajo normal.", "El motor esta listo para validar closed loop y trims.")
        }
        "06" -> trimJudgement(value, "STFT Banco 1")
        "07" -> trimJudgement(value, "LTFT Banco 1")
        "08" -> trimJudgement(value, "STFT Banco 2")
        "09" -> trimJudgement(value, "LTFT Banco 2")
        "0A" -> when {
            value <= 0f -> watchJudgement("VIGILAR", "Presion de combustible no reportada o cero.", "Muchos carros no exponen este PID; usa manometro si el DTC apunta a combustible.")
            value < 250f && load?.let { it > 40f } == true -> badJudgement("MALO", "Presion de combustible baja para carga moderada/alta.", "Revisa bomba, filtro, regulador, voltaje de bomba y lineas.")
            else -> goodJudgement("BUENO", "Presion reportada sin alerta generica.", "Confirma contra especificacion OEM porque este rango varia por sistema.")
        }
        "0B" -> when {
            value < 5f || value > 255f -> badJudgement("MALO", "MAP fuera de escala OBD (${value.toInt()} kPa).", "Revisa manguera MAP, sensor, alimentacion 5V, masa y decoder.")
            isEngineOff && baro != null && kotlin.math.abs(value - baro) <= 8f -> goodJudgement("BUENO", "MAP KOEO coincide con barometrica; sensor base plausible.", "Enciende motor: el MAP debe bajar en ralenti y subir al acelerar.")
            isEngineOff && baro != null -> watchJudgement("VIGILAR", "MAP KOEO no coincide bien con BARO (${value.toInt()} vs ${baro.toInt()} kPa).", "Compara contra presion atmosferica local; revisa MAP/BARO si la diferencia persiste.")
            isIdle && value in 18f..45f -> goodJudgement("BUENO", "MAP en ralenti dentro de vacio generico sano.", "Debe subir rapido al abrir acelerador y acercarse a BARO en WOT.")
            isIdle && value in 46f..60f -> watchJudgement("VIGILAR", "MAP algo alto en ralenti; vacio menor al esperado.", "Cruza con trims: si positivos, busca fuga de vacio; si no, revisa carga, timing o distribucion.")
            isIdle && value > 60f -> badJudgement("MALO", "MAP alto en ralenti; posible bajo vacio real o sensor sesgado.", "Haz prueba de vacio/humo, revisa valvulas/EGR abierta, distribucion y manguera MAP.")
            else -> goodJudgement("BUENO", "MAP coherente para lectura generica.", "Acelera suave: debe responder rapido a cambios de carga.")
        }
        "0C" -> when {
            value > 400f -> goodJudgement("MOTOR ON", "Motor encendido; la lectura es valida para diagnostico dinamico.", "Reproduce la condicion del DTC y observa cambios.")
            value in 1f..400f -> watchJudgement("VIGILAR", "RPM muy baja; puede estar arrancando, apagandose o lectura incompleta.", "Confirma motor encendido estable antes de juzgar otros PIDs.")
            else -> watchJudgement("MOTOR OFF", "RPM en cero; no se pueden evaluar PIDs que requieren motor en marcha.", "Enciende el carro para validar mezcla, MAF, MAP, O2, EGR y temperatura.")
        }
        "0D" -> goodJudgement("BUENO", "Velocidad reportada por ECU: ${value.toInt()} km/h.", "Usala para reproducir fallas que aparecen en ruta o bajo carga.")
        "0E" -> when {
            value < -15f || value > 45f -> watchJudgement("VIGILAR", "Avance de encendido inusual (${String.format("%.1f", value)}°).", "Cruza con knock, temperatura, carga, combustible y misfire.")
            else -> goodJudgement("BUENO", "Avance de encendido dentro de ventana generica.", "Observa si cae fuerte cuando aparece el fallo.")
        }
        "0F" -> when {
            value < -30f || value > 90f -> badJudgement("MALO", "Temperatura de admision poco creible (${value.toInt()}°C).", "Revisa sensor IAT/MAF, cableado o calor excesivo en admision.")
            isEngineOff && ect != null && kotlin.math.abs(value - ect) > 6f -> watchJudgement("VIGILAR", "KOEO frio: IAT y ECT deberian estar cercanos; diferencia alta detectada.", "Deja el carro frio y compara ambos sensores antes de culpar mezcla.")
            else -> goodJudgement("BUENO", "IAT coherente para diagnostico general.", "Comparala con ambiente en arranque frio.")
        }
        "10" -> when {
            isEngineOff && value > 0.5f -> badJudgement("MALO", "MAF reporta flujo con motor apagado.", "Revisa MAF sesgado, tierra, alimentacion, retorno de senal o decoder.")
            value <= 0f && rpm?.let { it > 700f } == true -> badJudgement("MALO", "MAF en cero con motor encendido.", "Revisa MAF desconectado, alimentacion, masa, senal o admision bloqueada.")
            isIdle && value in 2f..7f -> goodJudgement("BUENO", "MAF en ralenti dentro de guia generica.", "Grafica contra RPM/TPS: debe subir de forma progresiva y sin saltos.")
            isIdle && value in 1f..12f -> watchJudgement("VIGILAR", "MAF en ralenti fuera de zona ideal pero posible por cilindrada/carga.", "Compara con trims; positivo sugiere MAF bajo o aire no medido, negativo sugiere MAF alto.")
            isIdle -> badJudgement("MALO", "MAF en ralenti muy fuera de guia generica.", "Revisa filtro/admisión, fugas despues del MAF, suciedad del sensor y cableado.")
            rpm?.let { it in 2200f..2800f } == true && speed?.let { it < 10f } == true && value in 15f..25f -> goodJudgement("BUENO", "MAF cerca de 2500 RPM dentro de guia generica.", "Confirma que el ascenso sea lineal entre 1000 y 2500 RPM.")
            rpm?.let { it in 2200f..2800f } == true && speed?.let { it < 10f } == true && (value < 8f || value > 40f) -> watchJudgement("VIGILAR", "MAF cerca de 2500 RPM no cuadra con guia comun.", "Valida cilindrada y especificacion OEM; si trims acompanan, prueba MAF y admision.")
            value > 80f && load?.let { it < 35f } == true -> watchJudgement("VIGILAR", "MAF alto para baja carga.", "Revisa sensor sesgado, admision alterada o escala no compatible.")
            else -> goodJudgement("BUENO", "MAF responde con valor real plausible.", "Debe subir al acelerar; si queda plano, revisa sensor/cableado.")
        }
        "11" -> when {
            value < 0f || value > 100f -> badJudgement("MALO", "Acelerador fuera de escala (${String.format("%.1f", value)}%).", "Revisa TPS/APP, cuerpo de aceleracion y calibracion.")
            isIdle && value <= 20f -> goodJudgement("BUENO", "Acelerador coherente para ralenti.", "Debe subir suave y proporcional al pedal.")
            isIdle && value > 25f -> watchJudgement("VIGILAR", "Acelerador alto en ralenti.", "Puede haber cuerpo sucio, aprendizaje pendiente o entrada de aire.")
            else -> goodJudgement("BUENO", "Posicion de acelerador coherente.", "Debe moverse suave sin saltos.")
        }
        "14", "15", "16", "17" -> when {
            value < 0f || value > 1.275f -> badJudgement("MALO", "Voltaje O2 fuera de escala narrowband (${String.format("%.2f", value)} V).", "Confirma PID correcto; si es wideband usa el PID lambda/AFR correspondiente.")
            ect != null && ect > 70f && value <= 0.08f -> watchJudgement("VIGILAR", "O2 muy bajo con motor caliente.", "Puede indicar mezcla pobre, fuga de escape antes del sensor o sensor agotado.")
            ect != null && ect > 70f && value >= 0.9f -> watchJudgement("VIGILAR", "O2 muy alto con motor caliente.", "Puede indicar mezcla rica, inyector goteando, EVAP purgando o sensor lento.")
            else -> goodJudgement("BUENO", "O2 dentro de ventana generica de sensor narrowband.", "Lo correcto no es un numero fijo: debe oscilar en closed loop.")
        }
        "1F" -> goodJudgement("BUENO", "Tiempo desde arranque: ${value.toInt()} s.", "Ayuda a saber si la prueba se hizo en frio o caliente.")
        "2C" -> when {
            value < 0f || value > 100f -> badJudgement("MALO", "EGR comandado fuera de escala.", "Revisa PID/calibracion o valvula.")
            value > 5f && rpm?.let { it < 700f } == true -> watchJudgement("VIGILAR", "EGR comandado en condicion cercana a ralenti.", "Si hay ralenti inestable, revisa EGR trabada abierta.")
            else -> goodJudgement("BUENO", "Comando EGR plausible.", "Compara comando vs respuesta MAP/MAF si el DTC es EGR.")
        }
        "2F" -> when {
            value < 5f -> watchJudgement("VIGILAR", "Nivel de combustible bajo.", "Algunos DTCs de mezcla/fuel pressure pueden empeorar con bajo nivel.")
            else -> goodJudgement("BUENO", "Nivel de combustible suficiente para prueba general.", "Mantén nivel adecuado para pruebas de ruta.")
        }
        "33" -> when {
            value !in 70f..110f -> watchJudgement("VIGILAR", "Barometrica fuera de rango generico (${value.toInt()} kPa).", "Considera altitud; si no coincide, revisa sensor BARO/MAP.")
            else -> goodJudgement("BUENO", "Presion barometrica plausible.", "Usala como referencia para MAP/boost.")
        }
        "3C" -> when {
            value > 900f -> criticalJudgement("CRITICO", "Temperatura de catalizador extrema (${value.toInt()}°C).", "No fuerces el vehiculo; revisa misfire/mezcla rica antes de danar catalizador.")
            value > 750f -> badJudgement("MALO", "Catalizador muy caliente.", "Busca misfire, mezcla rica, escape restringido o carga excesiva.")
            else -> goodJudgement("BUENO", "Temperatura de catalizador sin alerta generica.", "Cruza con O2 pre/post catalizador.")
        }
        "42" -> when {
            value in 13.2f..15.0f && rpm?.let { it > 400f } == true -> goodJudgement("BUENO", "Voltaje de carga sano con motor encendido.", "Sistema electrico estable para diagnostico.")
            value in 12.3f..12.8f && rpm?.let { it <= 400f } == true -> goodJudgement("BUENO", "Voltaje de bateria razonable con motor apagado.", "Enciende motor para confirmar alternador.")
            value < 11.8f -> criticalJudgement("CRITICO", "Voltaje bajo (${String.format("%.1f", value)} V).", "Carga/probar bateria; voltaje bajo genera DTCs falsos y fallas de comunicacion.")
            rpm?.let { it > 400f } == true && value < 12.6f -> badJudgement("MALO", "Motor encendido pero voltaje no sube como carga normal.", "Confirma en bornes con multimetro; revisa alternador, fusible, correa y masas.")
            value > 16.5f -> criticalJudgement("CRITICO", "Voltaje alto (${String.format("%.1f", value)} V).", "Revisa regulador/alternador antes de danar modulos.")
            value > 15.0f -> watchJudgement("VIGILAR", "Voltaje alto para carga tipica, aunque algunos alternadores inteligentes varian.", "Confirma con multimetro y especificacion OEM antes de condenar.")
            else -> watchJudgement("VIGILAR", "Voltaje fuera de zona ideal para esta condicion.", "Confirma con multimetro en bateria y revisa masas/carga.")
        }
        "5C" -> when {
            value > 125f -> badJudgement("MALO", "Aceite muy caliente (${value.toInt()}°C).", "Reduce carga y revisa refrigeracion/lubricacion.")
            else -> goodJudgement("BUENO", "Temperatura de aceite sin alerta generica.", "Cruza con temperatura de refrigerante.")
        }
        "5E" -> when {
            value > 115f -> badJudgement("MALO", "Transmision caliente (${value.toInt()}°C).", "Revisa nivel, tipo de fluido, enfriador y patinaje.")
            else -> goodJudgement("BUENO", "Temperatura de transmision sin alerta generica.", "Valida bajo carga si el DTC es de transmision.")
        }
        else -> goodJudgement("LECTURA REAL", "Valor recibido del ECU.", "Compara contra especificacion OEM si este PID es fabricante-especifico.")
    }

    return if (values.size >= 2 && core in listOf("06", "08")) {
        val totalTrim = values.take(2).sum()
        when {
            kotlin.math.abs(totalTrim) <= 10f -> goodJudgement("BUENO", "Correccion total STFT+LTFT dentro de zona sana (${String.format("%.1f", totalTrim)}%).", "Mezcla global luce estable en esta condicion.")
            kotlin.math.abs(totalTrim) <= 20f -> watchJudgement("VIGILAR", "Correccion total STFT+LTFT elevada (${String.format("%.1f", totalTrim)}%).", "Compara ralenti vs 2500 RPM para separar fuga de vacio de combustible/MAF.")
            else -> badJudgement("MALO", "Correccion total STFT+LTFT salida (${String.format("%.1f", totalTrim)}%).", "La mezcla esta fuera de control normal; diagnostica aire/combustible antes de cambiar sensores.")
        }
    } else {
        base
    }
}

private fun goodJudgement(status: String, diagnosis: String, action: String) =
    PidJudgement(status, MeetColors.neonGreen, 0, diagnosis, action)

private fun watchJudgement(status: String, diagnosis: String, action: String) =
    PidJudgement(status, MeetColors.warning, 1, diagnosis, action)

private fun badJudgement(status: String, diagnosis: String, action: String) =
    PidJudgement(status, Color(0xFFFF6D00), 2, diagnosis, action)

private fun criticalJudgement(status: String, diagnosis: String, action: String) =
    PidJudgement(status, MeetColors.error, 3, diagnosis, action)

private fun parseRange(range: String?): Pair<Float, Float>? {
    if (range.isNullOrBlank()) return null
    val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(range).map { it.value.toFloat() }.toList()
    if (nums.size < 2) return null
    return nums[0].coerceAtMost(nums[1]) to nums[0].coerceAtLeast(nums[1])
}

private fun buildPidHelperText(coreCommand: String, status: String, hasValue: Boolean, isConnected: Boolean): String {
    if (!hasValue) {
        return if (isConnected) {
            "El scanner esta conectado, pero este PID todavia no llego o el ECU no lo soporta."
        } else {
            "Conecta el scanner y enciende el vehiculo para ver esta lectura real."
        }
    }
    return when (coreCommand.removePrefix("01")) {
        "0C" -> if (status == "MOTOR ON") "Motor encendido: lectura util para reproducir la falla." else "RPM baja/cero: valida con motor encendido si el DTC aparece en marcha."
        "06", "07", "08", "09" -> "Fuel trim cerca de 0 es sano; positivo alto indica mezcla pobre, negativo alto indica mezcla rica."
        "05" -> "Temperatura real ayuda a confirmar si la falla ocurre en frio, caliente o en ciclo cerrado."
        "10" -> "MAF debe subir con RPM/carga; lectura plana o ilogica apunta a sensor, aire falso o cableado."
        "0B" -> "MAP debe seguir vacio/carga; lectura fija sugiere manguera, sensor o alimentacion."
        "42" -> "Voltaje real separa falla electrica de bateria/alternador bajo."
        else -> "Lectura real tomada del flujo OBD activo para este DTC."
    }
}

private fun buildLiveSectionVerdict(
    readings: List<LivePidReading>,
    isConnected: Boolean,
    telemetrySamples: Map<String, TelemetrySample>
): LiveSectionVerdict {
    if (!isConnected) {
        return LiveSectionVerdict(
            title = "SIN ENLACE REAL",
            detail = "Conecta el scanner para evaluar si los valores estan buenos o salidos.",
            color = MeetColors.textSecondary,
            severity = 0
        )
    }
    if (telemetrySamples.values.none(TelemetrySample::hasRealValue)) {
        return LiveSectionVerdict(
            title = "ESPERANDO DATOS",
            detail = "El enlace existe, pero aun no hay PIDs suficientes para dictamen.",
            color = MeetColors.warning,
            severity = 1
        )
    }
    val measured = readings.filter { it.hasValue }
    if (measured.isEmpty()) {
        return LiveSectionVerdict(
            title = "SIN PIDS DEL DTC",
            detail = "Hay datos OBD, pero ninguno coincide todavia con los parametros clave de este DTC.",
            color = MeetColors.warning,
            severity = 1
        )
    }
    val worst = measured.maxByOrNull { it.severity } ?: measured.first()
    val badCount = measured.count { it.severity >= 2 }
    val watchCount = measured.count { it.severity == 1 }
    return when {
        worst.severity >= 3 -> LiveSectionVerdict(
            title = "CRITICO",
            detail = "${worst.title}: ${worst.diagnosis}",
            color = MeetColors.error,
            severity = 3
        )
        badCount > 0 -> LiveSectionVerdict(
            title = "VALOR SALIDO",
            detail = "$badCount parametro(s) fuera de zona buena. Prioridad: ${worst.title}.",
            color = Color(0xFFFF6D00),
            severity = 2
        )
        watchCount > 0 -> LiveSectionVerdict(
            title = "VIGILAR",
            detail = "$watchCount parametro(s) requieren observar tendencia antes de condenar piezas.",
            color = MeetColors.warning,
            severity = 1
        )
        else -> LiveSectionVerdict(
            title = "PARAMETROS BUENOS",
            detail = "Las lecturas clave recibidas estan dentro de zona sana generica para esta condicion.",
            color = MeetColors.neonGreen,
            severity = 0
        )
    }
}

private fun buildDtcSolutionCards(
    dtcCode: String,
    definition: DtcDefinitionEntity?,
    displayCauses: List<DisplayRepairCause>,
    steps: List<RepairStep>,
    repairCosts: List<DtcRepairCostEntity>,
    verifiedFixes: List<DtcVerifiedFixEntity>,
    communityCases: List<RepairCase>
): List<DtcSolutionCard> {
    val cards = mutableListOf<DtcSolutionCard>()

    verifiedFixes.forEach { fix ->
        cards.add(
            DtcSolutionCard(
                source = fix.source?.uppercase() ?: "BD VERIFICADA",
                title = "Solución confirmada",
                description = fix.fixDescriptionEs,
                successMetric = fix.voteCount.takeIf { it > 0 }?.let { count ->
                    EvidenceMetric(
                        value = fix.successRate.coerceIn(0f, 1f),
                        source = fix.source ?: "BASE_CONOCIMIENTO_VERIFICADA",
                        sampleCount = count,
                        confidenceMethod = "RESULTADOS_REGISTRADOS_NO_CALIBRADOS",
                        generatedAt = System.currentTimeMillis(),
                    )
                },
                voteCount = fix.voteCount,
                partRequired = fix.partRequired,
                estimatedCostUsd = fix.estimatedCostUsd,
                difficultyLevel = fix.difficultyLevel,
                verifiedFixId = fix.id
            )
        )
    }

    communityCases
        .filter { it.solution.isNotBlank() }
        .forEach { repairCase ->
            val vehicle = listOf(repairCase.vehicle_make, repairCase.vehicle_model, repairCase.year.toString(), repairCase.engine)
                .filter { it.isNotBlank() && it != "0" }
                .joinToString(" ")
            cards.add(
                DtcSolutionCard(
                    source = if (repairCase.verified) "COMUNIDAD VERIFICADA" else "COMUNIDAD",
                    title = if (vehicle.isBlank()) "Caso real aportado por la comunidad" else "Caso real: $vehicle",
                    description = buildString {
                        if (repairCase.symptoms.isNotBlank()) appendLine("Síntomas reportados: ${repairCase.symptoms.trim()}")
                        appendLine("Solución aplicada: ${repairCase.solution.trim()}")
                        if (repairCase.parts_used.isNotBlank()) appendLine("Partes/materiales usados: ${repairCase.parts_used.trim()}")
                        if (repairCase.time_spent > 0) appendLine("Tiempo real informado: ${repairCase.time_spent} min")
                    }.trim(),
                    successMetric = repairCase.votes.takeIf { it > 0 }?.let { count ->
                        EvidenceMetric(
                            value = (repairCase.success_rate / 100.0).toFloat().coerceIn(0f, 1f),
                            source = "COMUNIDAD_AUTOREPORTADA",
                            sampleCount = count,
                            confidenceMethod = "NO_CALIBRADO",
                            generatedAt = System.currentTimeMillis(),
                        )
                    },
                    voteCount = repairCase.votes,
                    partRequired = repairCase.parts_used.takeIf { it.isNotBlank() },
                    estimatedCostUsd = repairCase.cost.toFloat().takeIf { it > 0f },
                    difficultyLevel = if (repairCase.time_spent >= 120 || repairCase.cost >= 350.0) "dificil" else "medio"
                )
            )
        }

    cards.addAll(buildGeneratedDtcSolutions(dtcCode, definition, displayCauses, steps, repairCosts))

    return cards
        .filter { it.description.isNotBlank() }
        .distinctBy { normalizeRepairCause("${it.source}:${it.title}:${it.description.take(180)}") }
        .sortedWith(
            compareByDescending<DtcSolutionCard> { solutionRank(it.source) }
                .thenByDescending { it.successMetric?.value ?: -1f }
                .thenByDescending { it.voteCount }
        )
        .take(12)
}

private fun solutionRank(source: String): Int {
    val value = source.lowercase()
    return when {
        value.contains("tsb") || value.contains("oem") || value.contains("verificada") -> 4
        value.contains("comunidad") -> 3
        value.contains("taller") -> 2
        else -> 1
    }
}

private fun buildGeneratedDtcSolutions(
    dtcCode: String,
    definition: DtcDefinitionEntity?,
    displayCauses: List<DisplayRepairCause>,
    steps: List<RepairStep>,
    repairCosts: List<DtcRepairCostEntity>
): List<DtcSolutionCard> {
    val code = dtcCode.uppercase()
    val family = classifyDtcSolutionFamily(code, definition, displayCauses)
    val cost = repairCosts.firstOrNull()
    val costLine = if (cost != null) {
        "Rango local estimado: $${cost.minCostUsd.toInt()}-$${cost.maxCostUsd.toInt()} USD${cost.partsDescription?.let { " en $it" } ?: ""}."
    } else {
        "Costo variable: confirma precio de repuesto, mano de obra y disponibilidad antes de cambiar piezas."
    }
    val causesLine = displayCauses.take(4).joinToString("; ") { it.text }.ifBlank {
        definition?.possibleCauses?.replace("|", "; ")?.take(220) ?: "usa la descripcion del DTC, freeze frame y sintomas reales para aislar el sistema."
    }
    val procedureLine = steps.take(4).mapIndexed { index, step ->
        "${index + 1}. ${step.title}: ${step.description}"
    }.joinToString("\n")

    val cards = mutableListOf(
        DtcSolutionCard(
            source = "TALLER ELYSIUM",
            title = "Plan maestro para $code",
            description = """
                Confirmar antes de reemplazar: guarda freeze frame, kilometraje, temperatura, RPM, carga del motor y si el DTC es pendiente, confirmado o permanente.
                Causas probables a priorizar: $causesLine
                Procedimiento base:
                ${procedureLine.ifBlank { "1. Inspeccion visual. 2. Prueba electrica o mecanica del componente. 3. Reparacion. 4. Borrar DTC y repetir ciclo de manejo." }}
                $costLine
            """.trimIndent(),
            difficultyLevel = "medio"
        ),
        buildElectricalSolutionCard(code, family),
        buildChemicalsSolutionCard(code, family),
        buildHardwareExtractionSolutionCard(code, family),
        DtcSolutionCard(
            source = "TALLER ELYSIUM",
            title = "Confirmacion final sin simulacion",
            description = """
                No declares el carro reparado solo porque se borro la luz. Borra el DTC, realiza prueba de manejo con las mismas condiciones del freeze frame, revisa datos en vivo y confirma que el monitor OBD complete sin que el codigo vuelva.
                Si regresa de inmediato, trata como falla electrica activa. Si regresa despues de calentar o vibrar, sospecha arnes, conector flojo, fuga termica, soldadura fria o componente que falla bajo carga.
            """.trimIndent(),
            difficultyLevel = "facil"
        )
    )

    cards.add(1, buildFamilySpecificSolutionCard(family))
    return cards
}

private fun classifyDtcSolutionFamily(
    code: String,
    definition: DtcDefinitionEntity?,
    displayCauses: List<DisplayRepairCause>
): String {
    val text = listOf(
        code,
        definition?.descriptionEs,
        definition?.descriptionEn,
        definition?.possibleCauses,
        displayCauses.joinToString(" ") { it.text }
    ).filterNotNull().joinToString(" ").lowercase()

    return when {
        code.startsWith("P030") || text.contains("misfire") || text.contains("fallo de encendido") -> "misfire"
        code in listOf("P0171", "P0174") || text.contains("too lean") || text.contains("mezcla pobre") -> "lean"
        code in listOf("P0172", "P0175") || text.contains("too rich") || text.contains("mezcla rica") -> "rich"
        code.startsWith("P010") || text.contains("maf") || text.contains("map") || text.contains("mass air") -> "air_metering"
        code.startsWith("P013") || code.startsWith("P014") || code.startsWith("P015") || code.startsWith("P016") || text.contains("oxygen sensor") || text.contains("sensor de oxigeno") -> "oxygen"
        code == "P0420" || code == "P0430" || text.contains("catalyst") || text.contains("catalizador") -> "catalyst"
        code.startsWith("P044") || code.startsWith("P045") || text.contains("evap") || text.contains("evaporative") -> "evap"
        code.startsWith("P040") || text.contains("egr") -> "egr"
        code.startsWith("P011") || code == "P0128" || text.contains("coolant") || text.contains("refrigerante") || text.contains("temperatura") -> "cooling"
        code.startsWith("P012") || code.startsWith("P022") || code.startsWith("P21") || text.contains("throttle") || text.contains("acelerador") -> "throttle"
        code.startsWith("P07") || text.contains("transmission") || text.contains("transmision") -> "transmission"
        code.startsWith("U") || text.contains("communication") || text.contains("can bus") -> "network"
        code.startsWith("C") || text.contains("abs") || text.contains("brake") -> "chassis"
        code.startsWith("B") || text.contains("airbag") || text.contains("body") -> "body"
        else -> "electrical"
    }
}

private fun buildFamilySpecificSolutionCard(family: String): DtcSolutionCard {
    val (title, description, part, difficulty, _) = when (family) {
        "misfire" -> arrayOf(
            "Misfire: chispa, combustible y compresion en ese orden",
            """
                1. Intercambia bobina y bujia con otro cilindro; si el fallo se mueve, ya tienes el culpable.
                2. Si no se mueve, escucha inyector, mide resistencia y revisa pulso con noid light u osciloscopio.
                3. Si chispa e inyector estan bien, prueba compresion y leak-down. No uses aditivos para tapar baja compresion, valvulas quemadas o junta de culata.
                4. Si la bujia sale mojada de aceite o refrigerante, repara la causa mecanica antes de montar piezas nuevas.
            """.trimIndent(),
            "Bujia, bobina, inyector o reparacion mecanica segun prueba",
            "medio",
            "HEURISTIC_ONLY"
        )
        "lean" -> arrayOf(
            "Mezcla pobre: aire no medido antes de culpar sensores",
            """
                1. Revisa ducto de admision, PCV, mangueras de vacio y empaque del multiple con prueba de humo.
                2. Limpia MAF solo con limpiador MAF; no uses WD-40 multiuso, carburador ni brake cleaner sobre el filamento.
                3. Compara STFT/LTFT en ralenti y 2500 RPM: si mejora acelerado, busca fuga de vacio; si empeora bajo carga, mide presion/volumen de combustible.
                4. Reemplaza sensor O2/AFR solo si grafica lenta, sesgada o el circuito falla electricamente.
            """.trimIndent(),
            "Manguera de vacio, empaque, MAF, filtro o bomba segun prueba",
            "medio",
            "HEURISTIC_ONLY"
        )
        "rich" -> arrayOf(
            "Mezcla rica: exceso de combustible o lectura de aire falsa",
            """
                1. Revisa filtro de aire, MAF contaminado, sensor ECT que marque frio falso y presion de combustible elevada.
                2. Haz prueba de fuga de inyectores: la presion no debe caer rapido con motor apagado.
                3. Si hay humo negro, olor fuerte a gasolina o aceite diluido, no sigas manejando; puedes danar catalizador.
                4. Los aditivos solo ayudan si hay suciedad leve; no corrigen inyector trabado, regulador roto ni sensor fuera de rango.
            """.trimIndent(),
            "MAF, inyector, regulador, ECT o filtro",
            "medio",
            "HEURISTIC_ONLY"
        )
        "air_metering" -> arrayOf(
            "MAF/MAP/aire: limpiar, medir y comparar carga calculada",
            """
                1. Inspecciona ductos despues del filtro; una grieta pequena altera mezcla y carga calculada.
                2. Limpia MAF con producto especifico y deja secar completo. MAP puede limpiarse con limpiador electronico si no tiene membrana expuesta delicada.
                3. Con KOEO, MAP debe aproximar presion atmosferica; en ralenti debe reflejar vacio estable.
                4. Si hay aceite en admision, corrige PCV/turbo antes de reemplazar sensores repetidamente.
            """.trimIndent(),
            "Limpiador MAF, sensor MAF/MAP, ducto admision",
            "facil",
            "HEURISTIC_ONLY"
        )
        "oxygen" -> arrayOf(
            "O2/AFR: no cambiar sensor sin revisar causa externa",
            """
                1. Revisa fugas de escape antes del sensor, fugas de vacio, mezcla pobre/rica y cableado quemado por el escape.
                2. Sensores calentados requieren alimentacion, masa y resistencia del heater; un fusible puede alimentar varios sensores.
                3. Usa llave/copa de O2 y penetrante en frio. No apliques grasa comun en la punta; solo anti-seize compatible en rosca si el sensor no lo trae.
                4. Si el sensor esta pegado, escala con calor controlado y extractor; evita llama despues de usar solventes.
            """.trimIndent(),
            "Sensor O2/AFR, fusible, arnes o reparacion de escape",
            "medio",
            "HEURISTIC_ONLY"
        )
        "catalyst" -> arrayOf(
            "Catalizador: confirmar eficiencia antes de gastar fuerte",
            """
                1. Repara misfire, mezcla rica/pobre, fuga de escape o consumo de aceite antes de condenar catalizador.
                2. Compara O2 delantero vs trasero: si ambos copian la misma onda con motor caliente, el catalizador puede estar agotado.
                3. Mide contrapresion si hay falta de potencia; un catalizador tapado puede ahogar el motor.
                4. Aditivos de catalizador solo sirven como intento leve por contaminacion superficial; no reparan sustrato derretido, roto o vacio.
            """.trimIndent(),
            "Catalizador, reparacion de mezcla, sensor O2 o fuga de escape",
            "dificil",
            "HEURISTIC_ONLY"
        )
        "evap" -> arrayOf(
            "EVAP: humo, tapa y valvulas antes de bajar tanque",
            """
                1. Verifica tapa de combustible, cuello de llenado y mangueras visibles.
                2. Usa maquina de humo EVAP con baja presion; no metas aire de taller directo porque puedes danar valvulas.
                3. Prueba purge y vent solenoid: deben sellar y abrir al comandarlas.
                4. No uses selladores ni pegamentos en lineas EVAP cerca de gasolina; reemplaza manguera/oring compatible con combustible.
            """.trimIndent(),
            "Tapa gasolina, purge, vent, manguera EVAP, canister",
            "medio",
            "HEURISTIC_ONLY"
        )
        "egr" -> arrayOf(
            "EGR: carbon, comando y pasajes",
            """
                1. Confirma si la EGR es electrica o de vacio y si recibe comando desde ECU.
                2. Limpia carbon con limpiador de cuerpo de aceleracion/EGR fuera del vehiculo cuando sea posible; protege sensores y empaques.
                3. Si el vastago se traba, no fuerces motor electrico; limpia asiento o reemplaza.
                4. Si los pasajes del multiple estan tapados, la solucion real es desmontar y descarbonizar, no solo cambiar la valvula.
            """.trimIndent(),
            "Valvula EGR, empaque, limpiador EGR, manguera de vacio",
            "medio",
            "HEURISTIC_ONLY"
        )
        "cooling" -> arrayOf(
            "Temperatura/refrigeracion: sensor, termostato y aire en sistema",
            """
                1. Compara ECT frio con temperatura ambiente; si arranca marcando absurdo, revisa sensor/cableado.
                2. Para P0128, verifica termostato abierto, nivel bajo, abanicos y purga de aire.
                3. No uses tapafugas como reparacion permanente: puede obstruir radiador, heater core o pasos finos.
                4. Usa refrigerante correcto y purga segun procedimiento del fabricante.
            """.trimIndent(),
            "Termostato, sensor ECT, refrigerante, tapa/radiador",
            "medio",
            "HEURISTIC_ONLY"
        )
        "throttle" -> arrayOf(
            "Acelerador electronico: limpieza y reaprendizaje",
            """
                1. Revisa bateria y masas; bajo voltaje dispara fallas de cuerpo de aceleracion.
                2. Limpia mariposa con limpiador de throttle body, sin inundar motor electrico ni forzar engranes plasticos.
                3. Verifica APP/TPS doble: las dos senales deben moverse suaves y coherentes.
                4. Ejecuta reaprendizaje/adaptacion si el fabricante lo requiere despues de limpiar o reemplazar.
            """.trimIndent(),
            "Cuerpo de aceleracion, pedal APP, limpiador throttle",
            "medio",
            "HEURISTIC_ONLY"
        )
        "transmission" -> arrayOf(
            "Transmision: fluido correcto antes de sensores caros",
            """
                1. Verifica nivel, color, olor y especificacion exacta del ATF/CVT/DCT; fluido equivocado causa fallas falsas.
                2. Revisa conectores de solenoides, arnes interno y tierra de transmision.
                3. Si hay limaduras o olor quemado, no confies en aditivos: diagnostica presion, cuerpo de valvulas y embragues.
                4. Despues de reparar, borra adaptativos solo si el procedimiento del fabricante lo indica.
            """.trimIndent(),
            "Fluido correcto, filtro, solenoide, arnes o cuerpo de valvulas",
            "dificil",
            "HEURISTIC_ONLY"
        )
        "network" -> arrayOf(
            "Red CAN/UDS: energia, masa y terminacion",
            """
                1. No programes ni reemplaces modulos antes de medir bateria, fusibles, alimentaciones y masas.
                2. Mide resistencia CAN con bateria desconectada: normalmente cerca de 60 ohm entre CAN-H y CAN-L cuando la red esta completa.
                3. Si hay corto a masa/B+, desconecta modulos por sectores hasta que la red vuelva.
                4. Reparacion de arnes CAN: empalme soldado o crimp automotriz sellado, par trenzado respetado y termocontraible adhesivo.
            """.trimIndent(),
            "Fusible, masa, arnes CAN, modulo afectado",
            "dificil",
            "HEURISTIC_ONLY"
        )
        "chassis" -> arrayOf(
            "Chasis/ABS: sensor, aro reluctor y cable flexible",
            """
                1. Lee velocidad de cada rueda en vivo; la rueda que cae a cero o salta identifica el sector.
                2. Inspecciona cable del sensor donde flexiona con suspension y direccion.
                3. Limpia oxido en asiento del sensor; exceso de separacion cambia la senal.
                4. No uses grasa o limaduras cerca del sensor magnetico; puede contaminar lectura.
            """.trimIndent(),
            "Sensor ABS, aro reluctor, cableado, rodamiento",
            "medio",
            "HEURISTIC_ONLY"
        )
        "body" -> arrayOf(
            "Body/airbag/confort: baja tension y conectores primero",
            """
                1. En sistemas SRS/airbag desconecta bateria y espera el tiempo del fabricante antes de tocar conectores.
                2. Revisa humedad bajo alfombra, modulos de puerta, conectores bajo asiento y fusibles.
                3. No midas airbags con ohmimetro directo ni apliques voltaje de prueba.
                4. Despues de reparar, confirma con escaner que el modulo permite borrar el DTC.
            """.trimIndent(),
            "Conector, fusible, modulo body/SRS segun prueba",
            "dificil",
            "HEURISTIC_ONLY"
        )
        else -> arrayOf(
            "Circuito/sensor: diagnostico electrico profesional",
            """
                1. Identifica si el codigo dice circuito abierto, corto a tierra, corto a positivo, rango/rendimiento o intermitente.
                2. Abierto/corto: mide alimentacion, masa, referencia 5V/12V y continuidad con carga ligera.
                3. Rango/rendimiento: compara dato vivo con una medicion fisica real.
                4. Intermitente: prueba de movimiento del arnes, calor controlado y vibracion suave mientras miras el dato en vivo.
            """.trimIndent(),
            "Sensor/actuador, conector, terminal o arnes",
            "medio",
            "HEURISTIC_ONLY"
        )
    }

    return DtcSolutionCard(
        source = "TALLER ELYSIUM",
        title = title,
        description = description,
        partRequired = part,
        difficultyLevel = difficulty
    )
}

private fun buildElectricalSolutionCard(code: String, family: String): DtcSolutionCard {
    val extra = if (family == "network") {
        "En redes CAN respeta par trenzado, longitud de reparacion minima y aislamiento; no conviertas un empalme en una antena."
    } else {
        "En sensores analogicos revisa referencia, masa y senal. En solenoides/actuadores revisa alimentacion, comando y consumo."
    }
    return DtcSolutionCard(
        source = "TALLER ELYSIUM",
        title = "Conectores, cableado y masas para $code",
        description = """
            Desconecta bateria si vas a reparar arnes. Abre el conector, busca pines verdes, flojos, hundidos, quemados o llenos de aceite.
            Limpia con limpiador de contactos electronicos, deja evaporar y aplica grasa dielectrica solo en sello externo, no como relleno entre terminales.
            Repara cables con crimp automotriz sellado o soldadura bien hecha mas termocontraible adhesivo; no retuerzas cables con cinta como reparacion final.
            $extra
        """.trimIndent(),
        partRequired = "Limpiador de contactos, terminales, termocontraible adhesivo",
        difficultyLevel = "medio"
    )
}

private fun buildChemicalsSolutionCard(code: String, family: String): DtcSolutionCard {
    val familyAdvice = when (family) {
        "lean", "rich", "misfire" -> "Aditivo limpia-inyectores: solo como apoyo si las pruebas indican suciedad leve y no hay falla electrica, baja compresion, fuga de vacio o inyector trabado."
        "catalyst" -> "Aditivo de catalizador: solo intento de bajo costo cuando no hay misfire, consumo de aceite ni sustrato derretido; si la eficiencia falla de verdad, se reemplaza."
        "cooling" -> "Tapafugas de refrigerante: solo emergencia para mover el vehiculo; no es reparacion profesional y puede tapar radiador o calefaccion."
        "transmission" -> "Aditivos de transmision: evita usarlos para ocultar desgaste interno; primero confirma fluido correcto, nivel y presiones."
        else -> "Aditivos no reparan circuitos, sensores muertos, arnes abierto ni modulos sin alimentacion."
    }
    return DtcSolutionCard(
        source = "TALLER ELYSIUM",
        title = "Quimicos, WD-40, limpiadores y aditivos para $code",
        description = """
            Usa el quimico correcto por zona: MAF cleaner para MAF, throttle body cleaner para mariposa, contact cleaner para conectores, penetrante para tornillos agarrados.
            No uses WD-40 multiuso dentro de sensores MAF/O2/AFR ni conectores de ECU como sustituto de limpiador electronico. Si usas penetrante tipo WD-40 Specialist Penetrant, que sea en roscas frias y lejos de conectores abiertos.
            No rocíes solventes sobre escape caliente, alternador girando, bobinas energizadas ni cerca de gasolina. Deja evaporar antes de conectar bateria o arrancar.
            $familyAdvice
        """.trimIndent(),
        partRequired = "Limpiador especifico, penetrante, aditivo solo si aplica",
        difficultyLevel = "facil"
    )
}

private fun buildHardwareExtractionSolutionCard(code: String, family: String): DtcSolutionCard {
    val relevance = when (family) {
        "oxygen", "catalyst", "egr", "evap", "cooling" -> "Muy relevante en sensores de escape, EGR, termostatos, abrazaderas y lineas oxidadas."
        else -> "Usalo solo si el componente asociado al $code esta fisicamente agarrado u oxidado."
    }
    return DtcSolutionCard(
        source = "TALLER ELYSIUM",
        title = "Tornillos agarrados, Metabo y soldadura: escalera segura",
        description = """
            $relevance
            Escala sin destruir: dado de 6 puntas correcto, golpe seco controlado, penetrante 20-60 min, reapriete leve y afloje gradual. Si hay calor, que sea localizado, con extintor cerca y nunca despues de rociar solvente.
            Pulidora/cortadora tipo Metabo: solo para cortar cabeza, abrazadera o soporte cuando ya protegiste arnes, mangueras, deposito, lineas de freno/combustible y vidrios. Cubre chispas y no cortes cerca de vapores.
            Soldar una tuerca a un perno roto es ultimo recurso: bateria desconectada, modulos sensibles protegidos segun procedimiento, masa de soldadora cerca del punto, zona limpia de aceite/gasolina y operador competente.
            Si se dana rosca, repara con helicoil/timesert o inserto equivalente; silicon y pegamento no sustituyen torque ni rosca estructural.
        """.trimIndent(),
        partRequired = "Penetrante, dado 6 puntas, extractor, helicoil/timesert si aplica",
        difficultyLevel = "dificil"
    )
}

object DtcRepairDatabase {
    fun getSteps(code: String): List<RepairStep> {
        val u = code.uppercase()
        return when {
            u.startsWith("P030") || u == "P0300" -> listOf(
                RepairStep("Prueba de Bobinas de Encendido", "Intercambia la bobina del cilindro afectado con otra posición compatible. Vuelve a escanear; si la falla se desplaza al cilindro receptor, la bobina queda identificada como sospechosa. Confirma el procedimiento y compatibilidad en la documentación del vehículo antes de sustituir.", "⚡", null, null),
                RepairStep("Inspección de la Bujía", "Retira la bujía con la herramienta correcta e inspecciona electrodo, aislador, depósitos y contaminación. Compara separación, tipo y torque con la especificación OEM del vehículo antes de ajustar o sustituir.", "🔧", null, null),
                RepairStep("Verificación de Inyectores de Combustible", "Comprueba mando eléctrico, alimentación, señal y contribución del inyector con herramientas apropiadas. La resistencia y la presión aceptables dependen del inyector y del sistema; consulta la especificación OEM y no condenes la pieza con un rango genérico.", "⛽", null, null),
                RepairStep("Prueba mecánica del cilindro", "Si chispa y combustible fueron verificados, mide compresión y, cuando corresponda, fuga de cilindro. Compara entre cilindros y contra la especificación OEM; un valor aislado sin procedimiento, temperatura y condiciones de prueba no confirma una reparación.", "📊", null, null)
            )
            u == "P0171" || u == "P0174" -> listOf(
                RepairStep("Búsqueda de aire no medido", "Inspecciona admisión, PCV, juntas y mangueras mediante un método seguro y apropiado, preferentemente humo controlado. No rocíes productos inflamables sobre un motor caliente. Correlaciona el hallazgo con fuel trims y condiciones de carga.", "💨", null, null),
                RepairStep("Inspección del sensor MAF", "Revisa contaminación, alimentación, masa, señal, conector y tubería. Limpia únicamente si el fabricante del sensor lo permite, con producto específico y sin tocar el elemento; valida el resultado con datos antes y después.", "🧼", null, null),
                RepairStep("Prueba de presión de combustible", "Mide presión y retención con el adaptador y las precauciones correctas. Compara exclusivamente contra la especificación OEM para ese motor y condición; una presión baja no identifica por sí sola bomba, filtro, regulador o alimentación.", "⛽", null, null),
                RepairStep("Evaluación de sensores de mezcla", "Grafica sensores de oxígeno o relación aire-combustible junto con trims, carga y temperatura. El comportamiento esperado depende de la tecnología y estrategia del vehículo; verifica tipo y especificación antes de condenar un sensor.", "📡", null, null)
            )
            else -> listOf(
                RepairStep("Inspección del conector y cableado", "Identifica primero el componente y terminales asociados a $code con información aplicable al vehículo. Inspecciona daños, humedad, corrosión, tensión de terminales y continuidad sin forzar ni puentear circuitos.", "🔌", null, null),
                RepairStep("Medición del circuito", "Con el diagrama correcto, mide alimentación, masa, referencia y señal en los puntos y condiciones indicados por el fabricante. No asumas que todo circuito usa 5 V o 12 V.", "⚡", null, null),
                RepairStep("Prueba del componente", "Aplica la prueba indicada para el tipo exacto de sensor o actuador y compara con la especificación OEM. Una lectura cero o infinita puede ser válida o inválida según el componente, circuito y condición de medición.", "📟", null, null),
                RepairStep("Verificación posterior", "Después de la intervención, conserva el escaneo previo, borra únicamente cuando sea seguro y vuelve a observar el DTC bajo las condiciones requeridas. Declara resuelto solo con ausencia verificada, monitores pertinentes y evidencia posterior suficiente.", "🚗", null, null)
            )
        }
    }

    fun getRequiredTools(code: String): List<String> {
        val u = code.uppercase()
        return when {
            u.startsWith("P030") || u == "P0300" -> listOf("Llave de Bujías 5/8\"", "Multímetro Digital", "Bobina de Repuesto", "Escáner OBD2 Elysium Vanguard")
            u == "P0171" || u == "P0174" -> listOf("Limpiador de Sensor MAF", "Manómetro de Combustible", "Destornillador Plano/Fórmula", "Escáner OBD2 Elysium Vanguard")
            else -> listOf("Multímetro Digital", "Limpiador de Contactos Eléctricos", "Juego de Destornilladores y Llaves", "Escáner OBD2 Elysium Vanguard")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcRepairGuideScreen(
    navController: NavController,
    dtcCode: String,
    findingId: String? = null,
    viewModel: ObdViewModel
) {
    var definition by remember { mutableStateOf<com.elysium369.meet.data.local.entities.DtcDefinitionEntity?>(null) }
    var symptoms by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcSymptomEntity>>(emptyList()) }
    var causes by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcCauseEntity>>(emptyList()) }
    var dbProcedures by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcProcedureEntity>>(emptyList()) }
    var relatedPids by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcRelatedPidEntity>>(emptyList()) }
    var coOccurrences by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity>>(emptyList()) }
    var repairCosts by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcRepairCostEntity>>(emptyList()) }
    var verifiedFixes by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcVerifiedFixEntity>>(emptyList()) }
    var communityCases by remember { mutableStateOf<List<RepairCase>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var activeStepIdx by remember { mutableIntStateOf(0) }
    
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val telemetrySamples by viewModel.telemetrySamples.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isScannerConnected = connectionState == ObdState.CONNECTED

    // Upvoted fixes cache to disable upvote button after click
    val upvotedFixIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(dtcCode) {
        isLoading = true
        coroutineScope.launch {
            definition = viewModel.getDtcDefinition(dtcCode)
            symptoms = viewModel.getDtcSymptoms(dtcCode)
            causes = viewModel.getDtcCauses(dtcCode)
            dbProcedures = viewModel.getDtcProcedures(dtcCode)
            relatedPids = viewModel.getDtcRelatedPids(dtcCode)
            coOccurrences = viewModel.getDtcCoOccurrences(dtcCode)
            repairCosts = viewModel.getDtcRepairCosts(dtcCode)
            verifiedFixes = viewModel.getDtcVerifiedFixes(dtcCode)
            communityCases = viewModel.getDtcCommunityRepairCases(dtcCode)
            isLoading = false
        }
    }

    val steps = remember(definition, dbProcedures) {
        val rawStepsList = if (dbProcedures.isNotEmpty()) {
            dbProcedures.map { proc ->
                RepairStep(
                    title = proc.titleEs,
                    description = proc.descriptionEs,
                    icon = proc.icon,
                    minutes = proc.estimatedMinutes,
                    difficulty = when (proc.difficulty.lowercase()) {
                        "facil" -> "Fácil"
                        "medio" -> "Medio"
                        else -> "Difícil"
                    },
                    estimateProvenance = "Procedimiento registrado en la base de conocimiento",
                )
            }
        } else {
            val rawSteps = definition?.diagnosticSteps
            if (!rawSteps.isNullOrEmpty()) {
                try {
                    if (rawSteps.trim().startsWith("[")) {
                        val array = org.json.JSONArray(rawSteps)
                        val list = mutableListOf<RepairStep>()
                        for (i in 0 until array.length()) {
                            list.add(DtcRepairHelper.parseStepString(array.getString(i), i + 1))
                        }
                        list
                    } else {
                        val lines = rawSteps.split(Regex("[|\n]")).map { it.trim() }.filter { it.isNotEmpty() }
                        lines.mapIndexed { idx, line -> DtcRepairHelper.parseStepString(line, idx + 1) }
                    }
                } catch (e: Exception) {
                    listOf(RepairStep("Procedimiento de Diagnóstico", rawSteps, "🔧", null, null))
                }
            } else {
                DtcRepairDatabase.getSteps(dtcCode)
            }
        }
        rawStepsList.distinctBy { it.description.trim().lowercase() }
    }

    val tools = remember(definition, dbProcedures) {
        val toolsFromProcedures = dbProcedures.mapNotNull { it.toolRequired }.filter { it.isNotBlank() }
        if (toolsFromProcedures.isNotEmpty()) {
            toolsFromProcedures
        } else {
            val rawTools = definition?.specialToolsRequired
            if (!rawTools.isNullOrEmpty()) {
                try {
                    if (rawTools.trim().startsWith("[")) {
                        val array = org.json.JSONArray(rawTools)
                        val list = mutableListOf<String>()
                        for (i in 0 until array.length()) {
                            list.add(array.getString(i))
                        }
                        list
                    } else {
                        rawTools.split(Regex("[,|\n]")).map { it.trim() }.filter { it.isNotEmpty() }
                    }
                } catch (e: Exception) {
                    listOf(rawTools)
                }
            } else {
                DtcRepairDatabase.getRequiredTools(dtcCode)
            }
        }
    }

    val displayCauses = remember(dtcCode, causes) {
        buildDisplayRepairCauses(dtcCode, causes)
    }

    val solutionCards = remember(dtcCode, definition, displayCauses, steps, repairCosts, verifiedFixes, communityCases) {
        buildDtcSolutionCards(
            dtcCode = dtcCode,
            definition = definition,
            displayCauses = displayCauses,
            steps = steps,
            repairCosts = repairCosts,
            verifiedFixes = verifiedFixes,
            communityCases = communityCases
        )
    }

    val livePidReadings = remember(relatedPids, definition, telemetrySamples, isScannerConnected, dtcCode) {
        buildLivePidReadings(
            relatedPids = relatedPids,
            definition = definition,
            telemetrySamples = telemetrySamples,
            isConnected = isScannerConnected,
            dtcCode = dtcCode
        )
    }
    val liveSectionVerdict = remember(livePidReadings, isScannerConnected, telemetrySamples) {
        buildLiveSectionVerdict(
            readings = livePidReadings,
            isConnected = isScannerConnected,
            telemetrySamples = telemetrySamples
        )
    }

    val toolChecks = remember(tools) { mutableStateListOf(*Array(tools.size) { false }) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            EliteTopAppBar(
                title = "GUÍA DE REPARACIÓN",
                subtitle = "Código DTC: $dtcCode",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MeetColors.cyberCyan)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                // Tab Navigation
                val tabs = listOf("Diagnóstico", "Procedimiento", "Parámetros Live", "Soluciones")
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MeetColors.cyberCyan,
                    divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) },
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MeetColors.cyberCyan,
                                height = 3.dp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Black else FontWeight.Bold,
                                    color = if (selectedTabIndex == index) MeetColors.cyberCyan else MeetColors.textSecondary,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        )
                    }
                }

                // Tab Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    when (selectedTabIndex) {
                        0 -> {
                            // ═══════════ TAB 0: DIAGNÓSTICO ═══════════
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.neonGreen,
                                enableHolo3D = true,
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        "DTC → PRUEBA → PIEZA → 3D/360",
                                        color = MeetColors.neonGreen,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        "Abre únicamente componentes vinculados a $dtcCode. La relación orienta la inspección; no autoriza reemplazo ni compatibilidad exacta sin VIN/OEM/prueba física.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                    )
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                findingId?.takeIf { it.isNotBlank() }?.let { canonicalId ->
                                                    navController.navigate(
                                                        "component_locator?findingId=${java.net.URLEncoder.encode(canonicalId, "UTF-8")}",
                                                    )
                                                }
                                            },
                                            enabled = !findingId.isNullOrBlank(),
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                        ) {
                                            Text(
                                                if (findingId.isNullOrBlank()) "3D REQUIERE HALLAZGO GUARDADO" else "VER RUTA 3D",
                                                color = MeetColors.backgroundDark,
                                                fontWeight = FontWeight.Black,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { navController.navigate("part_request") },
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, MeetColors.cyberCyan),
                                        ) {
                                            Text("COTIZAR PIEZA", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            // Header Card
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.cyberCyan,
                                enableHolo3D = true
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "GRAFO DE CONOCIMIENTO OBD2",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Análisis Técnico $dtcCode",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    val system = definition?.system ?: "General"
                                    val severity = definition?.severity ?: "Media"
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .background(MeetColors.cyberCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(system.uppercase(), color = MeetColors.cyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    when (severity.lowercase()) {
                                                        "high", "alta", "crítico" -> MeetColors.error.copy(alpha = 0.12f)
                                                        "low", "baja" -> MeetColors.neonGreen.copy(alpha = 0.12f)
                                                        else -> MeetColors.warning.copy(alpha = 0.12f)
                                                    },
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "SEVERIDAD: ${severity.uppercase()}",
                                                color = when (severity.lowercase()) {
                                                    "high", "alta", "crítico" -> MeetColors.error
                                                    "low", "baja" -> MeetColors.neonGreen
                                                    else -> MeetColors.warning
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val descEs = definition?.descriptionEs ?: ""
                                    if (descEs.isNotEmpty()) {
                                        Text(
                                            descEs,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }

                            // Symptoms
                            PhantomSectionHeader("Síntomas Comunes")
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (symptoms.isNotEmpty()) {
                                        symptoms.forEach { symptom ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Text("•", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(symptom.symptomEs, color = Color.White, fontSize = 13.sp)
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Text(
                                                            "Probabilidad: ${symptom.probability.uppercase()}",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        if (symptom.isDriverNoticeable) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MeetColors.warning.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("PERCEPTIBLE POR CONDUCTOR", color = MeetColors.warning, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val fallbackSymptoms = definition?.symptoms
                                        if (!fallbackSymptoms.isNullOrBlank()) {
                                            Text(fallbackSymptoms, color = Color.White, fontSize = 13.sp)
                                        } else {
                                            Text("No se encontraron síntomas específicos registrados para este código.", color = MeetColors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Causes
                            PhantomSectionHeader("Causas Probables")
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (displayCauses.isNotEmpty()) {
                                        displayCauses.forEach { displayCause ->
                                            val cause = displayCause.source
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        displayCause.text,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        if (cause.isElectronic) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MeetColors.electricBlue.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("⚡ ELEC", color = MeetColors.electricBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        if (cause.isMechanical) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MeetColors.warning.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("⚙️ MEC", color = MeetColors.warning, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }

                                                // Probability bar
                                                val probValue = when (cause.probability.lowercase()) {
                                                    "alta" -> 0.9f
                                                    "media" -> 0.6f
                                                    else -> 0.3f
                                                }
                                                val probColor = when (cause.probability.lowercase()) {
                                                    "alta" -> MeetColors.neonGreen
                                                    "media" -> MeetColors.warning
                                                    else -> MeetColors.textSecondary
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    LinearProgressIndicator(
                                                        progress = { probValue },
                                                        color = probColor,
                                                        trackColor = Color.White.copy(alpha = 0.05f),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(4.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = cause.probability.uppercase(),
                                                        color = probColor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val fallbackCauses = DtcUtils.getSpanishPossibleCauses(dtcCode, definition?.possibleCauses)
                                        if (!fallbackCauses.isNullOrBlank()) {
                                            Text(fallbackCauses.replace(" | ", "\n").replace("|", "\n"), color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                                        } else {
                                            Text("No se encontraron causas comunes registradas.", color = MeetColors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Co-occurrences
                            if (coOccurrences.isNotEmpty()) {
                                PhantomSectionHeader("Códigos Asociados (Co-ocurrencia)")
                                EliteCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Los siguientes códigos suelen aparecer de manera simultánea en el diagnóstico debido a fallas mecánicas en cascada:",
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        coOccurrences.forEach { co ->
                                            val related = if (co.dtcCode == dtcCode) co.relatedDtcCode else co.dtcCode
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        related,
                                                        color = MeetColors.cyberCyan,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 13.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Fuerza: ${(co.correlationStrength * 100).toInt()}%",
                                                        color = MeetColors.textSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                val combo = co.combinedDiagnosisEs
                                                if (!combo.isNullOrBlank()) {
                                                    Text(
                                                        combo,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Repair Costs
                            if (repairCosts.isNotEmpty()) {
                                PhantomSectionHeader("Costos Estimados de Reparación")
                                EliteCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        repairCosts.forEach { cost ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        "Rango Estimado (${cost.region})",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    val labor = cost.laborHours
                                                    if (labor != null && labor > 0) {
                                                        Text(
                                                            "Tiempo estimado de labor: $labor h",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "$${cost.minCostUsd.toInt()} - $${cost.maxCostUsd.toInt()} ${cost.currency}",
                                                    color = MeetColors.neonGreen,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 16.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            val desc = cost.partsDescription
                                            if (!desc.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Repuestos sugeridos: $desc",
                                                    color = MeetColors.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val fallbackCost = definition?.repairCostUSD
                                if (!fallbackCost.isNullOrBlank()) {
                                    PhantomSectionHeader("Costos Estimados de Reparación")
                                    EliteCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Rango Promedio", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text(fallbackCost, color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                            }
                                            val estHours = definition?.laborHoursEstimate
                                            if (!estHours.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Tiempo estimado de labor: $estHours", color = MeetColors.textSecondary, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ═══════════ TAB 1: PROCEDIMIENTO ═══════════
                            // Required tools
                            if (tools.isNotEmpty()) {
                                PhantomSectionHeader("Herramientas Requeridas")
                                EliteCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        tools.forEachIndexed { idx, tool ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { if (idx < toolChecks.size) toolChecks[idx] = !toolChecks[idx] }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                val isChecked = idx < toolChecks.size && toolChecks[idx]
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { if (idx < toolChecks.size) toolChecks[idx] = it },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = MeetColors.neonGreen,
                                                        uncheckedColor = MeetColors.textSecondary,
                                                        checkmarkColor = MeetColors.backgroundDeep
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = tool,
                                                    color = if (isChecked) MeetColors.textSecondary else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Stepper
                            if (steps.isNotEmpty()) {
                                PhantomSectionHeader("Progreso del Diagnóstico")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    steps.forEachIndexed { idx, _ ->
                                        val isCompleted = idx < activeStepIdx
                                        val isActive = idx == activeStepIdx
                                        val barColor = when {
                                            isCompleted -> MeetColors.neonGreen
                                            isActive -> MeetColors.cyberCyan
                                            else -> MeetColors.borderSubtle
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(barColor)
                                        )
                                    }
                                }

                                val currentStep = steps.getOrNull(activeStepIdx)
                                if (currentStep != null) {
                                    AnimatedContent(
                                        targetState = currentStep,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { it } togetherWith
                                            fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { -it }
                                        },
                                        label = "stepAnim"
                                    ) { targetStep ->
                                        EliteCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            glowColor = when (targetStep.difficulty) {
                                                "Fácil" -> MeetColors.neonGreen
                                                "Medio" -> MeetColors.warning
                                                "Difícil" -> MeetColors.error
                                                else -> MeetColors.cyberCyan
                                            },
                                            enableHolo3D = true
                                        ) {
                                            Column(modifier = Modifier.padding(18.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .clip(CircleShape)
                                                                .background(MeetColors.neonGreen.copy(alpha = 0.12f))
                                                                .border(1.dp, MeetColors.neonGreen, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            AnimatedNeonGlyph(targetStep.icon, contentDescription = null, fontSize = 14.sp)
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            "PASO ${activeStepIdx + 1} DE ${steps.size}",
                                                            color = MeetColors.neonGreen,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Black,
                                                            letterSpacing = 1.sp
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                when (targetStep.difficulty) {
                                                                    "Fácil" -> MeetColors.neonGreen.copy(alpha = 0.12f)
                                                                    "Medio" -> MeetColors.warning.copy(alpha = 0.12f)
                                                                    "Difícil" -> MeetColors.error.copy(alpha = 0.12f)
                                                                    else -> MeetColors.cyberCyan.copy(alpha = 0.12f)
                                                                }
                                                            )
                                                            .border(
                                                                1.dp,
                                                                when (targetStep.difficulty) {
                                                                    "Fácil" -> MeetColors.neonGreen
                                                                    "Medio" -> MeetColors.warning
                                                                    "Difícil" -> MeetColors.error
                                                                    else -> MeetColors.cyberCyan
                                                                },
                                                                RoundedCornerShape(6.dp)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Text(
                                                            text = targetStep.difficulty?.uppercase() ?: "NIVEL NO VERIFICADO",
                                                            color = when (targetStep.difficulty) {
                                                                "Fácil" -> MeetColors.neonGreen
                                                                "Medio" -> MeetColors.warning
                                                                "Difícil" -> MeetColors.error
                                                                else -> MeetColors.cyberCyan
                                                            },
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 8.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = targetStep.title,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = targetStep.description,
                                                    color = MeetColors.textPrimary,
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("⏱️ Tiempo: ", color = MeetColors.textSecondary, fontSize = 11.sp)
                                                    Text(
                                                        targetStep.minutes
                                                            ?.takeIf { targetStep.estimateProvenance != null }
                                                            ?.let { "$it min · ${targetStep.estimateProvenance}" }
                                                            ?: "No estimado; depende del vehículo y la prueba física",
                                                        color = if (targetStep.minutes == null) MeetColors.warning else Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (activeStepIdx > 0) {
                                        EliteOutlinedButton(
                                            text = "ANTERIOR",
                                            onClick = { activeStepIdx-- },
                                            color = MeetColors.cyberCyan,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    val isLast = activeStepIdx == steps.size - 1
                                    EliteButton(
                                        text = if (isLast) "TERMINÉ · VERIFICAR" else "SIGUIENTE PASO",
                                        onClick = {
                                            if (isLast) {
                                                navController.navigate("scanner") { launchSingleTop = true }
                                            } else {
                                                activeStepIdx++
                                            }
                                        },
                                        color = MeetColors.neonGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No se encontraron pasos de reparación para este código.", color = MeetColors.textSecondary, fontSize = 14.sp)
                                }
                            }
                        }

                        2 -> {
                            // ═══════════ TAB 2: PARÁMETROS LIVE ═══════════
                            PhantomSectionHeader("Datos Reales del Scanner en Vivo")
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                if (isScannerConnected) "Scanner conectado: lecturas reales del ECU" else "Scanner desconectado: esperando enlace real",
                                                color = if (isScannerConnected) MeetColors.neonGreen else MeetColors.textSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                if (telemetrySamples.values.any(TelemetrySample::hasRealValue)) {
                                                    "PIDs OBD reales válidos: ${telemetrySamples.values.count(TelemetrySample::hasRealValue)}"
                                                } else "Sin datos OBD reales válidos todavía",
                                                color = MeetColors.textSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Button(
                                            onClick = { navController.navigate("scanner") { launchSingleTop = true } },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MeetColors.cyberCyan.copy(alpha = 0.12f),
                                                contentColor = MeetColors.cyberCyan
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("SCANNER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    val rpm = telemetrySamples.resolveVerifiedPidValue("$01 0C")
                                    val speed = telemetrySamples.resolveVerifiedPidValue("$01 0D")
                                    val temp = telemetrySamples.resolveVerifiedPidValue("$01 05")
                                    val voltage = telemetrySamples.resolveVerifiedPidValue("$01 42")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LiveMiniMetric(
                                            "RPM",
                                            rpm?.toInt()?.toString() ?: "--",
                                            when {
                                                rpm == null -> "NO VERIFICADO"
                                                rpm > 400f -> "MOTOR ON"
                                                else -> "MOTOR OFF"
                                            },
                                            Modifier.weight(1f),
                                        )
                                        LiveMiniMetric("KM/H", speed?.toInt()?.toString() ?: "--", "VEL.", Modifier.weight(1f))
                                        LiveMiniMetric("TEMP", temp?.toInt()?.let { "$it°" } ?: "--", "ECT", Modifier.weight(1f))
                                        LiveMiniMetric("VOLT", voltage?.let { String.format("%.1f", it) } ?: "--", "ECU", Modifier.weight(1f))
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(liveSectionVerdict.color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                            .border(1.dp, liveSectionVerdict.color.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "VEREDICTO LIVE",
                                            color = MeetColors.textSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            liveSectionVerdict.title,
                                            color = liveSectionVerdict.color,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            liveSectionVerdict.detail,
                                            color = Color.White.copy(alpha = 0.88f),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                                    Text(
                                        "Estos valores salen del mismo flujo OBD real que alimenta los gauges. La app evalúa cada lectura contra rango OEM si existe y contra lógica diagnóstica genérica cuando el carro solo expone PIDs estándar.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )

                                    if (livePidReadings.isNotEmpty()) {
                                        livePidReadings.forEach { reading ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (reading.hasValue) reading.statusColor.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.03f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .border(1.dp, reading.statusColor.copy(alpha = if (reading.hasValue) 0.28f else 0.12f), RoundedCornerShape(8.dp))
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (reading.hasValue) 10.dp else 8.dp)
                                                        .clip(CircleShape)
                                                        .background(reading.statusColor)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        reading.title,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        "Comando OBD: ${reading.command}",
                                                        color = MeetColors.textSecondary,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        reading.helperText,
                                                        color = MeetColors.textSecondary,
                                                        fontSize = 10.sp,
                                                        lineHeight = 14.sp
                                                    )
                                                    if (reading.diagnosis.isNotBlank()) {
                                                        Text(
                                                            "Diagnóstico: ${reading.diagnosis}",
                                                            color = Color.White.copy(alpha = 0.82f),
                                                            fontSize = 10.sp,
                                                            lineHeight = 14.sp
                                                        )
                                                    }
                                                    if (reading.action.isNotBlank()) {
                                                        Text(
                                                            "Acción: ${reading.action}",
                                                            color = reading.statusColor.copy(alpha = 0.95f),
                                                            fontSize = 10.sp,
                                                            lineHeight = 14.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        reading.status,
                                                        color = reading.statusColor,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Row(verticalAlignment = Alignment.Bottom) {
                                                        Text(
                                                            reading.displayValue,
                                                            color = Color.White,
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Black,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        if (reading.unit.isNotBlank() && reading.displayValue != "--") {
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                reading.unit,
                                                                color = MeetColors.textSecondary,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    val normalRange = reading.normalRange
                                                    if (!normalRange.isNullOrBlank()) {
                                                        Text(
                                                            "Referencia: $normalRange",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 8.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text("No hay parámetros vinculados a este DTC y todavía no hay datos OBD para sugerir lecturas.", color = MeetColors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        3 -> {
                            // ═══════════ TAB 3: SOLUCIONES VERIFICADAS ═══════════
                            PhantomSectionHeader("Soluciones de Taller + Comunidad")
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                solutionCards.forEach { solution ->
                                    val sourceLower = solution.source.lowercase()
                                    val accentColor = when {
                                        sourceLower.contains("tsb") -> MeetColors.electricBlue
                                        sourceLower.contains("oem") -> MeetColors.cyberCyan
                                        sourceLower.contains("comunidad") -> MeetColors.neonGreen
                                        else -> MeetColors.cyberCyan
                                    }
                                    EliteCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        glowColor = accentColor.copy(alpha = 0.18f)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = solution.source,
                                                        color = accentColor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                val provenRate = solution.successMetric
                                                Text(
                                                    text = provenRate?.let { metric ->
                                                        if (metric.confidenceMethod == "CALIBRATED_HOLDOUT") {
                                                            "Resultado calibrado: ${(metric.value * 100).toInt()}% · ${metric.sampleCount} muestra(s) · ${metric.source}"
                                                        } else {
                                                            "Evidencia registrada · ${metric.sampleCount} muestra(s) · ${metric.source} · sin probabilidad calibrada"
                                                        }
                                                    } ?: "Sin estadística verificable",
                                                    color = if (provenRate == null) MeetColors.warning else accentColor,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = solution.title,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                lineHeight = 19.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = solution.description,
                                                color = Color.White.copy(alpha = 0.92f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 17.sp
                                            )

                                            val part = solution.partRequired
                                            if (!part.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Material/Repuesto: $part",
                                                    color = MeetColors.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    val cost = solution.estimatedCostUsd
                                                    if (cost != null && cost > 0) {
                                                        Text(
                                                            text = "Costo aprox.: ~$${cost.toInt()} USD",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = "Nivel: ${solution.difficultyLevel.uppercase()}",
                                                        color = MeetColors.textSecondary,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                val fixId = solution.verifiedFixId
                                                if (fixId != null) {
                                                    val isUpvoted = upvotedFixIds.contains(fixId)
                                                    Button(
                                                        onClick = {
                                                            if (!isUpvoted) {
                                                                coroutineScope.launch {
                                                                    viewModel.upvoteDtcFix(fixId)
                                                                    upvotedFixIds.add(fixId)
                                                                    verifiedFixes = viewModel.getDtcVerifiedFixes(dtcCode)
                                                                }
                                                            }
                                                        },
                                                        enabled = !isUpvoted,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isUpvoted) Color.Transparent else accentColor.copy(alpha = 0.1f),
                                                            contentColor = accentColor,
                                                            disabledContainerColor = Color.Transparent,
                                                            disabledContentColor = MeetColors.textSecondary
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        border = if (isUpvoted) null else androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isUpvoted) "VALIDADO" else "UTIL (${solution.voteCount})",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = if (sourceLower.contains("comunidad")) "Votos: ${solution.voteCount}" else "GUIA LOCAL",
                                                        color = accentColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveMiniMetric(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MeetColors.cyberCyan.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = MeetColors.textSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            value,
            color = if (value == "--") MeetColors.textSecondary else MeetColors.cyberCyan,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            subtitle,
            color = MeetColors.textSecondary,
            fontSize = 7.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
