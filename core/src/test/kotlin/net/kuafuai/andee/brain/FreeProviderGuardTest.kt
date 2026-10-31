package net.kuafuai.andee.brain

import org.junit.Assert.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class FreeProviderGuardTest {
    private fun catalog(tier: String = "free", input: String = "0.0",
                        output: String = "0", tools: Boolean = true): String =
        """{"data":[{"id":"qwen/qwen3-vl-plus:free","access_tier":"$tier","capabilities":{"tools":$tools},"pricing":{"currency":"USD","unit":"per_1m_tokens","input":$input,"output":$output,"cache_read":"0"}}]}"""

    @Test fun exactFreeTierZeroPriceToolsRequired() {
        assertTrue(FreeProviderGuard.catalogIsVerifiedFree(catalog(), FreeProviderGuard.XKIRO_QWEN))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(tier="paid"), FreeProviderGuard.XKIRO_QWEN))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(input="0.1"), FreeProviderGuard.XKIRO_QWEN))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(output="0.01"), FreeProviderGuard.XKIRO_QWEN))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(tools=false), FreeProviderGuard.XKIRO_QWEN))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree("{}", FreeProviderGuard.XKIRO_QWEN))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(), "paid-model"))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(
            """{"data":[{"id":"qwen/qwen3-vl-plus:free","access_tier":"free","capabilities":{"tools":true},"pricing":{"input":0}}]}""",
            FreeProviderGuard.XKIRO_QWEN))
    }

    @Test fun quotaUsesDocumentedUnlimitedOrPositiveFreeAllowanceOnly() {
        val free = """{"object":"usage","free_tokens":{"used_today":1,"limit_per_day":500000,"remaining":499999}}"""
        val low = """{"object":"usage","free_tokens":{"used_today":499999,"limit_per_day":500000,"remaining":1}}"""
        val unlimited = """{"object":"usage","free_tokens":{"used_today":25000,"limit_per_day":null,"remaining":null}}"""
        assertTrue(FreeProviderGuard.quotaIsVerifiedFree(free))
        assertTrue(FreeProviderGuard.quotaIsVerifiedFree(low))
        assertTrue(FreeProviderGuard.quotaIsVerifiedFree(unlimited))
        assertEquals(null, FreeProviderGuard.quotaProblem(unlimited))
        assertEquals("free_quota_exhausted", FreeProviderGuard.quotaProblem(
            """{"object":"usage","free_tokens":{"used_today":500000,"limit_per_day":500000,"remaining":0}}"""
        ))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree(
            """{"object":"usage","free_tokens":{"used_today":5,"limit_per_day":null,"remaining":10}}"""
        ))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree(
            """{"object":"usage","free_tokens":{"used_today":5,"limit_per_day":500,"remaining":null}}"""
        ))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree(
            """{"object":"usage","free_tokens":{"used_today":5,"remaining":100}}"""
        ))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree(
            """{"object":"usage","free_tokens":{"used_today":-1,"limit_per_day":null,"remaining":null}}"""
        ))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree("{}"))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree("not-json"))
    }

    @Test fun groqErrorHintsAreSafeAndActionable() {
        for (code in listOf(400, 401, 403, 404, 413, 429, 503)) {
            val hint = FreeProviderGuard.groqErrorHint(code)
            assertTrue(hint.isNotBlank())
            assertFalse(hint.contains("sk-"))
        }
        assertTrue(FreeProviderGuard.groqErrorHint(413).contains("payload too large"))
        assertTrue(FreeProviderGuard.groqErrorHint(429).contains("limit"))
        assertTrue(FreeProviderGuard.groqErrorHint(401).contains("API key"))
    }

    @Test fun catalogReasonsSeparateMissingModelFromUnsafePricingOrTools() {
        val deepseek = FreeProviderGuard.XKIRO_QWEN
        assertNull(FreeProviderGuard.catalogProblem(catalog(), deepseek))
        assertEquals("unsupported_model", FreeProviderGuard.catalogProblem(catalog(), FreeProviderGuard.XKIRO_AUTO))
        assertEquals("not_free_tier", FreeProviderGuard.catalogProblem(catalog(tier="paid"), deepseek))
        assertEquals("nonzero_price", FreeProviderGuard.catalogProblem(catalog(input="0.01"), deepseek))
        assertEquals("tools_not_verified", FreeProviderGuard.catalogProblem(catalog(tools=false), deepseek))
        assertEquals("invalid_catalog", FreeProviderGuard.catalogProblem("not-json", deepseek))
        assertEquals("pricing_unverified", FreeProviderGuard.catalogProblem(
            """{"data":[{"id":"$deepseek","access_tier":"free","capabilities":{"tools":true},"pricing":{"input":0,"output":0,"new_paid_fee":"unknown"}}]}""",
            deepseek,
        ))
    }

    @Test fun qwenDocumentedFreeIdIsDifferentFromRetiredGuess() {
        assertEquals("qwen/qwen3-vl-plus:free", FreeProviderGuard.XKIRO_QWEN)
        assertFalse(FreeProviderGuard.models(FreeProviderGuard.XKIRO).contains("qwen/qwen3-coder-plus:free"))
        assertNull(FreeProviderGuard.catalogProblem(
            """{"data":[{"id":"qwen/qwen3-vl-plus:free","access_tier":"free","capabilities":{"tools":true},"pricing":{"currency":"USD","unit":"per_1m_tokens","input":0,"output":"0.000000","cache_read":0}}]}""",
            FreeProviderGuard.XKIRO_QWEN,
        ))
    }

    @Test fun mockHttpPreflightUsesOnlyGetAndRejectsUnavailableModelBeforeUsage() {
        val seen = mutableListOf<String>()
        val modelList = """{"data":[{"id":"qwen/qwen3-vl-plus:free","access_tier":"free","capabilities":{"tools":true},"pricing":{"input":0,"output":0}}]}"""
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            seen.add("${chain.request().method} ${chain.request().url.encodedPath}")
            val body = when (chain.request().url.encodedPath) {
                "/v1/models" -> modelList
                "/v1/usage" -> """{"object":"usage","free_tokens":{"used_today":1000,"limit_per_day":500000,"remaining":499000}}"""
                else -> error("Unexpected URL")
            }
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body.toByteArray().toResponseBodyForFixture())
                .build()
        }.build()
        FreeProviderGuard.preflightXKiro(http, "fake-key-not-real", FreeProviderGuard.XKIRO_QWEN)
        assertEquals(listOf("GET /v1/models", "GET /v1/usage"), seen)
        seen.clear()
        try {
            FreeProviderGuard.preflightXKiro(http, "fake-key-not-real", FreeProviderGuard.XKIRO_DEEPSEEK)
            fail("Removed DeepSeek choice should never be sent")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("not approved"))
        }
        assertTrue(seen.isEmpty())
    }

    private fun ByteArray.toResponseBodyForFixture(): okhttp3.ResponseBody = this.toResponseBody(null)

    @Test fun xKiroAutoOnlyKeepsFreeToolCapableCatalogModelsAndNeverDeepSeek() {
        val models = """{"data":[
          {"id":"deepseek/deepseek-v4.1-flash:free","access_tier":"free","capabilities":{"tools":true,"vision":true},"pricing":{"input":0,"output":0}},
          {"id":"qwen/qwen3-vl-plus:free","access_tier":"free","capabilities":{"tools":true,"vision":true},"pricing":{"currency":"USD","input":"0","output":0}},
          {"id":"qwen/qwen3.5-flash:free","access_tier":"free","capabilities":{"tools":true,"vision":true},"pricing":{"input":0,"output":"0"}},
          {"id":"cohere/command-a","access_tier":"free","capabilities":{"tools":true,"vision":false},"pricing":{"input":0,"output":0}},
          {"id":"qwen/paid","access_tier":"paid","capabilities":{"tools":true,"vision":true},"pricing":{"input":0,"output":0}},
          {"id":"qwen/looks-free-but-charged","access_tier":"free","capabilities":{"tools":true,"vision":true},"pricing":{"input":"0.1","output":0}},
          {"id":"qwen/no-tools:free","access_tier":"free","capabilities":{"tools":false,"vision":true},"pricing":{"input":0,"output":0}},
          {"id":"qwen/unknown-cache-price:free","access_tier":"free","capabilities":{"tools":true,"vision":true},"pricing":{"input":0,"output":0,"cache_write":"unknown"}}
        ]}"""
        val picked = FreeProviderGuard.autoEligibleModels(models)
        assertEquals(listOf(
            "qwen/qwen3-vl-plus:free",
            "qwen/qwen3.5-flash:free",
            "cohere/command-a",
        ), picked)
        assertEquals(picked.take(2), FreeProviderGuard.autoEligibleModels(models, true))
        assertTrue(FreeProviderGuard.autoEligibleModels("{}").isEmpty())
        assertTrue(FreeProviderGuard.autoEligibleModels("invalid").isEmpty())
        assertFalse(FreeProviderGuard.models(FreeProviderGuard.XKIRO).contains(FreeProviderGuard.XKIRO_DEEPSEEK))
    }

    @Test fun autoUsesReadOnlyChecksAndOnlyDefiniteRejectionAllowsRetry() {
        val seen = mutableListOf<String>()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            seen += "${chain.request().method} ${chain.request().url.encodedPath}"
            val reply = if (chain.request().url.encodedPath == "/v1/models")
                """{"data":[{"id":"qwen/qwen3-vl-plus:free","access_tier":"free","capabilities":{"vision":true,"tools":true},"pricing":{"input":0,"output":0}},{"id":"cohere/command-a","access_tier":"free","capabilities":{"tools":true},"pricing":{"input":0,"output":0}}]}"""
            else """{"object":"usage","free_tokens":{"used_today":200,"limit_per_day":500000,"remaining":499800}}"""
            okhttp3.Response.Builder()
                .request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(reply.toByteArray().toResponseBodyForFixture()).build()
        }.build()
        assertEquals(
            listOf("qwen/qwen3-vl-plus:free", "cohere/command-a"),
            FreeProviderGuard.preflightXKiroAuto(http, "dummy-not-real"),
        )
        assertEquals(listOf("GET /v1/models", "GET /v1/usage"), seen)
        assertEquals(listOf("qwen/qwen3-vl-plus:free"),
            FreeProviderGuard.autoEligibleModels(
                """{"data":[{"id":"qwen/qwen3-vl-plus:free","access_tier":"free","capabilities":{"vision":true,"tools":true},"pricing":{"input":0,"output":0}}]}""", true))
        assertTrue(FreeProviderGuard.autoRetryable(404))
        assertTrue(FreeProviderGuard.autoRetryable(429))
        for (code in listOf(400, 401, 403, 408, 500, 502, 503, 504)) {
            assertFalse("No auto retry for $code", FreeProviderGuard.autoRetryable(code))
        }
    }

    @Test fun modelsAndEndpointsCannotBeChangedToPaidOrUntrustedHost() {
        FreeProviderGuard.validate(FreeProviderGuard.XKIRO, FreeProviderGuard.XKIRO_BASE, FreeProviderGuard.XKIRO_QWEN)
        FreeProviderGuard.validate(FreeProviderGuard.XKIRO, FreeProviderGuard.XKIRO_BASE, FreeProviderGuard.XKIRO_AUTO)
        FreeProviderGuard.validate(FreeProviderGuard.GROQ, FreeProviderGuard.GROQ_BASE, FreeProviderGuard.GROQ_120B)
        FreeProviderGuard.validate(FreeProviderGuard.OPENROUTER, FreeProviderGuard.OPENROUTER_BASE, FreeProviderGuard.OPENROUTER_AUTO)
        for (bad in listOf(
            Triple(FreeProviderGuard.XKIRO, "https://api.evil.invalid/v1", FreeProviderGuard.XKIRO_QWEN),
            Triple(FreeProviderGuard.XKIRO, FreeProviderGuard.XKIRO_BASE, FreeProviderGuard.XKIRO_DEEPSEEK),
            Triple(FreeProviderGuard.XKIRO, FreeProviderGuard.XKIRO_BASE, "deepseek/deepseek-v4.1-flash"),
            Triple(FreeProviderGuard.GROQ, FreeProviderGuard.GROQ_BASE, "paid-model"),
            Triple(FreeProviderGuard.OPENROUTER, FreeProviderGuard.OPENROUTER_BASE, "some-paid-model"),
            Triple("zai", "https://api.z.ai", "glm-4.7-flash"),
        )) {
            try {
                FreeProviderGuard.validate(bad.first, bad.second, bad.third)
                fail("Accepted untrusted or paid request: $bad")
            } catch (_: IllegalStateException) {
                // required fail-closed
            }
        }
    }
}
