package net.kuafuai.andee.audio

import android.content.Context
import net.kuafuai.andee.brain.BrainHost
import net.kuafuai.andee.config.ProviderKeyStore
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns one Gemini Live voice session around Andee's existing AudioIO.
 *
 * Input is mono PCM16/16 kHz; output is mono PCM16/24 kHz. Android actions
 * are never performed here: function calls are dispatched through BrainHost.
 * Call start/stop from a service-owned lifecycle, not from a view.
 */
class GeminiLiveVoiceController(
    context: Context,
    private val audio: AudioIO,
    private val host: BrainHost,
    private val listener: Listener,
    private val model: String = GeminiLiveSession.DEFAULT_MODEL,
    private val voice: String = GeminiLiveSession.DEFAULT_VOICE,
    private val tokenProvider: GeminiLiveTokenProvider = GeminiLiveTokenProvider(),
) {
    interface Listener {
        fun onConnecting()
        fun onReady()
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onTurnComplete()
        fun onFailure(message: String)
        fun onStopped()
    }

    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(true)
    private val playbackStarted = AtomicBoolean(false)
    /** IDs cancelled by the server before their queued Android action begins. */
    private val cancelledToolCalls = ConcurrentHashMap.newKeySet<String>()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GeminiLiveVoice").apply { isDaemon = true }
    }
    private val toolWorker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GeminiLiveTools").apply { isDaemon = true }
    }

    @Volatile private var session: GeminiLiveSession? = null

    fun isActive(): Boolean = !closed.get()

    /** Starts token minting off the main thread, then opens the constrained Live socket. */
    fun start(systemInstruction: String) {
        if (!closed.compareAndSet(true, false)) return
        listener.onConnecting()
        worker.execute {
            try {
                val key = ProviderKeyStore.get(appContext, ProviderKeyStore.GEMINI_LIVE)
                require(key.isNotBlank()) { "Gemini Live API key is not configured in Voice Settings" }
                val token = tokenProvider.create(key, model)
                if (closed.get()) return@execute
                val declarations = GeminiLiveToolAdapter.declarations(host.toolLists())
                val live = GeminiLiveSession(
                    ephemeralToken = token,
                    model = model,
                    voice = voice,
                    tools = declarations,
                    listener = object : GeminiLiveSession.Listener {
                        override fun onReady() {
                            if (closed.get()) return
                            try {
                                audio.startStreaming(sampleRate = INPUT_SAMPLE_RATE, chunkMs = 100) { pcm ->
                                    if (!closed.get()) session?.sendAudio(pcm)
                                }
                                listener.onReady()
                            } catch (t: Throwable) {
                                fail("Microphone could not start: ${t.message ?: t.javaClass.simpleName}")
                            }
                        }

                        override fun onText(text: String) = Unit

                        override fun onInputTranscript(text: String) {
                            if (!closed.get()) listener.onInputTranscript(text)
                        }

                        override fun onOutputTranscript(text: String) {
                            if (!closed.get()) listener.onOutputTranscript(text)
                        }

                        override fun onTurnComplete() {
                            if (!closed.get()) listener.onTurnComplete()
                        }

                        override fun onAudio(pcm24Khz: ByteArray) {
                            if (closed.get() || pcm24Khz.isEmpty()) return
                            try {
                                if (playbackStarted.compareAndSet(false, true)) {
                                    audio.startStreamPlayback(OUTPUT_SAMPLE_RATE)
                                }
                                audio.feedPcm(pcm24Khz)
                            } catch (t: Throwable) {
                                fail("Speaker could not start: ${t.message ?: t.javaClass.simpleName}")
                            }
                        }

                        override fun onFunctionCall(id: String, name: String, args: JSONObject) {
                            if (closed.get()) return
                            try {
                                toolWorker.execute {
                                    // Cancellation can prevent a queued action from
                                    // starting. Once dispatch has begun, Android work
                                    // is not safely reversible.
                                    if (closed.get() || cancelledToolCalls.remove(id)) return@execute
                                    val current = session ?: return@execute
                                    GeminiLiveToolAdapter.dispatchAndRespond(host, current, id, name, args)
                                }
                            } catch (_: java.util.concurrent.RejectedExecutionException) {
                                // Stop may shut the queue between the closed check
                                // above and execute(); a late tool call must be inert.
                                android.util.Log.i(TAG, "Ignored tool call after session shutdown")
                            }
                        }

                        override fun onInterrupted() {
                            playbackStarted.set(false)
                            audio.stopStreamPlayback()
                        }

                        override fun onToolCallCancelled(ids: List<String>) {
                            // The worker checks this set immediately before dispatch,
                            // so a call still waiting in the queue will not touch Android.
                            // A call already inside CommandDispatcher cannot be rolled back.
                            cancelledToolCalls.addAll(ids)
                            android.util.Log.i(TAG, "Gemini cancelled ${ids.size} pending tool call(s)")
                        }

                        override fun onClosed(code: Int, reason: String) {
                            finish()
                        }

                        override fun onFailure(error: String) {
                            fail(error)
                        }
                    },
                )
                session = live
                live.connect(systemInstruction)
            } catch (t: Throwable) {
                fail(t.message ?: "Gemini Live could not start")
            }
        }
    }

    /** Sends contextual text without starting a second ASR/TTS pipeline. */
    fun sendText(text: String): Boolean = session?.sendText(text) ?: false

    fun stop() {
        if (!closed.compareAndSet(false, true)) return
        audio.stopStreaming()
        audio.stopStreamPlayback()
        playbackStarted.set(false)
        session?.close()
        session = null
        worker.shutdownNow()
        toolWorker.shutdown()
        cancelledToolCalls.clear()
        listener.onStopped()
    }

    private fun fail(message: String) {
        if (!closed.compareAndSet(false, true)) return
        audio.stopStreaming()
        audio.stopStreamPlayback()
        playbackStarted.set(false)
        session?.close()
        session = null
        worker.shutdownNow()
        toolWorker.shutdown()
        cancelledToolCalls.clear()
        listener.onFailure(message)
    }

    private fun finish() {
        if (!closed.compareAndSet(false, true)) return
        audio.stopStreaming()
        // A normal close drains no more server chunks; discard any stale tail.
        audio.stopStreamPlayback()
        playbackStarted.set(false)
        session = null
        worker.shutdownNow()
        toolWorker.shutdown()
        cancelledToolCalls.clear()
        listener.onStopped()
    }

    companion object {
        private const val TAG = "GeminiLiveVoice"
        private const val INPUT_SAMPLE_RATE = 16_000
        private const val OUTPUT_SAMPLE_RATE = 24_000
    }
}
