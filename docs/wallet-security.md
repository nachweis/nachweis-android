# Wallet security posture (B2)

This documents the security guarantees the wallet integration layer enforces and the
behavior users and reviewers should expect. It matches the code in
`app/src/main/java/com/quellkern/nachweis/wallet/`; if the code and this document disagree,
that is a bug.

## Storage: never backed up

Wallet data is stored under `Context.noBackupFilesDir` (`WalletStorage.documentsDir`). The
platform excludes that subtree from Android auto-backup and device-to-device transfer.
The manifest backup rules (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`)
exclude every data domain a second time, and `android:allowBackup="false"` disables backup
outright. Wallet metadata, tokens, history, and logs therefore never leave the device
through any backup or transfer channel.

Evidence: `WalletStorageInstrumentedTest`, `WalletConfigInstrumentedTest` (device),
`WalletConfigFactoryTest` (JVM).

## Keys: Keystore-enforced user authentication

Credential keys are created with `userAuthenticationRequired = true` and a **zero** reuse
window (`WalletSecurityPolicy.secure`), i.e. authentication is required for every key use.
This is an Android Keystore key property, not a UI-level `BiometricPrompt` gate: the key
material is bound to the device's secure lock and cannot be used without a fresh
authentication, even by code paths that skip the app's own prompts. StrongBox is preferred
where the device supports it and wallet-core downgrades gracefully otherwise.

Evidence: `KeystoreUserAuthInstrumentedTest` proves the platform rejects an unauthenticated
signature with `UserNotAuthenticatedException` for a key created this way.

### Key invalidation and recovery

Auth-bound Keystore keys are invalidated by the platform when the security context they were
bound to changes:

- **Removing the secure lock screen** (switching to no PIN/pattern/password) permanently
  invalidates auth-required keys.
- **Enrolling a new biometric** (e.g. adding a fingerprint) invalidates keys bound to
  biometric authentication.

After invalidation, using the key surfaces `KeyPermanentlyInvalidatedException`. Because keys
are non-exportable and per-credential, the recovery path is **re-issuance**: the wallet
discards the invalidated key and the user re-obtains the affected credential from its issuer.
No key or credential is ever restored from a backup (there is none), so invalidation cannot
silently resurrect stale material on another device. Re-issuance UX lands with the issuance
slice (B4); the foundation guarantees only that invalidated keys fail closed.

## Logging: no credential material

`SecureWalletLogger` emits only a log level and the originating class/method. wallet-core log
records can carry disclosed claims (in the message) and payloads (in the throwable); both are
dropped. Release builds log nothing from wallet-core (`logLevel = OFF`); debug builds are
limited to error-level metadata.

Evidence: `SecureWalletLoggerTest` asserts the message body, tokens, and throwable text never
appear in the formatted output.

## Failure handling

Initialization failures are mapped to a closed set of typed errors (`WalletInitError`) with
display-safe messages that never echo the underlying exception text. See
`WalletInitErrorTest`.
