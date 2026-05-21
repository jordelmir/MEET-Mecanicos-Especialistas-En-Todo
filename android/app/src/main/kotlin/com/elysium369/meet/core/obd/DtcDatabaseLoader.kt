package com.elysium369.meet.core.obd

import android.content.Context
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.data.supabase.RemoteDtcDefinition
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.InputStreamReader

class DtcDatabaseLoader(
    private val context: Context,
    private val db: MeetDatabase
) {
    private val loaderScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun loadIfEmpty() {
        loaderScope.launch {
            if (db.dtcDefinitionDao().getCount() < 2000) {
                try {
                    val stream = context.assets.open("dtc_database_es.json")
                    val reader = InputStreamReader(stream)
                    val jsonString = reader.readText()
                    reader.close()
                    stream.close()

                    android.util.Log.d("DtcLoader", "Starting DTC database load from JSON...")
                    val jsonArray = JSONArray(jsonString)
                    val definitions = mutableListOf<DtcDefinitionEntity>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val code = obj.optString("code", "")
                        if (code.isEmpty()) continue
                        definitions.add(
                            DtcDefinitionEntity(
                                code = code,
                                manufacturer = obj.optString("manufacturer", "GENERIC"),
                                descriptionEs = obj.optString("descriptionEs", "Sin descripción"),
                                descriptionEn = obj.optString("descriptionEn", "No description"),
                                system = obj.optString("system", "GENERAL"),
                                severity = obj.optString("severity", "LOW"),
                                possibleCauses = obj.optString("possibleCauses", "Consultar manual"),
                                urgency = obj.optString("urgency", "LOW")
                            )
                        )
                    }
                    db.dtcDefinitionDao().insertDefinitions(definitions)
                    android.util.Log.d("DtcLoader", "Successfully loaded ${definitions.size} DTC definitions into Room.")
                } catch (e: Exception) {
                    android.util.Log.e("DtcLoader", "Error loading DTC database", e)
                }
            } else {
                android.util.Log.d("DtcLoader", "DTC database already contains ${db.dtcDefinitionDao().getCount()} entries. Skipping load.")
            }
        }
    }

    suspend fun syncDtcDefinitionsFromCloud() {
        try {
            android.util.Log.i("DtcLoader", "Syncing DTC definitions from Supabase cloud...")
            val cloudDefs = SupabaseManager.client.postgrest["dtc_definitions"]
                .select()
                .decodeList<RemoteDtcDefinition>()
            
            if (cloudDefs.isNotEmpty()) {
                val localEntities = cloudDefs.map { remote ->
                    DtcDefinitionEntity(
                        code = remote.code,
                        manufacturer = remote.manufacturer,
                        descriptionEs = remote.description_es,
                        descriptionEn = remote.description_en,
                        system = remote.system,
                        severity = remote.severity,
                        possibleCauses = remote.possible_causes,
                        urgency = remote.urgency
                    )
                }
                db.dtcDefinitionDao().insertDefinitions(localEntities)
                android.util.Log.i("DtcLoader", "Successfully synced ${localEntities.size} DTC definitions from cloud.")
            }
        } catch (e: Exception) {
            android.util.Log.e("DtcLoader", "Failed to sync DTC definitions from cloud", e)
        }
    }

    fun cancel() {
        loaderScope.cancel()
    }
}
