package com.elysium369.meet.safety

import com.elysium369.meet.core.geo.runtime.MapStyleLoadPolicy
import org.junit.Assert.*
import org.junit.Test

class SafetyMapLoadResilienceTest {
    @Test fun failureOrTimeoutUsesFallbackThenShowsUnavailable() {
        val initial = MapStyleLoadPolicy.candidates("https://primary/style", "https://fallback/style")
        assertEquals(MapStyleLoadPolicy.Phase.LOADING, initial.phase)
        val fallback = initial.failed()
        assertEquals("https://fallback/style", fallback.currentUrl)
        assertEquals(MapStyleLoadPolicy.Phase.LOADING, fallback.phase)
        assertEquals(MapStyleLoadPolicy.Phase.UNAVAILABLE, fallback.failed().phase)
    }
    @Test fun loadedStyleDismissesLoadingAndRetryRestartsPrimary() {
        val initial = MapStyleLoadPolicy.candidates("https://primary/style", "https://fallback/style")
        assertEquals(MapStyleLoadPolicy.Phase.READY, initial.loaded().phase)
        val retried = initial.failed().failed().retry()
        assertEquals(initial, retried)
    }
    @Test fun blankAndDuplicateConfigurationCannotLoopForever() {
        assertEquals(MapStyleLoadPolicy.Phase.UNAVAILABLE, MapStyleLoadPolicy.candidates("", " ").phase)
        val single = MapStyleLoadPolicy.candidates(" https://only/style ", "https://only/style")
        assertEquals(1, single.urls.size)
        assertEquals(MapStyleLoadPolicy.Phase.UNAVAILABLE, single.failed().phase)
    }
}
