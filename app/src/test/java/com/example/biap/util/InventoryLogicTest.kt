package com.example.biap.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryLogicTest {

    @Test
    fun testParsePriceToCents() {
        assertEquals(2990L, FileUtils.parsePriceToCents("29.9"))
        assertEquals(2990L, FileUtils.parsePriceToCents("29.90"))
        assertEquals(2900L, FileUtils.parsePriceToCents("29"))
        assertEquals(0L, FileUtils.parsePriceToCents(""))
        assertEquals(0L, FileUtils.parsePriceToCents("invalid"))
        assertEquals(5L, FileUtils.parsePriceToCents("0.05"))
        assertEquals(100L, FileUtils.parsePriceToCents("1.00"))
        assertEquals(12345600L, FileUtils.parsePriceToCents("123456"))
    }

    @Test
    fun testSortSizes() {
        val input = listOf("XL", "S", "XXL", "M", "3XL", "L")
        val expected = listOf("S", "M", "L", "XL", "XXL", "3XL")
        assertEquals(expected, SizeUtils.sortSizes(input))
    }

    @Test
    fun testSizeSortWeight() {
        // Standard sizing weights match their index in standardSizes
        assertEquals(1.0, SizeUtils.getSizeSortWeight("XS"), 0.001)
        assertEquals(2.0, SizeUtils.getSizeSortWeight("S"), 0.001)
        assertEquals(3.0, SizeUtils.getSizeSortWeight("M"), 0.001)
        assertEquals(4.0, SizeUtils.getSizeSortWeight("L"), 0.001)
        assertEquals(5.0, SizeUtils.getSizeSortWeight("XL"), 0.001)
        assertEquals(6.0, SizeUtils.getSizeSortWeight("XXL"), 0.001)
        assertEquals(8.0, SizeUtils.getSizeSortWeight("3XL"), 0.001)
        
        // Numerical sizing (standard chest sizes)
        assertEquals(1032.0, SizeUtils.getSizeSortWeight("32"), 0.001)
        assertEquals(1044.0, SizeUtils.getSizeSortWeight("44"), 0.001)
        
        // Unrecognized custom size
        assertTrue(SizeUtils.getSizeSortWeight("CUSTOM") >= 10000.0)
    }
}
