package com.sc.biap.data

import kotlinx.serialization.Serializable

@Serializable
data class SizeInventory(
    val size: String,
    val stock: Int,
    val price: Long
)

@Serializable
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
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isArchived: Boolean get() = archivedAt > 0L
}

@Serializable
data class BusinessIntelligenceData(
    val designs: List<Design> = emptyList(),
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
    val profile: UserProfile? = null
)
