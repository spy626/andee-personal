# Andee Phase 10B.2 — xKiro live free-model catalog diagnostic hotfix

Base: Phase 10B.1 FULL-GREEN + phone-tested Settings `ae7b3a6e7502812b3d061b7f07fb0016764be82b`.
Owner real-phone video: Settings/provider selection worked, but xKiro text request showed `xKiro model price/tier/tool support unverified; no inference sent`. **No paid request was made**. The recording alone does not identify which model catalog check failed.

## Root issue and correction

Phase 10B.1 picked `qwen/qwen3-coder-plus:free` without verifying it is in the current official xKiro catalog. xKiro's published model page independently documents `qwen/qwen3-vl-plus:free` as a $0 chat model with tool calls and image input (see https://xkiro.com/models/qwen/qwen3-vl-plus%3Afree). Replace the guessed Qwen ID with that documented free ID. Keep `deepseek/deepseek-v4.1-flash:free` as the user's opt-in DeepSeek free option, because xKiro's official live offers still list that specific free ID (https://xkiro.com/deals); do not confuse it with **paid** `deepseek/deepseek-v4.1-flash` (https://xkiro.com/models/deepseek/deepseek-v4.1-flash). It is possible for free offer/model listings to change before a phone request, so live GET proof remains mandatory.

The old all-in-one error is replaced with **safe categorized rejection codes** (never raw JSON, key or response text): `model_not_listed`, `not_free_tier`, `tools_not_verified`, `pricing_missing`, `pricing_unverified`, `nonzero_price`, `invalid_catalog`, `unsupported_model`. Error tells owner to select a verified free model. This does not relax any existing price, authorization, quota or tool-use check. Free tier AND all explicit price fields == 0 AND tools==true AND free tokens>=12,000 still required.

## What stayed unchanged

The only production source modified is `FreeProviderGuard.kt`. Everything else — original Andee `BodyCoreService`, `LocalBrain`, `LlmClient`, voice, ASR/TTS, mascot, all device/screen tools, WhatsApp/SMS, WORK, memory, Settings, Vault and encryption — remains byte-exact to Phase 10B.1. No UI theme/cleanup and no new provider such as direct DeepSeek, LLM7, Z.AI, or Gemini Live. Free-only xKiro GET /models (public) and GET /usage (authorized) preflight run before any existing inference POST; no test makes a live paid request.

## CI and acceptance

Offline unit tests exercise documented Qwen catalog positive and unavailable DeepSeek catalog negative using in-memory OkHttp interceptor fake responses. They assert only GET /models and GET /usage, rejecting a missing model before usage and never sending POST. Existing xKiro paid-price/free-tier/tools/quota negative fixtures, strong biometric Vault, exact Andee feature-preservation, Android Emulator, Release/R8, Debug APK and signed Hub remain enabled. No actual xKiro key is shipped or requested in CI.

**Owner phone**: Install Phase 10B.2 only after all CI green. In Settings -> AI & Voice -> xKiro choose Qwen Free, tap Save, test a harmless "hi" (no sensitive screen/control data) with a personal free xKiro key already securely added. If that passes, optionally try DeepSeek Free; if `model_not_listed` or any nonzero price, do not use a paid model, keep Qwen. Any failure must be reported with redacted screen recording (no real key). Do not mislabel green CI as verified live pricing/quota or tool calling; those require phone acceptance. CodeFlying missing-key and AudioRecord state are not part of this provider hotfix.

## Later cleanup

After all providers and Gemini Live are stable in independent phone tests, review unused settings/providers with dependency scans and user approval. Do not remove original providers prematurely or silently alter autonomous tool behavior.
