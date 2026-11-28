# BIAP

Internal operations app for an Indian D2C ethnic wear atelier (Salwar Kameez & Kurtis).
Android, Kotlin, Jetpack Compose (Material 3 Expressive).

Manages the design catalogue (sizes, stock, batch ledger, per-size pricing in paise, karigar
assignment, demand/cut advice, attribute suggestions), orders, and karigar job work
(samples, production, alterations, outwork with photo & voice-note attachments), with
offline-first encrypted local storage and encrypted cloud backup.

---

## Stack

| | |
|---|---|
| Language / UI | Kotlin `2.4.21` (KSP `2.3.14`), Jetpack Compose (BOM `2026.11.01`), Material 3 Expressive (`1.5.0`) |
| Build | AGP `9.5.3`, Gradle `9.9.3`, `compileSdk` 37.1, `targetSdk` 37, `minSdk` 24 |
| Navigation | Navigation 3 (`1.2.0`) |
| Local storage | Room `2.8.5` (schema v11, 15 entities) + SQLCipher `4.19.1`, per-identity encrypted DB |
| Auth & Access | Firebase Auth (BOM `35.1.0` — Email/Password, Phone OTP, Google Sign-In via `play-services-auth 21.6.0`), Firestore access roles, App Check, Biometric `AppLock` |
| Media (Photos & Voice) | Google Drive `appDataFolder`, AES-256-GCM encrypted (`DriveImageStore`) |
| Backup | Google Drive `appDataFolder`, AES-256-GCM encrypted (`bi_data.json.enc`), PBKDF2-wrapped sync key (`sync_key.enc`) |
| Background work | WorkManager `2.12.0` (`DriveSyncWorker`, `ImageUploadWorker`, `OrphanImageCleanupWorker`) |

## Modules and layout

```
app/src/main/java/com/sc/biap/
├── MainActivity.kt          auth gate, biometric lock & theme setup → MainNavigation
├── Navigation.kt            Navigation 3 back stack & predictive-back transitions
├── NavigationKeys.kt        serializable NavKey routes
├── data/
│   ├── Attributes.kt        catalogue attribute vocabulary, autocomplete & spelling drift detection
│   ├── Batch.kt             per-size batch ledger & FIFO/LIFO stock arithmetic
│   ├── DataRepository.kt    identity, SQLCipher DB lifecycle, SHA-256 adoption, Drive sync & job work
│   ├── Demand.kt            sales pace & next-cut size suggestions
│   ├── Design.kt            domain model (`Design`, `DesignPhoto`, `BusinessIntelligenceData`)
│   ├── DesignStats.kt       stock state thresholds & catalogue summary
│   ├── JobWork.kt           samples, production, alterations & outwork tracking
│   ├── Karigar.kt           Hub karigar/activity matching, linking & merge ranking
│   ├── Order.kt             orders, stock deltas & price-immutability helpers
│   ├── SyncMerger.kt        LWW merge with tombstones & independent media/archive clocks
│   ├── VoiceCommand.kt      Hindi/Gujarati spoken job-work parser (incl. compound numbers)
│   └── room/                Room entities (schema v11), Converters, DAO & migrations (v3→v11)
├── security/                `AppLock`, `BiometricGate`, `DatabaseKey`, `SyncPassphrase`, `PassphraseStore`, `AccountLinker`, `LegacyDatabaseMigrator`
├── theme/                   BIAP palette (`Color.kt`), Material 3 Expressive dynamic theme + AMOLED (`Theme.kt`), spacing, type & `horizontalFadingEdges`
├── ui/
│   ├── auth/                sign-in / OTP / Google Drive auth screens
│   ├── components/          `MediaAttachments` (photo picker/camera + AAC voice-note recorder/player)
│   ├── design/              `AddDesignScreen`, `DesignDetailScreen`, `DesignPhotos`, `SizeBatchSheet`
│   ├── hub/                 `BusinessHubMenu`, `BusinessHubScreen`, `WorkManagementScreen`, `WorkDialogs`, `HubIcons`
│   ├── main/                `MainScreen`, `CatalogueList`, `CatalogueRow`, `AccountMenu`, `SyncStamp`, `SplashScreen`
│   ├── orders/              `OrdersList`, `NewOrderScreen`
│   ├── profile/             `ProfileScreen`, `AccessManagementScreen`
│   └── settings/            `SettingsScreen`, `PassphraseDialogs`
└── util/                    `AuthManager`, `DriveImageStore`, `EncryptionUtils`, `GoogleDriveSyncManager`, `FileUtils`, `VoiceNotes`, `SizeUtils`, workers
app/src/debug|release/       per-variant App Check provider
baselineprofile/             Gradle Managed Device (`minSdk` 28) baseline profile generator
.github/                     Dependabot config + CodeQL security analysis workflow
```

## Architecture notes

**Identity & local encryption.** The Firebase `uid` is the app identity and keys the
Room database (`bi_database_<sha256_16B>.db`, carrying forward legacy MD5-named and
unsuffixed guest databases via `adoptDatabaseFilesWithSuffix`). Each database is encrypted
at rest with SQLCipher using a 256-bit random passphrase (`DatabaseKey`) sealed in
`EncryptedSharedPreferences` / Android Keystore. Google Sign-In is an *authorisation* for
Drive backup, not a statement of who the user is.

**Backup key wrapping.** The Drive AES-256-GCM sync key is wrapped locally via
PBKDF2-HMAC-SHA256 (210,000 iterations, random salt) in `sync_key.enc` (`SyncPassphrase`);
the plaintext passphrase is never uploaded.

**Media are immutable, objects are not.** Replacing a photo or attaching job-work media
writes a new versioned encrypted file in Drive `appDataFolder` (`img_<designId>_*.enc`,
`img_job_<jobId>_*.enc`, `vn_<jobId>_*.enc`) and repoints the record only after upload
succeeds (`ImageUploadWorker`). Originals are kept uncompressed; Coil `AsyncImage`
downscales at display size. Newest 5 design versions are retained.

**Uploads are queued.** Saving never blocks on the network — photos and AAC voice notes
(`32 kbps` mono `.m4a` via `VoiceNotes`) are saved locally and displayed/played from disk
while `ImageUploadWorker` catches up. `DriveImageStore.ensurePhotoLocal` / `ensureVoiceLocal`
fetch and cache remote files on demand under `cloud_images/` and `cloud_voice/`.

**Theme & design tokens.** `BiapTheme` enables Material 3 Expressive dynamic color on API
31+ (with Light, Dark, and battery-saver/manual AMOLED modes) and falls back to the BIAP
Ink/Brass/Cyan palette (`Color.kt`). Layout spacing, shapes, and typography come from
`AtelierSpacing`, `AtelierShapes`, and `*Emphasized` / `*Strong` type roles.

## Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # minified + R8-shrunk release
./gradlew :app:testDebugUnitTest    # JVM unit tests (229 tests / 24 suites)
./gradlew :app:lintDebug            # Android Lint
```

Requires `app/google-services.json` and the debug signing SHA-1 registered in the
Firebase project — Phone Auth verifies package name and signing certificate together, so an
unregistered fingerprint fails verification. Release builds read signing properties from
gitignored `local.properties` when present.

## Security rules

Firestore access roles (`invites` and `/users/{uid}`) are enforced server-side in
`firestore.rules`. Firebase Storage was removed; all design photos and JobWork media now
live encrypted in the signed-in user's Google Drive `appDataFolder`.

## Branches

| branch | contents |
|---|---|
| `main` | active trunk (Schema v11, JobWork + media sync, Business Hub, SDK 37, CodeQL/Dependabot) |
