package net.kuafuai.andee.net

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Outbound side of the body's networking: dials into a remote hub (agentworld
 * or similar), sends a `register` envelope with our tool schemas, then serves
 * `request` messages coming down the pipe by handing them to
 * [CommandDispatcher] and mailing the result back.
 *
 * Reconnects with exponential backoff (500ms → 30s, capped). Runs alongside
 * [BodyWsServer] — that side keeps serving viewer/local traffic; this side is
 * how we get to a hub that lives behind NAT / on the public internet.
 *
 * Hub protocol:
 *   → {"type":"register","data":{"device_id":"...","device_name":"...","tools":[...]}}
 *   ← {"type":"request","id":"r7","method":"tap_screen","params":{...}}
 *   → {"type":"response","id":"r7","result":{...}}
 *   → {"type":"response","id":"r7","error":"..."}
 *   → {"type":"event","kind":"asr.final","device_id":"...","data":{...}}
 *   ← {"type":"progress","content":"读取界面","agent_id":"..."}
 *   ← {"type":"message","content":"已经帮你打开微信了","agent_id":"..."}
 *
 * `progress` is narration for the human — one subtitle line, never spoken.
 * `message` is the agent's finished answer, which the body speaks aloud.
 * Both are fire-and-forget; neither expects a response.
 *
 * Method names on the wire are the LLM-facing tool names (tap_screen,
 * get_screen_element, …). We translate them to the body's internal `screen.*`
 * method strings via [ToolSchemas.methodOf] before dispatching.
 */
