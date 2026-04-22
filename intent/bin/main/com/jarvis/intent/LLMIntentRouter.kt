package com.jarvis.intent

import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger

class LLMIntentRouter(
    private val fallbackRouter: IntentRouter? = null,
    private val logger: JarvisLogger = NoOpJarvisLogger,
    private val tinyClassifier: TinyIntentClassifier = TinyIntentClassifier(),
    private val onClassified: (ClassifierTrace) -> Unit = {}
) : IntentRouter {

    override suspend fun parse(input: String): IntentResult {
        // Stage 1: fast deterministic routing for known commands.
        val ruleBased = fallbackRouter?.parse(input)
        if (ruleBased != null && ruleBased.intent != "UNKNOWN") {
            onClassified(
                ClassifierTrace(
                    input = input,
                    source = "rule",
                    intent = ruleBased.intent,
                    confidence = ruleBased.confidence,
                    entities = ruleBased.entities
                )
            )
            return ruleBased
        }

        val tiny = tinyClassifier.classify(input)
        if (tiny != null) {
            onClassified(
                ClassifierTrace(
                    input = input,
                    source = "tiny",
                    intent = tiny.intent,
                    confidence = tiny.confidence,
                    entities = tiny.entities
                )
            )
            return tiny
        }

        logger.info("intent", "No deterministic match from classifier", mapOf("inputLength" to input.length))
        onClassified(
            ClassifierTrace(
                input = input,
                source = "classifier-none",
                intent = "UNKNOWN",
                confidence = 0.0,
                entities = emptyMap()
            )
        )
        return IntentResult("UNKNOWN", 0.0, emptyMap())
    }
}
