package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.core.ai.ChatMessage
import com.elysium369.meet.ui.SupportChatViewModel
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.components.EliteIconButton
import com.elysium369.meet.ui.components.EliteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    onBack: () -> Unit,
    viewModel: SupportChatViewModel = hiltViewModel(),
    vehicleInfo: String = "Vehículo Genérico (OBD2)"
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "MEET AI Support - $vehicleInfo",
                onBackClick = onBack,
                actions = {
                    EliteTextButton(
                        text = "Limpiar",
                        onClick = { viewModel.clearChat() },
                        color = com.elysium369.meet.ui.theme.MeetColors.error
                    )
                },
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
        ) {
            // Chat Messages Area
            Box(modifier = Modifier.weight(1f)) {
                EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .eliteScrollbar(listState)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message ->
                            ChatBubble(message)
                        }
                        if (isLoading) {
                            item {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }

            // Input Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MeetColors.backgroundDark)
                    .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Describe el problema...", color = MeetColors.textSecondary) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 4
                    )
                    
                    EliteIconButton(
                        icon = {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = if (!isLoading && inputText.isNotBlank()) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary)
                        },
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText, vehicleInfo)
                                inputText = ""
                            }
                        },
                        isEnabled = !isLoading && inputText.isNotBlank()
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isUser) MeetColors.cardBackground else MeetColors.backgroundDark
    val borderColor = if (isUser) MeetColors.electricBlue.copy(alpha = 0.5f) else MeetColors.neonGreen.copy(alpha = 0.5f)
    val textColor = if (isUser) Color.White else MeetColors.textPrimary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 0.dp,
                        bottomEnd = if (isUser) 0.dp else 16.dp
                    )
                )
                .background(backgroundColor)
                .border(
                    1.dp, 
                    borderColor,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 0.dp,
                        bottomEnd = if (isUser) 0.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        Text(
            text = if (isUser) "Tú" else "MEET AI",
            color = MeetColors.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MeetColors.backgroundDark)
            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MeetColors.neonGreen
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("MEET AI está analizando...", color = MeetColors.neonGreen, fontSize = 12.sp)
    }
}
