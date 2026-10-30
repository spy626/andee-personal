#!/usr/bin/env python3
"""Phase 10B.3 — safe xKiro client-side Auto: exact scope and no paid routes."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "3791ce5c5db4a9091e241d885825539bceeca4bd"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def data(path):
    return (ROOT / path).read_text(encoding="utf-8")

allowed = {
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/FreeProviderKeysTest.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",
    "tools/check_xkiro_auto_scope.py",
    "tools/check_xkiro_catalog_fix.py",
    "tools/check_free_provider_scope.py",
    "tools/check_settings_navigation.py",
    "tools/check_vault_biometric_only.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_settings_safe.py",
    "tools/verify_feature_preservation.py",
    ".github/workflows/build.yml",
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
actual = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
assert actual <= allowed, f"Phase 10B.3 edited out-of-scope files: {sorted(actual - allowed)}"
assert not git("diff", "--diff-filter=D", "--name-only", BASE, "HEAD"), "Removed source files"

for path in (

    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "app/src/main/java/net/kuafuai/andee/config/FactoryReset.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",

    "app/src/main/java/net/kuafuai/andee/ui/ball/EmotionBallTextureView.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/NotificationTriage.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/BrainConfig.kt",
    "core/src/main/kotlin/net/kuafuai/andee/asr/GoogleAsr.kt",
    "core/src/main/kotlin/net/kuafuai/andee/tts/GoogleTts.kt",
    "core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt",
):
    assert git("rev-parse", f"{BASE}:{path}") == git("rev-parse", f"HEAD:{path}"), f"Original Andee behavior changed: {path}"

guard = data("core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt")
for tok in (
    'const val XKIRO_AUTO = "xkiro/auto-free"',
    'XKIRO -> listOf(XKIRO_AUTO, XKIRO_QWEN)',
    'id.startsWith("deepseek/", ignoreCase = true)',
    'item.optString("access_tier") != "free"',
    'item.optJSONObject("capabilities")?.optBoolean("tools") != true',
    'item.optJSONObject("capabilities")?.optBoolean("vision") != true',
    'if (amount.compareTo(BigDecimal.ZERO) != 0)',
    'val quotaIssue = quotaProblem(get("$XKIRO_BASE/usage", true))',
    'fun autoEligibleModels(raw: String, needsVision: Boolean = false): List<String>',
    'fun preflightXKiroAuto(',
    'fun autoRetryable(status: Int): Boolean = status == 404 || status == 429',
):
    assert tok in guard, f"Missing free-routing gate: {tok}"

llm = data("core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt")
for tok in (
    'model == FreeProviderGuard.XKIRO_AUTO',
    'FreeProviderGuard.preflightXKiroAuto(http, apiKey, needsVision)',
    'candidate !in FreeProviderGuard.preflightXKiroAuto(',
    'body.put("model", candidate)',
    'candidates.take(if (autoXKiro) 3 else 1)',
    'FreeProviderGuard.autoRetryable(resp.code)',
    'if (autoXKiro) autoStickyModel = candidate',
    'FreeProviderGuard.preflightXKiro(http, apiKey, model)',
    'if (apiKey.isBlank())',
    '"$baseUrl/chat/completions"',
    '"xKiro Auto free models unavailable; no paid fallback"',
):
    assert tok in llm, f"Missing protected LLM auto route: {tok}"
assert llm.index('FreeProviderGuard.preflightXKiroAuto(http, apiKey, needsVision)') < llm.index('val req = Request.Builder()',llm.index('fun complete('))
assert '"deepseek/deepseek-v4.1-flash:free"' not in llm, "Retired DeepSeek must not reach model executor"

ui = data("app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt")
assert 'FreeProviderGuard.XKIRO -> listOf("Auto Free", "Qwen Free")' in ui
assert 'FreeProviderGuard.XKIRO -> listOf("DeepSeek Free", "Qwen Free")' not in ui
prefs = data("app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt")
assert 'val chosen = requested.takeIf { it in models } ?: models.first()' in prefs
assert '"free_model_xkiro" to p.getString("free_model_xkiro", FreeProviderGuard.XKIRO_AUTO)' in prefs

for path in ("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml"):
    assert 'name="settings_free_xkiro_safety"' in data(path)
test = data("core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt")
for tok in (
    "xKiroAutoOnlyKeepsFreeToolCapableCatalogModelsAndNeverDeepSeek",
    "autoUsesReadOnlyChecksAndOnlyDefiniteRejectionAllowsRetry",
    'FreeProviderGuard.XKIRO_DEEPSEEK',
):
    assert tok in test
emulator = data("app/src/androidTest/java/net/kuafuai/andee/acceptance/FreeProviderKeysTest.kt")
assert 'assertEquals(FreeProviderGuard.XKIRO_AUTO, cfg.model)' in emulator

workflow = data(".github/workflows/build.yml")
assert workflow.count("python3 tools/check_xkiro_auto_scope.py") == 2
assert ":app:connectedDebugAndroidTest" in workflow
print("PASS: xKiro Auto only verified free tool-capable models, no DeepSeek/paid/cross-provider routing, original Andee autonomy unchanged")
print("LIMIT: user-owned live xKiro Auto models and physical-device behavior remain to be tested")
