package com.jarvis.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.JarvisState

@Composable
fun OverlayScreen(
    state: JarvisState,
    inputText: String,
    modifier: Modifier = Modifier
) {
    // Determine target size and shape based on state
    val isListening = state == JarvisState.ACTIVE
    val isProcessing = state == JarvisState.THINKING || state == JarvisState.SPEAKING

    // Animate scale (pulsing effect when listening)
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Animate Width based on text content
    val targetWidth = if (inputText.isNotEmpty() || isProcessing) 300.dp else 120.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val targetHeight = if (inputText.isNotEmpty()) 120.dp else 64.dp
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .height(animatedHeight)
                .scale(pulseScale)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    if (isListening) MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (inputText.isNotEmpty()) {
                Text(
                    text = inputText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                // Show dots or state
                val stateText = when (state) {
                    JarvisState.ACTIVE -> "Listening..."
                    JarvisState.THINKING -> "Thinking..."
                    JarvisState.SPEAKING -> "Executing..."
                    else -> "Jarvis"
                }
                Text(
                    text = stateText,
                    color = if (isListening) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}
