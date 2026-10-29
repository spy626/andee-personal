# Andee Phase 6 — Sensitive turn provenance / controlled functionality

**Status: narrow security changes only; draft and not main-phone ready.**
Base: Recovery Phase 5 success, 5 emulator smoke tests + 6 signed Hub adapter
tests. Original `main` remains frozen.

## What changed (and what didn't)

- Changes **only** to `TurnGuards.kt` and the remote transport's
  `BodyWsClient.kt` production code, plus tests/CI/docs.
- A notification-derived or remote Hub turn cannot directly call:
  `device.sms.send`, `device.dial`, `device.calendar.add`,
  `device.permissions.request`, `device.open_url`, `device.alarm`,
  `dog.move`, `dog.sequence`, `dog.turn`, `dog.speed`,
  `dog.pose` or `dog.calibrate`.
- Hub requests now pass the original `untrusted` security flag into the
  device dispatcher. The original existing vault, peer, plans and scene
  guards also apply to Hub, since a Hub signature proves the server but
  not live owner intent. This is a change to remote Hub capabilities.
- A **fresh local owner voice/touch turn** retains its original direct SMS,
  dial and other flows. `DeviceCommsController` and the original SMS
  button/permission logic are **not changed**.
- WhatsApp notification `ACT` / `TELL` / `IGNORE`, fallback card,
  `screen.tap` / `screen.type` / `screen.submit_input`, `ui.ask`
  and the blue character's 19 moods remain in the same code paths.
  We deliberately do **not** add second confirmation cards to ordinary
  WhatsApp/SMS conversations.

## Security contract and intentional remaining gaps

- Direct sensitive calls should fail before a remote task starts.
  They are also guarded inside `CommandDispatcher`; a client cannot
  bypass the check simply by changing the tool schema name.
- All normal user turns still pass the original guard; remote signed-Hub
  turns are treated as untrusted. A future authenticated *per-action owner*
  token may restore remote sensitive methods without risking direct Hub
  execution, but that is not built here.
- This does **not** stop indirect side effects by `screen.tap` or
  `screen.submit_input` on native apps or WebViews; a malicious notification
  may still attempt to click Send/Buy/etc. Extension tool methods are
  not yet classified. Actual WhatsApp card-edit/send UX and SMS manual
  review have not been physically accepted. Do **not** enable sensitive
  permissions on a personal primary phone based on green CI.

## Automated proof expected

- `:core:test`: untrusted direct-dangerous tools are denied; original
  user direct methods allowed; original WhatsApp screen/card tools allowed.
- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:assembleRelease`,
  original source-identity contract, new narrow ingress/dispatch static
  guard, original signed-Hub and Android emulator smoke tests.
- Adapter TLS mock tests unchanged; no real Hub or phone messages sent.

No production WhatsApp/SMS, voice, notification/triage, `CardUi`, renderer,
`FloatingWindowUi` or screen-controller source changes.
