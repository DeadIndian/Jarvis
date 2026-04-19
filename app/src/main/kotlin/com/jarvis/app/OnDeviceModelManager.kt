package com.jarvis.app

import android.content.Context
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class OnDeviceModelStatus {
    UNKNOWN,
    UNAVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    AVAILABLE
}

enum class OnDeviceModelSource {
    MLKIT,
    IN_APP_PROFILE
}

data class OnDeviceModel(
    val id: String,
    val title: String,
    val source: OnDeviceModelSource,
    val status: OnDeviceModelStatus
)

interface OnDeviceModelManager {
    suspend fun listModels(): List<OnDeviceModel>
    suspend fun getActiveModelId(): String
    suspend fun setActiveModel(modelId: String)
    suspend fun downloadModel(modelId: String)
    suspend fun deleteModel(modelId: String)
    suspend fun complete(prompt: String): String
    suspend fun installedModels(): List<OnDeviceModel>
}

class MlKitNativeModelManager(
    context: Context,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : OnDeviceModelManager {
    private val bridge = MlKitNativeRuntimeBridge()
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun listModels(): List<OnDeviceModel> = withContext(Dispatchers.IO) {
        val mlkitModels = bridge.supportedModelIds.map { modelId ->
            val rawStatus = bridge.checkStatus(modelId)
            OnDeviceModel(
                id = modelId,
                title = bridge.getModelTitle(modelId),
                source = OnDeviceModelSource.MLKIT,
                status = mapStatus(rawStatus)
            )
        }

        val deepSeekModel = OnDeviceModel(
            id = DEEPSEEK_MODEL_ID,
            title = DEEPSEEK_TITLE,
            source = OnDeviceModelSource.IN_APP_PROFILE,
            status = mapStatus(bridge.checkStatus(DEEPSEEK_BACKING_MODEL_ID))
        )

        mlkitModels + deepSeekModel
    }

    override suspend fun getActiveModelId(): String = withContext(Dispatchers.IO) {
        val supported = listModels().map { it.id }
        val persisted = preferences.getString(KEY_ACTIVE_MODEL, null)
        val resolved = if (persisted != null && supported.contains(persisted)) {
            persisted
        } else {
            supported.firstOrNull() ?: throw IllegalStateException("No models available")
        }
        if (persisted != resolved) {
            preferences.edit().putString(KEY_ACTIVE_MODEL, resolved).apply()
        }
        resolved
    }

    override suspend fun setActiveModel(modelId: String) {
        withContext(Dispatchers.IO) {
            val supported = listModels().map { it.id }
            require(supported.contains(modelId)) { "Unsupported model: $modelId" }
            preferences.edit().putString(KEY_ACTIVE_MODEL, modelId).apply()
            logger.info("llm", "Active model updated", mapOf("modelId" to modelId))
        }
    }

    override suspend fun downloadModel(modelId: String) {
        withContext(Dispatchers.IO) {
            if (modelId == DEEPSEEK_MODEL_ID) {
                bridge.downloadModel(DEEPSEEK_BACKING_MODEL_ID)
            } else {
                bridge.downloadModel(modelId)
            }
            logger.info("llm", "Model download completed", mapOf("modelId" to modelId))
        }
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            if (modelId == DEEPSEEK_MODEL_ID) {
                bridge.deleteModel(DEEPSEEK_BACKING_MODEL_ID)
            } else {
                bridge.deleteModel(modelId)
            }
            logger.info("llm", "Model cache cleared", mapOf("modelId" to modelId))
        }
    }

    override suspend fun complete(prompt: String): String {
        val modelId = getActiveModelId()
        return withContext(Dispatchers.IO) {
            if (modelId == DEEPSEEK_MODEL_ID) {
                val profiledPrompt = """
                    You are DeepSeek-R1 style assistant profile running in Jarvis.
                    Be concise, factual, and action-oriented.

                    User request:
                    $prompt
                """.trimIndent()
                bridge.complete(DEEPSEEK_BACKING_MODEL_ID, profiledPrompt)
            } else {
                bridge.complete(modelId, prompt)
            }
        }
    }

    override suspend fun installedModels(): List<OnDeviceModel> {
        return listModels().filter { it.status == OnDeviceModelStatus.AVAILABLE }
    }

    private fun mapStatus(status: Int): OnDeviceModelStatus {
        return when (status) {
            MlKitNativeRuntimeBridge.STATUS_AVAILABLE -> OnDeviceModelStatus.AVAILABLE
            MlKitNativeRuntimeBridge.STATUS_DOWNLOADABLE -> OnDeviceModelStatus.DOWNLOADABLE
            MlKitNativeRuntimeBridge.STATUS_DOWNLOADING -> OnDeviceModelStatus.DOWNLOADING
            MlKitNativeRuntimeBridge.STATUS_UNAVAILABLE -> OnDeviceModelStatus.UNAVAILABLE
            else -> OnDeviceModelStatus.UNKNOWN
        }
    }

    companion object {
        private const val PREFS_NAME = "jarvis_model_runtime"
        private const val KEY_ACTIVE_MODEL = "active_model"
        private const val DEEPSEEK_MODEL_ID = "deepseek-r1-distill-qwen-1.5b"
        private const val DEEPSEEK_BACKING_MODEL_ID = "mlkit-full-stable"
        private const val DEEPSEEK_TITLE = "DeepSeek R1 Distill Qwen 1.5B (in-app profile)"
    }
}
