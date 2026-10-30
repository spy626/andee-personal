package net.kuafuai.andee.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.kuafuai.andee.brain.FreeProviderGuard
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Independent non-biometric AndroidKeyStore encryption for ordinary LLM API
 * keys. This deliberately does NOT reuse the biometric Vault lock: agent
 * inference should not prompt for a fingerprint on every background turn.
 *
 * Never save keys in voice_prefs, debug logs, the Hub, or BuildConfig.
 */
object ProviderKeyStore {
    const val GEMINI_LIVE = "gemini_live"
    private const val KEY_ALIAS = "andee-free-llm-keys-v1"
    private const val PREF = "andee_free_provider_keys"
    private const val KEYSTORE = "AndroidKeyStore"
    private val supported = setOf(
        FreeProviderGuard.XKIRO,
        FreeProviderGuard.GROQ,
        FreeProviderGuard.OPENROUTER,
        GEMINI_LIVE,
    )

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @Synchronized
    fun put(context: Context, provider: String, raw: String) {
        require(provider in supported) { "Unsupported provider key slot" }
        val value = raw.trim()
        require(value.isNotEmpty() && value.length <= 4096) { "Invalid API key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val blob = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        check(context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString("key_$provider", encoded).commit()) {
            "Provider API key could not be saved"
        }
    }

    @Synchronized
    fun get(context: Context, provider: String): String {
        require(provider in supported) { "Unsupported provider key slot" }
        val data = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("key_$provider", null) ?: return ""
        // Never silently use an old or corrupt key, never erase it on failure.
        return try {
            val blob = Base64.decode(data, Base64.DEFAULT)
            require(blob.size > 12 + 16) { "Invalid encrypted key blob" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, secretKey(),
                GCMParameterSpec(128, blob.copyOfRange(0, 12)),
            )
            String(cipher.doFinal(blob.copyOfRange(12, blob.size)), Charsets.UTF_8)
        } catch (t: Exception) {
            throw IllegalStateException("Saved provider key cannot be decrypted; not overwritten", t)
        }
    }

    /** Included in the original Factory Reset "keys" target. */
    @Synchronized
    fun wipe(context: Context) {
        // This alias is exclusive to free-provider keys, never the biometric Vault.
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        check(context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().commit()) { "Provider keys were not cleared" }
    }

    @Synchronized
    fun clear(context: Context, provider: String) {
        require(provider in supported)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove("key_$provider").apply()
    }
}
