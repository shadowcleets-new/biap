# Multi-image gallery with e-commerce style viewing

**Date:** 2026-08-08
**Status:** Approved, ready for implementation planning
**Supersedes:** the single-image pipeline introduced in `3e38f58` / `b25b0f3` / `7e932c1`

---

## 1. Problem

A design currently holds exactly one photo. Viewing it is poor:

- the detail hero renders with `ContentScale.Crop` in a fixed 280dp box, so **the whole photo can never be seen**
- there is no zoom, no pan, no full-screen view
- there is no way to hold more than one photo, so nothing to swipe between

For stock management the owner needs several photos per design (front, back, fabric detail, colourway) viewable the way an e-commerce listing behaves.

## 2. Decisions taken

| # | Decision | Rationale |
|---|---|---|
| D1 | Multiple images per design, capped at **10** | The stated requirement. Cap bounds storage and keeps the viewer sane. |
| D2 | **Position 0 is the cover**, drag to reorder | One concept, no hidden flag. Edit order equals swipe order. |
| D3 | Concurrent adds **union**; deletes use tombstones | Losing a colleague's photo is worse than carrying an extra row. |
| D4 | Approach A — **child table** `design_images` | Mirrors `size_inventories`, already proven in this schema. |
| D5 | Vestigial columns on `designs` are **kept, not dropped** | `minSdk 24` ⇒ SQLite 3.9, no `DROP COLUMN`. Rebuilding the table risks rows. |

### Rejected alternatives

**JSON list column on `designs`.** Less code, but union-by-id would mean comparing lists inside a serialized blob and tombstones have nowhere natural to live — reimplementing relational semantics inside a string. Rejected on correctness, not effort.

**Keep `imageRef` as cover, gallery in a side table.** Smallest migration, but contradicts D2: dragging a gallery photo to the cover slot would move a row between two different stores, and "a design's photos" would have two sources of truth.

## 3. Data model

```kotlin
@Entity(
  tableName = "design_images",
  foreignKeys = [ForeignKey(
    entity = DesignEntity::class,
    parentColumns = ["id"], childColumns = ["designId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("designId")]
)
data class DesignImageEntity(
  @PrimaryKey val id: String,   // device-generated UUID; the merge key, never changes
  val designId: String,
  val position: Int,            // 0 = cover
  val localPath: String?,       // device-local copy
  val imageRef: String?,        // Storage key; null until uploaded
  val updatedAt: Long,
  val updatedBy: String?
)

@Entity(tableName = "deleted_design_images")
data class DeletedDesignImageEntity(
  @PrimaryKey val id: String,   // the image id
  val deletedAt: Long
)
```

`id` is generated on the device that adds the photo and never mutates. Two devices adding photos offline therefore produce different ids and cannot collide — union falls out of the model rather than needing special-casing.

### Migration v4 → v5

1. `CREATE TABLE design_images` and `deleted_design_images`
2. For each design with a non-null `imagePath` or `imageRef`, insert one row at `position = 0` carrying the existing values, `updatedAt = designs.imageUpdatedAt`, `updatedBy = designs.imageUpdatedBy`
3. Leave `designs.imagePath`, `imageRef`, `imageUpdatedAt`, `imageUpdatedBy` in place, unused

The id is generated **in SQL**, since a Room migration cannot call Kotlin:

```sql
INSERT INTO design_images (id, designId, position, localPath, imageRef, updatedAt, updatedBy)
SELECT lower(hex(randomblob(16))), id, 0, imagePath, imageRef, imageUpdatedAt, imageUpdatedBy
FROM designs
WHERE imagePath IS NOT NULL OR imageRef IS NOT NULL;
```

`lower(hex(randomblob(16)))` yields a 32-char random hex id — not RFC-4122 formatted, but the id is an opaque merge key and never parsed, so the format is irrelevant. Ids generated in-app afterwards use `UUID.randomUUID()`; the two coexist without issue.

Nothing is deleted. The existing cover becomes photo 1.

