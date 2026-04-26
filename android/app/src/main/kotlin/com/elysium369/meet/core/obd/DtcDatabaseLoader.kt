package com.elysium369.meet.core.obd

import android.content.Context
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.InputStreamReader

class DtcDatabaseLoader(
    private val context: Context,
    private val db: MeetDatabase
) {
    fun loadIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {
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
                        definitions.add(
                            DtcDefinitionEntity(
                                code = obj.getString("code"),
                                descriptionEs = obj.getString("descriptionEs"),
                                descriptionEn = obj.getString("descriptionEn"),
                                system = obj.getString("system"),
                                severity = obj.getString("severity"),
                                possibleCauses = obj.getString("possibleCauses"),
                                urgency = obj.getString("urgency")
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
}
