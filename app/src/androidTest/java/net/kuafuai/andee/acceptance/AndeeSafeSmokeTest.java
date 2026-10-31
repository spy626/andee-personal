package net.kuafuai.andee.acceptance;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.kuafuai.andee.BodyCoreService;
import net.kuafuai.andee.ScreenBodyService;
import net.kuafuai.andee.device.DeviceCommsController;
import net.kuafuai.andee.device.NotificationRelayService;
import net.kuafuai.andee.ui.CardUi;
import net.kuafuai.andee.ui.SelfCheckActivity;
import net.kuafuai.andee.ui.ball.Mood;
import net.kuafuai.andee.ui.ball.MoodKt;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Device-level acceptance smoke on a disposable Android emulator.
 *
 * No sensitive permissions are granted by the test. No SMS/WhatsApp is sent,
 * no Accessibility service is enabled, no network/API key is configured.
 * This does not replace acceptance on a real spare Android phone.
 */
@RunWith(AndroidJUnit4.class)
public class AndeeSafeSmokeTest {
    private Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void originalServicesKeepSystemPermissionBoundaries() throws Exception {
        PackageManager pm = app().getPackageManager();
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE",
                pm.getServiceInfo(new ComponentName(app(), ScreenBodyService.class), 0).permission);
        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                pm.getServiceInfo(new ComponentName(app(), NotificationRelayService.class), 0).permission);
        assertFalse("The notification listener is never public",
                pm.getServiceInfo(new ComponentName(app(), NotificationRelayService.class), 0).exported);
        assertFalse("Body service must stay private",
                pm.getServiceInfo(new ComponentName(app(), BodyCoreService.class), 0).exported);
        assertTrue("Launcher self-check must remain accessible",
                pm.getActivityInfo(new ComponentName(app(), SelfCheckActivity.class), 0).exported);
    }

    @Test
    public void originalSmsPermissionDeniedPathDoesNotSend() throws Exception {
        Context context = app();
        // Refuse to run this test against a device which has SEND_SMS granted.
        // This prevents an inadvertent message from any acceptance run.
        assertEquals("Never run on a privileged SMS-enabled device",
                PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.SEND_SMS));
        JSONObject result = new DeviceCommsController(context)
                .sendSms("0000000000", "UNSENT ACCEPTANCE FIXTURE");
        assertEquals("permission denied: send_sms", result.getString("error"));
        assertFalse(result.has("sent"));
    }

    @Test
    public void originalCardAnswerTypeAndMascotMoodsRemainAvailable() {
        CardUi.Result card = new CardUi.Result("Send this", "button", null);
        assertEquals("Send this", card.getButton());
        assertEquals("button", card.getHow());
        assertNull(card.getHeard());

        assertEquals("Original 19 state vocabulary", 19, Mood.values().length);
        Set<Mood> distinct = new HashSet<>();
        for (Mood mood : Mood.values()) {
            assertTrue("Each mood must have a face: " + mood, MoodKt.getFACE().containsKey(mood));
            distinct.add(mood);
        }
        assertEquals(19, distinct.size());
        assertNotNull(Mood.valueOf("LISTENING"));
        assertNotNull(Mood.valueOf("THINKING"));
        assertNotNull(Mood.valueOf("SPEAKING"));
        assertNotNull(Mood.valueOf("SEARCHING"));
    }

    @Test
    public void originalFirstRunSelfCheckActivityStartsWithoutPermissions() {
        Context context = app();
        // This test is only valid on a clean/isolated emulator, with the
        // assistant off and the dangerous permissions intentionally disabled.
        assertEquals(PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        Intent intent = new Intent(context, SelfCheckActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        assertNotNull("First-run screen should launch", activity);
        assertEquals(SelfCheckActivity.class.getName(), activity.getClass().getName());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
    }
}
