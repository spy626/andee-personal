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

    // Legacy ID retained only to recognize old saved selections; never routed.
    const val XKIRO_DEEPSEEK = "deepseek/deepseek-v4.1-flash:free"
    const val XKIRO_AUTO = "xkiro/auto-free" // app-only marker; NEVER sent as API model
    const val XKIRO_QWEN = "qwen/qwen3-vl-plus:free"
    const val GROQ_120B = "openai/gpt-oss-120b"
    const val GROQ_20B = "openai/gpt-oss-20b"
    const val OPENROUTER_AUTO = "openrouter/free"

    fun models(provider: String): List<String> = when (provider) {
        XKIRO -> listOf(XKIRO_AUTO, XKIRO_QWEN)
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
        if (model !in models(XKIRO) || model == XKIRO_AUTO) return "unsupported_model"
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return "invalid_catalog"
        val arr = root.optJSONArray("data") ?: return "invalid_catalog"
        val item = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .firstOrNull { it.optString("id") == model }
            ?: return "model_not_listed"
        return catalogItemProblem(item)
    }

    /** A single item must have free access, real tools and all tariffs zero. */
    private fun catalogItemProblem(item: JSONObject): String? {
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

    /**
     * Live, per-request free model pool for client-side Auto.
     * No custom endpoint, no paid or DeepSeek, no multimodal incompatibilities.
     * Only models that explicitly pass the same strict pricing and tool test
     * can ever reach the unchanged original Andee agent loop.
     */
    fun autoEligibleModels(raw: String, needsVision: Boolean = false): List<String> {
        val rows = runCatching { JSONObject(raw).optJSONArray("data") }.getOrNull()
            ?: return emptyList()
        val accepted = mutableSetOf<String>()
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (!id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{3,127}"))) continue
            if (id == XKIRO_AUTO || id.startsWith("deepseek/", ignoreCase = true)) continue
            if (item.optString("modality", "chat") != "chat") continue
            if (catalogItemProblem(item) != null) continue
            if (needsVision && item.optJSONObject("capabilities")?.optBoolean("vision") != true) continue
            accepted.add(id)
        }
        // Phone-verified Qwen gets first chance. All other safe free models
        // remain eligible for strictly bounded failover within xKiro.
        return accepted.sortedWith(
            compareBy<String> {
                when {
                    it == XKIRO_QWEN -> 0
                    it.startsWith("qwen/") -> 1
                    else -> 2
                }
            }.thenBy { it },
        )
    }

    /** Only known definitive model rejection can switch automatically. */
    fun autoRetryable(status: Int): Boolean = status == 404 || status == 429

    /**
     * GET-only preflight for Auto. Return the entire currently eligible pool,
     * not a fake gateway model name. No user messages/tools are sent here.
     * An unverified catalog or account quota blocks all inference.
     */
    fun preflightXKiroAuto(
        http: OkHttpClient, apiKey: String, needsVision: Boolean = false,
    ): List<String> {
        validate(XKIRO, XKIRO_BASE, XKIRO_AUTO)
        if (apiKey.isBlank()) throw IOException("xKiro Auto key missing; no inference sent")
        fun get(url: String, authorized: Boolean): String {
            val request = Request.Builder().url(url).get().apply {
                if (authorized) header("Authorization", "Bearer $apiKey")
            }.build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.code in 300..399)
                    throw IOException("xKiro Auto free preflight HTTP ${response.code}; no inference sent")
                return response.body?.string()?.take(1_000_000)
                    ?: throw IOException("xKiro Auto free preflight empty; no inference sent")
            }
        }
        val eligible = autoEligibleModels(get("$XKIRO_BASE/models", false), needsVision)
        if (eligible.isEmpty())
            throw IOException("xKiro Auto no verified free tool-capable model; no inference sent")
        val quotaIssue = quotaProblem(get("$XKIRO_BASE/usage", true))
        if (quotaIssue != null)
            throw IOException("xKiro Auto free quota blocked ($quotaIssue); no inference sent")
        return eligible
    }

    /** Pure Boolean wrapper kept for Phase 10B.1 callers/tests. */
    fun catalogIsVerifiedFree(raw: String, model: String): Boolean =
        catalogProblem(raw, model) == null

    /**
     * xKiro /usage: an explicitly null limit_per_day AND null remaining means
     * the free-token cap does not apply to this account. Other unknown or
     * inconsistent counters are NOT unlimited and must fail closed.
     * Paid model eligibility remains independently blocked by zero-price
     * catalog checks before every request.
     */
    fun quotaProblem(raw: String): String? {
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return "invalid_usage"
        if (root.optString("object") != "usage") return "invalid_usage"
        val obj = root.optJSONObject("free_tokens") ?: return "free_quota_missing"
        if (!obj.has("limit_per_day") || !obj.has("remaining") ||
            !obj.has("used_today") || obj.isNull("used_today")) {
            return "free_quota_missing"
        }
        val used = obj.optLong("used_today", -1L)
        if (used < 0) return "free_quota_invalid"
        if (obj.isNull("limit_per_day")) {
            return if (obj.isNull("remaining")) null else "free_quota_inconsistent"
        }
        if (obj.isNull("remaining")) return "free_quota_inconsistent"
        val limit = obj.optLong("limit_per_day", -1L)
        val remaining = obj.optLong("remaining", -1L)
        if (limit < 0 || remaining < 0 || remaining > limit) return "free_quota_invalid"
        if (remaining == 0L) return "free_quota_exhausted"
        return null
    }

    /** Pure, offline-testable parser: no account details are logged. */
    fun quotaIsVerifiedFree(raw: String): Boolean = quotaProblem(raw) == null

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
        val quotaIssue = quotaProblem(get("$XKIRO_BASE/usage", true))
        if (quotaIssue != null)
            throw IOException("xKiro free quota blocked ($quotaIssue); no inference sent")
    }
}
