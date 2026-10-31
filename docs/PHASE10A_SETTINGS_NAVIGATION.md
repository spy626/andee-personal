# Andee Phase 10A — Clean Settings categories, zero provider behavior changes

Base: Phase 9.2 Settings crash fix (`c9dc4a1caa7a0d485084d1ab366be7337b9c1958`).
Branch: `agent/andee-phase10a-settings-navigation`. Draft/unmerged; main and earlier recovery slices unchanged.

## Scope and owner requirements
Phone test confirmed Settings crash fixed but API configuration is confusing. Phase 10A changes **only the presentation and navigation** of the original Settings screen, not actual provider routing, paid/free model selection, voice pipeline, Gemini Live, API endpoints, memory, autonomy, Hub, screen, SMS/WhatsApp or mascot.

Main Settings page is now five accessible, localized, tappable entries:
- AI & Voice — original backend tabs (CodeFlying / Local / Hub), original Gemini/Volcengine hearing & speaking and all original API settings
- Appearance & Wake — theme, original 19-mood character look link, wake controls
- Memory & Autonomy — notebook, notification triage, nudges, independent drive, max steps
- Vault & Tools — existing fingerprint-locked Vault Manage and installed-app Gear
- Advanced & Diagnostics — original Hub display, typing diagnostics and destructive reset confirmations

Main Settings header gets a back chevron on subpages; **colors, original top-bar settings button and mascot UI are unchanged**. No imaginary Gemini Live settings or fake provider model selector is shown in Phase 10A. Voice remains in the existing AI & Voice category until a separately tested Gemini Live integration can safely separate it from current Brain-dependent visibility rules.

## Preserve existing unsaved edits across categories
Original Settings rebuilt one long form and Save read only currently visible EditText + pickers. Switching categories now stages each page's edits in memory. Save merges staged fields/pickers for every visited category and calls original `VoiceConfig.save` once, with the same blank/override logic as before. Cancel discards staged data. Language/theme and other already-eager settings preserve their original immediate behavior.

The original `Vault.entries` must never be read when biometric-locked. Retain localized locked/unavailable summary. Existing Vault Manage and automatic tool autofill remain untouched.

## Regression checks
- Dedicated source checker requires exactly five categories, localized labels, staged field + picker preservation and original Settings methods.
- Updated emulator tests render each category under locked Vault and stage a dummy model input before navigating away and back, without saving or calling any external API.
- Previous Settings crash test now actually renders the moved Vault & Tools page under locked state.
- Compare Git blobs of all other production runtime modules to exact Phase 9.2 snapshot; run full original v0.8.0, Phase 7-9.2 security, Debug, Release/R8, signed-Hub adapter, DCO and Android emulator smoke.

## Still pending, not silently implemented
Next slices (after owner reviews new layout on phone) will add multiple providers: free-only xKiro model discovery including verified DeepSeek free variants, Groq free-compatible manual/Auto selection, OpenRouter, LLM7, Z.AI; **no direct paid DeepSeek API**. Gemini Live Voice will have separate credentials, voice/model picker and must preserve Andee voice/personality/tool ownership. No automatic paid inference or unconsented provider switching. A separate later UI slice will address colors and main top-bar design.

CI source checks are not physical-device layout or voice acceptance; new APK must be tested on a spare Android phone before merging.
