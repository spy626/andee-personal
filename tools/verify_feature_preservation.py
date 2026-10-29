#!/usr/bin/env python3
"""Fail-closed source preservation audit against exact upstream Andee v0.8.0 baseline.

This proves unmodified source-file identity, NOT that an Android device works.
When a feature legitimately changes, update the contract with new behavior tests;
do not silently widen ALLOWED_CHANGES to bypass a regression.
"""
import re
import subprocess
from pathlib import Path

BASE = "df667e69a342d462ff0bb4593a73d84a9509d059"
ROOT = Path(__file__).resolve().parents[1]

# Only tests/docs/build-tool compatibility changed on this audit branch.
# Changes to source files, permissions, the mascot, or existing tests fail CI.
ALLOWED_CHANGES = {
    ".github/workflows/build.yml",
    ".github/workflows/dco.yml",  # Exact pinned upstream history sign-off exemption only.
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.properties",
    "docs/FEATURE_PRESERVATION_RECOVERY.md",
    "docs/FEATURE_PARITY_AUDIT.md",
    "tools/verify_feature_preservation.py",
    "tools/check_v080_feature_integration.py",
    # Phase 9 biometric-only Vault hardening. No general owner-approval redesign.
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUi.kt",
    "app/src/main/AndroidManifest.xml",
    "tools/check_vault_biometric_only.py",
    "docs/PHASE9_VAULT_BIOMETRIC_ONLY.md",
    # Phase 9.1: Vault-only screen lock regression. No other production files.
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/VaultScreenLockTest.java",
    "tools/check_vault_screen_lock.py",
    "docs/PHASE9_1_SCREEN_LOCK_REVOCATION.md",
    "docs/PHASE8_V080_INTEGRATION.md",
    # Explicitly scoped transport fixes. Existing UI/messaging/tool
    # dispatch/voice/rendering source must remain byte-for-byte identical.
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    "app/src/main/java/net/kuafuai/andee/net/BodyWsServer.kt",
    "app/src/main/java/net/kuafuai/andee/net/BodyWsClient.kt",
    "core/src/main/kotlin/net/kuafuai/andee/net/NetworkPolicy.kt",
    "core/src/test/kotlin/net/kuafuai/andee/net/NetworkPolicyTest.kt",
    "tools/check_network_transport.py",
    # New local socket session-consent policy and its independent JVM tests.
    "core/src/main/kotlin/net/kuafuai/andee/net/LocalPeerConsent.kt",
    "core/src/test/kotlin/net/kuafuai/andee/net/LocalPeerConsentTest.kt",
    "tools/check_local_connection_consent.py",
    # Narrow signed remote-Hub identity handshake and owner-pinned public key UI.
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "core/src/main/kotlin/net/kuafuai/andee/net/HubIdentity.kt",
    "core/src/test/kotlin/net/kuafuai/andee/net/HubIdentityTest.kt",
    "tools/check_signed_hub_identity.py",
    "docs/HUB_SIGNED_IDENTITY_PROTOCOL.md",
    # Free, opt-in external bridge + TLS/mocked-backend tests; no Android UX edits.
    "docs/HUB_ADAPTER_RUNBOOK.md",
    "tools/hub_signed_adapter.py",
    "tools/tests/test_hub_signed_adapter.py",
    "tools/requirements-hub-adapter.txt",
    "tools/check_hub_adapter.py",
    # Phase 5 tests/docs ONLY: never authorize SMS/WhatsApp or touch production
    # notification, card, voice and mascot implementations.
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/AndeeSafeSmokeTest.java",
    "tools/check_original_feature_contracts.py",
    "docs/PHASE5_FEATURE_ACCEPTANCE.md",
    # Phase 6 narrow provenance gate: block direct sensitive tool execution
    # only on remote Hub / stranger-authored turns. SMS/WhatsApp UI untouched.
    "core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt",
    "core/src/test/kotlin/net/kuafuai/andee/net/TurnGuardsTest.kt",
    "tools/check_sensitive_turn_gates.py",
    "docs/PHASE6_SENSITIVE_ACTION_GATE.md",
    # Phase 7: live Android app boundary + untrusted extension fail-close.
    # Original WhatsApp/SMS, notification, voice and mascot modules untouched.
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "tools/check_untrusted_screen_and_extension.py",
    "docs/PHASE7_UNTRUSTED_UI_BOUNDARY.md",
    "SECURITY.md",  # Written disclosure of known risks, not runtime code.
}

def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout

def blobs(ref):
    entries = {}
    for item in git("ls-tree", "-rz", "--full-tree", ref).split(b"\0"):
        if not item:
            continue
        head, path = item.split(b"\t", 1)
        mode, kind, sha = head.split(b" ")
        if kind == b"blob":
            entries[path.decode("utf-8")] = sha.decode("ascii")
    return entries

assert subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"],
                      cwd=ROOT).returncode == 0, "original baseline is not an ancestor"
