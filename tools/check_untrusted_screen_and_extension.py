#!/usr/bin/env python3
"""Phase 7: prove untrusted screen/extension checks occur BEFORE side effects.

Source-level tripwire and JVM policy tests, not full instrumentation proof.
"""
from pathlib import Path
r = Path(__file__).resolve().parents[1]
p = (r / "core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt").read_text()
d = (r / "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt").read_text()
s = (r / "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt").read_text()
hub = (r / "app/src/main/java/net/kuafuai/andee/net/BodyWsClient.kt").read_text()
test = (r / "core/src/test/kotlin/net/kuafuai/andee/net/TurnGuardsTest.kt").read_text()

assert 'private val MESSAGING_PACKAGES = setOf(' in p
for pkg in ('com.whatsapp', 'com.whatsapp.w4b', 'com.tencent.mm'):
    assert '"'+pkg+'"' in p, f"missing official messaging package {pkg}"
for method in ('screen.tap', 'screen.tap_target', 'screen.tap_id',
               'screen.swipe', 'screen.long_press', 'screen.long_press_id',
               'screen.type', 'screen.submit_input'):
    assert '"'+method+'"' in p, f"untrusted screen method missing: {method}"
assert 'fun checkUntrustedScreen(' in p
assert 'fun messagingPackageForUntrustedLaunch(' in p
assert 'fun checkUntrustedExtension(' in p
assert 'fun livePackageForUntrustedGuard()' in s
assert 'activeRoot()?.packageName?' in s
assert 'lastDumpPkg' not in s[s.index('fun livePackageForUntrustedGuard()'):s.index('fun livePackageForUntrustedGuard()')+245], "stale tree used as authorization"
assert 'TurnGuards.checkUntrustedScreen(' in d
assert 'screenOrNull()?.livePackageForUntrustedGuard()' in d
assert d.index('TurnGuards.checkUntrustedScreen(') < d.index('if (driving && method.startsWith("screen."))'), "guard runs after UI side-effects"
assert 'TurnGuards.messagingPackageForUntrustedLaunch(' in d
assert 'label = if (untrusted) null else' in d, "spoofable app label used"
assert 'if (untrusted) TurnGuards.checkUntrustedExtension(method)' in d
assert 'forwardExternal(method, params)' in d, "trusted owner extension path removed"
assert d.index('TurnGuards.checkUntrustedExtension(method)') < d.index('forwardExternal(method, params)'), "untrusted extension forwarded before denial"
assert 'if (ToolSchemas.methodOf(toolName) == null)' in hub
assert hub.index('if (ToolSchemas.methodOf(toolName) == null)') < hub.index('dispatcher.beginTask()'), "Hub extension denial after task begins"
assert 'if (untrusted && method in UNTRUSTED_DIRECT_ACTIONS)' in p, "Phase 6 direct sensitive denial removed"
assert '"screen.tap", "screen.tap_target"' in test
assert 'TurnGuards.messagingPackageForUntrustedLaunch' in test
print("PASS: untrusted UI gestures restricted by live package and extensions denied before dispatch")
print("LIMITATION: within-messenger Send buttons and foreground races remain; Android phone UX not verified")
