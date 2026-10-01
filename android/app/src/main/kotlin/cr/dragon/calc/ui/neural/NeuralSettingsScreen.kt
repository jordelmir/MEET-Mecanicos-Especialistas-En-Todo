package cr.dragon.calc.ui.neural

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// Colores del Ecosistema Dragon
val DragonBlack = Color(0xFF0B0C10)
val DragonGreen = Color(0xFF00FF88) // Neon Vanguard Green
val DragonGray = Color(0xFF1F2833)
val DragonCyan = Color(0xFF66FCF1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuralSettingsScreen(
    onBack: () -> Unit,
    viewModel: NeuralSettingsViewModel = hiltViewModel()
) {
    val uplinkStatus by viewModel.uplinkStatus.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val providers = listOf("Gemini", "OpenAI", "Anthropic", "Local")
    var selectedProvider by remember { mutableStateOf(providers[0]) }
    var apiKey by remember { mutableStateOf(viewModel.getSavedKey(selectedProvider)) }
    var passwordVisible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DragonBlack)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text(
                "NEXO NEURAL",
                color = DragonGreen,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Selector de Matriz (Glassmorphism Effect)
        Text("MATRIZ DE INTELIGENCIA", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DragonGray.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedProvider,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(DragonGray)
                ) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider, color = Color.White) },
                            onClick = {
                                selectedProvider = provider
                                expanded = false
                                apiKey = viewModel.getSavedKey(provider)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Casetilla de Inyección (API Key)
        Text("LLAVE DE ACCESO (BYOK)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = DragonGreen
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DragonGreen,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Estado del Uplink
        StatusIndicator(uplinkStatus, statusMessage)

        Spacer(modifier = Modifier.weight(1f))

        // Botón de Ignición
        Button(
            onClick = { viewModel.saveAndVerifyKey(selectedProvider, apiKey) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = DragonGreen),
            enabled = uplinkStatus != UplinkStatus.VALIDATING
        ) {
            Text(
                if (uplinkStatus == UplinkStatus.VALIDATING) "SINCRONIZANDO..." else "ESTABLECER ENLACE NEURAL",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun StatusIndicator(status: UplinkStatus, message: String) {
    val color = when(status) {
        UplinkStatus.IDLE -> Color.Gray
        UplinkStatus.VALIDATING -> DragonCyan
        UplinkStatus.STABLE -> DragonGreen
        UplinkStatus.ERROR -> Color.Red
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            if (message.isBlank()) "Esperando activación..." else message,
            color = color,
            fontSize = 14.sp
        )
    }
}
