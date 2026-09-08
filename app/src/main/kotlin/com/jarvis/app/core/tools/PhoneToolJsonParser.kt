package com.jarvis.app.core.tools

import com.jarvis.app.core.contracts.ActionEvent
import org.json.JSONObject

/**
 * Parses an on-device model's raw text output into a phone tool call.
 * Tolerates prose around the JSON object and rejects tools not in the known phone set.
 */
object PhoneToolJsonParser {
    fun parse(text: String): ActionEvent.PhoneToolAction? {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val obj = JSONObject(text.substring(start, end + 1))
            val tool = obj.optString("tool").ifBlank { return null }
            if (tool !in ToolCatalog.phoneToolNames) return null
            val argsObj = obj.optJSONObject("arguments") ?: JSONObject()
            val args = mutableMapOf<String, Any>()
            argsObj.keys().forEach { args[it] = argsObj.get(it) }
            ActionEvent.PhoneToolAction(tool, args)
        }.getOrNull()
    }
}
