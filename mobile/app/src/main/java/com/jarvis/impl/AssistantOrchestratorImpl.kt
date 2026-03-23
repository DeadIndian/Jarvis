package com.jarvis.impl

import com.jarvis.core.ActionExecutor
import com.jarvis.core.AssistantOrchestrator
import com.jarvis.core.CommandRegistry
import com.jarvis.engines.IntentResolver
import com.jarvis.engines.SpeechToTextEngine
import com.jarvis.engines.TextToSpeechEngine
import com.jarvis.engines.WakeWordEngine
import com.jarvis.policy.PolicyEngine

class AssistantOrchestratorImpl(
    private val wakeWordEngine: WakeWordEngine,
    private val sttEngine: SpeechToTextEngine,
    private val intentResolver: IntentResolver,
    private val commandRegistry: CommandRegistry,
    private val policyEngine: PolicyEngine,
    private val actionExecutor: ActionExecutor,
    private val ttsEngine: TextToSpeechEngine
) : AssistantOrchestrator {

    override fun start() {
        commandRegistry.validateOrThrow()
        wakeWordEngine.startListening {
            onWakeEvent()
        }
    }

    override fun stop() {
        wakeWordEngine.stopListening()
        sttEngine.stopListening()
        ttsEngine.stop()
    }

    override fun onWakeEvent(): String {
        val message = if (policyEngine.canActivateNow()) {
            "Listening..."
        } else {
            "Assistant is unavailable in current mode"
        }
        ttsEngine.speak(message)
        return message
    }

    override fun processText(input: String): String {
        if (!policyEngine.canActivateNow()) {
            val blocked = "Blocked by policy (${policyEngine.getMode().name})"
            ttsEngine.speak(blocked)
            return blocked
        }

        val transcript = sttEngine.transcribe(input)
        val normalized = intentResolver.normalize(transcript)
        val command = commandRegistry.resolve(normalized)
            ?: return "I did not understand that command"

        val result = actionExecutor.execute(command, transcript)
        ttsEngine.speak(result.outputText)
        return result.outputText
    }

    override fun processVoice(onResponse: (String) -> Unit, onError: (String) -> Unit) {
        if (!policyEngine.canActivateNow()) {
            val blocked = "Blocked by policy (${policyEngine.getMode().name})"
            ttsEngine.speak(blocked)
            onError(blocked)
            return
        }

        sttEngine.listenOnce(
            onResult = { transcript ->
                val output = processText(transcript)
                onResponse(output)
            },
            onError = { error ->
                onError(error)
            }
        )
    }
}
