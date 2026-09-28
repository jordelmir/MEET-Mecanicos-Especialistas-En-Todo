package com.elysium.nexus.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab

/**
 * The §15 in-app help system.
 *
 * The user can tap a small floating "?" button on
 * any screen to get a help card. The help card
 * shows:
 *
 *  - **What is this?** — a plain-language
 *    description of the screen.
 *  - **How do I use it?** — step-by-step
 *    instructions.
 *  - **Tip** — an extra power-user tip.
 *
 * The help card is a modal overlay with a dark
 * scrim behind it. Tapping outside the card or the
 * "X" button dismisses it.
 *
 * ## Guided tour
 *
 * On the first launch, the user sees a 3-step
 * guided tour that walks them through the app:
 *
 *  1. "Pick a device" — the hub screen.
 *  2. "Connect it" — the device connection flow.
 *  3. "Use it" — the control surface.
 *
 * The tour is shown via [GuidedTourOverlay]. The
 * "Don't show again" preference is stored in
 * SharedPreferences (per ADR-0031).
 *
 * ## Plain language
 *
 * The help text is written for someone who has
 * never used a smart app before. We avoid jargon.
 * We use short sentences. We explain every button
 * and every icon.
 */

/**
 * A help card. The [HelpCard] is a modal overlay
 * that the user can dismiss.
 *
 * @param title the title shown at the top of the
 *   card. Plain language.
 * @param whatIsThis a one-paragraph description of
 *   what the screen does.
 * @param howToUse a list of step-by-step
 *   instructions. Each step is one short sentence.
 * @param tip an optional power-user tip shown at
 *   the bottom.
 */
@Composable
fun HelpCard(
    title: String,
    whatIsThis: String,
    howToUse: List<String>,
    tip: String? = null,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ElysiumColors.SurfaceHigh,
                            ElysiumColors.Surface
                        )
                    )
                )
                .padding(20.dp)
                .clickable(enabled = false, onClick = { })
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = ElysiumColors.NeonCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    ),
                    color = ElysiumColors.OnSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.Surface)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = ElysiumColors.OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            SectionLabel("¿Qué es esto?")
            Text(
                text = whatIsThis,
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = ElysiumColors.OnSurface
            )
            SectionLabel("¿Cómo se usa?")
            howToUse.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ElysiumColors.NeonCyan),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ElysiumColors.Background
                        )
                    }
                    Text(
                        text = step,
                        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                        color = ElysiumColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (tip != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElysiumColors.NeonOrange.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = ElysiumColors.NeonOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = tip,
                            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                            color = ElysiumColors.OnSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            NeonChip(
                label = "Entendido",
                onClick = onDismiss,
                accent = ElysiumColors.NeonCyan,
                active = true
            )
        }
    }
}

/**
 * A small section label inside the help card.
 * "PASO 1", "CONSEJO", etc.
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        ),
        color = ElysiumColors.NeonCyan
    )
}

/**
 * A small floating "?" button. The user taps it to
 * open the help card. The button is a NeonFab in
 * the "?" icon variant — a small purple circle in
 * the top-right of every screen.
 */
@Composable
fun HelpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElysiumColors.NeonPurple.copy(alpha = 0.9f),
                        ElysiumColors.NeonPurple.copy(alpha = 0.6f)
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = Color.White
        )
    }
}

/**
 * The first-launch guided tour.
 *
 * The tour walks the user through the 3 main
 * screens:
 *
 *  1. "Pick a device" — the hub.
 *  2. "Connect it" — the connection flow.
 *  3. "Use it" — the control surface.
 *
 * The tour is shown via [GuidedTourOverlay] as a
 * sequence of help cards with "Next" / "Done"
 * buttons. The user can skip the tour at any
 * time.
 */
@Composable
fun GuidedTourOverlay(
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf(
        HelpStep(
            title = "¡Bienvenido a Elysium Nexus!",
            whatIsThis = "Esta app convierte tu teléfono en un control remoto universal. " +
                "Puedes controlar tu TV, tu consola, tu compu, todo desde un solo lugar.",
            howToUse = listOf(
                "En la pantalla principal verás tarjetas para cada tipo de dispositivo.",
                "Toca la tarjeta que quieras (por ejemplo, TV) para empezar.",
                "Sigue las instrucciones en pantalla. Cada paso está explicado."
            ),
            tip = "Si nunca has usado una app como esta, toca el botón '?' " +
                "morado en cualquier momento para ver ayuda."
        ),
        HelpStep(
            title = "Paso 1: Elige tu dispositivo",
            whatIsThis = "Te mostraremos una lista de marcas y modelos. " +
                "Elige el que se parezca al tuyo. " +
                "Si no estás seguro, elige 'Genérico' — funciona con la mayoría.",
            howToUse = listOf(
                "Toca la categoría que quieras (TV, PlayStation, Xbox, etc.).",
                "Toca la marca de tu dispositivo.",
                "Toca 'Conectar' y sigue los pasos."
            ),
            tip = "Si tu marca no aparece, elige 'Genérico' y prueba. " +
                "Si no funciona, puedes enseñarle los códigos con el control remoto físico."
        ),
        HelpStep(
            title = "Paso 2: Conecta",
            whatIsThis = "Para una TV, tu teléfono usa el infrarrojo (IR) — " +
                "como un control remoto normal. " +
                "Apuntarás el teléfono a la TV y enviarás una señal de prueba.",
            howToUse = listOf(
                "Apunta la parte de arriba del teléfono a la TV (a menos de 3 metros).",
                "Toca 'Enviar señal de prueba'.",
                "Si la TV responde, toca 'Sí, funcionó' para guardar la conexión."
            ),
            tip = "Si la TV no responde, prueba con otra marca. " +
                "También puedes enseñarle los códigos con el botón 'Aprender'."
        ),
        HelpStep(
            title = "Paso 3: Usa tu control",
            whatIsThis = "Una vez conectado, verás los botones del control remoto. " +
                "Toca cualquier botón para enviar la señal a tu dispositivo. " +
                "Es como usar el control remoto físico, pero desde tu teléfono.",
            howToUse = listOf(
                "Apunta el teléfono a tu dispositivo.",
                "Toca el botón que quieras (Power, Vol+, Canal, etc.).",
                "Para volver al inicio, toca la flecha 'Atrás' arriba a la izquierda."
            ),
            tip = "Puedes personalizar los botones más tarde. " +
                "Por ahora, los controles predeterminados funcionan con la mayoría de dispositivos."
        )
    )
    val current = steps[step]
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .padding(24.dp)
            ) {
                HelpCard(
                    title = current.title,
                    whatIsThis = current.whatIsThis,
                    howToUse = current.howToUse,
                    tip = current.tip,
                    onDismiss = {
                        if (step < steps.size - 1) step++ else onComplete()
                    }
                )
                // Override the help card's "Entendido"
                // button label to "Siguiente" / "Empezar"
                // depending on the step.
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 0) {
                        NeonChip(
                            label = "Atrás",
                            onClick = { step-- },
                            accent = ElysiumColors.NeonPurple
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    NeonChip(
                        label = if (step < steps.size - 1) "Siguiente" else "¡Empezar!",
                        onClick = {
                            if (step < steps.size - 1) step++ else onComplete()
                        },
                        accent = ElysiumColors.NeonCyan,
                        active = true
                    )
                }
            }
        }
    }
}

private data class HelpStep(
    val title: String,
    val whatIsThis: String,
    val howToUse: List<String>,
    val tip: String?
)
