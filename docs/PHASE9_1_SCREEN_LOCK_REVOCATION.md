# Phase 9.1: Vault screen-lock revocation only

Status: Draft, stacked on the fully CI-green Phase 9 biometric-only branch. Main and Phase 9 remain unchanged.

## No feature redesign
Preserve original Andee v0.8.0 and Phase 1-9 behavior. Do not add approval prompts to WhatsApp, SMS, screen gestures, autonomous work, voice, notifications or mascot. The ONLY production changes in this slice are VaultBioGate.kt and VaultUnlockActivity.kt.

## Security correction
Once the credential Vault is first opened, register an app-process Android ACTION_SCREEN_OFF receiver with RECEIVER_NOT_EXPORTED on API 33+, or the equivalent runtime registration on older Android. Any screen-off event immediately revokes the nine-minute Vault session and cancels its pending biometric waiters. Unlocking the phone does not restore this session: a fresh strong biometric is needed to use Vault again. A refused runtime receiver registration blocks the Vault rather than allowing an unobserved session.

Each biometric Activity is handed a unique local prompt ID. Completion is accepted only if the same ID still owns the current live request, the phone is unlocked, and screen-off observation remains registered. A delayed success from before the phone lock cannot reopen the Vault. Original secret-list, get and autofill behavior after a valid Vault unlock is unchanged.

## Tests
The source-only Phase 9.1 gate compares relevant runtime source to the Phase 9 commit and rejects any source edits outside the narrow Vault scope. The Android emulator has three regression methods: a synthetic unlocked session is revoked by invoking the actual screen-off receiver; stale success after screen-off cannot unlock; incorrect request IDs cannot grant a session. The emulator does not send SMS or WhatsApp messages, enroll a real fingerprint or use saved credentials.

## Physical acceptance not yet proven
On an isolated spare Android phone, test the real power button/screen off, unlock again, real strong fingerprint prompt, repeated use within nine minutes, cancelling biometric, autofill field focus, original stored Vault migration, process restart, background Activity launch restrictions, and possible manufacturer-specific lock behavior. Test with disposable credentials only. Never mistake green CI for hardware biometric or data migration proof.

No changes to original normal screen automation, SMS/WhatsApp flows, v0.8.0 background work, model prompts or network permissions.