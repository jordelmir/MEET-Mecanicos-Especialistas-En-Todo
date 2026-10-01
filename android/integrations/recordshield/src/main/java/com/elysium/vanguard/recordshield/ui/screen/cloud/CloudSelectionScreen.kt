package com.elysium.vanguard.recordshield.ui.screen.cloud

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.elysium.vanguard.recordshield.data.cloud.CloudStorageManager
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import com.elysium.vanguard.recordshield.ui.theme.*

@Composable
fun CloudSelectionScreen(
    cloudManager: CloudStorageManager,
    onBackClick: () -> Unit,
    onGoogleDriveSetup: () -> Unit,
    onSupabaseSetup: () -> Unit
) {
    val context = LocalContext.current
    val secureStorage = remember { SecureStorage(context) }
    val selectedProvider = remember { mutableStateOf(cloudManager.getSelectedProviderId()) }
    var showConnectedDialog by remember { mutableStateOf(false) }
    var gdriveConfigured by remember { mutableStateOf(secureStorage.googleDriveAccessToken != null) }

    // Refresh connection state when screen resumes (e.g. after Google Sign-In activity returns)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                gdriveConfigured = secureStorage.googleDriveAccessToken != null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "CLOUD BACKUP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "Protect your evidence off-device",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Status Banner ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MatrixGreen.copy(alpha = 0.08f),
                                MatrixGreen.copy(alpha = 0.02f)
                            )
                        )
                    )
                    .border(1.dp, MatrixGreen.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MatrixGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MatrixGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "EVIDENCE AUTO-BACKUP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MatrixGreen,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Every 10 seconds during recording, encrypted chunks upload to your cloud. Device destroyed? Evidence survives.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section Label ──
            Text(
                text = "SELECT PROVIDER",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Google Drive Card ──
            PremiumProviderCard(
                name = "Google Drive",
                subtitle = if (gdriveConfigured) "Connected to your Google account" else "15 GB free — like WhatsApp backups",
                icon = Icons.Default.AccountCircle,
                accentColor = Color(0xFF4285F4),
                isSelected = selectedProvider.value == CloudStorageManager.PROVIDER_GOOGLE_DRIVE,
                isConnected = gdriveConfigured,
                onClick = {
                    selectedProvider.value = CloudStorageManager.PROVIDER_GOOGLE_DRIVE
                    cloudManager.setSelectedProvider(CloudStorageManager.PROVIDER_GOOGLE_DRIVE)
                },
                onAction = {
                    if (gdriveConfigured) {
                        showConnectedDialog = true
                    } else {
                        onGoogleDriveSetup()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Supabase Card ──
            PremiumProviderCard(
                name = "Supabase Cloud",
                subtitle = "Encrypted Vercel backend. Coming soon.",
                icon = Icons.Default.Storage,
                accentColor = Color(0xFF3FCF8E),
                isSelected = selectedProvider.value == CloudStorageManager.PROVIDER_SUPABASE,
                isConnected = false,
                isComingSoon = true,
                onClick = {
                    selectedProvider.value = CloudStorageManager.PROVIDER_SUPABASE
                    cloudManager.setSelectedProvider(CloudStorageManager.PROVIDER_SUPABASE)
                },
                onAction = {
                    Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── How It Works ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "HOW IT WORKS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val steps = listOf(
                        Triple("01", "RECORD", "Start recording — chunks capture every 10s"),
                        Triple("02", "ENCRYPT", "Each chunk is SHA-256 hashed for integrity"),
                        Triple("03", "UPLOAD", "Chunks upload to your cloud in real-time"),
                        Triple("04", "VERIFY", "Local file deleted only after confirmed upload"),
                        Triple("05", "SURVIVE", "Device destroyed? Uploaded chunks persist")
                    )

                    steps.forEach { (num, title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = num,
                                style = MaterialTheme.typography.labelSmall,
                                color = MatrixGreen.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(28.dp)
                            )
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Connected Dialog ──
    if (showConnectedDialog) {
        AlertDialog(
            onDismissRequest = { showConnectedDialog = false },
            containerColor = ElevatedSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MatrixGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Google Drive", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your evidence is being backed up automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MatrixGreen.copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MatrixGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Chunks are encrypted and uploaded in real-time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MatrixGreen
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConnectedDialog = false
                        onGoogleDriveSetup()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MatrixGreen)
                ) {
                    Text("RECONNECT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            secureStorage.googleDriveAccessToken = null
                            secureStorage.googleDriveRefreshToken = null
                            secureStorage.selectedCloudProvider = null
                            showConnectedDialog = false
                            Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = RecordingRed)
                    ) {
                        Text("DISCONNECT", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { showConnectedDialog = false }) {
                        Text("CLOSE", color = TextTertiary)
                    }
                }
            }
        )
    }
}

@Composable
fun PremiumProviderCard(
    name: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    isConnected: Boolean,
    isComingSoon: Boolean = false,
    onClick: () -> Unit,
    onAction: () -> Unit
) {
    val borderColor = if (isSelected) accentColor.copy(alpha = 0.6f) else SubtleBorder
    val bgAlpha = animateFloatAsState(
        targetValue = if (isSelected) 0.12f else 0.04f,
        animationSpec = tween(300),
        label = "cardBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = accentColor.copy(alpha = 0.15f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        accentColor.copy(alpha = bgAlpha.value),
                        Color.Transparent
                    )
                )
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (isConnected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                    }
                    if (isComingSoon) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DeepPurple.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SOON",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            // Action Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isConnected) MatrixGreen.copy(alpha = 0.12f)
                        else accentColor.copy(alpha = 0.12f)
                    )
                    .border(
                        1.dp,
                        if (isConnected) MatrixGreen.copy(alpha = 0.3f) else accentColor.copy(alpha = 0.3f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isConnected) "VIEW" else if (isComingSoon) "SOON" else "CONNECT",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) MatrixGreen else accentColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
