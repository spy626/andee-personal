# Phase 10B.4 — Android system Back dismisses Andee fullscreen chat

**User evidence:** 85-second recorded phone test and marked screenshot (30 October 2026). Andee xKiro Auto chat replied naturally and mascot reacted, but the user had to tap the top-right X to return to the underlying Android screen. The marked X is **minimize to floating mascot**, not a full service exit. No new xKiro failure was visible in the demonstrated short chat.

## Why Back missed the overlay
`FloatingWindowUi` creates a `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` in both full and compact shapes. Android system Back was therefore delivered to whatever activity was underneath, while Andee's fullscreen card remained visible. The X uses `listeners.onMinimizeClick()` -> original `dispatcher.compactForUser()`.

## Narrow fix
- Only a **visible, fullscreen, touchable** Andee card may claim Android Back focus. Remove `FLAG_NOT_FOCUSABLE` in that state, keep `FLAG_LAYOUT_IN_SCREEN` and other original geometry/blur behavior.
- Android 11/12 or OEM hardware Back: existing overlay root receives `KEYCODE_BACK`. Android 13+ predictive/gesture Back: use the overlay's attached `OnBackInvokedDispatcher` and register `OnBackInvokedCallback`. Both call the **same existing X/minimize listener**; no second service, no `stopSelf()`, no deletion of messages, no microphone stop.
- Compact assistant, screen taps/gesture passthrough, captures, picker/away: always `FLAG_NOT_FOCUSABLE`, no registered callback. **Before** `TextInputActivity` starts, temporarily hand focus to it so Android Back first hides the keyboard or finishes editing. Re-enable fullscreen Back when the activity closes. Do not change `TextInputActivity`, IME settings or user text.
- Unregister callback when card folds or overlay is removed to prevent stale callbacks/memory leaks; re-register on next full expansion.

## Preservation and tests
- Original code changes limited to `FloatingWindowUi.kt` focus/back interception, plus two short keyboard-focus handoffs in `BodyCoreService.kt`; remaining source modules byte-exact to Phase 10B.3.
- Independent security checker compares BodyCoreService against exact expected tiny patch and verifies FloatingWindowUi changes restricted to imports/root/back/show/hide/applyWindow. Existing xKiro free-only, Groq/OpenRouter, Vault biometric, messaging, screen tools, autonomous WORK, mascot, Release/R8 and signed Hub audits retained.
- Android emulator tests pure focus-ownership rules: fullscreen claims, folded ball/keyboard/gesture/capture do not. This cannot prove Android 16 gesture handling on the owner's ROM.

## Owner phone acceptance
Install corrected build only after every GitHub CI job is green. On a spare/test device with existing API key securely saved:
1. Full chat visible -> Android Back gesture from left/right edge -> full chat folds to floating mascot, just like marked X.
2. Long-press floating mascot -> full chat restored with chat history intact.
3. Open typing keyboard -> one Android Back closes keyboard without folding chat; editor works and sending a message still works.
4. With only folded mascot, Back should navigate **underlying app**, not kill Andee. Optional tool/gesture and Settings sanity smoke.
If Android gesture still fails on ROM, report recording and Android version; do **not** claim hardware pass just from green CI.

Unwanted legacy providers/settings and colors are deferred until original Andee features plus Gemini Live are separately stable and physically accepted.
