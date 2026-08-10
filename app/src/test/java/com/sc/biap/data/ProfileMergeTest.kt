package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The profile merges on its own timestamp, independently of the designs around it.
 *
 * The case that matters most is the null one: an older build has no concept of a profile
 * and uploads null, and that must never be allowed to erase details entered elsewhere.
 */
class ProfileMergeTest {

    private fun data(profile: UserProfile?) = BusinessIntelligenceData(profile = profile)

    @Test
    fun `newer profile wins`() {
        val local = data(UserProfile(firstName = "Old", updatedAt = 100))
        val remote = data(UserProfile(firstName = "New", updatedAt = 200))
        assertEquals("New", SyncMerger.merge(local, remote).profile?.firstName)
        assertEquals("New", SyncMerger.merge(remote, local).profile?.firstName)
    }

    @Test
    fun `a null side never erases a real profile`() {
        val real = UserProfile(firstName = "Malay", updatedAt = 100)
        assertEquals("Malay", SyncMerger.merge(data(real), data(null)).profile?.firstName)
        assertEquals("Malay", SyncMerger.merge(data(null), data(real)).profile?.firstName)
    }

    @Test
    fun `absent on both sides stays absent`() {
        assertNull(SyncMerger.merge(data(null), data(null)).profile)
    }

    @Test
    fun `editing a design does not drag an older profile along`() {
        // The local side has a newer DESIGN but an older PROFILE; each must win separately.
        val local = BusinessIntelligenceData(
            designs = listOf(Design(id = "d1", name = "Newer design", category = "K", updatedAt = 500)),
            profile = UserProfile(firstName = "Stale", updatedAt = 100),
        )
        val remote = BusinessIntelligenceData(
            designs = listOf(Design(id = "d1", name = "Older design", category = "K", updatedAt = 200)),
            profile = UserProfile(firstName = "Fresh", updatedAt = 900),
        )
        val merged = SyncMerger.merge(local, remote)
        assertEquals("Newer design", merged.designs.single().name)
        assertEquals("Fresh", merged.profile?.firstName)
    }
}
