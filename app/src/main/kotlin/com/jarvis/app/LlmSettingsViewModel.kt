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

data class LlmSettingsUiState(
    val backendMode: LlmBackendMode = LlmBackendMode.fromConfig(BuildConfig.JARVIS_LLM_BACKEND),
    val geminiApiKey: String = "",
    val hasSavedGeminiApiKey: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class LlmSettingsViewModel(
    private val repository: LlmSettingsRepository,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : ViewModel() {
    private val _uiState = MutableStateFlow(LlmSettingsUiState())
    val uiState: StateFlow<LlmSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val backendMode = repository.getBackendMode()
            val savedKey = repository.getGeminiApiKey()
            _uiState.value = _uiState.value.copy(
                backendMode = backendMode,
                geminiApiKey = "",
                hasSavedGeminiApiKey = savedKey != null,
                errorMessage = null
            )
        }
    }

    fun selectBackendMode(mode: LlmBackendMode) {
        repository.setBackendMode(mode)
        _uiState.value = _uiState.value.copy(backendMode = mode, errorMessage = null)
    }

    fun updateGeminiApiKey(value: String) {
        _uiState.value = _uiState.value.copy(geminiApiKey = value, errorMessage = null)
    }

    fun saveGeminiApiKey() {
        val rawKey = _uiState.value.geminiApiKey.trim()
        if (rawKey.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a Gemini API key before saving.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveGeminiApiKey(rawKey)
            _uiState.value = _uiState.value.copy(
                geminiApiKey = "",
                hasSavedGeminiApiKey = true,
                errorMessage = null
            )
        }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearGeminiApiKey()
            _uiState.value = _uiState.value.copy(
                geminiApiKey = "",
                hasSavedGeminiApiKey = false,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
