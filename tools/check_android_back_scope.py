#!/usr/bin/env python3
"""Phase 10B.4 — narrow Android Back UX hotfix with preserved Andee autonomy.

Back folds the fullscreen overlay via existing X/minimize callback. It must not
intercept keyboard Activity, folded ball, screenshots, synthetic gestures or
background autonomous work. A real Android 16 gesture still needs phone proof.
"""
from pathlib import Path
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
BASE="5ef814b0c332c1dc0090cd0099aca9808aa3b677"
def git(*args):
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True).strip()
def read(p):
    return (ROOT/p).read_text(encoding="utf-8")

allowed={
    "app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt",
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/AndroidBackNavigationTest.kt",
    ".github/workflows/build.yml",
    "docs/PHASE10B4_ANDROID_BACK.md",
    # Phase 10B.5: Back overlay priority + fail-closed Groq/quota diagnostics.
    "tools/check_phase10b5.py",
    "docs/PHASE10B5_BACK_GROQ_QUOTA.md",
    # Phase 10B.6: bounded LYRA-style Groq text-chat implementation.
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalPrompt.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/GroqChatContext.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/GroqChatContextTest.kt",
    "docs/PHASE10B6_GROQ_LYRA_CHAT.md",
    "tools/check_phase10b6.py",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",

    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",

    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",

    "app/src/main/AndroidManifest.xml",

    "tools/check_android_back_scope.py",
    "tools/verify_feature_preservation.py",
    "tools/check_vault_biometric_only.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_settings_safe.py",
    "tools/check_settings_navigation.py",
    "tools/check_free_provider_scope.py",
    "tools/check_xkiro_catalog_fix.py",
    "tools/check_xkiro_auto_scope.py",
}
changed=set(git("diff","--name-only",BASE,"HEAD").splitlines())
assert changed<=allowed, f"Unrelated changes: {sorted(changed-allowed)}"
assert not git("diff","--diff-filter=D","--name-only",BASE,"HEAD"), "Original files removed"

# Any delta to existing BodyCoreService must be exactly keyboard focus handoff.
body="app/src/main/java/net/kuafuai/andee/BodyCoreService.kt"
base=git("show",f"{BASE}:{body}")
expected=base.replace(
    '        typingFromFullscreen = true\n        net.kuafuai.andee.ui.TextInputActivity.start(this, then)',
    '        typingFromFullscreen = true\n'
    '        // Full chat now owns Android Back. Before opening a real Activity and\n'
    '        // keyboard, release that focus so Back dismisses the IME/field first.\n'
    '        window.setTypingBackPassthrough(true)\n'
    '        net.kuafuai.andee.ui.TextInputActivity.start(this, then)',
).replace(
    '        window.setTypingTop(0)\n        window.setAway(false)\n'
    '        if (typingFromFullscreen && !window.isCompact()) dispatcher.expand()',
    '        window.setTypingTop(0)\n        window.setAway(false)\n'
    '        window.setTypingBackPassthrough(false)\n'
    '        if (typingFromFullscreen && !window.isCompact()) dispatcher.expand()',
)
assert expected.strip() == read(body).strip(), "Body service changed beyond two keyboard focus handoffs"

# Protect whole original LLM/voice/screen/autonomous functions byte-for-byte.
for p in (
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/java/net/kuafuai/andee/ui/TextInputActivity.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "core/src/main/kotlin/net/kuafuai/andee/asr/GoogleAsr.kt",
    "core/src/main/kotlin/net/kuafuai/andee/tts/GoogleTts.kt",
):
    assert git("rev-parse",f"{BASE}:{p}")==git("rev-parse",f"HEAD:{p}"), f"Original Andee runtime changed: {p}"

# Window changes only in imports, class header, focus state, show/hide, and
# applyWindow. Existing ball gestures, scene rendering, messages untouched.
p="app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt"
diff=git("diff","--unified=0",BASE,"HEAD","--",p)
ranges=((1,35),(95,120),(295,315),(545,660),(940,971),(2270,2335))
hunks=re.findall(r"^@@ -(\d+)(?:,\d+)? \+\d+(?:,\d+)? @@",diff,re.M)
assert len(hunks)>=4, "Expected focus, back and window-state changes"
for n in map(int,hunks):
    assert any(lo<=n<=hi for lo,hi in ranges), f"Unexpected change to original mascot/chat/gesture logic at base line {n}"
window=read(p)
for marker in (
    "private fun shouldHandleBack(): Boolean =",
    "FullscreenBackPolicy.claimsBack(",
    "private fun minimizeOnAndroidBack()",
    "listeners.onMinimizeClick()",
    "v.setOnKeyListener { _, keyCode, event ->",
    "KeyEvent.KEYCODE_BACK",
    "android.window.OnBackInvokedCallback { minimizeOnAndroidBack() }",
    "dispatcher.registerOnBackInvokedCallback(",
    "backDispatcher?.unregisterOnBackInvokedCallback(callback)",
    "setTypingBackPassthrough(enabled: Boolean)",
    "if (shouldHandleBack()) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE",
    "if (gesturePassthrough || typingTop > 0)",
    "if (Build.VERSION.SDK_INT >= 33) unregisterApi33Back()",
    "syncAndroidBackRegistration()",
):
    assert marker in window, f"Back behavior missing: {marker}"
assert "TYPE_APPLICATION_OVERLAY" in window
assert 'barButton(R.drawable.ic_close, Glass.PHOTO_INK, "收起") { listeners.onMinimizeClick() }' in window
assert "setBallAreaHeight(" in window and "registerTap()" in window, "Mascot/gesture owner removed"

test=read("app/src/androidTest/java/net/kuafuai/andee/acceptance/AndroidBackNavigationTest.kt")
assert "fullscreenChatClaimsAndroidBackExactlyLikeX" in test
assert "foldedMascotAndKeyboardAndAutomationNeverInterceptBack" in test
workflow=read(".github/workflows/build.yml")
assert workflow.count("python3 tools/check_android_back_scope.py")==2
assert ":app:connectedDebugAndroidTest" in workflow

print("PASS: Android Back routes only fullscreen Andee to existing X action; compact, typing, screen tools and agent unchanged")
print("LIMIT: Android 16 predictive gesture and physical keyboard/IME acceptance still require spare-phone recording")
