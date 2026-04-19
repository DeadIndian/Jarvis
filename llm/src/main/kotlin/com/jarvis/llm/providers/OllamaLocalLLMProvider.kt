package com.jarvis.llm.providers

import com.jarvis.llm.LLMProvider
import java.net.HttpURLConnection
import java.net.URL

class OllamaLocalLLMProvider(
    private val model: String,
    private val baseUrl: String = "http://127.0.0.1:11434",
    private val temperature: Double = 0.2,
    private val requestTimeoutMs: Int = 60_000
) : LLMProvider {
    override val name: String = "ollama-$model"

    override suspend fun complete(prompt: String): String {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }

        val endpoint = URL(baseUrl.trimEnd('/') + "/api/generate")
        val requestBody = buildRequestBody(prompt)

        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = requestTimeoutMs
            readTimeout = requestTimeoutMs
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json")
        }

        return connection.use {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }

            val statusCode = connection.responseCode
            val raw = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: "no error body"
                throw IllegalStateException("Ollama request failed ($statusCode): $errorBody")
            }

            extractJsonString(raw, "response")?.trim().orEmpty()
        }
    }

    private fun buildRequestBody(prompt: String): String {
        val escapedModel = jsonEscape(model)
        val escapedPrompt = jsonEscape(prompt)
        return """{"model":"$escapedModel","prompt":"$escapedPrompt","stream":false,"options":{"temperature":$temperature}}"""
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = pattern.find(json) ?: return null
        return jsonUnescape(match.groupValues[1])
    }

    private fun jsonEscape(value: String): String {
        val out = StringBuilder(value.length + 8)
        value.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun jsonUnescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch != '\\' || i + 1 >= value.length) {
                out.append(ch)
                i += 1
                continue
            }

            when (val next = value[i + 1]) {
                '\\' -> out.append('\\')
                '"' -> out.append('"')
                '/' -> out.append('/')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (i + 5 < value.length) {
                        val hex = value.substring(i + 2, i + 6)
                        val decoded = hex.toIntOrNull(16)
                        if (decoded != null) {
                            out.append(decoded.toChar())
                            i += 6
                            continue
                        }
                    }
                    out.append('u')
                }
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }
}
