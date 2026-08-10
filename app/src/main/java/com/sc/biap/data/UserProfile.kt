package com.sc.biap.data

import kotlinx.serialization.Serializable

/**
 * @file UserProfile.kt
 * @description Who a person is, and what they are allowed to do.
 *
 * THESE TWO THINGS ARE STORED IN DIFFERENT PLACES, ON PURPOSE:
 *
 *   Personal details - name, age, designation, photo - are the user's own data. They ride
 *   with the catalogue in the same Google Drive appDataFolder, so they restore on reinstall
 *   through machinery that already works.
 *
 *   [AccessLevel] does NOT. A role kept in the user's own private folder would be a role the
 *   user can edit, and "assigned by an admin" would be decoration - anyone could promote
 *   themselves by editing their own file. It lives in Firestore under users/{uid}, where a
 *   security rule can permit the owner to read it while only an admin may write it.
 *
 *   That split is the whole reason a role is trustworthy. It is deliberately the ONLY thing
 *   kept outside Drive, so the second source stays as small as possible.
 */
@Serializable
data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    /** Blank when not given. Stored as text so an unanswered age is not a misleading 0. */
    val age: String = "",
    val designation: String = "",
    /**
     * Either a DriveImageStore key for a photo the user chose, or an https URL taken from
     * their Google account. Both are loadable directly; which one it is does not matter to
     * anything downstream.
     */
    val photoRef: String? = null,
    val updatedAt: Long = 0L,
) {
    val fullName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    val isEmpty: Boolean
        get() = firstName.isBlank() && lastName.isBlank() &&
            age.isBlank() && designation.isBlank() && photoRef.isNullOrBlank()
}

/**
 * What a person may do. Assigned by an admin, never chosen by the person themselves.
 *
 * Ordered from most to least privileged so comparisons stay readable, and stored by name
 * rather than ordinal - inserting a level later must not silently re-grade everybody.
 */
enum class AccessLevel(val label: String, val description: String) {
    ADMIN("Admin", "Full access, and can set what everyone else may do"),
    MANAGER("Manager", "Can add and edit designs, stock and pricing"),
    STAFF("Staff", "Can add and edit designs and stock, but not pricing"),
    VIEWER("Viewer", "Can look at the catalogue but change nothing");

    companion object {
        /**
         * The level a person has until an admin says otherwise.
         *
         * VIEWER, not ADMIN: an unreadable or missing role must fail closed. Defaulting to
         * full access would mean a Firestore outage silently handing everybody the keys.
         */
        val DEFAULT = VIEWER

        fun fromName(raw: String?): AccessLevel =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
