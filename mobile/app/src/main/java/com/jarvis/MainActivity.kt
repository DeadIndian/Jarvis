package com.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.core.AssistantOrchestrator
import com.jarvis.impl.ActionExecutorImpl
import com.jarvis.impl.AssistantOrchestratorImpl
import com.jarvis.impl.AndroidSpeechToTextEngine
import com.jarvis.impl.AndroidTextToSpeechEngine
import com.jarvis.impl.CommandRegistryImpl
import com.jarvis.impl.CustomActionType
import com.jarvis.impl.CustomCommandEntry
import com.jarvis.impl.CustomCommandStore
import com.jarvis.impl.DefaultIntentResolver
import com.jarvis.impl.DefaultPolicyEngine
import com.jarvis.impl.ManualWakeWordEngine
import com.jarvis.impl.StarterCommands
import com.jarvis.policy.AssistantMode

class MainActivity : AppCompatActivity() {
    private val requestMicCode = 1101

    private lateinit var registry: CommandRegistryImpl
    private lateinit var orchestrator: AssistantOrchestrator
    private lateinit var policyEngine: DefaultPolicyEngine
    private lateinit var wakeWordEngine: ManualWakeWordEngine
    private lateinit var sttEngine: AndroidSpeechToTextEngine
    private lateinit var ttsEngine: AndroidTextToSpeechEngine
    private lateinit var customCommandStore: CustomCommandStore
    private lateinit var statusText: TextView
    private lateinit var customCommandsPreview: TextView

    private val customCommands = mutableListOf<CustomCommandEntry>()
    private val customActionOptions = CustomActionType.entries.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registry = CommandRegistryImpl()
        StarterCommands.all().forEach(registry::register)
        customCommandStore = CustomCommandStore(this)
        customCommands.addAll(customCommandStore.loadAll())
        customCommands.forEach { registry.register(it.toCommandDefinition()) }

        wakeWordEngine = ManualWakeWordEngine()
        policyEngine = DefaultPolicyEngine()
        sttEngine = AndroidSpeechToTextEngine(this)
        ttsEngine = AndroidTextToSpeechEngine(this)

        orchestrator = AssistantOrchestratorImpl(
            wakeWordEngine = wakeWordEngine,
            sttEngine = sttEngine,
            intentResolver = DefaultIntentResolver(),
            commandRegistry = registry,
            policyEngine = policyEngine,
            actionExecutor = ActionExecutorImpl(this),
            ttsEngine = ttsEngine
        )
        orchestrator.start()

        statusText = findViewById(R.id.statusText)
        val commandInput = findViewById<EditText>(R.id.commandInput)
        val customKeywordsInput = findViewById<EditText>(R.id.customKeywordsInput)
        val customActionSpinner = findViewById<Spinner>(R.id.customActionSpinner)
        val customResponseInput = findViewById<EditText>(R.id.customResponseInput)
        customCommandsPreview = findViewById(R.id.customCommandsPreview)

        configureActionSpinner(customActionSpinner)
        refreshCustomCommandsPreview()

        findViewById<Button>(R.id.wakeButton).setOnClickListener {
            wakeWordEngine.triggerWake()
            setStatus("Wake triggered")
        }

        findViewById<Button>(R.id.runCommandButton).setOnClickListener {
            val input = commandInput.text.toString()
            if (input.isBlank()) {
                setStatus("Enter a command first")
                return@setOnClickListener
            }
            val output = orchestrator.processText(input)
            setStatus(output)
        }

        findViewById<Button>(R.id.speakCommandButton).setOnClickListener {
            runVoiceCommand()
        }

        findViewById<Button>(R.id.saveCustomCommandButton).setOnClickListener {
            saveCustomCommand(
                keywordsRaw = customKeywordsInput.text.toString(),
                actionIndex = customActionSpinner.selectedItemPosition,
                responseText = customResponseInput.text.toString(),
                keywordsField = customKeywordsInput,
                responseField = customResponseInput
            )
        }

        findViewById<Button>(R.id.modeActiveButton).setOnClickListener {
            setMode(AssistantMode.ACTIVE)
        }

        findViewById<Button>(R.id.modeWorkButton).setOnClickListener {
            setMode(AssistantMode.WORK)
        }

        findViewById<Button>(R.id.modeSleepButton).setOnClickListener {
            setMode(AssistantMode.SLEEP)
        }

        findViewById<Button>(R.id.modeOffButton).setOnClickListener {
            setMode(AssistantMode.MANUAL_OFF)
        }

        setStatus("Ready in mode: ${policyEngine.getMode().name}")
    }

    override fun onDestroy() {
        orchestrator.stop()
        sttEngine.destroy()
        ttsEngine.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestMicCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runVoiceCommand()
            } else {
                setStatus("Microphone permission denied")
            }
        }
    }

    private fun runVoiceCommand() {
        if (!hasMicPermission()) {
            requestMicPermission()
            return
        }

        setStatus("Listening...")
        orchestrator.processVoice(
            onResponse = { response ->
                runOnUiThread { setStatus(response) }
            },
            onError = { error ->
                runOnUiThread { setStatus(error) }
            }
        )
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            requestMicCode
        )
    }

    private fun setMode(mode: AssistantMode) {
        policyEngine.setMode(mode)
        setStatus("Mode changed to ${mode.name}")
    }

    private fun setStatus(message: String) {
        statusText.text = "Status: $message"
    }

    private fun configureActionSpinner(spinner: Spinner) {
        val labels = customActionOptions.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun saveCustomCommand(
        keywordsRaw: String,
        actionIndex: Int,
        responseText: String,
        keywordsField: EditText,
        responseField: EditText
    ) {
        val keywords = keywordsRaw
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()

        if (keywords.isEmpty()) {
            setStatus("Add at least one keyword or phrase")
            return
        }

        val action = customActionOptions.getOrElse(actionIndex) { CustomActionType.REPLY }
        if (action == CustomActionType.REPLY && responseText.isBlank()) {
            setStatus("Response text is required for reply action")
            return
        }

        val hasDuplicateKeyword = customCommands.any { entry ->
            entry.keywords.any { existing -> keywords.contains(existing.lowercase()) }
        }
        if (hasDuplicateKeyword) {
            setStatus("One of these keywords is already mapped")
            return
        }

        val entry = CustomCommandEntry(
            id = customCommandStore.nextId(),
            keywords = keywords,
            actionKey = action.key,
            responseText = responseText.trim()
        )

        customCommandStore.add(entry)
        customCommands.add(entry)
        registry.register(entry.toCommandDefinition())

        keywordsField.text.clear()
        responseField.text.clear()
        refreshCustomCommandsPreview()
        setStatus("Saved custom command: ${keywords.joinToString(" / ")}")
    }

    private fun refreshCustomCommandsPreview() {
        if (customCommands.isEmpty()) {
            customCommandsPreview.text = "No custom commands yet"
            return
        }

        val preview = customCommands
            .takeLast(8)
            .joinToString(separator = "\n") { entry ->
                val actionLabel = CustomActionType.fromKey(entry.actionKey).label
                val response = entry.responseText.ifBlank { "(default response)" }
                "${entry.keywords.joinToString(" | ")} -> $actionLabel | $response"
            }
        customCommandsPreview.text = preview
    }
}
