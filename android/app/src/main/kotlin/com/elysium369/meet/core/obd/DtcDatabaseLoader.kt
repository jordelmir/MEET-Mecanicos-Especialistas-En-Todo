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

    fun cancel() {
        loaderScope.cancel()
    }
}
