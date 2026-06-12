package com.elysium369.meet.core.obd

import android.content.Context
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
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
            if (db.dtcDefinitionDao().getCount() == 0) {
                try {
                    val stream = context.assets.open("dtc_database_es.json")
                    val reader = InputStreamReader(stream)
                    val jsonString = reader.readText()
                    reader.close()
                    stream.close()

                    val jsonArray = JSONArray(jsonString)
                    val definitions = mutableListOf<DtcDefinitionEntity>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val code = obj.optString("code", "")
                        if (code.isEmpty()) continue
                        definitions.add(
                            DtcDefinitionEntity(
                                code = code,
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun cancel() {
        loaderScope.cancel()
    }
}
