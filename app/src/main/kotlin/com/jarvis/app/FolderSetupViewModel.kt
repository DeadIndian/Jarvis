package com.jarvis.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderSetupViewModel(
    private val context: Context,
    private val logger: JarvisLogger = NoOpJarvisLogger,
    private val llmProvider: LLMProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderSetupUiState())
    val uiState: StateFlow<FolderSetupUiState> = _uiState.asStateFlow()

    private val setupManager = FolderSetupManager(context, logger)

    fun checkFolderSetup(basePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, errorMessage = null)
            
            try {
                val result = setupManager.checkFolderSetup(basePath)
                
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    needsSetup = result.needsSetup,
                    hasMalformedFiles = result.hasMalformedFiles,
                    malformedFileCount = result.malformedFiles.size,
                    showSetupDialog = result.needsSetup && !result.hasMalformedFiles,
                    showMalformedDialog = result.hasMalformedFiles
                )

                logger.info("FolderSetup", "Check complete: needsSetup=${result.needsSetup}, hasMalformed=${result.hasMalformedFiles}")
            } catch (e: Exception) {
                logger.error("FolderSetup", "Failed to check folder setup: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    errorMessage = "Failed to check folder: ${e.message}"
                )
            }
        }
    }

    fun setupFolder(basePath: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSettingUp = true,
                showSetupDialog = false,
                errorMessage = null
            )

            try {
                val success = setupManager.setupFolder(basePath)
                
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isSettingUp = false,
                        needsSetup = false,
                        successMessage = "Folder structure created successfully"
                    )
                    onComplete?.invoke()
                    logger.info("FolderSetup", "Folder setup complete")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSettingUp = false,
                        errorMessage = "Failed to create some directories"
                    )
                }
            } catch (e: Exception) {
                logger.error("FolderSetup", "Failed to setup folder: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isSettingUp = false,
                    errorMessage = "Setup failed: ${e.message}"
                )
            }
        }
    }

    fun classifyAndOrganizeFiles(basePath: String, provider: LLMProvider, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isClassifying = true,
                showMalformedDialog = false,
                classificationProgress = 0,
                totalFilesToClassify = _uiState.value.malformedFileCount,
                errorMessage = null
            )

            try {
                val checkResult = setupManager.checkFolderSetup(basePath)
                val malformedFiles = checkResult.malformedFiles

                if (malformedFiles.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isClassifying = false,
                        filesOrganized = true,
                        successMessage = "No files need reorganization"
                    )
                    onComplete?.invoke()
                    return@launch
                }

                _uiState.value = _uiState.value.copy(totalFilesToClassify = malformedFiles.size)

                val classifications = setupManager.classifyFilesWithAI(malformedFiles, provider)
                
                _uiState.value = _uiState.value.copy(classificationProgress = malformedFiles.size)

                val movedCount = setupManager.reorganizeFiles(basePath, classifications)

                _uiState.value = _uiState.value.copy(
                    isClassifying = false,
                    filesOrganized = true,
                    hasMalformedFiles = false,
                    malformedFileCount = 0,
                    successMessage = "Organized $movedCount files successfully"
                )
                
                onComplete?.invoke()
                logger.info("FolderSetup", "File classification and organization complete: $movedCount files")
            } catch (e: Exception) {
                logger.error("FolderSetup", "Failed to classify files: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isClassifying = false,
                    errorMessage = "Classification failed: ${e.message}"
                )
            }
        }
    }

    fun skipOrganizingFiles() {
        _uiState.value = _uiState.value.copy(
            showMalformedDialog = false,
            hasMalformedFiles = false,
            malformedFileCount = 0
        )
    }

    fun dismissDialogs() {
        _uiState.value = _uiState.value.copy(
            showSetupDialog = false,
            showMalformedDialog = false,
            errorMessage = null,
            successMessage = null
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}
