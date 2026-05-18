package com.jarvis.app

data class FolderSetupUiState(
    val isChecking: Boolean = false,
    val needsSetup: Boolean = false,
    val hasMalformedFiles: Boolean = false,
    val malformedFileCount: Int = 0,
    val isSettingUp: Boolean = false,
    val isClassifying: Boolean = false,
    val classificationProgress: Int = 0,
    val totalFilesToClassify: Int = 0,
    val filesOrganized: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showSetupDialog: Boolean = false,
    val showMalformedDialog: Boolean = false
)
