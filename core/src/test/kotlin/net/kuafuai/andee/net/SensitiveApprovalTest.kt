package net.kuafuai.andee.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveApprovalTest {
    @Test fun onlyPhysicalApproveButtonCounts() {
        assertTrue(SensitiveApproval.userTappedApprove("button", "Yes", "Yes"))
        assertTrue(SensitiveApproval.userTappedApprove("button", "是", "是"))
        for (method in listOf("voice", "voice_other", "timeout", "superseded", "tap_outside", "no_answer", null)) {
            assertFalse(SensitiveApproval.userTappedApprove(method, "Yes", "Yes"))
        }
        assertFalse(SensitiveApproval.userTappedApprove("button", "No", "Yes"))
        assertFalse(SensitiveApproval.userTappedApprove("button", "yes", "Yes"))
        assertFalse(SensitiveApproval.userTappedApprove("button", null, "Yes"))
        assertFalse(SensitiveApproval.userTappedApprove("button", "", ""))
    }
}
