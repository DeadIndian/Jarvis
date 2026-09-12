package com.jarvis.app.core.session

import android.content.Context
import android.util.Log
import com.jarvis.app.core.audio.LocalAudioFeedback
import com.jarvis.app.core.audio.LocalTtsEngine
import com.jarvis.app.core.contracts.ActionEvent
import com.jarvis.app.core.contracts.PerceptionEvent
import com.jarvis.app.core.router.DeterministicRouter
import com.jarvis.app.core.router.RoutingResult
import com.jarvis.app.core.security.ActionValidator
import com.jarvis.app.core.security.ValidationResult
import com.jarvis.app.core.llm.AgentBackend
import com.jarvis.app.core.llm.LLMBackend
import com.jarvis.app.core.llm.LLMResult
import com.jarvis.app.core.memory.ConversationMemory
import com.jarvis.app.core.tools.PhoneToolExecutor
import com.jarvis.app.core.tools.PhoneToolJsonParser
import com.jarvis.app.core.tools.PhoneToolRegistry
import com.jarvis.app.core.trigger.TriggerSource
import com.jarvis.app.core.trigger.TriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

interface VoiceSessionListener {
    fun onSessionStarted(sessionId: String, triggerType: TriggerType)
    fun onTranscriptReceived(sessionId: String, transcript: String)
    fun onRoutingDecision(sessionId: String, result: RoutingResult)
    fun onActionExecuted(sessionId: String, action: ActionEvent)
    fun onError(sessionId: String, error: String)
}

/**
 * VoiceSessionManager orchestrates the complete phone voice session lifecycle.
 */
