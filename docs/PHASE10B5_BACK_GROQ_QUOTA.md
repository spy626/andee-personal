# Phase 10B.5 — Android Back, Groq Free diagnostics and xKiro free-token quota

Base: Phase 10B.4 `d645d00e815955e81c1e4d53d3a73b6d194a57c7` (Build #69 GREEN). No changes to `main`, no earlier branch rewrites, no paid API tests.

## User physical device findings, October 30, 2026
- Fullscreen Android Back still navigates out of underlying app instead of folding Andee into the existing floating mascot. The prior root focus + `OnBackInvokedCallback(PRIORITY_DEFAULT)` did not meet owner acceptance on their Android phone.
- Groq provider does not reply. No exact Groq HTTP code or screenshot has yet been supplied, so its actual live failure is **unconfirmed**. The Free-account setting defaults OFF and a missing key prevents requests; Groq GPT-OSS 20B and 120B are currently documented for local tool calling (https://console.groq.com/docs/tool-use/overview).
- xKiro Auto and manual Qwen became blocked on the *free quota* preflight on owner phone. Official xKiro usage API has `free_tokens.limit_per_day=null` AND `free_tokens.remaining=null` when no free-token cap applies; the old parser incorrectly rejected all null counters (https://docs.xkiro.com/api/usage/). A 12,000-token hardcoded minimum also incorrectly rejected positive free allowances smaller than 12,000.

## Minimal runtime patches, four files
1. `AndroidManifest.xml`: explicitly enable predictive-back callback. `FloatingWindowUi.kt`: on Android API 33+, register the already implemented fullscreen-only `OnBackInvokedCallback` with `PRIORITY_OVERLAY` rather than `PRIORITY_DEFAULT`. Retain the same original X `listeners.onMinimizeClick()` action, callback lifecycle, KEYCODE_BACK legacy fallback and compact/keyboard/screenshot passthrough.
2. `FreeProviderGuard.kt`: distinguish the fully documented, coherent unlimited free quota (used_today >= 0; `limit_per_day=null`, `remaining=null`) from incomplete, malformed, exhausted or inconsistent counters. Finite free quota must have a positive remaining count, valid nonnegative used and limit. Reject unknown/malformed. Separate catalog price checks still require `access_tier=free`, all price components zero, tools enabled, no DeepSeek, same xKiro host. Paid requests and model/provider fallback remain prohibited.
3. `LlmClient.kt`: Groq missing/free-account-unconfirmed key gets explicit Settings instructions and no request. Both Groq text-only GPT-OSS variants reject unsupported screenshots *before POST* rather than discarding the screenshot. Groq 400/401/403/404/429/5xx failures get safe categorized messages without echoing prompt, key or provider raw body. Free-account toggle remains OFF until user confirms it; no paid model, billing, automatic retry or cross-provider switch.

## Tests
- JVM offline xKiro `/usage` fixture: documented unlimited null/null, positive finite, exhausted finite, malformed and asymmetric null values. Retain exact free catalog + tools checks and full read-only GET mock tests.
- JVM Groq error descriptions prove HTTP 401 key failure, 429 rate limit and body/key secrecy.
- Strict narrow source-diff checker compares original manifest/back priority exactly and protects all other Android service, voice, screen tools, memory, Vault, SMS/WhatsApp and autonomous WORK. Independent existing Phase 9–10B.4 audits continue; full Android 11 emulator tests, Debug, R8 Release, Hub and DCO.

## Physical acceptance (after all CI green)
1. Test fullscreen Android Back **gesture**: should fold to mascot like X. Test keyboard Back: hides keyboard first; collapsed ball remains passive. Android overlay behaviors vary across ROMs: a green Android 11 emulator is not proof of Android 16 gesture success. Do not delete the X fallback until acceptance.
2. In Settings > AI & Voice > Groq Free, confirm Free-account toggle is ON only for the user's actual Free account, save a valid *user-owned free* API key, choose GPT-OSS 20B, Save, send a harmless text-only greeting. Record only the error text/HTTP code if it fails; **never record the key**. A paid Groq account cannot be certified as charge-free from the phone UI alone.
3. In xKiro Auto or Qwen Free, send one harmless message; verify the accepted quota is either fully documented unlimited or finite remaining > 0 and model price zero. Never switch to paid to bypass an exhausted free allowance.

## Later cleanup
Remove old settings/providers only after user-accepted stable xKiro, Groq, OpenRouter and Gemini Live integrations. No original CodeFlying, Huoshan, Android system TTS, mascot, autonomous tool or biometric Vault deletion in this patch.
