package net.kuafuai.andee.brain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroqChatContextTest {
    @Test fun keepsOnlySystemAndRecentPlainTextDialogue() {
        val input = listOf(
            JSONObject().put("role", "system").put("content", "compact system"),
            JSONObject().put("role", "user").put("content", "old question"),
            JSONObject().put("role", "assistant").put("content", "old answer"),
            JSONObject().put("role", "tool").put("tool_call_id", "call_1").put("content", "secret tool output"),
            JSONObject().put("role", "user").put("content", "latest question"),
            JSONObject().put("role", "assistant").put("content", "latest answer"),
            JSONObject().put("role", "user").put("content", JSONArray().put(JSONObject().put("type", "image_url"))),
        )

        val actual = GroqChatContext.compact(input)
        assertEquals(listOf("system", "user", "assistant"), actual.map { it.optString("role") })
        assertEquals("compact system", actual[0].optString("content"))
        assertEquals("latest question", actual[1].optString("content"))
        assertEquals("latest answer", actual[2].optString("content"))
        assertFalse(actual.any { it.toString().contains("secret tool output") })
    }

    @Test fun enforcesHardCharacterBudgetAndKeepsNewestMessage() {
        val input = mutableListOf(
            JSONObject().put("role", "system").put("content", "S".repeat(5_000)),
        )
        repeat(20) { index ->
            input += JSONObject().put("role", if (index % 2 == 0) "user" else "assistant")
                .put("content", "message-$index " + "x".repeat(2_000))
        }

        val actual = GroqChatContext.compact(input)
        val chars = actual.sumOf { it.optString("content").length }
        assertTrue("request chars=$chars", chars <= GroqChatContext.MAX_TOTAL_CHARS)
        assertTrue(actual.size <= GroqChatContext.MAX_MESSAGES + 1)
        assertTrue(actual.last().optString("content").contains("message-19"))
        assertTrue(actual.first().optString("content").length <= GroqChatContext.MAX_SYSTEM_CHARS)
    }

    @Test fun groqPromptDoesNotAdvertiseDeviceOrMemoryCapabilities() {
        val prompt = LocalPrompt.groqChat("playful voice")
        assertTrue(prompt.contains("text-chat only"))
        assertTrue(prompt.contains("no access to the device"))
        assertTrue(prompt.contains("no personal memory"))
        assertTrue(prompt.contains("playful voice"))
        assertTrue(prompt.contains("Never claim"))
    }
}
