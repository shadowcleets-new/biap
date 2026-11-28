package com.sc.biap.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputFiltersTest {

    @Test fun digitsUpToStripsAndRejectsOverflow() {
        assertEquals("123456", InputFilters.digitsUpTo("123 456", 6))
        assertNull(InputFilters.digitsUpTo("1234567", 6))
        assertEquals("", InputFilters.digitsUpTo("abc", 6))
    }

    @Test fun digitsTruncatedCutsLongPaste() {
        assertEquals("98765", InputFilters.digitsTruncated("98-765-43", 5))
    }

    @Test fun dialCodeKeepsOnePlus() {
        assertEquals("+91", InputFilters.dialCode("+91"))
        assertEquals("+91", InputFilters.dialCode("++9a1"))
        assertEquals("+123", InputFilters.dialCode("+12345"))
        assertEquals("+", InputFilters.dialCode(""))
    }

    @Test fun priceAcceptsRupeesAndPaise() {
        assertEquals("1499", InputFilters.price("1499"))
        assertEquals("1499.5", InputFilters.price("1499.5"))
        assertEquals("1499.50", InputFilters.price("₹1499.50"))
    }

    @Test fun priceRejectsBadShapes() {
        assertNull(InputFilters.price("1.2.3"))
        assertNull(InputFilters.price("1499.505"))
        assertNull(InputFilters.price("1234567890"))
    }
}
