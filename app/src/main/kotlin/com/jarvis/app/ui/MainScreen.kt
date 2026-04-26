package com.jarvis.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.ModelStatusUiState
import com.jarvis.app.OnDeviceModelStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    jarvisState: String,
    logs: List<String>,
    modelStatusUiState: ModelStatusUiState,
    onUseModelClicked: () -> Unit,
    onModelSelected: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit
) {
    var showLogs by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jarvis", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Logs")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Main Content (Centered Status)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = jarvisState,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val isReady = modelStatusUiState.activeModelId != null
                Text(
                    text = if (isReady) "Model Ready" else "Model Not Initialized",
                    color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (!isReady) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onUseModelClicked) {
                        Text("Initialize Model")
                    }
                }
            }

            // Logs Overlay
            AnimatedVisibility(
                visible = showLogs,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(16.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("System Logs", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        items(logs.reversed()) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Settings Overlay
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(16.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("Model Settings", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                            
                            Button(onClick = onRefreshModels) {
                                Text("Refresh Models")
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        items(modelStatusUiState.models) { model ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (model.id == modelStatusUiState.activeModelId) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(model.title, fontWeight = FontWeight.Bold)
                                    Text("Status: ${model.status.name}", style = MaterialTheme.typography.bodySmall)
                                    
                                    Row(modifier = Modifier.padding(top = 8.dp)) {
                                        if (model.status == OnDeviceModelStatus.DOWNLOADABLE) {
                                            Button(onClick = { onDownloadModel(model.id) }) {
                                                Text("Download")
                                            }
                                        } else if (model.status == OnDeviceModelStatus.AVAILABLE) {
                                            Button(onClick = { onModelSelected(model.id) }) {
                                                Text("Select")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            OutlinedButton(onClick = { onDeleteModel(model.id) }) {
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            if (modelStatusUiState.downloadingModelId != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Downloading: ${modelStatusUiState.downloadProgress}%")
                                LinearProgressIndicator(
                                    progress = { modelStatusUiState.downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
