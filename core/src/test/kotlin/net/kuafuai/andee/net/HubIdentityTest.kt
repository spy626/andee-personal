package net.kuafuai.andee.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class HubIdentityTest {
    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(key: java.security.PrivateKey, id: String, nonce: String): String {
        val signed = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key)
            update(HubIdentity.signedPayload(id, nonce))
        }.sign()
        return Base64.getEncoder().encodeToString(signed)
    }

    @Test fun authenticPinnedHubCanProveIdentity() {
        val key = keyPair()
        val pub = Base64.getEncoder().encodeToString(key.public.encoded)
        val nonce = HubIdentity.newChallenge()
        assertTrue(HubIdentity.isValidPinnedKey(pub))
        assertTrue(HubIdentity.verify(pub, "body-test", nonce, sign(key.private, "body-test", nonce)))
    }

    @Test fun wrongKeyWrongDeviceAndReplayAreDenied() {
        val genuine = keyPair()
        val attacker = keyPair()
        val pinned = Base64.getEncoder().encodeToString(genuine.public.encoded)
        val nonce1 = HubIdentity.newChallenge()
        val nonce2 = HubIdentity.newChallenge()
        assertNotEquals(nonce1, nonce2)
        val valid = sign(genuine.private, "body-1", nonce1)
        assertFalse(HubIdentity.verify(pinned, "body-1", nonce2, valid))
        assertFalse(HubIdentity.verify(pinned, "body-2", nonce1, valid))
        assertFalse(HubIdentity.verify(pinned, "body-1", nonce1, sign(attacker.private, "body-1", nonce1)))
    }

    @Test fun malformedAndMissingKeysNeverGrantTrust() {
        val good = keyPair()
        val nonce = HubIdentity.newChallenge()
        val sig = sign(good.private, "body-1", nonce)
        assertFalse(HubIdentity.isValidPinnedKey(""))
        assertFalse(HubIdentity.isValidPinnedKey("not base64"))
        assertFalse(HubIdentity.verify("", "body-1", nonce, sig))
        assertFalse(HubIdentity.verify("bad", "body-1", nonce, sig))
        assertFalse(HubIdentity.verify(Base64.getEncoder().encodeToString(good.public.encoded),
            "body-1", "wrong nonce", sig))
        assertFalse(HubIdentity.verify(Base64.getEncoder().encodeToString(good.public.encoded),
            "body-1", nonce, "wrong signature"))
    }
}
