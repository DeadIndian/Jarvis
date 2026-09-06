package com.jarvis.app.core.tools

import com.jarvis.app.core.contracts.ActionEvent

/**
 * PhoneToolRegistry is a minimal in-memory registry of deterministic phone tools.
 * It holds the canonical set of tools that DeterministicRouter can produce.
 *
 * This exists as a single source of truth for tool names and their handlers.
 */
class PhoneToolRegistry(
    private val executor: PhoneToolExecutor
) {

    // ponytail: the tool set is intentionally small and fixed; expand when local routing grows.
    val deterministicTools: Set<String> = setOf(
        "set_volume",
        "toggle_bluetooth",
        "toggle_wifi",
        "media_control",
        "open_app"
    )

    fun hasTool(tool: String): Boolean = tool in deterministicTools

    fun execute(action: ActionEvent.PhoneToolAction): Boolean {
        return executor.executeTool(action.tool, action.arguments)
    }
}
