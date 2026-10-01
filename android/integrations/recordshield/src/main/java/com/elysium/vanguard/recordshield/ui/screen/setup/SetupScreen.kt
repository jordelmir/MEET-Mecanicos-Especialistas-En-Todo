package com.elysium.vanguard.recordshield.ui.screen.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.recordshield.ui.component.PinDot
import com.elysium.vanguard.recordshield.ui.component.PinKeypad
import com.elysium.vanguard.recordshield.ui.theme.*

@Composable
fun SetupScreen(
    onPinSet: (String) -> Unit
) {
    var step by remember { mutableStateOf(SetupStep.ENTER_PIN) }
    var firstPin by remember { mutableStateOf("") }
    var secondPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorShake by remember { mutableStateOf(false) }
    val maxPinLength = 6

    val shakeOffset by animateFloatAsState(
        targetValue = if (errorShake) 20f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        finishedListener = { errorShake = false },
        label = "shake"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Setup icon with glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .drawBehind {
                        drawCircle(
                            color = MatrixGreen.copy(alpha = 0.15f),
                            radius = size.minDimension / 2 + 20f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MatrixGreen,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "INITIAL SETUP",
                style = MaterialTheme.typography.labelLarge,
                color = MatrixGreen,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (step == SetupStep.ENTER_PIN) "SET MASTER PIN" else "CONFIRM MASTER PIN",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 2.sp
            )
            Text(
                text = "This PIN secures your evidence.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // PIN Dots
            val currentPin = if (step == SetupStep.ENTER_PIN) firstPin else secondPin
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeOffset.dp)
            ) {
                repeat(maxPinLength) { index ->
                    PinDot(
                        isFilled = index < currentPin.length,
                        isError = isError
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Keypad
            PinKeypad(
                onDigit = { digit ->
                    if (step == SetupStep.ENTER_PIN) {
                        if (firstPin.length < maxPinLength) {
                            firstPin += digit
                            if (firstPin.length == maxPinLength) {
                                step = SetupStep.CONFIRM_PIN
                            }
                        }
                    } else {
                        if (secondPin.length < maxPinLength) {
                            secondPin += digit
                            isError = false
                            if (secondPin.length == maxPinLength) {
                                if (secondPin == firstPin) {
                                    onPinSet(secondPin)
                                } else {
                                    isError = true
                                    errorShake = true
                                    secondPin = ""
                                }
                            }
                        }
                    }
                },
                onBackspace = {
                    if (step == SetupStep.ENTER_PIN) {
                        if (firstPin.isNotEmpty()) firstPin = firstPin.dropLast(1)
                    } else {
                        if (secondPin.isNotEmpty()) secondPin = secondPin.dropLast(1)
                        else {
                            // Go back to first step if backspacing on empty second PIN
                            step = SetupStep.ENTER_PIN
                            firstPin = firstPin.dropLast(1)
                        }
                    }
                    isError = false
                }
            )

            if (isError) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PINS DO NOT MATCH",
                    style = MaterialTheme.typography.labelSmall,
                    color = RecordingRed,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

enum class SetupStep {
    ENTER_PIN,
    CONFIRM_PIN
}
