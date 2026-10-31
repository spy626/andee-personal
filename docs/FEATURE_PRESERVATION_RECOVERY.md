# Andee personal fork — feature-preserving recovery contract

Status: **Recovery in progress; original behavior has NOT yet been verified on a real phone.**

## Safe recovery anchors

- Original Andee baseline (byte-for-byte upstream-matching fork `main` at recovery start): `03189e0c643fe2c2799fa96e3afb9b369e95c8ed`.
- Immutable reference branch (no additional commits intended): `recovery/original-andee-feature-baseline`.
- This working branch, `recovery/feature-parity-first`, begins from that original baseline rather than the stacked security patches.
- Security Phase 1, Phase 2, Phase 3 draft PRs are **not merged**. Preserve them for audit; do not force-push, delete or silently cherry-pick them into this branch.
- Baseline is not security-certified and is **not** an installation recommendation for a device with sensitive data.

## Non-negotiable features to preserve

- Original blue Andee floating animated mascot, moods/poses, wake/listen/think/speak/act motion, home-screen and third-party-app overlays.
- Proactive notifications, message triage, suggestion cards and **No reply / Let me edit / Send this** reply options shown in the owner's reference demo.
- WhatsApp reply workflow: prepare draft with verified recipient + full text, allow no reply/edit/send, send once **only** when the user approves that specific action.
- SMS has the **same natural workflow**: e.g., "Rahul ko 'Baad mein aata hun' SMS bhejo" -> resolve contacts (ask which Rahul if ambiguous) -> create/preview addressed SMS draft -> ask "Send karun?" -> send the approved message once, or edit/cancel. Avoid duplicate approval dialogs. The previous direct `SmsManager.sendTextMessage()` path must not bypass user review.
- Preserve original local AI, voice, accessibility/screen operation, notifications, note/memory, scenes, settings, cloud/local provider support, paired-peer features and any original supported peripherals. Keep feature inventory and review every modified code path.
- No new remote phone-control capability yet.

## Trust boundaries (without breaking features)

- Third-party notification or web/app text is always untrusted **content**, not owner instructions.
- Selecting the message to view or generating a draft must not grant general access to control the phone.
- Confirming **one exact recipient + exact full text + channel** authorizes only that pending message once; no replay, modified recipient/body, another message, or unrelated action.
- Preview/cancel/edit are never treated as permission to send.
- Voice confirmation may be part of user experience only when the owner-controlled session has an appropriate authorization boundary; voice matching alone is not identity verification. Where uncertain, require an owner gesture / OS authentication instead of sending.
- SMS/call/vault and other sensitive operations require risk-appropriate checks; avoid blanket rules that disable demonstrated workflows.
- Avoid reopening the unauthenticated legacy inbound network driver. Remote hub requires authenticated identity before it can request device actions.

## Acceptance gates — required before any merge

1. **Inventory** all original features and record before/after behavior for any changed path; inspect especially `NotificationRelayService`, `BodyCoreService.notificationLookRequested`, `LocalBrain`, `TurnGuards`, `CommandDispatcher`, `DeviceCommsController`, `CardUi` and the ball renderer.
2. **Automated tests**: no send without approval; exact SMS recipient/body preview; cancel and timeout never send; edit changes pending draft only; approval is one-shot and cannot be replayed; multiple matching contacts require disambiguation; guard against changed recipient between preview/send; injected notifications never auto-send; preserve user-triggered message workflows.
3. **Android physical test**: notification -> suggestions -> edit or send -> confirmed result in WhatsApp; analogous SMS recipient/draft/approval/send; no accidental duplicate send; overlays and animation remain active, including keyboard and lock-screen transitions.
4. **CI**: Debug + Release/R8 build, app/core tests, DCO and integration test gates. Compilation green is not enough to claim original feature parity.
5. **Risk changes**: no broad security-policy changes without proof that approved native workflows still work.

## Recovery engineering rule

**Preserve first, test second, harden third.** If a safety fix blocks an original user-visible behavior, redesign the permission transition rather than remove the feature. The old draft PRs are reference material, not the default base for this recovery.
