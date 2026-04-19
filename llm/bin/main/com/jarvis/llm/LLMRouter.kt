package com.jarvis.llm

data class LLMRequest(val prompt: String, val allowCloudFallback: Boolean)
data class LLMResponse(val text: String, val provider: String)

interface LLMRouter {
    suspend fun complete(request: LLMRequest): LLMResponse
}
