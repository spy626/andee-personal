#!/usr/bin/env python3
"""Phase 5 feature-contract checks on the ORIGINAL runtime implementation.

These checks catch accidental removal or rewiring. They do not emulate third
party WhatsApp, the Android microphone, a SIM, screen gestures or remote Hub.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
def source(path):
    return (ROOT / path).read_text(encoding="utf-8")

sms = source("app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt")
dispatch = source("app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt")
relay = source("app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt")
triage = source("core/src/main/kotlin/net/kuafuai/andee/brain/NotificationTriage.kt")
body = source("app/src/main/java/net/kuafuai/andee/BodyCoreService.kt")
cards = source("app/src/main/java/net/kuafuai/andee/ui/CardUi.kt")
moods = source("core/src/main/kotlin/net/kuafuai/andee/ui/ball/Mood.kt")
manifest = source("app/src/main/AndroidManifest.xml")
guard = source("core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt")

def ordered(text, *needles):
    positions = [text.index(n) for n in needles]
    assert positions == sorted(positions), f"Flow or guard order changed: {needles}"

# Original SMS path: existing workflow is not redesigned or replaced.
assert 'fun sendSms(number: String, body: String): JSONObject' in sms
ordered(sms, 'fun sendSms(number: String, body: String): JSONObject',
        'if (!ensurePermission(Manifest.permission.SEND_SMS)) return denied("send_sms")',
        'SmsManager.getDefault().sendTextMessage(number, null, body, null, null)')
assert '"device.sms.send" -> deviceComms.sendSms(' in dispatch

# Original WhatsApp notification -> ring/debounce -> triage/fallback -> original action.
assert '"com.whatsapp" -> "WhatsApp"' in relay
assert '"com.whatsapp",' in relay
assert "private fun offerForTriage(" in relay
assert "private fun showSignboardFor(" in relay
assert "net.kuafuai.andee.ui.CardUi.ask(" in relay
assert "listOf(lookLabel, ignoreLabel)" in relay
assert "if (result.button == lookLabel)" in relay
assert "notificationLookRequested(n)" in relay
assert "NotificationTriage.Action.IGNORE" in body
assert "NotificationTriage.Action.TELL" in body
assert "NotificationTriage.Action.ACT" in body
assert "notificationLookRequested(n, byUser = false)" in body
assert "net.kuafuai.andee.brain.LocalBrain.TurnKind.NOTIFY" in body
assert "TurnGuards.check(method, silent, untrusted)" in dispatch
assert 'untrusted && method.startsWith("device.vault.")' in guard

# Single original CardUi implementation: buttons, user callback and voice.
assert "fun tryAnswerByVoice(spokenText: String): VoiceOutcome" in cards
assert 'handle.close("button", label)' in cards
assert 'main.post { onDone(r) }' in cards
assert 'fun close(how: String, button: String? = null, heard: String? = null)' in cards
assert "if (finished) return" in cards

# No feature replacement: all 19 documented moods and original renderer path.
mood_names = ("CALM", "HAPPY", "CURIOUS", "TENSE", "ANXIOUS", "CONCERNED",
              "WINK", "WAVE", "PROTECT", "CELEBRATE", "REST",
              "LISTENING", "HOLDING", "THINKING", "SPEAKING",
              "SEARCHING", "SUCCESS", "FAILED", "SLEEPING")
for name in mood_names:
    assert name in moods, f"Missing mascot mood {name}"
assert "<service" in manifest and 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' in manifest
assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
assert 'android:name="android.permission.SEND_SMS"' in manifest
print("PASS: original SMS/WhatsApp triage/cards/voice/mascot source entry points preserved")
print("LIMITATION: source contracts do NOT prove live WhatsApp replies, SMS delivery, mic, or 3D rendering")
