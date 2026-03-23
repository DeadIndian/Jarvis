package com.jarvis.impl

import android.content.Context
import com.jarvis.commands.CommandActivation
import com.jarvis.commands.CommandDefinition
import com.jarvis.commands.CommandExecution
import com.jarvis.commands.CommandResponse
import org.json.JSONArray
import org.json.JSONObject

data class CustomCommandEntry(
    val id: String,
    val keywords: List<String>,
    val actionKey: String,
    val responseText: String
) {
    fun toCommandDefinition(): CommandDefinition {
        val action = CustomActionType.fromKey(actionKey)
        return CommandDefinition(
            id = id,
            description = "Custom command: ${keywords.firstOrNull().orEmpty()}",
            activatesOn = CommandActivation(intents = keywords),
            executes = CommandExecution(handler = action.handler),
            response = CommandResponse(
                success = responseText.ifBlank { action.defaultSuccess },
                failure = "Could not run custom command"
            )
        )
    }
}

enum class CustomActionType(
    val key: String,
    val label: String,
    val handler: String,
    val defaultSuccess: String
) {
    REPLY(
        key = "reply",
        label = "Reply with custom message",
        handler = "custom/reply",
        defaultSuccess = "Done."
    ),
    OPEN_SETTINGS(
        key = "open_settings",
        label = "Open device settings",
        handler = "device/open_settings",
        defaultSuccess = "Opening settings"
    ),
    OPEN_ALARMS(
        key = "open_alarms",
        label = "Open alarms/reminders",
        handler = "assistant/reminder",
        defaultSuccess = "Opening reminder flow"
    );

    companion object {
        fun fromKey(key: String): CustomActionType {
            return entries.firstOrNull { it.key == key } ?: REPLY
        }
    }
}

class CustomCommandStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<CustomCommandEntry> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (idx in 0 until array.length()) {
                    val item = array.optJSONObject(idx) ?: continue
                    val id = item.optString("id").trim()
                    val actionKey = item.optString("actionKey").trim()
                    val responseText = item.optString("responseText")
                    if (id.isBlank() || actionKey.isBlank()) {
                        continue
                    }
                    val keywordsArray = item.optJSONArray("keywords") ?: JSONArray()
                    val keywords = buildList {
                        for (i in 0 until keywordsArray.length()) {
                            val value = keywordsArray.optString(i).trim()
                            if (value.isNotBlank()) add(value)
                        }
                    }
                    if (keywords.isEmpty()) {
                        continue
                    }
                    add(
                        CustomCommandEntry(
                            id = id,
                            keywords = keywords,
                            actionKey = actionKey,
                            responseText = responseText
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(entry: CustomCommandEntry) {
        val updated = loadAll().toMutableList().apply { add(entry) }
        saveAll(updated)
    }

    fun nextId(): String = "custom.${System.currentTimeMillis()}"

    private fun saveAll(entries: List<CustomCommandEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
                .put("id", entry.id)
                .put("actionKey", entry.actionKey)
                .put("responseText", entry.responseText)
                .put("keywords", JSONArray(entry.keywords))
            array.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "jarvis_custom_commands"
        private const val KEY_ITEMS = "items"
    }
}
