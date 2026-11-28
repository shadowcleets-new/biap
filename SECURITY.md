# Security Policy

BIAP stores a business's catalogue, orders and karigar records, encrypted on the device
(SQLCipher) and in Google Drive (AES-256-GCM). Security reports are taken seriously.

## Reporting a vulnerability

**Do not open a public issue.** Use GitHub's private reporting instead:
**Security → Report a vulnerability** on this repository
(<https://github.com/shadowcleets-new/biap/security/advisories/new>).

Please include what is affected, steps to reproduce, and the impact you expect.
You will get an acknowledgement within 7 days.

## Supported versions

Only the latest commit on `main` is supported.

## In scope

- Local database encryption and key handling (`security/`, `DatabaseKey`, `PassphraseStore`)
- Drive backup encryption and sync key wrapping (`EncryptionUtils`, `SyncPassphrase`)
- Authentication, App Check, Firestore and Storage rules (`firestore.rules`, `storage.rules`)
- Exported Android components and intent handling

## Note on `google-services.json`

The Firebase API key in `app/google-services.json` is a public client identifier, not a
secret. Access is enforced by Firebase Auth, App Check and the security rules above.
