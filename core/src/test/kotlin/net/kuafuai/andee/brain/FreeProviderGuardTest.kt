package net.kuafuai.andee.brain

import org.junit.Assert.*
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
