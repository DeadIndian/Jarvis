package com.jarvis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.core.config.AgentConfigRepository
import com.jarvis.app.core.config.McpServer
import com.jarvis.app.core.config.ToolModel

/** Callbacks the host Activity wires to real download/delete side effects. */
data class ToolModelActions(
    val onDownload: (ToolModel) -> Unit,
    val onDelete: (ToolModel) -> Unit,
    val isDownloaded: (ToolModel) -> Boolean,
    val downloadProgress: (String) -> Int?   // model name -> 0..100 or null if idle
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: AgentConfigRepository,
    toolModelActions: ToolModelActions,
    onBack: () -> Unit
) {
    val darkBg = Color(0xFF0D1117)
    val panelBg = Color(0xFF161B22)
    val cyan = Color(0xFF00F0FF)
    val textWhite = Color(0xFFF0F6FC)

    // Agent config state.
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiKey by remember { mutableStateOf(config.apiKey ?: "") }
    var model by remember { mutableStateOf(config.model) }

    // Lists (re-read from repo on change).
    var mcpServers by remember { mutableStateOf(config.getMcpServers()) }
    var toolModels by remember { mutableStateOf(config.getToolModels()) }

    Scaffold(
        containerColor = darkBg,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = cyan, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = panelBg)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().background(darkBg)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- Intelligent agent model ---
            SectionHeader("Intelligent Agent", cyan)
            DarkField("Base URL", baseUrl, panelBg, textWhite, cyan) { baseUrl = it }
            DarkField("API Key", apiKey, panelBg, textWhite, cyan, isPassword = true) { apiKey = it }
            DarkField("Model", model, panelBg, textWhite, cyan) { model = it }
            Button(
                onClick = {
                    config.baseUrl = baseUrl
                    config.apiKey = apiKey
                    config.model = model
                },
                colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = darkBg)
            ) { Text("Save agent config") }
            Text(
                "Changes apply on next app start.",
                color = Color(0xFF8B949E), fontSize = 12.sp
            )

            Divider(color = Color(0xFF30363D))

            // --- MCP servers ---
            SectionHeader("MCP Servers", cyan)
            mcpServers.forEach { s ->
                RowItem("${s.name}  ·  ${s.url}", panelBg, textWhite) {
                    config.removeMcpServer(s.name); mcpServers = config.getMcpServers()
                }
            }
            AddMcpForm(panelBg, textWhite, cyan) { name, url, key ->
                config.addMcpServer(McpServer(name, url, key.ifBlank { null }))
                mcpServers = config.getMcpServers()
            }

            Divider(color = Color(0xFF30363D))

            // --- Tool-call models ---
            SectionHeader("On-device Tool Models", cyan)
            toolModels.forEach { m ->
                val progress = toolModelActions.downloadProgress(m.name)
                val downloaded = toolModelActions.isDownloaded(m)
                Column(
                    Modifier.fillMaxWidth().background(panelBg).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name + if (m.active) "  (active)" else "", color = textWhite, fontWeight = FontWeight.Bold)
                            Text(if (downloaded) "Downloaded" else "Not downloaded", color = Color(0xFF8B949E), fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            toolModelActions.onDelete(m)
                            config.removeToolModel(m.name); toolModels = config.getToolModels()
                        }) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFF85149)) }
                    }
                    if (progress != null) LinearProgressIndicator(
                        progress = progress / 100f, color = cyan, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!downloaded) TextButton(onClick = { toolModelActions.onDownload(m) }) {
                            Text("Download", color = cyan)
                        }
                        if (downloaded && !m.active) TextButton(onClick = {
                            config.setActiveToolModel(m.name); toolModels = config.getToolModels()
                        }) { Text("Set active", color = cyan) }
                    }
                }
            }
            AddToolModelForm(panelBg, textWhite, cyan) { name, url ->
                config.addToolModel(ToolModel(name, url))
                toolModels = config.getToolModels()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String, cyan: Color) =
    Text(text, color = cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkField(
    label: String, value: String, panelBg: Color, textWhite: Color, cyan: Color,
    isPassword: Boolean = false, onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = Color(0xFF8B949E)) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textWhite, unfocusedTextColor = textWhite,
            focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF30363D),
            cursorColor = cyan
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RowItem(text: String, panelBg: Color, textWhite: Color, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(panelBg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = textWhite, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFF85149)) }
    }
}

@Composable
private fun AddMcpForm(panelBg: Color, textWhite: Color, cyan: Color, onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DarkField("Name", name, panelBg, textWhite, cyan) { name = it }
        DarkField("URL (…/mcp)", url, panelBg, textWhite, cyan) { url = it }
        DarkField("API key (optional)", key, panelBg, textWhite, cyan, isPassword = true) { key = it }
        Button(
            enabled = name.isNotBlank() && url.isNotBlank(),
            onClick = { onAdd(name, url, key); name = ""; url = ""; key = "" },
            colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = Color(0xFF0D1117))
        ) { Text("Add MCP server") }
    }
}

@Composable
private fun AddToolModelForm(panelBg: Color, textWhite: Color, cyan: Color, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DarkField("Model name", name, panelBg, textWhite, cyan) { name = it }
        DarkField("Download URL", url, panelBg, textWhite, cyan) { url = it }
        Button(
            enabled = name.isNotBlank() && url.isNotBlank(),
            onClick = { onAdd(name, url); name = ""; url = "" },
            colors = ButtonDefaults.buttonColors(containerColor = cyan, contentColor = Color(0xFF0D1117))
        ) { Text("Add tool model") }
    }
}
