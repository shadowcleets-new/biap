package com.sc.biap.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class SizeInventory(
    val size: String,
    val stock: Int,
    val price: Long
)

/**
 * One photograph of a design.
 *
 * Carries the same pair the cover has always carried: a local file that works offline and
 * immediately, and a storage key for the uploaded copy that lets somebody else's device see
 * it. Either may be absent - a photo just taken has no key yet, and a photo taken on another
 * device has no local file here.
 */
@Serializable
@Immutable
data class DesignPhoto(
    val localPath: String? = null,
    val ref: String? = null,
    val uploadedBy: String? = null,
) {
    val isEmpty: Boolean get() = localPath.isNullOrBlank() && ref.isNullOrBlank()
}

@Serializable
@Immutable
data class Design(
    val id: String,
    val name: String,
    val category: String,
    // Local cache path. Remains authoritative offline and for images not yet uploaded.
    val imagePath: String? = null,
    // Storage key of the CURRENT image version, e.g. "designs/<id>/1707_a3f9.jpg".
    // Versioned: replacing a photo writes a new key and repoints here, so a blob is
    // never overwritten in place.
    val imageRef: String? = null,
    // The image pointer carries its OWN timestamp so it merges independently of the
    // rest of the design - editing a price must not clobber a newer photo.
    val imageUpdatedAt: Long = 0L,
    val imageUpdatedBy: String? = null,
    val sizeInventories: List<SizeInventory> = emptyList(),
    val customFields: Map<String, String> = emptyMap(),
    val karigars: List<String> = emptyList(),
    /**
     * When this design was archived, or 0 if it is active.
     *
     * Archiving replaced deletion as the ordinary way to retire a design: a discontinued
     * style is history worth keeping, not a mistake to erase. Its stock, prices and karigars
     * stay readable, it simply leaves the working catalogue.
     */
    val archivedAt: Long = 0L,
    /**
     * When [archivedAt] last changed - NOT when the design last changed.
     *
     * Carried separately for the same reason [imageUpdatedAt] is. Whole-record last-write-wins
     * would mean someone editing a price on a device holding a stale archive state drags that
     * stale state along and silently un-archives the design. Winning the record and winning
     * the archive flag are two different questions.
     */
    val archiveUpdatedAt: Long = 0L,
    /**
     * Photographs BEYOND the cover. The cover stays in `imagePath`/`imageRef` above.
     *
     * WHY THE COVER IS NOT JUST photos[0]:
     *   Those three cover fields are load-bearing everywhere - the catalogue row, the order
     *   carousel, the upload worker, the orphan sweep, and an independent merge clause that
     *   stops a price edit dragging a stale photo along. Collapsing them into a list would
     *   have meant rewriting all of that at once, and a build that predates this field would
     *   read the new payload and find no photograph at all. Leaving the cover exactly where
     *   it is means an older device still sees the main photo of every design.
     *
     *   [photos] hides the split, so callers work with one list and never have to care.
     */
    val extraImages: List<DesignPhoto> = emptyList(),
    /** When [extraImages] last changed. Merged on its own clock, as the cover pointer is. */
    val extraImagesUpdatedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isArchived: Boolean get() = archivedAt > 0L

    /** Every photograph, cover first. Empty when the design has never been photographed. */
    val photos: List<DesignPhoto>
        get() = buildList {
            val cover = DesignPhoto(imagePath, imageRef, imageUpdatedBy)
            if (!cover.isEmpty) add(cover)
            addAll(extraImages.filterNot { it.isEmpty })
        }

    /**
     * Replace the whole set of photographs, cover first, capped at [MAX_PHOTOS].
     *
     * The two timestamps move only when their own half actually changed. Bumping the cover's
     * clock because a second photo was added would let that write win the cover merge against
     * a genuinely newer cover uploaded elsewhere - the exact trap `imageUpdatedAt` exists to
     * avoid.
     */
    fun withPhotos(photos: List<DesignPhoto>, now: Long = System.currentTimeMillis()): Design {
        val kept = photos.filterNot { it.isEmpty }.take(MAX_PHOTOS)
        val cover = kept.firstOrNull()
        val extras = kept.drop(1)
        val coverMoved = cover?.localPath != imagePath || cover?.ref != imageRef
        return copy(
            imagePath = cover?.localPath,
            imageRef = cover?.ref,
            imageUpdatedBy = cover?.uploadedBy,
            imageUpdatedAt = if (coverMoved) now else imageUpdatedAt,
            extraImages = extras,
            extraImagesUpdatedAt = if (extras != extraImages) now else extraImagesUpdatedAt,
        )
    }

    companion object {
        /** Photographs one design may carry, cover included. */
        const val MAX_PHOTOS = 5
    }
}

@Serializable
@Immutable
data class BusinessIntelligenceData(
    val designs: List<Design> = emptyList(),
    /** Pieces out with karigars. Defaulted like the rest, so an older payload still loads. */
    val jobWork: List<JobWork> = emptyList(),
    val deletedJobWork: Map<String, Long> = emptyMap(),
    val globalCustomFields: Set<String> = emptySet(),
    val globalCustomFieldsMetadata: Map<String, Long> = emptyMap(),
    val deletedDesignIds: Set<String> = emptySet(),
    val deletedDesigns: Map<String, Long> = emptyMap(),
    val deletedCustomFields: Map<String, Long> = emptyMap(),
    /**
     * Orders, backed up alongside the designs.
     *
     * Defaulted for the same reason [profile] is: a payload written by a build that predates
     * orders carries neither of these keys, and must still parse rather than failing the
     * whole sync.
     */
    val orders: List<Order> = emptyList(),
    /** Cancelled orders, id to when. Without these a cancelled order returns on next sync. */
    val deletedOrders: Map<String, Long> = emptyMap(),
    /**
     * The owner's own details. Null on payloads written before profiles existed, which is
     * why it is defaulted rather than required - an older device's upload must still parse.
     */
    val profile: UserProfile? = null,
    /**
     * Batches, the ledger behind each size's stock. Defaulted like every field added after
     * the first release: a payload from an older build carries no batches at all and must
     * still parse rather than failing the whole sync.
     */
    val batches: List<Batch> = emptyList(),
    /** The workshop's people, and the activities each is assigned to. */
    val karigars: List<Karigar> = emptyList(),
    val deletedKarigars: Map<String, Long> = emptyMap(),
    /** Activity names, with when each was last touched, so they merge like custom fields. */
    val activities: Map<String, Long> = emptyMap(),
    val deletedActivities: Map<String, Long> = emptyMap(),
)
