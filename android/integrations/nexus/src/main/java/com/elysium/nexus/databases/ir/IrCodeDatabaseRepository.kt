package com.elysium.nexus.databases.ir

import android.content.Context
import org.json.JSONObject

data class IrBrand(
    val id: String,
    val name: String,
    val categories: List<IrCategory>
)

data class IrCategory(
    val type: String,
    val protocols: List<String>,
    val codesets: List<IrCodeset>
)

data class IrCodeset(
    val id: String,
    val name: String,
    val frequencyHz: Int,
    val protocol: String,
    val commands: Map<String, String>
)

class IrCodeDatabaseRepository(private val context: Context) {

    private var cachedBrands: List<IrBrand>? = null

    fun loadDatabase(): List<IrBrand> {
        cachedBrands?.let { return it }

        val jsonString = try {
            context.assets.open("ir_codes_db.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // Fallback default JSON string if asset not yet copied
            DEFAULT_IR_JSON
        }

        val brands = parseIrJson(jsonString)
        cachedBrands = brands
        return brands
    }

    fun findCodesetForBrand(brandName: String, categoryType: String = "TV"): IrCodeset? {
        val brands = loadDatabase()
        val match = brands.find { it.name.contains(brandName, ignoreCase = true) || it.id.equals(brandName, ignoreCase = true) }
            ?: return null

        val category = match.categories.find { it.type.equals(categoryType, ignoreCase = true) }
            ?: match.categories.firstOrNull() ?: return null

        return category.codesets.firstOrNull()
    }

    private fun parseIrJson(jsonStr: String): List<IrBrand> {
        val brandsList = mutableListOf<IrBrand>()
        val root = JSONObject(jsonStr)
        val brandsArray = root.optJSONArray("brands") ?: return emptyList()

        for (i in 0 until brandsArray.length()) {
            val brandObj = brandsArray.getJSONObject(i)
            val brandId = brandObj.getString("id")
            val brandName = brandObj.getString("name")

            val categoriesList = mutableListOf<IrCategory>()
            val categoriesArray = brandObj.optJSONArray("categories") ?: continue

            for (j in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.getJSONObject(j)
                val catType = catObj.getString("type")

                val protocolsList = mutableListOf<String>()
                val protoArr = catObj.optJSONArray("protocols")
                if (protoArr != null) {
                    for (k in 0 until protoArr.length()) {
                        protocolsList.add(protoArr.getString(k))
                    }
                }

                val codesetsList = mutableListOf<IrCodeset>()
                val codesetsArr = catObj.optJSONArray("codesets")
                if (codesetsArr != null) {
                    for (k in 0 until codesetsArr.length()) {
                        val csObj = codesetsArr.getJSONObject(k)
                        val csId = csObj.getString("id")
                        val csName = csObj.getString("name")
                        val csFreq = csObj.optInt("frequency_hz", 38000)
                        val csProto = csObj.getString("protocol")

                        val cmdMap = mutableMapOf<String, String>()
                        val cmdObj = csObj.optJSONObject("commands")
                        if (cmdObj != null) {
                            val keys = cmdObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                cmdMap[key] = cmdObj.getString(key)
                            }
                        }

                        codesetsList.add(IrCodeset(csId, csName, csFreq, csProto, cmdMap))
                    }
                }

                categoriesList.add(IrCategory(catType, protocolsList, codesetsList))
            }

            brandsList.add(IrBrand(brandId, brandName, categoriesList))
        }

        return brandsList
    }

    companion object {
        val DEFAULT_IR_JSON = """
        {
          "version": 1,
          "brands": [
            {
              "id": "samsung", "name": "Samsung",
              "categories": [{
                "type": "TV", "protocols": ["SAMSUNG32", "NEC"],
                "codesets": [{
                  "id": "samsung_tv_std", "name": "Samsung TV Standard", "frequency_hz": 37900, "protocol": "SAMSUNG32",
                  "commands": {"POWER": "0xE0E040BF", "VOL_UP": "0xE0E0E01F", "VOL_DOWN": "0xE0E0D02F", "MUTE": "0xE0E0F00F"}
                }]
              }]
            },
            {
              "id": "lg", "name": "LG Electronics",
              "categories": [{
                "type": "TV", "protocols": ["NEC"],
                "codesets": [{
                  "id": "lg_tv_std", "name": "LG TV Standard", "frequency_hz": 38000, "protocol": "NEC",
                  "commands": {"POWER": "0x20DF10EF", "VOL_UP": "0x20DF40BF", "VOL_DOWN": "0x20DFC03F", "MUTE": "0x20DF906F"}
                }]
              }]
            }
          ]
        }
        """.trimIndent()
    }
}
