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

    // --- compound Hindi and Gujarati numbers ---

    @Test
    fun `Hindi fractional hundreds read accurately`() {
        assertEquals(150, parse("Deen Mohd ko dedh sau piece printing").quantity)
        assertEquals(250, parse("Deen Mohd ko dhai sau piece printing").quantity)
        assertEquals(125, parse("Deen Mohd ko sawa sau piece printing").quantity)
        assertEquals(225, parse("Deen Mohd ko sawa do sau piece printing").quantity)
        assertEquals(75, parse("Deen Mohd ko pauna sau piece printing").quantity)
        assertEquals(75, parse("Deen Mohd ko paune sau piece printing").quantity)
        assertEquals(175, parse("Deen Mohd ko paune do sau piece printing").quantity)
        assertEquals(275, parse("Deen Mohd ko paune teen sau piece printing").quantity)
        assertEquals(350, parse("Deen Mohd ko saadhe teen sau piece printing").quantity)
    }

    @Test
    fun `Gujarati fractional hundreds and compounds read accurately`() {
        assertEquals(150, parse("Atul ne dodh so piece printing").quantity)
        assertEquals(250, parse("Atul ne adhi so piece printing").quantity)
        assertEquals(175, parse("Atul ne pona be so piece printing").quantity)
        assertEquals(350, parse("Atul ne sada tran so piece printing").quantity)
        assertEquals(220, parse("Atul ne be so vees piece printing").quantity)
    }

    @Test
    fun `additive hundreds and thousands read accurately`() {
        assertEquals(100, parse("Atul ko ek sau piece printing").quantity)
        assertEquals(120, parse("Atul ko ek sau bees piece printing").quantity)
        assertEquals(250, parse("Atul ko do sau pachaas piece printing").quantity)
        assertEquals(120, parse("Atul ko 1 sau 20 piece printing").quantity)
        assertEquals(120, parse("Atul ko sau bees piece printing").quantity)
        assertEquals(1200, parse("Atul ko barah sau piece printing").quantity)
        assertEquals(1000, parse("Atul ko ek hazaar piece printing").quantity)
        assertEquals(1250, parse("Atul ko sawa hazaar piece printing").quantity)
        assertEquals(1500, parse("Atul ko dedh hazaar piece printing").quantity)
        assertEquals(2500, parse("Atul ko dhai hazaar piece printing").quantity)
        assertEquals(1750, parse("Atul ko paune do hazaar piece printing").quantity)
        assertEquals(1250, parse("Atul ko ek hazaar do sau pachaas piece printing").quantity)
    }

    @Test
    fun `fractional dozens read accurately`() {
        assertEquals(18, parse("Israr ko dedh darjan dyeing").quantity)
        assertEquals(30, parse("Israr ko dhai darjan dyeing").quantity)
        assertEquals(15, parse("Israr ko sawa darjan dyeing").quantity)
        assertEquals(9, parse("Israr ko pauna darjan dyeing").quantity)
        assertEquals(21, parse("Israr ko paune do darjan dyeing").quantity)
        assertEquals(42, parse("Israr ko saadhe teen darjan dyeing").quantity)
    }

    @Test
    fun `Devanagari and Gujarati Unicode scripts keep vowel marks intact`() {
        assertEquals(250, parse("Deen Mohd ko ढाई सौ piece printing").quantity)
        assertEquals(150, parse("Deen Mohd ko डेढ़ सौ piece printing").quantity)
        assertEquals(120, parse("Deen Mohd ko एक सौ बीस piece printing").quantity)
        assertEquals(175, parse("Deen Mohd ko पौने दो सौ piece printing").quantity)
        assertEquals(250, parse("Atul ne અઢી સો piece printing").quantity)
        assertEquals(150, parse("Atul ne દોઢ સો piece printing").quantity)
        assertEquals(175, parse("Atul ne પોણા બે સો piece printing").quantity)
    }

    @Test
    fun `mixed ASR digit-after-fraction forms and English so filler behave safely`() {
        assertEquals(175, parse("Atul ko paune 200 piece").quantity)
        assertEquals(125, parse("Atul ko sawa 100 piece").quantity)
        assertEquals(350, parse("Atul ko saadhe 300 piece").quantity)
        assertEquals(35, parse("so Atul ko 35 piece printing").quantity)
        assertEquals(35, parse("so 35 piece printing").quantity)
        assertNull(parse("Atul ko paune do piece printing").quantity)
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
