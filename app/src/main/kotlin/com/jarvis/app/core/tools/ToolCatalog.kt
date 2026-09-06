package com.jarvis.app.core.tools

import org.json.JSONArray
import org.json.JSONObject

/**
 * ToolCatalog exposes the phone's local tools as OpenAI-compatible function schemas
 * so the cloud agent can emit structured tool_calls against them.
 *
 * Keep this in sync with InMemoryPhoneToolExecutor (handlers) and ActionValidator (allowlist).
 */
object ToolCatalog {

    /** Phone tool names the agent is allowed to call. */
    val phoneToolNames: Set<String> = setOf(
        "set_volume", "toggle_bluetooth", "toggle_wifi", "media_control",
        "open_app", "make_call", "send_whatsapp", "read_notifications", "toggle_flashlight"
    )

    /** OpenAI `tools` array for the phone tools. */
    fun phoneTools(): JSONArray {
        val arr = JSONArray()
        arr.put(fn("set_volume", "Set media volume to a percentage.",
            obj("level" to intProp("Volume percent, 0-100")), listOf("level")))
        arr.put(fn("toggle_bluetooth", "Enable or disable Bluetooth.",
            obj("enable" to boolProp("true to enable")), listOf("enable")))
        arr.put(fn("toggle_wifi", "Enable or disable Wi-Fi.",
            obj("enable" to boolProp("true to enable")), listOf("enable")))
        arr.put(fn("media_control", "Control media playback.",
            obj("action" to enumProp("play, pause, next, previous, stop", listOf("play","pause","next","previous","stop"))),
            listOf("action")))
        arr.put(fn("open_app", "Open an installed app by name.",
            obj("app_name" to strProp("App display name, e.g. Spotify")), listOf("app_name")))
        arr.put(fn("make_call", "Call a contact or number (requires user confirmation).",
            obj("phone_number" to strProp("Contact name or phone number")), listOf("phone_number")))
        arr.put(fn("send_whatsapp", "Open WhatsApp with a pre-filled message to a contact or number; the user taps send.",
            obj(
                "recipient" to strProp("Contact name or phone number"),
                "message" to strProp("Message text to pre-fill")
            ), listOf("recipient", "message")))
        arr.put(fn("read_notifications", "Read recent notifications.",
            obj("limit" to intProp("How many, 1-50")), emptyList()))
        arr.put(fn("toggle_flashlight", "Turn the flashlight on or off.",
            obj("enable" to boolProp("true to turn on")), listOf("enable")))
        return arr
    }

    /** Wraps a raw JSON Schema (e.g. from MCP inputSchema) as an OpenAI function tool. */
    fun toOpenAiTool(name: String, description: String, parameters: JSONObject): JSONObject =
        JSONObject().put("type", "function").put("function",
            JSONObject().put("name", name).put("description", description).put("parameters", parameters))

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject {
        val params = JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", JSONArray(required))
        return toOpenAiTool(name, desc, params)
    }

    private fun obj(vararg pairs: Pair<String, JSONObject>): JSONObject =
        JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }

    private fun strProp(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun intProp(desc: String) = JSONObject().put("type", "integer").put("description", desc)
    private fun boolProp(desc: String) = JSONObject().put("type", "boolean").put("description", desc)
    private fun enumProp(desc: String, values: List<String>) =
        JSONObject().put("type", "string").put("description", desc).put("enum", JSONArray(values))
}
