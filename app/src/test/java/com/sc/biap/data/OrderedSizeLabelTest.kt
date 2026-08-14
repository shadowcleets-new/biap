package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * @file OrderedSizeLabelTest.kt
 * @description How an order's sizes read on a carousel card.
 *
 * The rule is worth pinning down because the two forms mean different things. A range says
 * the design went out from one end of its ladder to the other; a list says these specific
 * sizes did and the gaps matter. Getting them the wrong way round would be a quiet lie on a
 * card meant to be read at a glance.
 */
class OrderedSizeLabelTest {

    /** What a design that stocks the full run actually holds - note there is no 4XL. */
    private val fullLadder = listOf("L", "XL", "XXL", "3XL", "5XL")

    @Test
    fun `an unbroken run of the design's own sizes reads as a range`() {
        assertEquals(
            "L – 5XL",
            orderedSizeLabel(listOf("L", "XL", "XXL", "3XL", "5XL"), fullLadder),
        )
    }

    @Test
    fun `a gap in the middle is listed out`() {
        assertEquals(
            "XL, XXL & 5XL",
            orderedSizeLabel(listOf("XL", "XXL", "5XL"), fullLadder),
        )
    }

    @Test
    fun `the design's ladder decides contiguity, not the universal one`() {
        // L, XL, XXL, 3XL, 5XL skips 4XL on the universal ladder - but this design has no
        // 4XL, so nothing was skipped and the range form is the honest one. Measured the
        // other way this would read "L, XL, XXL, 3XL & 5XL", true of the ladder and wrong
        // about the design.
        assertEquals("L – 5XL", orderedSizeLabel(fullLadder, fullLadder))

        // The same five sizes against a design that DOES stock 4XL really do skip a rung.
        assertEquals(
            "L, XL, XXL, 3XL & 5XL",
            orderedSizeLabel(fullLadder, listOf("L", "XL", "XXL", "3XL", "4XL", "5XL")),
        )
    }

    @Test
    fun `a partial run at one end is still a range`() {
        assertEquals("L – XXL", orderedSizeLabel(listOf("L", "XL", "XXL"), fullLadder))
        assertEquals("3XL – 5XL", orderedSizeLabel(listOf("3XL", "5XL"), fullLadder))
    }

    @Test
    fun `two sizes that are not neighbours are joined with an ampersand`() {
        assertEquals("L & 5XL", orderedSizeLabel(listOf("L", "5XL"), fullLadder))
    }

    @Test
    fun `a single size is just itself`() {
        assertEquals("XL", orderedSizeLabel(listOf("XL"), fullLadder))
    }

    @Test
    fun `input order does not matter`() {
        assertEquals(
            "L – 5XL",
            orderedSizeLabel(listOf("5XL", "L", "3XL", "XXL", "XL"), fullLadder),
        )
    }

    @Test
    fun `2XL and XXL are the same rung, and the order's own spelling is kept`() {
        // Two things at once. SizeUtils treats them as one size, so a line stored as "2XL"
        // still lines up with a design holding "XXL" - comparing strings rather than sort
        // weights would break the run and silently fall back to the list form. That it comes
        // back as a range at all is the alias matching working.
        //
        // But the label echoes what the ORDER recorded, not what the design calls it. An
        // order is a record of what happened, and quietly restating it in the catalogue's
        // current vocabulary is the same class of edit as recomputing its prices.
        assertEquals("L – 2XL", orderedSizeLabel(listOf("L", "XL", "2XL"), fullLadder))
    }

    @Test
    fun `a size the design no longer has falls back to listing`() {
        // Safe direction to fail: a list is never wrong about which sizes went out, only
        // longer than it needed to be.
        assertEquals("L, XL & 7XL", orderedSizeLabel(listOf("L", "XL", "7XL"), fullLadder))
        assertEquals("L, XL & XXL", orderedSizeLabel(listOf("L", "XL", "XXL"), emptyList()))
    }

    @Test
    fun `duplicate sizes collapse`() {
        assertEquals("L – XL", orderedSizeLabel(listOf("L", "XL", "L"), fullLadder))
    }

    @Test
    fun `no sizes is empty rather than a stray dash`() {
        assertEquals("", orderedSizeLabel(emptyList(), fullLadder))
    }
}
