package com.jarvis.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.app.FolderSetupUiState

@Composable
fun FolderSetupDialog(
    uiState: FolderSetupUiState,
    onFormatFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    if (uiState.showSetupDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Folder Needs Setup") },
            text = {
                Column {
                    Text("The selected folder does not have the required structure for Jarvis memory.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Jarvis needs specific folders like notes/concepts, notes/projects, etc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = onFormatFolder) {
                    Text("Format Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MalformedFilesDialog(
    uiState: FolderSetupUiState,
    onOrganizeWithAI: () -> Unit,
    onSkip: () -> Unit
) {
    if (uiState.showMalformedDialog) {
        AlertDialog(
            onDismissRequest = onSkip,
            title = { Text("Files Need Organization") },
            text = {
                Column {
                    Text("Found ${uiState.malformedFileCount} file(s) that don't follow the Jarvis format.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Would you like Jarvis to use AI to analyze and organize these files into the correct folders?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = onOrganizeWithAI) {
                    Text("Organize with AI")
                }
            },
            dismissButton = {
                TextButton(onClick = onSkip) {
                    Text("Skip")
                }
            }
        )
    }
}

@Composable
fun FolderSetupProgressDialog(
    uiState: FolderSetupUiState,
    onDismiss: () -> Unit
) {
    if (uiState.isSettingUp || uiState.isClassifying) {
        AlertDialog(
            onDismissRequest = { },
            title = { 
                Text(
                    if (uiState.isSettingUp) "Setting Up Folder" 
                    else "Organizing Files"
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isClassifying && uiState.totalFilesToClassify > 0) {
                        Text("Analyzing files with AI...")
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { 
                                if (uiState.totalFilesToClassify > 0) {
                                    uiState.classificationProgress.toFloat() / uiState.totalFilesToClassify
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${uiState.classificationProgress} / ${uiState.totalFilesToClassify} files",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Please wait...")
                    }
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
fun FolderSetupResultDialog(
    uiState: FolderSetupUiState,
    onDismiss: () -> Unit
) {
    val hasResult = uiState.successMessage != null || uiState.errorMessage != null
    
    if (hasResult && !uiState.isSettingUp && !uiState.isClassifying && !uiState.showSetupDialog && !uiState.showMalformedDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { 
                Text(
                    if (uiState.successMessage != null) "Success" else "Error",
                    color = if (uiState.successMessage != null) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    uiState.successMessage ?: uiState.errorMessage ?: "",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}
