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

## Phase 3 — pinned signed identity for the remote Hub (draft)

- Existing remote Hub URL remains WSS-only; adds a user-editable **public**
  P-256 SPKI identity key in the existing cloud backend tab. Clearing the
  key revokes future Hub connections. On backend setting changes the Hub
  client is rebuilt with the new pin.
- An outbound Hub socket starts unauthenticated. It sends only a fresh random
  nonce and device ID in `auth.challenge`. Until a valid signed `auth.proof`
  arrives, no tool schema registration, events, or commands. Invalid or missing
  signing identity / replay / wrong device ID / failed signature causes refusal;
  no compatibility bypass to the old insecure Hub protocol.
- Existing local AI, SMS controller, WhatsApp notification/triage, CardUi,
  LocalBrain, voice control, floating window and mascot rendering stay untouched.
- External Hub server adapter is **not in this repository**; legacy remote
  Hub workflows require that adapter and live integration acceptance before
  calling feature parity complete. Session identity does not prevent malicious
  instructions from a compromised but authenticated Hub.

## Phase 5 acceptance gates — emulator and source, not personal phone

New isolated test-only `app/src/androidTest/.../AndeeSafeSmokeTest.java`
checks the first-run Self-check activity, original system-bound notification
listener/Accessibility service permissions, the original SMS **permission denied**
path (explicitly refuses to run on a device with SEND_SMS enabled), CardUi's
answer result, and all 19 mascot moods backed by faces. No real SMS or
WhatsApp message is sent, no API keys are configured and no sensitive access
is granted. An Android 11 x86_64 Google APIs emulator runs it in CI;
`:app:connectedDebugAndroidTest` failures are not bypassed.

`tools/check_original_feature_contracts.py` asserts original WhatsApp
notification triage/card action, original SMS tool/permission check, and
voice/mascot source entry points still exist. It is explicitly a source
contract, not an end-to-end replay of a WhatsApp conversation.

See `docs/PHASE5_FEATURE_ACCEPTANCE.md` for exact evidence, open risks and
the future spare-phone acceptance plan. This does NOT certify use on the
owner's primary Android device or close the prior sensitive-action risk.

## Phase 6 — direct sensitive-action provenance gate (unmerged draft)

- `TurnGuards` now mechanically refuses direct `device.sms.send`,
  `device.dial`, `device.calendar.add`, permission escalation request,
  external URL launch, alarm writes and physical robot commands during a
  `TurnKind.NOTIFY` or other `untrusted` turn.
- `BodyWsClient` treats every *authenticated remote Hub* command as
  **untrusted source** too. The same restrictions apply before the Hub task
  begins and again at the final dispatcher boundary. Hub signature proves
  the server's identity, not user intent.
- Original local owner-initiated `device.sms.send` remains exactly as before.
  WhatsApp notification triage, original `CardUi`, `screen.tap`,
  `screen.type`, `screen.submit_input` and `ui.ask` remain available on
  notification turns for original proactive draft/reply behavior.
- Security trade-off: optional remote Hub can no longer directly send SMS,
  dial calls, modify calendar or control the robot even when signed. The
  human must issue these through a fresh trusted device-local turn.
  Original remote Hub wire messages and free adapter are unchanged.
- **Known major limitation:** screen gestures can still click a real
  in-app Send/Buy/Approve button, and extension-provided tools are not
  classified here. Neither origin guard nor Hub identity alone prevents
  indirect malicious UI automation. The Android accessibility actions,
  SMS/WhatsApp live send behavior and owner-confirmation UX remain
  unverified on a physical spare phone. Do not tag as main-phone-safe.

## Phase 7 — untrusted screen and dynamic tool boundary (draft)

- Only `TurnGuards`, `CommandDispatcher`, `ScreenController` (one read-only live active-root package accessor) and `BodyWsClient` have changed in production compared with Phase 6. **No edits** in `DeviceCommsController`, WhatsApp notification handling/triage, `CardUi`, local AI, voice, original UI renderer, moods, or actual screen-action methods.
- Notification and remote Hub gestures `screen.tap`, tap target/id, swipe, long press, type and submit are refused unless the **current live Accessibility root** shows official WhatsApp, WhatsApp Business or WeChat package. Old tree snapshots are not used for the decision; absent/unknown/root-of-our-own-overlay denies. Untrusted global action only allows **Back** while in a known messaging foreground. Read-only UI tree/screenshots remain available for notification triage.
- Untrusted `device.launch` uses a fixed real package ID even if the brain supplies a label, and denies launching settings/banking/unknown apps. Local trusted owner turns still launch everything exactly as before.
- Dynamic `forwardExternal` extension/market methods denied for any untrusted origin. Signed remote Hub extension tools are rejected **before** task start. Trusted local-owner use and existing registration/wire protocol remain unchanged, but the prior remote Hub and notification extension automation capabilities are intentionally restricted.
- **Not complete safety:** WhatsApp/WeChat still expose Send, Call, attachments and other irreversible UI controls. A malicious message or compromised Hub may manipulate allowed messaging buttons; screen foreground can also change between check and action. Neither simulated emulator nor source tests prove reply/editor correctness. Do not enable sensitive permissions on the main phone.
