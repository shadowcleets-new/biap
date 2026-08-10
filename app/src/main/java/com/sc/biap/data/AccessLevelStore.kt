package com.sc.biap.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * @file AccessLevelStore.kt
 * @description Who may do what. The only thing this app keeps outside Google Drive.
 *
 * WHY THIS IS NOT IN DRIVE WITH EVERYTHING ELSE:
 *   A person's own Drive folder is a folder that person can edit. A role stored there is a
 *   role its holder can rewrite, and "assigned by an admin" would be a label on a field
 *   anybody could change. Enforcement has to live somewhere the subject does not control.
 *
 *   Firestore holds users/{uid} with a role and just enough identity - a name and one
 *   contact - for an access manager to tell colleagues apart. Age, designation and photo
 *   stay in Drive; assigning someone a role does not require knowing their birthday.
 *
 * THE RULES ARE THE ENFORCEMENT, NOT THIS CLASS:
 *   Every restriction below is also written in firestore.rules and checked by the server.
 *   The checks here exist so the UI can disable a control instead of offering an action
 *   that will bounce. A tampered client gains nothing: it can call [assignRole] with any
 *   pair it likes and the server will still refuse.
 *
 * FAILS CLOSED:
 *   Offline, missing document or unreadable value all yield [AccessLevel.DEFAULT], which is
 *   VIEWER. An outage must never hand out privileges. [Result.isAuthoritative] lets the UI
 *   say "could not confirm" rather than asserting somebody is a Viewer when the lookup
 *   simply failed.
 */
object AccessLevelStore {

    private const val TAG = "AccessLevelStore"
    private const val COLLECTION = "users"

    private const val FIELD_ROLE = "role"
    private const val FIELD_NAME = "displayName"
    private const val FIELD_CONTACT = "contact"
    private const val FIELD_ROLE_UPDATED_AT = "roleUpdatedAt"
    private const val FIELD_ROLE_UPDATED_BY = "roleUpdatedBy"

    private val users get() = FirebaseFirestore.getInstance().collection(COLLECTION)

    data class Result(
        val level: AccessLevel,
        /** False when [level] is the fail-closed fallback rather than a value actually read. */
        val isAuthoritative: Boolean,
    )

    /** The signed-in person's own level. */
    suspend fun current(): Result = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result(AccessLevel.DEFAULT, isAuthoritative = false)

        return@withContext try {
            val snapshot = users.document(uid).get().await()
            if (!snapshot.exists()) {
                Log.i(TAG, "no role document for uid=$uid; treating as ${AccessLevel.DEFAULT}")
                // Genuinely unassigned rather than unreadable, so this answer is real.
                Result(AccessLevel.DEFAULT, isAuthoritative = true)
            } else {
                Result(AccessLevel.fromName(snapshot.getString(FIELD_ROLE)), isAuthoritative = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not read role: ${e.message}")
            Result(AccessLevel.DEFAULT, isAuthoritative = false)
        }
    }

    /**
     * Make sure this account appears in the directory, so an admin can find them.
     *
     * Called on sign-in. Creates the record at VIEWER - which the rules also pin, so a
     * tampered client cannot arrive pre-promoted - and afterwards only ever refreshes the
     * name and contact. The role is deliberately absent from the update path: a write that
     * carries a role is a write the server rejects for anyone editing their own record.
     */
    suspend fun publishIdentity(): Unit = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext
        try {
            val document = users.document(user.uid)
            val existing = document.get().await()

            if (!existing.exists()) {
                document.set(
                    mapOf(
                        FIELD_ROLE to AccessLevel.DEFAULT.name,
                        FIELD_NAME to displayNameOf(user),
                        FIELD_CONTACT to contactOf(user),
                    )
                ).await()
                Log.i(TAG, "published identity for uid=${user.uid} at ${AccessLevel.DEFAULT}")
                return@withContext
            }

            // Identity only. Including the role here would be rejected the moment it
            // differed from what is stored, and must never be sent by a self-write.
            document.set(
                mapOf(
                    FIELD_NAME to displayNameOf(user),
                    FIELD_CONTACT to contactOf(user),
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            // Never block sign-in over the directory; the person simply reads as a Viewer
            // until this succeeds on a later launch.
            Log.w(TAG, "could not publish identity: ${e.message}")
        }
    }

    /**
     * Everyone in the organisation. Readable only by an access manager - the rules refuse
     * the query for anybody else, so this returns empty rather than a partial list.
     */
    suspend fun members(): List<OrgMember> = withContext(Dispatchers.IO) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        return@withContext try {
            users.get().await().documents.map { document ->
                OrgMember(
                    uid = document.id,
                    displayName = document.getString(FIELD_NAME).orEmpty().ifBlank { "Unnamed" },
                    contact = document.getString(FIELD_CONTACT).orEmpty(),
                    level = AccessLevel.fromName(document.getString(FIELD_ROLE)),
                    isSelf = document.id == myUid,
                )
            }.sortedWith(compareBy({ it.level.ordinal }, { it.displayName.lowercase() }))
        } catch (e: Exception) {
            Log.w(TAG, "could not list members: ${e.message}")
            emptyList()
        }
    }

    /**
     * Move [member] to [newLevel]. Returns null on success, or a reason to show the user.
     *
     * Writes only the role fields, which is also all the rules permit down this path - so
     * it cannot be used to quietly rewrite a colleague's name while the door is open.
     */
    suspend fun assignRole(
        actorLevel: AccessLevel,
        member: OrgMember,
        newLevel: AccessLevel,
    ): String? = withContext(Dispatchers.IO) {
        val actorUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext "You are not signed in."

        // Checked here purely to give a clear message; the server checks it regardless.
        if (member.uid == actorUid) {
            return@withContext "You cannot change your own access level."
        }
        if (!actorLevel.canAssign(member.level, newLevel)) {
            return@withContext "Your access level does not allow that change."
        }

        return@withContext try {
            users.document(member.uid).update(
                mapOf(
                    FIELD_ROLE to newLevel.name,
                    FIELD_ROLE_UPDATED_AT to FieldValue.serverTimestamp(),
                    FIELD_ROLE_UPDATED_BY to actorUid,
                )
            ).await()
            Log.i(TAG, "set ${member.uid} to $newLevel")
            null
        } catch (e: Exception) {
            Log.w(TAG, "could not set role for ${member.uid}: ${e.message}")
            "The server refused that change. ${e.localizedMessage.orEmpty()}".trim()
        }
    }

    private fun displayNameOf(user: FirebaseUser): String =
        user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: user.phoneNumber
            ?: "Unnamed"

    private fun contactOf(user: FirebaseUser): String =
        user.email?.takeIf { it.isNotBlank() } ?: user.phoneNumber.orEmpty()
}
