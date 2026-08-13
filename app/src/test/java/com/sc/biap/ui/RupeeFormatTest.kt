package com.sc.biap.ui

import com.sc.biap.ui.main.rupees
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prices are stored in paise. Rendering them as if they were rupees shows every figure a
 * hundred times too large - which is what happened the first time, and looked like a
 * formatting nicety rather than the catastrophe it is on a screen full of money.
 */
class RupeeFormatTest {

    @Test
    fun `paise are converted to rupees`() {
        assertEquals("₹1,150", rupees(115_000))
        assertEquals("₹1,195", rupees(119_500))
    }

    @Test
    fun `grouping is Indian, not western`() {
        // 1,50,000 - lakh grouping, two digits at a time after the first three.
        assertEquals("₹1,50,000", rupees(15_000_000))
        assertEquals("₹12,34,567", rupees(123_456_700))
    }

    @Test
    fun `short values are not grouped`() {
        assertEquals("₹0", rupees(0))
        assertEquals("₹9", rupees(900))
        assertEquals("₹999", rupees(99_900))
    }

    @Test
    fun `paise appear only when there are any`() {
        assertEquals("₹1,150", rupees(115_000))
        assertEquals("₹1,150.50", rupees(115_050))
        assertEquals("₹1,150.05", rupees(115_005))
    }
}
