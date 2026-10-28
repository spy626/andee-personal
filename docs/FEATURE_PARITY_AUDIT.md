# Andee feature-preservation deep audit (source level)

**Baseline:** `03189e0c643fe2c2799fa96e3afb9b369e95c8ed`, preserved as `recovery/original-andee-feature-baseline`.

**Audit branch:** `audit/full-feature-preservation-ci`, based on `recovery/feature-parity-first`.

## Evidence and pass criteria

- The recovery branch is only one documentation commit ahead of the baseline (original `app/`, `core/`, `desktop/`, assets and Android manifest are unchanged).
- The audit branch uses a whole-repository Git **blob SHA identity gate** against the original baseline, rejecting deleted files, altered source files or unexpected additions. Only audit docs, CI and AGP/Gradle compatibility files may differ. A successful guard is strong evidence for **source preservation**, not runtime behavior.
- Debug APK build, existing Android and core JVM tests, and Release/R8 build run in GitHub Actions; do not report these as passed until the latest audit-branch CI actually passes.
- AGP `8.7.2` + Gradle `8.9` compatibility upgrades are build-tool-only; the original AGP `8.3.1` release R8 incompatibility was reproduced earlier. This audit does not adopt the other security patches.

## Detailed source inventory

| Original capability | Verified implementation entry points | Audit status |
| --- | --- | --- |
| Blue floating mascot / animations | `EmotionBallTextureView`, `EmotionBallRenderer`, `Mood`, `FloatingWindowUi` | Source intact; real phone visual parity pending |
| Wake/listen/think/speak, voice answers | `BodyCoreService`, `LocalBrain`, `CardUi.tryAnswerByVoice` | Source intact; microphone/ASR/TTS acceptance pending |
| Notification listener & triage | `NotificationRelayService`, `NotificationTriage`, `BodyCoreService.notificationLookRequested` | Source intact; notification tests pending |
| WhatsApp notification intake | `com.whatsapp` in notification scope; generic screen tools | Source intact; exact three-choice reply + actual send unverified |
| Dynamic user-choice card | `CardUi.ask`, `CardUi.bubble`, `ui.ask` | Source intact; specific video button wording not guaranteed by static code |
| SMS permission / send | `DeviceCommsController.sendSms`, `SmsManager.sendTextMessage` | Source intact; **no guaranteed preview/edit/one-shot approved-send flow** |
| Contact resolution | `device.contacts` dispatch, contacts controller | Source intact; duplicate Rahul disambiguation pending |
| Phone calls | `DeviceCommsController.dial`, `ACTION_DIAL`, `ACTION_CALL` | Source intact; phone acceptance pending |
| Accessibility tap, typing, screenshots | `ScreenController`, `CommandDispatcher` `screen.*` methods | Source intact; device app-to-app acceptance pending |
| Vault/credentials | `Vault`, `device.vault.*` | Source intact; security not approved |
| Assistant memory/notes, scenes | `LocalBrain` / note/scene dispatch | Source unchanged; behavior pending |
| Paired devices, peer tasks | `PeerTasks`, `PeerLink`, `AndroidPeers` | Source intact; no new remote-control feature |
| Sensors/camera/peripheral support | Device controllers, `LookActivity`, dog motion/USB | Source intact; hardware tests pending |
| Desktop/macOS body | Original `desktop/` tree | Source intact; desktop build/device tests pending |

## Confirmed gaps (do not call them regressions of recovery source)

1. **SMS:** baseline `device.sms.send` calls `sendSms` / `sendTextMessage` directly. A fixed, full-body, editable recipient draft, confirmation and one-shot send **are not enforced by the baseline code**. The owner's requested UX is a new behavior contract, not a proven existing SMS feature.
2. **WhatsApp:** the baseline has notification triage, cards and generic Accessibility actions, but those APIs alone cannot guarantee the *No reply / Let me edit / Send this* flow seen in the owner-supplied demo. A real action-level test is still required.
3. **Security drafts:** Phase 2 default-deny `screen.*` on `NOTIFY` could block proactive WhatsApp replies. Phase 3 introduces independent mandatory button approvals that could conflict with the requested seamless confirmation. Neither draft was merged.
4. **Safety:** untouched original code includes unauthenticated legacy network control and direct sensitive actions; preserving it does not make it safe to install on a personal phone.

## Next behavioral acceptance required

- Test WhatsApp real incoming notification -> suggestion -> Cancel / Edit / Send, **exact recipient + body only**, overlay and keyboard focus, no duplicate send.
- Test SMS: contact search and multiple matching names, visible full draft, edit before send, voice-assisted but correctly authorized confirm, one-shot send and no send after cancel/timeout.
- Test device lock/unlock, permission revocation, session changes and interruption halfway through a draft.
- Test remote-hub identity/authentication and injection resistance without disabling the working interaction; no new phone-to-phone control until explicitly requested.

**No source-preservation claim should be promoted to end-to-end feature-parity success without these Android device tests.**

## Feature-preserving network transport patch — separate draft

Changed runtime source is limited to:
- `BodyCoreService.kt`: explicitly bind original local WebSocket to 127.0.0.1, refuse invalid/plaintext remote Hub URLs.
- `BodyWsServer.kt`: loopback-only default and extra reject-on-open/frame protection.
- `BodyWsClient.kt`: WSS preflight before connecting.
- New shared `NetworkPolicy` and JVM + CI guards.

**Preservation:** the `LocalBrain`, `NotificationRelayService`, `NotificationTriage`, `DeviceCommsController`, `CommandDispatcher`, `CardUi`, `FloatingWindowUi`, animation renderer/moods, and all existing SMS/WhatsApp/voice/screen/peer code remain byte-for-byte unchanged. Legacy localhost/ADB-forward/call-extension socket keeps starting; LAN socket access is deliberately removed.

**Explicit limitations:** other Android apps may still reach the unauthenticated local port; WSS hubs are TLS-protected but no app-layer hub pairing is implemented. Notification prompt injection and irreversible action consent have not been fixed. Do not install this branch on a personal primary phone or consider it fully secure until these risks and physical acceptance are addressed. Exact SMS/WhatsApp draft/send experience is still a real-device acceptance requirement.


## Phase 2 local per-socket consent (draft)

`BodyWsServer` now queues a bounded set of initial frames (no dispatch / no
event broadcast), and only enables a specific live socket after phone-side
owner approval. This design keeps the original ADB and call-extension wire
formats and passive viewer registration; pre-consent requests never execute.
On socket close, authorization is revoked. Another local app can still request
consent, so this is owner authorization **not cryptographic peer identity**.
New `LocalPeerConsent` JVM tests reject voice/timeout/wrong label,
locked device or hidden UI. CI checks that legacy local transport remains
available but pre-consent requests and event broadcasts are gated.

The new connection-level card may appear when using local debugging or call
extensions. It does not replace or insert a second confirmation into any SMS,
WhatsApp or normal assistant workflow. Verified tests are not a substitute for
physical Android pairing, passive-viewer, extension and original message-reply
acceptance. Recovery branch remains **not safe for a main phone**.