class VoiceSessionManager(
    private val context: Context,
    private val audioFeedback: LocalAudioFeedback,
    private val ttsEngine: LocalTtsEngine,
    private val phoneToolExecutor: PhoneToolExecutor,
    private val llmBackend: LLMBackend? = null,
    private val agentBackend: AgentBackend? = null,
    private val sessionListener: VoiceSessionListener? = null,
    // Fires with every assistant reply just before TTS, so UIs can show it. TTS still handled here.
    private val onSpeak: ((String) -> Unit)? = null
) {
    private val toolRegistry = PhoneToolRegistry(phoneToolExecutor)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeSessionId: String? = null
    private var isSessionActive = false

    // Token-bounded conversation window shared by every response path.
    private val memory = ConversationMemory()

    /** Single place all spoken replies go through: mirror to any UI, then TTS. */
    private fun say(text: String) {
        onSpeak?.invoke(text)
        ttsEngine.speak(text)
    }

    /**
     * Entry point for any TriggerSource activation.
     */
    fun startSession(triggerSource: TriggerSource) {
        val sessionId = "session_" + UUID.randomUUID().toString().take(8)

        // 1. Interruption / Barge-in
        if (isSessionActive) {
            ttsEngine.stopSpeaking()
        }

        activeSessionId = sessionId
        isSessionActive = true
        sessionListener?.onSessionStarted(sessionId, triggerSource.type)

        // 2. Immediate local cue sound ("ding") < 250ms
        audioFeedback.playActivationDing()

        Log.i(TAG, "VoiceSession $sessionId started from trigger: ${triggerSource.type}")
    }

    /**
     * Processes captured transcript from local STT engine.
     */
    fun processTranscript(transcript: String) {
        val sessionId = activeSessionId ?: return
        sessionListener?.onTranscriptReceived(sessionId, transcript)

        val perceptionEvent = PerceptionEvent(
            sessionId = sessionId,
            transcript = transcript
        )
        Log.d(TAG, "Processing transcript: '${perceptionEvent.transcript}' for session $sessionId")

        memory.addUser(transcript)

        // 3. Local Deterministic Router
        val routingResult = DeterministicRouter.route(transcript)
        sessionListener?.onRoutingDecision(sessionId, routingResult)

        when (routingResult) {
            is RoutingResult.LocalTool -> {
                executeLocalTool(sessionId, routingResult.action)
            }
            is RoutingResult.Ambiguous -> {
                // Simple/ambiguous phone command: try on-device model (B) first, fall back to cloud agent (A).
                Log.i(TAG, "Ambiguous for $sessionId; trying on-device model, then agent.")
                scope.launch {
                    if (!tryOnDeviceTool(sessionId, transcript)) runAgent(sessionId, transcript)
                }
            }
            is RoutingResult.ServerHandoff -> {
                // Complex/conversational: straight to cloud agent (A). Skip B entirely.
                Log.i(TAG, "Complex for $sessionId; routing to cloud agent.")
                scope.launch { runAgent(sessionId, transcript) }
            }
        }
    }

    /**
     * On-device path (B): FunctionGemma emits a phone tool call as JSON. If it produces a valid
     * phone tool, execute it and return true. Otherwise return false to fall through to the agent.
     */
    private suspend fun tryOnDeviceTool(sessionId: String, transcript: String): Boolean {
        val backend = llmBackend ?: return false
        val result = backend.complete(transcript, memory.window())
        val text = (result as? LLMResult.Text)?.text ?: return false
        val action = PhoneToolJsonParser.parse(text) ?: return false
        withContext(Dispatchers.Main) { executeLocalTool(sessionId, action) }
        return true
    }

    /** Cloud agent path (A): full tool loop with MCP long-term memory + short-term window. */
    private suspend fun runAgent(sessionId: String, transcript: String) {
        val agent = agentBackend
        if (agent == null) {
            // No agent configured; try the plain on-device model as a last resort.
            attemptLocalLLM(sessionId, transcript)
            return
        }
        val result = agent.run(memory.window())
        memory.addAssistant(result.text)
        withContext(Dispatchers.Main) {
            say(result.text)
            result.actions.forEach { sessionListener?.onActionExecuted(sessionId, it) }
            if (result.actions.isEmpty()) sessionListener?.onActionExecuted(sessionId, ActionEvent.SpeakAction(result.text))
        }
    }

    /**
     * Validates and executes a local phone action safely.
     */
    fun executeLocalTool(sessionId: String, action: ActionEvent.PhoneToolAction) {
        val validation = ActionValidator.validate(action)
        when (validation) {
            is ValidationResult.Invalid -> {
                Log.w(TAG, "Action validation failed: ${validation.reason}")
                say("Cannot execute action. ${validation.reason}")
                sessionListener?.onError(sessionId, validation.reason)
            }
            // ponytail: no modal confirm dialog — the spoken "Calling X" + system dialer (ACTION_DIAL
            // fallback when CALL_PHONE ungranted) IS the confirmation. Add a real dialog if calls
            // ever fire without user intent.
            is ValidationResult.ValidWithConfirmation -> runTool(sessionId, action)
            is ValidationResult.Valid -> runTool(sessionId, action)
        }
    }

    private fun runTool(sessionId: String, action: ActionEvent.PhoneToolAction) {
        Log.i(TAG, "Executing phone tool '${action.tool}'")
        val success = toolRegistry.execute(action)
        if (success) {
            val summary = getSpokenConfirmation(action)
            memory.addToolResult(action.tool, summary)
            say(summary)
            sessionListener?.onActionExecuted(sessionId, action)
        } else {
            val failure = "Failed to execute ${action.tool}."
            memory.addToolResult(action.tool, failure)
            say(failure)
            sessionListener?.onError(sessionId, "Tool execution failed")
        }
    }

    private fun attemptLocalLLM(sessionId: String, transcript: String) {
        val backend = llmBackend ?: run {
            say("I can't handle that yet. Try a phone command like set volume or open an app.")
            return
        }
        scope.launch {
            val result = backend.complete(transcript, memory.window())
            when (result) {
                is LLMResult.Text -> {
                    if (result.text.isNotBlank()) {
                        memory.addAssistant(result.text)
                        say(result.text)
                        sessionListener?.onActionExecuted(sessionId, ActionEvent.SpeakAction(result.text))
                    } else {
                        say("I couldn't find an answer for that.")
                    }
                }
                is LLMResult.Unavailable -> {
                    say("I don't have a local model or cloud key configured for that yet.")
                }
            }
        }
    }

    fun stopSession() {
        activeSessionId = null
        isSessionActive = false
    }

    private fun getSpokenConfirmation(action: ActionEvent.PhoneToolAction): String {
        return when (action.tool) {
            "set_volume" -> "Volume set to ${action.arguments["level"]}%."
            "toggle_bluetooth" -> if (action.arguments["enable"] == true) "Bluetooth enabled." else "Bluetooth disabled."
            "toggle_wifi" -> if (action.arguments["enable"] == true) "Wi-Fi enabled." else "Wi-Fi disabled."
            "media_control" -> "Media ${action.arguments["action"]} command sent."
            "open_app" -> "Opening ${action.arguments["app_name"]}."
            "make_call" -> "Calling ${action.arguments["phone_number"]}."
            "send_whatsapp" -> "Opening WhatsApp to ${action.arguments["recipient"]}. Tap send when ready."
            "read_notifications" -> "Reading your notifications."
            "toggle_flashlight" -> if (action.arguments["enable"] == true) "Flashlight on." else "Flashlight off."
            else -> "Action ${action.tool} executed."
        }
    }

    companion object {
        private const val TAG = "VoiceSessionManager"
    }
}