base, head = blobs(BASE), blobs("HEAD")
missing = sorted(set(base) - set(head))
modified = sorted(path for path in base.keys() & head.keys()
                  if base[path] != head[path] and path not in ALLOWED_CHANGES)
unexpected_new = sorted(set(head) - set(base) - ALLOWED_CHANGES)
assert not missing, f"ORIGINAL FEATURES / FILES REMOVED: {missing}"
assert not modified, f"ORIGINAL SOURCE FILES MODIFIED WITHOUT APPROVAL: {modified}"
assert not unexpected_new, f"UNREVIEWED ADDITIONS: {unexpected_new}"

# Confirm original capability entry points still exist, not merely some files.
MUST_CONTAIN = {
    "app/src/main/AndroidManifest.xml": (
        "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.RECORD_AUDIO",
        "android.permission.SEND_SMS", "android.permission.READ_CONTACTS",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        ".BodyCoreService", ".ScreenBodyService",
    ),
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt": (
        "fun notificationLookRequested(", "fun handleNotification(",
        "brain?.notified(", "fun onBrainFinal(",
        "CardUi.tryAnswerByVoice", "fun notificationLookQuery(",
    ),
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt": (
        "fun onNotificationPosted(", "offerForTriage(", "com.whatsapp",
        "fun showSignboardFor(", "notificationLookRequested(",
    ),
    "core/src/main/kotlin/net/kuafuai/andee/brain/NotificationTriage.kt": (
        "enum class Action", "fun decide(", "ACT,", "TELL,", "IGNORE,",
    ),
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt": (
        "fun notified(", "fun submit(", "fun runTool(", "TurnKind.NOTIFY",
    ),
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt": (
        '"device.sms.send"', '"device.dial"', '"device.vault.fill"',
        '"screen.tap"', '"screen.type"', '"screen.submit_input"', '"ui.ask"',
    ),
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt": (
        "fun sendSms(", "sendTextMessage(", "fun dial(", "ACTION_DIAL", "ACTION_CALL",
    ),
    "app/src/main/java/net/kuafuai/andee/ui/CardUi.kt": (
        "fun ask(", "fun bubble(", "fun tryAnswerByVoice(", "wireButtons(",
    ),
    "app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt": (
        "fun showSignboard(", "fun setState(", "TYPE_APPLICATION_OVERLAY",
    ),
    "app/src/main/java/net/kuafuai/andee/ui/ball/EmotionBallTextureView.kt": (
        "setMood(", "setEmotion(", "setListening(", "setWorking(", "postDelayed",
    ),
    "core/src/main/kotlin/net/kuafuai/andee/ui/ball/Mood.kt": (
        "enum class Mood",
    ),
    "core/src/main/kotlin/net/kuafuai/andee/ui/ball/EmotionBallRenderer.kt": (
        "class EmotionBallRenderer",
    ),
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt": (
        "fun typeText(", "fun tapAim(", "fun dumpUiTree(",
    ),
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt": (
        "fun hubConfig(", "fun notifyScope(", "fun brainMode(",
    ),
    "core/src/main/kotlin/net/kuafuai/andee/peer/PeerTasks.kt": (
        "fun onMessage(", "fun take(", "interface Brain",
    ),
    "core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt": (
        "fun check(", 'method.startsWith("device.vault.")',
    ),
    "core/src/main/kotlin/net/kuafuai/andee/net/NetworkPolicy.kt": (
        "fun isSecureHubUrl(", '"wss"',
    ),
    "core/src/main/kotlin/net/kuafuai/andee/net/HubIdentity.kt": (
        "fun isValidPinnedKey(", "fun newChallenge(", "fun verify(",
    ),
}
for path, tokens in MUST_CONTAIN.items():
    data = (ROOT / path).read_text(encoding="utf-8")
    absent = [tok for tok in tokens if tok not in data]
    assert not absent, f"CAPABILITY ENTRY POINT REMOVED: {path}: {absent}"

# Original untouched code can contain unsafe operations or incomplete flows:
# do not mislabel this file-identity check as security or end-to-end UX proof.
preserved = len(base) - len([p for p in base if p in ALLOWED_CHANGES])
# The reviewed security paths may differ; all other v0.8.0 source and assets
# must remain exactly the same as the original release.
assert len(base) == 474, f"Upstream v0.8.0 baseline inventory unexpectedly changed: {len(base)}"
# Phase 9 reviews three more original files (Vault, VaultUi, Manifest); 17
# upstream paths are explicitly allowlisted in total. The
# dedicated Phase 9 gate also compares every unrelated runtime file to Phase 8.
assert preserved >= 457, f"v0.8.0 feature preservation threshold failed: {preserved}"
unchanged = sum(base[p] == head[p] for p in base)
print(f"PASS: {unchanged}/{len(base)} upstream v0.8.0 Git blobs byte-for-byte unchanged;"
      f" no deleted source and {len(MUST_CONTAIN)} feature paths with core entry points")
print("LIMITATION: WhatsApp/SMS draft, approval, sending and Android animation"
      " still require behavioral integration and physical device tests.")
