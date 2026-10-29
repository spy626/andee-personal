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
    const val XKIRO_QWEN = "qwen/qwen3-vl-plus:free"
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

    /**
     * A safe, actionable reason for rejecting a model. Never include a raw
     * response, user message, key, pricing numbers, or account details.
     * A missing free model ID is different from a paid model: neither is sent.
     */
    fun catalogProblem(raw: String, model: String): String? {
        if (model !in models(XKIRO)) return "unsupported_model"
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return "invalid_catalog"
        val arr = root.optJSONArray("data") ?: return "invalid_catalog"
        val item = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .firstOrNull { it.optString("id") == model }
            ?: return "model_not_listed"
        if (item.optString("access_tier") != "free") return "not_free_tier"
        // Original Andee autonomy requires tools. Text-only routes are unsafe.
        if (item.optJSONObject("capabilities")?.optBoolean("tools") != true)
            return "tools_not_verified"
        val p = item.optJSONObject("pricing") ?: return "pricing_missing"
        if (!p.has("input") || !p.has("output")) return "pricing_missing"
        // Fail closed for every unrecognised or nonzero tariff component.
        // All price fields must be explicit numeric zero (USD metadata ignored).
        val keys = p.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in setOf("currency", "unit")) continue
            val amount = p.opt(key)?.toString()?.toBigDecimalOrNull()
                ?: return "pricing_unverified"
            if (amount.compareTo(BigDecimal.ZERO) != 0) return "nonzero_price"
        }
        return null
    }

    /** Pure Boolean wrapper kept for Phase 10B.1 callers/tests. */
    fun catalogIsVerifiedFree(raw: String, model: String): Boolean =
        catalogProblem(raw, model) == null

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
        val problem = catalogProblem(get("$XKIRO_BASE/models", false), model)
        if (problem != null) {
            throw IOException("xKiro preflight blocked ($problem): select a verified free model in Settings; no inference sent")
        }
        if (!quotaIsVerifiedFree(get("$XKIRO_BASE/usage", true)))
            throw IOException("xKiro remaining free-token quota unverified; no inference sent")
    }
}
