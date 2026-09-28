package com.elysium.vanguard.recordshield.ui.screen.consent

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.recordshield.ui.theme.*

/**
 * ============================================================================
 * ConsentScreen — Explicit Consent Before First Recording
 * ============================================================================
 *
 * WHY THIS IS REQUIRED:
 *   - Google Play Store requires explicit consent for camera/mic access
 *   - GDPR requires informed consent before data collection
 *   - User must understand what the app does before recording
 *   - Legal protection: proves user was informed and consented
 *
 * WHAT THE USER SEES:
 *   1. Clear explanation of what the app does
 *   2. What permissions are needed and why
 *   3. What data is recorded and where it's stored
 *   4. Option to accept or decline
 *   5. Link to full Privacy Policy
 * ============================================================================
 */
@Composable
fun ConsentScreen(
    onConsentGiven: () -> Unit,
    onDecline: () -> Unit
) {
    var isAccepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Shield icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally)
                .drawBehind {
                    drawCircle(
                        color = MatrixGreen.copy(alpha = 0.15f),
                        radius = size.minDimension / 2 + 20f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MatrixGreen,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "CONSENT REQUIRED",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Text(
            text = "Before you can record, please review the following",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // What the app does
        ConsentSection(
            icon = Icons.Default.Videocam,
            title = "WHAT THIS APP DOES",
            items = listOf(
                "Records audio and video evidence in 10-second chunks",
                "Stores recordings ONLY inside this app (not in gallery)",
                "Optionally uploads chunks to your selected cloud provider",
                "Protects recordings with a 6-digit PIN"
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Permissions
        ConsentSection(
            icon = Icons.Default.Security,
            title = "PERMISSIONS NEEDED",
            items = listOf(
                "Camera — to capture video evidence",
                "Microphone — to capture audio evidence",
                "Notifications — required by Android for background recording",
                "Internet — only if you enable cloud backup"
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Data handling
        ConsentSection(
            icon = Icons.Default.Storage,
            title = "HOW YOUR DATA IS HANDLED",
            items = listOf(
                "Recordings stay on your device unless YOU choose cloud backup",
                "No data is shared with third parties",
                "No analytics or tracking is used",
                "You can delete all data at any time from the app"
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Warning card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(WarningAmber.copy(alpha = 0.3f), WarningAmber.copy(alpha = 0.1f))))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = WarningAmber
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "By tapping 'I CONSENT', you confirm that you understand how this app works and agree to the collection of audio/video data for personal safety purposes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Accept button
        Button(
            onClick = onConsentGiven,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAccepted) MatrixGreen else MatrixGreen.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            enabled = true
        ) {
            Text(
                text = "I UNDERSTAND AND CONSENT",
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = DeepBlack
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Decline button
        TextButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "DECLINE — Exit App",
                color = RecordingRed,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ConsentSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<String>
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MatrixGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MatrixGreen,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(start = 28.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    color = MatrixGreen,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
