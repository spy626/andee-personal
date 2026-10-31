package net.kuafuai.andee.config

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import net.kuafuai.andee.ui.VaultUnlockActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A short, biometric-only credential Vault session. Never gates ordinary
 * Andee screen automation, messages, voice, mascot or autonomous work.
 *
 * ACTION_SCREEN_OFF revokes a prior session for the remainder of its lifetime:
 * unlocking the phone again does NOT restore a previously granted Vault session.
 * A pending biometric result from before that lock is ignored via request ID.
 */
object VaultBioGate {
    const val EXTRA_PROMPT_ID = "net.kuafuai.andee.vault.PROMPT_ID"
    private const val OPEN_MS = 9 * 60_000L

    private val lock = Any()
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    @Volatile private var unlockedUntil = 0L
    @Volatile private var observerRegistered = false
    private var prompting = false
    private var promptSequence = 0L
    private var activePromptId = 0L

    /** Registered only inside this app process, once Vault access is attempted. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) lockNow()
        }
    }

    private fun observeScreenOff(context: Context): Boolean = synchronized(lock) {
        if (observerRegistered) return@synchronized true
        // Cannot protect a Vault session without observing phone lock. Fail
        // closed if the OS refuses registration; do not silently weaken auth.
        val app = context.applicationContext
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(screenOffReceiver, filter)
            }
            observerRegistered = true
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deviceUnlocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false

    fun ready(context: Context): Boolean =
        observerRegistered &&
            SystemClock.elapsedRealtime() < unlockedUntil &&
            deviceUnlocked(context)

    fun requireActive(context: Context) {
        check(ready(context)) {
            "Andee Vault is biometric-locked. Unlock the Vault on this phone first."
        }
    }

    /** Only credential Vault access invokes this; never a general owner approval. */
    fun request(context: Context, callback: (Boolean) -> Unit) {
        if (!observeScreenOff(context) || !deviceUnlocked(context)) {
            callback(false)
            return
        }
        var launchId = 0L
        var alreadyUnlocked = false
        synchronized(lock) {
            if (ready(context)) {
                alreadyUnlocked = true
            } else {
                callbacks.add(callback)
                if (!prompting) {
                    prompting = true
                    activePromptId = ++promptSequence
                    launchId = activePromptId
                }
            }
        }
        if (alreadyUnlocked) {
            callback(true)
            return
        }
        if (launchId == 0L) return // Join one existing biometric prompt.
        try {
            context.startActivity(
                Intent(context, VaultUnlockActivity::class.java)
                    .putExtra(EXTRA_PROMPT_ID, launchId)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    ),
            )
        } catch (_: Exception) {
            complete(false, launchId, context)
        }
    }

    /** Original Vault tool path: fingerprint prompt, then normal automatic fill. */
    fun requireUnlocked(context: Context) {
        if (ready(context)) return
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Vault cannot await a biometric prompt on the UI thread"
        }
        val done = CountDownLatch(1)
        var accepted = false
        request(context) { success ->
            accepted = success
            done.countDown()
        }
        // Less than the 60s tool deadline. A stale request must not type later.
        if (!done.await(20, TimeUnit.SECONDS) || !accepted || !ready(context)) {
            throw IllegalStateException("Vault biometric unlock was cancelled, unavailable or timed out")
        }
    }

    /**
     * Only a current Android biometric Activity may finish its own request.
     * A previous success delivered after screen-off or a replaced prompt can
     * NEVER reactivate a revoked session.
     */
    fun complete(accepted: Boolean, promptId: Long, context: Context) {
        val waiting: List<(Boolean) -> Unit>
        val granted: Boolean
        synchronized(lock) {
            if (!prompting || promptId == 0L || promptId != activePromptId) return
            granted = accepted && observerRegistered && deviceUnlocked(context)
            unlockedUntil = if (granted) SystemClock.elapsedRealtime() + OPEN_MS else 0L
            prompting = false
            activePromptId = 0L
            waiting = callbacks.toList()
            callbacks.clear()
        }
        waiting.forEach { runCatching { it(granted) } }
    }

    /** Screen-off, reset, or explicit lock: revoke the session and stale prompts. */
    fun lockNow() {
        val waiting: List<(Boolean) -> Unit>
        synchronized(lock) {
            unlockedUntil = 0L
            prompting = false
            activePromptId = 0L
            waiting = callbacks.toList()
            callbacks.clear()
        }
        waiting.forEach { runCatching { it(false) } }
    }

    fun sessionMillis(): Long = OPEN_MS
}
