package com.jarvis.app

import android.content.Context
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class OnDeviceModelStatus {
    UNKNOWN,
    UNAVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    AVAILABLE
}

enum class OnDeviceModelSource {
    LITERT,
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
    suspend fun downloadModel(modelId: String, onProgress: (Int) -> Unit = {})
    suspend fun deleteModel(modelId: String)
    suspend fun complete(prompt: String): String
    suspend fun installedModels(): List<OnDeviceModel>
}

class LiteRtOnDeviceModelManager(
    private val context: Context,
    private val logger: JarvisLogger = NoOpJarvisLogger,
    private val modelDirectoryPath: String = "",
    private val huggingFaceToken: String = ""
) : OnDeviceModelManager {
    private val bridge = LiteRtRuntimeBridge(context)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelDirectory: File = resolveModelDirectory(context, modelDirectoryPath)

    override suspend fun listModels(): List<OnDeviceModel> = withContext(Dispatchers.IO) {
        ensureModelDirectory()
        val catalog = catalogById()
        val localArtifacts = discoverLocalArtifacts()
        val localModels = localArtifacts.map { artifact ->
            OnDeviceModel(
                id = artifact.id,
                title = artifact.title,
                source = OnDeviceModelSource.LITERT,
                status = OnDeviceModelStatus.AVAILABLE
            )
        }

        val deepSeekStatus = if (localArtifacts.isNotEmpty()) {
            OnDeviceModelStatus.AVAILABLE
        } else {
            OnDeviceModelStatus.DOWNLOADABLE
        }
        val deepSeekModel = OnDeviceModel(
            id = DEEPSEEK_MODEL_ID,
            title = DEEPSEEK_TITLE,
            source = OnDeviceModelSource.IN_APP_PROFILE,
            status = deepSeekStatus
        )

        val downloadableCatalogModels = catalog.values
            .filterNot { spec -> localArtifacts.any { it.id == spec.id } }
            .sortedBy { spec -> spec.sortOrder }
            .map { spec ->
                OnDeviceModel(
                    id = spec.id,
                    title = spec.title,
                    source = OnDeviceModelSource.LITERT,
                    status = OnDeviceModelStatus.DOWNLOADABLE
                )
            }

        downloadableCatalogModels + localModels + deepSeekModel
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

    override suspend fun downloadModel(modelId: String, onProgress: (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            ensureModelDirectory()
            val targetId = if (modelId == DEEPSEEK_MODEL_ID) DEFAULT_BASE_MODEL_ID else modelId
            val catalog = catalogById()[targetId]
                ?: throw IllegalArgumentException(
                    "No downloadable artifact mapped for '$targetId'. Add a .task/.litertlm file to ${modelDirectory.absolutePath}."
                )

            onProgress(1)
            downloadToFile(catalog.downloadUrl, resolveModelFile(catalog.fileName), onProgress)
            onProgress(100)
            logger.info("llm", "LiteRT model download completed", mapOf("modelId" to targetId))
        }
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            if (modelId == DEEPSEEK_MODEL_ID) {
                return@withContext
            }

            val artifact = resolveArtifactById(modelId)
                ?: throw IllegalArgumentException("No local artifact found for model '$modelId'")
            if (artifact.file.delete()) {
                bridge.clear(artifact.file.absolutePath)
                logger.info("llm", "LiteRT model cache cleared", mapOf("modelId" to modelId))
            } else {
                throw IllegalStateException("Failed to delete model artifact: ${artifact.file.absolutePath}")
            }
        }
    }

    override suspend fun complete(prompt: String): String {
        val modelId = getActiveModelId()
        return withContext(Dispatchers.IO) {
            if (modelId == DEEPSEEK_MODEL_ID) {
                val backing = resolveDeepSeekBackingArtifact()
                val profiledPrompt = """
                    You are DeepSeek-R1 style assistant profile running in Jarvis.
                    Be concise, factual, and action-oriented.

                    User request:
                    $prompt
                """.trimIndent()
                bridge.complete(backing.file.absolutePath, profiledPrompt)
            } else {
                val artifact = resolveArtifactById(modelId)
                    ?: throw IllegalStateException(
                        "Active model '$modelId' is missing. Add a .task/.litertlm artifact and refresh."
                    )
                bridge.complete(artifact.file.absolutePath, prompt)
            }
        }
    }

    override suspend fun installedModels(): List<OnDeviceModel> {
        return listModels().filter { it.status == OnDeviceModelStatus.AVAILABLE }
    }

    private fun resolveArtifactById(modelId: String): ModelArtifact? {
        val catalog = catalogById()[modelId]
        if (catalog != null) {
            val file = resolveModelFile(catalog.fileName)
            if (file.isFile) {
                return ModelArtifact(
                    id = catalog.id,
                    title = catalog.title,
                    file = file
                )
            }
        }

        return discoverLocalArtifacts().firstOrNull { it.id == modelId }
    }

    private fun resolveDeepSeekBackingArtifact(): ModelArtifact {
        val discovered = discoverLocalArtifacts()
        return discovered.firstOrNull { it.id == DEFAULT_BASE_MODEL_ID }
            ?: discovered.firstOrNull()
            ?: throw IllegalStateException(
                "No LiteRT model is installed. Download '$DEFAULT_BASE_MODEL_ID' first."
            )
    }

    private fun discoverLocalArtifacts(): List<ModelArtifact> {
        ensureModelDirectory()
        val files = modelDirectory.listFiles().orEmpty()
            .filter { it.isFile && (it.extension.equals("task", true) || it.extension.equals("litertlm", true)) }

        if (files.isEmpty()) {
            return emptyList()
        }

        val catalog = catalogById()
        return files.map { file ->
            val match = catalog.values.firstOrNull { spec ->
                file.name.equals(spec.fileName, ignoreCase = true)
            }

            if (match != null) {
                ModelArtifact(id = match.id, title = match.title, file = file)
            } else {
                val modelId = file.nameWithoutExtension.lowercase()
                ModelArtifact(id = modelId, title = file.nameWithoutExtension, file = file)
            }
        }.distinctBy { it.id }
    }

    private fun catalogById(): Map<String, ModelCatalogEntry> {
        return mapOf(
            DEFAULT_BASE_MODEL_ID to ModelCatalogEntry(
                id = DEFAULT_BASE_MODEL_ID,
                title = DEFAULT_BASE_MODEL_TITLE,
                fileName = DEFAULT_BASE_MODEL_FILE_NAME,
                downloadUrl = DEFAULT_BASE_MODEL_URL,
                sortOrder = 10
            ),
            GEMMA4_E2B_MODEL_ID to ModelCatalogEntry(
                id = GEMMA4_E2B_MODEL_ID,
                title = GEMMA4_E2B_MODEL_TITLE,
                fileName = GEMMA4_E2B_MODEL_FILE_NAME,
                downloadUrl = GEMMA4_E2B_MODEL_URL,
                sortOrder = 20
            ),
            GEMMA4_E4B_MODEL_ID to ModelCatalogEntry(
                id = GEMMA4_E4B_MODEL_ID,
                title = GEMMA4_E4B_MODEL_TITLE,
                fileName = GEMMA4_E4B_MODEL_FILE_NAME,
                downloadUrl = GEMMA4_E4B_MODEL_URL,
                sortOrder = 30
            )
        )
    }

    private fun resolveModelFile(fileName: String): File {
        return File(modelDirectory, fileName)
    }

    private fun ensureModelDirectory() {
        // Avoid false negatives when concurrent calls race to create the directory.
        val exists = modelDirectory.exists()
        if (!exists && !modelDirectory.mkdirs() && !modelDirectory.exists()) {
            throw IllegalStateException("Failed to create model directory: ${modelDirectory.absolutePath}")
        }

        if (modelDirectory.exists() && !modelDirectory.isDirectory) {
            throw IllegalStateException("Model path is not a directory: ${modelDirectory.absolutePath}")
        }
    }

    private fun downloadToFile(downloadUrl: String, target: File, onProgress: (Int) -> Unit) {
        val tmp = File(target.absolutePath + ".part")
        if (tmp.exists()) {
            tmp.delete()
        }

        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            if (huggingFaceToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $huggingFaceToken")
            }
        }

        val code = connection.responseCode
        if (code !in 200..299) {
            val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?: connection.inputStream?.bufferedReader()?.use { it.readText() }
                ?: "no response body"
            val hint = when (code) {
                401, 403 -> "This model requires Hugging Face authentication or license acceptance. Set JARVIS_HF_TOKEN or place a .litertlm/.task file in ${modelDirectory.absolutePath}."
                404 -> "Configured model artifact was not found. Check the model URL and filename."
                else -> "Model download failed with HTTP $code"
            }
            throw IllegalStateException("$hint Response: $responseBody")
        }

        val totalBytes = connection.contentLengthLong
        connection.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L
                var lastProgress = 1
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    downloadedBytes += read

                    if (totalBytes > 0) {
                        val computed = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(1, 99)
                        if (computed > lastProgress) {
                            lastProgress = computed
                            onProgress(computed)
                        }
                    } else if (lastProgress < 10) {
                        lastProgress = 10
                        onProgress(lastProgress)
                    }
                }
            }
        }

        if (target.exists()) {
            target.delete()
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun resolveModelDirectory(context: Context, overridePath: String): File {
        val trimmed = overridePath.trim()
        if (trimmed.isNotEmpty()) {
            return File(trimmed)
        }
        return File(context.filesDir, "llm")
    }

    private data class ModelCatalogEntry(
        val id: String,
        val title: String,
        val fileName: String,
        val downloadUrl: String,
        val sortOrder: Int
    )

    private data class ModelArtifact(
        val id: String,
        val title: String,
        val file: File
    )

    companion object {
        private const val PREFS_NAME = "jarvis_model_runtime"
        private const val KEY_ACTIVE_MODEL = "active_model"
        private const val DEEPSEEK_MODEL_ID = "deepseek-r1-distill-qwen-1.5b"
        private const val DEEPSEEK_TITLE = "DeepSeek-style profile (LiteRT local runtime)"
        private const val DEFAULT_BASE_MODEL_ID = "gemma-3-1b-it-q4"
        private const val DEFAULT_BASE_MODEL_TITLE = "Gemma 3 1B IT (LiteRT .litertlm)"
        private const val DEFAULT_BASE_MODEL_FILE_NAME = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
        private const val DEFAULT_BASE_MODEL_URL = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"

        private const val GEMMA4_E2B_MODEL_ID = "gemma-4-e2b-it"
        private const val GEMMA4_E2B_MODEL_TITLE = "Gemma 4 E2B IT (LiteRT-LM .litertlm)"
        private const val GEMMA4_E2B_MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val GEMMA4_E2B_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        private const val GEMMA4_E4B_MODEL_ID = "gemma-4-e4b-it"
        private const val GEMMA4_E4B_MODEL_TITLE = "Gemma 4 E4B IT (LiteRT-LM .litertlm)"
        private const val GEMMA4_E4B_MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm"
        private const val GEMMA4_E4B_MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    }
}
