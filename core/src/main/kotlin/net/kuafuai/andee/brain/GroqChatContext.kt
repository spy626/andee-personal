package net.kuafuai.andee.brain

import org.json.JSONObject

/**
 * Request budget for the Groq Free chat-only route.
 *
 * Groq's documented GPT-OSS Free tier is 8,000 tokens/minute. Keep the
 * submitted dialogue deliberately far below that ceiling and reserve a
 * bounded completion. This is a chat context policy, not a destructive edit
 * to Andee's normal agent history: other providers keep their full history.
 */
object GroqChatContext {
    const val MAX_COMPLETION_TOKENS = 512
    const val MAX_TOTAL_CHARS = 4_500
    const val MAX_SYSTEM_CHARS = 900
    const val MAX_USER_CHARS = 1_600
    const val MAX_ASSISTANT_CHARS = 700
    const val MAX_MESSAGES = 8

    /**
     * Keep one compact system instruction and the newest plain-text user /
     * assistant turns. Tool, function, multimodal and internal messages are
     * excluded rather than partially serialised.
     */
    fun compact(messages: List<JSONObject>): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val system = messages.firstOrNull { it.optString("role") == "system" }
            ?.optString("content")
            .orEmpty()
            .take(MAX_SYSTEM_CHARS)
        if (system.isNotBlank()) {
            result += JSONObject().put("role", "system").put("content", system)
        }

        var remaining = (MAX_TOTAL_CHARS - system.length).coerceAtLeast(0)
        val candidates = messages.asSequence()
            .filter { it.optString("role") == "user" || it.optString("role") == "assistant" }
            .filter { it.opt("content") is String }
            .filter { it.optString("content").isNotBlank() }
            .takeLast(MAX_MESSAGES)
            .toList()

        val kept = mutableListOf<JSONObject>()
        for (message in candidates.asReversed()) {
            if (remaining <= 0) break
            val role = message.optString("role")
            val cap = if (role == "user") MAX_USER_CHARS else MAX_ASSISTANT_CHARS
            val content = message.optString("content")
            val take = minOf(content.length, cap, remaining)
            if (take <= 0) continue
            kept += JSONObject()
                .put("role", role)
                .put("content", content.takeLast(take))
            remaining -= take
        }
        result.addAll(kept.asReversed())
        return result
    }
}