class BodyWsClient(
    private val url: String,
    private val hubPublicKey: String,
    private val deviceId: String,
    private val deviceName: String,
    private val dispatcher: CommandDispatcher,
    /**
     * Extension-provided tools are merged into the `register` payload alongside
     * [ToolSchemas.all]. When the registry changes, the service tears this
     * client down and starts a fresh one so the hub sees an up-to-date list —
     * the hub has no separate "tools changed" frame, `register` is the only
     * lever we have on its idea of what this device can do.
     */
    private val registry: CallExtensionRegistry,
    private val onProgress: (String) -> Unit = {},
    private val onFinal: (String) -> Unit = {},
) {

    private val running = AtomicBoolean(false)
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "BodyWsClient-scheduler").apply { isDaemon = true }
        }

    // Requests can block (dispatchGesture waits ~seconds), so hand them off
    // to a worker so we don't stall the recv thread and miss pings/pongs.
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BodyWsClient-worker").apply { isDaemon = true }
    }

    @Volatile
    private var conn: Inner? = null

    @Volatile
    private var backoffMs: Long = INITIAL_BACKOFF_MS

    fun start() {
        // Do not rely solely on settings preflight: fail closed for any
        // direct/future caller before creating a remote control connection.
        if (!NetworkPolicy.isSecureHubUrl(url)) {
            Log.w(TAG, "hub URL rejected: wss:// is required")
            return
        }
        if (!HubIdentity.isValidPinnedKey(hubPublicKey)) {
            Log.w(TAG, "hub refused: missing/invalid pinned signing key")
            return
        }
        if (!running.compareAndSet(false, true)) return
        // The Hub URL can contain a high-entropy access capability in its path.
        // Never write that URL or path to Android logs.
        Log.i(TAG, "starting authenticated remote Hub client (device=$deviceId)")
        scheduleConnect(0)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        val c = conn
        conn = null
        runCatching { c?.close() }
        scheduler.shutdownNow()
        worker.shutdownNow()
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private fun scheduleConnect(delayMs: Long) {
        if (!running.get()) return
        scheduler.schedule({ if (running.get()) connectOnce() }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun connectOnce() {
        try {
            val c = Inner(URI(url))
            conn = c
            c.connect()
        } catch (t: Throwable) {
            Log.e(TAG, "connect failed: ${t.message}", t)
            onDisconnected()
        }
    }

    private fun onConnected(source: Inner) {
        // Register and expose tools only after THIS socket proved its pinned identity.
        if (conn !== source || !source.authenticated) return
        backoffMs = INITIAL_BACKOFF_MS  // reset backoff on success
        // forHub(), NOT all(): the five `note.*` notebook tools are localOnly.
        // The notebook holds what this person likes, what they promised, and
        // what they are like — it is the one part of this app that is nobody
        // else's business, so it is never advertised to the hub. Swapping this
        // back to all() would silently ship it on the next reconnect, with no
        // visible symptom other than the cloud brain gaining five tools.
        val builtin = ToolSchemas.forHub()
        val extra = registry.tools()
        // Merge order: built-ins first, extension tools appended. The hub
        // stores them by name, so order only matters for logs — but keeping
        // built-ins ahead means a duplicate name from the extension can't hide
        // one of our own by accident (we don't reject collisions, we just get
        // to notice them in the tail).
        val tools = org.json.JSONArray()
        for (i in 0 until builtin.length()) tools.put(builtin.getJSONObject(i))
        for (i in 0 until extra.length()) tools.put(extra.getJSONObject(i))
        val register = JSONObject()
            .put("type", "register")
            .put(
                "data", JSONObject()
                    .put("device_id", deviceId)
                    .put("device_name", deviceName)
                    .put("tools", tools)
            )
        runCatching { source.send(register.toString()) }
        Log.i(
            TAG,
            "registered as $deviceId with ${tools.length()} tools " +
                "(${builtin.length()} built-in + ${extra.length()} from extension)"
        )
    }

    private fun onDisconnected() {
        if (!running.get()) return
        val next = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        Log.i(TAG, "reconnect in ${next}ms")
        scheduleConnect(next)
    }

    private fun handleRequest(req: JSONObject, source: Inner) {
        if (conn !== source || !source.authenticated) return
        val id = req.optString("id", "")
        val toolName = req.optString("method", "")
        val params = req.optJSONObject("params")
        // Two-name translation for built-ins (`tap_screen` → `screen.tap`).
        // Extension tools don't play this game — the schema they registered
        // carries one name, and that same name goes straight to the dispatcher,
        // which will hand it back to the extension over the reverse channel.
        val method = ToolSchemas.methodOf(toolName)
            ?: if (registry.hasMethod(toolName)) toolName else null
        if (method == null) {
            respondError(id, "unknown tool: $toolName", source)
            return
        }
        // A request on *this* socket is the brain driving, which is what the
        // edge glow announces. Marked here rather than in the dispatcher
        // because only the transport can tell the brain apart from the top-bar
        // buttons and the debug client on 9008, which share the same dispatch
        // path and are nobody's task. Usually redundant — `progress` normally
        // arrives first — but a model that opens with a tool call still counts.
        dispatcher.beginTask()
        worker.execute {
            // A request queued on a defunct socket cannot cross into the new
            // trusted Hub session.
            if (conn !== source || !source.authenticated || !source.isOpen) return@execute
            val response = try {
                val result = dispatcher.dispatch(method, params)
                JSONObject()
                    .put("type", "response")
                    .put("id", id)
                    .put("result", result ?: JSONObject.NULL)
            } catch (t: Throwable) {
                Log.w(TAG, "tool '$toolName' failed: ${t.message}")
                JSONObject()
                    .put("type", "response")
                    .put("id", id)
                    .put("error", t.message ?: t.javaClass.simpleName)
            }
            if (conn === source && source.authenticated && source.isOpen) {
                runCatching { source.send(response.toString()) }
            }
        }
    }

    /**
     * Push an unsolicited event up to the hub (ASR transcripts, etc). Dropped
     * silently when the socket is down — events are not queued, the brain only
     * ever sees what happened while it was listening.
     */
    fun sendEvent(kind: String, data: JSONObject) {
        val c = conn ?: return
        if (!c.isOpen || !c.authenticated) return
        val envelope = JSONObject()
            .put("type", "event")
            .put("kind", kind)
            .put("device_id", deviceId)
            .put("data", data)
        runCatching { c.send(envelope.toString()) }
    }

    private fun respondError(id: String, msg: String, source: Inner) {
        val response = JSONObject()
            .put("type", "response")
            .put("id", id)
            .put("error", msg)
        if (conn === source && source.authenticated && source.isOpen) {
            runCatching { source.send(response.toString()) }
        }
    }

    /**
     * WebSocketClient callbacks. Small inner class so we can null out [conn]
     * cleanly across reconnects.
     */
    private inner class Inner(uri: URI) : WebSocketClient(uri) {
        // Fresh per-connection challenge; a captured proof cannot be replayed.
        private val challenge = HubIdentity.newChallenge()
        @Volatile var authenticated: Boolean = false
            private set

        override fun onOpen(handshake: ServerHandshake?) {
            if (!running.get() || conn !== this) {
                close(1008, "stale Hub socket")
                return
            }
            val payload = JSONObject().put("type", "auth.challenge")
                .put("data", JSONObject()
                    .put("version", 1)
                    .put("device_id", deviceId)
                    .put("nonce", challenge))
            try {
                send(payload.toString())
            } catch (t: Throwable) {
                close(1008, "could not challenge Hub identity")
                return
            }
            scheduler.schedule({
                if (running.get() && conn === this && !authenticated) {
                    close(1008, "Hub identity proof timed out")
                }
            }, AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        override fun onMessage(message: String) {
            if (!running.get() || conn !== this) return
            if (message.length > MAX_HUB_FRAME_CHARS) {
                close(1009, "Hub frame too large")
                return
            }
            val msg = runCatching { JSONObject(message) }.getOrNull()
            if (msg == null) {
                if (!authenticated) close(1008, "invalid Hub proof frame")
                return
            }

            if (!authenticated) {
                val proof = msg.optJSONObject("data")
                val valid = msg.optString("type") == "auth.proof" &&
                    proof?.optInt("version") == 1 &&
                    HubIdentity.verify(
                        hubPublicKey,
                        deviceId,
                        challenge,
                        proof?.optString("signature").orEmpty(),
                    )
                if (!valid) {
                    Log.w(TAG, "Hub identity signature verification failed")
                    close(1008, "untrusted Hub identity")
                    return
                }
                authenticated = true
                current = this@BodyWsClient
                onConnected(this)
                return
            }

            when (msg.optString("type")) {
                "request" -> handleRequest(msg, this)
                "progress" -> msg.optString("content").takeIf { it.isNotBlank() }?.let(onProgress)
                "message" -> msg.optString("content").takeIf { it.isNotBlank() }?.let(onFinal)
                else -> Log.d(TAG, "ignoring type=${msg.optString("type")}")
            }
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            Log.i(TAG, "onClose code=$code reason=$reason remote=$remote")
            if (conn === this) {
                conn = null
                if (current === this@BodyWsClient) current = null
                onDisconnected()
            }
        }

        override fun onError(ex: Exception?) {
            Log.w(TAG, "onError ${ex?.message}")
        }
    }

    companion object {
        private const val TAG = "BodyWsClient"
        private const val INITIAL_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val AUTH_TIMEOUT_SECONDS = 10L
        private const val MAX_HUB_FRAME_CHARS = 262_144

        /**
         * The connected client, for services outside BodyCoreService's
         * wiring (NotificationRelayService pushing `notification` events).
         * Null while the hub link is down — pushers drop silently by design:
         * buffering-and-replaying after reconnect would deliver stale
         * "new message" interrupts.
         */
        @Volatile
        var current: BodyWsClient? = null
            private set
    }
}
