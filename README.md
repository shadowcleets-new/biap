# BIAP

Internal operations app for an Indian D2C ethnic wear atelier (Salwar Kameez & Kurtis).
Android, Kotlin, Jetpack Compose.

Manages the design catalogue — sizes, stock, per-size pricing in paise, karigar
(artisan) assignment and custom garment attributes — with photos, offline-first
storage and cloud backup.

---

## Stack

| | |
|---|---|
| Language / UI | Kotlin 2.1.0, Jetpack Compose, Material 3 |
| Build | AGP 9.2.1, Compose BOM 2026.03.01, `compileSdk` 36, `minSdk` 24 |
| Navigation | Navigation 3 (`1.0.1`) |
| Local storage | Room 2.8.4 (schema v4), per-identity database |
| Auth | Firebase Auth — Email/Password, Phone OTP, Google Sign-In |
| Images | Firebase Storage, versioned objects |
| Backup | Google Drive `appDataFolder`, AES-256-GCM encrypted |
| Background work | WorkManager |

## Modules and layout

```
app/src/main/java/com/sc/biap/
├── MainActivity.kt          auth gate → MainNavigation
├── Navigation.kt            Navigation 3 back stack
├── data/
│   ├── DataRepository.kt    identity, DB lifecycle, Drive sync
│   ├── Design.kt            domain model
│   ├── SyncMerger.kt        LWW merge; image pointer resolves independently
│   └── room/                entities, DAO, migrations
├── theme/                   Contemporary Heritage palette, type, spacing tokens
├── ui/                      main / design screens, DesignImage resolver
└── util/                    ImageStore, workers, encryption, Drive, auth
app/src/debug|release/       per-variant App Check provider
```

## Architecture notes

**Identity.** The Firebase uid is the app identity and keys the Room database
(`bi_database_<md5(uid)>.db`). Google Sign-In is an *authorisation* for Drive
access, not a statement of who the user is.

**Images are mutable, objects are not.** Replacing a photo writes a new key
(`designs/{designId}/{millis}_{nonce}.jpg`) and repoints the design at it, rather
than overwriting in place. A failed upload therefore cannot destroy the live
photo, concurrent replacements cannot corrupt each other, and on-device caches
never serve a stale image. Newest 5 versions retained.

**Uploads are queued.** Saving never blocks on the network — the photo is written
locally and displayed from there while `ImageUploadWorker` catches up. Ordering is
enforced: upload bytes → move pointer → prune.

**Design tokens.** Colour comes from the Heritage palette, spacing from
`AtelierSpacing`, shape from `AtelierShapes`, emphasis from the `*Strong` type
extensions. No hardcoded hex or ad-hoc `fontWeight` in the four main screens.

## Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # minified release
./gradlew testDebugUnitTest    # unit tests
```

Requires `app/google-services.json` and the debug signing SHA-1 registered in the
Firebase project — Phone Auth verifies package name and signing certificate
together, so an unregistered fingerprint fails verification.

## Security rules

`storage.rules` mirrors what is published on the Storage bucket. Currently any
authenticated user may read every design photo — correct for a single
organisation, and tightened to org membership when the org model lands.

## Branches

| branch | contents |
|---|---|
| `main` | current work — restored feature layer, modernised UI, image pipeline |
| `master` | earlier auth-only build |
| `feature/m3-expressive` | pre-existing checkpoint |
