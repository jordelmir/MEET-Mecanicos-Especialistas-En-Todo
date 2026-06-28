package com.elysium369.meet.ui.screens.chat

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.data.local.entities.ChatMessageEntity
import com.elysium369.meet.ui.FleetChatViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.theme.MeetColors
import androidx.navigation.NavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetChatDetailScreen(
    viewModel: FleetChatViewModel,
    onBack: () -> Unit,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val partner by viewModel.selectedPartner.collectAsState()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isRecording by viewModel.isRecording.collectAsState()
    val isBlocked by viewModel.isPartnerBlocked.collectAsState()

    val playingMessageId by viewModel.playingMessageId.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()
    val audioPositionText by viewModel.audioPositionText.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File.createTempFile("meet_attachment_", "_file", context.cacheDir)
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.sendFileAttachment(tempFile, "FILE")
            } catch (e: Exception) {
                android.util.Log.e("FleetChatDetail", "Failed to resolve file attachment", e)
            }
        }
    }

    if (partner == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MeetColors.electricBlue)
        }
        return
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            EliteTopAppBar(
                title = partner?.userId ?: "Chat",
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = { viewModel.toggleBlockActivePartner() }) {
                        AnimatedNeonIcon(
                            imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                            contentDescription = if (isBlocked) "Desbloquear" else "Bloquear",
                            tint = if (isBlocked) MeetColors.electricBlue else MeetColors.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {
                items(messages) { message ->
                    val isOwn = message.senderId == viewModel.currentUserId
                    MessageBubble(
                        message = message,
                        isOwn = isOwn,
                        playingMessageId = playingMessageId,
                        audioProgress = audioProgress,
                        audioPositionText = audioPositionText,
                        onPlayAudio = { viewModel.togglePlayVoice(message) },
                        navController = navController
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MeetColors.cardBackground,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBlocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Has bloqueado a este usuario o estás bloqueado",
                                color = MeetColors.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (isRecording) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MeetColors.backgroundDeep)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MeetColors.error)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grabando nota de voz...", color = Color.White, fontSize = 12.sp)
                            }
                            Text(
                                text = "Cancelar",
                                color = MeetColors.error,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { viewModel.cancelRecordingVoice() }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.stopAndSendVoice() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MeetColors.electricBlue)
                        ) {
                            AnimatedNeonIcon(Icons.Default.Send, contentDescription = "Enviar Audio", tint = Color.Black)
                        }
                    } else {
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Adjuntar archivo", tint = MeetColors.electricBlue)
                        }

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Escribe un mensaje...", color = MeetColors.textSecondary) },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MeetColors.backgroundDeep,
                                unfocusedContainerColor = MeetColors.backgroundDeep,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (textInput.trim().isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.sendTextMessage(textInput)
                                    textInput = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.electricBlue)
                            ) {
                                AnimatedNeonIcon(Icons.Default.Send, contentDescription = "Enviar", tint = Color.Black)
                            }
                        } else {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.backgroundDark)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = { offset ->
                                                viewModel.startRecordingVoice()
                                                try {
                                                    tryAwaitRelease()
                                                } finally {
                                                    viewModel.stopAndSendVoice()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                AnimatedNeonIcon(Icons.Default.Mic, contentDescription = "Grabar", tint = MeetColors.electricBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    isOwn: Boolean,
    playingMessageId: String?,
    audioProgress: Float,
    audioPositionText: String,
    onPlayAudio: () -> Unit,
    navController: NavController? = null
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn) MeetColors.electricBlue.copy(alpha = 0.15f) else MeetColors.cardBackground
    val borderBrush = if (isOwn) {
        Brush.horizontalGradient(listOf(MeetColors.electricBlue, MeetColors.cyberCyan))
    } else {
        Brush.horizontalGradient(listOf(MeetColors.backgroundDark, MeetColors.backgroundDark))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        EliteCard(
            modifier = Modifier.widthIn(max = 290.dp),
            glowColor = if (isOwn) MeetColors.electricBlue.copy(alpha = 0.2f) else Color.Transparent,
            backgroundColor = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.messageType) {
                    "AUDIO" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = onPlayAudio) {
                                AnimatedNeonIcon(
                                    imageVector = if (playingMessageId == message.id) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir nota de voz",
                                    tint = MeetColors.electricBlue
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (playingMessageId == message.id) {
                                    LinearProgressIndicator(
                                        progress = { audioProgress },
                                        color = MeetColors.electricBlue,
                                        trackColor = MeetColors.backgroundDark,
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = audioPositionText,
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(12) { index ->
                                            val height = (index % 4 * 4) + 6
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(height.dp)
                                                    .background(MeetColors.textSecondary, RoundedCornerShape(1.dp))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🎙️ Nota de voz (${message.durationSeconds}s)",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    "DTC_ALERT" -> {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedNeonIcon(Icons.Default.Warning, contentDescription = null, tint = MeetColors.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Falla de Motor Activa", color = MeetColors.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message.messageText ?: "",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            val text = message.messageText ?: ""
                            val dtcRegex = Regex("[A-Z][0-9]{4}")
                            val foundCodes = dtcRegex.findAll(text).map { it.value }.toList()
                            if (foundCodes.isNotEmpty() && navController != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                foundCodes.forEach { code ->
                                    EliteButton(
                                        text = "🤖 CONSULTAR IA: $code",
                                        onClick = { navController.navigate("ai/$code") },
                                        color = MeetColors.cyberCyan,
                                        textColor = Color.Black,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    "FILE" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNeonIcon(Icons.Default.AttachFile, contentDescription = null, tint = MeetColors.electricBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.messageText ?: "Archivo",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = message.messageText ?: "",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp
                    )
                    if (isOwn) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AnimatedNeonIcon(
                            imageVector = when (message.status) {
                                "SENT" -> Icons.Default.Check
                                "DELIVERED" -> Icons.Default.DoneAll
                                "READ" -> Icons.Default.DoneAll
                                else -> Icons.Default.Check
                            },
                            contentDescription = null,
                            tint = if (message.status == "READ" || message.status == "DELIVERED") MeetColors.electricBlue else MeetColors.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
