#!/usr/bin/env python3
"""Andee Phase 10B.1 — narrow source contract, independent of prior gates.

No live requests or real keys. Protect the original local WORK, tool dispatch,
screen taps, SMS/WhatsApp, voice, mascot and biometric Vault byte-for-byte.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "538ef60334a01eda36c7fe21f5cc0f20d0d40270"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

allowed = {
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "app/src/main/java/net/kuafuai/andee/config/FactoryReset.kt",
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "core/src/main/kotlin/net/kuafuai/andee/config/BrainConfig.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalPrompt.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/GroqChatContext.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/GroqChatContextTest.kt",
    "app/src/androidTest/java/net/kuafuai/andee/acceptance/FreeProviderKeysTest.kt",
    "tools/check_free_provider_scope.py",
    "tools/check_vault_biometric_only.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_settings_safe.py",
    "tools/check_settings_navigation.py",
    "tools/verify_feature_preservation.py",
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
    "docs/PHASE10B6_GROQ_LYRA_CHAT.md",
    "tools/check_phase10b6.py",
    "app/src/main/AndroidManifest.xml",

    "app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt",
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    ".github/workflows/build.yml",
}
actual = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
assert actual <= allowed, f"Phase 10B.1 changed unrelated files: {sorted(actual - allowed)}"
assert not git("diff", "--diff-filter=D", "--name-only", BASE, "HEAD"), "A source file was deleted"

for path in (

    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt",

    "app/src/main/java/net/kuafuai/andee/ui/ball/EmotionBallTextureView.kt",
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUnlockActivity.kt",
    "app/src/main/java/net/kuafuai/andee/ui/VaultUi.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/NotificationTriage.kt",
    "core/src/main/kotlin/net/kuafuai/andee/asr/GoogleAsr.kt",
    "core/src/main/kotlin/net/kuafuai/andee/tts/GoogleTts.kt",
    "core/src/main/kotlin/net/kuafuai/andee/net/TurnGuards.kt",
):
    assert git("rev-parse", f"{BASE}:{path}") == git("rev-parse", f"HEAD:{path}"), f"Original Andee runtime changed: {path}"

brain = read("core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt")
old_brain = git("show", f"{BASE}:core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt")
expected_brain = old_brain.replace(
    "        reasoningEffort = cfg.reasoningEffort,\n    )",
    "        reasoningEffort = cfg.reasoningEffort,\n        freeProvider = cfg.freeProvider,\n    )",
)
# Reverse only the explicitly reviewed Groq-specific branches, then require
# every legacy/xKiro/OpenRouter agent path to remain byte-identical.
normalised = brain
for before, after in (
    ('        if (text.isEmpty() && images.isEmpty()) return\n',
     '        if (text.isEmpty() && images.isEmpty()) return\n'
     '        if (cfg.freeProvider == FreeProviderGuard.GROQ && images.isNotEmpty()) {\n'
     '            onFinal("Groq Free is text-only in Andee. Photos and screenshots are not sent; switch to a verified vision-capable free provider for image tasks.")\n'
     '            return\n'
     '        }\n'),
    ('    fun note(text: String) {\n',
     '    fun note(text: String) {\n'
     '        // LYRA-style Groq never receives internal notes or personal-memory context.\n'
     '        if (cfg.freeProvider == FreeProviderGuard.GROQ) return\n'),
    ('    private fun rebuildHistoryFromScrollback() {\n',
     '    private fun rebuildHistoryFromScrollback() {\n'
     '        if (cfg.freeProvider == FreeProviderGuard.GROQ) return\n'),
    ('    private fun systemPrompt(): String = LocalPrompt.withPersona(\n'
     '        host.persona(),\n'
     '        host.activeSceneName()?.let { host.scene(it) },\n'
     '        host.platformPrompt(),\n'
     '    )',
     '    private fun systemPrompt(): String =\n'
     '        if (cfg.freeProvider == FreeProviderGuard.GROQ) {\n'
     '            LocalPrompt.groqChat(host.persona())\n'
     '        } else {\n'
     '            LocalPrompt.withPersona(\n'
     '                host.persona(),\n'
     '                host.activeSceneName()?.let { host.scene(it) },\n'
     '                host.platformPrompt(),\n'
     '            )\n'
     '        }'),
    ('    private fun runTurn(userText: String, gen: Int, kind: TurnKind, images: List<String> = emptyList()) {\n',
     '    private fun runTurn(userText: String, gen: Int, kind: TurnKind, images: List<String> = emptyList()) {\n'
     '        // Groq follows LYRA\'s text-only chat contract, not Andee\'s agent route.\n'
     '        // Background, notification, peer, work and review turns must not be\n'
     '        // turned into unsupported pretend actions.\n'
     '        if (cfg.freeProvider == FreeProviderGuard.GROQ && kind != TurnKind.USER) {\n'
     '            host.logInfo(TAG, "Groq text-chat skipped non-user $kind turn; no device action or memory request sent")\n'
     '            return\n'
     '        }\n'),
    ('        } else {\n            // The notebook index rides in **here**, on the user message, and\n',
     '        } else if (cfg.freeProvider == FreeProviderGuard.GROQ) {\n'
     '            // No notebook, plan, selfhood, scene, page, topic, gear, friends,\n'
     '            // device state or timestamp is sent to Groq. Only the user\'s words.\n'
     '            JSONObject().put("role", "user").put("content", userText.take(GroqChatContext.MAX_USER_CHARS))\n'
     '        } else {\n            // The notebook index rides in **here**, on the user message, and\n'),
    ('        val tools = if (quiet) LlmClient.toolSpecs(*host.workTools(workTerritory).toTypedArray())\n'
     '        else LlmClient.toolSpecs(*host.toolLists().toTypedArray())',
     '        val tools = if (cfg.freeProvider == FreeProviderGuard.GROQ) JSONArray()\n'
     '        else if (quiet) LlmClient.toolSpecs(*host.workTools(workTerritory).toTypedArray())\n'
     '        else LlmClient.toolSpecs(*host.toolLists().toTypedArray())'),
    ('    private fun maybeHarvest() {\n        if (cfg.apiKey.isEmpty()) return',
     '    private fun maybeHarvest() {\n'
     '        if (cfg.freeProvider == FreeProviderGuard.GROQ || cfg.apiKey.isEmpty()) return'),
):
    assert after in normalised, "Missing reviewed Groq-only LocalBrain change"
    normalised = normalised.replace(after, before, 1)
assert normalised.strip() == expected_brain.strip(), "LocalBrain changes escaped the Groq-only chat route"

guard = read("core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt")
for required in (
    'const val XKIRO_BASE = "https://api.xkiro.com/v1"',
    'const val GROQ_BASE = "https://api.groq.com/openai/v1"',
    'const val OPENROUTER_BASE = "https://openrouter.ai/api/v1"',
    'const val XKIRO_DEEPSEEK = "deepseek/deepseek-v4.1-flash:free"',
    '"access_tier") != "free"', '"tools") != true',
    'p.has("input")', 'p.has("output")',
    'if (amount.compareTo(BigDecimal.ZERO) != 0)',
    'fun quotaProblem(raw: String): String?',
    'get("$XKIRO_BASE/models", false)',
    'get("$XKIRO_BASE/usage", true)',
    'throw IOException("xKiro free quota blocked ($quotaIssue); no inference sent")',
):
    assert required in guard, f"Missing free-only enforcement: {required}"

llm = read("core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt")
assert 'if (freeProvider == "legacy") {' in llm
assert "FreeProviderGuard.validate(freeProvider, baseUrl, model)" in llm
assert 'body.put("provider", JSONObject().put("max_price", JSONObject()' in llm
assert 'FreeProviderGuard.preflightXKiro(http, apiKey, model)' in llm
assert llm.index('FreeProviderGuard.preflightXKiro') < llm.index('val req = Request.Builder()')
assert '"$baseUrl/chat/completions"' in llm
assert 'if (tools.length() > 0 && freeProvider != FreeProviderGuard.GROQ)' in llm
assert 'GroqChatContext.compact(messages)' in llm

prefs = read("app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt")
for name in ('"free_provider"', '"free_model_xkiro"', '"free_model_groq"',
             '"free_model_openrouter"', '"free_groq_confirm"'):
    assert name in prefs
assert 'mode == BRAIN_LOCAL && freeProvider in setOf(' in prefs
assert 'p.getString("free_groq_confirm", "off") == "on"' in prefs
assert 'ProviderKeyStore.get(context, freeProvider)' in prefs
assert 'freeProvider = freeProvider' in prefs
assert 'if (mode == BRAIN_CODEFLYING)' in prefs

keys = read("app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt")
for req in ("AndroidKeyStore", "AES/GCM/NoPadding", "setKeySize(256)",
            "andee_free_provider_keys", 'Cipher.DECRYPT_MODE',
            "require(provider in supported)"):
    assert req in keys
assert "VaultBioGate" not in keys, "Regular inference must not add biometric approval"
assert "fun wipe(context: Context)" in keys
assert 'ProviderKeyStore.wipe(it)' in read("app/src/main/java/net/kuafuai/andee/config/FactoryReset.kt")

ui = read("app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt")
assert 'private fun freeProviderSection(' in ui
assert 'ProviderKeyStore.put(context, id, it)' in ui
assert 'pickBrain(VoiceConfig.BRAIN_LOCAL)' in ui
assert 'if (updates.isNotEmpty()) VoiceConfig.save(context, updates)' in ui

for testpath, token in (
    ("core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt", "exactFreeTierZeroPriceToolsRequired"),
    ("app/src/androidTest/java/net/kuafuai/andee/acceptance/FreeProviderKeysTest.kt", "keysEncryptedAndNeverInVoicePrefs"),
):
    assert token in read(testpath)

for path in ("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml"):
    assert 'name="settings_free_xkiro_safety"' in read(path)
    assert 'name="settings_free_groq_warning"' in read(path)

workflow = read(".github/workflows/build.yml")
assert workflow.count("python3 tools/check_free_provider_scope.py") == 2
assert ":app:connectedDebugAndroidTest" in workflow
print("PASS: strict free route allowlist, xKiro preflight, Keystore keys, original Andee agent/voice/tools preserved")
print("LIMIT: Groq Free account attestation is user-controlled; no live provider billing/model compatibility acceptance without user-owned keys")
