package cr.dragon.calc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cr.dragon.calc.graphing.GraphScreen
import cr.dragon.calc.ui.document.DocumentScreen

@Composable
fun MainAppEntry(viewModel: CalculatorViewModel = hiltViewModel()) {
    var showGraph by remember { mutableStateOf(false) }

    // Observamos el estado de arranque de la máquina
    val bootState = viewModel.bootState

    when (bootState) {
        is AppBootState.Idle -> {
            // Pantalla de carga mínima
            Box(modifier = Modifier.fillMaxSize().background(DragonBlack), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DragonGreen)
            }
        }
        is AppBootState.Ready -> {
            // Documento Principal ya inicializado
            if (showGraph) {
                GraphScreen(
                    onBackToCalc = { showGraph = false }
                )
            } else {
                DocumentScreen(
                    viewModel = viewModel,
                    onOpenGraph = { showGraph = true }
                )
            }
        }
        is AppBootState.Error -> {
            // Pantalla de error fatal controlada
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DragonBlack)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "FATAL ERROR [V3-SINGULARITY]:\n\n${bootState.reason}\n\n" +
                           "Sugerencia: Reinstala la app o libera espacio (1.5GB req).",
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
