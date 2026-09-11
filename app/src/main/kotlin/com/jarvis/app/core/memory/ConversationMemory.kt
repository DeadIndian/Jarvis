package com.jarvis.app.core.memory

/** One turn in the live conversation window. */
data class ChatTurn(val role: String, val content: String)

/**
 * ConversationMemory is the single source of truth for the current conversation.
 * It records EVERY turn — user speech, assistant replies, and tool executions —
 * and returns a token-bounded window of the most recent turns.
 *
 * Durable facts (preferences, people, decisions) are NOT kept here; those go to
 * long-term memory via basic-memory (MCP). This layer only needs to be complete
 * and bounded, so a plain list + a token budget is enough.
 */
class ConversationMemory(
    // ~1500 tokens of recent context. Big enough for several exchanges, small
    // enough to stay cheap on every request.
    private val tokenBudget: Int = 1500
) {
    private val turns = ArrayDeque<ChatTurn>()

    fun addUser(text: String) = add("user", text)
    fun addAssistant(text: String) = add("assistant", text)

    /** Records a tool execution as a compact assistant turn, e.g. "[make_call → Calling John]". */
    fun addToolResult(tool: String, spokenSummary: String) =
        add("assistant", "[$tool → $spokenSummary]")

    private fun add(role: String, content: String) {
        if (content.isBlank()) return
        turns.addLast(ChatTurn(role, content))
        // ponytail: unbounded list guarded by a hard cap so it can't grow forever
        //           across a very long session; window() does the real trimming.
        while (turns.size > MAX_TURNS) turns.removeFirst()
    }

    /**
     * Most recent turns whose combined estimated tokens fit the budget, in
     * chronological order. The newest turn is always included even if it alone
     * exceeds the budget.
     */
    fun window(): List<ChatTurn> {
        val picked = ArrayDeque<ChatTurn>()
        var used = 0
        for (turn in turns.asReversed()) {
            val cost = estimateTokens(turn.content)
            if (picked.isNotEmpty() && used + cost > tokenBudget) break
            picked.addFirst(turn)
            used += cost
        }
        return picked.toList()
    }

    fun clear() = turns.clear()

    // chars/4 is the standard rough token estimate.
    // ponytail: chars/4 heuristic; swap for a real tokenizer only if budget accuracy ever matters.
    private fun estimateTokens(text: String): Int = (text.length + 3) / 4

    private companion object {
        const val MAX_TURNS = 200
    }
}
