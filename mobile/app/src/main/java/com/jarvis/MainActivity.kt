package com.jarvis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
import com.jarvis.impl.DefaultIntentResolver
import com.jarvis.impl.DefaultPolicyEngine
import com.jarvis.impl.ManualWakeWordEngine
import com.jarvis.impl.StarterCommands
import com.jarvis.policy.AssistantMode

class MainActivity : AppCompatActivity() {
    private val requestMicCode = 1101

    private lateinit var orchestrator: AssistantOrchestrator
    private lateinit var policyEngine: DefaultPolicyEngine
    private lateinit var wakeWordEngine: ManualWakeWordEngine
    private lateinit var sttEngine: AndroidSpeechToTextEngine
    private lateinit var ttsEngine: AndroidTextToSpeechEngine
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val registry = CommandRegistryImpl()
        StarterCommands.all().forEach(registry::register)

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
}
