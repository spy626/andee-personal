package net.kuafuai.andee.audio

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Mints a one-use, short-lived Gemini Live token from the user's locally
 * stored Developer API key. The long-lived key is sent only over HTTPS to
 * Google's token provisioning endpoint and is never put in the WebSocket URL.
 *
 * Call from a worker thread; this method performs blocking network I/O.
 */
class GeminiLiveTokenProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    @Throws(IOException::class)
    fun create(apiKey: String, model: String = GeminiLiveSession.DEFAULT_MODEL): String {
        require(apiKey.isNotBlank()) { "Gemini Live API key is missing" }
        require(model.matches(Regex("[A-Za-z0-9._-]{1,128}"))) {
            "Invalid Gemini Live model"
        }

        val now = System.currentTimeMillis()
        val expireTime = Instant.ofEpochMilli(now + TOKEN_LIFETIME_MS).toString()
        val newSessionExpireTime = Instant.ofEpochMilli(now + NEW_SESSION_WINDOW_MS).toString()
        val body = JSONObject()
            .put("uses", 1)
            .put("expireTime", expireTime)
            .put("newSessionExpireTime", newSessionExpireTime)
            .put(
                "liveConnectConstraints",
                JSONObject()
                    .put("model", "models/$model")
                    .put(
                        "config",
                        JSONObject()
                            .put("responseModalities", org.json.JSONArray().put("AUDIO"))
                            .put("sessionResumption", JSONObject()),
                    ),
            )
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(payload).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                val message = if (detail.isNotBlank())
                    "Gemini token request failed (${response.code}): $detail"
                else "Gemini token request failed (${response.code})"
                throw IOException(message)
            }
            val token = runCatching { JSONObject(payload).optString("name") }.getOrDefault("")
            if (token.isBlank()) throw IOException("Gemini returned an empty Live token")
            return token
        }
    }

    companion object {
        private const val TOKEN_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/auth_tokens"
        private const val TOKEN_LIFETIME_MS = 30 * 60 * 1000L
        private const val NEW_SESSION_WINDOW_MS = 60 * 1000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}