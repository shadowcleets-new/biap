package com.sc.biap.util

object SizeUtils {
    // Canonical ladder: XXL is the canonical spelling of 2XL, 3XL of XXXL.
    private val standardSizes = listOf(
        "XXS", "XS", "S", "M", "L", "XL", "XXL",
        "3XL", "4XL", "5XL", "6XL", "7XL", "8XL", "9XL", "10XL"
    )
    private val aliases = mapOf("2XL" to "XXL", "XXXL" to "3XL")

    /**
     * Sort weight: standard letter sizes first (index order), numeric sizes at 1000+value
     * (clamped below the custom band), custom strings alphabetically from 10000.
     */
    fun getSizeSortWeight(size: String): Double {
        val trimmed = size.trim().uppercase()
        val canonical = aliases[trimmed] ?: trimmed

        val letterIndex = standardSizes.indexOf(canonical)
        if (letterIndex != -1) {
            return letterIndex.toDouble()
        }

        val intValue = canonical.toIntOrNull()
        if (intValue != null) {
            // Clamp so absurd numeric sizes can never collide with the custom band at 10000.
            return 1000.0 + intValue.coerceAtMost(8999)
        }

        var alphaScore = 0.0
        var multiplier = 0.1
        for (char in canonical.take(5)) {
            alphaScore += (char - 'A') * multiplier
            multiplier /= 100.0
        }
        return 10000.0 + alphaScore
    }

    /**
     * Sorts a list of size strings logically.
     */
    fun sortSizes(sizes: List<String>): List<String> {
        return sizes.sortedBy { getSizeSortWeight(it) }
    }
}
