#!/usr/bin/env python3
"""Phase 9.2: catch biometric Vault/Settings regression, preserve Andee behavior.

Source check is deliberately independent of Gradle. Android instrumentation
also opens the real Settings UI with a locked Vault on a disposable emulator.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "a7d5186f8b12360a76e22b6e7fd514ba9b514b0e"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def source(path):
    return (ROOT / path).read_text(encoding="utf-8")

allowed = {
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsLockedVaultTest.kt",
    "tools/check_vault_settings_safe.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_biometric_only.py",
    "tools/verify_feature_preservation.py",
    ".github/workflows/build.yml",
    "docs/PHASE9_2_SETTINGS_CRASH_FIX.md",
    # Phase 10A: clean Settings navigation only; no ordinary AI/voice runtime edits.
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsNavigationTest.kt",
    "tools/check_settings_navigation.py",
    "docs/PHASE10A_SETTINGS_NAVIGATION.md",
    # Phase 10B.1: explicit free provider/zero-cost guard and encrypted keys only.
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/BrainConfig.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/FreeProviderKeysTest.kt",
    "tools/check_free_provider_scope.py",
    "docs/PHASE10B1_FREE_PROVIDERS.md",
}
actual = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
assert actual <= allowed, f"Non-Settings production files modified: {sorted(actual-allowed)}"
assert not git("diff", "--diff-filter=D", "--name-only", BASE, "HEAD"), "Original files removed"

settings = source("app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt")
vault = settings[settings.index("private fun vaultSection("):settings.index("private fun diagnosticsSection(")]
assert "import net.kuafuai.andee.config.VaultBioGate" in settings
assert "if (!VaultBioGate.ready(context))" in vault, "Settings must not read locked credentials"
assert "return lctx.getString(R.string.settings_vault_locked_note)" in vault
assert "val n = runCatching { Vault.entries(context).size }.getOrElse {" in vault
assert "return lctx.getString(R.string.settings_vault_unavailable_note)" in vault
assert vault.index("if (!VaultBioGate.ready(context))") < vault.index("Vault.entries(context)"), "Credential read before gate"
assert "VaultUi(context) { state.text = summary() }.show()" in vault, "Manage workflow changed"

for path in ("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml"):
    text = source(path)
    for name in ("settings_vault_locked_note", "settings_vault_unavailable_note"):
        assert f'name="{name}"' in text, f"Missing localized {name} in {path}"

test = source("app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsLockedVaultTest.kt")
assert "fun settingsBuildsWithoutReadingLockedVault()" in test
assert "fun lockedVaultStillRefusesToReadCredentials()" in test
assert "settings.show()" in test and "Vault.entries(app)" in test

workflow = source(".github/workflows/build.yml")
assert ":app:connectedDebugAndroidTest" in workflow
assert workflow.count("python3 tools/check_vault_settings_safe.py") == 2

for path in (
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUi.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    "app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt",
):
    assert git("rev-parse", f"{BASE}:{path}") == git("rev-parse", f"HEAD:{path}"), f"Unrelated Andee behavior changed: {path}"
print("PASS: Settings opens without reading locked Vault, biometric-only credentials preserved, unrelated Andee runtime untouched")
print("LIMIT: video proves real crash; physical device stack trace and Android 16 UI verification still required")
