package com.elysium369.meet.safety.ui.institutional

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyInstitutionalPresentationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenRendersSafetyDestinationsAndHonestIntegrationStatuses() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                SafetyInstitutionalPresentationScreen(
                    onBack = {},
                    onNavigateToReport = {},
                    onNavigateToMap = {},
                    onNavigateToMyReports = {},
                    onNavigateToCases = {},
                    onNavigateToTimelines = {},
                    onNavigateToObservatory = {},
                    onNavigateToResearch = {},
                )
            }
        }

        composeRule.onNodeWithText("ELYSIUM SAFETY").assertIsDisplayed()
        composeRule.onNodeWithText("PRESENTACIÓN INSTITUCIONAL").assertIsDisplayed()
        composeRule.onNodeWithText("3D HOLOGRAPHIC COMMAND DECK").assertIsDisplayed()
        composeRule.onNodeWithText("Crear reporte").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir mapa").assertIsDisplayed()
        composeRule.onNodeWithText("GUÍA INTERACTIVA · 5 ETAPAS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Registra el acontecimiento").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Siguiente etapa").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Conserva el material original").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("FLUJO DE INFORMACIÓN · 6 ETAPAS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("CAPTURAR").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PRESERVAR").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("UBICAR").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("RELACIONAR").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("CONTRASTAR").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("REVISAR").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("QUÉ SIGNIFICA CADA ESTADO").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reportar un incidente").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mapa territorial").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Expedientes de seguridad").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Investigación científica y cadena de evidencia").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PENDIENTE DE PILOTO AUTORIZADO").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reportCardInvokesTheExistingSafetyReportDestination() {
        composeRule.mainClock.autoAdvance = false
        var reportDestinationOpened = false
        composeRule.setContent {
            MaterialTheme {
                SafetyInstitutionalPresentationScreen(
                    onBack = {},
                    onNavigateToReport = { reportDestinationOpened = true },
                    onNavigateToMap = {},
                    onNavigateToMyReports = {},
                    onNavigateToCases = {},
                    onNavigateToTimelines = {},
                    onNavigateToObservatory = {},
                    onNavigateToResearch = {},
                )
            }
        }

        composeRule.onNodeWithText("Reportar un incidente").performScrollTo().performClick()
        assertTrue(reportDestinationOpened)
    }
    @Test
    fun pipelineStagesNavigateToExistingSafetyDestinations() {
        composeRule.mainClock.autoAdvance = false
        val openedDestinations = mutableListOf<String>()
        composeRule.setContent {
            MaterialTheme {
                SafetyInstitutionalPresentationScreen(
                    onBack = {},
                    onNavigateToReport = { openedDestinations.add("report") },
                    onNavigateToMap = { openedDestinations.add("map") },
                    onNavigateToMyReports = { openedDestinations.add("my-reports") },
                    onNavigateToCases = { openedDestinations.add("cases") },
                    onNavigateToTimelines = { openedDestinations.add("timelines") },
                    onNavigateToObservatory = { openedDestinations.add("observatory") },
                    onNavigateToResearch = { openedDestinations.add("research") },
                )
            }
        }

        val routeAssertions = listOf(
            "CAPTURAR" to "report",
            "PRESERVAR" to "report",
            "UBICAR" to "map",
            "RELACIONAR" to "research",
            "CONTRASTAR" to "research",
            "REVISAR" to "cases",
        )
        routeAssertions.forEach { (stageLabel, expectedDestination) ->
            composeRule.onNodeWithText(stageLabel).performScrollTo().performClick()
            assertEquals("Stage $stageLabel", expectedDestination, openedDestinations.last())
        }
    }

}
