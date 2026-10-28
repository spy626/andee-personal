#!/usr/bin/env python3
"""Ensure the Android tool paths continue to require explicit local consent."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
dispatch = (root / "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt").read_text()
cards = (root / "app/src/main/java/net/kuafuai/andee/ui/CardUi.kt").read_text()
policy = (root / "core/src/main/kotlin/net/kuafuai/andee/net/SensitiveApproval.kt").read_text()

def segment(begin: str, end: str) -> str:
    assert begin in dispatch and end in dispatch, f"missing dispatch branch: {begin}"
    return dispatch.split(begin, 1)[1].split(end, 1)[0]

sms = segment('"device.sms.send" -> {', '"device.calendar" ->')
assert 'requirePhysicalApproval("Send this SMS?"' in sms
assert sms.index("requirePhysicalApproval(") < sms.index("deviceComms.sendSms(")
assert '"To: $number\\nMessage:\\n$body"' in sms, "SMS full original body must be shown"

dial = segment('"device.dial" -> {', '"device.call_log" ->')
assert 'if (direct) requirePhysicalApproval(' in dial
assert dial.index("requirePhysicalApproval(") < dial.index("deviceComms.dial(")

vault = segment('"device.vault.fill" -> {', '"device.notifications" ->')
assert 'requirePhysicalApproval(' in vault
assert vault.index("requirePhysicalApproval(") < vault.index("requireScreen().typeText(")

assert 'fullText = true' in dispatch, "Security card must never truncate its contents"
assert 'val detailScroll = ScrollView(context)' in cards, "Missing full-content scroll"
assert 'if (!fullText)' in cards, "Full details must never be ellipsized"
assert 'captureSpeech = false' in dispatch, "Voice is not physical permission"
assert 'how == "button"' in policy and 'button == approveLabel' in policy

print("PASS: explicit, non-reusable button approval guards SMS, direct dialing and vault fills")
