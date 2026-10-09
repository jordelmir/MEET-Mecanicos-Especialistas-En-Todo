package com.elysium369.meet.safety.ui.institutional

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        composeRule.onNodeWithText("GUÍA INTERACTIVA · 5 ETAPAS").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Siguiente etapa").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Crear reporte").assertIsDisplayed()
        composeRule.onNodeWithText("Abrir mapa").assertIsDisplayed()
                composeRule.onNodeWithText("Registra el acontecimiento").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Conserva el material original").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("QUÉ SIGNIFICA CADA ESTADO").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reportar un incidente").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mapa territorial").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Expedientes de seguridad").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Investigación científica y cadena de evidencia").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("INGESTA REAL NO INTEGRADA").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PENDIENTE DE PILOTO AUTORIZADO").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "El adaptador de normalización y la regla de concentración existen como lógica de dominio.",
            substring = true,
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reportCardInvokesTheExistingSafetyReportDestination() {
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
        composeRule.runOnIdle {
            assertTrue(reportDestinationOpened)
        }
    }
}
