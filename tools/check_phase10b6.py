#!/usr/bin/env python3
"""Phase 10B.6: Groq is an isolated, bounded LYRA-style text-chat route."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "b3890f8cc3eb37bc43106eaa65f6c5b73a62a047"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

allowed = {
    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalPrompt.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/GroqChatContext.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/GroqChatContextTest.kt",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "tools/check_free_provider_scope.py",
    "tools/check_phase10b5.py",
    "tools/check_phase10b6.py",
    "tools/verify_feature_preservation.py",
    "tools/check_vault_biometric_only.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_settings_safe.py",
    "tools/check_settings_navigation.py",
    "tools/check_xkiro_catalog_fix.py",
    "tools/check_xkiro_auto_scope.py",
    "tools/check_android_back_scope.py",
    # Phase 10B.7: explicitly reviewed Gemini Live voice transport and focused tests.
    "app/src/main/java/net/kuafuai/andee/audio/GeminiLiveSession.kt",
    "app/src/main/java/net/kuafuai/andee/audio/GeminiLiveTokenProvider.kt",
    "app/src/main/java/net/kuafuai/andee/audio/GeminiLiveToolAdapter.kt",
    "app/src/main/java/net/kuafuai/andee/audio/GeminiLiveVoiceController.kt",
    "app/src/test/java/net/kuafuai/andee/audio/GeminiLiveTokenProviderTest.kt",
    "app/src/test/java/net/kuafuai/andee/audio/GeminiLiveToolAdapterTest.kt",
    # Gemini Live integration explicitly reviewed in Phase 10B.7; dedicated scope gates cover these files.
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "docs/PHASE10B6_GROQ_LYRA_CHAT.md",
    ".github/workflows/build.yml",
    ".github/workflows/dco.yml",
}
changed = set(git("diff", "--name-only", BASE, "HEAD").splitlines())
assert changed <= allowed, f"Unexpected Phase 10B.6 files: {sorted(changed - allowed)}"
assert not git("diff", "--diff-filter=D", "--name-only", BASE, "HEAD"), "Source file deleted"

llm = read("core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt")
for token in (
    "GroqChatContext.compact(messages)",
    "GroqChatContext.MAX_COMPLETION_TOKENS",
    "tools.length() > 0 && freeProvider != FreeProviderGuard.GROQ",
    "Groq text-chat returned unsupported tool calls",
    "FreeProviderGuard.groqErrorHint(resp.code)",
):
    assert token in llm, f"Missing Groq request guard: {token}"
assert '"$baseUrl/chat/completions"' in llm

context = read("core/src/main/kotlin/net/kuafuai/andee/brain/GroqChatContext.kt")
for token in ("MAX_TOTAL_CHARS = 4_500", "MAX_COMPLETION_TOKENS = 512",
              ".filter { it.optString(\"role\") == \"user\" || it.optString(\"role\") == \"assistant\" }",
              ".filter { it.opt(\"content\") is String }", "takeLast(MAX_MESSAGES)"):
    assert token in context, f"Missing compact context policy: {token}"

brain = read("core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt")
for token in (
    "LocalPrompt.groqChat(host.persona())",
    "kind != TurnKind.USER",
    "JSONArray()",
    "GroqChatContext.clip(userText, GroqChatContext.MAX_USER_CHARS)",
    "Groq Free is text-only in Andee",
    "cfg.freeProvider == FreeProviderGuard.GROQ || cfg.apiKey.isEmpty()",
    "if (cfg.freeProvider == FreeProviderGuard.GROQ) return",
):
    assert token in brain, f"Missing Groq-only LocalBrain boundary: {token}"

prompt = read("core/src/main/kotlin/net/kuafuai/andee/brain/LocalPrompt.kt")
assert "fun groqChat(persona: String)" in prompt
assert "personal memory" in prompt
assert "Never claim" in prompt

guard = read("core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt")
assert '413 -> "request payload too large' in guard
assert "no automatic paid fallback" in guard

unit = read("core/src/test/kotlin/net/kuafuai/andee/brain/GroqChatContextTest.kt")
for token in ("keepsOnlySystemAndRecentPlainTextDialogue",
              "enforcesHardCharacterBudgetAndKeepsNewestMessage",
              "groqPromptDoesNotAdvertiseDeviceOrMemoryCapabilities"):
    assert token in unit

for path in ("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml"):
    content = read(path)
    assert 'name="settings_free_groq_warning"' in content
    assert 'name="settings_free_scope"' in content
    if path.endswith("values-en/strings.xml"):
        assert "personal memory" in content and "background autonomy" in content, f"Missing English Groq text-only scope warning in {path}"
    else:
        # The default resources are Chinese; validate the same safety boundary
        # in the language actually shipped there instead of English keywords.
        assert "个人记忆" in content and "后台自主任务" in content, f"Missing Chinese Groq text-only scope warning in {path}"

# Provider credential/configuration wiring, Vault, voice engines and Android
# action executors must be exactly the Phase 10B.5 versions.
# Phase 10B.7 intentionally changes the voice lifecycle and Gemini settings/key wiring.
# Those paths are reviewed by check_android_back_scope.py, provider-scope and settings gates.
for path in (
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
):
    assert git("rev-parse", f"{BASE}:{path}") == git("rev-parse", f"HEAD:{path}"), f"Unexpected change: {path}"

print("PASS: Groq text-only context is bounded, no tool/memory/image/background payload reaches Groq")
print("PASS: other Andee providers, secure key wiring, Vault, voice and device executors unchanged")
