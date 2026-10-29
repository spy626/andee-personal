# Phase 9: biometric protection ONLY for credential Vault

Draft / unmerged. Base: Phase 8 commit 33049233a157372db6eefc2864688815ebf776f7.

## Product behavior preserved
The owner explicitly does not want blanket approvals or manual taps. Original upstream v0.8.0 and Phase 7 behavior remain for SMS, WhatsApp, normal screen automation, voice, 19 moods, heartbeat, autonomous WORK and remote Hub. No new approval checkpoints are added to these activities.

Only the credential Vault needs strong Android biometric unlocking. One successful strong biometric grants a short nine-minute access session, while the Android Keystore key requires an authentication window of ten minutes. The original Vault list/get/fill tools work normally once the biometric session is active. The prompt is not repeated for every ordinary AI action.

## Lossless migration
New biometric-bound AES/GCM Keystore key is separate from the legacy key. Old ciphertext remains until a new encrypted copy is decrypted, verified and committed to storage. Only after that is the old blob retired. Interrupted migration prioritizes the verified biometric copy. Failed decryption throws instead of pretending the Vault is empty. Factory reset still explicitly erases Vault data.

## Restrictions
No face/PIN/voice/AI-command fallback to the strong biometric authentication in this slice. Without a strong biometric enrolled, the Vault stays locked, even with existing saved entries. Android can invalidate user-auth-bound keys when biometric or lock settings change, so test only with disposable credentials first.

An Android background Activity launch may be denied; Vault then fails closed. The biometric sheet may change foreground focus, so tool callbacks resume only after the sheet closes. Real-phone test required before trusting automated secret fill.

## Physical acceptance remaining
Test old Vault migration, new Vault, wrong/cancel fingerprint, device lock, expiry, automatic credential fill, reboot, key invalidation, factory reset and device-specific UI. Previously documented prompt injection and autonomous-tool risks are unchanged by request; do not claim production safety.

Do not merge or install with real secrets on the daily-use phone until device acceptance passes.