#!/usr/bin/env python3
"""Phase 9.1: Vault-only phone-lock revocation audit.

CI source boundary + device-level regression fixture (run by existing Android
emulator job). Does not prove real hardware fingerprint/migration acceptance.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "9bb10fd9a46e73e713e462c7fd2c4ae69c1957b3"

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

allowed = {
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/VaultScreenLockTest.java",
    "tools/check_vault_biometric_only.py",
    "tools/verify_feature_preservation.py",
    "tools/check_vault_screen_lock.py",
    "docs/PHASE9_1_SCREEN_LOCK_REVOCATION.md",
    # Phase 9.2: narrow Settings locked-Vault crash fix and tests.
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsLockedVaultTest.kt",
    "tools/check_vault_settings_safe.py",
    "docs/PHASE9_2_SETTINGS_CRASH_FIX.md",
    # Phase 10A: clean Settings navigation only; no ordinary AI/voice runtime edits.
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/SettingsNavigationTest.kt",
    "tools/check_settings_navigation.py",
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
    "app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt",
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    ".github/workflows/build.yml",
}
changed = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
assert changed <= allowed, f"Changes outside Vault revocation: {sorted(changed - allowed)}"
assert not set(git("diff", "--diff-filter=D", "--name-only", BASE, "HEAD").splitlines()), "Files removed"

policy = read("app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt")
activity = read("app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt")
test = read("app/src/androidTest/java/net/kuafuai/andee/acceptance/VaultScreenLockTest.java")
workflow = read(".github/workflows/build.yml")

for required in (
    "Intent.ACTION_SCREEN_OFF", "private val screenOffReceiver",
    "app.registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)",
    "app.registerReceiver(screenOffReceiver, filter)",
    "if (!observeScreenOff(context) || !deviceUnlocked(context))",
    "if (intent?.action == Intent.ACTION_SCREEN_OFF) lockNow()",
    "activePromptId = ++promptSequence", ".putExtra(EXTRA_PROMPT_ID, launchId)",
    "fun complete(accepted: Boolean, promptId: Long, context: Context)",
    "promptId != activePromptId", "accepted && observerRegistered && deviceUnlocked(context)",
    "fun lockNow()", "unlockedUntil = 0L", "activePromptId = 0L",
    "waiting.forEach { runCatching { it(false) } }",
):
    assert required in policy, f"Missing screen-off safety boundary: {required}"
assert policy.index("if (!prompting || promptId == 0L || promptId != activePromptId) return") < policy.index("unlockedUntil = if (granted)"), "stale prompt checked too late"

assert "VaultBioGate.EXTRA_PROMPT_ID" in activity
assert "VaultBioGate.complete(success, promptId, applicationContext)" in activity
assert "VaultBioGate.complete(false, promptId, applicationContext)" in activity
assert "VaultBioGate.complete(success)" not in activity

for name in (
    "screenOffRevokesAnAlreadyUnlockedVaultSession",
    "successFromBeforeScreenOffCannotReopenVault",
    "onlyTheCurrentBiometricPromptMayGrantLease",
):
    assert name in test, f"Missing device regression: {name}"
assert "android-emulator-smoke:" in workflow and ":app:connectedDebugAndroidTest" in workflow
assert "python3 tools/check_vault_screen_lock.py" in workflow

# No unrelated production changes; automatic messaging/screen WORK remains as is.
for path in (
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUi.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",

):
    assert git("rev-parse", f"{BASE}:{path}") == git("rev-parse", f"HEAD:{path}"), f"Unrelated feature source changed: {path}"

print("PASS: Vault screen-off revokes session, old biometric responses fail closed, normal Andee feature source untouched")
print("LIMIT: synthetic emulator test is not proof of physical fingerprint or legacy Vault migration")
