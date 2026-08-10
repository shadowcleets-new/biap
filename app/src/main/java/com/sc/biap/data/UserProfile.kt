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
/**
 * The organisation, most privileged first.
 *
 * [tier] carries the hierarchy rather than the declaration order, because Manager and
 * Accountant are PEERS - both sit exactly one level below Admin, neither above the other.
 * Listing one before the other is a display choice, and using ordinal as rank would quietly
 * turn that choice into a demotion.
 */
enum class AccessLevel(val label: String, val description: String, val tier: Int) {
    SUPER_ADMIN("Super Admin", "Above all. Can appoint or remove Admins", tier = 0),
    ADMIN("Admin", "Full access, and can set access for everyone below Admin", tier = 1),
    MANAGER("Manager", "Can add and edit designs, stock and pricing", tier = 2),
    ACCOUNTANT("Accountant", "Can see and edit pricing and costs across the catalogue", tier = 2),
    SUPPORT_STAFF("Support Staff", "Can add and edit designs and stock, but not pricing", tier = 3),

    // Not in the requested hierarchy, and deliberately kept: something has to be the
    // fail-closed answer when a role cannot be read, and it must grant the least possible.
    // Promoting an unreadable role to Support Staff would mean a Firestore outage handing
    // out edit rights.
    VIEWER("Viewer", "Can look at the catalogue but change nothing", tier = 99);

    /** May open the access-management screen at all. */
    val canManageAccess: Boolean get() = this == SUPER_ADMIN || this == ADMIN

    /**
     * Whether this level may move [target] to [newLevel].
     *
     * Mirrors firestore.rules exactly. The rules are what actually enforce this - these
     * checks only stop the app offering a change it knows the server will reject, so the
     * user gets a disabled control instead of a failure after the fact. If the two ever
     * disagree, the server wins and the app is the one that is wrong.
     */
    fun canAssign(target: AccessLevel, newLevel: AccessLevel): Boolean = when (this) {
        // A Super Admin may set anything for anyone.
        SUPER_ADMIN -> true
        // An Admin manages everyone BELOW Admin, and cannot mint peers or superiors.
        // Without this, any Admin could promote themselves a deputy and the distinction
        // between the two top levels would not survive the first afternoon.
        ADMIN -> !target.canManageAccess && !newLevel.canManageAccess
        else -> false
    }

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

/**
 * A person in the organisation as an access-manager sees them.
 *
 * Carries only what is needed to tell one colleague from another when assigning a role.
 * Their age, designation and photo stay in their own Drive folder and are none of an
 * admin's business.
 */
data class OrgMember(
    val uid: String,
    val displayName: String,
    val contact: String,
    val level: AccessLevel,
    val isSelf: Boolean,
)