The four vestigial columns stay declared on `DesignEntity` (marked deprecated and unread) so Room's schema validation still matches the table. Dropping them needs a full table rebuild and can be done later in a dedicated migration if there is appetite.

Registered via `AppDatabase.ALL_MIGRATIONS` alongside `MIGRATION_3_4`, wired into all five database builders.

## 4. Sync and merge

`BusinessIntelligenceData` gains:

```kotlin
val designImages: List<DesignImage> = emptyList(),
val deletedDesignImages: Map<String, Long> = emptyMap()   // imageId -> deletedAt
```

Both default, so an older `bi_data.json.enc` payload still decodes and simply arrives with empty lists. Existing Drive backups keep working.

`SyncMerger` algorithm, reusing the shape already proven for designs:

```
1. tombstones       union, keeping max(deletedAt) per image id
2. images           group by image id; each resolves by its own updatedAt (per-image LWW)
3. drop             any image whose deletedAt > its updatedAt
4. prune tombstones for images re-added after deletion
```

### Position under concurrent edits

`position` is an ordinary column resolved by per-image LWW. If two people reorder simultaneously the later edit wins the ordering, but **no photo is lost**. Merged rows may collide on a position or leave gaps.

The repository therefore **renormalises positions to 0..n-1 on read**, ordered by `position` then `id` as a stable tiebreak. Order may shuffle after a conflicting reorder; photos never vanish or duplicate.

This is a deliberate trade. Correct distributed ordering needs fractional indexing or CRDTs — disproportionate machinery for an atelier reordering photographs.

## 5. Upload pipeline and storage

### Key layout

```
before  designs/{designId}/{millis}_{nonce}.jpg
after   designs/{designId}/{imageId}/{millis}_{nonce}.jpg
```

The extra segment gives **each photo its own 5-version history**. `pruneToLatest` already operates on a prefix; pointing it at `designs/{designId}/{imageId}/` keeps photo A's versions safe from photo B's. Without the nesting, adding a second photo would silently begin deleting the first photo's history. `versionsToPrune` is unchanged and its 7 tests still hold.

### Worker

`ImageUploadWorker` gains `imageId` in its input data and moves that image's pointer. The ordering guarantee is unchanged and remains the safety property:

1. upload bytes to a brand-new key
2. only then move **this image's** `imageRef`
3. only then prune **this image's** history

Unique work becomes `ImageUpload_{designId}_{imageId}`. Under the current per-design naming, adding two photos in quick succession would have the second **cancel the first's upload**.

`DesignDao.updateImagePointer` targets `design_images.id`. It stays a single-column write for the same reason as before: a background upload must not overwrite fields the user edited while it was in flight.

### Storage security rules — must ship first

The published rule matches a single path segment:

```
match /designs/{designId}/{fileName}
```

New keys have two segments, so uploads would be **denied**. The rule must become:

```
match /designs/{designId}/{imageId}/{fileName}
```

Publish this **before** shipping the app change. The rule fails closed, so getting the order wrong produces silent upload failures. Update `storage.rules` in the repo to match.

### Deletion

Removing a photo deletes the row, writes a tombstone, cancels the pending upload for that image by its unique work name `ImageUpload_{designId}_{imageId}`, and **leaves the Storage objects in place** — reclaimed by the existing `OrphanImageCleanupWorker` sweep. Bytes are never destroyed on a path that might be wrong.

Cancelling by that exact name matters: cancelling per-design would abort uploads for photos the user is keeping.

### Storage ceiling

10 photos × 5 versions × ~5 MB ⇒ ~250 MB per design as an absolute worst case where every slot is filled and re-shot five times. Realistic usage (photos added once, rarely replaced) is ~50 MB per fully-photographed design. The 10-photo cap is what bounds this.

## 6. UI

### `ImageViewer` (new, full-screen)

- `HorizontalPager` across the design's images
- pinch-zoom and pan via `detectTransformGestures` + `graphicsLayer`
- double-tap toggles fit ↔ 2×
- swipe-down to dismiss
- page indicator dots when count > 1
- **`ContentScale.Fit`** — the first place the whole photo is visible

