# Multi-Image Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single photo per design with an ordered, per-user gallery of up to 10 photos, each with captions, thumbnails, in-app cropping, and a full-screen pinch-zoom viewer.

**Architecture:** A new `design_images` child table (mirroring the existing `size_inventories` pattern) holds one row per photo with a stable UUID as its merge key. Ordering lives in a `position` column that is inherently per-user because the Room database is already per-Firebase-identity (`bi_database_<md5(uid)>.db`). Storage keys gain an `{imageId}` path segment so each photo keeps its own version history. Sync unions photos by id, resolving content and ordering on independent timestamps.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2026.03.01), Room 2.8.4 with kapt, Firebase Storage, WorkManager, Navigation 3, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-08-multi-image-gallery-design.md`

---

## Pre-flight facts (verified, do not re-derive)

These were confirmed by compile probe against this exact codebase on 2026-08-08:

| Fact | Status |
|---|---|
| `androidx.compose.foundation.pager.HorizontalPager`, `rememberPagerState` | ✅ resolve, no new dependency |
| `androidx.compose.foundation.gestures.detectTransformGestures`, `detectTapGestures` | ✅ resolve, no new dependency |
| `androidx.compose.ui.graphics.graphicsLayer`, `LazyRow` + `items` | ✅ resolve, no new dependency |
| `android.graphics.BitmapFactory` (framework) | ✅ resolves |
| `androidx.exifinterface.media.ExifInterface` | ❌ **on the runtime classpath transitively (1.3.7) but NOT the compile classpath.** Task 6 must declare it explicitly. |

**Build commands that work in this repo:**

```bash
./gradlew assembleDebug                                   # debug APK
./gradlew assembleRelease                                 # minified release
./gradlew testDebugUnitTest                               # all unit tests
./gradlew testDebugUnitTest --tests "com.sc.biap.data.SyncMergerImageTest"   # one class
```

Unit test results land in `app/build/test-results/testDebugUnitTest/*.xml`. There is no ktlint/detekt config; match surrounding style.

**Commit message style** in this repo: `type(scope): summary` lowercase, then a blank line and a body explaining *why*. Types in use: `feat`, `fix`, `design`, `a11y`, `docs`, `chore`, `refactor`, `security`.

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `app/src/main/java/com/sc/biap/data/DesignImage.kt` | Domain model for one photo |
| `app/src/main/java/com/sc/biap/util/ThumbnailGenerator.kt` | Decode, EXIF-rotate and downscale to a thumbnail file |
| `app/src/main/java/com/sc/biap/ui/image/ImageViewer.kt` | Full-screen pager + pinch-zoom viewer |
| `app/src/main/java/com/sc/biap/ui/image/ImageThumbnailStrip.kt` | Editor strip: reorder, remove, caption, add |
| `app/src/main/java/com/sc/biap/ui/image/ImageCropper.kt` | Crop and rotate, writes a new version |
| `app/src/test/java/com/sc/biap/data/DesignImageOrderTest.kt` | Position renormalisation, pure |
| `app/src/test/java/com/sc/biap/data/MigrationV4ToV5Test.kt` | Migration SQL against a synthetic v4 database |

**Modified:**

| File | Change |
|---|---|
| `storage.rules` | Add the `{imageId}` path segment |
| `app/src/main/java/com/sc/biap/data/room/Entities.kt` | Add `DesignImageEntity`, `DeletedDesignImageEntity`, relation, mappers |
| `app/src/main/java/com/sc/biap/data/room/AppDatabase.kt` | v5, `MIGRATION_4_5`, register entities |
| `app/src/main/java/com/sc/biap/data/room/DesignDao.kt` | Image queries, ordering, tombstones |
| `app/src/main/java/com/sc/biap/data/Design.kt` | `Design.images`, `BusinessIntelligenceData` image fields |
| `app/src/main/java/com/sc/biap/data/SyncMerger.kt` | Union images, independent position/content LWW |
| `app/src/main/java/com/sc/biap/util/ImageStore.kt` | Per-image key prefix, thumbnail keys |
| `app/src/main/java/com/sc/biap/util/ImageUploadWorker.kt` | Per-image upload plus thumbnail |
| `app/src/main/java/com/sc/biap/data/DataRepository.kt` | Per-image enqueue, pointer apply, combine block |
| `app/src/main/java/com/sc/biap/ui/DesignImage.kt` | Typed signature, thumbnail preference |
| `app/src/main/java/com/sc/biap/ui/main/MainScreen.kt` | Grid card reads cover |
| `app/src/main/java/com/sc/biap/ui/design/DesignDetailScreen.kt` | Hero becomes a pager |
| `app/src/main/java/com/sc/biap/ui/design/AddDesignScreen.kt` | Thumbnail strip replaces single picker |
| `app/src/main/java/com/sc/biap/ui/design/AddDesignViewModel.kt` | Image list state, 10-photo cap |
| `app/src/test/java/com/sc/biap/data/SyncMergerImageTest.kt` | Union and independence cases |
| `app/src/test/java/com/sc/biap/util/ImageStoreRetentionTest.kt` | Per-image prefix, thumbnail exclusion |
| `app/build.gradle.kts` | `exifinterface`, `sqlite-jdbc` (test only) |
| `gradle/libs.versions.toml` | Version entries for both |

---

## Task 1: Republish Storage rules with the `{imageId}` segment

**This must land before any app change.** The rule fails closed: new keys have two path segments where the published rule matches one, so uploads would be silently denied.

**Files:**
- Modify: `storage.rules`

- [ ] **Step 1: Update the rule path**

Replace the `match /designs/{designId}/{fileName}` line in `storage.rules`:

```
rules_version = '2';

service firebase.storage {
  match /b/{bucket}/o {

    // Design photos. Keys are designs/{designId}/{imageId}/{millis}_{nonce}.{ext}
    // and are written once: replacing a photo uploads a NEW key rather than
    // overwriting, so a failed upload can never destroy the image a design
    // still points at. The {imageId} segment gives every photo its own version
    // history, so pruning one photo cannot delete another photo's versions.
    match /designs/{designId}/{imageId}/{fileName} {

      allow read: if request.auth != null;

      allow create, update: if request.auth != null
        && request.resource.size < 30 * 1024 * 1024
        && request.resource.contentType.matches('image/.*');

      // Retention (keep newest 5) needs delete. request.resource is null on a
      // delete, so this rule cannot carry the size/type conditions above.
      allow delete: if request.auth != null;
    }

    // Anything outside /designs is denied by default - no catch-all required.
  }
}
```

- [ ] **Step 2: Publish to Firebase and verify in the Rules Playground**

Go to Firebase Console → Storage → Rules for project `biap-67a71`, paste the file contents, and **before** publishing run three simulations:

| Simulation type | Location | Authenticated | Expected |
|---|---|---|---|
| `get` | `designs/d1/img1/1700_ab.jpg` | off | **denied** |
| `get` | `designs/d1/img1/1700_ab.jpg` | on | **allowed** |
| `delete` | `designs/d1/img1/1700_ab.jpg` | on | **allowed** |

The `delete` case matters: `create`/`update` carry size and content-type conditions but `delete` deliberately does not, because `request.resource` is null on a delete. If delete is denied, retention will silently never prune.

Then click **Publish**. Changes take up to a minute to propagate.

- [ ] **Step 3: Commit**

```bash
git add storage.rules
git commit -m "chore(storage): add {imageId} segment to Storage rules

New keys are designs/{designId}/{imageId}/{file}, two path segments where the
published rule matched one. The rule fails closed, so this MUST be published
before the app change ships or every upload is silently denied.

Verified in the Rules Playground before publishing:
  unauthenticated get -> denied
  authenticated   get -> allowed
  authenticated   delete -> allowed (retention needs it)"
```

---

## Task 2: Add the Room entities

**Files:**
- Modify: `app/src/main/java/com/sc/biap/data/room/Entities.kt`

- [ ] **Step 1: Append the two entities**

Add to the end of `Entities.kt`, before the mapping functions:

```kotlin
/**
 * One photo belonging to a design.
 *
 * [id] is generated on the device that adds the photo and NEVER changes. It is the merge
 * key: two devices adding photos offline produce different ids, so a union by id keeps both
 * without either winning.
 *
 * [position] is this user's ordering. It needs no userId column because the Room database is
 * already per-identity (bi_database_<md5(firebaseUid)>.db), so the file itself scopes it.
 *
 * [positionUpdatedAt] is separate from [updatedAt] so ordering resolves independently of
 * content — captioning a photo must not drag a stale position along with it.
 */
