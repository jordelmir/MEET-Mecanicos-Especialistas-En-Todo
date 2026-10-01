package com.elysium.nexus.databases.ir

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class IrCodeDatabaseRepositoryTest {

    private val sampleJson = """
    {
      "version": "2.0.0",
      "brands": {
        "Samsung": {
          "models": {
            "tv_generic": {
              "protocol": "Samsung32",
              "device_address": "0x07",
              "commands": {
                "power": "0x02"
              }
            }
          }
        },
        "LG": {
          "models": {
            "tv_generic": {
              "protocol": "NEC",
              "device_address": "0x04",
              "commands": {
                "power": "0x08"
              }
            }
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun `parseDatabaseJson parses brands correctly`() {
        val json = JSONObject(sampleJson)
        val brandsObj = json.getJSONObject("brands")

        assertTrue(brandsObj.has("Samsung"))
        assertTrue(brandsObj.has("LG"))

        val samsungObj = brandsObj.getJSONObject("Samsung")
        val samsungModels = samsungObj.getJSONObject("models")
        val tvGeneric = samsungModels.getJSONObject("tv_generic")

        assertEquals("Samsung32", tvGeneric.getString("protocol"))
        assertEquals("0x07", tvGeneric.getString("device_address"))
        assertEquals("0x02", tvGeneric.getJSONObject("commands").getString("power"))
    }
}
