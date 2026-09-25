package com.elysium369.meet.agent.laya

import com.elysium369.meet.core.agent.laya.LayaSpatialSearchEngine
import com.elysium369.meet.ride.map.LayaEnhancedPlaceSearchProvider
import com.elysium369.meet.ride.map.RideMapDataSource
import com.elysium369.meet.ride.map.RidePlaceSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LayaSpatialSearchEngineTest {

    private val engine = LayaSpatialSearchEngine()

    @Test
    fun sanJoseQueryPrioritizesSanJoseCostaRica() = runTest {
        val results = engine.search("san jose", biasLatitude = 9.9333, biasLongitude = -84.0833, limit = 5)
        assertTrue("Results should not be empty", results.isNotEmpty())
        val top = results.first()
        assertEquals("San José", top.primaryLabel)
        assertTrue(top.secondaryLabel.contains("Costa Rica"))
        assertEquals(RideMapDataSource.CACHE, top.source)
    }

    @Test
    fun automotiveHubsAreRecognized() = runTest {
        val resultsUruca = engine.search("uruca", limit = 3)
        assertTrue(resultsUruca.isNotEmpty())
        assertTrue(resultsUruca.first().primaryLabel.contains("Uruca"))

        val resultsPasoAncho = engine.search("paso ancho", limit = 3)
        assertTrue(resultsPasoAncho.isNotEmpty())
        assertTrue(resultsPasoAncho.first().primaryLabel.contains("Paso Ancho"))
    }

    @Test
    fun externalRerankingPenalizesCaliforniaOverCostaRica() {
        val mockResults = listOf(
            RidePlaceSuggestion(
                providerId = "osm_california",
                primaryLabel = "San Jose",
                secondaryLabel = "Santa Clara County, California, United States",
                latitude = 37.3382,
                longitude = -121.8863,
                attribution = "OpenStreetMap",
                source = RideMapDataSource.NETWORK
            ),
            RidePlaceSuggestion(
                providerId = "osm_costa_rica",
                primaryLabel = "San José",
                secondaryLabel = "San José, Costa Rica",
                latitude = 9.9333,
                longitude = -84.0833,
                attribution = "OpenStreetMap",
                source = RideMapDataSource.NETWORK
            )
        )

        // When in Costa Rica
        val reranked = engine.rerankExternalResults(
            query = "san jose",
            externalResults = mockResults,
            biasLatitude = 9.9333,
            biasLongitude = -84.0833
        )

        assertEquals("San José Costa Rica must rank #1", "osm_costa_rica", reranked.first().providerId)
        assertEquals("San Jose California must be penalized to last", "osm_california", reranked.last().providerId)
    }

    @Test
    fun voiceQueryNaturalLanguageParsing() {
        // Emergency Tow in Cartago
        val towIntent = engine.parseVoiceQuery("Ocupo una grúa urgente en Cartago por favor")
        assertEquals("EMERGENCY_TOW", towIntent.recognizedIntent)
        assertEquals("AUTO_TOW", towIntent.serviceCategory)
        assertEquals("Cartago", towIntent.targetPlaceName)
        assertTrue(towIntent.confidence > 0.8)

        // Mechanics in Escazú
        val mechIntent = engine.parseVoiceQuery("Busco taller mecánico en Escazú para revisar frenos")
        assertEquals("SEARCH_SERVICE", mechIntent.recognizedIntent)
        assertEquals("AUTO_MECHANICAL", mechIntent.serviceCategory)
        assertEquals("Escazú", mechIntent.targetPlaceName)

        // Locksmith in Heredia
        val lockIntent = engine.parseVoiceQuery("Cerrajero urgente para abrir carro en Heredia")
        assertEquals("SEARCH_SERVICE", lockIntent.recognizedIntent)
        assertEquals("LOCKSMITH", lockIntent.serviceCategory)
        assertEquals("Heredia", lockIntent.targetPlaceName)

        // Parts finder in Alajuela
        val partsIntent = engine.parseVoiceQuery("Necesito comprar una batería nueva en Alajuela")
        assertEquals("PARTS_FINDER", partsIntent.recognizedIntent)
        assertEquals("PARTS_FINDER", partsIntent.serviceCategory)
        assertEquals("Alajuela", partsIntent.targetPlaceName)
    }

    @Test
    fun enhancedPlaceSearchProviderReturnsLocalGazetteerWhenDelegateFails() = runTest {
        val provider = LayaEnhancedPlaceSearchProvider(delegate = null, spatialEngine = engine)
        val results = provider.search("Santa Ana", biasLatitude = 9.9333, biasLongitude = -84.0833, limit = 5)
        assertTrue("Provider should return local gazetteer fallback", results.isNotEmpty())
        assertTrue(results.any { it.primaryLabel == "Santa Ana" })
    }
}
