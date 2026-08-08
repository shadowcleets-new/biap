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
| D2 | **Ordering and cover are per-user** | Each person's arrangement must survive untouched, "no matter how much reshuffling happens". |
| D3 | Concurrent adds **union**; deletes use tombstones | Losing a colleague's photo is worse than carrying an extra row. |
| D4 | Approach A — **child table** `design_images` | Mirrors `size_inventories`, already proven in this schema. |
| D5 | Vestigial columns on `designs` are **kept, not dropped** | `minSdk 24` ⇒ SQLite 3.9, no `DROP COLUMN`. Rebuilding the table risks rows. |
| D6 | Per-image **captions** | Requested. |
| D7 | **In-app cropping** produces a new version of the same image | Requested. Reuses the existing versioning, so a bad crop is revertible. |
| D8 | **Thumbnails** generated at upload | Requested. Originals stay uncompressed; the thumbnail is an additional derived asset. |

> **D2 supersedes an earlier decision** that "position 0 is the cover" as a single shared value. Ordering is now personal, so each user's first photo is their own cover.

### Rejected alternatives

**JSON list column on `designs`.** Less code, but union-by-id would mean comparing lists inside a serialized blob and tombstones have nowhere natural to live — reimplementing relational semantics inside a string. Rejected on correctness, not effort.

**Keep `imageRef` as cover, gallery in a side table.** Smallest migration, but two sources of truth for "a design's photos".

**Shared order with fractional indexing.** Converges deterministically and keeps a canonical cover, but does not give each person their own arrangement — the last deliberate reorder wins for everyone. Rejected against D2.

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
  val position: Int,            // this user's order; 0 = this user's cover
  val localPath: String?,       // device-local copy
  val imageRef: String?,        // Storage key of the current version; null until uploaded
  val thumbRef: String?,        // Storage key of the derived thumbnail; null until uploaded
  val caption: String?,         // D6
  val updatedAt: Long,          // content/caption LWW
  val positionUpdatedAt: Long,  // ordering LWW, resolved independently — see §4
  val updatedBy: String?
)

@Entity(tableName = "deleted_design_images")
data class DeletedDesignImageEntity(
  @PrimaryKey val id: String,   // the image id
  val deletedAt: Long
)
```

`id` is generated on the device that adds the photo and never mutates. Two devices adding photos offline therefore produce different ids and cannot collide — union falls out of the model rather than needing special-casing.

### No separate ordering table is required

`position` lives directly on `design_images` because **the Room database is already per-identity**:

```kotlin
getDatabaseSuffix(userKey)  ⇒  bi_database_<md5(firebaseUid)>.db
```

Every Firebase identity opens its own database file. A `position` column is therefore inherently that user's ordering — a `(userId, designId, imageId)` table would duplicate what the file path already encodes. This is a material simplification over the obvious design.

### Migration v4 → v5

1. `CREATE TABLE design_images` and `deleted_design_images`
2. For each design with a non-null `imagePath` or `imageRef`, insert one row at `position = 0`
3. Leave `designs.imagePath`, `imageRef`, `imageUpdatedAt`, `imageUpdatedBy` in place, unused

The id is generated **in SQL**, since a Room migration cannot call Kotlin:

```sql
INSERT INTO design_images
  (id, designId, position, localPath, imageRef, thumbRef, caption,
   updatedAt, positionUpdatedAt, updatedBy)
SELECT lower(hex(randomblob(16))), id, 0, imagePath, imageRef, NULL, NULL,
       imageUpdatedAt, imageUpdatedAt, imageUpdatedBy
