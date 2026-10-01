package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tying the hand-typed names on existing designs to the Hub list.
 *
 * This rewrites real records, so what matters most is what it must NEVER do: lose a name,
 * merge two people who are genuinely different, or change anything it was not asked to.
 */
class KarigarLinkTest {

    private fun hub(vararg names: String) = names.map { Karigar(id = it, name = it) }

    private val seeded = hub(
        "Umesh Devaliya", "Saleem Master", "Bablu", "Sandeep", "Dinesh Soni",
        "Babli", "Atul", "Israr", "Mohd. Yusuf", "Rakesh Vanra", "Deen Mohd.",
    )

    // --- the spellings that should be treated as one person ---

    @Test
    fun `punctuation and spacing do not make a second person`() {
        assertEquals(matchKey("Mohd. Yusuf"), matchKey("Mohd Yusuf"))
        assertEquals(matchKey("Deen Mohd."), matchKey("deen mohd"))
        assertEquals(matchKey("Rakesh  Vanra"), matchKey("Rakesh Vanra"))
        assertEquals(matchKey("  Atul  "), matchKey("Atul"))
    }

    @Test
    fun `a real difference in letters is still two people`() {
        // The whole risk of normalising: it must not go so far that it merges the workshop.
        assertTrue(matchKey("Babli") != matchKey("Bablu"))
        assertTrue(matchKey("Sandeep") != matchKey("Sandip"))
    }

    // --- planning ---

    @Test
    fun `a differently spelled name is renamed to the Hub's spelling`() {
        val plan = planKarigarLinks(listOf("Mohd Yusuf", "deen mohd."), seeded)
        assertEquals(mapOf("Mohd Yusuf" to "Mohd. Yusuf", "deen mohd." to "Deen Mohd."), plan.renames)
        assertEquals(emptyList<String>(), plan.toAdd)
    }

    @Test
    fun `a name already spelled the Hub's way is left alone`() {
        val plan = planKarigarLinks(listOf("Atul", "Israr"), seeded)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `a name the Hub has never heard of joins the Hub, and is not dropped`() {
        val plan = planKarigarLinks(listOf("Atul", "Ramesh Bhai"), seeded)
        assertEquals(listOf("Ramesh Bhai"), plan.toAdd)
        assertEquals(emptyMap<String, String>(), plan.renames)
        // and it survives onto the design
        assertEquals(
            listOf("Atul", "Ramesh Bhai"),
            applyKarigarLinks(listOf("Atul", "Ramesh Bhai"), plan),
        )
    }

    @Test
    fun `an unknown name appearing twice is added to the Hub only once`() {
        val plan = planKarigarLinks(listOf("Ramesh Bhai", "ramesh  bhai", "Ramesh Bhai"), seeded)
        assertEquals(1, plan.toAdd.size)
    }

    @Test
    fun `blank entries are ignored rather than becoming a karigar`() {
        val plan = planKarigarLinks(listOf("", "   ", "Atul"), seeded)
        assertTrue(plan.isEmpty)
        assertEquals(listOf("Atul"), applyKarigarLinks(listOf("", "  ", "Atul"), plan))
    }

    // --- applying ---

    @Test
    fun `two spellings of one person collapse to a single chip`() {
        val plan = planKarigarLinks(listOf("Mohd Yusuf", "Mohd. Yusuf"), seeded)
        assertEquals(listOf("Mohd. Yusuf"), applyKarigarLinks(listOf("Mohd Yusuf", "Mohd. Yusuf"), plan))
    }

    @Test
    fun `order is kept, because a design's list is the owner's ordering`() {
        val names = listOf("Israr", "Mohd Yusuf", "Atul")
        val plan = planKarigarLinks(names, seeded)
        assertEquals(listOf("Israr", "Mohd. Yusuf", "Atul"), applyKarigarLinks(names, plan))
    }

    @Test
    fun `a design that needs nothing comes back identical, so nothing is written`() {
        val names = listOf("Babli", "Israr", "Mohd. Yusuf")
        val plan = planKarigarLinks(names, seeded)
        assertEquals(names, applyKarigarLinks(names, plan))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `an empty Hub adopts every name on the designs rather than clearing them`() {
        val names = listOf("Atul", "Babli")
        val plan = planKarigarLinks(names, emptyList())
        assertEquals(listOf("Atul", "Babli"), plan.toAdd)
        assertEquals(names, applyKarigarLinks(names, plan))
    }

    // --- merging two spellings of one person ---

    @Test
    fun `merging rewrites the folded-away name wherever it appears`() {
        // What the owner confirmed: Umesh is Umesh Devaliya.
        val plan = KarigarLinkPlan(renames = mapOf("Umesh" to "Umesh Devaliya"))
        assertEquals(
            listOf("Atul", "Umesh Devaliya"),
            applyKarigarLinks(listOf("Atul", "Umesh"), plan),
        )
    }

    @Test
    fun `a design naming both spellings ends up with one chip, not two`() {
        val plan = KarigarLinkPlan(renames = mapOf("Umesh" to "Umesh Devaliya"))
        assertEquals(
            listOf("Umesh Devaliya"),
            applyKarigarLinks(listOf("Umesh", "Umesh Devaliya"), plan),
        )
    }

    @Test
    fun `a design that never named the folded-away person is untouched`() {
        // The pass only writes when something changes, so this must come back identical.
        val plan = KarigarLinkPlan(renames = mapOf("Umesh" to "Umesh Devaliya"))
        val names = listOf("Babli", "Israr")
        assertEquals(names, applyKarigarLinks(names, plan))
    }

    @Test
    fun `the screenshot's three names link cleanly to the seeded list`() {
        // Babli, Israr and Mohd. Yusuf, as they appear on the design in the report.
        val names = listOf("Babli", "Israr", "Mohd. Yusuf")
        val plan = planKarigarLinks(names, seeded)
        assertTrue(plan.toAdd.isEmpty())
        assertEquals(names, applyKarigarLinks(names, plan))
    }
}
