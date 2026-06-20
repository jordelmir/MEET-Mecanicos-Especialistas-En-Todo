package com.elysium369.meet.core.obd

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InputStreamReader

class DtcDatabaseLoader(
    private val context: Context,
    private val db: MeetDatabase
) {
    private val loaderScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun loadIfEmpty() {
        loaderScope.launch {
            if (db.dtcDefinitionDao().getCount() == 0) {
                Log.i("DtcDatabaseLoader", "DTC definitions table is empty. Starting streaming load...")
                try {
                    val stream = context.assets.open("dtc_database_es.json")
                    val reader = JsonReader(InputStreamReader(stream, "UTF-8"))

                    reader.beginObject()
                    while (reader.hasNext()) {
                        val rootKey = reader.nextName()
                        if (rootKey == "records") {
                            reader.beginArray()
                            val definitions = mutableListOf<DtcDefinitionEntity>()
                            var batchCount = 0
                            var totalCount = 0

                            while (reader.hasNext()) {
                                reader.beginObject()
                                var code = ""
                                var manufacturer = "GENERIC"
                                var isGeneric = "True"
                                var nameEn = "No description"
                                var nameEs = "Sin descripción"
                                var obd2StandardNameEn: String? = null
                                var system = "GENERAL"
                                var subSystem: String? = null
                                var severity = "LOW"
                                var urgency = "LOW"
                                var dtcCategory: String? = null
                                var faultType: String? = null
                                var monitorType: String? = null
                                var readinessMonitor: String? = null
                                var faultPersistence: String? = null
                                var possibleCauses: String? = null
                                var symptoms: String? = null
                                var affectedComponents: String? = null
                                var diagnosticSteps: String? = null
                                var relatedCodes: String? = null
                                var freezeFramePIDs: String? = null
                                var liveDataThresholds: String? = null
                                var repairComplexity: String? = null
                                var drivabilityImpact: String? = null
                                var repairCostUSD: String? = null
                                var laborHoursEstimate: String? = null
                                var diyFriendly: String? = null
                                var specialToolsRequired: String? = null
                                var repairVerification: String? = null
                                var preventiveMaintenance: String? = null
                                var milBehavior: String? = null
                                var emissionsImpact: String? = null
                                var warrantyNote: String? = null
                                var cascadeRisk: String? = null
                                var frequencyRank: String? = null
                                var safeToResetWithoutRepair: String? = null
                                var vehicleYearRange: String? = null
                                var obd2Protocol: String? = null
                                var countryRegulation: String? = null
                                var obd2DiagnosticMode: String? = null
                                var tsbBulletins: String? = null

                                while (reader.hasNext()) {
                                    val key = reader.nextName()
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.skipValue()
                                        continue
                                    }
                                    when (key) {
                                        "code" -> code = readStringOrRaw(reader)
                                        "manufacturer" -> manufacturer = readStringOrRaw(reader)
                                        "isGeneric" -> isGeneric = readStringOrRaw(reader)
                                        "nameEn" -> nameEn = readStringOrRaw(reader)
                                        "nameEs" -> nameEs = readStringOrRaw(reader)
                                        "obd2StandardNameEn" -> obd2StandardNameEn = readStringOrNull(reader)
                                        "system" -> system = readStringOrRaw(reader)
                                        "subSystem" -> subSystem = readStringOrNull(reader)
                                        "severity" -> severity = readStringOrRaw(reader)
                                        "urgency" -> urgency = readStringOrRaw(reader)
                                        "dtcCategory" -> dtcCategory = readStringOrNull(reader)
                                        "faultType" -> faultType = readStringOrNull(reader)
                                        "monitorType" -> monitorType = readStringOrNull(reader)
                                        "readinessMonitor" -> readinessMonitor = readStringOrNull(reader)
                                        "faultPersistence" -> faultPersistence = readStringOrNull(reader)
                                        "possibleCauses" -> possibleCauses = readStringOrNull(reader)
                                        "symptoms" -> symptoms = readStringOrNull(reader)
                                        "affectedComponents" -> affectedComponents = readStringOrNull(reader)
                                        "diagnosticSteps" -> diagnosticSteps = readStringOrNull(reader)
                                        "relatedCodes" -> relatedCodes = readStringOrNull(reader)
                                        "freezeFramePIDs" -> freezeFramePIDs = readStringOrNull(reader)
                                        "liveDataThresholds" -> liveDataThresholds = readStringOrNull(reader)
                                        "repairComplexity" -> repairComplexity = readStringOrNull(reader)
                                        "drivabilityImpact" -> drivabilityImpact = readStringOrNull(reader)
                                        "repairCostUSD" -> repairCostUSD = readStringOrNull(reader)
                                        "laborHoursEstimate" -> laborHoursEstimate = readStringOrNull(reader)
                                        "diyFriendly" -> diyFriendly = readStringOrNull(reader)
                                        "specialToolsRequired" -> specialToolsRequired = readStringOrNull(reader)
                                        "repairVerification" -> repairVerification = readStringOrNull(reader)
                                        "preventiveMaintenance" -> preventiveMaintenance = readStringOrNull(reader)
                                        "milBehavior" -> milBehavior = readStringOrNull(reader)
                                        "emissionsImpact" -> emissionsImpact = readStringOrNull(reader)
                                        "warrantyNote" -> warrantyNote = readStringOrNull(reader)
                                        "cascadeRisk" -> cascadeRisk = readStringOrNull(reader)
                                        "frequencyRank" -> frequencyRank = readStringOrNull(reader)
                                        "safeToResetWithoutRepair" -> safeToResetWithoutRepair = readStringOrNull(reader)
                                        "vehicleYearRange" -> vehicleYearRange = readStringOrNull(reader)
                                        "obd2Protocol" -> obd2Protocol = readStringOrNull(reader)
                                        "countryRegulation" -> countryRegulation = readStringOrNull(reader)
                                        "obd2DiagnosticMode" -> obd2DiagnosticMode = readStringOrNull(reader)
                                        "tsbBulletins" -> tsbBulletins = readStringOrNull(reader)
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()

                                if (code.isNotEmpty()) {
                                    definitions.add(
                                        DtcDefinitionEntity(
                                            code = code,
                                            manufacturer = manufacturer,
                                            isGeneric = isGeneric,
                                            descriptionEs = nameEs,
                                            descriptionEn = nameEn,
                                            obd2StandardNameEn = obd2StandardNameEn,
                                            system = system,
                                            subSystem = subSystem,
                                            severity = severity,
                                            urgency = urgency,
                                            dtcCategory = dtcCategory,
                                            faultType = faultType,
                                            monitorType = monitorType,
                                            readinessMonitor = readinessMonitor,
                                            faultPersistence = faultPersistence,
                                            possibleCauses = possibleCauses,
                                            symptoms = symptoms,
                                            affectedComponents = affectedComponents,
                                            diagnosticSteps = diagnosticSteps,
                                            relatedCodes = relatedCodes,
                                            freezeFramePIDs = freezeFramePIDs,
                                            liveDataThresholds = liveDataThresholds,
                                            repairComplexity = repairComplexity,
                                            drivabilityImpact = drivabilityImpact,
                                            repairCostUSD = repairCostUSD,
                                            laborHoursEstimate = laborHoursEstimate,
                                            diyFriendly = diyFriendly,
                                            specialToolsRequired = specialToolsRequired,
                                            repairVerification = repairVerification,
                                            preventiveMaintenance = preventiveMaintenance,
                                            milBehavior = milBehavior,
                                            emissionsImpact = emissionsImpact,
                                            warrantyNote = warrantyNote,
                                            cascadeRisk = cascadeRisk,
                                            frequencyRank = frequencyRank,
                                            safeToResetWithoutRepair = safeToResetWithoutRepair,
                                            vehicleYearRange = vehicleYearRange,
                                            obd2Protocol = obd2Protocol,
                                            countryRegulation = countryRegulation,
                                            obd2DiagnosticMode = obd2DiagnosticMode,
                                            tsbBulletins = tsbBulletins
                                        )
                                    )
                                    totalCount++

                                    if (definitions.size >= 500) {
                                        db.dtcDefinitionDao().insertDefinitions(definitions.toList())
                                        definitions.clear()
                                        batchCount++
                                        Log.d("DtcDatabaseLoader", "Inserted batch $batchCount (total: $totalCount records loaded)")
                                    }
                                }
                            }
                            reader.endArray()

                            if (definitions.isNotEmpty()) {
                                db.dtcDefinitionDao().insertDefinitions(definitions)
                                Log.d("DtcDatabaseLoader", "Inserted final batch of ${definitions.size} records. Total records: $totalCount")
                            }
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                    reader.close()
                    stream.close()
                    Log.i("DtcDatabaseLoader", "Streaming load completed successfully.")
                } catch (e: Exception) {
                    Log.e("DtcDatabaseLoader", "Error during streaming database load: ${e.message}", e)
                }
            }
        }
    }

    private fun readRawValue(reader: JsonReader): String {
        val sb = StringBuilder()
        readRawValueInternal(reader, sb)
        return sb.toString()
    }

    private fun readRawValueInternal(reader: JsonReader, sb: java.lang.StringBuilder) {
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                sb.append("[")
                var first = true
                while (reader.hasNext()) {
                    if (!first) sb.append(",")
                    first = false
                    readRawValueInternal(reader, sb)
                }
                reader.endArray()
                sb.append("]")
            }
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                sb.append("{")
                var first = true
                while (reader.hasNext()) {
                    if (!first) sb.append(",")
                    first = false
                    val name = reader.nextName()
                    sb.append("\"").append(name).append("\":")
                    readRawValueInternal(reader, sb)
                }
                reader.endObject()
                sb.append("}")
            }
            JsonToken.STRING -> {
                val s = reader.nextString()
                sb.append("\"").append(escapeJsonString(s)).append("\"")
            }
            JsonToken.NUMBER -> {
                sb.append(reader.nextString())
            }
            JsonToken.BOOLEAN -> {
                sb.append(reader.nextBoolean())
            }
            JsonToken.NULL -> {
                reader.nextNull()
                sb.append("null")
            }
            else -> reader.skipValue()
        }
    }

    private fun escapeJsonString(s: String): String {
        val sb = java.lang.StringBuilder()
        for (i in 0 until s.length) {
            val ch = s[i]
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        val ss = "000" + java.lang.Integer.toHexString(ch.code)
                        sb.append("\\u").append(ss.substring(ss.length - 4))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun readStringOrRaw(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            else -> readRawValue(reader)
        }
    }

    private fun readStringOrNull(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING -> reader.nextString()
            else -> readRawValue(reader)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // KNOWLEDGE GRAPH LOADER — Populates child tables from JSON arrays
    // ══════════════════════════════════════════════════════════════════

    fun loadKnowledgeGraphIfEmpty() {
        loaderScope.launch {
            try {
                val kgDao = db.dtcKnowledgeGraphDao()
                if (kgDao.getSymptomsCount() > 0) {
                    // Clean up any accumulated duplicates from prior loads
                    // (autoGenerate PKs + REPLACE never conflict → duplicates pile up)
                    kgDao.deleteAllProcedures()
                    kgDao.deleteAllSymptoms()
                    kgDao.deleteAllCauses()
                    kgDao.deleteAllRelatedPids()
                    kgDao.deleteAllCoOccurrences()
                    kgDao.deleteAllRepairCosts()
                    Log.i("DtcDatabaseLoader", "Cleared stale knowledge graph data for clean reload")
                }

                Log.i("DtcDatabaseLoader", "Building DTC Knowledge Graph from JSON asset...")
                val stream = context.assets.open("dtc_database_es.json")
                val reader = JsonReader(InputStreamReader(stream, "UTF-8"))

                val symptoms = mutableListOf<com.elysium369.meet.data.local.entities.DtcSymptomEntity>()
                val causes = mutableListOf<com.elysium369.meet.data.local.entities.DtcCauseEntity>()
                val procedures = mutableListOf<com.elysium369.meet.data.local.entities.DtcProcedureEntity>()
                val relatedPids = mutableListOf<com.elysium369.meet.data.local.entities.DtcRelatedPidEntity>()
                val coOccurrences = mutableListOf<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity>()
                val repairCosts = mutableListOf<com.elysium369.meet.data.local.entities.DtcRepairCostEntity>()

                var totalRecords = 0

                reader.beginObject()
                while (reader.hasNext()) {
                    val rootKey = reader.nextName()
                    if (rootKey == "records") {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var code = ""
                            var manufacturer = "GENERIC"
                            var symptomsRaw: String? = null
                            var causesRaw: String? = null
                            var stepsRaw: String? = null
                            var relatedCodesRaw: String? = null
                            var freezeFrameRaw: String? = null
                            var costRaw: String? = null
                            var laborRaw: String? = null

                            while (reader.hasNext()) {
                                val key = reader.nextName()
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.skipValue()
                                    continue
                                }
                                when (key) {
                                    "code" -> code = readStringOrRaw(reader)
                                    "manufacturer" -> manufacturer = readStringOrRaw(reader)
                                    "symptoms" -> symptomsRaw = readStringOrNull(reader)
                                    "possibleCauses" -> causesRaw = readStringOrNull(reader)
                                    "diagnosticSteps" -> stepsRaw = readStringOrNull(reader)
                                    "relatedCodes" -> relatedCodesRaw = readStringOrNull(reader)
                                    "freezeFramePIDs" -> freezeFrameRaw = readStringOrNull(reader)
                                    "repairCostUSD" -> costRaw = readStringOrNull(reader)
                                    "laborHoursEstimate" -> laborRaw = readStringOrNull(reader)
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()

                            if (code.isEmpty()) continue
                            totalRecords++

                            // Parse Symptoms
                            parseSymptoms(code, manufacturer, symptomsRaw)?.let { symptoms.addAll(it) }

                            // Parse Causes
                            parseCauses(code, manufacturer, causesRaw)?.let { causes.addAll(it) }

                            // Parse Procedures
                            parseProcedures(code, manufacturer, stepsRaw)?.let { procedures.addAll(it) }

                            // Parse Co-Occurrences from relatedCodes
                            parseCoOccurrences(code, relatedCodesRaw)?.let { coOccurrences.addAll(it) }

                            // Parse Related PIDs from freezeFramePIDs
                            parseRelatedPids(code, manufacturer, freezeFrameRaw)?.let { relatedPids.addAll(it) }

                            // Parse Repair Costs
                            parseRepairCost(code, manufacturer, costRaw, laborRaw)?.let { repairCosts.add(it) }

                            // Batch insert every 500 records
                            if (totalRecords % 500 == 0) {
                                flushKnowledgeGraph(kgDao, symptoms, causes, procedures, relatedPids, coOccurrences, repairCosts)
                                Log.d("DtcDatabaseLoader", "KG batch flushed at record $totalRecords")
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                reader.close()
                stream.close()

                // Final flush
                flushKnowledgeGraph(kgDao, symptoms, causes, procedures, relatedPids, coOccurrences, repairCosts)

                // Rebuild the Full-Text Search (FTS) index
                Log.i("DtcDatabaseLoader", "Rebuilding Full-Text Search index...")
                kgDao.clearSearchIndex()
                kgDao.rebuildSearchIndex()
                Log.i("DtcDatabaseLoader", "✅ FTS Search Index rebuilt successfully")

                val totalKg = kgDao.getSymptomsCount() + kgDao.getCausesCount() + kgDao.getProceduresCount()
                Log.i("DtcDatabaseLoader", "✅ Knowledge Graph built: $totalKg child records from $totalRecords DTCs")
            } catch (e: Exception) {
                Log.e("DtcDatabaseLoader", "Error building knowledge graph", e)
            }
        }
    }

    private suspend fun flushKnowledgeGraph(
        kgDao: com.elysium369.meet.data.local.dao.DtcKnowledgeGraphDao,
        symptoms: MutableList<com.elysium369.meet.data.local.entities.DtcSymptomEntity>,
        causes: MutableList<com.elysium369.meet.data.local.entities.DtcCauseEntity>,
        procedures: MutableList<com.elysium369.meet.data.local.entities.DtcProcedureEntity>,
        relatedPids: MutableList<com.elysium369.meet.data.local.entities.DtcRelatedPidEntity>,
        coOccurrences: MutableList<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity>,
        repairCosts: MutableList<com.elysium369.meet.data.local.entities.DtcRepairCostEntity>
    ) {
        if (symptoms.isNotEmpty()) { kgDao.insertSymptoms(symptoms.toList()); symptoms.clear() }
        if (causes.isNotEmpty()) { kgDao.insertCauses(causes.toList()); causes.clear() }
        if (procedures.isNotEmpty()) { kgDao.insertProcedures(procedures.toList()); procedures.clear() }
        if (relatedPids.isNotEmpty()) { kgDao.insertRelatedPids(relatedPids.toList()); relatedPids.clear() }
        if (coOccurrences.isNotEmpty()) { kgDao.insertCoOccurrences(coOccurrences.toList()); coOccurrences.clear() }
        if (repairCosts.isNotEmpty()) { kgDao.insertRepairCosts(repairCosts.toList()); repairCosts.clear() }
    }

    private fun parseSymptoms(code: String, manufacturer: String, raw: String?): List<com.elysium369.meet.data.local.entities.DtcSymptomEntity>? {
        if (raw.isNullOrBlank()) return null
        val items = parseStringListFromRaw(raw)
        if (items.isEmpty()) return null
        return items.map { symptom ->
            com.elysium369.meet.data.local.entities.DtcSymptomEntity(
                dtcCode = code,
                manufacturer = manufacturer,
                symptomEs = symptom.trim(),
                probability = "media",
                isDriverNoticeable = true
            )
        }
    }

    private fun parseCauses(code: String, manufacturer: String, raw: String?): List<com.elysium369.meet.data.local.entities.DtcCauseEntity>? {
        if (raw.isNullOrBlank()) return null
        val items = raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) return null
        return items.mapIndexed { idx, cause ->
            val prob = when {
                idx == 0 -> "alta"
                idx == 1 -> "media"
                else -> "baja"
            }
            val lc = cause.lowercase()
            val isElec = lc.contains("circuito") || lc.contains("sensor") || lc.contains("voltaje") ||
                         lc.contains("eléctric") || lc.contains("electr") || lc.contains("conector") ||
                         lc.contains("arnés") || lc.contains("señal") || lc.contains("módulo")
            val isMech = lc.contains("desgast") || lc.contains("roto") || lc.contains("mecánic") ||
                         lc.contains("válvula") || lc.contains("junta") || lc.contains("fuga") ||
                         lc.contains("obstrui") || lc.contains("filtro") || lc.contains("bomba")
            com.elysium369.meet.data.local.entities.DtcCauseEntity(
                dtcCode = code,
                manufacturer = manufacturer,
                causeEs = cause,
                probability = prob,
                isElectronic = isElec,
                isMechanical = isMech
            )
        }
    }

    private fun parseProcedures(code: String, manufacturer: String, raw: String?): List<com.elysium369.meet.data.local.entities.DtcProcedureEntity>? {
        if (raw.isNullOrBlank()) return null
        val items = parseStringListFromRaw(raw)
        if (items.isEmpty()) return null

        // Deduplicate steps: normalize text for comparison, remove entries
        // where the description is a substring of the previous/next step
        val seen = mutableSetOf<String>()
        val uniqueItems = items.filter { step ->
            val normalized = step.replace(Regex("^(?i)(paso\\s*\\d+[:.]*|\\d+[:.]*)\\s*"), "")
                .trim().lowercase()
            if (normalized.isBlank() || normalized.length < 5) return@filter false
            seen.add(normalized)  // returns false if already present
        }

        if (uniqueItems.isEmpty()) return null

        return uniqueItems.mapIndexed { idx, step ->
            val cleanStr = step.replace(Regex("^(?i)(paso\\s*\\d+[:.]*|\\d+[:.]*)\\s*"), "")
            val parts = cleanStr.split(":", limit = 2)
            val title = if (parts.size > 1) parts[0].trim() else "Paso ${idx + 1}"
            val desc = if (parts.size > 1) parts[1].trim() else cleanStr.trim()
            val lc = cleanStr.lowercase()
            val icon = when {
                lc.contains("volt") || lc.contains("multímetro") || lc.contains("resistencia") -> "⚡"
                lc.contains("limpiar") || lc.contains("limpieza") || lc.contains("aerosol") -> "🧼"
                lc.contains("combustible") || lc.contains("gasolina") || lc.contains("presión") -> "⛽"
                lc.contains("escáner") || lc.contains("escanear") || lc.contains("meet") -> "📱"
                lc.contains("conducir") || lc.contains("ciclo") || lc.contains("ruta") -> "🚗"
                else -> "🔧"
            }
            val difficulty = when {
                idx < 2 -> "facil"
                idx < 4 -> "medio"
                else -> "dificil"
            }
            com.elysium369.meet.data.local.entities.DtcProcedureEntity(
                dtcCode = code,
                manufacturer = manufacturer,
                stepNumber = idx + 1,
                titleEs = title,
                descriptionEs = desc,
                estimatedMinutes = 10 + (idx * 5),
                difficulty = difficulty,
                icon = icon
            )
        }
    }

    private fun parseCoOccurrences(code: String, raw: String?): List<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity>? {
        if (raw.isNullOrBlank()) return null
        val items = parseStringListFromRaw(raw)
        if (items.isEmpty()) return null
        return items.filter { it.matches(Regex("[PCBU]\\d{4,5}")) }.map { relatedCode ->
            com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity(
                dtcCode = code,
                relatedDtcCode = relatedCode,
                correlationStrength = 0.5f
            )
        }
    }

    private fun parseRelatedPids(code: String, manufacturer: String, raw: String?): List<com.elysium369.meet.data.local.entities.DtcRelatedPidEntity>? {
        if (raw.isNullOrBlank()) return null
        val items = parseStringListFromRaw(raw)
        if (items.isEmpty()) return null
        return items.mapIndexed { idx, pidStr ->
            val parts = pidStr.split(Regex(" — | - "), limit = 2)
            val cmd = parts[0].trim()
            val name = if (parts.size > 1) parts[1].trim() else cmd
            com.elysium369.meet.data.local.entities.DtcRelatedPidEntity(
                dtcCode = code,
                manufacturer = manufacturer,
                pidCommand = cmd,
                pidNameEs = name,
                priority = idx
            )
        }
    }

    private fun parseRepairCost(code: String, manufacturer: String, costRaw: String?, laborRaw: String?): com.elysium369.meet.data.local.entities.DtcRepairCostEntity? {
        if (costRaw.isNullOrBlank()) return null
        try {
            val cleaned = costRaw.replace("$", "").replace(",", "").trim()
            val costParts = cleaned.split("-", "–").map { it.trim().toFloatOrNull() ?: 0f }
            val minCost = costParts.getOrNull(0) ?: return null
            val maxCost = costParts.getOrNull(1) ?: minCost
            if (minCost <= 0f && maxCost <= 0f) return null

            var laborHours: Float? = null
            if (!laborRaw.isNullOrBlank()) {
                val laborParts = laborRaw.replace("h", "").trim().split("-", "–").mapNotNull { it.trim().toFloatOrNull() }
                laborHours = laborParts.firstOrNull()
            }

            return com.elysium369.meet.data.local.entities.DtcRepairCostEntity(
                dtcCode = code,
                manufacturer = manufacturer,
                region = "US",
                minCostUsd = minCost,
                maxCostUsd = maxCost,
                laborHours = laborHours,
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parses a raw JSON value that can be either:
     * - A JSON array string: ["item1", "item2"]
     * - A pipe-separated string: "item1 | item2"
     * - A newline-separated string
     */
    private fun parseStringListFromRaw(raw: String): List<String> {
        val trimmed = raw.trim()
        return try {
            if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed)
                (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
            } else if (trimmed.contains("|")) {
                trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            } else if (trimmed.contains("\n")) {
                trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                listOf(trimmed).filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            if (trimmed.isNotEmpty()) listOf(trimmed) else emptyList()
        }
    }

    fun cancel() {
        loaderScope.cancel()
    }
}