Zoom state resets per page. Pan is clamped to image bounds. While zoomed, horizontal drag pans rather than paging — otherwise zooming makes swiping feel broken.

### `DesignDetailScreen`

Hero becomes a pager over the same images; tap opens `ImageViewer`. Stays `ContentScale.Crop` — a fixed-height hero wants filling. `Fit` belongs in the viewer.

### `AddDesignScreen` / detail edit mode

Horizontal thumbnail strip plus an add tile, disabled at 10. Long-press to drag-reorder, ✕ to remove. The first slot is labelled **Cover** so D2 is visible rather than folklore.

### `MainScreen` portfolio grid — a required change, not optional

The grid card currently calls:

```kotlin
rememberDesignImageModel(design.imagePath, design.imageRef)   // MainScreen.kt:601
```

Those are exactly the fields that become vestigial. **Left alone, every card in the portfolio grid would render blank.** The card must instead read the design's cover — the image at `position = 0`.

`rememberDesignImageModel` therefore changes signature from loose `(imagePath, imageRef)` strings to taking a `DesignImage`, so no call site can accidentally read the dead columns:

```kotlin
@Composable fun rememberDesignImageModel(image: DesignImage?): Any?
```

`Design` exposes `val coverImage: DesignImage? get() = images.firstOrNull()` so the grid reads intent rather than an index.

## 7. Error handling

| Case | Behaviour |
|---|---|
| Photo not yet uploaded | Local file renders; local-first precedence unchanged |
| No local copy, remote unresolved | Placeholder on that page; other pages still swipe |
| Upload fails | Photo stays local and visible; worker retries; nothing lost |
| Delete during pending upload | Cancel the unique work, then write the tombstone |
| 11th photo attempted | Add tile disabled — blocked before insert, not rejected after |
| Migrated design with no photo | Empty list; viewer not offered |

## 8. Testing

Extending the existing suite in the same style:

- **`SyncMergerImageTest`** — union cases: both devices add ⇒ both survive; delete beats older edit; re-add after delete clears the tombstone; concurrent reorder loses ordering but keeps every photo
- **`ImageStoreRetentionTest`** — per-image prefix: photo A's versions are never pruned by photo B's
- **Position renormalisation** (pure function) — gaps close, duplicate positions break deterministically by id, cover stays cover
- **Migration v4 → v5** — against a synthetic v4 database, as v3 → v4 was: row counts preserved, existing photo lands at position 0, existing columns untouched

**Not automated:** the gestures themselves. Pinch, pan and fling need instrumentation tests on a device; value-per-effort is poor and problems are obvious within seconds of manual use.

## 9. Out of scope

Deliberately excluded — each is a separate feature:

- per-image captions or metadata
- in-app cropping, rotation or filters
- image compression (owner requires uncompressed originals)
- thumbnail generation (rejected earlier; egress is not the binding constraint on Firebase Storage at this scale)
- org-scoped Storage rules (waits on the Phase 1 organisation model)

## 10. Implementation order

The change is large but coherent. It must land in this order, because each step depends on the last and two of them fail closed:

1. **Republish `storage.rules`** with the `{imageId}` segment — before any app change. The rule fails closed; wrong order means silent upload failures.
2. **Schema**: entities, migration v4 → v5, DAO. Verified against a synthetic v4 database before anything reads it.
3. **Domain + merge**: `DesignImage`, `Design.images`, `SyncMerger` union, position renormalisation. Unit-tested here, before any UI depends on it.
4. **Pipeline**: per-image keys, `ImageUploadWorker`, pointer writes, retention prefix.
5. **UI**: `MainScreen` cover (this is the step that un-breaks the grid), detail pager, thumbnail strip, `ImageViewer`.

Steps 2–5 can each build and pass tests independently, so the work is reviewable in slices rather than one large diff.

## 11. Consequences

The single-image pipeline built on 2026-08-07 is **rewritten, not extended**. Its core ideas carry over unchanged — versioned write-once objects, pointer-moves-after-upload ordering, keep-newest-5 retention, local-first display — but the plumbing moves from per-design to per-image.

`storage.rules` must be republished before the app change ships.
