package com.example.biap.util

object SizeUtils {
    private val standardSizes = listOf(
        "XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL", 
        "3XL", "4XL", "5XL", "6XL", "7XL", "8XL", "9XL", "10XL"
    )

    /**
     * Determines a sort weight for sizes so that standard letter sizes
     * come first (sorted XXS to 10XL), then numeric sizes (sorted numerically),
     * and custom sizes alphabetically at the end.
     */
    fun getSizeSortWeight(size: String): Double {
        val trimmed = size.trim().uppercase()
        
        // 1. Check standard sizes first
        val letterIndex = standardSizes.indexOf(trimmed)
        if (letterIndex != -1) {
            return letterIndex.toDouble()
        }

        // 2. Check if it's numeric (e.g. 32, 34...)
        val intValue = trimmed.toIntOrNull()
        if (intValue != null) {
            return 1000.0 + intValue
        }

        // 3. Custom alphabetical sizes
        var alphaScore = 0.0
        var multiplier = 0.1
        for (char in trimmed.take(5)) {
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
