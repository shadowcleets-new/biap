# 📋 Consolidated Technical Task List: biap Atelier ERP

This document tracks the implementation status of features, optimizations, architectural work, security improvements, and roadmap items for **biap** (`com.sc.biap`).

---

## 1. UI/UX & Aesthetics

### Spacing, Tokens & Redesigned Operations Layout
- [x] Define centralized semantic spacing/shape tokens (`AtelierSpacing` in `theme/Spacing.kt`, `AtelierShapes` in `theme/Theme.kt`).
- [x] Refactor hardcoded paddings/margins across `MainScreen.kt`, `DesignDetailScreen.kt`, and `AddDesignScreen.kt`.
- [x] Redesign main portfolio from photo grid to operations row index (`CatalogueList.kt`, `CatalogueRow.kt`) showing sorted size matrices, stock tags, and floating transparent bottom navigation (`MainScreen.kt`, `OrdersList.kt`).
- [x] Rebuild `DesignDetailScreen.kt` & `DetailPieces.kt` with `DesignPhotoPager` (`DesignPhotos.kt`), full `SizeMatrix`, `SizeBatchSheet`, and unmistakable `ReadingTopBar` / `EditingTopBar`.

### Touch Target Accessibility & Scrolling Polish
- [x] Apply `Modifier.minimumInteractiveComponentSize()` to interactive chips, icon buttons, and dialog buttons in `AddDesignScreen.kt`, `DesignDetailScreen.kt`, and `MainScreen.kt`.
- [x] Create reusable `Modifier.horizontalFadingEdges` in `theme/FadingEdges.kt` and apply across horizontally scrolling chip rows (`AddDesignScreen.kt`, `DesignDetailScreen.kt`, `CatalogueList.kt`, `WorkManagementScreen.kt`, `WorkDialogs.kt`, `SizeBatchSheet.kt`).

### Search Bar & Dynamic Filtering
- [x] Add visible "Clear Search" icon button to `CatalogueSearchField` (`CatalogueList.kt`) wired to `MainScreenViewModel.updateSearchQuery("")`.
- [x] Display search/filter match counter (`CatalogueMatchCount` in `CatalogueList.kt`) and add `"Clear search"` / `"Clear filters"` CTAs in scrollable `CatalogueEmptyState`.
- [x] Add category and stock-state filter chips (`CatalogueFilterRow` — `"Needs attention"`, dynamic categories, `"Archived · N"`) wired to `MainScreenViewModel`.

### Accessibility & Input Polish
- [x] Add explicit text labels for stock warning states (`MatrixRow` `"Out of stock"` / `"RUNNING LOW"` / `"HEALTHY"`, `AttentionTag`, `AttentionBand`, `WorkSummary`).
- [x] Intercept keyboard `ImeAction.Done` on Karigar inputs (`AddDesignScreen.kt` and `KarigarEditorDialog` in `DesignDetailScreen.kt`).
- [x] Prevent cold-start `"NO DESIGNS YET"` / `NotFound` flash via `DataRepository.isDatabaseReady`, `MainScreenViewModel`, `DesignDetailViewModel`, and `"LOADING…"` header status.
- [x] Verify `targetSdk 37` edge-to-edge inset handling (`Navigation.kt` inset delegation on `Main`, `MainScreen.kt` `WindowInsets` + `.imePadding()` on empty states).
- [x] Expose `Settings` in both the Drive-connected (`AccountMenu.kt`) and signed-out-of-Drive (`MainScreen.kt`) account menus.

---

## 2. Data Model & Local Storage

### SQLite Room Database (Schema v11) & Encrypted Persistence
- [x] Configure Room (`2.8.5`) with KSP (`2.3.12`) in `gradle/libs.versions.toml` and `app/build.gradle.kts`.
- [x] Implement schema v11 (15 entities) with incremental migrations `v3 -> v11` (`AppDatabase.kt`, `Entities.kt`, `Converters.kt`), `DesignDao`, and SQLCipher encryption (`DatabaseKey.kt`).
- [x] Implement per-identity SHA-256 database naming (`bi_database_<sha256_16B>.db`) with safe `.db`, `-wal`, `-shm`, `.db.bak`, and `biap_sync` stamp adoption from legacy MD5/guest databases (`DataRepository.kt`).
- [x] Implement Last-Write-Wins (LWW) conflict resolution with deletion tombstones across designs, custom fields, profile, orders, batches, karigars, activities, and job work (`SyncMerger.kt`), plus FK-safe transactional `DesignDao.importData`.
- [x] Implement `.bak` auto-backups, `.corrupt_*` recovery, and `OrphanImageCleanupWorker` covering `designs.imagePath`, `extraImages`, `job_work.photoPath`, and `user_profile.photoRef`.

### Image Handling
- ~~Implement destructive JPEG compression / downscaling (1920x1080) on saved photos before writing to `filesDir`.~~ — **Intentionally rejected by owner** (originals stay uncompressed per `docs/superpowers/specs/2026-08-08-multi-image-gallery-design.md`; `AsyncImage` downscales on decode for thumbnails/lists).