FROM designs
WHERE imagePath IS NOT NULL OR imageRef IS NOT NULL;
```

`lower(hex(randomblob(16)))` yields a 32-char random hex id — not RFC-4122 formatted, but the id is an opaque merge key and never parsed. Ids generated in-app afterwards use `UUID.randomUUID()`; the two coexist.

Nothing is deleted. The existing cover becomes photo 1. The four vestigial columns stay declared on `DesignEntity` (deprecated, unread) so Room's schema validation still matches the table; dropping them needs a full table rebuild and can be done later.

Registered via `AppDatabase.ALL_MIGRATIONS` alongside `MIGRATION_3_4`, wired into all five database builders.

## 4. Sync, merge and ordering

`BusinessIntelligenceData` gains:

```kotlin
val designImages: List<DesignImage> = emptyList(),
val deletedDesignImages: Map<String, Long> = emptyMap()   // imageId -> deletedAt
```

Both default, so an older `bi_data.json.enc` payload still decodes and arrives with empty lists. Existing Drive backups keep working.

`SyncMerger`, reusing the shape already proven for designs:

```
1. tombstones        union, keeping max(deletedAt) per image id
2. images            group by image id; content resolves by updatedAt (per-image LWW)
3. ordering          position resolves by positionUpdatedAt, INDEPENDENTLY of content
4. drop              any image whose deletedAt > its updatedAt
5. prune tombstones  for images re-added after deletion
```

Step 3 is the same principle already applied to the image pointer: **captioning a photo must not drag a stale position along with it**, exactly as editing a price must not drag a stale photo.

### What "personal ordering" means, and what guarantees it

Ordering is per-user because the database is per-user. Concretely:

- **Same user, two devices** — order *should* sync. Priya reorders on her phone; her tablet follows. Resolved by `positionUpdatedAt` LWW. This is correct and wanted.
- **Different users** — order must never merge. Priya's arrangement is hers; Raj's is his.

The second guarantee holds **only if each identity's sync payload is separate.** It is not, today: `bi_data.json.enc` is a fixed filename in a Google account's `appDataFolder`, so two Firebase identities connecting the same Google account merge everything, including order.

> **Dependency.** D2 is not actually delivered until the Drive payload is namespaced per identity — the "Drive sync key contention" issue already documented but not yet fixed. Until then, ordering is per-*database*, which equals per-user only while each user uses a distinct Google account for Drive.
>
> This must be resolved before multi-user rollout. It is not a blocker for single-user use today.

### New photos arriving from another user

A photo added by someone else has no position in your ordering. It is **appended** at `max(position) + 1` on first sight, never inserted into the middle. Your existing arrangement is undisturbed; the new photo simply shows up at the end.

### Position hygiene

Merged rows may still collide on a position or leave gaps (two devices of the *same* user, both offline). The repository **renormalises positions to 0..n-1 on read**, ordered by `position` then `id` as a stable tiebreak. This is now a within-user concern only, and cannot reshuffle one person's order because of another person's edit.

## 5. Upload pipeline and storage

### Key layout

```
before  designs/{designId}/{millis}_{nonce}.jpg
after   designs/{designId}/{imageId}/{millis}_{nonce}.jpg
        designs/{designId}/{imageId}/{millis}_{nonce}_thumb.jpg
