#!/usr/bin/env python3
"""Phase 10B.5: four-file runtime hotfix, no change to original Andee autonomy.

Back registration priority + Android opt-in; Groq safe readiness/errors;
xKiro documented unlimited/finite *free* quota with zero-cost model gate intact.
"""
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
BASE="d645d00e815955e81c1e4d53d3a73b6d194a57c7"
def git(*args):
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True).strip()
def read(path):
    return (ROOT/path).read_text(encoding="utf-8")
def orig(path):
    return git("show",f"{BASE}:{path}")
def same_except(path, pairs):
    changed=read(path)
    previous=orig(path)
    for before,after in pairs:
        assert changed.count(after)==1, f"Expected changed expression missing: {path}"
        changed=changed.replace(after,before)
    assert changed.strip()==previous.strip(), f"Unexpected production delta: {path}"

paths={
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt",
    "core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt",
    "tools/check_phase10b5.py",
    "docs/PHASE10B5_BACK_GROQ_QUOTA.md",
    ".github/workflows/build.yml",
    "tools/verify_feature_preservation.py",
    "tools/check_vault_biometric_only.py",
    "tools/check_vault_screen_lock.py",
    "tools/check_vault_settings_safe.py",
    "tools/check_settings_navigation.py",
    "tools/check_free_provider_scope.py",
    "tools/check_xkiro_catalog_fix.py",
    "tools/check_xkiro_auto_scope.py",
    "tools/check_android_back_scope.py",
}
changed=set(git("diff","--name-only",BASE,"HEAD").splitlines())
assert changed<=paths, f"Unreviewed change: {sorted(changed-paths)}"
assert not git("diff","--diff-filter=D","--name-only",BASE,"HEAD"), "Deleted source"
for p in (
    "app/src/main/java/net/kuafuai/andee/BodyCoreService.kt",
    "app/src/main/java/net/kuafuai/andee/config/VoiceConfig.kt",
    "app/src/main/java/net/kuafuai/andee/config/ProviderKeyStore.kt",
    "app/src/main/java/net/kuafuai/andee/config/Vault.kt",
    "app/src/main/java/net/kuafuai/andee/config/VaultBioGate.kt",
    "app/src/main/java/net/kuafuai/andee/ui/SettingsUi.kt",
    "app/src/main/java/net/kuafuai/andee/ui/TextInputActivity.kt",
    "app/src/main/java/net/kuafuai/andee/net/CommandDispatcher.kt",
    "app/src/main/java/net/kuafuai/andee/device/DeviceCommsController.kt",
    "app/src/main/java/net/kuafuai/andee/device/NotificationRelayService.kt",
    "app/src/main/java/net/kuafuai/andee/screen/ScreenController.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/LocalBrain.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/SelfWork.kt",
    "core/src/main/kotlin/net/kuafuai/andee/brain/Heartbeat.kt",
    "core/src/main/kotlin/net/kuafuai/andee/config/BrainConfig.kt",
    "core/src/main/kotlin/net/kuafuai/andee/asr/GoogleAsr.kt",
    "core/src/main/kotlin/net/kuafuai/andee/tts/GoogleTts.kt",
):
    assert git("rev-parse",f"{BASE}:{p}")==git("rev-parse",f"HEAD:{p}"), f"Original agent changed: {p}"

same_except("app/src/main/AndroidManifest.xml", [
    ('        android:networkSecurityConfig="@xml/network_security_config"\n',
     '        android:networkSecurityConfig="@xml/network_security_config"\n'
     '        android:enableOnBackInvokedCallback="true"\n'),
])
same_except("app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt", [
    ("android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback,",
     "android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback,"),
])
f=read("app/src/main/java/net/kuafuai/andee/ui/FloatingWindowUi.kt")
for marker in ("listeners.onMinimizeClick()", "setTypingBackPassthrough(enabled: Boolean)",
               "FullscreenBackPolicy.claimsBack(", "unregisterApi33Back()"):
    assert marker in f, f"Back/IME behavior lost: {marker}"

