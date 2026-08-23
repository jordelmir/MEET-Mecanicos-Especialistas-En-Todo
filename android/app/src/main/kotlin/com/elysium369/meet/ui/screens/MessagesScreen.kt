package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.communications.ConversationSummary
import com.elysium369.meet.communications.CallConnectionState
import com.elysium369.meet.communications.DecryptedMessage
import com.elysium369.meet.ui.CommunicationViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.text.DateFormat
import java.util.Date

@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    serviceVertical: String? = null,
    serviceReferenceId: String? = null,
    serviceTitle: String? = null,
    viewModel: CommunicationViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val selected by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startCall() else viewModel.microphonePermissionDenied()
    }
    val requestOrStartCall = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCall()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(serviceVertical, serviceReferenceId, serviceTitle) {
        if (!serviceVertical.isNullOrBlank() && !serviceReferenceId.isNullOrBlank()) {
            viewModel.openServiceContext(
                serviceVertical,
                serviceReferenceId,
                serviceTitle?.takeIf(String::isNotBlank) ?: "Conversación del servicio",
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MeetColors.backgroundDeep) {
        Column(modifier = Modifier.fillMaxSize()) {
            MessagesHeader(
                title = selected?.title ?: "Mensajes",
                onBack = if (selected != null) ({ viewModel.selectConversation(null) }) else onBack,
                onCall = selected?.let { requestOrStartCall },
                onEndCall = if (callState == CallConnectionState.ACTIVE || callState == CallConnectionState.CONNECTING) {
                    viewModel::endCall
                } else null,
            )
            notice?.let { HonestStateBanner(it) }
            if (selected == null) {
                Inbox(
                    conversations = conversations,
                    requestedVertical = serviceVertical,
                    onOpen = { viewModel.selectConversation(it.id) },
                )
            } else {
                ConversationBody(
                    conversation = selected!!,
                    messages = messages,
                    onSend = { text, clear -> viewModel.sendText(text, clear) },
                )
            }
        }
    }
}

@Composable
private fun MessagesHeader(
    title: String,
    onBack: () -> Unit,
    onCall: (() -> Unit)?,
    onEndCall: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MeetColors.cardBackground).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White)
        }
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = MeetColors.cyberCyan,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text("Elysium Communications", color = MeetColors.textSecondary, fontSize = 10.sp)
        }
        if (onEndCall != null) {
            IconButton(onClick = onEndCall) {
                Icon(Icons.Outlined.CallEnd, "Finalizar llamada", tint = MeetColors.error)
            }
        } else if (onCall != null) {
            IconButton(onClick = onCall) {
                Icon(Icons.Outlined.Call, "Llamada Elysium", tint = MeetColors.neonGreen)
            }
        }
    }
}

@Composable
private fun Inbox(
    conversations: List<ConversationSummary>,
    requestedVertical: String?,
    onOpen: (ConversationSummary) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!requestedVertical.isNullOrBlank()) {
            HonestStateBanner(
                "Abre una solicitud activa para hablar con su proveedor autorizado. Los números telefónicos permanecen privados.",
            )
            Spacer(Modifier.height(12.dp))
        }
        Text("Conversaciones", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Aún no tienes conversaciones", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Aparecerán al asignarse un proveedor, conductor o contacto autorizado.",
                        color = MeetColors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(conversations, key = { it.id }) { conversation ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(conversation) }
                            .background(MeetColors.cardBackground, RoundedCornerShape(16.dp))
                            .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).background(MeetColors.cyberCyan.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MeetColors.cyberCyan)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(conversation.title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                conversation.serviceVertical?.let { "Servicio · ${it.replace('_', ' ')}" } ?: "Conversación privada",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        Text("›", color = MeetColors.cyberCyan, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationBody(
    conversation: ConversationSummary,
    messages: List<DecryptedMessage>,
    onSend: (String, () -> Unit) -> Unit,
) {
    var text by remember(conversation.id) { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = 0.08f)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
            Text(
                if ((conversation.participantCount ?: 0) < 2) {
                    "Esperando participante autorizado · envío bloqueado"
                } else {
                    "Protección local activa · transporte remoto pendiente"
                },
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
            item { Spacer(Modifier.height(4.dp)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(MeetColors.cardBackground).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(4000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un mensaje") },
                maxLines = 4,
            )
            IconButton(onClick = { onSend(text) { text = "" } }, enabled = text.isNotBlank()) {
                Icon(Icons.Outlined.Send, "Enviar", tint = MeetColors.cyberCyan)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: DecryptedMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(
                    if (message.isMine) MeetColors.electricBlue.copy(alpha = 0.28f) else MeetColors.cardBackground,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
        ) {
            Text(message.body, color = if (message.decryptionFailed) MeetColors.warning else Color.White)
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAtEpochMs)) + " · " + message.deliveryState,
                color = MeetColors.textSecondary,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun HonestStateBanner(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MeetColors.warning.copy(alpha = 0.12f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
