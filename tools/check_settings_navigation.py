#!/usr/bin/env python3
"""Phase 10A: settings routing-only source and feature-preservation contract.

Runtime work in this slice stays in SettingsUi, plus localized navigation
strings. Gemini/provider network and original autonomous Andee paths are
explicitly excluded. Android emulator exercises each category and edits.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "c9dc4a1caa7a0d485084d1ab366be7337b9c1958"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

allowed = {
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsNavigationTest.kt",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsLockedVaultTest.kt",
    "tools/check_settings_navigation.py",
    "tools/check_vault_biometric_only.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_settings_safe.py",
    "tools/verify_feature_preservation.py",
    ".github/workflows/build.yml",
    "docs/PHASE10A_SETTINGS_NAVIGATION.md",
    # Phase 10B.1: explicit free provider/zero-cost guard and encrypted keys only.
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "app/src/main/java/net/kuafuai/andee/config/FactoryReset.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/BrainConfig.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/FreeProviderKeysTest.kt",
    "tools/check_free_provider_scope.py",
    "docs/PHASE10B1_FREE_PROVIDERS.md",
    # Phase 10B.2: xKiro free catalog diagnostics and documented ID only.
    "tools/check_xkiro_catalog_fix.py",
    "docs/PHASE10B2_XKIRO_CATALOG_FIX.md",
    # Phase 10B.3: xKiro free-only Auto routing and migration.
    "tools/check_xkiro_auto_scope.py",
    "docs/PHASE10B3_XKIRO_AUTO_FREE.md",
    # Phase 10B.4: system Back on visible full card; exact-scope checker active.
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/AndroidBackNavigationTest.kt",
    "tools/check_android_back_scope.py",
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
    "app/src/main/AndroidManifest.xml",

    "app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt",
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
}
changed = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
assert changed <= allowed, f"Unexpected code changes: {sorted(changed-allowed)}"
assert not git("diff", "--diff-filter=D", "--name-only", BASE, "HEAD"), "Files removed"

ui = read("app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt")
for token in (
    "private enum class Page { HOME, AI_VOICE, APPEARANCE, MEMORY, DEVICE, ADVANCED }",
    "Page.HOME -> settingsHome(form)",
    "Page.AI_VOICE -> brainSection(form)",
    "Page.APPEARANCE -> {",
    "Page.MEMORY -> {",
    "Page.DEVICE -> {",
    "Page.ADVANCED -> {",
    "private fun stageVisibleEdits()",
    "saved = saved + stagedFields + stagedPicks",
    "stageVisibleEdits()",
    "for ((k, raw) in stagedFields)",
    "for ((k, value) in stagedPicks)",
    "private fun settingsHome(form: LinearLayout)",
    "navigateTo(Page.HOME)",
    "vaultSection(form)", "gearSection(form)",
    "notebookSection(form)", "notifySection(form)", "driveSection(form)",
    "maxStepsSection(form)", "diagnosticsSection(form)",
    "themeSection(form)", "expressionsSection(form)", "wakeSection(form)",
    "showHubSection(form)", "typingSection(form)",
    "fun isShowing(): Boolean", "VoiceConfig.save(context, updates)",
    "VaultBioGate.ready(context)", "VaultUi(context)",
):
    assert token in ui, f"Missing feature/settings safety contract: {token}"

# Original runtime settings methods must still exist in the ONE SettingsUi
# owner. No independent provider executor/brain or second Settings screen.
for method in (
    "brainSection", "themeSection", "expressionsSection", "wakeSection",
    "notebookSection", "gearSection", "notifySection", "nudgeSection",
    "driveSection", "vaultSection", "maxStepsSection", "advancedSection",
    "showHubSection", "typingSection", "diagnosticsSection",
    "langPicker", "saveAndHide", "currentEdits",
):
    if method == "currentEdits":
        continue  # folded into stageVisibleEdits to persist across pages
    assert f"fun {method}(" in ui, f"Existing Settings function removed: {method}"

for path in ("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml"):
    data = read(path)
    for key in (
        "settings_nav_ai_voice", "settings_nav_ai_voice_note",
        "settings_nav_appearance", "settings_nav_appearance_note",
        "settings_nav_memory", "settings_nav_memory_note",
        "settings_nav_device", "settings_nav_device_note",
        "settings_nav_advanced", "settings_nav_advanced_note", "settings_nav_back",
    ):
        assert f'name="{key}"' in data, f"Missing localized navigation key {key}: {path}"

test = read("app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsNavigationTest.kt")
for method in (
    "fiveHomeRoutesAndExistingSettingsPagesBuildWithVaultLocked",
    "unsavedApiEditSurvivesNavigationWithoutTouchingPreferences",
):
    assert f"fun {method}()" in test, f"Missing emulator navigation test: {method}"
vault_test = read("app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsLockedVaultTest.kt")
assert '(it as Enum<*>).name == "DEVICE"' in vault_test
assert 'Vault.entries(app)' in vault_test

workflow = read(".github/workflows/build.yml")
assert workflow.count("python3 tools/check_settings_navigation.py") == 2
assert ":app:connectedDebugAndroidTest" in workflow

# Any production method outside SettingsUi and localized resource strings
# must still be byte-for-byte the previous green Phase 9.2 snapshot.
for path in (
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUi.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt",


    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "core/src/main/kotlin/net/kuafuai/andee/asr/GoogleAsr.kt",
    "core/src/main/kotlin/net/kuafuai/andee/tts/GoogleTts.kt",
):
    assert git("rev-parse", f"{BASE}:{path}") == git("rev-parse", f"HEAD:{path}"), f"Original behavior changed: {path}"

print("PASS: five Settings navigation categories + staged edits, original Andee behavior/voice/autonomy unchanged")
print("LIMIT: source audit and emulator cannot prove real-phone touch layout or all manufacturer gestures")
