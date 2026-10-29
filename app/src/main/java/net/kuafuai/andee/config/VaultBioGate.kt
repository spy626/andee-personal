package net.kuafuai.andee.config

import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.os.Looper
import android.os.SystemClock
import net.kuafuai.andee.ui.VaultUnlockActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One biometric-only unlock for a short Vault session, not an approval for
 * general Andee actions. The Keystore key separately requires BIOMETRIC_STRONG.
 *
 * Never infer approval from a spoken response, remote Hub, screen text or tool
 * arguments. Only VaultUnlockActivity may complete the local system prompt.
 */
object VaultBioGate {
    // Shorter than the 600-second Keystore authentication window.
    private const val OPEN_MS = 9 * 60_000L
    private val lock = Any()
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    @Volatile private var unlockedUntil = 0L
    private var prompting = false

    fun ready(context: Context): Boolean =
        SystemClock.elapsedRealtime() < unlockedUntil &&
            context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false

    fun requireActive(context: Context) {
        check(ready(context)) {
            "Andee Vault is biometric-locked. Unlock the Vault on this phone first."
        }
    }

    /** Nonblocking for overlay UI. Only the Vault needs biometric input. */
    fun request(context: Context, callback: (Boolean) -> Unit) {
        if (ready(context)) {
            callback(true)
            return
        }
        var launch = false
        synchronized(lock) {
            if (ready(context)) {
                callback(true)
                return
            }
            callbacks.add(callback)
            if (!prompting) {
                prompting = true
                launch = true
            }
        }
        if (!launch) return
        try {
            context.startActivity(
                Intent(context, VaultUnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
            )
        } catch (_: Exception) {
            complete(false)
        }
    }

    /** Tool worker path: wait for the *Vault-only* system biometric prompt. */
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
        if (!done.await(70, TimeUnit.SECONDS) || !accepted || !ready(context)) {
            throw IllegalStateException("Vault biometric unlock was cancelled, unavailable or timed out")
        }
    }

    /** Called ONLY following the Android framework's biometric callback. */
    fun complete(accepted: Boolean) {
        val waiting: List<(Boolean) -> Unit>
        synchronized(lock) {
            unlockedUntil = if (accepted) SystemClock.elapsedRealtime() + OPEN_MS else 0L
            prompting = false
            waiting = callbacks.toList()
            callbacks.clear()
        }
        waiting.forEach { runCatching { it(accepted) } }
    }

    fun lockNow() {
        synchronized(lock) { unlockedUntil = 0L }
    }

    fun sessionMillis(): Long = OPEN_MS
}
