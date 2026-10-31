# Phase 9.2 — Android phone report: Settings fails with biometric Vault locked

Owner phone acceptance video: 94-second recording (29 October 2026). Main Andee UI renders but selecting the Settings gear drops back to Android Home; later a system dialog says “Andee keeps stopping”. The UI displays CodeFlying API-key-missing errors; the build intentionally ships without shared cloud credentials. Neither the missing key nor a biometric key should crash the Settings card.

## Confirmed static incompatibility
Phase 9 `Vault.entries(context)` now requires a live strong-biometric Vault session. The original v0.8.0 `SettingsUi.vaultSection()` synchronously reads `Vault.entries(context).size` during `SettingsUi.build()`, which occurs before `show()`'s try/catch surrounding `WindowManager.addView`. Without prior Vault authentication this throws an `IllegalStateException` while creating the Settings sheet. The exact real-phone stack trace is not present in the uploaded recording; this is a directly supported code-level crash path, not verified attribution for every observed crash.

## Smallest safe correction
Only `SettingsUi.kt` and localized Settings strings change in production. When Vault is locked, display a localized locked indicator without reading saved credential count. When its key is invalid/unavailable, display an unavailable indicator instead of claiming zero entries. When Vault already has valid biometric authorization, continue displaying the original count. The existing Manage button opens the unchanged `VaultUi`, which is the only place Settings requests biometric verification.

No changes to Keystore, Vault encryption/migration, strong biometric requirement, saved data, gesture or tool dispatch, voice, UI mascot, autonomous WORK, SMS/WhatsApp, or local/remote Hub.

## Tests
Disposable Android emulator instrumentation builds the full Settings screen while Vault is locked; previously this would throw. A second test asserts that credential reads remain forbidden while Vault is locked. Independent source checker enforces unchanged original Andee runtime files and localized strings. Both previous Phase 9/9.1 audits and all Android/Release/Hub tests remain.

## UI design and the next step
This is a crash hotfix, not a cosmetic redesign. The owner has not been able to display the actual Settings UI, so redesigning its nested content now risks removing tools and configuration. Once the hotfix APK displays Settings successfully, use a fresh recording to decide where to place a separate Gemini Live voice mode and how to reduce clutter without deleting original voice providers.

## Hardware acceptance remaining
On a spare device: Settings opens on a fresh install; the Vault section says locked; Manage shows biometric; cancel returns safely; valid unlock displays original summary; leave Settings/reopen works; notification permission return path no longer kills Settings; verify independent “Andee keeps stopping” if still present. Do not enable sensitive permissions or enter real secrets during initial debugging.
