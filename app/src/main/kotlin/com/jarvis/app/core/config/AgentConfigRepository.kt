package com.jarvis.app.core.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

/** A configured MCP server the agent can call tools on (Streamable-HTTP transport). */
data class McpServer(
    val name: String,
    val url: String,
    val apiKey: String? = null
)

/** A downloadable on-device tool-call model. `active` = the one used for local routing. */
data class ToolModel(
    val name: String,
    val url: String,
    val active: Boolean = false
) {
    /** Filename derived from URL, used on disk. */
    val fileName: String get() = url.substringAfterLast('/').substringBefore('?').ifBlank { "$name.litertlm" }
}

/**
 * AgentConfigRepository stores runtime-editable agent settings in EncryptedSharedPreferences
 * (keys are secrets, so encryption matters). MCP servers and tool models are stored as JSON arrays.
 */
class AgentConfigRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "jarvis_agent_config"
        private const val KEY_BASE_URL = "agentBaseUrl"
        private const val KEY_API_KEY = "agentApiKey"
        private const val KEY_MODEL = "agentModel"
        private const val KEY_MCP_SERVERS = "mcpServers"
        private const val KEY_TOOL_MODELS = "toolModels"

        // Sensible defaults from the project's known infra (freellmapi, bm.thisqr.works).
        const val DEFAULT_BASE_URL = "https://api.thisqr.works/v1"
        const val DEFAULT_MODEL = "auto"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Intelligent (cloud) agent model ---
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)!!.ifBlank { DEFAULT_BASE_URL }
        set(v) = prefs.edit().putString(KEY_BASE_URL, v.trim()).apply()

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeUnless { it.isBlank() }
        set(v) = prefs.edit().putString(KEY_API_KEY, v?.trim().orEmpty()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL)!!.ifBlank { DEFAULT_MODEL }
        set(v) = prefs.edit().putString(KEY_MODEL, v.trim()).apply()

    val hasAgent: Boolean get() = !apiKey.isNullOrBlank()

    // --- MCP servers ---
    fun getMcpServers(): List<McpServer> {
        val arr = JSONArray(prefs.getString(KEY_MCP_SERVERS, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            McpServer(o.getString("name"), o.getString("url"), o.optString("apiKey").ifBlank { null })
        }
    }

    fun setMcpServers(servers: List<McpServer>) {
        val arr = JSONArray()
        servers.forEach { s ->
            arr.put(JSONObject().put("name", s.name).put("url", s.url).put("apiKey", s.apiKey.orEmpty()))
        }
        prefs.edit().putString(KEY_MCP_SERVERS, arr.toString()).apply()
    }

    fun addMcpServer(server: McpServer) = setMcpServers(getMcpServers().filter { it.name != server.name } + server)
    fun removeMcpServer(name: String) = setMcpServers(getMcpServers().filter { it.name != name })

    // --- Tool-call models ---
    fun getToolModels(): List<ToolModel> {
        val arr = JSONArray(prefs.getString(KEY_TOOL_MODELS, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ToolModel(o.getString("name"), o.getString("url"), o.optBoolean("active", false))
        }
    }

    fun setToolModels(models: List<ToolModel>) {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(JSONObject().put("name", m.name).put("url", m.url).put("active", m.active))
        }
        prefs.edit().putString(KEY_TOOL_MODELS, arr.toString()).apply()
    }

    fun addToolModel(model: ToolModel) = setToolModels(getToolModels().filter { it.name != model.name } + model)
    fun removeToolModel(name: String) = setToolModels(getToolModels().filter { it.name != name })

    /** Marks one model active, all others inactive. */
    fun setActiveToolModel(name: String) =
        setToolModels(getToolModels().map { it.copy(active = it.name == name) })

    fun activeToolModel(): ToolModel? = getToolModels().firstOrNull { it.active }
}
