package net.kuafuai.andee.brain

import org.junit.Assert.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class FreeProviderGuardTest {
    private fun catalog(tier: String = "free", input: String = "0.0",
                        output: String = "0", tools: Boolean = true): String =
        """{"data":[{"id":"deepseek/deepseek-v4.1-flash:free","access_tier":"$tier","capabilities":{"tools":$tools},"pricing":{"currency":"USD","unit":"per_1m_tokens","input":$input,"output":$output,"cache_read":"0"}}]}"""

    @Test fun exactFreeTierZeroPriceToolsRequired() {
        assertTrue(FreeProviderGuard.catalogIsVerifiedFree(catalog(), FreeProviderGuard.XKIRO_DEEPSEEK))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(tier="paid"), FreeProviderGuard.XKIRO_DEEPSEEK))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(input="0.1"), FreeProviderGuard.XKIRO_DEEPSEEK))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(output="0.01"), FreeProviderGuard.XKIRO_DEEPSEEK))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(tools=false), FreeProviderGuard.XKIRO_DEEPSEEK))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree("{}", FreeProviderGuard.XKIRO_DEEPSEEK))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(catalog(), "paid-model"))
        assertFalse(FreeProviderGuard.catalogIsVerifiedFree(
            """{"data":[{"id":"deepseek/deepseek-v4.1-flash:free","access_tier":"free","capabilities":{"tools":true},"pricing":{"input":0}}]}""",
            FreeProviderGuard.XKIRO_DEEPSEEK))
    }

    @Test fun remainingFreeTokensMustBeKnownAndSufficient() {
        assertTrue(FreeProviderGuard.quotaIsVerifiedFree("""{"free_tokens":{"remaining":12000}}"""))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree("""{"free_tokens":{"remaining":11999}}"""))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree("""{"free_tokens":{"remaining":null}}"""))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree("""{"free_tokens":{}}"""))
        assertFalse(FreeProviderGuard.quotaIsVerifiedFree("""{}"""))
    }

    @Test fun catalogReasonsSeparateMissingModelFromUnsafePricingOrTools() {
        val deepseek = FreeProviderGuard.XKIRO_DEEPSEEK
        assertNull(FreeProviderGuard.catalogProblem(catalog(), deepseek))
        assertEquals("model_not_listed", FreeProviderGuard.catalogProblem(catalog(), FreeProviderGuard.XKIRO_QWEN))
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
                "/v1/usage" -> """{"free_tokens":{"remaining":100000}}"""
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
            fail("DeepSeek ID absent in fixture catalog should not be sent")
        } catch (e: java.io.IOException) {
            assertTrue(e.message.orEmpty().contains("model_not_listed"))
        }
        assertEquals(listOf("GET /v1/models"), seen)
    }

    private fun ByteArray.toResponseBodyForFixture(): okhttp3.ResponseBody = this.toResponseBody(null)

    @Test fun modelsAndEndpointsCannotBeChangedToPaidOrUntrustedHost() {
        FreeProviderGuard.validate(FreeProviderGuard.XKIRO, FreeProviderGuard.XKIRO_BASE, FreeProviderGuard.XKIRO_DEEPSEEK)
        FreeProviderGuard.validate(FreeProviderGuard.GROQ, FreeProviderGuard.GROQ_BASE, FreeProviderGuard.GROQ_120B)
        FreeProviderGuard.validate(FreeProviderGuard.OPENROUTER, FreeProviderGuard.OPENROUTER_BASE, FreeProviderGuard.OPENROUTER_AUTO)
        for (bad in listOf(
            Triple(FreeProviderGuard.XKIRO, "https://api.evil.invalid/v1", FreeProviderGuard.XKIRO_DEEPSEEK),
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