llm=read("core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt")
for token in (
    'if (freeProvider == FreeProviderGuard.GROQ && needsVision)',
    'if (freeProvider == FreeProviderGuard.GROQ)',
    'FreeProviderGuard.groqErrorHint(resp.code)',
    'FreeProviderGuard.preflightXKiroAuto(http, apiKey, needsVision)',
    'FreeProviderGuard.preflightXKiro(http, apiKey, model)',
    'FreeProviderGuard.validate(freeProvider, baseUrl, model)',
):
    assert token in llm, f"Missing fail-closed inference gate: {token}"
assert llm.index('if (apiKey.isBlank())') < llm.index('val req = Request.Builder()')
assert llm.index('if (freeProvider == FreeProviderGuard.GROQ && needsVision)') < llm.index('val req = Request.Builder()')
assert 'body.put("tools", tools).put("tool_choice", "auto")' in llm
assert '"$baseUrl/chat/completions"' in llm
# Precisely three reviewed changes in the existing client: missing key,
# reject image for text-only Groq, sanitize Groq HTTP without raw body.
llm_diff=git("diff","--unified=0",BASE,"HEAD","--",
             "core/src/main/kotlin/net/kuafuai/andee/brain/LlmClient.kt")
assert llm_diff.count("\n@@ ")==3, "Unreviewed Groq LLM behavior patch"
assert "onMinimizeClick" not in llm_diff

guard=read("core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt")
old_guard=orig("core/src/main/kotlin/net/kuafuai/andee/brain/FreeProviderGuard.kt")
def region(s, start, end):
    return s[s.index(start):s.index(end)]
# The actual exact zero-cost+tools model parser and free-host/model list are
# BYTE IDENTICAL to the last phone-tested Phase 10B.4 snapshot.
# Preserve the free/zero-price/tools parser and whole Auto eligibility
# unchanged; only the /usage preflights + new quota parser may differ.
for start,end in (
    ("    fun models(provider:", "    fun endpoint(provider:"),
    ("    fun catalogProblem(", "    fun preflightXKiroAuto("),
):
    assert region(old_guard,start,end)==region(guard,start,end), (
        f"Free-model catalog/tool/price or model whitelist changed: {start}"
    )
for token in (
    'if (item.optString("access_tier") != "free")',
    'if (amount.compareTo(BigDecimal.ZERO) != 0)',
    'item.optJSONObject("capabilities")?.optBoolean("tools") != true',
    'obj.isNull("limit_per_day")',
    'obj.isNull("remaining")',
    'if (remaining == 0L) return "free_quota_exhausted"',
    'fun quotaIsVerifiedFree(raw: String): Boolean = quotaProblem(raw) == null',
    'val quotaIssue = quotaProblem(get("$XKIRO_BASE/usage", true))',
    'fun groqErrorHint(status: Int): String = when (status)',
):
    assert token in guard, f"Missing free-safety check: {token}"
assert ".post(" not in guard
unit=read("core/src/test/kotlin/net/kuafuai/andee/brain/FreeProviderGuardTest.kt")
for n in ("quotaUsesDocumentedUnlimitedOrPositiveFreeAllowanceOnly",
          "groqErrorHintsAreSafeAndActionable",
          "autoUsesReadOnlyChecksAndOnlyDefiniteRejectionAllowsRetry"):
    assert n in unit

workflow=read(".github/workflows/build.yml")
assert workflow.count("python3 tools/check_phase10b5.py")==2
assert ":app:connectedDebugAndroidTest" in workflow
print("PASS: Phase10B.5 xKiro free quota and Groq diagnostics; Android Back overlay priority; original Andee autonomy unchanged")
print("LIMIT: real Groq account+key, native Android 16 Back and xKiro allowance still require owner phone acceptance")
