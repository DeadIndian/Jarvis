package com.jarvis.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelStatusUiState(
    val models: List<OnDeviceModel> = emptyList(),
    val activeModelId: String? = null,
    val downloadingModelId: String? = null,
    val downloadProgress: Int = 0, // 0-100
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ModelStatusViewModel(
    private val modelManager: OnDeviceModelManager,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelStatusUiState())
    val uiState: StateFlow<ModelStatusUiState> = _uiState.asStateFlow()

    init {
        refreshModelList()
    }

    fun refreshModelList() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val models = modelManager.listModels()
                val activeModelId = modelManager.getActiveModelId()

                _uiState.value = _uiState.value.copy(
                    models = models,
                    activeModelId = activeModelId,
                    isLoading = false
                )
            } catch (e: Exception) {
                logger.error("model_ui", "Failed to refresh model list", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load models: ${e.message}"
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                modelManager.setActiveModel(modelId)
                _uiState.value = _uiState.value.copy(activeModelId = modelId, errorMessage = null)
            } catch (e: Exception) {
                logger.error("model_ui", "Failed to select model", e, mapOf("modelId" to modelId))
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to select model: ${e.message}"
                )
            }
        }
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    downloadingModelId = modelId,
                    downloadProgress = 0,
                    errorMessage = null
                )

                // Download model and update UI progress continuously when possible.
                modelManager.downloadModel(modelId) { progress ->
                    _uiState.value = _uiState.value.copy(
                        downloadingModelId = modelId,
                        downloadProgress = progress.coerceIn(0, 100),
                        errorMessage = null
                    )
                }

                // Refresh list to get updated status
                val models = modelManager.listModels()
                _uiState.value = _uiState.value.copy(
                    models = models,
                    downloadingModelId = null,
                    downloadProgress = 100,
                    errorMessage = null
                )

                logger.info("model_ui", "Model download complete", mapOf("modelId" to modelId))

                // Clear progress after a moment
                kotlinx.coroutines.delay(500)
                _uiState.value = _uiState.value.copy(
                    downloadingModelId = null,
                    downloadProgress = 0
                )
            } catch (e: Exception) {
                logger.error("model_ui", "Model download failed", e, mapOf("modelId" to modelId))
                _uiState.value = _uiState.value.copy(
                    downloadingModelId = null,
                    downloadProgress = 0,
                    errorMessage = "Download failed: ${e.message}"
                )
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                modelManager.deleteModel(modelId)

                // Refresh list to get updated status
                val models = modelManager.listModels()
                val activeModelId = modelManager.getActiveModelId()

                _uiState.value = _uiState.value.copy(
                    models = models,
                    activeModelId = activeModelId,
                    errorMessage = null
                )

                logger.info("model_ui", "Model deleted", mapOf("modelId" to modelId))
            } catch (e: Exception) {
                logger.error("model_ui", "Model delete failed", e, mapOf("modelId" to modelId))
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Delete failed: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
