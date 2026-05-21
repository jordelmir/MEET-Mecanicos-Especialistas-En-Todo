package com.elysium369.meet.core.sync

import android.util.Log
import com.elysium369.meet.data.local.dao.CustomPidDao
import com.elysium369.meet.data.local.dao.DtcDefinitionDao
import com.elysium369.meet.data.local.entities.CustomPidEntity
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RecallItem(
    val campaignNumber: String,
    val component: String,
    val summary: String,
    val consequence: String,
    val remedy: String,
    val notes: String? = null
)

object ElysiumCloudServices {

    private const val TAG = "ElysiumCloudServices"
    private const val NHTSA_RECALLS_URL = "https://api.nhtsa.gov/recalls/recallsByVehicle"
    
    // Remote URLs for definitions and custom pids
    private const val CLOUD_DTC_URL = "https://raw.githubusercontent.com/elysium-vanguard/dtc-database/main/dtcs.json"
    private const val CLOUD_PIDS_URL = "https://raw.githubusercontent.com/elysium-vanguard/custom-pids-repository/main/community_pids.json"

    /**
     * Fetch safety recalls from NHTSA API for a specific vehicle.
     */
    suspend fun fetchNhtsaRecalls(make: String, model: String, year: Int): List<RecallItem> = withContext(Dispatchers.IO) {
        val encodedMake = URLEncoder.encode(make, "UTF-8")
        val encodedModel = URLEncoder.encode(model, "UTF-8")
        val urlString = "$NHTSA_RECALLS_URL?make=$encodedMake&model=$encodedModel&modelYear=$year"
        
        Log.d(TAG, "Fetching recalls from: $urlString")
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val resultsArray = json.optJSONArray("Results")
                val list = mutableListOf<RecallItem>()
                
                if (resultsArray != null) {
                    for (i in 0 until resultsArray.length()) {
                        val obj = resultsArray.getJSONObject(i)
                        list.add(
                            RecallItem(
                                campaignNumber = obj.optString("NHTSACampaignNumber", "N/A"),
                                component = obj.optString("Component", "General"),
                                summary = obj.optString("Summary", "No hay descripción disponible."),
                                consequence = obj.optString("Conequence", "No especificado."),
                                remedy = obj.optString("Remedy", "Contactar concesionario oficial."),
                                notes = if (obj.has("Notes")) obj.optString("Notes") else null
                            )
                        )
                    }
                }
                Log.d(TAG, "Successfully fetched ${list.size} recalls from NHTSA")
                return@withContext list
            } else {
                Log.e(TAG, "NHTSA Recall API returned error code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from NHTSA Recall API", e)
        } finally {
            connection.disconnect()
        }
        return@withContext emptyList<RecallItem>()
    }

    /**
     * Sincroniza las definiciones avanzadas de DTC desde la nube a la base de datos local.
     */
    suspend fun syncDtcDefinitionsFromCloud(dtcDefinitionDao: DtcDefinitionDao): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting DTC definitions sync...")
        val url = URL(CLOUD_DTC_URL)
        val connection = url.openConnection() as HttpURLConnection
        var success = false
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }

            val list = mutableListOf<DtcDefinitionEntity>()
            if (responseText != null) {
                val json = JSONObject(responseText)
                val dtcArray = json.optJSONArray("dtcs")
                if (dtcArray != null) {
                    for (i in 0 until dtcArray.length()) {
                        val obj = dtcArray.getJSONObject(i)
                        list.add(
                            DtcDefinitionEntity(
                                code = obj.getString("code"),
                                manufacturer = obj.optString("manufacturer", "GENERIC"),
                                descriptionEs = obj.getString("descriptionEs"),
                                descriptionEn = obj.optString("descriptionEn", ""),
                                system = obj.optString("system", "Motor"),
                                severity = obj.optString("severity", "MEDIUM"),
                                possibleCauses = obj.optString("possibleCauses", ""),
                                urgency = obj.optString("urgency", "MODERADO")
                            )
                        )
                    }
                }
            } else {
                Log.i(TAG, "Unable to reach remote DTC sync server, applying pre-loaded elite diagnostic records")
                // Elite fallback offline datasets to enrich user's local Room database immediately
                list.addAll(getEliteFallbackDtcDefinitions())
            }

            if (list.isNotEmpty()) {
                dtcDefinitionDao.insertDefinitions(list)
                Log.d(TAG, "DTC Cloud Sync: Ingested ${list.size} definitions successfully.")
                success = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing DTC definitions, loading fallback records", e)
            try {
                dtcDefinitionDao.insertDefinitions(getEliteFallbackDtcDefinitions())
                success = true
            } catch (localEx: Exception) {
                Log.e(TAG, "Error inserting local fallback DTCs", localEx)
            }
        } finally {
            connection.disconnect()
        }
        return@withContext success
    }

    /**
     * Sincroniza el catálogo de PIDs personalizados de la comunidad a la base de datos local.
     */
    suspend fun syncCommunityCustomPIDs(customPidDao: CustomPidDao): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Community Custom PIDs sync...")
        val url = URL(CLOUD_PIDS_URL)
        val connection = url.openConnection() as HttpURLConnection
        var success = false
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }

            val list = mutableListOf<CustomPidEntity>()
            if (responseText != null) {
                val json = JSONObject(responseText)
                val pidsArray = json.optJSONArray("pids")
                if (pidsArray != null) {
                    for (i in 0 until pidsArray.length()) {
                        val obj = pidsArray.getJSONObject(i)
                        list.add(
                            CustomPidEntity(
                                id = obj.getString("id"),
                                userId = obj.optString("userId", "COMMUNITY"),
                                mode = obj.optString("mode", "01"),
                                pid = obj.getString("pid"),
                                name = obj.getString("name"),
                                unit = obj.optString("unit", ""),
                                formula = obj.getString("formula"),
                                minVal = obj.optDouble("minVal", 0.0).toFloat(),
                                maxVal = obj.optDouble("maxVal", 100.0).toFloat(),
                                warningThreshold = if (obj.has("warningThreshold")) obj.getDouble("warningThreshold").toFloat() else null,
                                color = obj.optString("color", "#00FFD4")
                            )
                        )
                    }
                }
            } else {
                Log.i(TAG, "Unable to reach remote PIDs server, applying pre-loaded advanced OBD formulas")
                // Elite fallback offline custom PIDs for advanced parameters
                list.addAll(getEliteFallbackCustomPIDs())
            }

            if (list.isNotEmpty()) {
                list.forEach { pid ->
                    customPidDao.insertCustomPid(pid)
                }
                Log.d(TAG, "PIDs Dynamic Ingestion: Merged ${list.size} advanced PIDs successfully.")
                success = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing custom PIDs, loading fallback records", e)
            try {
                getEliteFallbackCustomPIDs().forEach { pid ->
                    customPidDao.insertCustomPid(pid)
                }
                success = true
            } catch (localEx: Exception) {
                Log.e(TAG, "Error inserting local fallback PIDs", localEx)
            }
        } finally {
            connection.disconnect()
        }
        return@withContext success
    }

    private fun getEliteFallbackDtcDefinitions(): List<DtcDefinitionEntity> {
        return listOf(
            DtcDefinitionEntity(
                code = "P0171",
                manufacturer = "GENERIC",
                descriptionEs = "Mezcla de Combustible Demasiado Pobre (Banco 1)",
                descriptionEn = "System Too Lean (Bank 1)",
                system = "Inyección de Combustible",
                severity = "HIGH",
                possibleCauses = "Fuga de vacío, falla en el sensor MAF, sensor de oxígeno defectuoso, inyectores obstruidos.",
                urgency = "ALTA"
            ),
            DtcDefinitionEntity(
                code = "P0300",
                manufacturer = "GENERIC",
                descriptionEs = "Fallas de Encendido Múltiples/Aleatorias Detectadas",
                descriptionEn = "Random/Multiple Cylinder Misfire Detected",
                system = "Ignición",
                severity = "CRITICAL",
                possibleCauses = "Bujías desgastadas, bobinas de encendido defectuosas, cables de bujías dañados, presión de combustible baja.",
                urgency = "INMEDIATA"
            ),
            DtcDefinitionEntity(
                code = "P0420",
                manufacturer = "GENERIC",
                descriptionEs = "Eficiencia del Sistema del Catalizador por Debajo del Umbral (Banco 1)",
                descriptionEn = "Catalyst System Efficiency Below Threshold (Bank 1)",
                system = "Control de Emisiones",
                severity = "MEDIUM",
                possibleCauses = "Convertidor catalítico dañado, sensor de oxígeno defectuoso antes/después, fugas de escape.",
                urgency = "MODERADO"
            ),
            DtcDefinitionEntity(
                code = "P0113",
                manufacturer = "GENERIC",
                descriptionEs = "Entrada Alta del Circuito de Temperatura del Aire de Admisión (IAT)",
                descriptionEn = "Intake Air Temperature Sensor 1 Circuit High",
                system = "Admisión / Sensores",
                severity = "MEDIUM",
                possibleCauses = "Sensor IAT defectuoso, arnés de cableado abierto o en cortocircuito.",
                urgency = "MODERADO"
            )
        )
    }

    private fun getEliteFallbackCustomPIDs(): List<CustomPidEntity> {
        return listOf(
            CustomPidEntity(
                id = "pid_trans_temp",
                userId = "COMMUNITY",
                mode = "22",
                pid = "1940",
                name = "Temperatura de Aceite de Transmisión",
                unit = "°C",
                formula = "A - 40",
                minVal = -40f,
                maxVal = 150f,
                warningThreshold = 100f,
                color = "#FF3333"
            ),
            CustomPidEntity(
                id = "pid_hybrid_soc",
                userId = "COMMUNITY",
                mode = "22",
                pid = "090D",
                name = "Estado de Carga de Batería Híbrida (SOC)",
                unit = "%",
                formula = "A * 0.392",
                minVal = 0f,
                maxVal = 100f,
                warningThreshold = 20f,
                color = "#00FFD4"
            ),
            CustomPidEntity(
                id = "pid_oil_life",
                userId = "COMMUNITY",
                mode = "22",
                pid = "1152",
                name = "Vida útil remanente del aceite de motor",
                unit = "%",
                formula = "A",
                minVal = 0f,
                maxVal = 100f,
                warningThreshold = 15f,
                color = "#FFFF33"
            ),
            CustomPidEntity(
                id = "pid_egt_temp",
                userId = "COMMUNITY",
                mode = "01",
                pid = "78",
                name = "Temperatura de Gas de Escape (EGT)",
                unit = "°C",
                formula = "((A*256)+B)/10 - 40",
                minVal = -40f,
                maxVal = 1000f,
                warningThreshold = 850f,
                color = "#FF9900"
            )
        )
    }
}