### Multi-Piece Configuration Flexibility
- [ ] Add top tab-toggles in `AddDesignScreen` and `DesignDetailScreen` edit mode to toggle off unused garment pieces (`Top`, `Bottom`, `Dupatta`) for 1-piece/2-piece designs and omit disabled fields prior to save (note: read mode `groupAttributes` already hides blank attributes).

---

## 3. Synchronisation, Security & Workshop Operations

### Background Sync & Security
- [x] Implement `DriveSyncWorker` (`CoroutineWorker`) with WorkManager `NetworkType.CONNECTED` constraint, exponential backoff, and retry cap (`DataRepository.kt`, `DriveSyncWorker.kt`).
- [x] Replace plaintext/account-seeded sync keys with Keystore/`EncryptedSharedPreferences` (`AES256_GCM` `MasterKey`), random 256-bit AES keys (`EncryptionUtils.kt`, `DatabaseKey.kt`), and PBKDF2-HMAC-SHA256 passphrase key wrapping (`SyncPassphrase.kt`, `PassphraseStore.kt`, `sync_key.enc`).
- [x] Implement persisted `"SYNCED AT ..."` / `"SYNCED YESTERDAY AT ..."` / `"SYNCED N DAYS AGO"` header status (`SyncStamp.kt`, `biap_sync` prefs) and snackbar `"Retry"` actions (`MainScreen.kt`).
- [x] Implement encrypted Drive media pipeline (`DriveImageStore`, `ImageUploadWorker`) for design photos (up to 5 per design, 5-version retention) and JobWork photo/AAC voice-note attachments (`MediaAttachments.kt`, `VoiceNotes.kt`).

### Workshop Intelligence, Orders, JobWork & Hub
- [x] Implement Orders tab (`OrdersList.kt`, `NewOrderScreen.kt`) with transactional stock movement and historical order editing (`Order.kt`).
- [x] Implement per-size Batch ledger (`Batch.kt`, `SizeBatchSheet.kt`) and stock reset at size/design scopes.
- [x] Implement Business Hub (`BusinessHubMenu.kt`, `BusinessHubScreen.kt`), drag-drop karigar-activity assignment, one-time karigar linking, ranked karigar merge (`Karigar.kt`), and `AttributeDrift` spelling tidy-up (`Attributes.kt`, `AttributeTidyDialog`).
- [x] Implement Ledger Demand & Next-Cut advice (`Demand.kt`, `LedgerSaysCard`).
- [x] Implement 4-kind JobWork tracking (`SAMPLE`, `PRODUCTION`, `ALTERATION`, `OUTWORK` in `JobWork.kt`, `WorkManagementScreen.kt`, `WorkDialogs.kt`) and Hindi/Gujarati voice command parsing (`VoiceCommand.kt`).

---

## 4. Architecture & Modularization

### Multi-Module Refactoring & Dependency Injection
- [ ] Refactor `:app` into dedicated library/feature modules (`:core:database`, `:core:sync`, `:core:auth`, `:feature:portfolio`, `:feature:detail`, etc.).
- [ ] Introduce Dependency Injection (Hilt or Koin) to replace `DefaultDataRepository.getInstance(context)` singletons.

---

## 5. Testing, CI & Device Verification

### Test Suites & CI
- [x] Unit test suite expanded to **229 tests across 24 suites**: LWW merge & tombstones (`OrderMergeTest`, `ProfileMergeTest`, `SyncMergerArchiveTest`, `SyncMergerImageTest`, `JobWorkSyncMergeTest`), persistence/FK cascade & adoption (`SyncV9ToV11PersistenceTest`, `DatabaseAdoptionTest`), retention/cleanup (`DriveImageStoreRetentionTest`, `OrphanImageCleanupTest`), security (`SyncPassphraseTest`, `AccessLevelTest`), domain logic (`BatchLedgerTest`, `DemandTest`, `DesignPhotosTest`, `JobWorkTest`, `KarigarAssignmentTest`, `KarigarLinkTest`, `OrderEditTest`, `OrderedSizeLabelTest`, `AttributesTest`, `VoiceCommandTest`), and UI/VM helpers (`RupeeFormatTest`, `SyncStampTest`, `ColdStartLoadingTest`).
- [x] Configure GitHub Actions `codeql.yml` (Kotlin build analysis + actions scan) and `dependabot.yml`.
- [ ] Add instrumented tests (`app/src/androidTest/`) for real SQLCipher Room migrations (`v3 -> v11`), WAL recovery, and `ComposeTestRule` UI checks.
- [ ] Run hardware verification on physical Pixel 9 once connected: live 25-design MD5 -> SHA-256 DB open (`03b1581`), real-mic voice-note record/playback (`380a908`), and full-screen `targetSdk 37` visual inset audit (`2fe0a72`).

---

## 6. Product Strategy & Roadmap

### Remaining ERP Features
- [ ] Implement client measurement cards (client profile entities + bespoke measurement lookup in order/job workflows).
- [ ] Add explicit tailor workload capacity limits/warnings inside design karigar pickers (building on `loadByKarigar` in `JobWork.kt`).
