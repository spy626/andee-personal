package net.kuafuai.andee.brain

import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class LlmClientXkiroTest {

    private val requestPath = AtomicReference<String?>()
    private val authHeader = AtomicReference<String?>()
    private val requestJson = AtomicReference<JSONObject?>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/chat/completions") { exchange ->
            requestPath.set(exchange.requestURI.path)
            authHeader.set(exchange.requestHeaders.getFirst("Authorization"))
            requestJson.set(JSONObject(exchange.requestBody.bufferedReader().use { it.readText() }))
            val response = """{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"call_1","type":"function","function":{"name":"market_search","arguments":"{\"query\":\"weather\"}"}}]}}],"usage":{"prompt_tokens":7,"completion_tokens":2}}"""
                .toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        start()
    }

    @After
    fun stop() = server.stop(0)

    private fun client(model: String = "vendor/model-free", thinking: Boolean = true) =
        LlmClient(
            baseUrl = "http://127.0.0.1:${server.address.port}/v1",
            apiKey = "test-xkiro-key",
            model = model,
            thinking = thinking,
            reasoningEffort = "high",
            xKiroMode = true,
        )

    @Test
    fun `sends xKiro reasoning and function tools without a thinking object`() {
        val tools = JSONArray().put(
            JSONObject().put("type", "function").put(
                "function", JSONObject().put("name", "market_search"),
            ),
        )
        val reply = client().complete(
            listOf(JSONObject().put("role", "user").put("content", "Find weather tools")),
            tools,
        )

        val body = requestJson.get()!!
        assertEquals("/v1/chat/completions", requestPath.get())
        assertEquals("Bearer test-xkiro-key", authHeader.get())
        assertEquals("vendor/model-free", body.getString("model"))
        assertEquals("high", body.getString("reasoning_effort"))
        assertFalse(body.has("thinking"))
        assertEquals("auto", body.getString("tool_choice"))
        assertEquals(1, body.getJSONArray("tools").length())
        assertEquals("market_search", reply.toolCalls.single().name)
        assertEquals("""{"query":"weather"}""", reply.toolCalls.single().argumentsRaw)
        assertEquals(7, reply.promptTokens)
        assertEquals(2, reply.completionTokens)
    }

    @Test
    fun `uses none to disable xKiro reasoning`() {
        client(thinking = false).complete(
            listOf(JSONObject().put("role", "user").put("content", "Hi")),
            JSONArray(),
        )
        assertEquals("none", requestJson.get()!!.getString("reasoning_effort"))
        assertFalse(requestJson.get()!!.has("thinking"))
    }

    @Test
    fun `rejects a model ID without vendor prefix before networking`() {
        val error = runCatching {
            client(model = "model-free").complete(
                listOf(JSONObject().put("role", "user").put("content", "Hi")),
                JSONArray(),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("vendor/model"))
        assertEquals(null, requestPath.get())
    }
}