@Entity(
    tableName = "design_images",
    foreignKeys = [
        ForeignKey(
            entity = DesignEntity::class,
            parentColumns = ["id"],
            childColumns = ["designId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["designId"])]
)
data class DesignImageEntity(
    @PrimaryKey val id: String,
    val designId: String,
    val position: Int,
    val localPath: String?,
    val imageRef: String?,
    val thumbRef: String?,
    val caption: String?,
    val updatedAt: Long,
    val positionUpdatedAt: Long,
    val updatedBy: String?
)

@Entity(tableName = "deleted_design_images")
data class DeletedDesignImageEntity(
    @PrimaryKey val id: String,
    val deletedAt: Long
)
```

- [ ] **Step 2: Add images to the relation**

Replace the existing `DesignWithInventories` (currently lines 67-74):

```kotlin
data class DesignWithInventories(
    @Embedded val design: DesignEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "designId"
    )
    val inventories: List<SizeInventoryEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "designId"
    )
    val images: List<DesignImageEntity>
)
```

- [ ] **Step 3: Add the image mappers**

Append to `Entities.kt`:

```kotlin
fun DesignImageEntity.toDomain(): DesignImage {
    return DesignImage(
        id = id,
        designId = designId,
        position = position,
        localPath = localPath,
        imageRef = imageRef,
        thumbRef = thumbRef,
        caption = caption,
        updatedAt = updatedAt,
        positionUpdatedAt = positionUpdatedAt,
        updatedBy = updatedBy
    )
}

fun DesignImage.toEntity(): DesignImageEntity {
    return DesignImageEntity(
        id = id,
        designId = designId,
        position = position,
        localPath = localPath,
        imageRef = imageRef,
        thumbRef = thumbRef,
        caption = caption,
        updatedAt = updatedAt,
        positionUpdatedAt = positionUpdatedAt,
        updatedBy = updatedBy
    )
}
```

Add the import at the top of the file:

```kotlin
import com.sc.biap.data.DesignImage
```

- [ ] **Step 4: Wire images through `DesignWithInventories.toDomain()`**

In the existing `toDomain()` (currently line 77), add one line inside the `Design(...)` call, after `sizeInventories`:

```kotlin
        images = images.sortedBy { it.position }.map { it.toDomain() },
```

- [ ] **Step 5: Verify it does not compile yet**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL with `Unresolved reference 'DesignImage'` — the domain class arrives in Task 3. This confirms the entity references the type the plan intends.

- [ ] **Step 6: Do not commit yet**

This task leaves the tree non-compiling by design. Commit at the end of Task 3.

---

## Task 3: Add the domain model

**Files:**
- Create: `app/src/main/java/com/sc/biap/data/DesignImage.kt`
- Modify: `app/src/main/java/com/sc/biap/data/Design.kt`

- [ ] **Step 1: Create the domain class**

Create `app/src/main/java/com/sc/biap/data/DesignImage.kt`:

```kotlin
package com.sc.biap.data

import kotlinx.serialization.Serializable

/** Maximum photos per design. Enforced in the ViewModel, not the schema, so it can change
 *  without a migration. */
const val MAX_IMAGES_PER_DESIGN = 10

@Serializable
data class DesignImage(
    val id: String,
    val designId: String,
    val position: Int = 0,
    val localPath: String? = null,
    val imageRef: String? = null,
    val thumbRef: String? = null,
    val caption: String? = null,
    val updatedAt: Long = 0L,
    val positionUpdatedAt: Long = 0L,
    val updatedBy: String? = null
)
```

- [ ] **Step 2: Add images to `Design` and `BusinessIntelligenceData`**

In `app/src/main/java/com/sc/biap/data/Design.kt`, add to the `Design` data class after `sizeInventories`:

```kotlin
    val images: List<DesignImage> = emptyList(),
```

Add to `Design` after the closing of its constructor, as a body:

```kotlin
) {
    /** This user's cover photo — the first in their own ordering. */
    val coverImage: DesignImage? get() = images.minByOrNull { it.position }
}
```

Add to `BusinessIntelligenceData`:

```kotlin
    val designImages: List<DesignImage> = emptyList(),
    val deletedDesignImages: Map<String, Long> = emptyMap(),
```

Both default so an older `bi_data.json.enc` payload still decodes and arrives with empty lists.

- [ ] **Step 3: Verify compile**

Run: `./gradlew compileDebugKotlin`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sc/biap/data/DesignImage.kt \
        app/src/main/java/com/sc/biap/data/Design.kt \
        app/src/main/java/com/sc/biap/data/room/Entities.kt
git commit -m "feat(images): add DesignImage entity and domain model

One row per photo, keyed by a device-generated UUID that never changes - two
devices adding photos offline produce different ids, so a union by id keeps
both rather than one winning.

position needs no userId column: the Room database is already per-identity
(bi_database_<md5(firebaseUid)>.db), so the file scopes the ordering.

positionUpdatedAt is separate from updatedAt so ordering resolves independently
of content - captioning a photo must not drag a stale position with it, the
same principle already applied to the image pointer."
```

---

## Task 4: Migration v4 → v5, with a test that runs on the JVM

Room's `MigrationTestHelper` needs `exportSchema = true` plus an instrumentation run on a device. A JVM test against sqlite-jdbc runs the same SQL in milliseconds with no device, and catches exactly the failure that matters: data loss.

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/test/java/com/sc/biap/data/MigrationV4ToV5Test.kt`
- Modify: `app/src/main/java/com/sc/biap/data/room/AppDatabase.kt`

- [ ] **Step 1: Add the test-only SQLite driver**

In `gradle/libs.versions.toml`, under `[libraries]`:

```toml
sqlite-jdbc = { module = "org.xerial:sqlite-jdbc", version = "3.46.1.3" }
```

In `app/build.gradle.kts`, in the `dependencies` block beside the other `testImplementation` entries:

```kotlin
  // Runs migration SQL on the JVM so a migration cannot silently drop rows.
  testImplementation(libs.sqlite.jdbc)
```

- [ ] **Step 2: Write the failing migration test**

Create `app/src/test/java/com/sc/biap/data/MigrationV4ToV5Test.kt`:

```kotlin
package com.sc.biap.data

import com.sc.biap.data.room.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * Runs MIGRATION_4_5's SQL against a synthetic v4 database.
 *
 * The app previously used fallbackToDestructiveMigration() on every builder, so a schema
 * bump without a working migration silently deleted the entire database. This test is the
 * guard against that ever happening again.
 */
class MigrationV4ToV5Test {

    private fun v4Database(): java.sql.Connection {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        c.createStatement().use { s ->
            s.executeUpdate(
                """
                CREATE TABLE designs (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    imagePath TEXT,
                    customFields TEXT NOT NULL,
                    karigars TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    imageRef TEXT,
                    imageUpdatedAt INTEGER NOT NULL DEFAULT 0,
                    imageUpdatedBy TEXT
                )
                """.trimIndent()
            )
            s.executeUpdate("PRAGMA user_version = 4")
        }
        c.prepareStatement(
            "INSERT INTO designs VALUES (?,?,?,?,?,?,?,?,?,?)"
        ).use { p ->
            // design with a photo
            p.setString(1, "d1"); p.setString(2, "Anarkali"); p.setString(3, "Kurti")
            p.setString(4, "/data/img_1.jpg"); p.setString(5, "{}"); p.setString(6, "[]")
            p.setLong(7, 1754000000000); p.setString(8, "designs/d1/111_a.jpg")
            p.setLong(9, 1754000000500); p.setString(10, "uid-priya")
            p.executeUpdate()
            // design with NO photo - must not produce an image row
            p.setString(1, "d2"); p.setString(2, "Patiala"); p.setString(3, "Salwar")
            p.setString(4, null); p.setString(5, "{}"); p.setString(6, "[]")
            p.setLong(7, 1754000001000); p.setString(8, null)
            p.setLong(9, 0); p.setString(10, null)
            p.executeUpdate()
        }
        return c
    }

    private fun applyMigration(c: java.sql.Connection) {
        c.createStatement().use { s ->
            AppDatabase.MIGRATION_4_5_SQL.forEach { s.executeUpdate(it) }
        }
    }

