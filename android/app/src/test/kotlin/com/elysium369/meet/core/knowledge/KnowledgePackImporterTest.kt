package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgePackImporterTest {

    private val importer = KnowledgePackImporter()

    @Test
    fun `parses well-formed pack`() {
        val raw = """
            {
              "packId": "pack_test",
              "title": "Test Pack",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": { "tier": "A_OWNED_CREATED", "licenseType": "OWNED_CONTENT" },
              "nodes": [
                { "id": "n_a", "type": "Component", "name": "A" },
                { "id": "n_b", "type": "Component", "name": "B" }
              ],
              "edges": [
                { "id": "e_1", "from": "n_a", "to": "n_b", "type": "POWERS" }
              ]
            }
        """.trimIndent()
        val pack = importer.parse(raw).getOrThrow()
        assertEquals("pack_test", pack.packId)
        assertEquals(2, pack.nodes.size)
        assertEquals(1, pack.edges.size)
    }

    @Test
    fun `rejects source tier H`() {
        val raw = """
            {
              "packId": "pack_h",
              "title": "H Pack",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": { "tier": "H_REJECTED_UNKNOWN_LICENSE", "licenseType": "UNKNOWN" },
              "nodes": [{ "id": "n_x", "type": "Component", "name": "X" }]
            }
        """.trimIndent()
        val result = importer.importPack(raw)
        assertTrue(result is PackImportResult.Rejected)
        assertTrue((result as PackImportResult.Rejected).reason.contains("H is rejected"))
    }

    @Test
    fun `rejects edges referencing missing nodes`() {
        val raw = """
            {
              "packId": "pack_e",
              "title": "Edge Pack",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": { "tier": "A_OWNED_CREATED", "licenseType": "OWNED_CONTENT" },
              "nodes": [{ "id": "n_a", "type": "Component", "name": "A" }],
              "edges": [
                { "id": "e_1", "from": "n_a", "to": "n_ghost", "type": "POWERS" }
              ]
            }
        """.trimIndent()
        val result = importer.importPack(raw)
        assertTrue(result is PackImportResult.Success)
        val s = result as PackImportResult.Success
        assertEquals(1, s.nodesAccepted)
        assertEquals(0, s.edgesAccepted)
        assertTrue(s.rejectedEdgeIds.contains("e_1"))
    }

    @Test
    fun `rejects nodes with invalid id format`() {
        val raw = """
            {
              "packId": "pack_id",
              "title": "ID Pack",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": { "tier": "A_OWNED_CREATED", "licenseType": "OWNED_CONTENT" },
              "nodes": [
                { "id": "BAD-ID-1", "type": "Component", "name": "Bad" },
                { "id": "n_ok", "type": "Component", "name": "OK" }
              ]
            }
        """.trimIndent()
        val result = importer.importPack(raw)
        assertTrue(result is PackImportResult.Success)
        val s = result as PackImportResult.Success
        assertEquals(1, s.nodesAccepted)
        assertTrue(s.rejectedNodeIds.contains("BAD-ID-1"))
    }

    @Test
    fun `rejects duplicate node ids`() {
        val raw = """
            {
              "packId": "pack_dup",
              "title": "Dup Pack",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": { "tier": "A_OWNED_CREATED", "licenseType": "OWNED_CONTENT" },
              "nodes": [
                { "id": "n_a", "type": "Component", "name": "A1",
                  "validationStatus": "VALIDATED" },
                { "id": "n_b", "type": "Component", "name": "B",
                  "validationStatus": "VALIDATED" },
                { "id": "n_a", "type": "Component", "name": "DupA",
                  "validationStatus": "VALIDATED" }
              ]
            }
        """.trimIndent()
        val result = importer.importPack(raw)
        assertTrue("Result was: $result", result is PackImportResult.Success)
        val s = result as PackImportResult.Success
        // First n_a accepted, second rejected. n_b accepted.
        assertEquals(1, s.rejectedNodeIds.size)
        assertTrue(s.rejectedNodeIds.contains("n_a"))
    }

    @Test
    fun `rejects non-redistributable content without tier G`() {
        val raw = """
            {
              "packId": "pack_nd",
              "title": "NoDistrib",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": {
                "tier": "D_VERIFIED_MECHANIC",
                "licenseType": "PAID",
                "redistributionAllowed": false
              },
              "nodes": [{ "id": "n_a", "type": "Component", "name": "A" }]
            }
        """.trimIndent()
        val result = importer.importPack(raw)
        assertTrue(result is PackImportResult.Rejected)
    }

    @Test
    fun `accepts G_EXTERNAL_LINK_ONLY even when not redistributable`() {
        val raw = """
            {
              "packId": "pack_g",
              "title": "ExternalLink",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": {
                "tier": "G_EXTERNAL_LINK_ONLY",
                "licenseType": "OEM_PORTAL",
                "redistributionAllowed": false
              },
              "nodes": [{ "id": "n_a", "type": "Component", "name": "A" }]
            }
        """.trimIndent()
        val result = importer.importPack(raw)
        assertTrue(result is PackImportResult.Success)
    }

    @Test
    fun `rejects pack with no recognized validation status`() {
        val raw = """
            {
              "packId": "pack_v",
              "title": "Validation Pack",
              "domain": "test",
              "schemaVersion": 1,
              "packVersion": "1.0.0",
              "sourcePolicy": { "tier": "A_OWNED_CREATED", "licenseType": "OWNED_CONTENT" },
              "nodes": [
                { "id": "n_a", "type": "Component", "name": "A",
                  "validationStatus": "REJECTED" }
              ]
            }
        """.trimIndent()
        val result = importer.importPack(raw)
        assertTrue(result is PackImportResult.Rejected)
    }
}
