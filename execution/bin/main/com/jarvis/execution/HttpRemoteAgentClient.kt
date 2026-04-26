package com.jarvis.execution

import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.json.JSONArray
import org.json.JSONObject

class HttpRemoteAgentClient(
    private val baseUrl: String,
    private val endpoint: String = "/process",
    private val timeoutMs: Long = 3_000L,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : RemoteAgentClient {
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMs
            connectTimeoutMillis = timeoutMs
            socketTimeoutMillis = timeoutMs
        }
    }

    override suspend fun process(request: RemoteRequest): RemoteResponse {
        return runCatching {
            val response = httpClient.post(resolveUrl()) {
                header(HttpHeaders.ContentType, "application/json")
                setBody(request.toJson().toString())
            }

            if (response.status != HttpStatusCode.OK) {
                logger.warn(
                    "remote",
                    "Server request failed",
                    mapOf("status" to response.status.value)
                )
                return RemoteResponse(text = "")
            }

            response.bodyAsText().toRemoteResponse()
        }.getOrElse { throwable ->
            logger.warn(
                "remote",
                "Server request failed",
                mapOf("error" to (throwable.message ?: "unknown"))
            )
            RemoteResponse(text = "")
        }
    }

    private fun resolveUrl(): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedEndpoint = endpoint.trim()
        return if (normalizedEndpoint.startsWith("/")) {
            "$normalizedBase$normalizedEndpoint"
        } else {
            "$normalizedBase/$normalizedEndpoint"
        }
    }

    private fun RemoteRequest.toJson(): JSONObject {
        val memoryArray = JSONArray().apply {
            memoryContext.forEach { chunk ->
                put(
                    JSONObject()
                        .put("id", chunk.id)
                        .put("text", chunk.text)
                        .put("filePath", chunk.filePath)
                        .put("section", chunk.section)
                        .put("timestamp", chunk.timestamp)
                        .put("tokenCount", chunk.tokenCount)
                        .put("embedding", JSONArray(chunk.embedding.toList()))
                )
            }
        }

        val metadataObject = JSONObject().apply {
            metadata.forEach { (key, value) -> put(key, value) }
        }

        return JSONObject()
            .put("input", input)
            .put("memoryContext", memoryArray)
            .put("metadata", metadataObject)
    }

    private fun String.toRemoteResponse(): RemoteResponse {
        if (isBlank()) {
            return RemoteResponse(text = "")
        }

        val root = JSONObject(this)
        val text = root.optString("text", "")
        val actionArray = root.optJSONArray("actions") ?: JSONArray()
        val actions = buildList {
            for (index in 0 until actionArray.length()) {
                val actionObject = actionArray.optJSONObject(index) ?: continue
                val payloadObject = actionObject.optJSONObject("payload") ?: JSONObject()
                val payload = buildMap {
                    val keys = payloadObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, payloadObject.optString(key, ""))
                    }
                }
                add(
                    RemoteAction(
                        type = actionObject.optString("type", ""),
                        payload = payload
                    )
                )
            }
        }
        return RemoteResponse(text = text, actions = actions)
    }
}
