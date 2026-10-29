package net.kuafuai.andee.acceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kuafuai.andee.ui.FullscreenBackPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No permissions, overlays, keystrokes, cloud keys, or destructive actions. */
@RunWith(AndroidJUnit4::class)
class AndroidBackNavigationTest {
    private fun claims(
        compact: Boolean = false,
        typing: Boolean = false,
        typingTop: Int = 0,
        gestures: Boolean = false,
        capture: Boolean = false,
        away: Boolean = false,
    ): Boolean = FullscreenBackPolicy.claimsBack(
        compact, typing, typingTop, gestures, capture, away,
    )

    @Test fun fullscreenChatClaimsAndroidBackExactlyLikeX() {
        assertTrue("Visible normal fullscreen chat owns Back", claims())
    }

    @Test fun foldedMascotAndKeyboardAndAutomationNeverInterceptBack() {
        assertFalse("Folded mascot lets underlying Android app own Back", claims(compact = true))
        assertFalse("Before launching IME Activity, release Back focus", claims(typing = true))
        assertFalse("Active keyboard band owns Back first", claims(typingTop = 700))
        assertFalse("Screen gestures pass Back to underlying app", claims(gestures = true))
        assertFalse("Screenshot must not steal Back", claims(capture = true))
        assertFalse("Photo picker/camera must retain Back", claims(away = true))
        assertFalse("Even a folded + typing state must not intercept", claims(compact = true, typing = true))
    }
}
