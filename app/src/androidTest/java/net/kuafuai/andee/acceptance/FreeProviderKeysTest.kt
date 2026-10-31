package net.kuafuai.andee.acceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.kuafuai.andee.config.ProviderKeyStore
import net.kuafuai.andee.config.VoiceConfig
import net.kuafuai.andee.brain.FreeProviderGuard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeProviderKeysTest {
    @Test fun keysEncryptedAndNeverInVoicePrefs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = FreeProviderGuard.XKIRO
        val secret = "andee-phase10b-fixture-only-not-real"
        try {
            ProviderKeyStore.put(context, id, secret)
            assertEquals(secret, ProviderKeyStore.get(context, id))
            val raw = context.getSharedPreferences("andee_free_provider_keys", 0)
                .getString("key_$id", "")!!
            assertNotEquals(secret, raw)
            assertFalse(raw.contains(secret))
            val plain = context.getSharedPreferences("voice_prefs", 0)
            assertFalse(plain.all.values.any { it?.toString()?.contains(secret) == true })
        } finally {
            ProviderKeyStore.clear(context, id)
        }
        assertEquals("", ProviderKeyStore.get(context, id))
    }

    @Test fun providerKeyResetErasesAllFreeKeysButNotVault() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = "phase10b-reset-test-only"
        try {
            ProviderKeyStore.put(app, FreeProviderGuard.XKIRO, fixture)
            ProviderKeyStore.put(app, FreeProviderGuard.GROQ, fixture)
            ProviderKeyStore.wipe(app)
            assertEquals("", ProviderKeyStore.get(app, FreeProviderGuard.XKIRO))
            assertEquals("", ProviderKeyStore.get(app, FreeProviderGuard.GROQ))
            assertFalse(app.getSharedPreferences("andee_free_provider_keys", 0).all.isNotEmpty())
        } finally {
            ProviderKeyStore.clear(app, FreeProviderGuard.XKIRO)
            ProviderKeyStore.clear(app, FreeProviderGuard.GROQ)
        }
    }

    /** Old manually selected DeepSeek must resolve to safe Auto, never paid. */
    @Test fun selectedFreeProviderUsesOriginalLocalBrainAndExactEndpoint() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val p = app.getSharedPreferences("voice_prefs", 0)
        val old = mapOf(
            "brain" to p.getString("brain", null),
            "free_provider" to p.getString("free_provider", null),
            "free_model_xkiro" to p.getString("free_model_xkiro", null),
        )
        try {
            VoiceConfig.save(app, mapOf(
                "brain" to VoiceConfig.BRAIN_LOCAL,
                "free_provider" to FreeProviderGuard.XKIRO,
                "free_model_xkiro" to FreeProviderGuard.XKIRO_DEEPSEEK,
            ))
            val cfg = VoiceConfig.brainConfig(app)
            assertEquals(VoiceConfig.BRAIN_LOCAL, cfg.mode)
            assertEquals(FreeProviderGuard.XKIRO, cfg.freeProvider)
            assertEquals(FreeProviderGuard.XKIRO_BASE, cfg.baseUrl)
            assertEquals(FreeProviderGuard.XKIRO_AUTO, cfg.model)
            assertEquals(FreeProviderGuard.XKIRO_AUTO, VoiceConfig.current(app)["free_model_xkiro"])
        } finally {
            p.edit().apply {
                for ((k,v) in old) if (v == null) remove(k) else putString(k,v)
            }.commit()
        }
    }
}
