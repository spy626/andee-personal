package net.kuafuai.andee.brain

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal

/**
 * Strict zero-cost route guard for opt-in providers. The original Andee
 * LocalBrain remains the single agent/tool executor; this is only a request
 * gate and never performs its own model inference.
 *
 * Never use a :free suffix alone as proof of xKiro billing status.
 */
object FreeProviderGuard {
    const val LEGACY = "legacy"
    const val XKIRO = "xkiro"
    const val GROQ = "groq"
    const val OPENROUTER = "openrouter"

    const val XKIRO_BASE = "https://api.xkiro.com/v1"
    const val GROQ_BASE = "https://api.groq.com/openai/v1"
    const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"

    const val XKIRO_DEEPSEEK = "deepseek/deepseek-v4.1-flash:free"
    const val XKIRO_QWEN = "qwen/qwen3-coder-plus:free"
    const val GROQ_120B = "openai/gpt-oss-120b"
    const val GROQ_20B = "openai/gpt-oss-20b"
    const val OPENROUTER_AUTO = "openrouter/free"

    fun models(provider: String): List<String> = when (provider) {
        XKIRO -> listOf(XKIRO_DEEPSEEK, XKIRO_QWEN)
        GROQ -> listOf(GROQ_120B, GROQ_20B)
        OPENROUTER -> listOf(OPENROUTER_AUTO)
        else -> emptyList()
    }

    fun endpoint(provider: String): String = when (provider) {
        XKIRO -> XKIRO_BASE
        GROQ -> GROQ_BASE
        OPENROUTER -> OPENROUTER_BASE
        else -> throw IllegalArgumentException("Not a supported free provider")
    }

    /** Reject any redirected/mismatched host and any unlisted potentially paid model. */
    fun validate(provider: String, baseUrl: String, model: String) {
        if (provider !in listOf(XKIRO, GROQ, OPENROUTER) ||
            baseUrl != endpoint(provider) || model !in models(provider)) {
            throw IllegalStateException("Free provider/model not approved; request was not sent")
        }
    }

    /** Pure parser, covered by tests with paid, free and malformed catalog fixtures. */
    fun catalogIsVerifiedFree(raw: String, model: String): Boolean = runCatching {
        if (model !in models(XKIRO)) return@runCatching false
        val arr = JSONObject(raw).optJSONArray("data") ?: return@runCatching false
        val item = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .firstOrNull { it.optString("id") == model } ?: return@runCatching false
        if (item.optString("access_tier") != "free") return@runCatching false
        // Andee uses OpenAI-compatible tool calling throughout autonomous WORK.
        if (item.optJSONObject("capabilities")?.optBoolean("tools") != true)
            return@runCatching false
        val p = item.optJSONObject("pricing") ?: return@runCatching false
        if (!p.has("input") || !p.has("output")) return@runCatching false
        // All numeric tariff components must be explicitly zero. If xKiro adds
        // a new charge field, this logic fails closed rather than guessing.
        val keys = p.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in setOf("currency", "unit")) continue
            val amount = p.opt(key)?.toString()?.toBigDecimalOrNull() ?: return@runCatching false
            if (amount.compareTo(BigDecimal.ZERO) != 0) return@runCatching false
        }
        true
    }.getOrDefault(false)

    fun quotaIsVerifiedFree(raw: String): Boolean = runCatching {
        val obj = JSONObject(raw).optJSONObject("free_tokens") ?: return@runCatching false
        // Never interpret an unknown/null unlimited quota as permission.
        if (!obj.has("remaining") || obj.isNull("remaining")) return@runCatching false
        obj.optLong("remaining", -1L) >= 12_000L
    }.getOrDefault(false)

    /** Two GET-only checks; no POST and no arbitrary redirect on failed checks. */
    fun preflightXKiro(http: OkHttpClient, apiKey: String, model: String) {
        validate(XKIRO, XKIRO_BASE, model)
        if (apiKey.isBlank()) throw IOException("xKiro key missing; nothing was sent")
        fun get(url: String, authorized: Boolean): String {
            val b = Request.Builder().url(url).get()
            if (authorized) b.header("Authorization", "Bearer $apiKey")
            http.newCall(b.build()).execute().use { response ->
                if (!response.isSuccessful || response.code in 300..399)
                    throw IOException("xKiro free preflight HTTP ${response.code}; no inference sent")
                return response.body?.string()?.take(1_000_000)
                    ?: throw IOException("xKiro preflight body missing; no inference sent")
            }
        }
        if (!catalogIsVerifiedFree(get("$XKIRO_BASE/models", false), model))
            throw IOException("xKiro model price/tier/tool support unverified; no inference sent")
        if (!quotaIsVerifiedFree(get("$XKIRO_BASE/usage", true)))
            throw IOException("xKiro remaining free-token quota unverified; no inference sent")
    }
}
