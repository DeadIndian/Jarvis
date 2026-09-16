package com.jarvis.app.core.backend

import com.jarvis.app.core.contracts.ActionEvent
import com.jarvis.app.core.contracts.PerceptionEvent
import kotlinx.coroutines.flow.Flow

interface Backend {
    suspend fun handle(event: PerceptionEvent): Flow<ActionEvent>
}
