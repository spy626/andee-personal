package net.kuafuai.andee.audio

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-level Gemini Live WebSocket transport.
 *
 * This class deliberately does not execute Android actions or own microphone/
 * speaker lifecycle. The owner supplies PCM chunks and receives function calls;
 * action execution must go through AndroidBrainHost.dispatch().
 */
class GeminiLiveSession(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val listener: Listener,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build(),
) {
    interface Listener {
        fun onReady()
        fun onText(text: String)
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onAudio(pcm24Khz: ByteArray)
        fun onFunctionCall(id: String, name: String, args: JSONObject)
        fun onInterrupted()
        fun onClosed(code: Int, reason: String)
        fun onFailure(error: String)
    }

    private val closed = AtomicBoolean(false)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false

    fun connect(systemInstruction: String) {
        require(apiKey.isNotBlank()) { "Gemini API key is missing" }
        require(model.isNotBlank()) { "Gemini Live model is missing" }
        val url = "$ENDPOINT?key=$apiKey"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setup = JSONObject()
                    .put("setup", JSONObject()
                        .put("model", "models/$model")
                        .put("generationConfig", JSONObject()
                            .put("responseModalities", JSONArray().put("AUDIO"))
                            .put("speechConfig", JSONObject()
                                .put("voiceConfig", JSONObject()
                                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Kore")))))
                        .put("systemInstruction", JSONObject()
                            .put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
                        .put("inputAudioTranscription", JSONObject())
                        .put("outputAudioTranscription", JSONObject()))
                webSocket.send(setup.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false
                listener.onFailure(response?.let { "Gemini Live HTTP ${it.code}: ${it.message}" }
                    ?: (t.message ?: "Gemini Live connection failed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
                closed.set(true)
                listener.onClosed(code, reason)
            }
        })
    }

    /** Send raw mono PCM16 little-endian audio sampled at 16 kHz. */
    fun sendAudio(pcm16Khz: ByteArray): Boolean {
        if (!ready || closed.get() || pcm16Khz.isEmpty()) return false
        return socket?.send(JSONObject().put("realtimeInput", JSONObject()
            .put("audio", JSONObject()
                .put("mimeType", "audio/pcm;rate=16000")
                .put("data", Base64.encodeToString(pcm16Khz, Base64.NO_WRAP))))
            ?: false
    }

    fun sendText(text: String): Boolean {
        if (!ready || closed.get() || text.isBlank()) return false
        return socket?.send(JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString())
            ?: false
    }

    /** Return a completed Android tool result to the matching Gemini call id. */
    fun sendToolResponse(id: String, name: String, result: JSONObject): Boolean {
        if (!ready || closed.get() || id.isBlank() || name.isBlank()) return false
        val response = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("response", result)
        return socket?.send(JSONObject().put("toolResponse", JSONObject()
            .put("functionResponses", JSONArray().put(response))).toString()) ?: false
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        ready = false
        socket?.close(1000, "user stopped")
        socket = null
    }

    private fun handleServerMessage(raw: String) {
        try {
            val root = JSONObject(raw)
            if (root.has("setupComplete")) {
                ready = true
                listener.onReady()
                return
            }
            root.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
                for (i in 0 until calls.length()) {
                    val call = calls.optJSONObject(i) ?: continue
                    listener.onFunctionCall(
                        call.optString("id"),
                        call.optString("name"),
                        call.optJSONObject("args") ?: JSONObject(),
                    )
                }
            }
            root.optJSONObject("serverContent")?.let { content ->
                if (content.optBoolean("interrupted")) listener.onInterrupted()
                content.optJSONObject("inputTranscription")?.optString("text")
                    ?.takeIf { it.isNotBlank() }?.let(listener::onInputTranscript)
                content.optJSONObject("outputTranscription")?.optString("text")
                    ?.takeIf { it.isNotBlank() }?.let(listener::onOutputTranscript)
                val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts") ?: return@let
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    part.optString("text").takeIf { it.isNotBlank() }?.let(listener::onText)
                    val inline = part.optJSONObject("inlineData") ?: continue
                    if (inline.optString("mimeType").startsWith("audio/pcm")) {
                        val encoded = inline.optString("data")
                        if (encoded.isNotBlank()) {
                            listener.onAudio(Base64.decode(encoded, Base64.DEFAULT))
                        }
                    }
                }
            }
            root.optJSONObject("toolCallCancellation")?.let {
                listener.onFailure("Gemini cancelled one or more tool calls")
            }
            root.optJSONObject("error")?.let {
                listener.onFailure(it.optString("message", "Gemini Live protocol error"))
            }
        } catch (t: Throwable) {
            listener.onFailure("Invalid Gemini Live message: ${t.message ?: "parse error"}")
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}
