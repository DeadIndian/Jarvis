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

data class MemorySettingsUiState(
    val notesFolderPath: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class MemorySettingsViewModel(
    private val repository: MemorySettingsRepository,
    private val logger: JarvisLogger = NoOpJarvisLogger
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemorySettingsUiState())
    val uiState: StateFlow<MemorySettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val folderPath = repository.getNotesFolderPath() ?: EncryptedMemorySettingsRepository.DEFAULT_NOTES_FOLDER
            _uiState.value = _uiState.value.copy(
                notesFolderPath = folderPath,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun updateNotesFolderPath(path: String) {
        _uiState.value = _uiState.value.copy(notesFolderPath = path.trim(), errorMessage = null, successMessage = null)
    }

    fun saveNotesFolderPath() {
        val path = _uiState.value.notesFolderPath.trim()
        if (path.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter a folder name or select one.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.setNotesFolderPath(path)
                _uiState.value = _uiState.value.copy(
                    errorMessage = null,
                    successMessage = "Notes folder set to: $path (requires app restart)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save: ${e.message}")
            }
        }
    }

    fun clearNotesFolderPath() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearNotesFolderPath()
            _uiState.value = _uiState.value.copy(
                notesFolderPath = EncryptedMemorySettingsRepository.DEFAULT_NOTES_FOLDER,
                successMessage = "Notes folder reset to default (requires app restart)"
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}