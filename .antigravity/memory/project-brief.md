# Project Brief: biap (Atelier Operations & Inventory ERP)

## 1. Executive Summary
`biap` (`com.sc.biap`) is a native Android operations and inventory application for an Indian D2C ethnic wear atelier (Salwar Kameez & Kurtis). It manages the design catalogue, per-size stock and batch history, pricing in paise, artisan (`Karigar`) assignment and job work, orders, and encrypted offline-first synchronization.

## 2. Architectural Overview
- **Platform:** Native Android (Kotlin `2.3.21`, KSP `2.3.12`)
- **UI Framework:** Jetpack Compose (BOM `2026.09.00`), Material 3 Expressive (`1.5.0-alpha29`), Navigation 3 (`1.2.0`)
- **Build System:** Gradle `9.8.0`, AGP `9.4.1` (`:app`, `:baselineprofile`)
- **Minimum SDK:** Android 24+ (`:baselineprofile` `minSdk 28`)
- **Compile / Target SDK:** Android 37 (`compileSdk = 37`, `targetSdk = 37`)

## 3. Database Schema & Data Models
Room `2.8.5` at **schema v11** (15 entities) encrypted with SQLCipher `4.19.1` (`DatabaseKey` in Keystore-backed `EncryptedSharedPreferences`), named per Firebase `uid` using truncated SHA-256 (`bi_database_<sha256_16B>.db`):
- **Core Entities:** `DesignEntity` (incl. `imageRef`, `archivedAt`, `extraImages` up to 5 photos), `SizeInventoryEntity` (`stock`, `price: Long` paise), `GlobalCustomFieldEntity`, `UserProfileEntity`
- **Operations & Workshop:** `OrderEntity` (`OrderLine` list), `BatchEntity` (FK cascade from `designs`), `KarigarEntity`, `ActivityEntity`, `JobWorkEntity` (`WorkKind`: `SAMPLE`, `PRODUCTION`, `ALTERATION`, `OUTWORK`; `photoPath`/`photoRef`, `voicePath`/`voiceRef`)
- **Sync Tombstones:** `DeletedDesignEntity`, `DeletedCustomFieldEntity`, `DeletedOrderEntity`, `DeletedKarigarEntity`, `DeletedActivityEntity`, `DeletedJobWorkEntity`
- **Domain & Analytics Helpers:** `Demand` (pace & next-cut recommendations), `Attributes` (catalogue vocabulary & `AttributeDrift`), `VoiceCommand` (Hindi/Gujarati spoken send-out parser incl. compound quantities), `SyncMerger` (LWW + tombstones + independent media/archive clocks)

## 4. Active Endpoints & Integrations
- **Local Storage:** Per-identity SQLCipher Room DB (`AppDatabase` v11) with `.bak` / `.corrupt_*` recovery and `OrphanImageCleanupWorker`; `filesDir` images (`img_*.jpg`, `cloud_images/`) and `32 kbps` mono AAC voice notes (`voice_notes/`, `cloud_voice/`).
- **Cloud Backup & Media:** Google Drive `appDataFolder` via `GoogleDriveSyncManager` & `DriveImageStore` — AES-256-GCM encrypted `bi_data.json.enc`, PBKDF2-HMAC-SHA256 wrapped sync key `sync_key.enc`, and versioned encrypted media `img_*.enc`, `img_job_*.enc`, `vn_*.enc`. (Firebase Storage removed.)
- **Auth & Access Control:** Firebase Auth (Email/Password, Phone OTP, Google Sign-In via `play-services-auth 21.6.0`), Firestore (`invites`, `/users/{uid}`), Firebase App Check (`PlayIntegrity` / `Debug`), and `BiometricGate` / `AppLock`.
- **Background Work & CI:** WorkManager `2.12.0` (`DriveSyncWorker`, `ImageUploadWorker`, `OrphanImageCleanupWorker`), GitHub Actions (`codeql.yml`, `dependabot.yml`).

## 5. Development Backlog
- [x] Native Android Compose + Material 3 Expressive architecture (`com.sc.biap`, Navigation 3, `PerformanceProfile`).
- [x] Encrypted Room DB (Schema v3 -> v11, SQLCipher `4.19.1`, `DatabaseKey`, SHA-256 per-UID DBs + MD5/guest `.bak` carry-over).
- [x] Google Drive encrypted backup (`SyncPassphrase` PBKDF2 wrapping, `DriveSyncWorker`, `SyncMerger` across v9–v11 tables, `SyncStamp`).
- [x] Multi-photo gallery (`MAX_PHOTOS = 5`), encrypted Drive photo/voice sync (`DriveImageStore`, `ImageUploadWorker`), and orphan cleanup.
- [x] Operations UI: Catalogue rows with size matrix, Orders tab with editable orders, Batch ledger & stock resets, Demand/cut insights, and Business Hub (drag-drop assignments, merge picker, `AttributeDrift` tidy-up).
- [x] JobWork ledger (`SAMPLE`, `PRODUCTION`, `ALTERATION`, `OUTWORK`), photo/voice attachments, and Hindi/Gujarati compound-number voice command parsing.
- [x] Cold-start `isDatabaseReady` loading gate, search match counter/clear CTAs, `targetSdk 37` inset handling, and 229 passing JVM unit tests (24 suites).
- [ ] Multi-piece outfit tab toggles (`Top`, `Bottom`, `Dupatta`) in `AddDesignScreen` / edit mode.
- [ ] Multi-module refactoring (`:core:*`, `:feature:*`) and Dependency Injection (Hilt/Koin).
- [ ] Instrumented Room migration/WAL/`ComposeTestRule` test suite (`app/src/androidTest/`) and client measurement cards.
- [ ] Physical-device smoke verification (live 25-design MD5 -> SHA-256 DB open, mic voice-note playback, `targetSdk 37` visual inset audit).
