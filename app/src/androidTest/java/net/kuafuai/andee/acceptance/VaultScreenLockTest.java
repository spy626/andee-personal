package net.kuafuai.andee.acceptance;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.kuafuai.andee.config.VaultBioGate;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

/**
 * Disposable emulator regression of the Vault session's screen-off boundary.
 *
 * Uses synthetic unlocked leases and directly invokes the receiver, because
 * Android reserves the real ACTION_SCREEN_OFF broadcast for the system. Does
 * NOT fake a biometric enrollment, save credentials, or unlock a real Vault.
 * The physical fingerprint and screen-lock lifecycle still need spare-phone
 * acceptance.
 */
@RunWith(AndroidJUnit4.class)
public class VaultScreenLockTest {
    private boolean initialObserverState = false;

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static Field field(String name) throws Exception {
        Field f = VaultBioGate.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private void fakeActiveSession() throws Exception {
        field("observerRegistered").setBoolean(null, true);
        field("unlockedUntil").setLong(null, SystemClock.elapsedRealtime() + 60_000L);
    }

    @Before
    public void rememberOriginalState() throws Exception {
        initialObserverState = field("observerRegistered").getBoolean(null);
        VaultBioGate.INSTANCE.lockNow();
    }

    @After
    public void clearFakeSession() throws Exception {
        VaultBioGate.INSTANCE.lockNow();
        // Synthetic tests must not leave a fake receiver-registration flag
        // behind for any other Android instrumentation test in this process.
        field("observerRegistered").setBoolean(null, initialObserverState);
    }

    @Test
    public void screenOffRevokesAnAlreadyUnlockedVaultSession() throws Exception {
        Context app = context();
        KeyguardManager km = app.getSystemService(KeyguardManager.class);
        assumeFalse("Test needs an unlocked disposable emulator", km.isDeviceLocked());
        fakeActiveSession();
        assertTrue("precondition: fake session granted", VaultBioGate.INSTANCE.ready(app));

        // Execute the production receiver's actual onReceive code. A normal
        // app cannot send the OS-protected ACTION_SCREEN_OFF broadcast.
        BroadcastReceiver receiver = (BroadcastReceiver) field("screenOffReceiver").get(null);
        receiver.onReceive(app, new Intent(Intent.ACTION_SCREEN_OFF));

        assertFalse("screen-off must revoke the session", VaultBioGate.INSTANCE.ready(app));
        assertEquals("screen-off clears the old lease", 0L,
                field("unlockedUntil").getLong(null));
        assertFalse("phone unlock must not restore the old Vault lease",
                VaultBioGate.INSTANCE.ready(app));
    }

    @Test
    public void successFromBeforeScreenOffCannotReopenVault() throws Exception {
        Context app = context();
        KeyguardManager km = app.getSystemService(KeyguardManager.class);
        assumeFalse("Test needs an unlocked disposable emulator", km.isDeviceLocked());
        field("observerRegistered").setBoolean(null, true);
        field("prompting").setBoolean(null, true);
        field("activePromptId").setLong(null, 700L);

        VaultBioGate.INSTANCE.lockNow();
        VaultBioGate.INSTANCE.complete(true, 700L, app);

        assertFalse("stale biometric callback must be ignored", VaultBioGate.INSTANCE.ready(app));
        assertEquals(0L, field("unlockedUntil").getLong(null));
    }

    @Test
    public void onlyTheCurrentBiometricPromptMayGrantLease() throws Exception {
        Context app = context();
        KeyguardManager km = app.getSystemService(KeyguardManager.class);
        assumeFalse("Test needs an unlocked disposable emulator", km.isDeviceLocked());
        field("observerRegistered").setBoolean(null, true);
        field("prompting").setBoolean(null, true);
        field("activePromptId").setLong(null, 900L);

        VaultBioGate.INSTANCE.complete(true, 899L, app);
        assertFalse("wrong request ID cannot unlock", VaultBioGate.INSTANCE.ready(app));

        VaultBioGate.INSTANCE.complete(true, 900L, app);
        assertTrue("valid request ID may unlock on an unlocked phone",
                VaultBioGate.INSTANCE.ready(app));
        VaultBioGate.INSTANCE.lockNow();
        assertFalse(VaultBioGate.INSTANCE.ready(app));
    }
}
