package com.sc.biap.data

/**
 * @file Attributes.kt
 * @description Turning the fabric, work and colour typed on every design into a vocabulary
 *              that can be picked from instead of retyped.
 *
 * WHY NOT A MANAGED LIST:
 *   The catalogue already IS the list. Every value worth offering has been typed at least
 *   once, so the vocabulary is read back out of the designs rather than maintained as a
 *   second thing that can drift from them. Nothing to curate, nothing to seed, and a new
 *   fabric becomes available to every design the moment it is used on one.
 *
 * [TABLE OF CONTENTS]
 * 1. TYPES
 * 2. BUILDING THE VOCABULARY
 * 3. SUGGESTING WHILE TYPING
 */

// #region 1. Types

/**
 * One value as it is actually written, with the spellings it has been written in.
 *
 * [canonical] is the spelling used most often, which is the one worth offering back: a
 * fabric typed "Rayon" nine times and "rayon" once should be offered as "Rayon".
 */
data class AttributeValue(
    val canonical: String,
    val uses: Int,
    /** Every distinct raw spelling seen, including [canonical]. More than one means drift. */
    val spellings: Set<String> = setOf(canonical),
) {
    /** Written more than one way, so the catalogue cannot be grouped on it cleanly. */
    val isInconsistent: Boolean get() = spellings.size > 1
}
// #endregion

// #region 2. Building the vocabulary

/**
 * What has been typed into each attribute field across the whole catalogue.
 *
 * Case and punctuation are folded with the same rule that links karigar names, so "Rayon",
 * "rayon" and " Rayon " are one fabric. It stops exactly where that rule stops: "Rayon" and
 * "Rayon Slub" are two fabrics, because they differ by real words and guessing otherwise
 * would quietly merge two things the owner distinguishes.
 */
fun attributeVocabulary(designs: List<Design>): Map<String, List<AttributeValue>> {
    val byField = HashMap<String, HashMap<String, MutableList<String>>>()
    for (design in designs) {
        if (design.archivedAt > 0L) continue // a retired design should not keep voting
        for ((field, raw) in design.customFields) {
            val value = raw.trim()
            if (value.isEmpty()) continue
            byField.getOrPut(field) { HashMap() }
                .getOrPut(matchKey(value)) { mutableListOf() }
                .add(value)
        }
    }
    return byField.mapValues { (_, keyed) ->
        keyed.values
            .map { spellings ->
                val counted = spellings.groupingBy { it }.eachCount()
                AttributeValue(
                    // Most used wins; ties broken alphabetically so the result is stable
                    // rather than depending on which design happened to be read first.
                    canonical = counted.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                            .thenBy { it.key })
                        .first().key,
                    uses = spellings.size,
                    spellings = counted.keys.toSet(),
                )
            }
            .sortedWith(compareByDescending<AttributeValue> { it.uses }.thenBy { it.canonical })
    }
}

/** Values of one field written more than one way - worth offering to tidy up. */
fun inconsistentValues(vocabulary: Map<String, List<AttributeValue>>): Map<String, List<AttributeValue>> =
    vocabulary.mapValues { (_, values) -> values.filter { it.isInconsistent } }
        .filterValues { it.isNotEmpty() }
// #endregion

// #region 3. Suggesting while typing

/**
 * What to offer under the field as it is being filled in.
 *
 * With nothing typed it offers what is used most, because the common answer is usually the
 * right one. Once something is typed it narrows to values containing it, so a few letters
 * find a long fabric name without scrolling.
 */
fun suggestValues(
    vocabulary: Map<String, List<AttributeValue>>,
    field: String,
    typed: String,
    limit: Int = 6,
): List<String> {
    val values = vocabulary[field].orEmpty()
    val query = matchKey(typed)
    if (query.isEmpty()) return values.take(limit).map { it.canonical }
    return values
        .filter { matchKey(it.canonical).contains(query) }
        // Something typed in full is not worth offering back verbatim.
        .filterNot { matchKey(it.canonical) == query }
        .sortedWith(
            // A value that STARTS with what was typed is the likelier target than one that
            // merely contains it: "ray" should reach "Rayon" before "Cotton Rayon Mix".
            compareByDescending<AttributeValue> { matchKey(it.canonical).startsWith(query) }
                .thenByDescending { it.uses }
                .thenBy { it.canonical }
        )
        .take(limit)
        .map { it.canonical }
}
// #endregion
