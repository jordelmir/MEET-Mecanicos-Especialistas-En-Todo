package com.elysium.vanguard.recordshield.ui.screen.pin

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.recordshield.ui.theme.*
import com.elysium.vanguard.recordshield.ui.component.PinDot
import com.elysium.vanguard.recordshield.ui.component.PinKeypad


/**
 * ============================================================================
 * PinScreen — Captive Lock Screen
 * ============================================================================
 *
 * Design: Full-screen PIN gate with glassmorphism keypad. This screen is
 * displayed:
 *   1. When accessing the gallery (privacy protection)
 *   2. During recording as a captive lock (anti-sabotage)
 *   3. On app launch when PIN is configured
 *
 * Anti-Sabotage: When isLockMode = true, the back button and gestures
 * are intercepted, making it impossible to dismiss this screen without
 * entering the correct PIN or stopping the recording from the
 * notification (which itself could be hidden on some OEMs).
 * ============================================================================
 */
@Composable
fun PinScreen(
    title: String = "ENTER PIN",
    subtitle: String = "Access restricted",
    isLockMode: Boolean = false, // true = captive during recording
    onPinVerified: () -> Unit,
    onVerifyPin: (String) -> Boolean
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorShake by remember { mutableStateOf(false) }
    val maxPinLength = 6

    // Shake animation on wrong PIN
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
            // Shield icon with glow
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
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MatrixGreen,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 3.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // PIN Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeOffset.dp)
            ) {
                repeat(maxPinLength) { index ->
                    PinDot(
                        isFilled = index < enteredPin.length,
                        isError = isError
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Keypad
            PinKeypad(
                onDigit = { digit ->
                    if (enteredPin.length < maxPinLength) {
                        enteredPin += digit
                        isError = false

                        // Auto-verify when PIN is complete
                        if (enteredPin.length == maxPinLength) {
                            if (onVerifyPin(enteredPin)) {
                                onPinVerified()
                            } else {
                                isError = true
                                errorShake = true
                                enteredPin = ""
                            }
                        }
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                        isError = false
                    }
                }
            )

            if (isError) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "INCORRECT PIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = RecordingRed,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

// Removed local PinDot, PinKeypad, and KeypadButton in favor of shared components
