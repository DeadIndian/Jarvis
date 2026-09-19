package com.jarvis.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

interface MemorySettingsRepository {
    fun getNotesFolderPath(): String?
    fun setNotesFolderPath(path: String)
    fun clearNotesFolderPath()
}

class EncryptedMemorySettingsRepository(
    context: Context
) : MemorySettingsRepository {
    companion object {
        private const val PREFS_NAME = "jarvis_memory_settings"
        private const val KEY_NOTES_FOLDER_PATH = "notesFolderPath"
        const val DEFAULT_NOTES_FOLDER = "JarvisNotes"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getNotesFolderPath(): String? {
        return sharedPreferences.getString(KEY_NOTES_FOLDER_PATH, null)
            ?.takeUnless { it.isBlank() }
    }

    override fun setNotesFolderPath(path: String) {
        val normalizedPath = path.trim()
        require(normalizedPath.isNotBlank()) { "Notes folder path cannot be blank" }
        sharedPreferences.edit().putString(KEY_NOTES_FOLDER_PATH, normalizedPath).apply()
    }

    override fun clearNotesFolderPath() {
        sharedPreferences.edit().remove(KEY_NOTES_FOLDER_PATH).apply()
    }
}