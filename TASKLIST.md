# 📋 Consolidated Technical Task List: biap Atelier ERP

This document contains a highly granular, checklist-style breakdown of all feature tasks, optimizations, architectural refactoring, security improvements, and roadmap items for the **biap** mobile application.

---

## 1. UI/UX & Aesthetics

### Spacing & Layout Tokens (Phase 2)
- [x] Define a centralized semantic spacing scale `AtelierSpacing` in `Spacing.kt` under package `com.example.biap.theme`.
- [x] Refactor hardcoded paddings and spacing in the main portfolio screen `MainScreen.kt`.
- [x] Refactor hardcoded paddings and layout margins in details screen `DesignDetailScreen.kt`.
- [x] Refactor hardcoded paddings and layout margins in add screen `AddDesignScreen.kt`.
- [x] Verify layouts compile and align perfectly with Contemporary Heritage color styling.

### Responsive Viewports (Phase 2)
- [x] Retrieve screen metrics dynamically using `LocalConfiguration.current.screenWidthDp`.
- [x] Set main portfolio screen grid columns:
  - [x] 2 columns on phones (<600dp).
  - [x] 3 columns on foldables (600dp - 840dp).
  - [x] 4 columns on tablets (>=840dp).
- [x] Implement a dual-pane layout for `DesignDetailScreen` on tablets (width >= 840dp):
  - [x] Left pane (40% width): Sticky design image box with pan/zoom.
  - [x] Right pane (60% weight): Scrollable specifications/cards list.
  - [x] Clean modularization: Extract cards into private composables (`BasicDetailsSection`, `InventoryPricingSection`, `KarigarsSection`, `SpecificationsSection`) to prevent duplication.

### Touch Target Accessibility Compliance (Phase 2)
- [x] Pad custom size chips and toggles inside `AddDesignScreen` using `Modifier.minimumInteractiveComponentSize()`.
- [x] Pad custom size chips and selection chips inside `DesignDetailScreen` using `Modifier.minimumInteractiveComponentSize()`.
- [x] Pad all icon buttons (edit, add, back, Done/save) to at least 48x48dp interactive areas.
- [x] Pad dialog confirmation/dismiss buttons to at least 48x48dp interactive areas.
- [x] Verify visual dimensions are unaffected while touch target compliance is fully enforced.

### Horizontal Scroll Fading Edges
- [x] Create a custom reusable Compose modifier to render fading edge gradients (`drawWithContent` with DST_IN PorterDuff blends).
- [x] Apply fading edge modifiers to size chips row in details view.
- [x] Apply fading edge modifiers to size chips row in add view.
- [x] Apply fading edge modifiers to Assigned Karigars chip row.

### Search Bar Polish
- [ ] Add a visible "Clear Search" icon button to `SearchBarField` when there is active text.
- [ ] Bind clear button to reset the query string in `MainScreenViewModel`.
- [ ] Implement search counter text showing search query matches (e.g. `"14 matches found"`).

### Dynamic Filtering
- [ ] Add filter chips at the top of the main screen (under search bar) for quick category sorting (e.g. 'All', 'Kameez', 'Lehenga', 'Sherwani').
- [ ] Hook category filter chips to filter the grid contents.

### Accessibility Enhancements
- [ ] Add explicit text descriptions for stock warning indicators (e.g., "Low Stock: 3 items remaining" instead of only orange color).
- [ ] Intercept keyboard 'Done' actions on Karigar text input fields to add them directly.

---

## 2. Data Model & Local Storage

### SQLite Room Database Migration (Phase 1)
- [x] Configure Room compiler and dependencies inside `gradle/libs.versions.toml` and `app/build.gradle.kts`.
- [x] Create schema database entities:
  - [x] `DesignEntity` (stores core design properties).
  - [x] `SizeInventoryEntity` (stores sizes, stock, prices).
  - [x] `GlobalCustomFieldEntity` (stores global fields config).
  - [x] `DeletedDesignEntity` (stores tombstone tracking).
- [x] Implement converter classes for list and map serialization (custom fields).
- [x] Implement DAO interfaces `DesignDao` handling transactional logic.
- [x] Implement Last-Write-Wins (LWW) conflict resolution logic using `updatedAt` metadata.
- [x] Refactor `DefaultDataRepository` to query dynamic user-sandboxed database names (`bi_database_*.db`).

### Image Compression & Scaling
- [ ] Implement image compression using `BitmapFactory` and `compress(CompressFormat.JPEG, 85, ...)` before writing files to `filesDir`.
- [ ] Downscale max image resolutions to 1920x1080 (or similar) to prevent storage bloat.

### Multi-Piece Configuration Flexibility
- [ ] Add top tab-toggles in `AddDesignScreen` and `DesignDetailScreen` to toggle off unused items (e.g., toggle off 'Dupatta' or 'Bottom' for 1-piece kameez).
- [ ] Filter out disabled piece specifications from customFields maps prior to database writes.

---

## 3. Synchronisation & Security

### Background Synchronization via WorkManager
- [ ] Create `DriveSyncWorker` class extending `CoroutineWorker`.
- [ ] Configure `WorkManager` triggers (requires active network, unmetered constraint, and backoff policy).
- [ ] Register sync queue tasks inside `DataRepository` to trigger workers.

### Keystore Encryption Integration
- [ ] Replace standard Google Account ID key seeds with Keystore-backed AES key generation.
- [ ] Securely store dynamic salts inside `EncryptedSharedPreferences`.
- [ ] Update sync payload encryption/decryption utilities to use the new cipher.

### Sync UI Improvements
- [ ] Implement and display a "Last synced: [Timestamp]" subtitle on the portfolio top bar.
- [ ] Wire retry action button on the error snackbar notification for REST API upload/download failures.

---

## 4. Architecture & Modularization

### Multi-Module Refactoring
- [ ] Refactor the monolithic `:app` module.
- [ ] Extract library classes and declare new modules:
  - [ ] `:core:database` (handles Room, Entities, DAOs).
  - [ ] `:core:sync` (handles WorkManager, Google Drive API interactions).
  - [ ] `:core:auth` (handles Google Sign-In and profiles).
  - [ ] `:feature:portfolio` (main portfolio screen grid feature).
  - [ ] `:feature:detail` (add design and detail screen features).

### Dependency Injection
- [ ] Configure Hilt/Koin dependencies in build scripts.
- [ ] Create module containers defining singleton bindings for `Database`, `AuthManager`, and `Repository`.
- [ ] Inject repository instances into ViewModels via constructor injection.

---

## 5. Testing & Build Stability

### Test Suites Expansion
- [ ] Create mock instances for `GoogleDriveSyncController` and `EncryptionProvider`.
- [ ] Create unit tests for repository LWW synchronization and database import checkpoints.
- [ ] Create instrumented integration tests for Room database recovery, WAL checkpoints, and backup restoration.
- [ ] Add `ComposeTestRule` UI tests to verify dual-pane layouts on large devices and accessibility contrast limits.

---

## 6. Product Strategy & Roadmap

### Client Measurement Cards
- [ ] Add database tables and entities for client profile entries (Name, phone, specific garment measurements).
- [ ] Implement client lookup cards inside ordering workflows.

### Tailor Workload Tracker
- [ ] Add tracking of karigar schedules (orders assigned, deadlines).
- [ ] Display availability warnings in karigar select lists when workloads exceed tailors' capacity.
