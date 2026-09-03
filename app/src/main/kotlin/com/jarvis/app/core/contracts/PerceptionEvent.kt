package com.jarvis.app.core.contracts

import org.json.JSONObject

/**
 * PerceptionEvent represents a perception frame captured on the phone
 * and sent to the server brain or local router.
 */
data class PerceptionEvent(
    val sessionId: String,
    val transcript: String,
    val source: String = "phone",
    val timestamp: Long = System.currentTimeMillis(),
    val speakerId: String = "unknown",
    val speakerConfidence: Float = 0.0f,
    val emotionLabel: String = "neutral",
    val emotionConfidence: Float = 0.0f
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", "perception_event")
            put("session_id", sessionId)
            put("transcript", transcript)
            put("source", source)
            put("timestamp", timestamp)
            put("speaker", JSONObject().apply {
                put("id", speakerId)
                put("confidence", speakerConfidence.toDouble())
            })
            put("emotion", JSONObject().apply {
                put("label", emotionLabel)
                put("confidence", emotionConfidence.toDouble())
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PerceptionEvent {
            val speaker = json.optJSONObject("speaker")
            val emotion = json.optJSONObject("emotion")
            return PerceptionEvent(
                sessionId = json.optString("session_id", ""),
                transcript = json.optString("transcript", ""),
                source = json.optString("source", "phone"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                speakerId = speaker?.optString("id", "unknown") ?: "unknown",
                speakerConfidence = speaker?.optDouble("confidence", 0.0)?.toFloat() ?: 0.0f,
                emotionLabel = emotion?.optString("label", "neutral") ?: "neutral",
                emotionConfidence = emotion?.optDouble("confidence", 0.0)?.toFloat() ?: 0.0f
            )
        }
    }
}
