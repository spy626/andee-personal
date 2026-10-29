#!/usr/bin/env python3
"""Phase 10B.2: xKiro model catalog fix, no silent provider/paid fallback.

Independent exact-scope check comparing against known GREEN Phase 10B.1 head.
Also checks the guard is before the original agent's inference POST.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "ae7b3a6e7502812b3d061b7f07fb0016764be82b"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def source(path):
    return (ROOT / path).read_text(encoding="utf-8")

allow = {
    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",
    "tools/check_xkiro_catalog_fix.py",
    "tools/check_free_provider_scope.py",
    "tools/check_vault_biometric_only.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_settings_safe.py",
    "tools/check_settings_navigation.py",
    "tools/verify_feature_preservation.py",
    ".github/workflows/build.yml",
    "docs/PHASE10B2_XKIRO_CATALOG_FIX.md",
}
changed = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
assert changed <= allow, f"Unexpected scope: {sorted(changed - allow)}"
assert not git("diff", "--diff-filter=D", "--name-only", BASE, "HEAD"), "Deleted files"

for path in (
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/java/net/kuafuai/andee/config/FactoryReset.kt",
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/BrainConfig.kt",
    "core/src/main/kotlin/net/kuafuai/andee/asr/GoogleAsr.kt",
    "core/src/main/kotlin/net/kuafuai/andee/tts/GoogleTts.kt",
):
    assert git("rev-parse", f"{BASE}:{path}") == git("rev-parse", f"HEAD:{path}"), f"Original Andee production behavior changed: {path}"

guard = source("core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt")
assert 'const val XKIRO_DEEPSEEK = "deepseek/deepseek-v4.1-flash:free"' in guard
assert 'const val XKIRO_QWEN = "qwen/qwen3-vl-plus:free"' in guard
assert 'qwen/qwen3-coder-plus:free' not in guard, "Guessed old Qwen ID still used"
for marker in (
    "fun catalogProblem(raw: String, model: String): String?",
    'return "model_not_listed"',
    'return "invalid_catalog"',
    'return "not_free_tier"',
    'return "tools_not_verified"',
    'return "pricing_missing"',
    'return "pricing_unverified"',
    'return "nonzero_price"',
    'item.optString("access_tier") != "free"',
    'item.optJSONObject("capabilities")?.optBoolean("tools") != true',
    'if (amount.compareTo(BigDecimal.ZERO) != 0)',
    'get("$XKIRO_BASE/models", false)',
    'get("$XKIRO_BASE/usage", true)',
    'throw IOException("xKiro preflight blocked ($problem):',
    "fun quotaIsVerifiedFree(raw: String): Boolean",
):
    assert marker in guard, f"Missing xKiro free safety check: {marker}"
assert "fun catalogIsVerifiedFree(raw: String, model: String): Boolean =" in guard
assert "catalogProblem(raw, model) == null" in guard
assert guard.index("val problem = catalogProblem(") < guard.index('get("$XKIRO_BASE/usage", true)'), "Model must be verified before authenticated usage"
assert "POST" not in guard.split("fun preflightXKiro")[1], "Preflight should never POST"

tests = source("core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt")
for token in (
    "catalogReasonsSeparateMissingModelFromUnsafePricingOrTools",
    "qwenDocumentedFreeIdIsDifferentFromRetiredGuess",
    "mockHttpPreflightUsesOnlyGetAndRejectsUnavailableModelBeforeUsage",
    '"GET /v1/models", "GET /v1/usage"',
):
    assert token in tests
workflow = source(".github/workflows/build.yml")
assert workflow.count("python3 tools/check_xkiro_catalog_fix.py") == 2
print("PASS: xKiro catalog diagnostics, documented Qwen free ID, old Andee runtime unchanged, no paid fallback")
print("LIMIT: live user's free catalog/usage/account and real-phone response still require actual owner acceptance")
