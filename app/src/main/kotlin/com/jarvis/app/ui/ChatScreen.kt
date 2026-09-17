package com.jarvis.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
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

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender { USER, JARVIS, SYSTEM }
enum class VoiceOrbState { IDLE, LISTENING, THINKING, SPEAKING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    orbState: VoiceOrbState,
    connectionStatus: String,
    onSendMessage: (String) -> Unit,
    onMicClicked: () -> Unit,
    onSettingsClicked: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val darkBg = Color(0xFF0D1117)
    val panelBg = Color(0xFF161B22)
    val cyanAccent = Color(0xFF00F0FF)
    val textWhite = Color(0xFFF0F6FC)

    Scaffold(
        containerColor = darkBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "JARVIS",
                            color = cyanAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            connectionStatus,
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClicked) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = cyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = panelBg)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(darkBg)
        ) {
            // Animated Voice Orb Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(panelBg),
                contentAlignment = Alignment.Center
            ) {
                GlowingOrb(state = orbState, onMicClicked = onMicClicked)
            }

            // Chat Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg = msg, cyanAccent = cyanAccent, textWhite = textWhite)
                }
            }

            // Input Bar & Mic Trigger
            Surface(
                color = panelBg,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask JARVIS anything...", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = darkBg,
                            unfocusedContainerColor = darkBg,
                            focusedTextColor = textWhite,
                            unfocusedTextColor = textWhite,
                            focusedIndicatorColor = cyanAccent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText)
                                inputText = ""
                            } else {
                                onMicClicked()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF00F0FF), Color(0xFF0072FF))
                                ),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (inputText.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                            contentDescription = "Trigger",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlowingOrb(state: VoiceOrbState, onMicClicked: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val orbColor = when (state) {
        VoiceOrbState.IDLE -> Color(0xFF00F0FF)
        VoiceOrbState.LISTENING -> Color(0xFF3FB950)
        VoiceOrbState.THINKING -> Color(0xFFD29922)
        VoiceOrbState.SPEAKING -> Color(0xFF58A6FF)
    }

    val stateText = when (state) {
        VoiceOrbState.IDLE -> "Tap to Talk"
        VoiceOrbState.LISTENING -> "Listening..."
        VoiceOrbState.THINKING -> "Processing..."
        VoiceOrbState.SPEAKING -> "Speaking..."
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onMicClicked() }
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .scale(if (state == VoiceOrbState.LISTENING) scale else 1f)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(orbColor, orbColor.copy(alpha = 0.2f))
                    )
                )
                .border(2.dp, orbColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Mic",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(stateText, color = orbColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, cyanAccent: Color, textWhite: Color) {
    val isUser = msg.sender == MessageSender.USER
    val isSystem = msg.sender == MessageSender.SYSTEM

    val bubbleBg = when {
        isUser -> Color(0xFF1F6FEB)
        isSystem -> Color(0xFF388BFD).copy(alpha = 0.15f)
        else -> Color(0xFF21262D)
    }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleBg,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            border = if (isSystem) androidx.compose.foundation.BorderStroke(1.dp, cyanAccent.copy(alpha = 0.4f)) else null,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = msg.text,
                color = if (isSystem) cyanAccent else textWhite,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
