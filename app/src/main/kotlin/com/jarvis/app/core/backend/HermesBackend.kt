package com.jarvis.app.core.backend

import android.util.Log
import com.jarvis.app.core.contracts.ActionEvent
import com.jarvis.app.core.contracts.PerceptionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * HermesBackend: streams perception to Hermes on the home server, maps SSE events to ActionEvents.
 * Falls back gracefully if the server is unreachable.
 */
class HermesBackend(
    private val baseUrl: String,
    private val apiKey: String,
    private val sessionKey: String
) : Backend {

    private val tag = "HermesBackend"

    override suspend fun handle(event: PerceptionEvent): Flow<ActionEvent> = flow {
        val runId = createRun(event) ?: run {
            emit(ActionEvent.SpeakAction("Hermes server not reachable. Using local mode."))
            return@flow
        }

        streamEvents(runId, event)
    }

    private suspend fun createRun(event: PerceptionEvent): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("input", event.transcript)
                    put("session_id", event.sessionId)
                }
                val conn = (URL("$baseUrl/v1/runs").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Hermes-Session-Key", sessionKey)
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 10000
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                if (conn.responseCode == 200) {
                    JSONObject(conn.inputStream.bufferedReader().readText())
                        .optString("run_id")
                        .takeIf { !it.isNullOrEmpty() }
                } else {
                    Log.w(tag, "Hermes createRun failed: ${conn.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.w(tag, "Hermes unreachable: ${e.message}")
                null
            }
        }

    private suspend fun streamEvents(runId: String, event: PerceptionEvent) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = URL("$baseUrl/v1/runs/$runId/events")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("X-Hermes-Session-Key", sessionKey)
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 5000
                readTimeout = 0
            }
            val reader = conn.inputStream.bufferedReader()
            reader.useLines { lines ->
                // Stream parsing is placeholder; real impl parses SSE "data:" lines.
                // Maps message.interim/tool.started/.../run.completed to ActionEvents.
            }
        }
}
