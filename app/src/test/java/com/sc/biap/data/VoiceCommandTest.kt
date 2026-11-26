package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a spoken sentence into a send-out form.
 *
 * It is allowed to guess because it never acts: it fills a form the owner looks at. So the
 * tests check it guesses WELL on real sentences, and - just as important - that it leaves a
 * field blank rather than inventing one when the sentence does not say.
 */
class VoiceCommandTest {

    private val karigars = listOf(
        "Deen Mohd.", "Mohd. Yusuf", "Umesh Devaliya", "Saleem Master",
        "Babli", "Bablu", "Atul", "Israr",
    )
    private val activities = listOf("Making", "Katha", "Embroidery", "Dyeing", "Printing")

    private fun parse(s: String) = parseSpokenJob(s, karigars, activities)

    // --- the sentence this was built for ---

    @Test
    fun `the owner's own example is read correctly`() {
        val j = parse("Deen Mohd ko bees piece printing ke liye diye")
        assertEquals("Deen Mohd.", j.karigar)
        assertEquals("Printing", j.activity)
        assertEquals(20, j.quantity)
    }

    @Test
    fun `digits are read as readily as words`() {
        val j = parse("Atul ko 35 piece katha ke liye")
        assertEquals("Atul", j.karigar)
        assertEquals("Katha", j.activity)
        assertEquals(35, j.quantity)
    }

    @Test
    fun `a dozen is a number, because cloth is counted that way`() {
        assertEquals(12, parse("Israr ko ek darjan dyeing").quantity)
    }

    @Test
    fun `two dozen is twenty-four, not two`() {
        assertEquals(24, parse("Israr ko do darjan dyeing").quantity)
        assertEquals(36, parse("Israr ko 3 darjan dyeing").quantity)
    }

    @Test
    fun `Gujarati number words work too`() {
        assertEquals(20, parse("Atul ne vees piece printing").quantity)
        assertEquals(3, parse("Atul ne tran piece printing").quantity)
    }

    // --- not mistaking one name for another ---

    @Test
    fun `a longer name wins over a shorter one hiding inside it`() {
        // "Mohd. Yusuf" contains no shorter name, but "Deen Mohd." and it share a word.
        assertEquals("Mohd. Yusuf", parse("Mohd Yusuf ko das piece embroidery").karigar)
    }

    @Test
    fun `punctuation in a name does not stop it being heard`() {
        assertEquals("Deen Mohd.", parse("deen mohd ko paanch piece making").karigar)
    }

    @Test
    fun `two people a letter apart are both reachable`() {
        assertEquals("Babli", parse("Babli ko das piece katha").karigar)
        assertEquals("Bablu", parse("Bablu ko das piece katha").karigar)
    }

    // --- saying nothing rather than guessing ---

    @Test
    fun `a sentence naming nobody leaves the name blank`() {
        val j = parse("bees piece printing ke liye diye")
        assertNull(j.karigar)
        assertEquals(20, j.quantity)
        assertEquals("Printing", j.activity)
    }

    @Test
    fun `a sentence with no number leaves the quantity blank`() {
        assertNull(parse("Atul ko printing ke liye diye").quantity)
    }

    @Test
    fun `a sentence with no activity leaves it blank rather than picking the first`() {
        assertNull(parse("Atul ko bees piece diye").activity)
    }

    @Test
    fun `nonsense is understood as nothing at all`() {
        assertTrue(parse("hello how are you").isEmpty)
        assertTrue(parse("").isEmpty)
        assertTrue(parse("     ").isEmpty)
    }

    @Test
    fun `the word piece is never read as a quantity`() {
        assertNull(parse("Atul ko piece printing").quantity)
    }

    @Test
    fun `an empty hub matches nobody rather than failing`() {
        val j = parseSpokenJob("Atul ko bees piece printing", emptyList(), emptyList())
        assertNull(j.karigar)
        assertNull(j.activity)
        assertEquals(20, j.quantity)
    }
}
