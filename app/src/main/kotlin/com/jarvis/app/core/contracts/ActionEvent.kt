package com.jarvis.app.core.contracts

import org.json.JSONObject

sealed class ActionEvent {
    data class PhoneToolAction(
        val tool: String,
        val arguments: Map<String, Any>,
        val confirmationRequired: Boolean = false
    ) : ActionEvent() {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "phone_tool")
                put("tool", tool)
                put("arguments", JSONObject(arguments))
                put("confirmation_required", confirmationRequired)
            }
        }
    }

    data class SpeakAction(
        val text: String,
        val interruptible: Boolean = true
    ) : ActionEvent() {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "speak")
                put("text", text)
                put("interruptible", interruptible)
            }
        }
    }

    data class ServerTaskEvent(
        val taskId: String,
        val state: String,
        val message: String
    ) : ActionEvent() {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "server_task")
                put("task_id", taskId)
                put("state", state)
                put("message", message)
            }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ActionEvent? {
            return when (json.optString("type")) {
                "phone_tool" -> {
                    val tool = json.optString("tool", "")
                    val argsObj = json.optJSONObject("arguments")
                    val argsMap = mutableMapOf<String, Any>()
                    argsObj?.keys()?.forEach { key ->
                        argsMap[key] = argsObj.get(key)
                    }
                    PhoneToolAction(
                        tool = tool,
                        arguments = argsMap,
                        confirmationRequired = json.optBoolean("confirmation_required", false)
                    )
                }
                "speak" -> {
                    SpeakAction(
                        text = json.optString("text", ""),
                        interruptible = json.optBoolean("interruptible", true)
                    )
                }
                "server_task" -> {
                    ServerTaskEvent(
                        taskId = json.optString("task_id", ""),
                        state = json.optString("state", "started"),
                        message = json.optString("message", "")
                    )
                }
                else -> null
            }
        }
    }
}
