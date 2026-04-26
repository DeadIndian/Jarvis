package com.jarvis.execution

import com.jarvis.memory.models.MemoryChunk

interface RemoteAgentClient {
    suspend fun process(request: RemoteRequest): RemoteResponse
}

data class RemoteRequest(
    val input: String,
    val memoryContext: List<MemoryChunk>,
    val metadata: Map<String, String> = emptyMap()
)

data class RemoteResponse(
    val text: String,
    val actions: List<RemoteAction> = emptyList()
)

data class RemoteAction(
    val type: String,
    val payload: Map<String, String>
)