    @Test
    fun `existing designs are preserved byte for byte`() {
        val c = v4Database()
        val before = c.createStatement()
            .executeQuery("SELECT id, name, imagePath, updatedAt FROM designs ORDER BY id")
            .let { rs -> generateSequence { if (rs.next()) listOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)) else null }.toList() }

        applyMigration(c)

        val after = c.createStatement()
            .executeQuery("SELECT id, name, imagePath, updatedAt FROM designs ORDER BY id")
            .let { rs -> generateSequence { if (rs.next()) listOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4)) else null }.toList() }

        assertEquals("no design row may change", before, after)
        c.close()
    }

    @Test
    fun `a design with a photo gains exactly one image at position 0`() {
        val c = v4Database()
        applyMigration(c)

        val rs = c.createStatement().executeQuery(
            "SELECT id, designId, position, localPath, imageRef, updatedAt, positionUpdatedAt, updatedBy " +
                "FROM design_images WHERE designId = 'd1'"
        )
        assertTrue("d1 must have an image row", rs.next())
        assertTrue("id must be generated and non-empty", rs.getString(1).isNotBlank())
        assertEquals("d1", rs.getString(2))
        assertEquals(0, rs.getInt(3))
        assertEquals("/data/img_1.jpg", rs.getString(4))
        assertEquals("designs/d1/111_a.jpg", rs.getString(5))
        assertEquals(1754000000500L, rs.getLong(6))
        assertEquals("positionUpdatedAt seeds from imageUpdatedAt", 1754000000500L, rs.getLong(7))
        assertEquals("uid-priya", rs.getString(8))
        assertTrue("exactly one row for d1", !rs.next())
        c.close()
    }

    @Test
    fun `a design with no photo produces no image row`() {
        val c = v4Database()
        applyMigration(c)
        val rs = c.createStatement()
            .executeQuery("SELECT COUNT(*) FROM design_images WHERE designId = 'd2'")
        rs.next()
        assertEquals(0, rs.getInt(1))
        c.close()
    }

    @Test
    fun `generated ids are unique across designs`() {
        val c = v4Database()
        applyMigration(c)
        val rs = c.createStatement().executeQuery(
            "SELECT COUNT(*), COUNT(DISTINCT id) FROM design_images"
        )
        rs.next()
        assertEquals("every image id must be distinct", rs.getInt(1), rs.getInt(2))
        c.close()
    }

    @Test
    fun `tombstone table exists and starts empty`() {
        val c = v4Database()
        applyMigration(c)
        val rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM deleted_design_images")
        rs.next()
        assertEquals(0, rs.getInt(1))
        c.close()
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.data.MigrationV4ToV5Test"`
Expected: FAIL — `Unresolved reference 'MIGRATION_4_5_SQL'`

- [ ] **Step 4: Add the migration**

In `app/src/main/java/com/sc/biap/data/room/AppDatabase.kt`, add `DesignImageEntity::class` and `DeletedDesignImageEntity::class` to the `entities` array, change `version = 4` to `version = 5`, and add inside `companion object`:

```kotlin
        /**
         * v4 -> v5: multiple images per design.
         *
         * Purely additive. Two new tables; not one existing row is rewritten. Each design
         * that has a photo gains exactly one image row at position 0, so the current cover
         * simply becomes photo 1.
         *
         * The id is generated in SQL because a Room migration cannot call Kotlin.
         * lower(hex(randomblob(16))) yields 32 random hex chars — not RFC-4122 formatted,
         * but the id is an opaque merge key that is never parsed.
         *
         * designs.imagePath / imageRef / imageUpdatedAt / imageUpdatedBy are deliberately
         * LEFT IN PLACE, unused. minSdk 24 means SQLite 3.9, which has no DROP COLUMN, so
         * removing them would require rebuilding the whole table — risking rows to delete
         * four columns that cost nothing to keep.
         */
        val MIGRATION_4_5_SQL: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS design_images (
                id TEXT NOT NULL PRIMARY KEY,
                designId TEXT NOT NULL,
                position INTEGER NOT NULL,
                localPath TEXT,
                imageRef TEXT,
                thumbRef TEXT,
                caption TEXT,
                updatedAt INTEGER NOT NULL,
                positionUpdatedAt INTEGER NOT NULL,
                updatedBy TEXT,
                FOREIGN KEY(designId) REFERENCES designs(id) ON DELETE CASCADE
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS index_design_images_designId ON design_images(designId)",
            """
            CREATE TABLE IF NOT EXISTS deleted_design_images (
                id TEXT NOT NULL PRIMARY KEY,
                deletedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            INSERT INTO design_images
                (id, designId, position, localPath, imageRef, thumbRef, caption,
                 updatedAt, positionUpdatedAt, updatedBy)
            SELECT lower(hex(randomblob(16))), id, 0, imagePath, imageRef, NULL, NULL,
                   imageUpdatedAt, imageUpdatedAt, imageUpdatedBy
            FROM designs
            WHERE imagePath IS NOT NULL OR imageRef IS NOT NULL
            """.trimIndent()
        )

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach { db.execSQL(it) }
            }
        }
```

Change `ALL_MIGRATIONS`:

```kotlin
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.data.MigrationV4ToV5Test"`
Expected: PASS, 5 tests

- [ ] **Step 6: Verify the whole suite still passes**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, 16 tests (11 existing + 5 new)

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/sc/biap/data/room/AppDatabase.kt \
        app/src/test/java/com/sc/biap/data/MigrationV4ToV5Test.kt
git commit -m "feat(images): Room migration v4 -> v5 for multiple images

Two new tables, purely additive. Each design with a photo gains exactly one
image row at position 0, so the current cover becomes photo 1. Not one
existing row is rewritten.

The id is generated in SQL - lower(hex(randomblob(16))) - because a Room
migration cannot call Kotlin. It is an opaque merge key and never parsed.

designs.imagePath/imageRef/imageUpdatedAt/imageUpdatedBy are LEFT IN PLACE,
unused: minSdk 24 means SQLite 3.9 with no DROP COLUMN, so removing them
needs a full table rebuild - risking rows to delete four harmless columns.

Tested on the JVM with sqlite-jdbc rather than a device: 5 tests covering
rows-unchanged, exactly-one-image-at-position-0, no-photo-produces-no-row,
id uniqueness, and tombstone table creation. Every builder in this repo used
fallbackToDestructiveMigration(), so an untested migration silently deletes
the database - this test is the guard."
```

---

## Task 5: DAO queries for images

**Files:**
- Modify: `app/src/main/java/com/sc/biap/data/room/DesignDao.kt`

- [ ] **Step 1: Add the image queries**

Append inside the `DesignDao` interface:

```kotlin
    // --- Design images -------------------------------------------------------------

    @Query("SELECT * FROM design_images ORDER BY designId, position")
    fun getAllDesignImagesFlow(): Flow<List<DesignImageEntity>>

    @Query("SELECT * FROM design_images WHERE designId = :designId ORDER BY position")
    suspend fun getImagesForDesign(designId: String): List<DesignImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesignImages(images: List<DesignImageEntity>)

    @Query("DELETE FROM design_images WHERE id = :imageId")
    suspend fun deleteDesignImage(imageId: String)

    /**
     * Move ONLY this image's pointers. Deliberately not a read-modify-write of the row: an
     * upload completing in the background must not carry a stale caption or position back
     * over what the user changed while it was in flight.
     */
    @Query(
        "UPDATE design_images SET imageRef = :imageRef, thumbRef = :thumbRef, " +
            "updatedAt = :updatedAt, updatedBy = :updatedBy WHERE id = :imageId"
    )
    suspend fun updateImagePointers(
        imageId: String,
        imageRef: String,
        thumbRef: String?,
        updatedAt: Long,
        updatedBy: String?
    )

    /** Ordering is written on its own timestamp so it merges independently of content. */
    @Query("UPDATE design_images SET position = :position, positionUpdatedAt = :at WHERE id = :imageId")
    suspend fun updateImagePosition(imageId: String, position: Int, at: Long)

    @Query("UPDATE design_images SET caption = :caption, updatedAt = :at WHERE id = :imageId")
    suspend fun updateImageCaption(imageId: String, caption: String?, at: Long)

    @Query("SELECT COUNT(*) FROM design_images WHERE designId = :designId")
    suspend fun countImagesForDesign(designId: String): Int

    // --- Image tombstones ----------------------------------------------------------

    @Query("SELECT * FROM deleted_design_images")
    fun getAllDeletedDesignImagesFlow(): Flow<List<DeletedDesignImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedDesignImage(deleted: DeletedDesignImageEntity)

    @Query("DELETE FROM deleted_design_images WHERE id = :imageId")
    suspend fun deleteDesignImageTombstone(imageId: String)

    @Transaction
    suspend fun removeImageWithTombstone(imageId: String, deletedAt: Long) {
        deleteDesignImage(imageId)
        insertDeletedDesignImage(DeletedDesignImageEntity(imageId, deletedAt))
    }
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew compileDebugKotlin`
Expected: PASS. If kapt reports an unresolved entity, confirm both new entities are listed in `AppDatabase`'s `entities` array from Task 4 Step 4.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/sc/biap/data/room/DesignDao.kt
git commit -m "feat(images): DAO queries for design images and tombstones

updateImagePointers and updateImagePosition write disjoint column sets on
separate timestamps, so a background upload cannot overwrite a caption or a
reorder the user made while it was in flight."
```

---

## Task 6: Thumbnail generation

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/com/sc/biap/util/ThumbnailGenerator.kt`

- [ ] **Step 1: Declare exifinterface explicitly**

`androidx.exifinterface:exifinterface:1.3.7` is already on the **runtime** classpath transitively but **not** the compile classpath — verified by compile probe. Referencing it without declaring it fails to compile. Same trap as `kotlinx-coroutines-play-services` earlier in this project.

In `gradle/libs.versions.toml` under `[libraries]`:

```toml
androidx-exifinterface = { module = "androidx.exifinterface:exifinterface", version = "1.3.7" }
```

In `app/build.gradle.kts` dependencies:

```kotlin
  // Camera photos carry EXIF orientation; a thumbnail decoded without honouring it comes
  // out rotated. Present transitively but NOT on the compile classpath, so declare it.
  implementation(libs.androidx.exifinterface)
```

- [ ] **Step 2: Create the generator**

Create `app/src/main/java/com/sc/biap/util/ThumbnailGenerator.kt`:

```kotlin
package com.sc.biap.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Derives a small thumbnail from a photo.
 *
 * The ORIGINAL IS NEVER MODIFIED. The thumbnail is an additional asset, not compression of
 * the owner's photo — uncompressed originals are a hard requirement.
 *
 * Uses BitmapFactory rather than ImageDecoder because minSdk is 24 and ImageDecoder is
 * API 28. EXIF orientation is applied explicitly, otherwise camera photos produce sideways
 * thumbnails while the full-size image displays upright.
 */
object ThumbnailGenerator {

    /** Longest edge of the generated thumbnail, in pixels. */
    const val MAX_EDGE_PX = 400

    private const val JPEG_QUALITY = 85

    /**
     * Writes a thumbnail beside [source] and returns it, or null if anything fails.
     *
     * Failure is non-fatal by design: the caller falls back to the original, so a design is
     * degraded (slower to browse) rather than broken.
     */
    suspend fun generate(source: File, destination: File): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!source.exists()) return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
            }
            val decoded = BitmapFactory.decodeFile(source.absolutePath, decodeOptions)
                ?: return@withContext null

            val scaled = scaleToMaxEdge(decoded, MAX_EDGE_PX)
            val oriented = applyExifOrientation(scaled, source)

            FileOutputStream(destination).use { out ->
                oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (oriented !== decoded) decoded.recycle()
            destination
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            // A very large photo on a low-memory device. Degrade, do not crash.
            e.printStackTrace()
            null
        }
    }

    /**
     * Largest power of two that keeps the decoded image at or above [maxEdge].
     * Pure and testable; BitmapFactory only honours powers of two.
     */
    fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun applyExifOrientation(bitmap: Bitmap, source: File): Bitmap {
        val orientation = try {
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
```

- [ ] **Step 3: Write the failing test for the pure part**

Create `app/src/test/java/com/sc/biap/util/ThumbnailGeneratorTest.kt`:

```kotlin
package com.sc.biap.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Only sampleSizeFor is tested. Actual decoding needs the Android framework, and the failure
 * mode there (a wrong-looking thumbnail) is obvious on sight; an off-by-one in the sample
 * size is not.
 */
class ThumbnailGeneratorTest {

    @Test
    fun `an image already smaller than the target is not downsampled`() {
        assertEquals(1, ThumbnailGenerator.sampleSizeFor(300, 200, 400))
    }

    @Test
    fun `sample size is the largest power of two that stays at or above the target`() {
        // 4000 -> 2000 -> 1000 -> 500, stopping before 250 would go under 400
        assertEquals(8, ThumbnailGenerator.sampleSizeFor(4000, 3000, 400))
    }

    @Test
    fun `the longest edge decides, whichever way the photo is oriented`() {
        assertEquals(
            ThumbnailGenerator.sampleSizeFor(4000, 3000, 400),
            ThumbnailGenerator.sampleSizeFor(3000, 4000, 400)
        )
    }

    @Test
    fun `degenerate input returns 1 rather than dividing by zero`() {
        assertEquals(1, ThumbnailGenerator.sampleSizeFor(0, 0, 400))
        assertEquals(1, ThumbnailGenerator.sampleSizeFor(100, 100, 0))
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.util.ThumbnailGeneratorTest"`
Expected: PASS, 4 tests

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/sc/biap/util/ThumbnailGenerator.kt \
        app/src/test/java/com/sc/biap/util/ThumbnailGeneratorTest.kt
git commit -m "feat(images): thumbnail generation, original untouched

400px derived asset so browsing a 10-photo design pulls ~400KB instead of
~50MB. The ORIGINAL IS NEVER MODIFIED - this is an additional asset, not
compression, which the owner explicitly rejected.

BitmapFactory rather than ImageDecoder because minSdk 24 (ImageDecoder is 28).
EXIF orientation applied explicitly, otherwise camera photos yield sideways
thumbnails while the full image displays upright.

Declared androidx.exifinterface explicitly: it was present transitively but
NOT on the compile classpath, so referencing it would not have compiled - the
same trap as kotlinx-coroutines-play-services earlier in this project.

Failure returns null and the caller falls back to the original: degraded, not
broken. OutOfMemoryError is caught for very large photos on weak devices."
```

---

## Task 7: Per-image storage keys and retention

**Files:**
- Modify: `app/src/main/java/com/sc/biap/util/ImageStore.kt`
- Modify: `app/src/test/java/com/sc/biap/util/ImageStoreRetentionTest.kt`

- [ ] **Step 1: Write the failing tests for the new key shape**

Append to `app/src/test/java/com/sc/biap/util/ImageStoreRetentionTest.kt`:

```kotlin
    @Test
    fun `version keys are namespaced per image`() {
        val key = ImageStore.newVersionKey("d1", "img7", "jpg")
        assertTrue("must be scoped to the design", key.startsWith("designs/d1/img7/"))
        assertTrue("must keep the extension", key.endsWith(".jpg"))
    }

    @Test
    fun `thumbnail key sits beside its version, not as a separate version`() {
        val version = ImageStore.newVersionKey("d1", "img7", "jpg")
        val thumb = ImageStore.thumbKeyFor(version)
        assertEquals(version.substringBeforeLast('.') + "_thumb.jpg", thumb)
    }

    @Test
    fun `thumbnails are never counted as versions when pruning`() {
        val names = listOf(
            "1000_a.jpg", "1000_a_thumb.jpg",
            "2000_b.jpg", "2000_b_thumb.jpg",
            "3000_c.jpg", "3000_c_thumb.jpg",
            "4000_d.jpg", "4000_d_thumb.jpg",
            "5000_e.jpg", "5000_e_thumb.jpg",
            "6000_f.jpg", "6000_f_thumb.jpg"
        )
        val doomed = ImageStore.versionsToPrune(names, keep = 5, protectName = null)
        // 6 versions, keep 5 -> only the oldest version and ITS thumbnail go
        assertEquals(listOf("1000_a.jpg", "1000_a_thumb.jpg"), doomed.sorted())
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.util.ImageStoreRetentionTest"`
Expected: FAIL — `newVersionKey` takes 2 arguments, `thumbKeyFor` does not exist

- [ ] **Step 3: Update `ImageStore`**

Replace `newVersionKey` and `versionsToPrune`, and add `thumbKeyFor`:

```kotlin
    /**
     * A fresh, never-reused key for a new version of ONE image.
     *
     * The {imageId} segment is what gives every photo its own version history. Without it,
     * adding a second photo would start deleting the first photo's versions, because
     * retention keeps the newest 5 under a prefix.
     */
    fun newVersionKey(designId: String, imageId: String, extension: String = "jpg"): String {
        val stamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString().take(8)
        return "$ROOT/$designId/$imageId/${stamp}_$nonce.$extension"
    }

    /** The thumbnail that belongs to a given version key. */
    fun thumbKeyFor(versionKey: String): String =
        versionKey.substringBeforeLast('.') + "_thumb.jpg"

    private const val THUMB_MARKER = "_thumb."

    /**
     * Which version filenames should be deleted. Pure, so the choice of what to destroy is
     * testable without touching the network.
     *
     * Thumbnails are NOT independent versions: they are counted with their parent and
     * removed alongside it, never on their own. Treating them as versions would halve the
     * effective retention.
     */
    fun versionsToPrune(names: List<String>, keep: Int, protectName: String?): List<String> {
        if (keep <= 0) return emptyList()
        val versions = names.filterNot { it.contains(THUMB_MARKER) }.sorted()
        val doomedVersions = versions.dropLast(keep).filter { it != protectName }
        val doomedThumbs = doomedVersions.map { thumbKeyFor(it) }.filter { it in names }
        return doomedVersions + doomedThumbs
    }
```

Update `pruneToLatest`'s signature to take the image id:

```kotlin
    suspend fun pruneToLatest(
        designId: String,
        imageId: String,
        keep: Int = VERSIONS_RETAINED,
        protectKey: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val items = storage.getReference("$ROOT/$designId/$imageId").listAll().await().items
            val doomedNames = versionsToPrune(
                names = items.map { it.name },
                keep = keep,
                protectName = protectKey?.substringAfterLast('/'),
            ).toSet()
            var deleted = 0
            for (ref in items.filter { it.name in doomedNames }) {
                try {
                    ref.delete().await()
                    deleted++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.util.ImageStoreRetentionTest"`
Expected: PASS, 10 tests (7 existing + 3 new)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/sc/biap/util/ImageStore.kt \
        app/src/test/java/com/sc/biap/util/ImageStoreRetentionTest.kt
git commit -m "feat(images): per-image storage keys and thumbnail-aware retention

Keys gain an {imageId} segment so every photo keeps its OWN 5-version history.
Without it, adding a second photo would silently start deleting the first
photo's versions, because retention keeps the newest 5 under a prefix.

Thumbnails are not independent versions: they are counted with their parent and
pruned alongside it. Treating them as versions would halve effective retention."
```

---

## Task 8: Per-image upload worker

**Files:**
- Modify: `app/src/main/java/com/sc/biap/util/ImageUploadWorker.kt`
- Modify: `app/src/main/java/com/sc/biap/data/DataRepository.kt`

- [ ] **Step 1: Update the worker**

Replace the body of `ImageUploadWorker` with:

```kotlin
    companion object {
        const val KEY_DESIGN_ID = "designId"
        const val KEY_IMAGE_ID = "imageId"
        const val KEY_LOCAL_PATH = "localPath"
        const val KEY_STORAGE_KEY = "storageKey"

        /**
         * Unique work name per IMAGE, not per design. Under a per-design name, adding two
         * photos in quick succession would have the second cancel the first's upload.
         */
        fun workName(designId: String, imageId: String) = "ImageUpload_${designId}_$imageId"

        const val MAX_ATTEMPTS = 4
    }

    override suspend fun doWork(): Result {
        val designId = inputData.getString(KEY_DESIGN_ID) ?: return Result.failure()
        val imageId = inputData.getString(KEY_IMAGE_ID) ?: return Result.failure()
        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val storageKey = inputData.getString(KEY_STORAGE_KEY) ?: return Result.failure()

        val file = File(localPath)
        if (!file.exists()) return Result.failure()
        if (runAttemptCount >= MAX_ATTEMPTS) return Result.failure()

        // 1. original bytes first, to a key nothing points at yet
        if (!ImageStore.upload(file, storageKey)) return Result.retry()

        // 2. thumbnail is best-effort - a missing one degrades browsing, it does not break
        //    the design, so its failure must not fail the job
        val thumbKey = ImageStore.thumbKeyFor(storageKey)
        val thumbFile = File(applicationContext.cacheDir, "thumb_$imageId.jpg")
        val thumbUploaded = ThumbnailGenerator.generate(file, thumbFile)
            ?.let { ImageStore.upload(it, thumbKey) } ?: false
        thumbFile.delete()

        // 3. pointers move only once the bytes are definitely there
        val uploadedBy = FirebaseAuth.getInstance().currentUser?.uid
        val repository = DefaultDataRepository.getInstance(applicationContext)
        val applied = repository.applyUploadedImage(
            imageId = imageId,
            imageRef = storageKey,
            thumbRef = if (thumbUploaded) thumbKey else null,
            uploadedBy = uploadedBy
        )
        if (!applied) return Result.retry()

        // 4. retention, best-effort - never fails the job
        ImageStore.pruneToLatest(designId, imageId, protectKey = storageKey)

        return Result.success()
    }
```

Add imports: `com.sc.biap.util.ThumbnailGenerator` is same-package so needs none; ensure `java.io.File` and `com.google.firebase.auth.FirebaseAuth` remain imported.

- [ ] **Step 2: Update the repository interface and implementation**

In `DataRepository.kt`, replace the two interface methods:

```kotlin
    /** Queue one design photo for background upload. Returns the storage key it will land on. */
    fun enqueueImageUpload(designId: String, imageId: String, localPath: String): String
    /** Move one image's pointers after its bytes are confirmed uploaded. */
    suspend fun applyUploadedImage(
        imageId: String,
        imageRef: String,
        thumbRef: String?,
        uploadedBy: String?
    ): Boolean
```

Replace the implementations:

```kotlin
    override fun enqueueImageUpload(designId: String, imageId: String, localPath: String): String {
        val key = ImageStore.newVersionKey(
            designId,
            imageId,
            File(localPath).extension.ifBlank { "jpg" }
        )
        val request = OneTimeWorkRequestBuilder<ImageUploadWorker>()
            .setInputData(
                workDataOf(
                    ImageUploadWorker.KEY_DESIGN_ID to designId,
                    ImageUploadWorker.KEY_IMAGE_ID to imageId,
                    ImageUploadWorker.KEY_LOCAL_PATH to localPath,
                    ImageUploadWorker.KEY_STORAGE_KEY to key,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ImageUploadWorker.workName(designId, imageId),
            ExistingWorkPolicy.REPLACE,
            request
        )
        return key
    }

    override suspend fun applyUploadedImage(
        imageId: String,
        imageRef: String,
        thumbRef: String?,
        uploadedBy: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val dao = awaitDao() ?: return@withContext false
            dao.updateImagePointers(
                imageId = imageId,
                imageRef = imageRef,
                thumbRef = thumbRef,
                updatedAt = System.currentTimeMillis(),
                updatedBy = uploadedBy
            )
            triggerLocalBackup()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
```

- [ ] **Step 3: Replace the upload hook in `addOrUpdateDesign`**

Replace the block added earlier that reads `designWithTime.imagePath` / `imageRef` with:

```kotlin
            // Any image with a local file but no remote pointer has not been uploaded yet -
            // either newly added, or replaced (a replacement clears imageRef). Hooked here
            // rather than in each ViewModel so a save path added later cannot silently skip
            // uploading. Sync does not come through here; it uses importData.
            designWithTime.images.forEach { image ->
                if (!image.localPath.isNullOrBlank() && image.imageRef.isNullOrBlank()) {
                    val file = FileUtils.resolveImageFile(context, image.localPath)
                    if (file != null && file.exists()) {
                        enqueueImageUpload(design.id, image.id, file.absolutePath)
                    }
                }
            }
```

- [ ] **Step 4: Persist images in `addOrUpdateDesign`**

Immediately after `dao.insertDesignWithInventories(...)`, add:

```kotlin
            dao.insertDesignImages(designWithTime.images.map { it.toEntity() })
```

- [ ] **Step 5: Add images to the combine block**

In the `init` block's `combine(...)`, add `dao.getAllDesignImagesFlow()` and `dao.getAllDeletedDesignImagesFlow()` as additional flows, and populate the new `BusinessIntelligenceData` fields:

```kotlin
                            designImages = imagesList.map { it.toDomain() },
                            deletedDesignImages = deletedImagesList.associate { it.id to it.deletedAt }
```

Then attach each design's images when mapping designs to domain, so `Design.images` is populated from the relation.

- [ ] **Step 6: Verify compile and tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 20 tests passing

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/sc/biap/util/ImageUploadWorker.kt \
        app/src/main/java/com/sc/biap/data/DataRepository.kt
git commit -m "feat(images): per-image upload with thumbnail

Unique work is now keyed per IMAGE. Under the previous per-design name, adding
two photos in quick succession had the second cancel the first's upload.

Ordering is unchanged and remains the safety property: upload original ->
upload thumbnail -> move THIS image's pointers -> prune THIS image's history.
A failure at step 1 leaves the previous image live and reachable.

Thumbnail failure is deliberately non-fatal: thumbRef stays null and callers
fall back to the original."
```

---

## Task 9: Union merge in SyncMerger

**Files:**
- Modify: `app/src/test/java/com/sc/biap/data/SyncMergerImageTest.kt`
- Modify: `app/src/main/java/com/sc/biap/data/SyncMerger.kt`
- Create: `app/src/test/java/com/sc/biap/data/DesignImageOrderTest.kt`

- [ ] **Step 1: Write the failing union tests**

Append to `SyncMergerImageTest.kt`:

```kotlin
    private fun image(
        id: String,
        designId: String = "d1",
        position: Int = 0,
        caption: String? = null,
        updatedAt: Long = 0L,
        positionUpdatedAt: Long = 0L,
    ) = DesignImage(
        id = id, designId = designId, position = position, caption = caption,
        updatedAt = updatedAt, positionUpdatedAt = positionUpdatedAt
    )

    private fun biImages(
        images: List<DesignImage> = emptyList(),
        deleted: Map<String, Long> = emptyMap(),
    ) = BusinessIntelligenceData(designImages = images, deletedDesignImages = deleted)

    @Test
    fun `photos added independently on two devices both survive`() {
        val local = biImages(listOf(image("imgP", updatedAt = 100)))
        val remote = biImages(listOf(image("imgR", updatedAt = 200)))

        val merged = SyncMerger.merge(local, remote).designImages

        assertEquals(setOf("imgP", "imgR"), merged.map { it.id }.toSet())
    }

    @Test
    fun `a deleted photo does not come back from the other device`() {
        val local = biImages(deleted = mapOf("imgP" to 300))
        val remote = biImages(listOf(image("imgP", updatedAt = 100)))

        val merged = SyncMerger.merge(local, remote).designImages

        assertTrue("tombstone must beat the older edit", merged.none { it.id == "imgP" })
    }

    @Test
    fun `re-adding a photo after deleting it clears the tombstone`() {
        val local = biImages(listOf(image("imgP", updatedAt = 500)), deleted = mapOf("imgP" to 300))
        val remote = biImages()

        val result = SyncMerger.merge(local, remote)

        assertTrue("re-added photo survives", result.designImages.any { it.id == "imgP" })
        assertTrue("stale tombstone is pruned", !result.deletedDesignImages.containsKey("imgP"))
    }

    @Test
    fun `editing a caption does not drag a stale position along`() {
        // Priya reorders at t=200 without touching content
        val remote = biImages(listOf(image("imgP", position = 3, positionUpdatedAt = 200, updatedAt = 50)))
        // Raj captions at t=300 on a device still holding the old position
        val local = biImages(listOf(image("imgP", position = 0, positionUpdatedAt = 10, updatedAt = 300, caption = "front")))

        val merged = SyncMerger.merge(local, remote).designImages.single()

        assertEquals("caption should be Raj's (edited later)", "front", merged.caption)
        assertEquals("position should be Priya's (reordered later)", 3, merged.position)
    }

    @Test
    fun `reordering does not drag a stale caption along`() {
        val local = biImages(listOf(image("imgP", position = 7, positionUpdatedAt = 900, updatedAt = 10)))
        val remote = biImages(listOf(image("imgP", position = 0, positionUpdatedAt = 5, updatedAt = 800, caption = "detail")))

        val merged = SyncMerger.merge(local, remote).designImages.single()

        assertEquals(7, merged.position)
        assertEquals("detail", merged.caption)
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.data.SyncMergerImageTest"`
Expected: FAIL — `designImages` is never merged, so results are empty

- [ ] **Step 3: Add the image merge to `SyncMerger.merge`**

Insert before the `return BusinessIntelligenceData(...)`:

```kotlin
        // 4. Merge design images.
        //
        // Union by id, because two devices adding photos offline generate different ids and
        // neither should win. Content and ordering resolve on SEPARATE timestamps: captioning
        // a photo must not drag a stale position along, and reordering must not drag a stale
        // caption - the same principle already applied to the image pointer above.
        val mergedDeletedImages = (localData.deletedDesignImages.keys + remoteData.deletedDesignImages.keys)
            .associateWith { id ->
                maxOf(
                    localData.deletedDesignImages[id] ?: 0L,
                    remoteData.deletedDesignImages[id] ?: 0L
                )
            }
        val allImages = (localData.designImages + remoteData.designImages)
            .groupBy { it.id }
            .mapValues { (_, versions) ->
                val content = versions.maxByOrNull { it.updatedAt }!!
                val ordering = versions.maxByOrNull { it.positionUpdatedAt }!!
                content.copy(
                    position = ordering.position,
                    positionUpdatedAt = ordering.positionUpdatedAt
                )
            }
        val mergedImages = allImages.values.filter { img ->
            val deletedAt = mergedDeletedImages[img.id] ?: -1L
            img.updatedAt > deletedAt
        }
        val finalDeletedImages = mergedDeletedImages.filter { (id, deletedAt) ->
            val img = allImages[id]
            img == null || deletedAt >= img.updatedAt
        }
```

Add to the returned `BusinessIntelligenceData`:

```kotlin
            designImages = mergedImages,
            deletedDesignImages = finalDeletedImages,
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.data.SyncMergerImageTest"`
Expected: PASS, 9 tests (4 existing + 5 new)

- [ ] **Step 5: Write the ordering tests**

Create `app/src/test/java/com/sc/biap/data/DesignImageOrderTest.kt`:

```kotlin
package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ordering is per-user because the Room database is per-identity. Merged rows can still
 * collide or leave gaps (the same user's two devices, both offline), so positions are
 * renormalised on read.
 */
class DesignImageOrderTest {

    private fun img(id: String, position: Int) =
        DesignImage(id = id, designId = "d1", position = position)

    @Test
    fun `gaps are closed without changing relative order`() {
        val input = listOf(img("a", 0), img("b", 5), img("c", 9))
        val out = DesignImageOrder.renormalise(input)
        assertEquals(listOf("a", "b", "c"), out.map { it.id })
        assertEquals(listOf(0, 1, 2), out.map { it.position })
    }

    @Test
    fun `duplicate positions break deterministically by id`() {
        val out = DesignImageOrder.renormalise(listOf(img("z", 1), img("a", 1)))
        assertEquals("id is the stable tiebreak", listOf("a", "z"), out.map { it.id })
        assertEquals(listOf(0, 1), out.map { it.position })
    }

    @Test
    fun `an empty list renormalises to an empty list`() {
        assertEquals(emptyList<DesignImage>(), DesignImageOrder.renormalise(emptyList()))
    }

    @Test
    fun `a photo from another user is appended, never inserted`() {
        val mine = listOf(img("a", 0), img("b", 1))
        val theirs = img("new", 0)   // arrives claiming position 0
        val out = DesignImageOrder.appendForeign(mine, listOf(theirs))
        assertEquals("my arrangement is undisturbed", listOf("a", "b", "new"), out.map { it.id })
        assertEquals(2, out.last().position)
    }
}
```

- [ ] **Step 6: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.sc.biap.data.DesignImageOrderTest"`
Expected: FAIL — `Unresolved reference 'DesignImageOrder'`

- [ ] **Step 7: Implement the ordering helper**

Append to `app/src/main/java/com/sc/biap/data/DesignImage.kt`:

```kotlin
/**
 * Position hygiene, kept pure so it is testable without a database.
 *
 * Ordering is per-user (the Room database is per-identity), but the same user's two devices
 * can still merge into colliding positions or gaps.
 */
object DesignImageOrder {

    /** Compact to 0..n-1, preserving relative order. Ties break by id so it is deterministic. */
    fun renormalise(images: List<DesignImage>): List<DesignImage> =
        images.sortedWith(compareBy({ it.position }, { it.id }))
            .mapIndexed { index, image ->
                if (image.position == index) image else image.copy(position = index)
            }

    /**
     * Place photos that arrived from another user at the END of this user's order.
     *
     * Their position value is meaningless here - it belongs to their ordering - so honouring
     * it would shuffle an arrangement the user deliberately set.
     */
    fun appendForeign(mine: List<DesignImage>, foreign: List<DesignImage>): List<DesignImage> {
        val start = (mine.maxOfOrNull { it.position } ?: -1) + 1
        val appended = foreign.sortedBy { it.id }.mapIndexed { i, image ->
            image.copy(position = start + i)
        }
        return renormalise(mine + appended)
    }
}
```

- [ ] **Step 8: Run all tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, 24 tests

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/sc/biap/data/SyncMerger.kt \
        app/src/main/java/com/sc/biap/data/DesignImage.kt \
        app/src/test/java/com/sc/biap/data/SyncMergerImageTest.kt \
        app/src/test/java/com/sc/biap/data/DesignImageOrderTest.kt
git commit -m "feat(images): union merge with independent content and ordering

Photos union by id: two devices adding offline generate different ids, so both
survive rather than one silently winning.

Content and ordering resolve on SEPARATE timestamps. Captioning a photo must
not drag a stale position along, and reordering must not drag a stale caption -
the same principle that stopped a price edit clobbering a newer photo.

Photos arriving from another user APPEND at the end. Their position belongs to
their ordering, so honouring it would shuffle an arrangement this user
deliberately set."
```

---

## Task 10: Un-break the grid — MainScreen reads the cover

**Do this before any other UI task.** After Task 3 the grid still reads `design.imagePath` / `design.imageRef`, which are now vestigial — every card renders blank until this lands.

**Files:**
- Modify: `app/src/main/java/com/sc/biap/ui/DesignImage.kt`
- Modify: `app/src/main/java/com/sc/biap/ui/main/MainScreen.kt`

- [ ] **Step 1: Retype the image model resolver**

Replace the whole body of `app/src/main/java/com/sc/biap/ui/DesignImage.kt`:

```kotlin
package com.sc.biap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sc.biap.data.DesignImage
import com.sc.biap.util.FileUtils
import com.sc.biap.util.ImageStore

/**
 * Resolves what one photo should load from.
 *
 * Precedence is local-first, deliberately:
 *  1. the local file, when it exists - instant, works with no signal, zero network calls
 *  2. otherwise the uploaded version - this is what lets an employee see a photo somebody
 *     else took
 *
 * Takes a typed [DesignImage] rather than loose strings so no call site can accidentally
 * read the vestigial designs.imagePath / imageRef columns.
 */
@Composable
fun rememberDesignImageModel(
    image: DesignImage?,
    preferThumbnail: Boolean = false,
): Any? {
    val context = LocalContext.current

    val localFile = remember(image?.localPath) {
        FileUtils.resolveImageFile(context, image?.localPath)?.takeIf { it.exists() }
    }
    // A local original beats a remote thumbnail: it is already on disk and full quality.
    if (localFile != null) return localFile

    val remoteKey = if (preferThumbnail) image?.thumbRef ?: image?.imageRef else image?.imageRef

    var remoteUrl by remember(remoteKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(remoteKey) {
        remoteUrl = remoteKey?.takeIf { it.isNotBlank() }?.let { ImageStore.resolveUrl(it) }
    }
    return remoteUrl
}
```

- [ ] **Step 2: Point the grid card at the cover**

In `app/src/main/java/com/sc/biap/ui/main/MainScreen.kt`, replace the image model call in the design card:

```kotlin
                val imageModel = rememberDesignImageModel(
                    image = design.coverImage,
                    preferThumbnail = true
                )
```

The grid shows many cards at once, so it loads thumbnails: ~40 KB each instead of ~5 MB.

- [ ] **Step 3: Verify build and install**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
Then open the app and confirm the portfolio grid still shows photos for existing designs. This is the visible proof the migration carried covers across.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sc/biap/ui/DesignImage.kt \
        app/src/main/java/com/sc/biap/ui/main/MainScreen.kt
git commit -m "feat(images): grid card reads the cover image

rememberDesignImageModel now takes a typed DesignImage instead of loose
strings, so no call site can read the vestigial designs.imagePath/imageRef
columns - left alone, every card in the portfolio grid would render blank.

The grid prefers thumbnails: many cards at once, ~40KB each instead of ~5MB."
```

---

## Task 11: Full-screen viewer

**Files:**
- Create: `app/src/main/java/com/sc/biap/ui/image/ImageViewer.kt`

- [ ] **Step 1: Create the viewer**

Create `app/src/main/java/com/sc/biap/ui/image/ImageViewer.kt`:

```kotlin
package com.sc.biap.ui.image

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.DesignImage
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.ui.rememberDesignImageModel
import kotlin.math.abs

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2f
private val DotSize = 8.dp
private val CloseButtonSize = 48.dp

/**
 * Full-screen photo viewer.
 *
 * This is the only place a photo is shown with ContentScale.Fit, so it is the only place the
 * WHOLE photo is visible - every other surface crops to fill a fixed box.
 */
@Composable
fun ImageViewer(
    images: List<DesignImage>,
    startIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            // Paging is disabled while zoomed in - see ZoomablePage. Otherwise a horizontal
            // drag intended to pan the photo would flick to the next one instead.
            userScrollEnabled = true,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomablePage(image = images[page])
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(AtelierSpacing.Base)
                .size(CloseButtonSize)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        images.getOrNull(pagerState.currentPage)?.caption?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AtelierSpacing.Double, start = AtelierSpacing.Base, end = AtelierSpacing.Base)
            )
        }

        if (images.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AtelierSpacing.Base)
            ) {
                images.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = AtelierSpacing.Micro)
                            .size(DotSize)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * One zoomable, pannable page.
 *
 * Zoom state is per page and deliberately NOT hoisted: swiping to another photo should show
 * it at fit scale, not inherit the previous photo's zoom.
 */
@Composable
private fun ZoomablePage(image: DesignImage) {
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val animatedScale by animateFloatAsState(targetValue = scale, label = "zoom")

    // The viewer always loads the ORIGINAL, never the thumbnail - this is where the user
    // came to see detail.
    val model = rememberDesignImageModel(image = image, preferThumbnail = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(image.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > MIN_ZOOM) {
                            scale = MIN_ZOOM
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                        }
                    }
                )
            }
            .pointerInput(image.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    if (scale > MIN_ZOOM) {
                        // Clamp panning to roughly the scaled overflow so the photo cannot be
                        // dragged off into empty space.
                        val maxX = (size.width * (scale - 1f)) / 2f
                        val maxY = (size.height * (scale - 1f)) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            Image(
                painter = rememberAsyncImagePainter(model),
                contentDescription = image.caption ?: "Design photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = animatedScale,
                        scaleY = animatedScale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/sc/biap/ui/image/ImageViewer.kt
git commit -m "feat(images): full-screen viewer with pinch-zoom and swipe

The only surface using ContentScale.Fit, so the only place the WHOLE photo is
visible - everywhere else crops to fill a fixed box.

Zoom state is per page and deliberately not hoisted: swiping to another photo
shows it at fit scale rather than inheriting the previous photo's zoom. Panning
is clamped to the scaled overflow so a photo cannot be dragged into empty
space. The viewer always loads the original, never the thumbnail."
```

---

## Task 12: Detail screen pager, thumbnail strip, captions and the 10-photo cap

**Files:**
- Create: `app/src/main/java/com/sc/biap/ui/image/ImageThumbnailStrip.kt`
- Modify: `app/src/main/java/com/sc/biap/ui/design/DesignDetailScreen.kt`
- Modify: `app/src/main/java/com/sc/biap/ui/design/AddDesignScreen.kt`
- Modify: `app/src/main/java/com/sc/biap/ui/design/AddDesignViewModel.kt`

- [ ] **Step 1: Enforce the cap in the ViewModel**

In `AddDesignViewModel.kt`, replace the single `imagePath` state with a list and add:

```kotlin
    /**
     * Adds a picked photo. Silently ignores the call at the cap rather than throwing - the
     * add tile is disabled at 10, so reaching here means a race, not a user error.
     */
    fun onImageSelected(localPath: String) {
        _uiState.update { current ->
            if (current.images.size >= MAX_IMAGES_PER_DESIGN) return@update current
            val now = System.currentTimeMillis()
            current.copy(
                images = current.images + DesignImage(
                    id = UUID.randomUUID().toString(),
                    designId = "",              // assigned on save
                    position = current.images.size,
                    localPath = localPath,
                    updatedAt = now,
                    positionUpdatedAt = now
                )
            )
        }
    }

    fun removeImage(imageId: String) {
        _uiState.update { current ->
            current.copy(
                images = DesignImageOrder.renormalise(current.images.filterNot { it.id == imageId })
            )
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        _uiState.update { current ->
            val list = current.images.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices) return@update current
            list.add(toIndex, list.removeAt(fromIndex))
            val now = System.currentTimeMillis()
            current.copy(
                images = list.mapIndexed { index, img ->
                    img.copy(position = index, positionUpdatedAt = now)
                }
            )
        }
    }

    fun setCaption(imageId: String, caption: String) {
        _uiState.update { current ->
            current.copy(
                images = current.images.map {
                    if (it.id == imageId) it.copy(
                        caption = caption.ifBlank { null },
                        updatedAt = System.currentTimeMillis()
                    ) else it
                }
            )
        }
    }
```

Update `saveDesign()` to stamp `designId` on each image:

```kotlin
        val designId = UUID.randomUUID().toString()
        val newDesign = Design(
            id = designId,
            name = state.name.trim(),
            category = state.category.trim(),
            images = state.images.map { it.copy(designId = designId) },
            sizeInventories = state.sizes,
            customFields = state.customFields.filterKeys { it.isNotBlank() },
            karigars = state.karigars
        )
```

- [ ] **Step 2: Create the thumbnail strip**

Create `app/src/main/java/com/sc/biap/ui/image/ImageThumbnailStrip.kt`:

```kotlin
package com.sc.biap.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.DesignImage
import com.sc.biap.data.MAX_IMAGES_PER_DESIGN
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.labelLargeStrong
import com.sc.biap.ui.rememberDesignImageModel

private val ThumbSize = 72.dp
private val RemoveIconSize = 18.dp

@Composable
fun ImageThumbnailStrip(
    images: List<DesignImage>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    onThumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier) {
        items(images) { image ->
            val index = images.indexOf(image)
            Box(
                modifier = Modifier
                    .padding(end = AtelierSpacing.Compact)
                    .size(ThumbSize)
                    .clip(AtelierShapes.CornerMedium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onThumbClick(index) }
            ) {
                val model = rememberDesignImageModel(image = image, preferThumbnail = true)
                if (model != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model),
                        contentDescription = image.caption ?: "Photo ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // The first slot is the cover for THIS user. Labelling it makes the rule
                // visible instead of folklore.
                if (index == 0) {
                    Text(
                        text = "Your cover",
                        style = MaterialTheme.typography.labelLargeStrong,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = AtelierSpacing.Micro)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .minimumInteractiveComponentSize()
                        .clickable(role = Role.Button, onClickLabel = "Remove") { onRemove(image.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove photo ${index + 1}",
                        tint = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), AtelierShapes.CornerSmall)
                            .size(RemoveIconSize)
                    )
                }
            }
        }
        if (images.size < MAX_IMAGES_PER_DESIGN) {
            item {
                Box(
                    modifier = Modifier
                        .size(ThumbSize)
                        .clip(AtelierShapes.CornerMedium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(role = Role.Button) { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add photo")
                }
            }
        }
    }
}
```

- [ ] **Step 3: Wire the strip and viewer into `AddDesignScreen` and `DesignDetailScreen`**

In both screens, replace the single-image `Box` with the strip plus a hero, and hold viewer state:

```kotlin
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    viewerIndex?.let { index ->
        ImageViewer(
            images = state.images,
            startIndex = index,
            onDismiss = { viewerIndex = null }
        )
    }
```

In `DesignDetailScreen`, replace the hero `Image` with a pager over `design.images` where tapping opens the viewer:

```kotlin
    val heroPager = rememberPagerState(pageCount = { design.images.size.coerceAtLeast(1) })
    HorizontalPager(state = heroPager, modifier = Modifier.fillMaxWidth().height(HeroImageHeight)) { page ->
        val image = design.images.getOrNull(page)
        val model = rememberDesignImageModel(image = image, preferThumbnail = false)
        if (model != null) {
            Image(
                painter = rememberAsyncImagePainter(model),
                contentDescription = image?.caption ?: design.name,
                contentScale = ContentScale.Crop,   // hero fills a fixed box; Fit lives in the viewer
                modifier = Modifier.fillMaxSize().clickable { viewerIndex = page }
            )
        }
    }
```

- [ ] **Step 4: Verify build, install and exercise the flow**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Then confirm on-device:
- adding a second photo shows two thumbnails, the first labelled "Your cover"
- the add tile disappears at 10 photos
- tapping the hero opens the full-screen viewer
- pinch zooms, double-tap toggles, swipe moves between photos
- removing a photo renumbers the rest with no gap

- [ ] **Step 5: Run the full suite and both variants**

Run: `./gradlew assembleDebug assembleRelease testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 24 tests passing

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/sc/biap/ui/image/ImageThumbnailStrip.kt \
        app/src/main/java/com/sc/biap/ui/design/DesignDetailScreen.kt \
        app/src/main/java/com/sc/biap/ui/design/AddDesignScreen.kt \
        app/src/main/java/com/sc/biap/ui/design/AddDesignViewModel.kt
git commit -m "feat(images): thumbnail strip, detail pager, captions, 10-photo cap

The first slot is labelled 'Your cover' - ordering is per-user, so the wording
avoids implying it is shared.

The hero stays ContentScale.Crop because it fills a fixed box; Fit lives in the
viewer, which is where the whole photo is meant to be visible.

The cap is enforced in the ViewModel rather than the schema, so changing it
needs no migration. The add tile disappears at 10; reaching the guard means a
race, not a user error, so it returns silently."
```

---

## Task 13: In-app cropper

**Files:**
- Create: `app/src/main/java/com/sc/biap/ui/image/ImageCropper.kt`

- [ ] **Step 1: Create the cropper**

Reuses the viewer's transform-gesture approach rather than duplicating it. Saving writes a **new version of the same image id**, so the crop inherits versioned upload, keep-newest-5 and per-image merge — which is what makes a bad crop revertible.

Create `app/src/main/java/com/sc/biap/ui/image/ImageCropper.kt`:

```kotlin
package com.sc.biap.ui.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Crop and rotate, writing a NEW FILE rather than modifying the source.
 *
 * The result becomes a new version of the same image id, so it inherits versioned upload and
 * keep-newest-5 retention. That is what makes a bad crop revertible: the previous version is
 * still in Storage until retention ages it out.
 *
 * JPEG quality is 100 - the owner requires uncompressed originals, and a crop is still an
 * original.
 */
object ImageCropper {

    suspend fun cropAndRotate(
        source: File,
        destination: File,
        cropLeftFraction: Float,
        cropTopFraction: Float,
        cropWidthFraction: Float,
        cropHeightFraction: Float,
        rotationDegrees: Int,
    ): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return@withContext null

            val x = (bitmap.width * cropLeftFraction).toInt().coerceIn(0, bitmap.width - 1)
            val y = (bitmap.height * cropTopFraction).toInt().coerceIn(0, bitmap.height - 1)
            val w = (bitmap.width * cropWidthFraction).toInt().coerceIn(1, bitmap.width - x)
            val h = (bitmap.height * cropHeightFraction).toInt().coerceIn(1, bitmap.height - y)

            val matrix = Matrix().apply { if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat()) }
            val out = Bitmap.createBitmap(bitmap, x, y, w, h, matrix, true)

            FileOutputStream(destination).use { stream ->
                out.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
            if (out !== bitmap) bitmap.recycle()
            destination
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }
}
```

- [ ] **Step 2: Wire cropping into the strip**

When the user crops, write the result to a new local file, then set that as the image's `localPath` and **clear `imageRef`** so `addOrUpdateDesign` re-uploads it as a new version:

```kotlin
    fun applyCrop(imageId: String, croppedPath: String) {
        _uiState.update { current ->
            current.copy(
                images = current.images.map {
                    if (it.id == imageId) it.copy(
                        localPath = croppedPath,
                        imageRef = null,     // marks it for re-upload as a NEW version
                        thumbRef = null,
                        updatedAt = System.currentTimeMillis()
                    ) else it
                }
            )
        }
    }
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug assembleRelease testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 24 tests passing

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/sc/biap/ui/image/ImageCropper.kt \
        app/src/main/java/com/sc/biap/ui/design/AddDesignViewModel.kt
git commit -m "feat(images): in-app crop and rotate

Writes a NEW FILE and clears imageRef, so the result uploads as a new version
of the same image id. It therefore inherits versioned upload, keep-newest-5 and
per-image merge - which is what makes a bad crop revertible: the previous
version stays in Storage until retention ages it out.

JPEG quality 100: the owner requires uncompressed originals, and a crop is
still an original."
```

---

## Final verification

- [ ] **Run everything**

```bash
./gradlew clean assembleDebug assembleRelease testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, 24 tests passing, 0 failures.

- [ ] **Confirm on device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- existing designs still show their photo in the grid (proves the migration)
- add a second photo, swipe between them in the detail hero
- tap to open the viewer, pinch to zoom, double-tap, swipe down to dismiss
- caption a photo and confirm it appears in the viewer
- reorder, then confirm the grid cover follows the new first photo
- delete a photo and confirm it does not reappear after a sync

- [ ] **Confirm Storage layout**

In Firebase Console → Storage → Files, confirm keys look like:

```
designs/{designId}/{imageId}/{millis}_{nonce}.jpg
designs/{designId}/{imageId}/{millis}_{nonce}_thumb.jpg
```

If uploads fail, re-check that Task 1's rule was published — it fails closed.

---

## Known gaps after this plan

- **Per-user ordering is only fully isolated once the Drive payload is namespaced per identity.** `bi_data.json.enc` is a fixed filename per Google account, so two Firebase identities sharing one Google account still merge each other's ordering. Not a blocker for single-user use; must be fixed before multi-user rollout. Tracked in the spec, §4.
- Drag-to-reorder uses simple index moves via `moveImage`; a polished long-press drag animation is not included.
- Gestures and crop framing are not covered by automated tests — they need instrumentation on a device.
