package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Assigning a karigar by the work written on a design.
 *
 * The owner's example is the specification: Deen Mohd. is assigned to Printing, so a design
 * whose work reads "Block Printing" should pick him up. The matching therefore has to be
 * looser than equality - the work written on a design is more specific than the activity -
 * without becoming so loose that half the workshop lands on every design.
 */
class KarigarAssignmentTest {

    private fun karigar(name: String, vararg activities: String) =
        Karigar(id = name, name = name, activities = activities.toList())

    private val deen = karigar("Deen Mohd.", Activities.PRINTING)
    private val saleem = karigar("Saleem Master", Activities.MAKING)
    private val babli = karigar("Babli", Activities.EMBROIDERY, Activities.KATHA)

    // --- matching work to an activity ---

    @Test
    fun `the owner's own example works`() {
        assertTrue(workMatchesActivity("Block Printing", Activities.PRINTING))
    }

    @Test
    fun `a more specific work value still matches its activity`() {
        assertTrue(workMatchesActivity("Hand Embroidery", Activities.EMBROIDERY))
        assertTrue(workMatchesActivity("Tie and Dyeing", Activities.DYEING))
        assertTrue(workMatchesActivity("Screen printing", Activities.PRINTING))
    }

    @Test
    fun `a bracketed activity matches on any of its words`() {
        // "Making (Cutting/Stitching)" should be found by any of the three things it names.
        assertTrue(workMatchesActivity("Stitching", Activities.MAKING))
        assertTrue(workMatchesActivity("Cutting", Activities.MAKING))
        assertTrue(workMatchesActivity("Making", Activities.MAKING))
    }

    @Test
    fun `unrelated work matches nothing`() {
        assertFalse(workMatchesActivity("Zari border", Activities.PRINTING))
        assertFalse(workMatchesActivity("Cotton", Activities.DYEING))
        assertFalse(workMatchesActivity("", Activities.PRINTING))
        assertFalse(workMatchesActivity("   ", Activities.PRINTING))
    }

    @Test
    fun `short words in an activity name cannot match on their own`() {
        // Otherwise "and", "or" and the like would match nearly any work value and assign
        // the whole workshop to every design.
        assertEquals(listOf("making", "cutting", "stitching"), activityTokens(Activities.MAKING))
        assertTrue(activityTokens("A or B").isEmpty())
    }

    // --- which fields count as work ---

    @Test
    fun `only the work fields are read`() {
        assertTrue(isWorkField("Top - Work"))
        assertTrue(isWorkField("Dupatta - Work"))
        assertTrue(isWorkField("bottom work"))
        assertFalse(isWorkField("Top - Fabric"))
        assertFalse(isWorkField("Top - Color"))
    }

    // --- putting it together ---

    @Test
    fun `block printing on the top assigns the printer`() {
        val fields = mapOf("Top - Work" to "Block Printing", "Top - Fabric" to "Cotton")
        assertEquals(listOf("Deen Mohd."), karigarsForWork(fields, listOf(deen, saleem, babli)))
    }

    @Test
    fun `work on different pieces assigns everyone involved, once each`() {
        val fields = mapOf(
            "Top - Work" to "Block Printing",
            "Bottom - Work" to "Block Printing",
            "Dupatta - Work" to "Hand Embroidery",
        )
        // Deen appears once despite matching twice.
        assertEquals(listOf("Babli", "Deen Mohd."), karigarsForWork(fields, listOf(babli, deen)).sorted())
    }

    @Test
    fun `a karigar with several activities is found by any of them`() {
        assertEquals(listOf("Babli"), karigarsForWork(mapOf("Top - Work" to "Katha"), listOf(babli)))
        assertEquals(listOf("Babli"), karigarsForWork(mapOf("Top - Work" to "Embroidery"), listOf(babli)))
    }

    @Test
    fun `nothing is assigned when no work is written, or nobody does it`() {
        assertEquals(emptyList<String>(), karigarsForWork(emptyMap(), listOf(deen)))
        assertEquals(emptyList<String>(), karigarsForWork(mapOf("Top - Work" to "   "), listOf(deen)))
        assertEquals(
            emptyList<String>(),
            karigarsForWork(mapOf("Top - Work" to "Block Printing"), listOf(saleem)),
        )
        // An unassigned karigar is never picked up, however the work reads.
        assertEquals(
            emptyList<String>(),
            karigarsForWork(mapOf("Top - Work" to "Block Printing"), listOf(karigar("Atul"))),
        )
    }

    @Test
    fun `fabric and colour never trigger an assignment`() {
        // "Printed cotton" in a FABRIC field is a description of cloth, not a job.
        val fields = mapOf("Top - Fabric" to "Printed cotton", "Top - Color" to "Printing blue")
        assertEquals(emptyList<String>(), karigarsForWork(fields, listOf(deen)))
    }

    @Test
    fun `the seeded lists are what the owner asked for`() {
        assertEquals(11, DEFAULT_KARIGARS.size)
        assertTrue("Deen Mohd." in DEFAULT_KARIGARS)
        assertTrue("Umesh Devaliya" in DEFAULT_KARIGARS)
        assertEquals(5, Activities.DEFAULTS.size)
        assertEquals(
            listOf("Making (Cutting/Stitching)", "Katha", "Embroidery", "Dyeing", "Printing"),
            Activities.DEFAULTS,
        )
    }
}
