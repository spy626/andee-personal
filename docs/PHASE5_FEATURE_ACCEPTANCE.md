# Andee Recovery Phase 5 — Feature Acceptance and Safety Status

**Status: emulator/source acceptance in progress; physical-device acceptance NOT DONE.**

Branch: `recovery/phase5-device-acceptance-gates` (draft/unmerged).
Original `main` and all original SMS, WhatsApp, notification, voice, card,
floating-window, screen-controller and 3D renderer runtime code stay intact.

## What automated tests can actually establish

| User-critical capability | Gate and evidence | Honest limitation |
|---|---|---|
| APK starts on Android without permissions | Android 11 isolated emulator first-run Self-check test | No physical phone / manufacturer-specific ROM |
| SMS existing workflow | Original source hash + dispatcher permission checks; on emulator SEND_SMS denied must return the original denial JSON | No real SMS send, no SIM, no preview/edit UI proven |
| WhatsApp notification support | Original package filtering, debounce, triage and fallback CardUi path checked against source and original baseline | No WhatsApp installed/logged in; no proactive reply end-to-end |
| Original answer cards | Android `CardUi.Result` physical-button answer contract + source checks for button/voice and exactly-once close | Doesn't simulate card rendering, voice click, or 'Let me edit' flow |
| Blue floating mascot | 19 `Mood` enum cases and face map verified on emulator; original renderer and window source hash unchanged | No OpenGL frame comparison, gesture or live animation acceptance |
| Voice and local AI | Original module code preserved + existing app/core JVM tests | No microphone/TTS/real AI provider invocation |
| Hub signed adapter | Original Phase 4 6 real local TLS/fake-Hub tests | No deployed genuine Hub or Android-to-Hub live test |

**CI green means only these checks passed, not that the full app is secure.**

## Isolated Android emulator safety conditions

- Android 11 (API 30), x86_64 emulator. No account, contacts, SIM or WhatsApp.
- Never grant `SEND_SMS`, `READ_SMS`, microphone, Notification Access,
  Accessibility, overlay or ADB privileged keyboard permissions.
- The SMS denial smoke test **fails before invoking** `sendSms` if the
  emulator already has SEND_SMS permission, so test automation cannot
  accidentally send the fixture text to a real number.
- Do not use the owner's physical daily-use phone to run this suite.
- Android Emulator test runs automatically as `android-emulator-smoke` in
  the PR. No production server is contacted; the Phase 4 fake-Hub tests are separate.

## Security blockers found by source review (not fixed by testing)

1. `DeviceCommsController.sendSms` checks Android SEND_SMS permission then
   sends immediately; the code does **not itself** enforce a final human
   review of recipient and contents. Its existing UX must be tested before
   altering it—do not assume confirmation just because a prompt asks.
2. Original `TurnGuards` blocks vault/peer/scene/plan changes on untrusted
   notification turns, but notification-originated `screen.tap` and other
   tools remain available, so prompt injection remains a meaningful risk.
3. A properly signed Hub can still send powerful device commands after
   authenticating; Hub trust proves identity, not user intent.
4. Local WebSocket consent is a phone button prompt, not cryptographic
   identity of the Android app initiating the connection. A hostile local
   app can attempt to trick the user into authorizing it.
5. Phase 4 secret-path bridge is for a self-hosted trusted Hub only and not a
   substitute for Android↔Hub physical acceptance.

These are open before any main-phone-ready tag. Prior Phase 1–4 recovery
PRs and this PR remain drafts; do not merge automatically.

## Future isolated physical-Android acceptance (only after security review)

Use a **spare test phone** with non-personal test accounts, simulated
contacts, controlled Wi-Fi and an explicit test SIM if needed. Test all
original behaviors without adding steps:

1. Install an ABI-matching debug APK. Verify first-run Self-check and that
   permissions remain off before the user grants them deliberately.
2. Confirm floating blue ball appears, can expand/collapse, and renders
   19 emotional/work states without clipping or crashes.
3. Test local AI text, voice ASR/TTS, silence/notification cards, and phone
   lock behavior.
4. With a dedicated test WhatsApp account, send a controlled message and
   verify original suggestion wording, **No reply / Let me edit / Send this**
   choice and correct destination.
5. With a dedicated test SMS contact/SIM, test the **existing** SMS flow,
   number and message accuracy, manual edit and actual send receipts. Do not
   redesign until actual behavior is observed.
6. Verify ADB-local extension connection approval, cancellation,
   reconnect/timeout and no pre-authorization tool execution.
7. Pair a trusted test Hub only after checking its signing key out of band.
   Confirm real Hub adapter tool registrations, bi-directional messages and
   wrong-key denial.
8. Provide a short sanitized screen recording (no phone numbers, keys,
   OTPs or private messages) for each relevant UX test.

**No actual physical-phone acceptance has happened in this slice.**
