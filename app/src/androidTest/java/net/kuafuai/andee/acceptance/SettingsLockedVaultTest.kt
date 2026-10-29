package net.kuafuai.andee.acceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.kuafuai.andee.config.Vault
import net.kuafuai.andee.config.VaultBioGate
import net.kuafuai.andee.ui.SettingsUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * A first-run Settings opening must work even with the strong-biometric Vault
 * locked. Before the Phase 9.2 hotfix SettingsUi.build() called Vault.entries()
 * for the count, and an IllegalStateException killed the Android process.
 *
 * Isolated emulator only; no keys, existing secrets, messages, or privileges.
 * The overlay addView permission may be denied, but building the Settings
 * content still exercises the regression before addView is attempted.
 */
@RunWith(AndroidJUnit4::class)
class SettingsLockedVaultTest {
    @Test
    fun settingsBuildsWithoutReadingLockedVault() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        VaultBioGate.lockNow()
        assertFalse("A fresh Vault session must be locked", VaultBioGate.ready(app))

        val error = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync {
            val settings = SettingsUi(
                context = app,
                onDismiss = {},
                onFactoryReset = {},
                onClearChat = {},
                onClearNotes = {},
            )
            try {
                settings.show()
                // Phase 10A moved Vault into its own Settings category.
                // Build the actual Vault & Tools page while still locked.
                val page = SettingsUi::class.java.getDeclaredField("page")
                    .apply { isAccessible = true }
                page.set(settings, page.type.enumConstants.first {
                    (it as Enum<*>).name == "DEVICE"
                })
                SettingsUi::class.java.getDeclaredMethod("build")
                    .apply { isAccessible = true }.invoke(settings)
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                settings.hide()
            }
        }
        assertNull("Opening Settings must not require biometric Vault access", error.get())
    }

    @Test
    fun lockedVaultStillRefusesToReadCredentials() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        VaultBioGate.lockNow()
        var denied = false
        try {
            Vault.entries(app)
        } catch (_: IllegalStateException) {
            denied = true
        }
        org.junit.Assert.assertTrue("Settings fix must not bypass Vault authentication", denied)
    }
}
