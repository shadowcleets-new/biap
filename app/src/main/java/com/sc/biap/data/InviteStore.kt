package com.sc.biap.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * @file InviteStore.kt
 * @description Invitations to join the organisation, keyed by phone number.
 *
 * WHY THE PHONE NUMBER IS THE DOCUMENT ID:
 *   An invite has to be written BEFORE the person has an account, so there is no uid to file
 *   it under. The number is the only identifier that exists on both sides of the gap: the
 *   admin knows it when inviting, and Firebase puts it in the invitee's token when they sign
 *   in. That token field is minted by Firebase after it has verified the number by SMS, so
 *   the match cannot be forged by a tampered client - which is what makes it safe to let the
 *   invitee apply their own role exactly once.
 *
 * WHAT STOPS AN INVITE BEING A BACK DOOR:
 *   The rules check three things the app cannot talk them out of. An invite may only be
 *   created for a level its author could have assigned directly, so an Admin cannot mint a
 *   Super Admin by writing an invite instead of a role. The invitee may only set their role
 *   to the value in the invite addressed to their own verified number. And the invite is
 *   consumed on use, so a number that later changes hands does not carry the grant with it.
 *
 * SENDING IS DELIBERATELY NOT AUTOMATIC:
 *   [messageFor] builds the text and the caller hands it to the SMS app for the human to
 *   send. The app never asks for SEND_SMS. An app that can send messages silently is an app
 *   that can send messages the owner did not intend, and the permission prompt alone would
 *   cost more trust than the two taps it saves.
 */
object InviteStore {

    private const val TAG = "InviteStore"
    private const val COLLECTION = "invites"

    private const val FIELD_ROLE = "role"
    private const val FIELD_INVITED_BY = "invitedBy"
    private const val FIELD_INVITED_BY_NAME = "invitedByName"
    private const val FIELD_INVITED_AT = "invitedAt"

    private val invites get() = FirebaseFirestore.getInstance().collection(COLLECTION)

    data class Invite(
        /** E.164, and also the document id. */
        val phone: String,
        val level: AccessLevel,
        val invitedByName: String,
    )

    /**
     * Normalise to E.164, or null if it cannot be.
     *
     * The document id has to match the token Firebase issues at sign-in EXACTLY, and that is
     * always E.164 with no spaces or punctuation. "98204 41127" and "+91 98204-41127" are the
     * same person; stored differently they are two invites, one of which never fires.
     */
    fun normalise(raw: String, defaultDialCode: String = "+91"): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        val e164 = if (trimmed.startsWith("+")) "+$digits" else "$defaultDialCode${digits.trimStart('0')}"
        return e164.takeIf { it.matches(Regex("^\\+\\d{7,15}$")) }
    }

    /** The text the admin will send. Kept short - it is an SMS, and it is read on a lock screen. */
    fun messageFor(invite: Invite, organisation: String = "BIAP"): String =
        "${invite.invitedByName} has invited you to $organisation as ${invite.level.label}. " +
            "Install the app and sign in with this phone number to accept."

    /**
     * Create or replace the invite for [phone].
     *
     * Returns null on success, or a reason to show. Replacing rather than refusing a
     * duplicate is intentional: re-inviting someone at a different level is the obvious way
     * to correct a mistake, and making that an error would leave no way to fix it in-app.
     */
    suspend fun invite(
        actorLevel: AccessLevel,
        phone: String,
        level: AccessLevel,
    ): String? = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return@withContext "You are not signed in."

        // An invite must not be a way around the assignment rules. Checked with VIEWER as
        // the notional current level, because that is what the invitee starts from.
        if (!actorLevel.canAssign(AccessLevel.VIEWER, level)) {
            return@withContext "Your access level does not allow inviting someone as ${level.label}."
        }

        return@withContext try {
            invites.document(phone).set(
                mapOf(
                    FIELD_ROLE to level.name,
                    FIELD_INVITED_BY to user.uid,
                    FIELD_INVITED_BY_NAME to nameOf(user),
                    FIELD_INVITED_AT to FieldValue.serverTimestamp(),
                )
            ).await()
            Log.i(TAG, "invited $phone as $level")
            null
        } catch (e: Exception) {
            Log.w(TAG, "could not create invite: ${e.message}")
            "The server refused that invite. ${e.localizedMessage.orEmpty()}".trim()
        }
    }

    /** Invites not yet accepted. Readable only by an access manager. */
    suspend fun pending(): List<Invite> = withContext(Dispatchers.IO) {
        return@withContext try {
            invites.get().await().documents.map {
                Invite(
                    phone = it.id,
                    level = AccessLevel.fromName(it.getString(FIELD_ROLE)),
                    invitedByName = it.getString(FIELD_INVITED_BY_NAME).orEmpty(),
                )
            }.sortedBy { it.phone }
        } catch (e: Exception) {
            Log.w(TAG, "could not list invites: ${e.message}")
            emptyList()
        }
    }

    suspend fun revoke(phone: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            invites.document(phone).delete().await()
            null
        } catch (e: Exception) {
            "Could not withdraw that invite. ${e.localizedMessage.orEmpty()}".trim()
        }
    }

    /**
     * The level this account was invited at, or null if there is no invite for its number.
     *
     * Reads by the signed-in user's OWN verified number, which is the only document the
     * rules will hand them.
     */
    suspend fun invitedLevelFor(user: FirebaseUser): AccessLevel? = withContext(Dispatchers.IO) {
        val phone = user.phoneNumber?.takeIf { it.isNotBlank() } ?: return@withContext null
        return@withContext try {
            val snapshot = invites.document(phone).get().await()
            if (!snapshot.exists()) null else AccessLevel.fromName(snapshot.getString(FIELD_ROLE))
        } catch (e: Exception) {
            // Absence and unreadable both mean "no grant to apply", and the caller falls back
            // to VIEWER either way, so this stays a debug note rather than a failure.
            Log.d(TAG, "no invite readable for this account: ${e.message}")
            null
        }
    }

    /**
     * Delete the invite once its grant has been applied.
     *
     * Called only AFTER the role write succeeded. Consuming it first would mean a failure in
     * between silently downgrades the person to Viewer with nothing left to explain why.
     */
    suspend fun consume(user: FirebaseUser): Unit = withContext(Dispatchers.IO) {
        val phone = user.phoneNumber?.takeIf { it.isNotBlank() } ?: return@withContext
        try {
            invites.document(phone).delete().await()
            Log.i(TAG, "invite for $phone consumed")
        } catch (e: Exception) {
            // Harmless if it lingers: the role is already applied, and re-applying the same
            // level on a later sign-in changes nothing.
            Log.w(TAG, "could not consume invite: ${e.message}")
        }
    }

    private fun nameOf(user: FirebaseUser): String =
        user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: user.phoneNumber
            ?: "An administrator"
}
