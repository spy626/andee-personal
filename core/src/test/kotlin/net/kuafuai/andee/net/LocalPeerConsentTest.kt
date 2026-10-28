package net.kuafuai.andee.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPeerConsentTest {
    private val approve = "Allow"

    @Test fun physicalTapOnUnlockedVisiblePhoneOnly() {
        assertTrue(LocalPeerConsent.approved("button", approve, approve, true, true))
        listOf("voice", "voice_other", "timeout", "superseded", "tap_outside", "error", "")
            .forEach { assertFalse(LocalPeerConsent.approved(it, approve, approve, true, true)) }
    }

    @Test fun wrongButtonOrMissingLabelCannotGrant() {
        assertFalse(LocalPeerConsent.approved("button", "Deny", approve, true, true))
        assertFalse(LocalPeerConsent.approved("button", null, approve, true, true))
        assertFalse(LocalPeerConsent.approved("button", "", approve, true, true))
        assertFalse(LocalPeerConsent.approved("button", "", "", true, true))
    }

    @Test fun lockedOrHiddenPhoneCannotGrant() {
        assertFalse(LocalPeerConsent.approved("button", approve, approve, false, true))
        assertFalse(LocalPeerConsent.approved("button", approve, approve, true, false))
        assertFalse(LocalPeerConsent.approved("button", approve, approve, false, false))
    }
}
