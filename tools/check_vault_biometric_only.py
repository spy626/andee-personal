#!/usr/bin/env python3
"""Phase 9: narrow Vault biometric-only source gate.

Strong credential-at-rest key binding plus verified migration; explicitly reject
new approval checks in SMS, WhatsApp, normal taps and autonomous work.
Source gate is not proof of on-device biometric behavior.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PHASE8 = "33049233a157372db6eefc2864688815ebf776f7"

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True)

allowed = {
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUi.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    ".github/workflows/build.yml",
    "tools/verify_feature_preservation.py",
    "tools/check_vault_biometric_only.py",
    "docs/PHASE9_VAULT_BIOMETRIC_ONLY.md",
    # Phase 9.1: screen-lock revocation and regression tests, no ordinary tool edits.
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/VaultScreenLockTest.java",
    "tools/check_vault_screen_lock.py",
    "docs/PHASE9_1_SCREEN_LOCK_REVOCATION.md",
}
actual = set(git("diff", "--name-only", PHASE8, "HEAD").splitlines())
assert actual <= allowed, f"Unreviewed changes outside Vault: {sorted(actual - allowed)}"

vault = read("app/src/main/java/net/kuafuai/andee/config/Vault.kt")
for token in (
    ".setUserAuthenticationRequired(true)",
    ".setUserAuthenticationParameters(600, KeyProperties.AUTH_BIOMETRIC_STRONG)",
    "private const val KEY_BLOB_LEGACY",
    "private const val KEY_BLOB_BIO",
    "decryptLegacy(legacy)",
    "decryptBiometric(encrypted) == original",
    "putString(KEY_BLOB_BIO, encrypted).commit()",
    "retireLegacy(preferences)",
    "VaultBioGate.requireActive(context)",
    "throw IllegalStateException",
):
    assert token in vault, f"Vault migration / biometric guard missing: {token}"
migration = vault[vault.index("val legacy = preferences.getString(KEY_BLOB_LEGACY"):]
assert migration.index("putString(KEY_BLOB_BIO, encrypted).commit()") < migration.index("retireLegacy(preferences)"), "old secrets removed before verified new commit"
assert "emptyList()" not in vault[vault.index("}.getOrElse {"):vault.index("/** Insert or replace by [Entry.id]. */")], "failed Vault read treated as empty"

bio = read("app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt")
prompt = read("app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt")
ui = read("app/src/main/java/net/kuafuai/andee/ui/VaultUi.kt")
dispatch = read("app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt")
manifest = read("app/src/main/AndroidManifest.xml")
assert "BIOMETRIC_STRONG" in prompt and "setAllowedAuthenticators" in prompt
assert "setNegativeButton" in prompt and "respond(false)" in prompt
assert "android.permission.USE_BIOMETRIC" in manifest
assert 'android:name=".ui.VaultUnlockActivity"' in manifest and 'android:exported="false"' in manifest.split('android:name=".ui.VaultUnlockActivity"')[1].split("/>")[0]
assert "VaultBioGate.request(context)" in ui and "VaultBioGate.ready(context)" in ui
assert "lockWatcher" in ui and "VaultBioGate.ready(context)" in ui
assert "VaultBioGate.requireUnlocked(appContext)" in dispatch
assert dispatch.count("VaultBioGate.requireUnlocked(appContext)") == 3, "approval added outside Vault or Vault path missed"
assert "VaultBioGate.requireActive(context)" in vault

# The user explicitly requires *all* ordinary Andee behaviors as before.
for path in (
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt",
    "app/src/main/java/net/kuafuai/andee/ui/CardUi.kt",
    "core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "app/src/main/java/net/kuafuai/andee/net/BodyWsServer.kt",
):
    assert git("rev-parse", f"{PHASE8}:{path}").strip() == git("rev-parse", f"HEAD:{path}").strip(), f"Original Andee behavior source modified: {path}"

print("PASS: Vault biometric-only scope, strong Keystore auth, migration, original Andee features unchanged")
print("LIMIT: physical fingerprint enrollment, saved-data migration and real-phone autofill require spare-phone acceptance")
