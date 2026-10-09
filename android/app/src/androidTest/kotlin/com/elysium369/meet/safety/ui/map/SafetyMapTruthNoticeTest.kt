package com.elysium369.meet.safety.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SafetyMapTruthNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noticeExplainsThatMapPointsAreNotConfirmedCrimes() {
        composeRule.setContent {
            MaterialTheme {
                MapTruthNotice()
            }
        }
        composeRule.onNodeWithText("LECTURA RESPONSABLE DEL MAPA").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Cada punto representa un registro accesible con su nivel de exposición. Un marcador no confirma por sí solo un delito; una capa vacía puede reflejar cobertura incompleta.",
            substring = true,
        ).assertIsDisplayed()
    }
}
