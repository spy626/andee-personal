package net.kuafuai.andee.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveConsentPolicyTest {
    @Test fun onlyExplicitTapApproves() {
        assertTrue(SensitiveConsentPolicy.isApproved("Approve", "button"))
        for (source in listOf("voice", "voice_other", "timeout", "superseded", "busy", "tap_outside", "error")) {
            assertFalse(source, SensitiveConsentPolicy.isApproved("Approve", source))
        }
        for (button in listOf("Cancel", "approve", "Approve extra", "")) {
            assertFalse(button, SensitiveConsentPolicy.isApproved(button, "button"))
        }
        assertFalse(SensitiveConsentPolicy.isApproved(null, "button"))
    }
}
