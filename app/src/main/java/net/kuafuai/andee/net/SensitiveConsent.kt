package net.kuafuai.andee.net

import android.app.KeyguardManager
import android.content.Context
import android.os.Looper
import net.kuafuai.andee.ui.CardUi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device tap consent for especially sensitive direct actions.
 *
 * This is not model authorization: only a fresh button event from the Android
 * card can approve one execution. Timeout, supersede, voice, no overlay, or
 * a locked device all fail closed. Screen tools are blocked while asking.
 */
object SensitiveConsent {
    private val asking = AtomicBoolean(false)
    private val manualReview = AtomicBoolean(false)
    val pending: Boolean get() = asking.get()
    val manualReviewPending: Boolean get() = manualReview.get()

    /**
     * After opening a system SMS/calendar editor, block ALL agent screen
     * gestures until the owner sends a new direct voice/typed conversation
     * turn. A model must not tap the Send/Save button it just exposed.
     */
    fun beginManualReview() { manualReview.set(true) }

    /** Only called from the actual on-device user input path, never a tool. */
    fun finishManualReviewByOwner() { manualReview.set(false) }

    fun requireApproval(context: Context, description: String) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Consent must be requested from a worker thread." }
        fun unlocked(): Boolean {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return km != null && !km.isKeyguardLocked
        }
        check(unlocked()) { "Unlock your phone to approve this sensitive action." }
        check(asking.compareAndSet(false, true)) { "A sensitive approval is already pending." }
        try {
            val completed = CountDownLatch(1)
            val approved = AtomicBoolean(false)
            CardUi.ask(
                question = "Andee requests a sensitive action:\n$description\nTap Approve only if you asked for this.",
                buttons = listOf("Approve", "Cancel"),
                timeoutMs = 18_000L,
                captureSpeech = false,
            ) { answer ->
                approved.set(SensitiveConsentPolicy.isApproved(answer.button, answer.how))
                completed.countDown()
            }
            check(completed.await(21, TimeUnit.SECONDS) && approved.get() && unlocked()) {
                "Sensitive action denied or not confirmed by a real on-device tap. Nothing was performed."
            }
        } finally {
            asking.set(false)
        }
    }
}
