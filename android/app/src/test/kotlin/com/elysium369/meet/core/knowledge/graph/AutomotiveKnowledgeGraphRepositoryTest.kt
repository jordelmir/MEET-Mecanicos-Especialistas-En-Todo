package com.elysium369.meet.core.knowledge.graph

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutomotiveKnowledgeGraphRepositoryTest {
    @Before
    fun reclaimHeapBeforeLargeAssetTest() = reclaimLargeFixtureHeap()

    @After
    fun reclaimHeapAfterLargeAssetTest() = reclaimLargeFixtureHeap()

    @Test
    fun `real graph validates its fixed corpus identity hash and complete statistics`() {
        val graph = AutomotiveKnowledgeGraphParser.decode(graphAsset().readText())

        assertEquals(1, graph.schemaVersion)
        assertEquals(EXPECTED_GRAPH_HASH, graph.contentSha256)
        assertEquals(EXPECTED_CORPUS_HASH, graph.sourceCorpusHash)
        assertEquals(EXPECTED_CORPUS_ID, graph.sourceInputs.corpusId)
        assertEquals(EXPECTED_CORPUS_VERSION, graph.sourceInputs.corpusVersion)
        assertEquals(EXPECTED_CORPUS_HASH, graph.sourceInputs.corpusManifestSha256)
        assertEquals(5_446, graph.nodes.size)
        assertEquals(5_411, graph.edges.size)
        assertEquals(EXPECTED_STATISTICS, graph.statistics)

        val qualifiedRefs = graph.nodes.flatMap(KnowledgeNode::sourceRefs).toSet()
        assertEquals(74_648, qualifiedRefs.size)
        assertEquals(74_638, qualifiedRefs.map(SourceRef::blockId).toSet().size)
        assertTrue(qualifiedRefs.all(SourceRef::isComplete))
    }

    @Test
    fun `repository exposes stable graph catalog dtc profile rule and component lookups`() {
        val repository = repositoryForRealAsset()

        assertEquals("P0230", repository.dtc(" p0230 ")?.canonicalKey)
        assertEquals(
            listOf("adas", "corpus_system_adas"),
            repository.nodesByCanonicalKey("adas").map(KnowledgeNode::id)
        )
        assertEquals(
            "Panel cortafuego / firewall",
            repository.nodeForCatalogEntity("document_16-o000016-panel-cortafuego-firewall")?.label
        )
        assertEquals(
            "Hyundai",
            repository.profile("hyundai_accent_verna_2005_1_6_at")?.make
        )
        assertEquals(
            VehicleApplicabilityState.NOT_DOCUMENTED,
            repository.applicabilityRule(
                "hyundai_accent_verna_2005_1_6_at",
                "maf_sensor"
            )?.state
        )

        val components = repository.components("corpus_system_engine")
        assertEquals(368, components.size)
        assertTrue(components.all { it.type == KnowledgeNodeType.COMPONENT })
        assertEquals(components.map(KnowledgeNode::id).sorted(), components.map(KnowledgeNode::id))
    }

    @Test
    fun `edge indexes and neighbors preserve evidence direction and semantic sequence`() {
        val repository = repositoryForRealAsset()

        val first = repository.outgoingEdges(
            "dtc_P0230",
            setOf(KnowledgeEdgeType.HAS_DIAGNOSTIC_TEST)
        ).single()
        assertEquals("edge_p0230_first_test", first.id)
        assertTrue(first.evidenceRequired.contains("freeze_frame"))
        assertEquals(first, repository.incomingEdges("test_p0230_capture_context").single())

        val neighbors = repository.neighbors("test_p0230_capture_context")
        assertEquals(
            listOf("edge_p0230_step_1", "edge_p0230_first_test"),
            neighbors.map { it.edge.id }
        )
        assertEquals(GraphDirection.OUTGOING, neighbors[0].direction)
        assertEquals(GraphDirection.INCOMING, neighbors[1].direction)
        assertEquals("test_p0230_power_and_ground", neighbors[0].node.id)
        assertEquals("dtc_P0230", neighbors[1].node.id)
    }

    @Test
    fun `tampered claimed content hash is rejected`() {
        val raw = graphAsset().readText().replace(
            EXPECTED_GRAPH_HASH,
            "0".repeat(64)
        )

        assertThrows(AutomotiveKnowledgeGraphValidationException::class.java) {
            AutomotiveKnowledgeGraphParser.decode(raw)
        }
    }

    @Test
    fun `public parser rejects a consistently rehashed payload outside the pinned release`() {
        val (raw, recomputedHash) = mutateAndRehash { root ->
            val nodes = root.getValue("nodes").jsonArray.toMutableList()
            nodes[0] = JsonObject(nodes[0].jsonObject.toMutableMap().apply {
                this["label"] = JsonPrimitive("Semantically valid but not release-authorized")
            })
            root["nodes"] = JsonArray(nodes)
        }

        assertFalse(recomputedHash == EXPECTED_GRAPH_HASH)
        assertThrows(AutomotiveKnowledgeGraphValidationException::class.java) {
            AutomotiveKnowledgeGraphParser.decode(raw)
        }
    }

    @Test
    fun `normalized DTC ambiguity is rejected before index creation`() {
        val (raw, recomputedHash) = mutateAndRehash { root ->
            val nodes = root.getValue("nodes").jsonArray.toMutableList()
            val dtc = nodes.first {
                it.jsonObject["id"]?.jsonPrimitive?.content == "dtc_P0230"
            }.jsonObject
            nodes.add(JsonObject(dtc.toMutableMap().apply {
                this["id"] = JsonPrimitive("dtc_p0230_duplicate")
                this["canonicalKey"] = JsonPrimitive("p0230")
            }))
            root["nodes"] = JsonArray(nodes)
        }

        val error = assertThrows(AutomotiveKnowledgeGraphValidationException::class.java) {
            AutomotiveKnowledgeGraphParser.decodeWithExpectedContentSha256(raw, recomputedHash)
        }
        assertTrue(error.message.orEmpty().contains("DTC", ignoreCase = true))
    }

    @Test
    fun `rehashed structural mutations still reject orphan duplicate and false statistics`() {
        assertMutationRejected { root ->
            val edges = root.getValue("edges").jsonArray.toMutableList()
            edges[0] = JsonObject(edges[0].jsonObject.toMutableMap().apply {
                this["to"] = JsonPrimitive("missing_node")
            })
            root["edges"] = JsonArray(edges)
        }
        assertMutationRejected { root ->
            val nodes = root.getValue("nodes").jsonArray.toMutableList()
            nodes.add(nodes.first())
            root["nodes"] = JsonArray(nodes)
        }
        assertMutationRejected { root ->
            val statistics = root.getValue("statistics").jsonObject.toMutableMap()
            statistics["nodeCount"] = JsonPrimitive(statistics.getValue("nodeCount").jsonPrimitive.int + 1)
            root["statistics"] = JsonObject(statistics)
        }
    }

    @Test
    fun `source references and reviewed authority remain fail closed after valid rehash`() {
        assertMutationRejected { root ->
            val nodes = root.getValue("nodes").jsonArray.toMutableList()
            val index = nodes.indexOfFirst { it.jsonObject["sourceRefs"]?.jsonArray?.isNotEmpty() == true }
            val node = nodes[index].jsonObject.toMutableMap()
            val refs = node.getValue("sourceRefs").jsonArray.toMutableList()
            refs[0] = JsonObject(refs[0].jsonObject.toMutableMap().apply {
                this["textHash"] = JsonPrimitive("incomplete")
            })
            node["sourceRefs"] = JsonArray(refs)
            nodes[index] = JsonObject(node)
            root["nodes"] = JsonArray(nodes)
        }
        assertMutationRejected { root ->
            val nodes = root.getValue("nodes").jsonArray.toMutableList()
            val index = nodes.indexOfFirst { it.jsonObject["sourceRefs"]?.jsonArray?.isNotEmpty() == true }
            val node = nodes[index].jsonObject.toMutableMap()
            val refs = node.getValue("sourceRefs").jsonArray.toMutableList()
            refs.add(refs.first())
            node["sourceRefs"] = JsonArray(refs)
            nodes[index] = JsonObject(node)
            root["nodes"] = JsonArray(nodes)
        }
        assertMutationRejected { root ->
            val edges = root.getValue("edges").jsonArray.toMutableList()
            val index = edges.indexOfFirst { it.jsonObject["id"]?.jsonPrimitive?.content == "edge_p0230_first_test" }
            edges[index] = JsonObject(edges[index].jsonObject.toMutableMap().apply {
                this["applicability"] = JsonPrimitive("CONFIRMED")
            })
            root["edges"] = JsonArray(edges)
        }
        assertMutationRejected { root ->
            val sourceInputs = root.getValue("sourceInputs").jsonObject.toMutableMap()
            val packs = sourceInputs.getValue("curatedPacks").jsonArray.toMutableList()
            packs[0] = JsonObject(packs[0].jsonObject.toMutableMap().apply {
                this["reviewState"] = JsonPrimitive("REVIEWED")
            })
            sourceInputs["curatedPacks"] = JsonArray(packs)
            root["sourceInputs"] = JsonObject(sourceInputs)
        }
    }

    @Test
    fun `corrupt product repository reports invalid and returns closed values`() {
        val repository = AutomotiveKnowledgeGraphRepository { "not-json".encodeToByteArray() }

        assertEquals(GraphIntegrityStatus.INVALID, repository.integrityStatus())
        assertNull(repository.node("anything"))
        assertNull(repository.dtc("P0230"))
        assertNull(repository.profile("anything"))
        assertNull(repository.applicabilityRule("anything", "anything"))
        assertNull(repository.observedEvidence("anything"))
        assertTrue(repository.neighbors("anything").isEmpty())
        assertTrue(repository.components("anything").isEmpty())
    }

    @Test
    fun `asset budget remains lazy and loads exactly once`() {
        val bytes = graphAsset().readBytes()
        val loadCount = AtomicInteger()
        val repository = AutomotiveKnowledgeGraphRepository {
            loadCount.incrementAndGet()
            bytes
        }

        assertEquals(0, loadCount.get())
        assertEquals(GraphLoadMetrics.EMPTY, repository.loadMetrics())
        assertEquals("dtc_P0230", repository.node("dtc_P0230")?.id)
        assertEquals("dtc_P0230", repository.node("dtc_P0230")?.id)
        assertEquals(GraphIntegrityStatus.VALID, repository.integrityStatus())
        assertEquals(1, loadCount.get())

        val metrics = repository.loadMetrics()
        println("AUTOMOTIVE_GRAPH_LOAD_METRICS=$metrics")
        assertEquals(18_821_087L, metrics.assetByteCount)
        assertTrue(metrics.assetByteCount <= 32L * 1024L * 1024L)
        assertEquals(bytes.size.toLong(), metrics.assetByteCount)
        assertEquals(5_446, metrics.nodeCount)
        assertEquals(5_411, metrics.edgeCount)
        assertEquals(74_648, metrics.sourceRefCount)
        assertTrue(metrics.parseDurationMillis >= 0L)
    }

    private fun repositoryForRealAsset(): AutomotiveKnowledgeGraphRepository {
        val bytes = graphAsset().readBytes()
        return AutomotiveKnowledgeGraphRepository { bytes }
    }

    private fun graphAsset(): File = listOf(
        File("src/main/assets/$GRAPH_ASSET_PATH"),
        File("app/src/main/assets/$GRAPH_ASSET_PATH"),
        File("android/app/src/main/assets/$GRAPH_ASSET_PATH")
    ).firstOrNull(File::isFile) ?: error("Missing graph asset: $GRAPH_ASSET_PATH")

    private fun mutateAndRehash(
        mutate: (MutableMap<String, JsonElement>) -> Unit
    ): Pair<String, String> {
        val root = Json.parseToJsonElement(graphAsset().readText()).jsonObject.toMutableMap()
        mutate(root)
        root.remove("contentSha256")
        val hash = sha256(canonical(JsonObject(root)).encodeToByteArray())
        root["contentSha256"] = JsonPrimitive(hash)
        return JsonObject(root).toString() to hash
    }

    private fun assertMutationRejected(mutate: (MutableMap<String, JsonElement>) -> Unit) {
        val (raw, recomputedHash) = mutateAndRehash(mutate)
        assertThrows(AutomotiveKnowledgeGraphValidationException::class.java) {
            AutomotiveKnowledgeGraphParser.decodeWithExpectedContentSha256(raw, recomputedHash)
        }
    }

    private fun canonical(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy(Map.Entry<String, JsonElement>::key)
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                "${JsonPrimitive(key)}:${canonical(value)}"
            }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonical(it)
        }
        else -> element.toString()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun reclaimLargeFixtureHeap() {
        System.gc()
        System.runFinalization()
    }

    companion object {
        private const val GRAPH_ASSET_PATH = "knowledge/graph/automotive_knowledge_graph.json"
        private const val EXPECTED_GRAPH_HASH =
            "2617bfa199a0e5b88f9ccb03ed46741d657f7f9fe00ba8aefe8f17926d4ab466"
        private const val EXPECTED_CORPUS_ID = "meet_owner_proprietary_parts_corpus"
        private const val EXPECTED_CORPUS_VERSION = "1.0.0"
        private const val EXPECTED_CORPUS_HASH =
            "7a4a2f2f328bf422ea1c4d987f88eb093e664d6cf4e53609282506d4261d960f"

        private val EXPECTED_STATISTICS = GraphStatistics(
            sourceBlockCount = 74_648,
            qualifiedSourceRefCount = 74_648,
            bareSourceBlockIdCount = 74_638,
            corpusSystemNodeCount = 26,
            corpusSectionNodeCount = 347,
            entityNodeCount = 5_050,
            corpusComponentNodeCount = 4_753,
            corpusRealCaseNodeCount = 297,
            totalSystemNodeCount = 28,
            totalSectionNodeCount = 347,
            totalComponentNodeCount = 4_759,
            totalSourceBlockNodeCount = 297,
            baseNodeCount = 5_423,
            structuralEdgeCount = 5_397,
            curatedNodeCount = 23,
            curatedEdgeCount = 14,
            nodeCount = 5_446,
            edgeCount = 5_411,
            profileCount = 1,
            applicabilityRuleCount = 8,
            observedEvidenceCount = 0
        )
    }
}
