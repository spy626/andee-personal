package net.kuafuai.andee.ui

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import net.kuafuai.andee.config.Vault
import net.kuafuai.andee.config.VaultBioGate

/**
 * Only for opening the credential Vault. Not exported, no network, no actions
 * for WhatsApp/SMS/screen/autonomous work. The OS displays the biometric UI.
 */
class VaultUnlockActivity : Activity() {
    private var finished = false
    private var cancellation: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState != null) {
            respond(false)
            return
        }
        val biometrics = getSystemService(BiometricManager::class.java)
        if (biometrics?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
            BiometricManager.BIOMETRIC_SUCCESS) {
            respond(false) // No insecure PIN/voice/AI fallback.
            return
        }
        try {
            // Generate the new key before prompting. The legacy Vault blob
            // remains untouched until encrypted migration is verified.
            Vault.prepareBiometricKey()
            cancellation = CancellationSignal()
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("Unlock Andee Vault")
                .setSubtitle("Confirm your fingerprint to access saved credentials")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("Cancel", mainExecutor) { _, _ -> respond(false) }
                .build()
            prompt.authenticate(cancellation!!, mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        respond(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        respond(false)
                    }
                })
        } catch (_: Exception) {
            respond(false)
        }
    }

    private fun respond(success: Boolean) {
        if (finished) return
        finished = true
        // Return focus to the user's original app before the Vault caller
        // resumes (especially automatic credential fill into another app).
        finish()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            VaultBioGate.complete(success)
        }, 450L)
    }

    override fun onDestroy() {
        if (!finished) {
            finished = true
            VaultBioGate.complete(false)
        }
        cancellation?.cancel()
        super.onDestroy()
    }
}
