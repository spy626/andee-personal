package net.kuafuai.andee.audio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveToolAdapterTest {

    @Test
    fun declarationsTranslateAndeeToolsToGeminiFunctionDeclarations() {
        val params = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("query", JSONObject().put("type", "string")))
            .put("required", JSONArray().put("query"))
        val tools = JSONArray().put(
            JSONObject()
                .put("name", "market_search")
                .put("method", "market.search")
                .put("description", "Search the market")
                .put("parameters", params)
                .put("localOnly", true)
        )

        val result = GeminiLiveToolAdapter.declarations(listOf(tools))
        val declarations = result.getJSONObject(0).getJSONArray("functionDeclarations")
        val declaration = declarations.getJSONObject(0)

        assertEquals(1, declarations.length())
        assertEquals("market_search", declaration.getString("name"))
        assertEquals("Search the market", declaration.getString("description"))
        assertEquals("BLOCKING", declaration.getString("behavior"))
        assertTrue(declaration.has("parameters"))
        assertFalse(declaration.has("method"))
        assertFalse(declaration.has("localOnly"))
    }

    @Test
    fun declarationsSkipInvalidAndDuplicateNames() {
        val tools = JSONArray()
            .put(JSONObject().put("name", "valid_tool").put("description", "first"))
            .put(JSONObject().put("name", "valid_tool").put("description", "duplicate"))
            .put(JSONObject().put("name", "bad.name").put("description", "invalid"))

        val declarations = GeminiLiveToolAdapter.declarations(listOf(tools))
            .getJSONObject(0).getJSONArray("functionDeclarations")

        assertEquals(1, declarations.length())
        assertEquals("first", declarations.getJSONObject(0).getString("description"))
    }
}
