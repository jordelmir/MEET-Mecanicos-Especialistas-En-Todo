package com.elysium369.meet.core.obd

import android.content.Context
import org.json.JSONArray
import java.io.InputStreamReader

data class DtcInfo(
    val code: String,
    val descriptionEs: String,
    val descriptionEn: String,
    val possibleCauses: String,
    val severity: String
)

object DtcDatabaseHelper {
    private var database = mapOf<String, DtcInfo>()

    fun init(context: Context) {
        if (database.isNotEmpty()) return
        try {
            val inputStream = context.assets.open("dtc_database_es.json")
            val jsonString = InputStreamReader(inputStream).readText()
            val jsonArray = JSONArray(jsonString)
            val map = mutableMapOf<String, DtcInfo>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val code = obj.optString("code", "")
                if (code.isNotEmpty()) {
                    map[code.uppercase()] = DtcInfo(
                        code = code.uppercase(),
                        descriptionEs = obj.optString("descriptionEs", "Descripción no disponible"),
                        descriptionEn = obj.optString("descriptionEn", ""),
                        possibleCauses = obj.optString("possibleCauses", "Revisar manual del fabricante o realizar diagnóstico profundo."),
                        severity = obj.optString("severity", "LOW")
                    )
                }
            }
            database = map
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDtcInfo(code: String): DtcInfo? {
        return database[code.uppercase()]
    }
    
    fun searchDtc(query: String): List<DtcInfo> {
        val upperQuery = query.uppercase()
        return database.values.filter { it.code.contains(upperQuery) }
    }
}
