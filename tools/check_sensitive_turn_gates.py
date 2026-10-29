#!/usr/bin/env python3
"""Fail CI if a remote Hub or untrusted notification can invoke direct writes.

Narrow regression check; not a substitute for malicious-content Android tests.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
g=(root/"core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt").read_text()
client=(root/"app/src/main/java/net/kuafuai/andee/net/BodyWsClient.kt").read_text()
dispatcher=(root/"app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt").read_text()
test=(root/"core/src/test/kotlin/net/kuafuai/andee/net/TurnGuardsTest.kt").read_text()
for m in ("device.sms.send", "device.dial", "device.calendar.add",
          "device.permissions.request", "device.open_url", "device.alarm",
          "dog.move", "dog.sequence", "dog.turn", "dog.speed", "dog.pose", "dog.calibrate"):
    assert '"'+m+'"' in g, f"Untrusted direct action gate missing {m}"
assert "if (untrusted && method in UNTRUSTED_DIRECT_ACTIONS)" in g
assert 'TurnGuards.check(method, silent = false, untrusted = true)' in client, "Hub ingress must guard before task begins"
assert client.index('TurnGuards.check(method, silent = false, untrusted = true)') < client.index('dispatcher.beginTask()'), "Hub task starts before trust check"
assert 'dispatcher.dispatch(method, params, untrusted = true)' in client, "Hub dispatch must preserve untrusted provenance"
assert 'TurnGuards.check(method, silent, untrusted)' in dispatcher, "Final sensitive tool guard missing"
assert '"device.sms.send" -> deviceComms.sendSms(' in dispatcher, "Original SMS handler changed"
for tool in ("screen.tap", "screen.type", "screen.submit_input", "ui.ask"):
    assert '"'+tool+'"' in test, f"Original WhatsApp/card path was not tested as allowed: {tool}"
assert "a user turn may call anything" in test
print("PASS: direct sensitive writes gated for notification and remote Hub; original local user SMS path retained")
print("LIMITATION: screen gestures, extension tools and trusted Hub replies can still have side effects; do not call this complete prompt-injection defense")
