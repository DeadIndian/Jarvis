package com.jarvis.app

data class OnDeviceModel(
    val id: String,
    val title: String,
    val source: OnDeviceModelSource,
    val status: OnDeviceModelStatus
)

enum class OnDeviceModelSource {
    LITERT, GOOGLE_AICORE, NONE
}

enum class OnDeviceModelStatus {
    AVAILABLE, DOWNLOADABLE, DOWNLOADING, ERROR
}

interface OnDeviceModelManager {
    suspend fun listModels(): List<OnDeviceModel>
    suspend fun getActiveModelId(): String
    suspend fun setActiveModel(modelId: String)
    suspend fun downloadModel(modelId: String, onProgress: (Int) -> Unit)
    suspend fun deleteModel(modelId: String)
    suspend fun complete(prompt: String): String
    suspend fun installedModels(): List<OnDeviceModel>
}

data class ModelStatusUiState(
    val models: List<OnDeviceModel> = emptyList(),
    val activeModelId: String? = null,
    val downloadingModelId: String? = null,
    val downloadProgress: Int = 0,
    val errorMessage: String? = null
)
