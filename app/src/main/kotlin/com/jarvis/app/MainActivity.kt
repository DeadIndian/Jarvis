package com.jarvis.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jarvis.core.Event
import com.jarvis.core.InMemoryEventBus
import com.jarvis.execution.PipelineOrchestrator
import com.jarvis.execution.SkillExecutionEngine
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.InMemorySkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var inputText: EditText
    private lateinit var orchestrator: PipelineOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        inputText = findViewById(R.id.inputText)

        orchestrator = buildOrchestrator()

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val input = inputText.text.toString().trim()
            if (input.isNotEmpty()) {
                dispatch(Event.TextInput(input))
            }
        }

        findViewById<Button>(R.id.powerButton).setOnClickListener {
            dispatch(Event.PowerButtonHeld)
        }

        findViewById<Button>(R.id.housePartyOnButton).setOnClickListener {
            dispatch(Event.HousePartyToggle(enabled = true))
        }

        findViewById<Button>(R.id.wakeWordButton).setOnClickListener {
            dispatch(Event.WakeWordDetected("jarvis"))
        }

        findViewById<Button>(R.id.timeoutButton).setOnClickListener {
            dispatch(Event.TimeoutElapsed)
        }

        updateStatus("State: ${orchestrator.currentState().name}")
        appendLog("Jarvis MVP initialized")
    }

    private fun dispatch(event: Event) {
        lifecycleScope.launch(Dispatchers.Default) {
            orchestrator.dispatch(event)
            val state = orchestrator.currentState()
            runOnUiThread {
                updateStatus("State: ${state.name}")
                appendLog("Event: ${event::class.simpleName}")
            }
        }
    }

    private fun buildOrchestrator(): PipelineOrchestrator {
        val eventBus = InMemoryEventBus()
        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill())
        }

        val outputChannel = UiOutputChannel(
            onState = { state -> runOnUiThread { appendLog("Output state: $state") } },
            onSpeak = { text -> runOnUiThread { appendLog("Jarvis: $text") } }
        )

        return PipelineOrchestrator(
            eventBus = eventBus,
            intentRouter = RuleBasedIntentRouter(),
            planner = SimplePlanner(),
            executionEngine = SkillExecutionEngine(skillRegistry),
            outputChannel = outputChannel,
            logger = AndroidLogJarvisLogger()
        )
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    private fun appendLog(line: String) {
        logText.text = logText.text.toString() + "\n" + line
    }
}
