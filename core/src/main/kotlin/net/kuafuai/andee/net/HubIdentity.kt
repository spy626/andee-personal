package net.kuafuai.andee.net

import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Owner-pinned, P-256 signed Hub identity check over an already-secure WSS
 * connection. The private signing key stays with the Hub; never on the phone.
 * Every reconnect requires a fresh random challenge before any tool exposure.
 */
object HubIdentity {
    private val rng = SecureRandom()
    private const val DOMAIN = "andee-hub-auth-v1"

    private fun parsePinnedKey(encoded: String): ECPublicKey? = runCatching {
        val cleaned = encoded.trim()
        if (cleaned.length !in 40..1024) return@runCatching null
        val raw = Base64.getDecoder().decode(cleaned)
        val pub = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(raw)) as? ECPublicKey
            ?: return@runCatching null
        if (pub.params.curve.field.fieldSize != 256) return@runCatching null
        pub
    }.getOrNull()

    fun isValidPinnedKey(encoded: String): Boolean = parsePinnedKey(encoded) != null

    fun newChallenge(): String =
        Base64.getEncoder().encodeToString(ByteArray(32).also { rng.nextBytes(it) })

    /** Canonical UTF-8 bytes that the external Hub must sign with SHA256withECDSA. */
    fun signedPayload(deviceId: String, nonce: String): ByteArray =
        "$DOMAIN\n$deviceId\n$nonce".toByteArray(Charsets.UTF_8)

    fun verify(pinnedKey: String, deviceId: String, nonce: String, signature: String): Boolean =
        runCatching {
            val pub = parsePinnedKey(pinnedKey) ?: return@runCatching false
            if (deviceId.isBlank()) return@runCatching false
            if (Base64.getDecoder().decode(nonce).size != 32) return@runCatching false
            if (signature.length !in 40..256) return@runCatching false
            val der = Base64.getDecoder().decode(signature)
            Signature.getInstance("SHA256withECDSA").apply {
                initVerify(pub)
                update(signedPayload(deviceId, nonce))
            }.verify(der)
        }.getOrDefault(false)
}
