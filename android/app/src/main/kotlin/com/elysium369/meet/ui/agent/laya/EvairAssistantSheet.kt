package com.elysium369.meet.ui.agent.laya

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.agent.laya.AssistantAction
import com.elysium369.meet.core.agent.laya.AssistantResponse
import com.elysium369.meet.core.agent.laya.LayaAssistantEngine
import com.elysium369.meet.core.agent.laya.RideAssistantContext
import com.elysium369.meet.core.agent.laya.VehicleAssistantContext
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val actions: List<AssistantAction> = emptyList(),
    val domain: String? = null,
    val latencyMs: Long? = null,
)

/**
 * EVAIR Assistant Sheet powered by Laya AI.
 * Real-time, non-hallucinating System 1 conversational assistant with domain ground truth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvairAssistantSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    rideContext: RideAssistantContext? = null,
    vehicleContext: VehicleAssistantContext? = null,
    onActionTriggered: ((AssistantAction) -> Unit)? = null,
    assistantEngine: LayaAssistantEngine = remember { LayaAssistantEngine() },
) {
    if (!isOpen) return

    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = if (rideContext != null) {
                    "¡Hola! Soy EVAIR. Estoy monitoreando tu viaje en tiempo real. Puedes preguntarme el tiempo de llegada, tarifas, pagos por SINPE Móvil o cualquier duda de seguridad."
                } else if (vehicleContext != null) {
                    "¡Hola! Soy EVAIR. Estoy conectado a la telemetría de tu vehículo. Pregúntame sobre códigos de error, estado de tu motor o consejos para la revisión técnica."
                } else {
                    "¡Hola! Soy EVAIR, tu asistente virtual inteligente de Elysium. ¿En qué te puedo colaborar hoy?"
                }
            )
        )
    }

    val listState = rememberLazyListState()

    val quickChips = remember(rideContext, vehicleContext) {
        when {
            rideContext != null -> listOf(
                "¿Dónde viene el chofer?",
                "¿Aceptan Sinpe Móvil?",
                "¿Puedo llevar a mi mascota?",
                "¿Cómo comparto mi viaje?",
                "¿Me cobran por cancelar?",
                "¿Cómo agrego una parada?",
            )
            vehicleContext != null -> listOf(
                "¿Qué significa Check Engine?",
                "¿Puedo seguir manejando así?",
                "¿Qué me van a revisar en Dekra?",
                "¿Cómo conecto el escáner OBD?",
                "¿Por qué sale humo negro o azul?",
                "¿Cuánto cuesta el cambio de frenos?",
            )
            else -> listOf(
                "¿Dónde viene el chofer?",
                "¿Qué significa la luz de Check Engine?",
                "¿Aceptan Sinpe Móvil?",
                "¿Cómo pido una grúa?",
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MeetColors.backgroundDark,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MeetColors.borderSubtle,
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing Avatar Badge
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.95f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(MeetColors.neonGreen, MeetColors.backgroundDeep)
                                )
                            )
                            .border(1.5.dp, MeetColors.neonGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MeetColors.backgroundDeep,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EVAIR ASISTENTE",
                                color = MeetColors.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, MeetColors.neonGreen)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = MeetColors.neonGreen,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "LAYA 33ms",
                                        color = MeetColors.neonGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (rideContext != null) "Viaje activo #${rideContext.rideId.take(8)}" else "Decisiones coherentes en tiempo real",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                }
            }

            HorizontalDivider(color = MeetColors.borderSubtle)

            // Conversation Messages Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        onActionClick = { action ->
                            onActionTriggered?.invoke(action)
                        }
                    )
                }

                if (isThinking) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MeetColors.cardBackground,
                            border = BorderStroke(0.5.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MeetColors.neonGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "EVAIR procesando en System 1...",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Quick Prompt Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickChips) { chipText ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeetColors.cardBackground,
                        border = BorderStroke(1.dp, MeetColors.borderBlue),
                        modifier = Modifier.clickable {
                            val userText = chipText
                            messages.add(ChatMessage(isUser = true, text = userText))
                            coroutineScope.launch {
                                isThinking = true
                                listState.animateScrollToItem(messages.size - 1)
                                val response = assistantEngine.ask(
                                    query = userText,
                                    rideContext = rideContext,
                                    vehicleContext = vehicleContext,
                                )
                                isThinking = false
                                messages.add(
                                    ChatMessage(
                                        isUser = false,
                                        text = response.text,
                                        actions = response.suggestedActions,
                                        domain = response.domain,
                                        latencyMs = response.latencyMs,
                                    )
                                )
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    ) {
                        Text(
                            text = chipText,
                            color = MeetColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MeetColors.cardBackground,
                border = BorderStroke(1.dp, MeetColors.borderBlue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text("Escribe tu consulta a EVAIR...", color = MeetColors.textSecondary, fontSize = 13.sp)
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            val userQuery = inputText.trim()
                            if (userQuery.isNotEmpty()) {
                                inputText = ""
                                messages.add(ChatMessage(isUser = true, text = userQuery))
                                coroutineScope.launch {
                                    isThinking = true
                                    listState.animateScrollToItem(messages.size - 1)
                                    val response = assistantEngine.ask(
                                        query = userQuery,
                                        rideContext = rideContext,
                                        vehicleContext = vehicleContext,
                                    )
                                    isThinking = false
                                    messages.add(
                                        ChatMessage(
                                            isUser = false,
                                            text = response.text,
                                            actions = response.suggestedActions,
                                            domain = response.domain,
                                            latencyMs = response.latencyMs,
                                        )
                                    )
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar",
                            tint = if (inputText.isNotBlank()) MeetColors.neonGreen else MeetColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onActionClick: (AssistantAction) -> Unit,
) {
    val isUser = message.isUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser) MeetColors.neonGreen.copy(alpha = 0.2f) else MeetColors.cardBackground,
            border = BorderStroke(
                1.dp,
                if (isUser) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.borderBlue
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser && message.domain != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "EVAIR • ${message.domain.uppercase()}",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        message.latencyMs?.let { ms ->
                            Text(
                                text = "${ms}ms",
                                color = MeetColors.textSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    color = MeetColors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Action Buttons if provided
        if (message.actions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.actions.forEach { action ->
                    FilledTonalButton(
                        onClick = { onActionClick(action) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MeetColors.cardBackgroundLighter,
                            contentColor = MeetColors.neonGreen
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(action.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
