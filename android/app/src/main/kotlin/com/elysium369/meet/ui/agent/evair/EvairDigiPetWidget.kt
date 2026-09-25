package com.elysium369.meet.ui.agent.evair

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.agent.laya.DigiEvolutionStage
import com.elysium369.meet.core.agent.laya.DigiSoulMood
import com.elysium369.meet.core.agent.laya.DigiSoulState
import com.elysium369.meet.core.agent.laya.EvairDigiSoulEngine
import com.elysium369.meet.ui.theme.MeetColors

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E V A I R   D I G I - P E T   C O M P A N I O N   W I D G E T
 * ══════════════════════════════════════════════════════════════════════
 * Interactive, animated digital companion HUD representing EVAIR's living soul,
 * Digievolution stage, mood, bond level, and autobiographical memories.
 */
@Composable
fun EvairDigiPetWidget(
    modifier: Modifier = Modifier,
    engine: EvairDigiSoulEngine = remember { EvairDigiSoulEngine() },
    onOpenAssistant: () -> Unit = {},
    onOpenAgentStore: () -> Unit = {},
) {
    var state by remember { mutableStateOf(engine.getState()) }
    var isExpanded by remember { mutableStateOf(false) }
    var petReactionText by remember { mutableStateOf<String?>(null) }

    // Breathing pulse animation for EVAIR's core
    val infiniteTransition = rememberInfiniteTransition(label = "DigiPetPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Stage theme color
    val stageColor = when (state.stage) {
        DigiEvolutionStage.MEGA -> Color(0xFFFFD700)      // Vanguard Gold
        DigiEvolutionStage.ULTIMATE -> Color(0xFFBD00FF)  // Cyber Violet
        DigiEvolutionStage.CHAMPION -> MeetColors.neonGreen // Guardian Green
        DigiEvolutionStage.ROOKIE -> MeetColors.cyberCyan   // Rookie Cyan
    }

    val animatedBorderColor by animateColorAsState(
        targetValue = if (state.mood == DigiSoulMood.WORRIED) Color(0xFFFF4444) else stageColor,
        label = "BorderColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isExpanded = !isExpanded
            },
        shape = RoundedCornerShape(20.dp),
        color = MeetColors.cardBackground.copy(alpha = 0.95f),
        border = BorderStroke(1.5.dp, animatedBorderColor.copy(alpha = 0.7f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header Row: Avatar Core, Name/Stage, Mood Badge, and Bond %
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animated Holographic Digi-Core
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(stageColor.copy(alpha = 0.4f), Color.Transparent)
                                )
                            )
                            .border(2.dp, stageColor, CircleShape)
                            .clickable {
                                // Petting action increases bond!
                                val result = engine.processEvent("KM_DRIVEN", "Acariciando a EVAIR", kmDelta = 0.5)
                                state = result.updatedState
                                petReactionText = "¡EVAIR sintió tu cariño! Vínculo +1"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.mood.emoji,
                            fontSize = 22.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.name,
                                color = MeetColors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = stageColor.copy(alpha = 0.2f),
                                border = BorderStroke(0.8.dp, stageColor)
                            ) {
                                Text(
                                    text = state.stage.title,
                                    color = stageColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Nivel ${state.level} • ${state.stage.rankTitle}",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Bond Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF3366),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${state.bondPercent}%",
                            color = MeetColors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // XP Progress Bar to Next Digievolution
            val currentMin = state.stage.minXp
            val nextStage = when (state.stage) {
                DigiEvolutionStage.ROOKIE -> DigiEvolutionStage.CHAMPION
                DigiEvolutionStage.CHAMPION -> DigiEvolutionStage.ULTIMATE
                DigiEvolutionStage.ULTIMATE -> DigiEvolutionStage.MEGA
                DigiEvolutionStage.MEGA -> null
            }

            val xpProgress = if (nextStage != null) {
                val span = (nextStage.minXp - currentMin).toFloat()
                val currentInSpan = (state.currentXp - currentMin).toFloat()
                (currentInSpan / span).coerceIn(0f, 1f)
            } else {
                1.0f
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (nextStage != null) "Próxima Digievolución: ${nextStage.title}" else "¡NIVEL MÁXIMO MEGA!",
                        color = MeetColors.textMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${state.currentXp} XP",
                        color = stageColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { xpProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = stageColor,
                    trackColor = MeetColors.cardBackgroundLighter,
                )
            }

            // Petting Toast / Instant Reaction
            if (petReactionText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = petReactionText!!,
                    color = MeetColors.neonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Expanded Section: Memories & Chat
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MeetColors.borderSubtle)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "📖 Diario de Recuerdos de EVAIR",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val recentMemories = state.memories.take(3)
                    if (recentMemories.isEmpty()) {
                        Text(
                            text = "Aún no tenemos memorias juntos. ¡Conduce y diagnostica tu auto para forjar nuestra historia!",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp
                        )
                    } else {
                        recentMemories.forEach { mem ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.cardBackgroundLighter.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (mem.emotion) {
                                            "TRIUMPH" -> "🏆"
                                            "ALERT" -> "⚠️"
                                            "PROTECTION" -> "🛡️"
                                            else -> "✨"
                                        },
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = mem.title,
                                            color = MeetColors.textPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = mem.narrative,
                                            color = MeetColors.textSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Actions: Chat & Agent Store
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenAssistant,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = stageColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Hablar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenAgentStore,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Agent Store",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
