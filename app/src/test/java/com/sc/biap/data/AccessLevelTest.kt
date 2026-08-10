package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assignment matrix. This mirrors firestore.rules, which is the real enforcement -
 * these tests exist so the app cannot silently drift away from what the server allows and
 * start offering changes that bounce.
 */
class AccessLevelTest {

    private val everyLevel = AccessLevel.entries

    @Test
    fun `super admin may set any level for anyone`() {
        for (target in everyLevel) {
            for (next in everyLevel) {
                assertTrue(
                    "SUPER_ADMIN should be able to move $target to $next",
                    AccessLevel.SUPER_ADMIN.canAssign(target, next)
                )
            }
        }
    }

    @Test
    fun `admin cannot create another admin or a super admin`() {
        for (target in listOf(AccessLevel.MANAGER, AccessLevel.STAFF, AccessLevel.VIEWER)) {
            assertFalse(AccessLevel.ADMIN.canAssign(target, AccessLevel.ADMIN))
            assertFalse(AccessLevel.ADMIN.canAssign(target, AccessLevel.SUPER_ADMIN))
        }
    }

    @Test
    fun `admin cannot touch a peer or a superior`() {
        for (next in everyLevel) {
            assertFalse(AccessLevel.ADMIN.canAssign(AccessLevel.ADMIN, next))
            assertFalse(AccessLevel.ADMIN.canAssign(AccessLevel.SUPER_ADMIN, next))
        }
    }

    @Test
    fun `admin may grade everyone below admin`() {
        val below = listOf(AccessLevel.MANAGER, AccessLevel.STAFF, AccessLevel.VIEWER)
        for (target in below) {
            for (next in below) {
                assertTrue(AccessLevel.ADMIN.canAssign(target, next))
            }
        }
    }

    @Test
    fun `nobody below admin may assign anything`() {
        for (actor in listOf(AccessLevel.MANAGER, AccessLevel.STAFF, AccessLevel.VIEWER)) {
            assertFalse(actor.canManageAccess)
            for (target in everyLevel) {
                for (next in everyLevel) {
                    assertFalse(actor.canAssign(target, next))
                }
            }
        }
    }

    @Test
    fun `an unreadable or unknown role falls back to the most restricted level`() {
        assertEquals(AccessLevel.VIEWER, AccessLevel.DEFAULT)
        assertEquals(AccessLevel.VIEWER, AccessLevel.fromName(null))
        assertEquals(AccessLevel.VIEWER, AccessLevel.fromName(""))
        assertEquals(AccessLevel.VIEWER, AccessLevel.fromName("root"))
        // Never silently upgrades on a typo or a renamed level.
        assertEquals(AccessLevel.VIEWER, AccessLevel.fromName("superadmin"))
    }

    @Test
    fun `role names survive reordering because they are stored by name`() {
        assertEquals(AccessLevel.SUPER_ADMIN, AccessLevel.fromName("SUPER_ADMIN"))
        assertEquals(AccessLevel.ADMIN, AccessLevel.fromName("admin"))
        assertEquals(AccessLevel.MANAGER, AccessLevel.fromName(" Manager "))
    }
}
