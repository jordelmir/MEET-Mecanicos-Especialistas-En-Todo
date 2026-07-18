package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.domain.GenericVehicleSystemsAssetContract
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericVehicleSystemsAssetTest {
    private val minimumD3MeshCounts = mapOf(
        "intake_boost" to 150,
        "transmission_drivetrain" to 230,
        "suspension" to 120,
        "steering_brakes_wheels" to 260,
        "electrical_control" to 285,
        "lighting" to 80,
        "hvac" to 95,
        "passive_safety" to 65,
        "adas" to 65,
        "body" to 110,
        "wipers" to 45,
        "interior" to 70,
        "infotainment" to 70,
        "access" to 35,
        "hybrid_ev" to 125,
        "fluids" to 70,
        "hardware" to 60,
        "functional_overview" to 65
    )

    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path")
    ).firstOrNull(File::isFile) ?: error("Missing vehicle-system asset: $path")

    @Test
    fun `system atlas assets are bounded traceable GLB 2 files with every contracted family`() {
        var aggregateBytes = 0L

        GenericVehicleSystemsAssetContract.assets.forEach { definition ->
            val model = asset(definition.assetPath)
            val bytes = model.readBytes()
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            assertEquals("glTF", bytes.copyOfRange(0, 4).decodeToString())
            assertEquals(2, header.getInt(4))
            assertEquals(model.length(), header.getInt(8).toLong())
            assertTrue("${definition.id} must contain real mesh data", model.length() > 250_000L)
            assertTrue("${definition.id} must remain mobile-sized", model.length() < 12_000_000L)
            aggregateBytes += model.length()

            val jsonLength = header.getInt(12)
            assertEquals(0x4E4F534A, header.getInt(16))
            val gltfJson = bytes.copyOfRange(20, 20 + jsonLength).decodeToString()
            definition.requiredMeshKeys.forEach { meshKey ->
                assertTrue(
                    "${definition.id} is missing stable renderable family $meshKey",
                    gltfJson.contains("${GenericVehicleSystemsAssetContract.MESH_NODE_PREFIX}${meshKey}__")
                )
            }

            val manifest = asset(definition.manifestPath).readText()
            val manifestJson = Json.parseToJsonElement(manifest).jsonObject
            val expectedHash = Regex("\\\"sha256\\\"\\s*:\\s*\\\"([a-f0-9]{64})\\\"")
                .find(manifest)?.groupValues?.get(1)
                ?: error("${definition.id} manifest is missing sha256")
            val actualHash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

            assertEquals(expectedHash, actualHash)
            assertTrue(manifest.contains(GenericVehicleSystemsAssetContract.AUTHORITY))
            assertTrue(manifest.contains("D3_RECOGNIZABLE_INTERNALS"))
            assertTrue(
                "${definition.id} lost its D3 mechanical detail budget",
                manifestJson.getValue("meshCount").jsonPrimitive.content.toInt() >=
                    minimumD3MeshCounts.getValue(definition.id)
            )
            assertTrue(manifest.contains("ILLUSTRATIVE_PROPORTIONS_ONLY"))
            assertTrue(manifest.contains("\"oemClaim\": false"))
            assertTrue(manifest.contains("\"vehicleSpecificClaim\": false"))
        }

        assertTrue("The complete staged atlas must remain below 55 MB", aggregateBytes < 55_000_000L)
    }

    @Test
    fun `every selectable alias exists literally in the proprietary entity index`() {
        val index = Json.parseToJsonElement(
            asset("knowledge/proprietary/entity_index.json").readText()
        ).jsonObject
        val literalRecords = index.getValue("entities").jsonArray.mapTo(hashSetOf()) { element ->
            val entity = element.jsonObject
            entity.getValue("systemId").jsonPrimitive.content to
                entity.getValue("nameOriginal").jsonPrimitive.content
        }

        GenericVehicleSystemsAssetContract.assets.forEach { definition ->
            definition.bindings.flatMap { it.sourceAliases }.forEach { alias ->
                assertTrue(
                    "Missing literal proprietary record ${alias.systemId}: ${alias.literalName}",
                    alias.systemId to alias.literalName in literalRecords
                )
            }
        }
    }

    @Test
    fun `every proprietary system has a specialized visual renderer`() {
        val manifest = Json.parseToJsonElement(
            asset("knowledge/proprietary/manifest.json").readText()
        ).jsonObject
        val systemIds = manifest.getValue("systems").jsonArray.map { system ->
            system.jsonObject.getValue("id").jsonPrimitive.content
        }
        val handledOutsideSystemAtlas = setOf("structure", "engine")
        val unresolved = systemIds.filterNot { systemId ->
            systemId in handledOutsideSystemAtlas ||
                GenericVehicleSystemsAssetContract.assetForSystem(systemId) != null
        }

        assertTrue("Systems without a specialized 3D renderer: $unresolved", unresolved.isEmpty())
    }
}
