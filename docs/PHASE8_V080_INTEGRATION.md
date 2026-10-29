# Phase 8: Andee v0.8.0 + Recovery Phase 7 security integration

**Status: DRAFT, not approved for daily-use phone installation.**

## Exact commits and ancestry

- Original v0.8.0 upstream release: `df667e69a342d462ff0bb4593a73d84a9509d059`
- Existing fork's Phase 7 security: `5999c623c3f9fb24027a63d9b4deaf5021b9bd6e`
- Integration merge: `de01691be2b5e5bafb2d0b14461ef8cb07f929b8` (both as parents)
- Source method: upstream release's **entire tree**, overlaid with the reviewed recovery Phase 1-7 security changes; six overlapping Kotlin production files merged by individual validated patch hunks. No direct changes to `main` or Phase 7.

## Original behavior to preserve

The full v0.8.0 source includes its 13 tools (`self_note`, `cares`, `diary`, `person`, `people`, eight `wiki_*`), self record, inner-drive/heartbeat and work-turn rules, people, diary, wiki, notes, timeline, Android TTS, voice, floating ball, original 19 moods, SMS and WhatsApp notification triage/cards, phone and desktop source, QR UI and release resources. Everything outside the explicitly reviewed security-merge paths is compared byte-for-byte to the upstream release.

## Restored previous recovery boundaries

- Local WebSocket remains loopback-only, with explicit unlocked-phone Allow per live connection.
- Remote Hub still requires WSS and a pinned, signed fresh-challenge proof.
- Existing notification / remote Hub direct-sensitive-action gate remains.
- Unclassified remote/notification dynamic extension tools stay denied.
- Untrusted screen gestures remain scoped to live WhatsApp/WhatsApp Business/WeChat foreground.
- Original owner-local SMS, messaging, voice, mascot and extension dispatch are not intentionally deleted.

## Known remaining risks — no production safety claim

- Untrusted text could still induce Send/Call/Share/Approve taps within a messaging app.
- Foreground-package checking is subject to time-of-check/time-of-use races.
- Original local SMS send does not require a per-recipient and message-body approval at execution.
- The encrypted Vault key is not user-authentication/biometric gated.
- Local connection approval doesn't cryptographically identify the local client app; an approved connection can be powerful.
- Additional autonomous `v0.8.0` behaviors and new tool flows require separate threat analysis and live physical testing.
- Plain-file wiki articles are not intended for private credentials.
- The original `v0.8.0` upstream WebSocket listener is more permissive than this recovery fork. Never overwrite these guards with the unmodified upstream networking implementation.

## Required acceptance before release

Run Android Debug and Release/R8, host JVM tests, source-file/feature guards, signed-Hub fake-server tests, disposable Android emulator smoke, manual spare-phone SMS/WhatsApp draft/edit/send/deny, voice/TTS, mascot, local-connection/remote-Hub denial, Vault and prompt-injection negative tests. Only then consider further review of merge into a release branch.

**Do not install this unfinished integration on the primary phone with Accessibility, notification, SMS or saved credentials enabled.**
