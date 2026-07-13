# HAIKU 4.5 OPERATION MANUAL — biap Atelier ERP
## Total Repair & Material 3 Expressive Uplift

**Author:** Fable 5 (Principal Android Software Architect)
**Executor:** Claude Haiku 4.5 — mechanical execution only. Do not design, do not invent, do not improvise.
**Repo root:** `C:\Users\acer\projects\biap`
**Baseline commit:** `c8ed6c7` + uncommitted working tree (the working tree is the baseline — do NOT check out the commit).
**Date authored:** 2026-07-13

---

## PRIME DIRECTIVES (from the owner — non-negotiable)

1. **The app stays 100% Kotlin.** No Java files, no new languages. All new code is Kotlin (Jetpack Compose for UI).
2. **Make full use of Material 3 Expressive.** `MaterialExpressiveTheme`, expressive `MotionScheme`, `LoadingIndicator`, `LinearWavyProgressIndicator`, emphasized typography — as dictated in Phase 4. The Heritage brand palette is retained; dynamic color stays OFF.
3. **Fix all the bugs.** Every entry in the Issue Registry below MUST be resolved by the Execution Queue in Phase 8. No issue may be skipped, deferred, or "noted for later."

## EXECUTION PROTOCOL

- Execute Phase 8 tasks in ascending Task-ID order. Never reorder. Never parallelize dependent tasks.
- A task is complete only when its Phase 10 verification row passes.
- After every 3 tasks, run `.\gradlew.bat compileDebugKotlin` from the repo root. If it fails, fix ONLY the task you just did; after 2 failed self-correction attempts on the same task, STOP and report the raw compiler output (owner's circuit-breaker rule). **Never run `git checkout -- <file>` to roll back — the working tree contains unpushed work.**
- The Phase 9 Guardrail Block applies verbatim to every task.

---

## ISSUE REGISTRY (all must be fixed)

| ID | Severity | File | Defect | Fixed by |
|----|----------|------|--------|----------|
| BUG-01 | CRITICAL | data/DataRepository.kt | `performSyncInternal` merges against `_biData.value`, which can be empty right after account switch → `importData` then wipes every table (permanent local data loss) | TASK-210 |
| BUG-02 | CRITICAL | data/DataRepository.kt | Writes silently dropped during account switch (`currentDao ?: return@launch`) | TASK-210 |
| BUG-03 | CRITICAL | data/DataRepository.kt | Old Drive backups (price as `Double`, pre-`updatedAt` schema) fail `decodeFromString` → infinite background retry loop, sync never succeeds | TASK-201, TASK-210 |
| BUG-04 | CRITICAL | util/EncryptionUtils.kt | Sync key silently falls back to PLAINTEXT SharedPreferences (`biap_prefs_fallback`); read/write can hit different stores → divergent keys on one device | TASK-204 |
| BUG-05 | CRITICAL | data/DataRepository.kt | Remote `sync_key.txt` unconditionally overwrites local key with no validation; two fresh devices racing both upload keys → different keys → endless `AEADBadTagException` retry | TASK-210 |
| BUG-06 | HIGH | util/GoogleDriveSyncManager.kt | Two-step upload (metadata POST, then media PATCH) leaves a zero-byte `bi_data.json.enc` if step 2 fails → all future syncs return failure forever | TASK-210 (self-heal), TASK-205 |
| BUG-07 | HIGH | util/OrphanImageCleanupWorker.kt | Opens every DB with `fallbackToDestructiveMigration()` (can silently WIPE a user DB inside a cleanup job); deletes images with no age threshold (can delete a just-captured photo); if one DB fails to open, its images are treated as orphans and deleted | TASK-211 |
| BUG-08 | HIGH | ui/design/DesignDetailScreen.kt:52 | Constructs `DefaultDataRepository(context)` directly instead of `getInstance` → second repository instance with disjoint sync state | TASK-308 |
| BUG-09 | HIGH | ui/design/DesignDetailScreen.kt:708 | View mode renders raw paise: `"₹${activeInventory.price}"` shows ₹150000 for a ₹1,500 item | TASK-308 |
| BUG-10 | HIGH | ui/design/DesignDetailScreen.kt:276 | Loads image via `File(imagePathToUse)` instead of `FileUtils.resolveImageFile` → broken images after reinstall/restore on this screen only | TASK-308 |
| BUG-11 | HIGH | ui/main/MainScreen.kt:193 | "Discard Guest Data" button discards nothing — dialog re-nags on every screen re-entry | TASK-210 (clearGuestData), TASK-304, TASK-305 |
| BUG-12 | HIGH | data/DataRepository.kt:506-520 | `hasGuestData()` checks only image paths → guest designs without photos never trigger the merge prompt; DB connection leaks on exception | TASK-203, TASK-210 |
| BUG-13 | HIGH | data/DataRepository.kt:522-564 | `mergeGuestData()` restamps tombstones `deletedAt = now` (can delete freshly-merged designs on next sync); `guestDb.close()` skipped on exception | TASK-210 |
| BUG-14 | HIGH | util/AuthManager.kt:60-63 | `UserRecoverableAuthException` (user must re-approve Drive access) swallowed to `null` → invisible never-ending background retry | TASK-206, TASK-207, TASK-304 |
| BUG-15 | MEDIUM | ui/design/DesignDetailScreen.kt:79 | `tempPhotoUri` in plain `remember` → camera photo lost if Activity is recreated while camera is open | TASK-308 |
| BUG-16 | MEDIUM | ui/design/DesignDetailScreen.kt:653-657 | Writes `selectedSizeIndex` state during composition; `remember(design)` resets selection on every background sync | TASK-308 |
| BUG-17 | MEDIUM | ui/design/DesignDetailScreen.kt | Background sync landing mid-edit is silently overwritten on save (no conflict check) | TASK-308 |
| BUG-18 | MEDIUM | ui/design/AddDesignViewModel.kt:65-84 | Zero-sentinel: user deliberately clearing stock AND price to 0 gets stale cached values silently restored | TASK-306, TASK-307 |
| BUG-19 | MEDIUM | ui/design/AddDesignScreen.kt:98-104 | `isFormDirty` ignores `category` → category-only edits bypass the unsaved-changes guard | TASK-306, TASK-307 |
| BUG-20 | MEDIUM | theme/Theme.kt:55-57 | `ColorScheme.warning` re-queries `isSystemInDarkTheme()` instead of reading the applied scheme → wrong color if `darkTheme` param is overridden | TASK-301 |
| BUG-21 | MEDIUM | theme/FadingEdges.kt:45-57 | Container narrower than 2×fadeWidth → gradient degenerates to Transparent→Transparent with `DstIn` → entire row content erased | TASK-303 |
| BUG-22 | MEDIUM | util/SizeUtils.kt:4-7 | `XXXL` and `3XL` are two distinct entries (same physical size sorts to 2 positions); numeric sizes ≥ 9000 collide with the custom band | TASK-208 |
| BUG-23 | MEDIUM | AndroidManifest.xml | No `INTERNET`/`ACCESS_NETWORK_STATE` permission declared (works only via library manifest-merge); `windowSoftInputMode` sits on `<application>` where it is invalid; `allowBackup="true"` with backup rules present but not wired | TASK-103, TASK-104 |
| BUG-24 | MEDIUM | app/build.gradle.kts | Duplicate `kotlin { jvmToolchain(17) }` block; release build has `isMinifyEnabled = false` and empty ProGuard rules | TASK-101, TASK-102 |
| BUG-25 | LOW | ui/main/SplashScreen.kt:142-152 | Hardcoded footer "DESIGN SYSTEM MOCKED IN STITCH BY GOOGLE" ships in production; mandatory 2.7 s splash delay | TASK-309 |
| BUG-26 | LOW | data/room/Converters.kt | JSON parse failures silently return empty list/map with no log (silent data evaporation) | TASK-209 |
| BUG-27 | LOW | theme/Type.kt:9 | Stale header comment claims "Google Sans" while styles are Serif | TASK-302 |
| BUG-28 | LOW | theme/Theme.kt:59-74 | Glow brushes hardcode near-palette hexes that silently drift from Color.kt | TASK-301 |
| BUG-29 | LOW | app/src/test/.../MainScreenViewModelTest.kt | `uiState_onItemSaved_isDisplayed` is a copy of the first test (name lies); `assertEquals` args reversed | TASK-402 |
| BUG-30 | LOW | util/EncryptionUtils.kt:20-59 | Dead legacy PBKDF2 passphrase code (1000 iterations) kept in file | TASK-204 |

---

# PHASE 1 — MODULAR PROJECT SCAFFOLDING & BUILD MATRIX

## 1.1 Module decision: SINGLE MODULE (`:app`) — FROZEN

The multi-module split (`:core:database`, `:core:sync`, `:feature:portfolio`, …) in the project backlog is **explicitly out of scope for this manual**. Haiku 4.5 MUST NOT create new Gradle modules, move files between source sets, or rename packages. Every file keeps its current path unless a task says otherwise.

Authoritative folder tree (only files this manual touches are listed):

```
C:\Users\acer\projects\biap\
├── build.gradle.kts                      (FROZEN — do not edit)
├── settings.gradle.kts                   (FROZEN — do not edit)
├── gradle.properties                     (FROZEN — do not edit)
├── gradle\libs.versions.toml             (FROZEN — do not edit)
├── HAIKU_OPERATION_MANUAL.md             (this file)
└── app\
    ├── build.gradle.kts                  (TASK-101)
    ├── proguard-rules.pro                (TASK-102)
    ├── google-services.json              (FROZEN)
    └── src\
        ├── main\
        │   ├── AndroidManifest.xml       (TASK-103)
        │   ├── res\xml\backup_rules.xml  (TASK-104)
        │   ├── res\xml\data_extraction_rules.xml (TASK-104)
        │   └── java\com\example\biap\
        │       ├── MainActivity.kt       (FROZEN)
        │       ├── Navigation.kt         (FROZEN)
        │       ├── NavigationKeys.kt     (FROZEN)
        │       ├── data\
        │       │   ├── DataRepository.kt         (TASK-210)
        │       │   ├── Design.kt                 (FROZEN)
        │       │   ├── LegacyBackupDecoder.kt    (NEW — TASK-201)
        │       │   ├── SyncMerger.kt             (NEW — TASK-202)
        │       │   └── room\
        │       │       ├── AppDatabase.kt        (FROZEN — version stays 3)
        │       │       ├── Converters.kt         (TASK-209)
        │       │       ├── DesignDao.kt          (TASK-203)
        │       │       └── Entities.kt           (FROZEN)
        │       ├── theme\
        │       │   ├── Color.kt                  (TASK-301, small addition)
        │       │   ├── FadingEdges.kt            (TASK-303)
        │       │   ├── Spacing.kt                (FROZEN)
        │       │   ├── Theme.kt                  (TASK-301)
        │       │   └── Type.kt                   (TASK-302)
        │       ├── ui\
        │       │   ├── PerformanceManager.kt     (FROZEN)
        │       │   ├── design\
        │       │   │   ├── AddDesignScreen.kt        (TASK-307)
        │       │   │   ├── AddDesignViewModel.kt     (TASK-306)
        │       │   │   ├── DesignDetailScreen.kt     (TASK-308)
        │       │   │   └── DesignDetailViewModel.kt  (FROZEN)
        │       │   └── main\
        │       │       ├── MainScreen.kt             (TASK-304)
        │       │       ├── MainScreenViewModel.kt    (TASK-305)
        │       │       └── SplashScreen.kt           (TASK-309)
        │       └── util\
        │           ├── AuthManager.kt                (TASK-206)
        │           ├── DriveSyncWorker.kt            (TASK-207)
        │           ├── EncryptionUtils.kt            (TASK-204)
        │           ├── FileUtils.kt                  (FROZEN)
        │           ├── GoogleDriveSyncManager.kt     (TASK-205)
        │           ├── OrphanImageCleanupWorker.kt   (TASK-211)
        │           └── SizeUtils.kt                  (TASK-208)
        ├── test\java\com\example\biap\
        │   ├── data\LegacyBackupDecoderTest.kt   (NEW — TASK-403)
        │   ├── data\SyncMergerTest.kt            (NEW — TASK-404)
        │   ├── ui\main\MainScreenViewModelTest.kt (TASK-402)
        │   └── util\InventoryLogicTest.kt        (TASK-401)
        └── androidTest\java\com\example\biap\ui\main\MainScreenTest.kt (FROZEN)
```

## 1.2 Version Catalog — `gradle/libs.versions.toml` is FROZEN

The catalog below is the **current, verified-building content**. Haiku MUST NOT change any version number. It is reproduced so Phase 10 can audit against it. Key pins: AGP `9.2.1`, Kotlin `2.1.0`, Compose BOM `2026.03.01` (its material3 includes the Material 3 Expressive APIs used in Phase 4), Room `2.8.4` (kapt), WorkManager `2.9.0`, security-crypto `1.1.0-alpha06`, OkHttp `4.12.0`, play-services-auth `21.2.0`, Navigation 3 `1.0.1`, Coil 3 `3.0.4`, kotlinx-serialization-json `1.6.3`.

Known accepted debts (do NOT "fix" these): Room runs through kapt (KSP migration out of scope); security-crypto is an alpha (no stable exists); the unused `kotlin-kapt` plugin alias stays.

## 1.3 Build types — exactly two. No flavors.

`debug` and `release` only. **Do not create a `staging` build type** — this app has no backend environments; its only remote is the user's own Google Drive. `buildConfig` is disabled; there are no `buildConfigField` constants and none may be added.

Release signing: the owner has not supplied a keystore. The `signingConfigs` block in TASK-101 is present but **commented out** with placeholder paths; Haiku MUST NOT invent keystore files or passwords. Release builds are compile-verified only (`assembleRelease`), not distributed, until the owner supplies signing values.

## 1.4 R8/ProGuard

Release turns on `isMinifyEnabled = true` + `isShrinkResources = true` with the exact rules file in TASK-102. Rules cover: kotlinx-serialization serializers for `com.example.biap.**`, OkHttp/Okio dontwarn, and Google Sign-In classes reached via reflection. Room 2.8.4, Coil 3, and play-services ship consumer rules — no extra rules for them.

---

# PHASE 2 — DEPENDENCY INJECTION & CORE OBJECT GRAPH

## 2.1 DI decision: MANUAL SINGLETON GRAPH — Hilt/Koin are BANNED

This app uses hand-wired construction. Haiku MUST NOT add Hilt, Koin, Dagger, or any DI library, annotation, or Gradle plugin. The complete object graph after this manual is applied:

```
DefaultDataRepository  (process-wide singleton; ONLY via DefaultDataRepository.getInstance(context))
 ├── constructs AuthManager(context)                    — exposed as `authManager`
 ├── constructs GoogleDriveSyncManager()                — private
 ├── uses      EncryptionUtils                          — Kotlin `object` (stateless)
 ├── uses      SyncMerger                               — Kotlin `object` (pure, NEW)
 ├── uses      LegacyBackupDecoder                      — Kotlin `object` (pure, NEW)
 ├── owns      AppDatabase / DesignDao                  — per-account, swapped on auth change
 └── schedules DriveSyncWorker / OrphanImageCleanupWorker via WorkManager
```

## 2.2 Exact constructor & factory signatures (Phase 10 audits these characters-exact)

```kotlin
class DefaultDataRepository(private val context: Context) : DataRepository
    companion object { fun getInstance(context: Context): DefaultDataRepository }

class AuthManager(private val context: Context)
class GoogleDriveSyncManager()
object EncryptionUtils
object SyncMerger
object LegacyBackupDecoder
object FileUtils
object SizeUtils

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel()
class AddDesignViewModel(private val repository: DataRepository) : ViewModel()
class DesignDetailViewModel(private val designId: String, private val repository: DataRepository) : ViewModel()

class DriveSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams)
class OrphanImageCleanupWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams)
```

## 2.3 ViewModel factory composables (the ONLY places ViewModels are constructed)

```kotlin
// MainScreen.kt
@Composable fun defaultMainScreenViewModel(): MainScreenViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { MainScreenViewModel(DefaultDataRepository.getInstance(context)) }
}
// AddDesignScreen.kt
@Composable fun defaultAddDesignViewModel(): AddDesignViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { AddDesignViewModel(DefaultDataRepository.getInstance(context)) }
}
// DesignDetailScreen.kt — AFTER TASK-308 (this is the BUG-08 fix)
@Composable fun defaultDesignDetailViewModel(designId: String): DesignDetailViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { DesignDetailViewModel(designId, DefaultDataRepository.getInstance(context)) }
}
```

**Invariant (Phase 10 audit):** after all tasks, `DefaultDataRepository(` as a direct constructor call appears exactly ONCE in the entire codebase — inside `getInstance`.

---

# PHASE 3 — THE IMMUTABLE DATA & NETWORK ENGINE

## 3.1 Network layer: Google Drive REST v3 via OkHttp — Retrofit is BANNED

The app's ONLY network surface is Google Drive `appDataFolder`. Haiku MUST NOT add Retrofit, Ktor, or any HTTP abstraction. `GoogleDriveSyncManager` is the single HTTP client. Exact wire contract:

| Op | Method & URL | Headers | Body | Success | Failure handling |
|----|--------------|---------|------|---------|------------------|
| searchFile | `GET https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=<urlencoded name='FILENAME'>&fields=files(id,name)` | `Authorization: Bearer <token>` | — | 200 → JSON `{"files":[{"id","name"},…]}` → return `files[0].id` else null | non-2xx or exception → null |
| downloadFile | `GET https://www.googleapis.com/drive/v3/files/{fileId}?alt=media` | `Authorization: Bearer <token>` | — | 200 → raw body string | non-2xx or exception → null |
| uploadFile (create) | `POST https://www.googleapis.com/drive/v3/files` then `PATCH https://www.googleapis.com/upload/drive/v3/files/{id}?uploadType=media` | `Authorization: Bearer <token>`; POST body `application/json; charset=utf-8`; PATCH body `application/octet-stream` | POST: `{"name":"<fileName>","parents":["appDataFolder"]}`; PATCH: content | fileId string | any step non-2xx/exception → null |
| uploadFile (update) | `PATCH https://www.googleapis.com/upload/drive/v3/files/{existingFileId}?uploadType=media` | as above | content | fileId | null |
| renameFile (NEW, TASK-205) | `PATCH https://www.googleapis.com/drive/v3/files/{fileId}` | `Authorization: Bearer <token>`, body `application/json; charset=utf-8` | `{"name":"<newName>"}` | true | false |

Remote files (all inside the hidden `appDataFolder`):
- `bi_data.json.enc` — Base64( iv[12] ‖ AES-256-GCM ciphertext ) of the dataset JSON.
- `sync_key.txt` — Base64 of the raw 32-byte AES key (see Phase 7 for the trust model).
- `bi_data.json.enc.unreadable_<epochMillis>` — quarantined undecryptable payloads (created by TASK-210, never read back).

## 3.2 Dataset JSON schema (encrypt/decrypt payload) — CURRENT (schema v2)

```json
{
  "designs": [{
    "id": "uuid-string", "name": "string", "category": "string",
    "imagePath": "img_1719000000000.jpg | null",
    "sizeInventories": [{"size": "M", "stock": 4, "price": 150000}],
    "customFields": {"Top - Fabric": "Silk"},
    "karigars": ["Master Salim"],
    "updatedAt": 1719000000000
  }],
  "globalCustomFields": ["Top - Fabric", "..."],
  "globalCustomFieldsMetadata": {"Top - Fabric": 1719000000000},
  "deletedDesignIds": ["uuid"],
  "deletedDesigns": {"uuid": 1719000000000},
  "deletedCustomFields": {"Old Field": 1719000000000}
}
```
`price` is **Long paise** (₹1,500.00 = `150000`). All timestamps are epoch millis from `System.currentTimeMillis()` (accepted debt: device-clock LWW).

**LEGACY schema (v1, still on some Drives):** `price` was a rupee `Double` (`1500.0`), `updatedAt`/`karigars`/metadata/tombstone maps may be absent. `LegacyBackupDecoder` (TASK-201) MUST accept both: strict v2 decode first; on failure, lenient v1 decode converting `price → Math.round(priceDouble * 100)`, defaulting `updatedAt = 0L` and absent collections to empty. Returns `null` only for garbage.

## 3.3 Room schema — database `version = 3`, FROZEN

Tables (see `Entities.kt`, unchanged): `designs` (PK `id` String; JSON-converted `customFields`, `karigars`; `updatedAt`), `size_inventories` (autogen PK; FK `designId → designs.id` `onDelete = CASCADE`; unique index `(designId, size)`; `price` Long), `global_custom_fields` (PK `field`; `updatedAt`), `deleted_designs` (PK `designId`; `deletedAt`), `deleted_custom_fields` (PK `field`; `deletedAt`).

TASK-203 adds three **one-shot suspend queries** to `DesignDao` (no schema change, version stays 3): `getAllDesignsOnce`, `getAllDeletedDesignsOnce`, `getAllDeletedCustomFieldsOnce`.

DB files in the default database dir: `bi_database.db` (guest) and `bi_database_<md5(googleId)>.db` (per account), plus `.bak` siblings (local backup) and `.corrupt_<ts>` snapshots (corruption quarantine). The main open path keeps `fallbackToDestructiveMigration()` (accepted, changelog-documented policy — Drive is the restore path). The cleanup worker and guest-DB opens must NOT use it (TASK-210, TASK-211).

## 3.4 Repository caching truth (single source of truth rules)

1. **Room is the only source of truth.** The `_biData` StateFlow is a read-through cache fed by `combine()` of four DAO flows; it is for UI display ONLY.
2. **Sync must never read `_biData.value`.** All merge inputs come from `snapshotFromDao(dao)` (one-shot DAO queries) — this is the BUG-01 fix and is non-negotiable.
3. Writes go through `awaitDao()` (waits up to 5 s for the account's DAO during account switches — BUG-02 fix), stamp `System.currentTimeMillis()`, maintain tombstones, then `triggerLocalBackup()`.
4. Offline is a first-class state: all CRUD is local-only; sync is explicit (button) via a `NetworkType.CONNECTED`-constrained WorkManager job. No implicit background sync exists or may be added.
5. Merge = `SyncMerger.merge(local, remote)` — pure LWW on `updatedAt`/`deletedAt` with tombstone pruning (TASK-202 extracts the existing verified logic verbatim; SyncMergerTest locks it).

---

# PHASE 4 — RIGID STATE ARCHITECTURE & UI MANIFEST

## 4.1 UDF pattern in force

`StateFlow<UiState>` out of ViewModels, plain function calls in (this codebase does not use an Event sealed class; Haiku MUST NOT introduce one). One-time effects use `LaunchedEffect` with the exact keys dictated below. Screens collect with `collectAsStateWithLifecycle()`.

## 4.2 UI State definitions (complete field inventory — Phase 10 checks completeness)

```kotlin
// MainScreenViewModel.kt (FROZEN shape)
sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: BusinessIntelligenceData) : MainScreenUiState
}
// plus: searchQuery: StateFlow<String>, isSyncing: StateFlow<Boolean>, syncStatus: StateFlow<SyncStatus>

// AddDesignViewModel.kt (after TASK-306)
data class AddDesignUiState(
    val name: String = "",
    val category: String = AddDesignViewModel.DEFAULT_CATEGORY,
    val imagePath: String? = null,
    val sizes: List<SizeInventory> = emptyList(),
    val availableSizes: List<String> = SizeUtils.sortSizes(listOf("S", "M", "L", "XL", "XXL")),
    val customFields: Map<String, String> = emptyMap(),
    val globalCustomFields: Set<String> = emptySet(),
    val karigars: List<String> = emptyList(),
    val isSaved: Boolean = false
)

// DesignDetailViewModel.kt (FROZEN shape)
sealed interface DesignDetailUiState { object Loading; data class Success(val design: Design); object NotFound }

// DataRepository.kt (FROZEN shape)
sealed interface SyncStatus { object Idle; object Syncing; data class Success(val message: String); data class Error(val message: String) }
```

Screen-local `remember` state added by this manual: `MainScreen`: `showDiscardConfirm: Boolean`; `DesignDetailScreen`: `tempPhotoUriString: String?` (rememberSaveable), `editBaseUpdatedAt: Long`, `showConflictDialog: Boolean`, `selectedSizeIndexRaw: Int`.

## 4.3 One-time effect keys (exact)

| Screen | Effect | Key(s) | Purpose |
|--------|--------|--------|---------|
| MainScreen | `LaunchedEffect(syncStatus)` | syncStatus | show sync snackbar + Retry, then `resetSyncStatus()` |
| MainScreen | `LaunchedEffect(currentUser)` | currentUser | guest-merge prompt when signed in and `hasGuestData()` |
| MainScreen | `LaunchedEffect(pendingAuthIntent)` | pendingAuthIntent | BUG-14 re-approval snackbar with "Approve" action |
| AddDesignScreen | `LaunchedEffect(state.isSaved)` | state.isSaved | navigate back after save |
| DesignDetailScreen | `LaunchedEffect(isEditing)` | isEditing | copy design into edit buffers + capture `editBaseUpdatedAt` |
| SplashScreen | `LaunchedEffect(profile)` | profile | intro animation then `onSplashComplete()` |

## 4.4 Material 3 Expressive manifest (Prime Directive 2)

The Compose BOM `2026.03.01` material3 contains these APIs. All are gated behind `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` — add that opt-in exactly where dictated in Phase 8. **If any of these symbols does not resolve at compile time, STOP and report — do not substitute a different component.**

| # | Adoption | Where | Rule |
|---|----------|-------|------|
| M3E-1 | `MaterialExpressiveTheme(colorScheme, motionScheme, typography, content)` replaces `MaterialTheme` | `Theme.kt` (TASK-301) | Heritage color schemes unchanged; `dynamicColor = false` stays |
| M3E-2 | `motionScheme = MotionScheme.expressive()` on HIGH_FIDELITY, `MotionScheme.standard()` on LOW_POWER | `Theme.kt` via `rememberPerformanceProfile()` | This makes every M3 component (chips, dialogs, FAB, switches) animate expressively app-wide, auto-degrading on weak/battery-saver devices |
| M3E-3 | `LoadingIndicator(...)` replaces `CircularProgressIndicator` on HIGH_FIDELITY (LOW_POWER keeps the circular one) | MainScreen ×2, DesignDetailScreen ×1 | sizes dictated in tasks |
| M3E-4 | `LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())` shown while `isSyncing` (LOW_POWER: `LinearProgressIndicator`) | MainScreen, above the search bar | full-width, no height override |
| M3E-5 | `MaterialTheme.typography.titleLargeEmphasized` for the portfolio title | MainScreen TopAppBar | replaces manual `fontWeight = FontWeight.Bold` on that title |
| M3E-6 | Existing spring press-scale on `DesignCard`, Navigation-3 spring transitions, splash animation | already present | keep; they now sit on the expressive motion theme |

Layout tokens are FROZEN: all spacing uses `AtelierSpacing` (None 0 / Hairline 1 / Micro 4 / Compact 8 / Cozy 12 / Base 16 / Large 24 / Double 32 dp), corners use `AtelierShapes` (Small 8 / Medium 12 / Large 16 dp). Haiku MUST NOT introduce new raw dp literals except those already present in the code being edited.

---

# PHASE 5 — ERROR BOUNDARIES, EXCEPTIONS & RESILIENCE

## 5.1 Failure classification (exact behavior table)

| Condition | Detection point | Mandated behavior |
|-----------|-----------------|-------------------|
| No signed-in account / missing Google ID | DriveSyncWorker start | `Result.failure()` → `SyncStatus.Error("Sync failed in background execution.")` |
| OAuth token needs user re-approval (HTTP 401 class) | `UserRecoverableAuthException` in `AuthManager.getAccessToken` | store `e.intent` in `pendingAuthIntent`; worker returns `Result.failure()` (NOT retry); MainScreen shows "Approve" snackbar that launches the intent, then clears it and re-syncs |
| Token null for any other reason | worker | `Result.retry()` until attempt cap |
| Drive HTTP 403/404/5xx or network I/O failure | `GoogleDriveSyncManager` returns null → exception path in `performSyncInternal` | `Result.retry()` with exponential backoff |
| Remote payload blank/zero-byte (BUG-06 artifact) | `encryptedContent.isNullOrBlank()` with a fileId | SELF-HEAL: PATCH the local encrypted snapshot onto that fileId, `Result.success()` |
| Remote payload undecryptable with candidate AND local key | both `decryptWithKey` attempts throw | QUARANTINE: `renameFile(..., "bi_data.json.enc.unreadable_<now>")`, fresh-upload local snapshot, `Result.success()` |
| Remote JSON undecodable even by legacy decoder | `LegacyBackupDecoder.decode` returns null | same QUARANTINE path |
| Any other exception in sync | outer catch | `Result.retry()` |

## 5.2 Retry algorithm (exact)

WorkManager `OneTimeWorkRequest` with `BackoffPolicy.EXPONENTIAL`, initial delay `WorkRequest.MIN_BACKOFF_MILLIS` (10 000 ms) — delays ≈ 10 s, 20 s, 40 s, 80 s. **Hard cap: `runAttemptCount >= 4` converts any `retry` into `failure`** (TASK-207) so the user sees the error snackbar instead of an invisible eternal loop. Unique work name `"GoogleDriveSync"`, policy `REPLACE`. Network constraint `NetworkType.CONNECTED`.

## 5.3 UI failure states (exact strings)

- Sync failure snackbar: message from `SyncStatus.Error`, `actionLabel = "Retry"`, `SnackbarDuration.Long`; Retry calls `syncWithGoogleDrive()`.
- Re-approval snackbar: `"Google Drive needs re-approval to back up your data."`, `actionLabel = "Approve"`.
- Guest merge failure: `"Failed to import offline designs."`; success: `"Offline designs successfully imported!"`.
- Guest discard success: `"Offline guest data deleted."`.
- Save-conflict dialog (DesignDetailScreen): title `"Design Changed During Edit"`, body `"This design was updated by a sync while you were editing. Save anyway and overwrite those changes?"`, confirm `"Overwrite"`, dismiss `"Cancel"`.
- Generic exception rendering (`e.printStackTrace()`) stays where it exists; do not add new empty catch blocks.

---

# PHASE 6 — SYSTEM PERMISSIONS & BACKGROUND PROCESSING

## 6.1 AndroidManifest.xml — the exact target state is the full file in TASK-103

Declared permissions after TASK-103: `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`. Nothing else — the photo flows use `PickVisualMedia` and `TakePicture` with a FileProvider, which require **no runtime permissions**, so there is no runtime-permission workflow in this app and none may be added. No `uses-feature` entries (camera is optional via implicit intent; adding `uses-feature android.hardware.camera` would shrink device availability — banned).

## 6.2 Background jobs (exact specs)

| Worker | Trigger | Constraints | Schedule | Unique name / policy |
|--------|---------|-------------|----------|----------------------|
| DriveSyncWorker | `syncWithGoogleDrive()` | `NetworkType.CONNECTED` | one-time, exponential backoff 10 s base, cap at attempt 4 | `"GoogleDriveSync"` / `REPLACE` |
| OrphanImageCleanupWorker | repository `init` | `setRequiresBatteryNotLow(true)` | periodic 24 h | `"OrphanImageCleanup"` / `KEEP` |

OrphanImageCleanupWorker safety contract (TASK-211): opens DBs WITHOUT destructive fallback; if ANY `bi_database*.db` fails to open, the run aborts with `Result.success()` and deletes NOTHING; only deletes `img_*.jpg` files older than 24 h that no database references.

---

# PHASE 7 — APP SECURITY & DATA PRIVACY HARDENING

## 7.1 Cryptography (exact algorithms — FROZEN)

- Payload cipher: `AES/GCM/NoPadding`, 256-bit key, 12-byte random IV per encryption, 128-bit auth tag. Wire format `Base64(iv ‖ ciphertext)`, `Base64.NO_WRAP`. (Existing `encryptWithKey`/`decryptWithKey` — unchanged.)
- Key generation: `SecureRandom`, 32 bytes, Base64-encoded.
- Key validation (NEW): `isValidKey(s)` = Base64-decodes to exactly 32 bytes.
- The legacy PBKDF2 passphrase functions (`encrypt`, `decrypt`, `deriveKey`, `ITERATIONS`) are DELETED (BUG-30). Nothing references them.

## 7.2 Key storage & the Drive trust model (documented, deliberate)

Local: `EncryptedSharedPreferences` file `biap_secure_prefs` (MasterKey `AES256_GCM`, key scheme `AES256_SIV`, value scheme `AES256_GCM`). The plaintext fallback file is ABOLISHED: TASK-204 makes `getSecurePrefs` self-heal (delete + recreate the pref file once on failure) and migrates any key found in the old `biap_prefs_fallback` into secure storage, then deletes the fallback file.

Remote: the AES key is escrrowed as `sync_key.txt` inside the Drive `appDataFolder` so the owner's other devices can decrypt. This means **encryption protects the payload in transit and against non-appDataFolder access, but anyone holding a `drive.appdata`-scoped token for this app can read both key and data.** This is the owner-accepted "Option B automated key management" trade-off (no passphrase UX). Haiku MUST NOT "improve" this into a passphrase scheme. TASK-210 hardens it: remote keys are format-validated before adoption, adoption is committed to local storage only after a successful decrypt or upload, the two-fresh-devices race is resolved by re-reading `files[0]` after upload, and a remote key that fails to decrypt the payload while the local key succeeds gets overwritten by the local key.

## 7.3 Backup rules (BUG-23)

`android:allowBackup="true"` stays, now wired to rules: device-to-device/cloud auto-backup EXCLUDES both SharedPreferences files (`biap_secure_prefs.xml` — Keystore-bound, unreadable after restore — and legacy `biap_prefs_fallback.xml`). Databases and images stay included (they restore cleanly; Drive sync reconciles). Exact XML in TASK-104.

## 7.4 Network security

All endpoints are `https://www.googleapis.com` / `https://accounts.google.com` — platform default config already blocks cleartext for targetSdk 36, and **no `network_security_config.xml` is to be created**. Certificate pinning against Google infrastructure is BANNED: Google rotates leaf certs frequently; a pin set would brick sync in the field with no recovery path.

---

# PHASE 8 — THE PRODUCTION EXECUTION QUEUE

Every task below carries the Phase 9 Guardrail Block implicitly. `OLD:` blocks must match the current file byte-for-byte on non-blank lines (blank lines inside a block may differ in trailing whitespace — some source files have trailing spaces on blank lines). `NEW:` blocks replace them exactly. Tasks marked **FULL FILE** replace the entire file with the given content.

---

## GROUP A — SAFETY NET & BUILD SYSTEM

### TASK-000 — Git checkpoint (MANDATORY FIRST)
**Target:** repo root. Run exactly:
```
git add -A
git commit -m "checkpoint: pre-Haiku baseline (Room migration + Drive sync working tree)"
```
Do not push. If the commit fails because git identity is unset, run `git -c user.name="biap-owner" -c user.email="owner@biap.local" commit -m "checkpoint: pre-Haiku baseline (Room migration + Drive sync working tree)"`.

### TASK-101 — `app/build.gradle.kts` (BUG-24) — **FULL FILE**
```kotlin
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
  kotlin("android")
  kotlin("kapt")
}

android {
    namespace = "com.example.biap"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.biap"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // signingConfigs intentionally absent: the owner has not supplied a release keystore.
    // When available, uncomment and fill from a local (git-ignored) keystore.properties:
    // signingConfigs {
    //     create("release") {
    //         storeFile = file("<owner-supplied>.keystore")
    //         storePassword = "<owner-supplied>"
    //         keyAlias = "<owner-supplied>"
    //         keyPassword = "<owner-supplied>"
    //     }
    // }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.okhttp)
  implementation(libs.play.services.auth)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  "kapt"(libs.androidx.room.compiler)

  // WorkManager & Security
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.security.crypto)
}
```

### TASK-102 — `app/proguard-rules.pro` (BUG-24) — **FULL FILE**
```
# --- kotlinx-serialization: keep generated serializers for our models ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.biap.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.biap.**$$serializer { *; }
-keepclassmembers class com.example.biap.** {
    *** Companion;
}

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Google Sign-In reflection surface ---
-keep class com.google.android.gms.auth.** { *; }

# Room 2.8.4, Coil 3, WorkManager and play-services ship consumer rules; no extra rules here.
```

### TASK-103 — `app/src/main/AndroidManifest.xml` (BUG-23) — **FULL FILE**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:fullBackupContent="@xml/backup_rules"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Biap">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>

</manifest>
```

### TASK-104 — Backup rule resources (BUG-23) — **FULL FILES**
`app/src/main/res/xml/backup_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="biap_secure_prefs.xml" />
    <exclude domain="sharedpref" path="biap_prefs_fallback.xml" />
</full-backup-content>
```
`app/src/main/res/xml/data_extraction_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="biap_secure_prefs.xml" />
        <exclude domain="sharedpref" path="biap_prefs_fallback.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="biap_secure_prefs.xml" />
        <exclude domain="sharedpref" path="biap_prefs_fallback.xml" />
    </device-transfer>
</data-extraction-rules>
```
Rationale (do not change): the Keystore master key never restores to a new device, so restored encrypted prefs would be unreadable; databases and `img_*.jpg` files stay included and Drive sync reconciles them.

---

## GROUP B — DATA & SYNC ENGINE

### TASK-201 — NEW FILE `app/src/main/java/com/example/biap/data/LegacyBackupDecoder.kt` (BUG-03) — **FULL FILE**
```kotlin
package com.example.biap.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Decodes a Drive backup payload. Tries the current schema (v2: price in Long paise,
 * updatedAt present) first; on failure falls back to the legacy schema (v1: price as
 * rupee Double, optional fields absent). Returns null only for undecodable garbage.
 */
object LegacyBackupDecoder {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decode(rawJson: String): BusinessIntelligenceData? {
        try {
            return json.decodeFromString<BusinessIntelligenceData>(rawJson)
        } catch (e: Exception) {
            // Fall through to the legacy decoder below.
        }
        return try {
            decodeLegacy(rawJson)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeLegacy(rawJson: String): BusinessIntelligenceData {
        val root = json.parseToJsonElement(rawJson).jsonObject

        val designs = root["designs"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val sizeInventories = obj["sizeInventories"]?.jsonArray?.mapNotNull { invElement ->
                val inv = invElement.jsonObject
                val size = inv["size"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val stock = inv["stock"]?.jsonPrimitive?.intOrNull ?: 0
                // Legacy prices were rupee Doubles (e.g. 1500.5); convert to paise.
                val priceDouble = inv["price"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                SizeInventory(size = size, stock = stock, price = Math.round(priceDouble * 100))
            } ?: emptyList()
            Design(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "",
                imagePath = obj["imagePath"]?.jsonPrimitive?.contentOrNull,
                sizeInventories = sizeInventories,
                customFields = obj["customFields"]?.jsonObject?.mapNotNull { (k, v) ->
                    v.jsonPrimitive.contentOrNull?.let { k to it }
                }?.toMap() ?: emptyMap(),
                karigars = obj["karigars"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                } ?: emptyList(),
                // 0L means "oldest possible" so any local edit wins the LWW merge.
                updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        } ?: emptyList()

        return BusinessIntelligenceData(
            designs = designs,
            globalCustomFields = root["globalCustomFields"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            globalCustomFieldsMetadata = root["globalCustomFieldsMetadata"]?.jsonObject
                ?.mapNotNull { (k, v) -> v.jsonPrimitive.longOrNull?.let { k to it } }?.toMap() ?: emptyMap(),
            deletedDesignIds = root["deletedDesignIds"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            deletedDesigns = root["deletedDesigns"]?.jsonObject
                ?.mapNotNull { (k, v) -> v.jsonPrimitive.longOrNull?.let { k to it } }?.toMap() ?: emptyMap(),
            deletedCustomFields = root["deletedCustomFields"]?.jsonObject
                ?.mapNotNull { (k, v) -> v.jsonPrimitive.longOrNull?.let { k to it } }?.toMap() ?: emptyMap()
        )
    }
}
```

### TASK-202 — NEW FILE `app/src/main/java/com/example/biap/data/SyncMerger.kt` — **FULL FILE**
This is the merge logic currently inlined in `performSyncInternal` (DataRepository.kt lines 358–422), extracted verbatim as a pure, testable object. Semantics MUST NOT change.
```kotlin
package com.example.biap.data

/**
 * Pure Last-Write-Wins merge of two dataset snapshots with deletion tombstones.
 * No I/O, no clocks — fully deterministic and unit-tested by SyncMergerTest.
 */
object SyncMerger {
    fun merge(localData: BusinessIntelligenceData, remoteData: BusinessIntelligenceData): BusinessIntelligenceData {
        // 1. Merge deleted-design tombstones (LWW on deletedAt)
        val localDeleted = localData.deletedDesigns + localData.deletedDesignIds.associateWith { 0L }
        val remoteDeleted = remoteData.deletedDesigns + remoteData.deletedDesignIds.associateWith { 0L }
        val mergedDeleted = (localDeleted.keys + remoteDeleted.keys).associateWith { id ->
            maxOf(localDeleted[id] ?: 0L, remoteDeleted[id] ?: 0L)
        }

        // 2. Merge active designs (LWW on updatedAt), drop those deleted later than their last edit
        val allDesigns = (localData.designs + remoteData.designs)
            .groupBy { it.id }
            .mapValues { (_, designsList) -> designsList.maxByOrNull { it.updatedAt }!! }
        val mergedDesigns = allDesigns.values.filter { design ->
            val deletedAt = mergedDeleted[design.id] ?: -1L
            design.updatedAt > deletedAt
        }
        // Prune tombstones for designs that were recreated/updated after deletion
        val finalDeleted = mergedDeleted.filter { (id, deletedAt) ->
            val design = allDesigns[id]
            design == null || deletedAt >= design.updatedAt
        }

        // 3. Merge global custom fields (LWW with tombstones)
        val mergedDeletedFields = (localData.deletedCustomFields.keys + remoteData.deletedCustomFields.keys)
            .associateWith { field ->
                maxOf(localData.deletedCustomFields[field] ?: 0L, remoteData.deletedCustomFields[field] ?: 0L)
            }
        val localFields = localData.globalCustomFieldsMetadata.toMutableMap()
        localData.globalCustomFields.forEach { field -> localFields.putIfAbsent(field, 0L) }
        val remoteFields = remoteData.globalCustomFieldsMetadata.toMutableMap()
        remoteData.globalCustomFields.forEach { field -> remoteFields.putIfAbsent(field, 0L) }
        val allFields = (localFields.keys + remoteFields.keys).associateWith { field ->
            maxOf(localFields[field] ?: 0L, remoteFields[field] ?: 0L)
        }
        val mergedFields = allFields.filter { (field, updatedAt) ->
            val deletedAt = mergedDeletedFields[field] ?: -1L
            updatedAt > deletedAt
        }
        val finalDeletedFields = mergedDeletedFields.filter { (field, deletedAt) ->
            val updatedAt = allFields[field]
            updatedAt == null || deletedAt >= updatedAt
        }

        return BusinessIntelligenceData(
            designs = mergedDesigns,
            globalCustomFields = mergedFields.keys,
            globalCustomFieldsMetadata = mergedFields,
            deletedDesignIds = finalDeleted.keys,
            deletedDesigns = finalDeleted,
            deletedCustomFields = finalDeletedFields
        )
    }
}
```

### TASK-203 — `data/room/DesignDao.kt`: add three one-shot queries
Insert immediately BEFORE the final closing brace `}` of the interface (after `getAllImagePaths()`):
```kotlin
    @Transaction
    @Query("SELECT * FROM designs")
    suspend fun getAllDesignsOnce(): List<DesignWithInventories>

    @Query("SELECT * FROM deleted_designs")
    suspend fun getAllDeletedDesignsOnce(): List<DeletedDesignEntity>

    @Query("SELECT * FROM deleted_custom_fields")
    suspend fun getAllDeletedCustomFieldsOnce(): List<DeletedCustomFieldEntity>
```
No other changes. Database version stays 3 (no schema change).

### TASK-204 — `util/EncryptionUtils.kt` (BUG-04, BUG-30) — **FULL FILE**
```kotlin
package com.example.biap.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val SECURE_PREFS_FILE = "biap_secure_prefs"
    private const val LEGACY_FALLBACK_PREFS_FILE = "biap_prefs_fallback"
    private const val KEY_PREF_NAME = "sync_key"

    /**
     * Generate a cryptographically secure 256-bit AES key, Base64-encoded.
     */
    fun generateRandomKey(): String {
        val random = SecureRandom()
        val keyBytes = ByteArray(32) // 256 bits
        random.nextBytes(keyBytes)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    /**
     * True only if the string Base64-decodes to exactly 32 bytes (a valid AES-256 key).
     */
    fun isValidKey(rawKeyBase64: String): Boolean {
        return try {
            Base64.decode(rawKeyBase64, Base64.NO_WRAP).size == 32
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encrypts plaintext using a raw Base64-encoded AES-256 key. Output: Base64(iv || ciphertext).
     */
    fun encryptWithKey(plainText: String, rawKeyBase64: String): String {
        val keyBytes = Base64.decode(rawKeyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        val random = SecureRandom()
        val iv = ByteArray(12)
        random.nextBytes(iv)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts ciphertext using a raw Base64-encoded AES-256 key. Throws on tampered/foreign-key input.
     */
    fun decryptWithKey(cipherTextBase64: String, rawKeyBase64: String): String {
        val keyBytes = Base64.decode(rawKeyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        require(combined.size > 12) { "Ciphertext too short" }
        val iv = ByteArray(12)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        val cipherText = ByteArray(combined.size - iv.size)
        System.arraycopy(combined, iv.size, cipherText, 0, cipherText.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun buildSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Opens the encrypted prefs. If unreadable (e.g. restored to a new device where the
     * Keystore master key does not exist), deletes the pref file once and recreates it.
     * There is NO plaintext fallback; if secure storage still fails, this throws and the
     * caller surfaces a sync error instead of silently degrading security.
     */
    fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            buildSecurePrefs(context)
        } catch (e: Exception) {
            context.deleteSharedPreferences(SECURE_PREFS_FILE)
            buildSecurePrefs(context)
        }
    }

    fun getOrGenerateLocalSyncKey(context: Context): String {
        val prefs = getSecurePrefs(context)
        migrateLegacyFallbackKey(context, prefs)
        var key = prefs.getString(KEY_PREF_NAME, null)
        if (key.isNullOrBlank() || !isValidKey(key)) {
            key = generateRandomKey()
            prefs.edit().putString(KEY_PREF_NAME, key).apply()
        }
        return key
    }

    fun saveLocalSyncKey(context: Context, key: String) {
        getSecurePrefs(context).edit().putString(KEY_PREF_NAME, key).apply()
    }

    /**
     * One-time migration: if the abolished plaintext fallback file holds a key and secure
     * storage does not, move it into secure storage; then delete the plaintext file.
     */
    private fun migrateLegacyFallbackKey(context: Context, securePrefs: SharedPreferences) {
        val fallback = context.getSharedPreferences(LEGACY_FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
        val legacyKey = fallback.getString(KEY_PREF_NAME, null)
        if (!legacyKey.isNullOrBlank() && isValidKey(legacyKey) &&
            securePrefs.getString(KEY_PREF_NAME, null).isNullOrBlank()
        ) {
            securePrefs.edit().putString(KEY_PREF_NAME, legacyKey).apply()
        }
        context.deleteSharedPreferences(LEGACY_FALLBACK_PREFS_FILE)
    }
}
```

### TASK-205 — `util/GoogleDriveSyncManager.kt`: query encoding + renameFile
**Edit 1** (searchFile URL — injection/encoding hardening):
OLD:
```kotlin
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$fileName'&fields=files(id,name)"
```
NEW:
```kotlin
        val encodedQuery = java.net.URLEncoder.encode("name='$fileName'", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)"
```
**Edit 2** — insert this function immediately BEFORE the final closing brace `}` of the class:
```kotlin
    /**
     * Renames a Drive file (metadata-only PATCH). Used to quarantine unreadable payloads.
     */
    suspend fun renameFile(accessToken: String, fileId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val body = """{"name": "$newName"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .patch(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }
```

### TASK-206 — `util/AuthManager.kt` (BUG-14) — **FULL FILE**
```kotlin
package com.example.biap.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class AuthManager(private val context: Context) {
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
        .build()

    private val signInClient: GoogleSignInClient by lazy { GoogleSignIn.getClient(context, gso) }

    private val _currentUser = MutableStateFlow<GoogleSignInAccount?>(
        try {
            GoogleSignIn.getLastSignedInAccount(context)
        } catch (e: Exception) {
            null
        }
    )
    val currentUser: StateFlow<GoogleSignInAccount?> = _currentUser.asStateFlow()

    /**
     * Set when Google requires interactive re-approval of Drive access (BUG-14).
     * MainScreen observes this and offers an "Approve" action that launches the intent.
     */
    private val _pendingAuthIntent = MutableStateFlow<Intent?>(null)
    val pendingAuthIntent: StateFlow<Intent?> = _pendingAuthIntent.asStateFlow()

    fun clearPendingAuthIntent() {
        _pendingAuthIntent.value = null
    }

    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        _currentUser.update { account }
    }

    fun signOut(onComplete: () -> Unit) {
        signInClient.signOut().addOnCompleteListener {
            _currentUser.update { null }
            onComplete()
        }
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = _currentUser.value ?: return@withContext null
        val gAccount = account.account ?: return@withContext null
        try {
            GoogleAuthUtil.getToken(
                context,
                gAccount,
                "oauth2:https://www.googleapis.com/auth/drive.appdata"
            )
        } catch (e: UserRecoverableAuthException) {
            // The user must re-approve access in a system UI; surface it instead of looping.
            _pendingAuthIntent.value = e.intent
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

### TASK-207 — `util/DriveSyncWorker.kt` (BUG-14, retry cap) — **FULL FILE**
```kotlin
package com.example.biap.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.biap.data.DefaultDataRepository

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = DefaultDataRepository.getInstance(applicationContext)
        val authManager = repository.authManager
        val account = authManager.currentUser.value ?: return Result.failure()
        val googleId = account.id ?: return Result.failure()

        val token = authManager.getAccessToken()
        if (token == null) {
            // Interactive re-approval pending, or repeated token failures: stop retrying
            // so the UI shows an actionable error instead of an invisible loop.
            return if (authManager.pendingAuthIntent.value != null || runAttemptCount >= 4) {
                Result.failure()
            } else {
                Result.retry()
            }
        }

        val result = repository.performSyncInternal(token, googleId)
        // Hard retry cap (~10s + 20s + 40s + 80s of backoff already spent).
        // Type check, not equals: WorkManager Result instances are compared by class.
        return if (result is Result.Retry && runAttemptCount >= 4) Result.failure() else result
    }
}
```

### TASK-208 — `util/SizeUtils.kt` (BUG-22) — **FULL FILE**
```kotlin
package com.example.biap.util

object SizeUtils {
    // Canonical ladder: XXL is the canonical spelling of 2XL, 3XL of XXXL.
    private val standardSizes = listOf(
        "XXS", "XS", "S", "M", "L", "XL", "XXL",
        "3XL", "4XL", "5XL", "6XL", "7XL", "8XL", "9XL", "10XL"
    )
    private val aliases = mapOf("2XL" to "XXL", "XXXL" to "3XL")

    /**
     * Sort weight: standard letter sizes first (index order), numeric sizes at 1000+value
     * (clamped below the custom band), custom strings alphabetically from 10000.
     */
    fun getSizeSortWeight(size: String): Double {
        val trimmed = size.trim().uppercase()
        val canonical = aliases[trimmed] ?: trimmed

        val letterIndex = standardSizes.indexOf(canonical)
        if (letterIndex != -1) {
            return letterIndex.toDouble()
        }

        val intValue = canonical.toIntOrNull()
        if (intValue != null) {
            // Clamp so absurd numeric sizes can never collide with the custom band at 10000.
            return 1000.0 + intValue.coerceAtMost(8999)
        }

        var alphaScore = 0.0
        var multiplier = 0.1
        for (char in canonical.take(5)) {
            alphaScore += (char - 'A') * multiplier
            multiplier /= 100.0
        }
        return 10000.0 + alphaScore
    }

    /**
     * Sorts a list of size strings logically.
     */
    fun sortSizes(sizes: List<String>): List<String> {
        return sizes.sortedBy { getSizeSortWeight(it) }
    }
}
```

### TASK-209 — `data/room/Converters.kt` (BUG-26): log parse failures
Replace BOTH catch blocks.
OLD (first):
```kotlin
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
```
NEW (first):
```kotlin
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Corrupt List column, returning empty: $value", e)
            emptyList()
        }
```
OLD (second):
```kotlin
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyMap()
        }
```
NEW (second):
```kotlin
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Corrupt Map column, returning empty: $value", e)
            emptyMap()
        }
```

### TASK-210 — `data/DataRepository.kt` (BUG-01, 02, 03, 05, 06, 11, 12, 13) — 10 surgical edits, in order

**Edit 1 — imports.** After the existing line `import kotlinx.coroutines.flow.first`, insert:
```kotlin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
```

**Edit 2 — interface.** OLD:
```kotlin
    // Guest merge operations
    suspend fun hasGuestData(): Boolean
    suspend fun mergeGuestData(): Boolean
```
NEW:
```kotlin
    // Guest merge operations
    suspend fun hasGuestData(): Boolean
    suspend fun mergeGuestData(): Boolean
    suspend fun clearGuestData(): Boolean
```

**Edit 3 — DAO holder becomes a flow (BUG-02).** OLD:
```kotlin
    private var currentDb: AppDatabase? = null
    private var currentDao: DesignDao? = null
    private var dbCollectionJob: Job? = null
```
NEW:
```kotlin
    private var currentDb: AppDatabase? = null
    private val currentDaoFlow = MutableStateFlow<DesignDao?>(null)
    private var dbCollectionJob: Job? = null
```

**Edit 4 — init block assignments.** OLD:
```kotlin
                    currentDb?.close()
                    currentDb = null
                    currentDao = null
                    driveFileId = null
```
NEW:
```kotlin
                    currentDb?.close()
                    currentDb = null
                    currentDaoFlow.value = null
                    driveFileId = null
```
OLD:
```kotlin
                    val dao = db.designDao()
                    currentDao = dao
```
NEW:
```kotlin
                    val dao = db.designDao()
                    currentDaoFlow.value = dao
```

**Edit 5 — helpers.** Insert these two functions immediately AFTER the closing brace of `openDatabaseForUser` and BEFORE `triggerLocalBackup`:
```kotlin
    /**
     * Waits briefly for the active account's DAO (it is null for a moment during
     * account switches). BUG-02 fix: writes no longer silently vanish in that window.
     */
    private suspend fun awaitDao(timeoutMs: Long = 5_000): DesignDao? =
        withTimeoutOrNull(timeoutMs) { currentDaoFlow.filterNotNull().first() }

    /**
     * BUG-01 fix: sync snapshots are read from the database, never from the UI StateFlow
     * (which can be empty right after an account switch and would nuke the merge).
     */
    private suspend fun snapshotFromDao(dao: DesignDao): BusinessIntelligenceData {
        val designs = dao.getAllDesignsOnce()
        val globals = dao.getAllGlobalCustomFields()
        val deletedDesigns = dao.getAllDeletedDesignsOnce()
        val deletedFields = dao.getAllDeletedCustomFieldsOnce()
        return BusinessIntelligenceData(
            designs = designs.map { it.toDomain() },
            globalCustomFields = globals.map { it.field }.toSet(),
            globalCustomFieldsMetadata = globals.associate { it.field to it.updatedAt },
            deletedDesignIds = deletedDesigns.map { it.designId }.toSet(),
            deletedDesigns = deletedDesigns.associate { it.designId to it.deletedAt },
            deletedCustomFields = deletedFields.associate { it.field to it.deletedAt }
        )
    }
```

**Edit 6 — replace the ENTIRE `performSyncInternal` function** (from `suspend fun performSyncInternal` through its closing `}` before `override fun signOut`) with:
```kotlin
    suspend fun performSyncInternal(token: String, googleId: String): ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            val dao = awaitDao(timeoutMs = 10_000) ?: return@withContext ListenableWorker.Result.failure()

            // Guard: the open database must belong to the account this sync was requested for.
            if (authManager.currentUser.value?.id != googleId) {
                return@withContext ListenableWorker.Result.failure()
            }

            // --- Key management: validated and race-safe (BUG-05) ---
            val localKey = EncryptionUtils.getOrGenerateLocalSyncKey(context)
            var keyFileId = driveSyncManager.searchFile(token, "sync_key.txt")
            if (keyFileId == null) {
                driveSyncManager.uploadFile(token, localKey, null, "sync_key.txt")
                // Re-read so two devices racing here converge on the same files[0] winner.
                keyFileId = driveSyncManager.searchFile(token, "sync_key.txt")
            }
            var candidateKey = localKey
            if (keyFileId != null) {
                val remoteKey = driveSyncManager.downloadFile(token, keyFileId)?.trim()
                if (!remoteKey.isNullOrBlank() && EncryptionUtils.isValidKey(remoteKey)) {
                    candidateKey = remoteKey
                }
            }

            val fileId = driveSyncManager.searchFile(token, "bi_data.json.enc")
            driveFileId = fileId
            val localData = snapshotFromDao(dao)

            if (fileId == null) {
                // First upload for this Drive.
                val encrypted = EncryptionUtils.encryptWithKey(json.encodeToString(localData), candidateKey)
                driveFileId = driveSyncManager.uploadFile(token, encrypted, null, "bi_data.json.enc")
                EncryptionUtils.saveLocalSyncKey(context, candidateKey)
                triggerLocalBackup()
                return@withContext ListenableWorker.Result.success()
            }

            val encryptedContent = driveSyncManager.downloadFile(token, fileId)
            if (encryptedContent.isNullOrBlank()) {
                // Self-heal a zero-byte artifact left by an interrupted upload (BUG-06).
                val encrypted = EncryptionUtils.encryptWithKey(json.encodeToString(localData), candidateKey)
                driveSyncManager.uploadFile(token, encrypted, fileId, "bi_data.json.enc")
                EncryptionUtils.saveLocalSyncKey(context, candidateKey)
                triggerLocalBackup()
                return@withContext ListenableWorker.Result.success()
            }

            // Decrypt: candidate (remote) key first, then the local key (BUG-05).
            var usedKey = candidateKey
            var decryptedJson = try {
                EncryptionUtils.decryptWithKey(encryptedContent, candidateKey)
            } catch (e: Exception) {
                null
            }
            if (decryptedJson == null && candidateKey != localKey) {
                decryptedJson = try {
                    EncryptionUtils.decryptWithKey(encryptedContent, localKey)
                } catch (e: Exception) {
                    null
                }
                if (decryptedJson != null) {
                    usedKey = localKey
                    if (keyFileId != null) {
                        // The escrowed key is wrong for the real payload; repair it.
                        driveSyncManager.uploadFile(token, localKey, keyFileId, "sync_key.txt")
                    }
                }
            }

            val remoteData = if (decryptedJson != null) LegacyBackupDecoder.decode(decryptedJson) else null

            if (remoteData == null) {
                // Unreadable payload (legacy key scheme or corruption): quarantine it,
                // then start fresh from the local snapshot (BUG-03 terminal case).
                driveSyncManager.renameFile(token, fileId, "bi_data.json.enc.unreadable_${System.currentTimeMillis()}")
                val encrypted = EncryptionUtils.encryptWithKey(json.encodeToString(localData), usedKey)
                driveFileId = driveSyncManager.uploadFile(token, encrypted, null, "bi_data.json.enc")
                EncryptionUtils.saveLocalSyncKey(context, usedKey)
                triggerLocalBackup()
                return@withContext ListenableWorker.Result.success()
            }

            val mergedData = SyncMerger.merge(localData, remoteData)
            dao.importData(mergedData)
            val reEncrypted = EncryptionUtils.encryptWithKey(json.encodeToString(mergedData), usedKey)
            driveSyncManager.uploadFile(token, reEncrypted, fileId, "bi_data.json.enc")
            EncryptionUtils.saveLocalSyncKey(context, usedKey)
            triggerLocalBackup()
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            ListenableWorker.Result.retry()
        }
    }
```

**Edit 7 — the four write methods (BUG-02).** In `addOrUpdateDesign`, `deleteDesign`, `addGlobalCustomField`, `removeGlobalCustomField`, replace each occurrence of:
```kotlin
            val dao = currentDao ?: return@launch
```
with:
```kotlin
            val dao = awaitDao() ?: return@launch
```
(4 occurrences total.)

**Edit 8 — replace the ENTIRE `hasGuestData` function (BUG-12)** with:
```kotlin
    override suspend fun hasGuestData(): Boolean = withContext(Dispatchers.IO) {
        // Only meaningful while signed in: in guest mode "bi_database.db" IS the active DB.
        if (authManager.currentUser.value == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        var guestDb: AppDatabase? = null
        try {
            guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db").build()
            guestDb.designDao().getAllDesignsOnce().isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            guestDb?.close()
        }
    }
```

**Edit 9 — replace the ENTIRE `mergeGuestData` function (BUG-13)** with:
```kotlin
    override suspend fun mergeGuestData(): Boolean = withContext(Dispatchers.IO) {
        if (authManager.currentUser.value == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        dbLock.withLock {
            val activeDao = awaitDao() ?: return@withContext false
            var guestDb: AppDatabase? = null
            try {
                guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db").build()
                val guestDao = guestDb.designDao()

                val designs = guestDao.getAllDesignsOnce()
                val globals = guestDao.getAllGlobalCustomFields()
                val deletedDesigns = guestDao.getAllDeletedDesignsOnce()
                val deletedCustomFields = guestDao.getAllDeletedCustomFieldsOnce()

                for (designWithInv in designs) {
                    val updatedDesign = designWithInv.design.copy(updatedAt = System.currentTimeMillis())
                    activeDao.insertDesign(updatedDesign)
                    activeDao.insertInventories(designWithInv.inventories.map { it.copy(id = 0) })
                }
                activeDao.insertGlobalCustomFields(globals.map { it.copy(updatedAt = System.currentTimeMillis()) })
                // Preserve ORIGINAL tombstone timestamps (BUG-13): restamping to "now" would
                // out-vote legitimately newer edits from other devices in the next LWW merge.
                activeDao.insertDeletedDesigns(deletedDesigns)
                activeDao.insertDeletedCustomFields(deletedCustomFields)

                guestDao.deleteAllDesigns()
                guestDao.deleteAllInventories()
                guestDao.deleteAllGlobalCustomFields()
                guestDao.deleteAllDeletedDesigns()
                guestDao.deleteAllDeletedCustomFields()
                triggerLocalBackup()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                guestDb?.close()
            }
        }
    }
```

**Edit 10 — add `clearGuestData` (BUG-11)** immediately AFTER the new `mergeGuestData`, before the class closing brace:
```kotlin
    override suspend fun clearGuestData(): Boolean = withContext(Dispatchers.IO) {
        // Only valid while signed in: in guest mode "bi_database.db" IS the active database.
        if (authManager.currentUser.value == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext true
        var guestDb: AppDatabase? = null
        try {
            guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db").build()
            val dao = guestDb.designDao()
            dao.deleteAllDesigns()
            dao.deleteAllInventories()
            dao.deleteAllGlobalCustomFields()
            dao.deleteAllDeletedDesigns()
            dao.deleteAllDeletedCustomFields()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            guestDb?.close()
        }
    }
```
Note: guest-DB opens in Edits 8–10 deliberately have NO `fallbackToDestructiveMigration()` — a helper flow must never wipe a database. If the guest DB has a mismatched schema the call returns false and the data stays untouched.

### TASK-211 — `util/OrphanImageCleanupWorker.kt` (BUG-07) — **FULL FILE**
```kotlin
package com.example.biap.util

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.biap.data.room.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OrphanImageCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val dbFolder = context.getDatabasePath("bi_database.db").parentFile
                ?: return@withContext Result.failure()

            // 1. Find all user database files
            val dbFiles = dbFolder.listFiles { file ->
                file.name.startsWith("bi_database") && file.name.endsWith(".db")
            } ?: emptyArray()

            val activeImagePaths = mutableSetOf<String>()

            // 2. Collect referenced imagePath values from EVERY database
            for (dbFile in dbFiles) {
                var db: AppDatabase? = null
                try {
                    // No destructive fallback: a cleanup job must never wipe a database.
                    db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name).build()
                    val paths = db.designDao().getAllImagePaths()
                    for (path in paths) {
                        activeImagePaths.add(path)
                        activeImagePaths.add(File(path).name)
                    }
                } catch (e: Exception) {
                    // If ANY database cannot be opened, its image references are unknown.
                    // Abort without deleting anything rather than misclassify them as orphans.
                    e.printStackTrace()
                    return@withContext Result.success()
                } finally {
                    db?.close()
                }
            }

            // 3. Scan filesDir for design photos
            val filesDir = context.filesDir
            val images = filesDir.listFiles { file ->
                file.name.startsWith("img_") && file.name.endsWith(".jpg")
            } ?: emptyArray()

            // 4. Delete unreferenced photos older than 24 h (grace period: a photo copied
            //    moments ago may not be committed to Room yet)
            val minAgeMillis = 24L * 60L * 60L * 1000L
            val now = System.currentTimeMillis()
            for (imageFile in images) {
                if (now - imageFile.lastModified() < minAgeMillis) continue
                if (imageFile.name !in activeImagePaths && imageFile.absolutePath !in activeImagePaths) {
                    imageFile.delete()
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
```

---

## GROUP C — THEME, MATERIAL 3 EXPRESSIVE & SCREENS

### TASK-301 — `theme/Color.kt` addition + `theme/Theme.kt` (BUG-20, BUG-28, M3E-1, M3E-2)

**Part 1 — `theme/Color.kt`**: append at the end of the file:
```kotlin
// Decorative glow gradient endpoints (referenced by AtelierTheme brushes)
val HeritageGlowGreyLight = Color(0xFFD1CFC7)   // Light Outline Grey
val HeritageGlowCharcoalDark = Color(0xFF3C3D40) // Muted Charcoal
```

**Part 2 — `theme/Theme.kt`** — **FULL FILE**:
```kotlin
package com.example.biap.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.biap.ui.PerformanceProfile
import com.example.biap.ui.rememberPerformanceProfile

private val DarkColorScheme = darkColorScheme(
    primary = HeritageDarkPrimary,
    onPrimary = HeritageDarkOnPrimary,
    primaryContainer = HeritageDarkPrimaryContainer,
    onPrimaryContainer = HeritageDarkOnPrimaryContainer,
    secondary = HeritageDarkSecondary,
    onSecondary = HeritageDarkOnSecondary,
    tertiary = HeritageDarkTertiary,
    onTertiary = HeritageDarkOnTertiary,
    background = HeritageDarkBackground,
    onBackground = HeritageDarkOnBackground,
    surface = HeritageDarkSurface,
    onSurface = HeritageDarkOnSurface,
    surfaceVariant = HeritageDarkSurfaceVariant,
    onSurfaceVariant = HeritageDarkOnSurfaceVariant,
    outline = HeritageDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = HeritageLightPrimary,
    onPrimary = HeritageLightOnPrimary,
    primaryContainer = HeritageLightPrimaryContainer,
    onPrimaryContainer = HeritageLightOnPrimaryContainer,
    secondary = HeritageLightSecondary,
    onSecondary = HeritageLightOnSecondary,
    tertiary = HeritageLightTertiary,
    onTertiary = HeritageLightOnTertiary,
    background = HeritageLightBackground,
    onBackground = HeritageLightOnBackground,
    surface = HeritageLightSurface,
    onSurface = HeritageLightOnSurface,
    surfaceVariant = HeritageLightSurfaceVariant,
    onSurfaceVariant = HeritageLightOnSurfaceVariant,
    outline = HeritageLightOutline
)

// BUG-20 fix: derive the warning color from the APPLIED scheme, not the system setting.
val ColorScheme.warning: Color
    get() = if (background.luminance() < 0.5f) HeritageDarkWarning else HeritageLightWarning

object AtelierTheme {
    // BUG-28 fix: brushes reference the palette constants so a palette change propagates.
    val lightGlowBrush = Brush.linearGradient(
        colors = listOf(HeritageLightPrimary, HeritageLightSecondary, HeritageGlowGreyLight)
    )
    val darkGlowBrush = Brush.linearGradient(
        colors = listOf(HeritageDarkPrimary, HeritageDarkSecondary, HeritageGlowCharcoalDark)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiapTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color stays OFF to protect the Contemporary Heritage brand identity.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  // M3 Expressive motion app-wide; standard scheme on battery-saver / low-core devices.
  val profile = rememberPerformanceProfile()
  val motionScheme =
    if (profile == PerformanceProfile.LOW_POWER) MotionScheme.standard() else MotionScheme.expressive()

  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    motionScheme = motionScheme,
    typography = Typography,
    content = content
  )
}

object AtelierShapes {
    val CornerSmall = RoundedCornerShape(8.dp)
    val CornerMedium = RoundedCornerShape(12.dp)
    val CornerLarge = RoundedCornerShape(16.dp)
}
```
Note: `ColorScheme.warning` is no longer `@Composable`. Call sites keep working unchanged (they already call it inside composables where a plain property getter is legal).

### TASK-302 — `theme/Type.kt` (BUG-27)
OLD:
```kotlin
// Set of Material typography styles to match Google Apps (Google Sans system font)
```
NEW:
```kotlin
// Contemporary Heritage typography: Serif (editorial luxury) for display/headline/titleLarge,
// SansSerif for body, labels and dense UI text.
```
No other change to this file.

### TASK-303 — `theme/FadingEdges.kt` (BUG-21) — **FULL FILE**
```kotlin
package com.example.biap.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a horizontal fading edge gradient when scrolling is active.
 * Uses BlendMode.DstIn for hardware-accelerated alpha blending.
 * The fade width is clamped to half the container so narrow containers
 * can never degenerate into a fully transparent gradient (BUG-21).
 */
fun Modifier.horizontalFadingEdges(
    scrollState: ScrollState,
    fadeWidth: Dp = 16.dp
): Modifier = this
    // ponytail: unconditional offscreen compositing; gate it only if profiling shows cost
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()

        val width = size.width
        if (width <= 0f) return@drawWithContent

        val fadeWidthPx = fadeWidth.toPx().coerceAtMost(width / 2f)
        if (fadeWidthPx <= 0f) return@drawWithContent

        val canScrollLeft = scrollState.value > 0
        val canScrollRight = scrollState.value < scrollState.maxValue

        if (!canScrollLeft && !canScrollRight) return@drawWithContent

        val leftFadePx = if (canScrollLeft) fadeWidthPx else 0f
        val rightFadePx = if (canScrollRight) fadeWidthPx else 0f

        // With fadeWidthPx <= width/2 these fractions are always ordered correctly.
        val leftFadeFraction = leftFadePx / width
        val rightFadeFraction = 1f - (rightFadePx / width)

        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to if (canScrollLeft) Color.Transparent else Color.Black,
                    leftFadeFraction to Color.Black,
                    rightFadeFraction to Color.Black,
                    1f to if (canScrollRight) Color.Transparent else Color.Black
                ),
                startX = 0f,
                endX = width
            ),
            blendMode = BlendMode.DstIn
        )
    }
```

### TASK-304 — `ui/main/MainScreen.kt` (BUG-11, BUG-14, M3E-3/4/5) — 7 surgical edits

**Edit 1 — opt-in.** OLD: `@OptIn(ExperimentalMaterial3Api::class)` (the one directly above `fun MainScreen`) → NEW: `@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)`

**Edit 2 — performance profile + reauth plumbing.** OLD:
```kotlin
    var showMenu by remember { mutableStateOf(false) }
    var showMergePrompt by remember { mutableStateOf(false) }
```
NEW:
```kotlin
    var showMenu by remember { mutableStateOf(false) }
    var showMergePrompt by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val profile = rememberPerformanceProfile()

    // BUG-14: surface Google's interactive re-approval instead of silently retrying forever.
    val pendingAuthIntent by authManager.pendingAuthIntent.collectAsStateWithLifecycle()
    val reauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        authManager.clearPendingAuthIntent()
        viewModel.syncWithGoogleDrive()
    }
    LaunchedEffect(pendingAuthIntent) {
        val intent = pendingAuthIntent
        if (intent != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Google Drive needs re-approval to back up your data.",
                actionLabel = "Approve",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                reauthLauncher.launch(intent)
            }
        }
    }
```

**Edit 3 — Discard actually discards (BUG-11).** OLD:
```kotlin
            dismissButton = {
                TextButton(
                    onClick = { showMergePrompt = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Discard Guest Data")
                }
            },
```
NEW:
```kotlin
            dismissButton = {
                TextButton(
                    onClick = {
                        showMergePrompt = false
                        showDiscardConfirm = true
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Discard Guest Data")
                }
            },
```
Then, immediately AFTER the closing `)` and `}` of the `if (showMergePrompt) { AlertDialog(...) }` block, insert:
```kotlin
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Delete Offline Designs?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = { Text("This permanently deletes the designs created in the offline guest workspace. This cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        scope.launch {
                            val cleared = viewModel.discardGuestData()
                            snackbarHostState.showSnackbar(
                                if (cleared) "Offline guest data deleted." else "Could not delete offline guest data."
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirm = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Keep")
                }
            },
            shape = AtelierShapes.CornerLarge,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
```

**Edit 4 — emphasized title (M3E-5).** OLD:
```kotlin
                title = { Text("Product Portfolio", fontWeight = FontWeight.Bold) },
```
NEW:
```kotlin
                title = { Text("Product Portfolio", style = MaterialTheme.typography.titleLargeEmphasized) },
```

**Edit 5 — expressive sync spinner (M3E-3).** OLD:
```kotlin
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
```
NEW:
```kotlin
                            if (isSyncing) {
                                if (profile == PerformanceProfile.LOW_POWER) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    LoadingIndicator(modifier = Modifier.size(28.dp))
                                }
                            } else {
```

**Edit 6 — expressive loading state + wavy sync bar (M3E-3/4).** OLD:
```kotlin
            MainScreenUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MainScreenUiState.Success -> {
                val data = (state as MainScreenUiState.Success).data
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    SearchBarField(
```
NEW:
```kotlin
            MainScreenUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (profile == PerformanceProfile.LOW_POWER) {
                        CircularProgressIndicator()
                    } else {
                        LoadingIndicator()
                    }
                }
            }
            is MainScreenUiState.Success -> {
                val data = (state as MainScreenUiState.Success).data
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    if (isSyncing) {
                        if (profile == PerformanceProfile.LOW_POWER) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    SearchBarField(
```

**Edit 7 — no other changes.** `DesignCard`, `SearchBarField`, sign-in launcher and menus stay byte-identical. (`androidx.compose.material3.*` is already wildcard-imported, so `LoadingIndicator`, `LinearWavyProgressIndicator`, `ExperimentalMaterial3ExpressiveApi` resolve without new imports.)

### TASK-305 — `ui/main/MainScreenViewModel.kt`: expose discard
OLD:
```kotlin
  suspend fun mergeGuestData(): Boolean {
    return dataRepository.mergeGuestData()
  }
```
NEW:
```kotlin
  suspend fun mergeGuestData(): Boolean {
    return dataRepository.mergeGuestData()
  }

  suspend fun discardGuestData(): Boolean {
    return dataRepository.clearGuestData()
  }
```

### TASK-306 — `ui/design/AddDesignViewModel.kt` (BUG-18, BUG-19)

**Edit 1** — OLD:
```kotlin
class AddDesignViewModel(private val repository: DataRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddDesignUiState())
```
NEW:
```kotlin
class AddDesignViewModel(private val repository: DataRepository) : ViewModel() {
    companion object {
        const val DEFAULT_CATEGORY = "Salwar Kameez"
    }

    private val _uiState = MutableStateFlow(AddDesignUiState())
```
**Edit 2** — OLD: `    val category: String = "Salwar Kameez",` → NEW: `    val category: String = AddDesignViewModel.DEFAULT_CATEGORY,`

**Edit 3 — replace the ENTIRE `updateSizeInventory` function (BUG-18)** with:
```kotlin
    fun updateSizeInventory(size: String, stock: Int, price: Long, enabled: Boolean, restoreFromCache: Boolean = false) {
        _uiState.update { current ->
            val updated = current.sizes.toMutableList()
            val existing = updated.find { it.size == size }
            updated.removeAll { it.size == size }
            if (enabled) {
                // Restore cached stock/price ONLY on an explicit chip re-enable — never while
                // the user is typing (BUG-18: clearing both fields to 0 must stay 0).
                val cached = sizeCache[size]
                val newItem = if (restoreFromCache && cached != null) cached else SizeInventory(size, stock, price)
                updated.add(newItem)
                if (newItem.stock > 0 || newItem.price > 0L) {
                    sizeCache[size] = newItem
                }
            } else {
                existing?.let { sizeCache[size] = it }
            }
            current.copy(sizes = updated)
        }
    }
```

### TASK-307 — `ui/design/AddDesignScreen.kt` (BUG-18, BUG-19)

**Edit 1 — form-dirty includes category (BUG-19).** OLD:
```kotlin
    val isFormDirty = remember(state) {
        state.name.isNotEmpty() ||
        state.imagePath != null ||
        state.sizes.isNotEmpty() ||
        state.karigars.isNotEmpty() ||
        state.customFields.values.any { it.isNotEmpty() }
    }
```
NEW:
```kotlin
    val isFormDirty = remember(state) {
        state.name.isNotEmpty() ||
        state.category != AddDesignViewModel.DEFAULT_CATEGORY ||
        state.imagePath != null ||
        state.sizes.isNotEmpty() ||
        state.karigars.isNotEmpty() ||
        state.customFields.values.any { it.isNotEmpty() }
    }
```
**Edit 2 — chip re-enable restores from cache (BUG-18).** OLD:
```kotlin
                                    } else {
                                        viewModel.updateSizeInventory(size, 0, 0L, true)
                                    }
```
NEW:
```kotlin
                                    } else {
                                        viewModel.updateSizeInventory(size, 0, 0L, true, restoreFromCache = true)
                                    }
```
**Edit 3 — custom-size dialog enable also goes through the cache path.** OLD:
```kotlin
                                            viewModel.addAvailableSize(normalizedSize)
                                            viewModel.updateSizeInventory(normalizedSize, 0, 0L, true)
```
NEW:
```kotlin
                                            viewModel.addAvailableSize(normalizedSize)
                                            viewModel.updateSizeInventory(normalizedSize, 0, 0L, true, restoreFromCache = true)
```
(The stock/price text-field `updateSizeInventory(size, ..., true)` calls are NOT modified — they use the default `restoreFromCache = false`.)

### TASK-308 — `ui/design/DesignDetailScreen.kt` (BUG-08, 09, 10, 15, 16, 17, M3E-3) — 8 surgical edits

**Edit 1 (BUG-08).** OLD:
```kotlin
    return viewModel { DesignDetailViewModel(designId, DefaultDataRepository(context)) }
```
NEW:
```kotlin
    return viewModel { DesignDetailViewModel(designId, DefaultDataRepository.getInstance(context)) }
```

**Edit 2 — opt-in.** OLD: `@OptIn(ExperimentalMaterial3Api::class)` (above `fun DesignDetailScreen`) → NEW: `@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)`

**Edit 3 (BUG-15).** OLD:
```kotlin
    var showSourceSelector by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
```
NEW:
```kotlin
    var showSourceSelector by remember { mutableStateOf(false) }
    var tempPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val tempPhotoUri = remember(tempPhotoUriString) {
        tempPhotoUriString?.let { Uri.parse(it) }
    }
    var editBaseUpdatedAt by rememberSaveable { mutableStateOf(0L) }
    var showConflictDialog by remember { mutableStateOf(false) }
```
Also add the import `import androidx.compose.runtime.saveable.rememberSaveable` after `import androidx.compose.runtime.*`, and in the camera-source button OLD `tempPhotoUri = uri` → NEW `tempPhotoUriString = uri.toString()`.

**Edit 4 — capture the edit baseline (BUG-17).** OLD:
```kotlin
            LaunchedEffect(isEditing) {
                if (isEditing) {
                    editName = design.name
```
NEW:
```kotlin
            LaunchedEffect(isEditing) {
                if (isEditing) {
                    editBaseUpdatedAt = design.updatedAt
                    editName = design.name
```

**Edit 5 — conflict-guarded save (BUG-17).** OLD:
```kotlin
                            if (isEditing) {
                                IconButton(
                                    onClick = {
                                        if (editName.isNotBlank()) {
                                            val updated = design.copy(
                                                name = editName.trim(),
                                                category = editCategory.trim(),
                                                imagePath = editImagePath,
                                                sizeInventories = editSizes,
                                                customFields = editCustomFields.filterKeys { it.isNotBlank() },
                                                karigars = editKarigars
                                            )
                                            viewModel.updateDesign(updated)
                                            isEditing = false
                                        }
                                    },
                                    enabled = editName.isNotBlank()
                                ) {
```
NEW:
```kotlin
                            if (isEditing) {
                                IconButton(
                                    onClick = {
                                        if (editName.isNotBlank()) {
                                            if (design.updatedAt != editBaseUpdatedAt) {
                                                showConflictDialog = true
                                            } else {
                                                val updated = design.copy(
                                                    name = editName.trim(),
                                                    category = editCategory.trim(),
                                                    imagePath = editImagePath,
                                                    sizeInventories = editSizes,
                                                    customFields = editCustomFields.filterKeys { it.isNotBlank() },
                                                    karigars = editKarigars
                                                )
                                                viewModel.updateDesign(updated)
                                                isEditing = false
                                            }
                                        }
                                    },
                                    enabled = editName.isNotBlank()
                                ) {
```
Then insert the conflict dialog immediately AFTER the closing `}` of the `LaunchedEffect(isEditing) { ... }` block (still inside the `Success` branch, before `Scaffold(`). Its confirm button repeats the same save body deliberately:
```kotlin
            if (showConflictDialog) {
                AlertDialog(
                    onDismissRequest = { showConflictDialog = false },
                    title = { Text("Design Changed During Edit") },
                    text = { Text("This design was updated by a sync while you were editing. Save anyway and overwrite those changes?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showConflictDialog = false
                                val updated = design.copy(
                                    name = editName.trim(),
                                    category = editCategory.trim(),
                                    imagePath = editImagePath,
                                    sizeInventories = editSizes,
                                    customFields = editCustomFields.filterKeys { it.isNotBlank() },
                                    karigars = editKarigars
                                )
                                viewModel.updateDesign(updated)
                                isEditing = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Overwrite")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConflictDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
```
**Edit 6 (BUG-10).** OLD:
```kotlin
                                painter = rememberAsyncImagePainter(File(imagePathToUse)),
```
NEW:
```kotlin
                                painter = rememberAsyncImagePainter(FileUtils.resolveImageFile(context, imagePathToUse)),
```

**Edit 7 (BUG-16).** OLD:
```kotlin
                                    val sortedInventories = design.sizeInventories.sortedBy { SizeUtils.getSizeSortWeight(it.size) }
                                    var selectedSizeIndex by remember(design) { mutableStateOf(0) }
                                    
                                    if (selectedSizeIndex >= sortedInventories.size) {
                                        selectedSizeIndex = 0
                                    }
                                    
                                    val activeInventory = sortedInventories.getOrNull(selectedSizeIndex)
```
NEW:
```kotlin
                                    val sortedInventories = design.sizeInventories.sortedBy { SizeUtils.getSizeSortWeight(it.size) }
                                    // Keyed on the design ID (not the object) so a background sync
                                    // refresh does not reset the user's selection; clamped as a
                                    // value instead of writing state during composition (BUG-16).
                                    var selectedSizeIndexRaw by remember(design.id) { mutableStateOf(0) }
                                    val selectedSizeIndex = selectedSizeIndexRaw.coerceIn(0, (sortedInventories.size - 1).coerceAtLeast(0))

                                    val activeInventory = sortedInventories.getOrNull(selectedSizeIndex)
```
And in the chip row below it, OLD: `onClick = { selectedSizeIndex = index },` → NEW: `onClick = { selectedSizeIndexRaw = index },`

**Edit 8 (BUG-09 + M3E-3).** OLD:
```kotlin
                                                    Text(
                                                        text = "₹${activeInventory.price}",
```
NEW:
```kotlin
                                                    Text(
                                                        text = "₹${FileUtils.formatPrice(activeInventory.price)}",
```
And in the Loading scaffold, OLD: `CircularProgressIndicator()` → NEW: `LoadingIndicator()` (single occurrence in this file).

### TASK-309 — `ui/main/SplashScreen.kt` (BUG-25) — 2 edits

**Edit 1 — timing.** OLD:
```kotlin
        if (profile == PerformanceProfile.LOW_POWER) {
            alphaAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
            delay(300)
        } else {
            // Animate the logo fade-in and subtle scale-up
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200)
            )
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 1200)
            )
            delay(1500)
        }
```
NEW:
```kotlin
        if (profile == PerformanceProfile.LOW_POWER) {
            alphaAnim.snapTo(1f)
            scaleAnim.snapTo(1f)
            delay(150)
        } else {
            // Animate the logo fade-in and subtle scale-up
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600)
            )
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 600)
            )
            delay(500)
        }
```
**Edit 2 — delete the footer credit.** Delete this entire block (including both preceding blank-line comments):
```kotlin
        // Bottom footer credit
        Text(
            text = "DESIGN SYSTEM MOCKED IN STITCH BY GOOGLE",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            color = Color(0xFFF5F2E9).copy(alpha = 0.3f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        )
```

---

## GROUP D — TESTS & FINAL VERIFICATION

### TASK-401 — `app/src/test/java/com/example/biap/util/InventoryLogicTest.kt`: align with TASK-208
OLD:
```kotlin
        assertEquals(6.0, SizeUtils.getSizeSortWeight("XXL"), 0.001)
        assertEquals(8.0, SizeUtils.getSizeSortWeight("3XL"), 0.001)
```
NEW:
```kotlin
        assertEquals(6.0, SizeUtils.getSizeSortWeight("XXL"), 0.001)
        assertEquals(6.0, SizeUtils.getSizeSortWeight("2XL"), 0.001)  // alias of XXL
        assertEquals(7.0, SizeUtils.getSizeSortWeight("3XL"), 0.001)
        assertEquals(7.0, SizeUtils.getSizeSortWeight("XXXL"), 0.001) // alias of 3XL
```
No other changes (`testParsePriceToCents` and `testSortSizes` still pass unmodified).

### TASK-402 — `app/src/test/java/com/example/biap/ui/main/MainScreenViewModelTest.kt` (BUG-29) — **FULL FILE**
```kotlin
package com.example.biap.ui.main

import com.example.biap.data.BusinessIntelligenceData
import com.example.biap.data.DataRepository
import com.example.biap.data.Design
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.first())
  }

  @Test
  fun uiState_searchQuery_filtersByName() = runTest {
    val data = BusinessIntelligenceData(
      designs = listOf(
        Design(id = "1", name = "Rose Kurti", category = "Kurti", updatedAt = 1L),
        Design(id = "2", name = "Blue Anarkali", category = "Anarkali", updatedAt = 1L)
      )
    )
    val viewModel = MainScreenViewModel(FakeMyModelRepository(data))
    viewModel.updateSearchQuery("rose")
    val success = viewModel.uiState.first { it is MainScreenUiState.Success } as MainScreenUiState.Success
    assertEquals(1, success.data.designs.size)
    assertEquals("Rose Kurti", success.data.designs[0].name)
  }
}

private class FakeMyModelRepository(
  initialData: BusinessIntelligenceData = BusinessIntelligenceData()
) : DataRepository {
  override val biData: StateFlow<BusinessIntelligenceData> = MutableStateFlow(initialData)
  override fun addOrUpdateDesign(design: Design) {}
  override fun deleteDesign(designId: String) {}
  override fun addGlobalCustomField(field: String) {}
  override fun removeGlobalCustomField(field: String) {}

  override val authManager: com.example.biap.util.AuthManager
    get() = throw UnsupportedOperationException("AuthManager not supported in tests")
  override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false)
  override val syncStatus: StateFlow<com.example.biap.data.SyncStatus> = MutableStateFlow(com.example.biap.data.SyncStatus.Idle)
  override fun syncWithGoogleDrive() {}
  override fun resetSyncStatus() {}
  override fun signOut(onComplete: () -> Unit) { onComplete() }
  override suspend fun hasGuestData(): Boolean = false
  override suspend fun mergeGuestData(): Boolean = false
  override suspend fun clearGuestData(): Boolean = true
}
```

### TASK-403 — NEW FILE `app/src/test/java/com/example/biap/data/LegacyBackupDecoderTest.kt` — **FULL FILE**
```kotlin
package com.example.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyBackupDecoderTest {

  @Test
  fun decode_currentSchema_passesThrough() {
    val json = """
      {"designs":[{"id":"a","name":"Kurti A","category":"Kurti",
        "sizeInventories":[{"size":"M","stock":2,"price":150000}],
        "customFields":{},"karigars":[],"updatedAt":5}],
       "globalCustomFields":["Top - Fabric"],
       "globalCustomFieldsMetadata":{"Top - Fabric":5},
       "deletedDesignIds":[],"deletedDesigns":{},"deletedCustomFields":{}}
    """.trimIndent()
    val result = LegacyBackupDecoder.decode(json)
    assertNotNull(result)
    assertEquals(150000L, result!!.designs[0].sizeInventories[0].price)
    assertEquals(5L, result.designs[0].updatedAt)
  }

  @Test
  fun decode_legacyDoublePrices_convertsRupeesToPaise() {
    val json = """
      {"designs":[{"id":"a","name":"Old Design","category":"Lehenga",
        "sizeInventories":[{"size":"L","stock":1,"price":1500.5}]}],
       "globalCustomFields":["Top - Fabric"]}
    """.trimIndent()
    val result = LegacyBackupDecoder.decode(json)
    assertNotNull(result)
    assertEquals(150050L, result!!.designs[0].sizeInventories[0].price)
    assertEquals(0L, result.designs[0].updatedAt)
    assertEquals(setOf("Top - Fabric"), result.globalCustomFields)
  }

  @Test
  fun decode_garbage_returnsNull() {
    assertNull(LegacyBackupDecoder.decode("this is not json at all {{{"))
  }
}
```

### TASK-404 — NEW FILE `app/src/test/java/com/example/biap/data/SyncMergerTest.kt` — **FULL FILE**
```kotlin
package com.example.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerTest {

  private fun design(id: String, name: String, updatedAt: Long) =
    Design(id = id, name = name, category = "Kurti", updatedAt = updatedAt)

  @Test
  fun merge_newerLocalDesignWins() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Local Newer", 200L)))
    val remote = BusinessIntelligenceData(designs = listOf(design("a", "Remote Older", 100L)))
    val merged = SyncMerger.merge(local, remote)
    assertEquals(1, merged.designs.size)
    assertEquals("Local Newer", merged.designs[0].name)
  }

  @Test
  fun merge_newerRemoteDesignWins() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Local Older", 100L)))
    val remote = BusinessIntelligenceData(designs = listOf(design("a", "Remote Newer", 200L)))
    val merged = SyncMerger.merge(local, remote)
    assertEquals("Remote Newer", merged.designs[0].name)
  }

  @Test
  fun merge_tombstoneDeletesOlderDesign() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Edited Before Delete", 100L)))
    val remote = BusinessIntelligenceData(
      deletedDesignIds = setOf("a"),
      deletedDesigns = mapOf("a" to 150L)
    )
    val merged = SyncMerger.merge(local, remote)
    assertTrue(merged.designs.isEmpty())
    assertEquals(150L, merged.deletedDesigns["a"])
  }

  @Test
  fun merge_recreationAfterDeletePrunesTombstone() {
    val local = BusinessIntelligenceData(designs = listOf(design("a", "Recreated", 300L)))
    val remote = BusinessIntelligenceData(
      deletedDesignIds = setOf("a"),
      deletedDesigns = mapOf("a" to 200L)
    )
    val merged = SyncMerger.merge(local, remote)
    assertEquals(1, merged.designs.size)
    assertTrue(merged.deletedDesigns.isEmpty())
  }

  @Test
  fun merge_customFieldTombstoneRemovesOlderField() {
    val local = BusinessIntelligenceData(
      globalCustomFields = setOf("Old Field"),
      globalCustomFieldsMetadata = mapOf("Old Field" to 100L)
    )
    val remote = BusinessIntelligenceData(deletedCustomFields = mapOf("Old Field" to 200L))
    val merged = SyncMerger.merge(local, remote)
    assertTrue(merged.globalCustomFields.isEmpty())
    assertEquals(200L, merged.deletedCustomFields["Old Field"])
  }
}
```

### TASK-500 — Final verification & closing commit
Run from the repo root, in order; ALL must pass:
```
.\gradlew.bat compileDebugKotlin
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```
If `assembleRelease` fails ONLY on R8 with a missing-class warning, add the exact `-dontwarn` line R8 suggests to `proguard-rules.pro` and rerun (this is the single permitted deviation). Then:
```
git add -A
git commit -m "fix: repair sync/data-safety/UI bugs and adopt Material 3 Expressive per HAIKU_OPERATION_MANUAL"
```
Then update `CHANGELOG.md` with a Chronos entry (format per project convention) listing every task executed, and tick the corresponding items in `TASKLIST.md`.

---

# PHASE 9 — HAIKU 4.5 MID-WORK GUARDRAILS (ANTI-DRIFT PROTOCOLS)

**GUARDRAIL BLOCK GRB-1 — applies verbatim to EVERY task above:**

1. **No truncation.** When a task says FULL FILE, output the complete file. When applying surgical edits, the resulting file on disk must be complete — never write `// rest of code remains the same`, `// ...`, `/* unchanged */`, or `// TODO`. If you cannot hold the whole file, re-read it and apply edits in place.
2. **Nomenclature lock.** Every identifier must match Phases 2–4 and the OLD/NEW blocks character-for-character: `currentDaoFlow`, `awaitDao`, `snapshotFromDao`, `SyncMerger.merge`, `LegacyBackupDecoder.decode`, `clearGuestData`, `pendingAuthIntent`, `clearPendingAuthIntent`, `isValidKey`, `renameFile`, `restoreFromCache`, `selectedSizeIndexRaw`, `editBaseUpdatedAt`, `DEFAULT_CATEGORY`, `HeritageGlowGreyLight`, `HeritageGlowCharcoalDark`. Inventing a synonym is a task failure.
3. **No conversational output.** Do not write intros ("Sure, here is…"), outros, or explanations between files. Stream compiler-ready code only. Progress reporting is one line per task: `TASK-NNN done`.
4. **No scope creep.** FROZEN files stay untouched. Banned additions (repeat): Hilt/Koin/Dagger, Retrofit/Ktor, new Gradle modules, build flavors, `network_security_config.xml`, SSL pinning, version bumps in `libs.versions.toml`, new runtime permissions, passphrase encryption UX, Credential Manager migration.
5. **Unresolvable symbol protocol.** If an M3 Expressive symbol (`MaterialExpressiveTheme`, `MotionScheme`, `LoadingIndicator`, `LinearWavyProgressIndicator`, `titleLargeEmphasized`, `ExperimentalMaterial3ExpressiveApi`) does not resolve, STOP the task, report the exact compiler error, and wait — do NOT substitute standard components or invent imports.
6. **OLD-block mismatch protocol.** If an OLD block is not found byte-for-byte, STOP that task and report the actual bytes found. Never fuzzy-match.

---

# PHASE 10 — THE 100% COMPLIANCE VERIFICATION SUITE

## 10.1 Compilation-dependents matrix (verify imports physically exist in the edited file)

| Task | The delivered file MUST import / reference |
|------|--------------------------------------------|
| TASK-201 | `kotlinx.serialization.json.Json`, `jsonObject`, `jsonArray`, `jsonPrimitive`, `contentOrNull`, `doubleOrNull`, `intOrNull`, `longOrNull` — and NOTHING from `android.*` |
| TASK-202 | only `com.example.biap.data.*` types; zero imports needed — file has NO import lines |
| TASK-203 | existing imports suffice (`@Transaction`, `@Query` already imported) |
| TASK-204 | `androidx.security.crypto.EncryptedSharedPreferences`, `androidx.security.crypto.MasterKey`, `android.content.SharedPreferences`; MUST NOT import `SecretKeyFactory`, `PBEKeySpec`, `KeySpec` (legacy code deleted) |
| TASK-205 | `java.net.URLEncoder` referenced fully-qualified inline (no new import line required) |
| TASK-206 | `com.google.android.gms.auth.UserRecoverableAuthException` |
| TASK-207 | unchanged imports; uses `runAttemptCount` (CoroutineWorker property) |
| TASK-210 | `kotlinx.coroutines.flow.filterNotNull`, `kotlinx.coroutines.withTimeoutOrNull`; references `SyncMerger` and `LegacyBackupDecoder` (same package, no import) |
| TASK-301 | `androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`, `MaterialExpressiveTheme`, `MotionScheme`, `androidx.compose.ui.graphics.luminance`, `com.example.biap.ui.PerformanceProfile`, `com.example.biap.ui.rememberPerformanceProfile`; MUST NOT import `androidx.compose.material3.MaterialTheme` |
| TASK-304 | no new import lines (material3 wildcard + existing imports cover everything) |
| TASK-308 | adds exactly one import: `androidx.compose.runtime.saveable.rememberSaveable` |

## 10.2 Signature conformity audit (character-exact; any deviation = task failure)

```kotlin
// DataRepository.kt
interface DataRepository { /* must now contain: */ suspend fun clearGuestData(): Boolean }
private suspend fun awaitDao(timeoutMs: Long = 5_000): DesignDao?
private suspend fun snapshotFromDao(dao: DesignDao): BusinessIntelligenceData
suspend fun performSyncInternal(token: String, googleId: String): ListenableWorker.Result
// SyncMerger.kt
fun merge(localData: BusinessIntelligenceData, remoteData: BusinessIntelligenceData): BusinessIntelligenceData
// LegacyBackupDecoder.kt
fun decode(rawJson: String): BusinessIntelligenceData?
// EncryptionUtils.kt
fun isValidKey(rawKeyBase64: String): Boolean
fun getSecurePrefs(context: Context): SharedPreferences
// GoogleDriveSyncManager.kt
suspend fun renameFile(accessToken: String, fileId: String, newName: String): Boolean
// AuthManager.kt
val pendingAuthIntent: StateFlow<Intent?>
fun clearPendingAuthIntent()
// AddDesignViewModel.kt
fun updateSizeInventory(size: String, stock: Int, price: Long, enabled: Boolean, restoreFromCache: Boolean = false)
// MainScreenViewModel.kt
suspend fun discardGuestData(): Boolean
// SizeUtils.kt
fun getSizeSortWeight(size: String): Double
fun sortSizes(sizes: List<String>): List<String>
```

## 10.3 State completeness check

- [ ] Every field of `AddDesignUiState` (9 fields) is still read by `AddDesignScreen` and written by a ViewModel method.
- [ ] `MainScreen` binds: `uiState` (3 branches), `searchQuery`, `isSyncing` (spinner AND wavy bar), `syncStatus` (snackbar), `currentUser` (menu/sign-in), `pendingAuthIntent` (approve snackbar), `showMergePrompt`, `showDiscardConfirm`.
- [ ] `DesignDetailScreen` binds: `uiState` (3 branches), all six edit buffers, `editBaseUpdatedAt` + `showConflictDialog`, `tempPhotoUriString`, `selectedSizeIndexRaw`.
- [ ] grep audits: `DefaultDataRepository(` → exactly 1 hit (getInstance); `_biData.value` inside `performSyncInternal` → 0 hits; `biap_prefs_fallback` → hits only in `LEGACY_FALLBACK_PREFS_FILE` const + migration fn; `fallbackToDestructiveMigration` → hits ONLY inside `openDatabaseForUser`; `"₹${activeInventory.price}"` → 0 hits; `DESIGN SYSTEM MOCKED` → 0 hits.

## 10.4 Behavioral QA script (emulator, after TASK-500 passes)

1. Cold start → splash ≤ 1.2 s → portfolio grid renders.
2. Add a design: photo from gallery, sizes M + XL with stock/price ₹1,499.50, one karigar, save → card shows "₹1499.5" range formatting.
3. Open detail view → price shows `₹1499.5` (NOT `₹149950`) — BUG-09.
4. Toggle a size chip off then on in Add screen → values restore; set stock and price to 0 manually → they STAY 0 after refocus — BUG-18.
5. Edit only the category in Add screen → back press shows the discard warning — BUG-19.
6. Rotate the device (or enable "Don't keep activities") while the camera is open from Detail edit → photo survives — BUG-15.
7. Sign in with Google → wavy progress bar appears during sync; sync completes with snackbar.
8. With guest designs present, sign in → merge prompt → "Discard Guest Data" → confirm → prompt never reappears — BUG-11.
9. Airplane mode → tap sync → snackbar error with Retry (no infinite silent spinner) — retry cap.
10. Battery saver ON → navigation transitions become fades, spinners become circular — LOW_POWER profile.

## 10.5 Issue-to-verification traceability

Registry BUG-01…BUG-30 → every ID appears in exactly one task header above. Auditor: grep this manual for `BUG-` and confirm each of the 30 IDs maps to a task and each task's verification row passed. The audit is complete only when all 30 are checked, `gradlew test` is green, and the 10-step QA script passes.

---
*End of manual. The shortest path to done is executing it exactly as written.*




