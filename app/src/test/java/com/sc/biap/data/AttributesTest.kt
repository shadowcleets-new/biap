package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The catalogue read back as a vocabulary, so fabric and work can be picked not retyped. */
class AttributesTest {

    private fun design(id: String, vararg f: Pair<String, String>, archived: Long = 0L) =
        Design(id = id, name = id, category = "Salwar Kameez",
            customFields = f.toMap(), archivedAt = archived)

    private val catalogue = listOf(
        design("a", "Top - Fabric" to "Rayon", "Top - Work" to "Block print"),
        design("b", "Top - Fabric" to "rayon", "Top - Work" to "Block print"),
        design("c", "Top - Fabric" to "Rayon", "Top - Work" to "Embroidery"),
        design("d", "Top - Fabric" to "Cotton"),
    )

    @Test
    fun `one fabric written three ways is one fabric`() {
        val v = attributeVocabulary(catalogue)["Top - Fabric"]!!
        assertEquals(listOf("Rayon", "Cotton"), v.map { it.canonical })
        assertEquals(3, v.first().uses)
    }

    @Test
    fun `the spelling used most is the one offered back`() {
        val v = attributeVocabulary(listOf(
            design("a", "F" to "rayon"), design("b", "F" to "Rayon"), design("c", "F" to "Rayon"),
        ))["F"]!!
        assertEquals("Rayon", v.first().canonical)
    }

    @Test
    fun `a tie is broken the same way every time, not by read order`() {
        val one = attributeVocabulary(listOf(design("a", "F" to "rayon"), design("b", "F" to "Rayon")))
        val other = attributeVocabulary(listOf(design("b", "F" to "Rayon"), design("a", "F" to "rayon")))
        assertEquals(one["F"]!!.first().canonical, other["F"]!!.first().canonical)
    }

    @Test
    fun `values differing by a real word stay separate`() {
        // The same line matchKey draws for karigars: punctuation folds, letters do not.
        val v = attributeVocabulary(listOf(design("a", "F" to "Rayon"), design("b", "F" to "Rayon Slub")))
        assertEquals(2, v["F"]!!.size)
    }

    @Test
    fun `a retired design stops voting on what is current`() {
        val v = attributeVocabulary(catalogue + design("e", "Top - Fabric" to "Georgette", archived = 1L))
        assertTrue(v["Top - Fabric"]!!.none { it.canonical == "Georgette" })
    }

    @Test
    fun `blank and whitespace values never enter the vocabulary`() {
        val v = attributeVocabulary(listOf(design("a", "F" to "", "G" to "   ")))
        assertTrue(v["F"].isNullOrEmpty())
        assertTrue(v["G"].isNullOrEmpty())
    }

    @Test
    fun `drift is reported so it can be tidied`() {
        val bad = inconsistentValues(attributeVocabulary(catalogue))
        assertEquals(setOf("Rayon", "rayon"), bad["Top - Fabric"]!!.first().spellings)
        // Block print was written identically twice - that is consistent, not drift.
        assertTrue(bad["Top - Work"].isNullOrEmpty())
    }

    // --- suggesting ---

    @Test
    fun `with nothing typed it offers what is used most first`() {
        val s = suggestValues(attributeVocabulary(catalogue), "Top - Fabric", "")
        assertEquals(listOf("Rayon", "Cotton"), s)
    }

    @Test
    fun `a few letters find the value`() {
        assertEquals(listOf("Rayon"), suggestValues(attributeVocabulary(catalogue), "Top - Fabric", "ray"))
    }

    @Test
    fun `a value starting with what was typed beats one merely containing it`() {
        val v = attributeVocabulary(listOf(
            design("a", "F" to "Cotton Rayon Mix"), design("b", "F" to "Cotton Rayon Mix"),
            design("c", "F" to "Rayon"),
        ))
        assertEquals("Rayon", suggestValues(v, "F", "ray").first())
    }

    @Test
    fun `what is already typed in full is not offered back`() {
        assertTrue(suggestValues(attributeVocabulary(catalogue), "Top - Fabric", "Rayon").isEmpty())
    }

    @Test
    fun `an unknown field suggests nothing rather than failing`() {
        assertTrue(suggestValues(attributeVocabulary(catalogue), "Nothing - Here", "x").isEmpty())
    }
}