```

The extra segment gives **each photo its own 5-version history**. `pruneToLatest` already operates on a prefix; pointing it at `designs/{designId}/{imageId}/` keeps photo A's versions safe from photo B's. Without the nesting, adding a second photo would silently begin deleting the first photo's history. `versionsToPrune` is unchanged, but must treat `*_thumb.jpg` as belonging to its parent version so a thumbnail is never orphaned or counted as a separate version.

### Thumbnails (D8)

Generated at upload from the original, longest edge 400px, uploaded alongside as `_thumb.jpg`. **The original is uploaded untouched** — the thumbnail is an additional derived asset, not compression of your photo.

Used by the portfolio grid and the editor's thumbnail strip. The detail hero and the full-screen viewer always load the original. A 10-photo design pulls ~400 KB of thumbnails instead of ~50 MB of originals when merely browsing, which is the difference between usable and broken on a workshop connection.

If thumbnail generation or upload fails, `thumbRef` stays null and callers fall back to the original — degraded, never broken.

### Worker

`ImageUploadWorker` gains `imageId`, uploads original then thumbnail, and moves that image's pointers. The ordering guarantee is unchanged and remains the safety property:

1. upload original bytes to a brand-new key
2. upload the derived thumbnail
3. only then move **this image's** `imageRef` and `thumbRef`
4. only then prune **this image's** history

Unique work becomes `ImageUpload_{designId}_{imageId}`. Under the current per-design naming, adding two photos in quick succession would have the second **cancel the first's upload**.

`DesignDao.updateImagePointer` targets `design_images.id` and writes only the pointer columns.

### Storage security rules — must ship first

The published rule matches a single path segment:

```
match /designs/{designId}/{fileName}
```

New keys have two segments, so uploads would be **denied**. It must become:

```
match /designs/{designId}/{imageId}/{fileName}
```

Publish this **before** shipping the app change — the rule fails closed, so the wrong order produces silent upload failures. Update `storage.rules` in the repo to match.

### Deletion

Removing a photo deletes the row, writes a tombstone, cancels the pending upload by its exact unique work name `ImageUpload_{designId}_{imageId}`, and **leaves the Storage objects in place** — reclaimed by the existing `OrphanImageCleanupWorker` sweep. Cancelling per-design instead would abort uploads for photos being kept.

### Storage ceiling

10 photos × 5 versions × ~5 MB ⇒ ~250 MB per design as an absolute worst case where every slot is filled and re-shot five times. Realistic usage is ~50 MB per fully-photographed design. Thumbnails add ~1%.

## 6. UI

### `ImageViewer` (new, full-screen)

- `HorizontalPager` across the design's images in **this user's** order
- pinch-zoom and pan via `detectTransformGestures` + `graphicsLayer`
- double-tap toggles fit ↔ 2×
- swipe-down to dismiss
- page indicator dots when count > 1
- caption shown as an overlay when present, hidden while zoomed
- **`ContentScale.Fit`** — the first place the whole photo is visible

Zoom state resets per page. Pan is clamped to image bounds. While zoomed, horizontal drag pans rather than paging — otherwise zooming makes swiping feel broken.

### `DesignDetailScreen`

Hero becomes a pager over the same images; tap opens `ImageViewer`. Stays `ContentScale.Crop` — a fixed-height hero wants filling. `Fit` belongs in the viewer.

### `AddDesignScreen` / detail edit mode

Horizontal thumbnail strip plus an add tile, disabled at 10. Long-press to drag-reorder, ✕ to remove, tap to edit caption. The first slot is labelled **Your cover** — wording that makes D2 honest rather than implying it is shared.

### `ImageCropper` (new, D7)

Opened from the thumbnail strip. Pinch/pan to frame within a fixed overlay, plus 90° rotate. Reuses the viewer's transform-gesture code rather than duplicating it.

Saving writes a **new version of the same image id** — new storage key, same `id`, `updatedAt` bumped. The crop therefore inherits everything: versioned upload, keep-newest-5, and per-image merge. **A bad crop is revertible** for as long as the previous version survives retention, because the original object was never overwritten.

Aspect ratio is free-form; no forced square. Cropping is optional and never applied automatically.

### `MainScreen` portfolio grid — a required change, not optional

The grid card currently calls:

```kotlin
rememberDesignImageModel(design.imagePath, design.imageRef)   // MainScreen.kt:601
```

Those are exactly the fields that become vestigial. **Left alone, every card in the portfolio grid would render blank.** The card must read the design's cover — this user's image at `position = 0` — and prefer `thumbRef` over `imageRef`.

`rememberDesignImageModel` changes signature from loose strings to a typed image plus a size preference, so no call site can read the dead columns:

```kotlin
@Composable fun rememberDesignImageModel(
  image: DesignImage?,
  preferThumbnail: Boolean = false
): Any?
```

`Design` exposes `val coverImage: DesignImage? get() = images.firstOrNull()` so the grid reads intent rather than an index.

## 7. Error handling

| Case | Behaviour |
|---|---|
| Photo not yet uploaded | Local file renders; local-first precedence unchanged |
| No local copy, remote unresolved | Placeholder on that page; other pages still swipe |
| Upload fails | Photo stays local and visible; worker retries; nothing lost |
| Thumbnail missing or failed | Fall back to the original; degraded, not broken |
| Delete during pending upload | Cancel that image's unique work, then write the tombstone |
| 11th photo attempted | Add tile disabled — blocked before insert, not rejected after |
| Crop fails or is cancelled | No new version written; previous image untouched |
| Migrated design with no photo | Empty list; viewer not offered |
| Photo added by another user | Appended at end of your order; your arrangement undisturbed |

## 8. Testing

Extending the existing suite in the same style:

- **`SyncMergerImageTest`** — union: both devices add ⇒ both survive; delete beats older edit; re-add after delete clears the tombstone; **caption edit does not drag a stale position**; **reorder does not drag a stale caption**
- **`ImageStoreRetentionTest`** — per-image prefix: photo A's versions are never pruned by photo B's; a thumbnail is never counted as an independent version
- **Position renormalisation** (pure function) — gaps close, duplicates break deterministically by id, a foreign photo appends at the end rather than inserting
- **Migration v4 → v5** — against a synthetic v4 database, as v3 → v4 was: row counts preserved, existing photo lands at position 0, existing columns untouched

**Not automated:** gestures (pinch, pan, fling, drag-reorder) and crop framing. These need instrumentation on a device; value-per-effort is poor and problems are obvious within seconds of manual use.

## 9. Out of scope

- filters or colour adjustment (crop and rotate only)
- compression of originals — explicitly rejected by the owner
- org-scoped Storage rules — waits on the Phase 1 organisation model
- video

## 10. Implementation order

Each step depends on the last, and two fail closed:

1. **Republish `storage.rules`** with the `{imageId}` segment — before any app change
2. **Schema**: entities, migration v4 → v5, DAO. Verified against a synthetic v4 database first
3. **Domain + merge**: `DesignImage`, `Design.images`, union, independent position/content LWW, append-foreign-photos. Unit-tested before any UI depends on it
4. **Pipeline**: per-image keys, thumbnails, `ImageUploadWorker`, pointer writes, retention prefix
5. **UI**: `MainScreen` cover (the step that un-breaks the grid), detail pager, thumbnail strip, captions, `ImageViewer`
6. **Cropper**: last, because it reuses the viewer's gesture code

Steps 2–6 each build and pass tests independently, so the work is reviewable in slices rather than one large diff.

## 11. Consequences

The single-image pipeline built on 2026-08-07 is **rewritten, not extended**. Its core ideas carry over unchanged — versioned write-once objects, pointer-moves-after-upload ordering, keep-newest-5 retention, local-first display — but the plumbing moves from per-design to per-image.

`storage.rules` must be republished before the app change ships.

**D2 is only fully delivered once the Drive payload is namespaced per identity.** Until that is fixed, two Firebase identities sharing one Google Drive account will still merge each other's ordering. Single-user use is unaffected.
