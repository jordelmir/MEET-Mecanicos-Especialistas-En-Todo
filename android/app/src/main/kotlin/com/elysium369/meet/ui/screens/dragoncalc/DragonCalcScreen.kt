package com.elysium369.meet.ui.screens.dragoncalc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cr.dragon.calc.ui.DragonTheme
import cr.dragon.calc.ui.MainAppEntry

/** Hosts the original DragonCalc document, graph, vision and neural UI in Elysium. */
@Composable
fun DragonCalcScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    DragonTheme {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onBack) { Text("← Volver a Elysium") }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { MainAppEntry() }
        }
    }
}
