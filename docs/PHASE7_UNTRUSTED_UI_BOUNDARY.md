# Phase 7 — Untrusted screen and extension tool boundary

**Unmerged draft, not a main-phone security release.**

## Reviewed change set

- `TurnGuards.kt`: new pure policy for dynamic extension refusal, fixed messaging package resolution and live foreground screen-mutation scoping; prior Phase 6 direct sensitive denial retained.
- `CommandDispatcher.kt`: policy applied before UI/gesture side effects and before unknown extension fallback. Untrusted app launch resolves to official package IDs. Trusted owner calls follow original paths.
- `ScreenController.kt`: adds **read-only** current Accessibility root package accessor, not new tap/type implementations. No cached `lastDumpPkg` fallback, no model-supplied app ID.
- `BodyWsClient.kt`: signed Hub extension requests now denied before task state changes; the original Hub message formats are unchanged.
- `TurnGuardsTest.kt`, CI static gate and preservation audit verify allowed/denied methods.

## Intended behavior

| Operation | Owner's local turn | Untrusted notification or signed remote Hub |
|---|---|---|
| Original SMS/WhatsApp cards and local voice | Preserved | Original triage/card paths retained |
| SMS/call/calendar/robot direct action | Existing behavior | Denied by Phase 6 |
| Screen tap/type/submit/swipe | Existing behavior | Only if **live foreground** is official WhatsApp, WhatsApp Business or WeChat |
| Global navigation | Existing behavior | Back only while approved messaging app is foreground |
| Launch app by package/label | Existing behavior | Official messaging package only, never a spoofable label |
| Dynamic extension/market tool | Existing forwarding | Denied by default; unclassified capabilities unsafe |
| Read-only UI tree/screenshot | Existing behavior | Remains available for triage; separate privacy boundaries still apply |

This deliberately reduces notification and signed Hub abilities in nonmessaging
apps. It may require a direct owner turn for non-WhatsApp notification
automation. The card wording, original SMS/WhatsApp reply UI, protocol and
animation have not been modified.

## Critical incomplete risks

- Package verification is NOT semantic button verification. A message may
  attempt to get the assistant to click **Send**, **Call**, **Share**, or a
  purchase/approval control inside an allowed messaging app. This risk is not
  closed by Phase 7. UI foreground can change between check and action.
- User may explicitly authorize a local WS client once; an untrusted locally
  installed app may still request approval. Extension tools not all classified
  for trusted local owner calls.
- The original `DeviceCommsController.sendSms` itself requires Android
  permission but does not force last-moment recipient/body confirmation;
  local trusted voice SMS action therefore still needs physical acceptance.
- Original voice, 19-mood mascot rendering and actual WhatsApp draft/edit/send
  remain **real-phone acceptance items**, not consequences of JVM CI passing.

**No daily-phone APK approval or automatic merge of staged recovery PRs.**
