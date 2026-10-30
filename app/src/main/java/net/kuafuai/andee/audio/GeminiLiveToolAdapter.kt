package net.kuafuai.andee.audio

import net.kuafuai.andee.brain.BrainHost
import org.json.JSONArray
import org.json.JSONObject

/**
 * Adapts Andee's existing tool catalog to Gemini Live and routes every call
 * back through BrainHost.dispatch (AndroidBrainHost -> TurnGuards/dispatcher).
 */
object GeminiLiveToolAdapter {

    /** Gemini expects tools: [{ functionDeclarations: [...] }]. */
    fun declarations(toolLists: List<JSONArray>): JSONArray {
        val declarations = JSONArray()
        val seen = LinkedHashSet<String>()
        for (list in toolLists) {
            for (i in 0 until list.length()) {
                val source = list.optJSONObject(i) ?: continue
                val name = source.optString("name").trim()
                if (!isValidName(name) || !seen.add(name)) continue
                val declaration = JSONObject()
                    .put("name", name)
                    .put("description", source.optString("description"))
                source.optJSONObject("parameters")?.let { declaration.put("parameters", it) }
                declarations.put(declaration)
            }
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", declarations))
    }

    /**
     * Execute a Gemini call as a normal Andee user voice turn. Unknown names
     * fail closed; no raw CommandDispatcher or Android action is exposed here.
     */
    fun dispatch(
        host: BrainHost,
        callId: String,
        toolName: String,
        args: JSONObject,
    ): JSONObject {
        if (callId.isBlank() || !isValidName(toolName)) {
            return error("Invalid function call")
        }
        val method = host.methodOf(toolName) ?: return error("Unknown Andee tool: $toolName")
        return try {
            val result = host.dispatch(
                method = method,
                params = JSONObject(args.toString()),
                callId = callId,
                silent = false,
                untrusted = false,
            )
            when (result) {
                is JSONObject -> result
                is JSONArray -> JSONObject().put("result", result)
                null -> JSONObject().put("result", JSONObject.NULL)
                else -> JSONObject().put("result", result)
            }
        } catch (t: Throwable) {
            error(t.message?.takeIf { it.isNotBlank() } ?: "Tool execution failed")
        }
    }

    private fun error(message: String) = JSONObject()
        .put("ok", false)
        .put("error", message)

    private fun isValidName(name: String): Boolean =
        name.length in 1..128 && name.all {
            it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-'
        }
}
