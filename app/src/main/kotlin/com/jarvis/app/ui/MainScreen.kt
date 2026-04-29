package com.jarvis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.HelpBlock
import com.jarvis.app.LlmSettingsUiState
import com.jarvis.app.ModelStatusUiState
import com.jarvis.app.OnDeviceModelStatus
import com.jarvis.app.LlmBackendMode
import com.jarvis.intent.CommandExample
import com.jarvis.intent.CommandSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    jarvisState: String,
    logs: List<String>,
    selectedTab: MainTab,
    helpOverview: List<HelpBlock>,
    helpCommandSections: List<CommandSection>,
    modelStatusUiState: ModelStatusUiState,
    settingsUiState: LlmSettingsUiState,
    onTabSelected: (MainTab) -> Unit,
    onUseModelClicked: () -> Unit,
    onModelSelected: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onBackendModeSelected: (LlmBackendMode) -> Unit,
    onGeminiApiKeyChanged: (String) -> Unit,
    onSaveGeminiApiKey: () -> Unit,
    onClearGeminiApiKey: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jarvis", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MainTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tab.title) }
                    )
                }
            }

            when (selectedTab) {
                MainTab.HOME -> HomeTab(
                    jarvisState = jarvisState,
                    modelStatusUiState = modelStatusUiState,
                    settingsUiState = settingsUiState,
                    onUseModelClicked = onUseModelClicked
                )

                MainTab.HELP -> HelpTab(
                    overview = helpOverview,
                    sections = helpCommandSections
                )

                MainTab.LOGS -> LogsTab(logs = logs)
                MainTab.SETTINGS -> SettingsTab(
                    modelStatusUiState = modelStatusUiState,
                    settingsUiState = settingsUiState,
                    onRefreshModels = onRefreshModels,
                    onModelSelected = onModelSelected,
                    onDownloadModel = onDownloadModel,
                    onDeleteModel = onDeleteModel,
                    onBackendModeSelected = onBackendModeSelected,
                    onGeminiApiKeyChanged = onGeminiApiKeyChanged,
                    onSaveGeminiApiKey = onSaveGeminiApiKey,
                    onClearGeminiApiKey = onClearGeminiApiKey
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    jarvisState: String,
    modelStatusUiState: ModelStatusUiState,
    settingsUiState: LlmSettingsUiState,
    onUseModelClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

        val isReady = when (settingsUiState.backendMode) {
            LlmBackendMode.GEMINI_CLOUD -> settingsUiState.hasSavedGeminiApiKey
            LlmBackendMode.HYBRID -> settingsUiState.hasSavedGeminiApiKey || modelStatusUiState.activeModelId != null
            else -> modelStatusUiState.activeModelId != null
        }

        Text(
            text = if (isReady) "Model Ready" else "Model Not Initialized",
            color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Backend: ${settingsUiState.backendMode.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (settingsUiState.backendMode == LlmBackendMode.GEMINI_CLOUD || settingsUiState.backendMode == LlmBackendMode.HYBRID) {
            Text(
                text = if (settingsUiState.hasSavedGeminiApiKey) "Gemini API key configured" else "Gemini API key required",
                style = MaterialTheme.typography.bodySmall,
                color = if (settingsUiState.hasSavedGeminiApiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ask Jarvis for help to open the guided command catalog.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isReady) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onUseModelClicked) {
                Text("Initialize Model")
            }
        }
    }
}

@Composable
private fun HelpTab(
    overview: List<HelpBlock>,
    sections: List<CommandSection>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(overview) { block ->
            HelpCard(title = block.title, body = block.body, examples = block.examples)
        }
        items(sections) { section ->
            HelpCard(title = section.title, body = section.description, examples = section.examples)
        }
    }
}

@Composable
private fun LogsTab(logs: List<String>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
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

@Composable
private fun SettingsTab(
    modelStatusUiState: ModelStatusUiState,
    settingsUiState: LlmSettingsUiState,
    onRefreshModels: () -> Unit,
    onModelSelected: (String) -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onBackendModeSelected: (LlmBackendMode) -> Unit,
    onGeminiApiKeyChanged: (String) -> Unit,
    onSaveGeminiApiKey: () -> Unit,
    onClearGeminiApiKey: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("LLM Backend", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            LlmBackendMode.entries.forEach { backend ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = settingsUiState.backendMode == backend,
                        onClick = { onBackendModeSelected(backend) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(backend.label)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (settingsUiState.backendMode == LlmBackendMode.GEMINI_CLOUD || settingsUiState.backendMode == LlmBackendMode.HYBRID) {
                Text("Gemini API Key", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = settingsUiState.geminiApiKey,
                    onValueChange = onGeminiApiKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.None)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveGeminiApiKey, enabled = settingsUiState.geminiApiKey.isNotBlank()) {
                        Text("Save API Key")
                    }
                    OutlinedButton(onClick = onClearGeminiApiKey, enabled = settingsUiState.hasSavedGeminiApiKey) {
                        Text("Clear Key")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (settingsUiState.hasSavedGeminiApiKey) "Gemini API key is stored securely." else "Save a Gemini key to enable cloud-backed LLM responses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (settingsUiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = settingsUiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            Text("Model Settings", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRefreshModels) {
                Text("Refresh Models")
            }
        }

        items(modelStatusUiState.models) { model ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (model.id == modelStatusUiState.activeModelId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
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
                Text("Downloading: ${modelStatusUiState.downloadProgress}%")
                LinearProgressIndicator(
                    progress = { modelStatusUiState.downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HelpCard(
    title: String,
    body: String,
    examples: List<CommandExample>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (examples.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEachIndexed { index, example ->
                        ExampleRow(index = index, example = example)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleRow(
    index: Int,
    example: CommandExample
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "${index + 1}. \"${example.phrase}\"",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = example.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
