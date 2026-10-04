package com.sc.biap.data

/**
 * @file VoiceCommand.kt
 * @description Understanding a spoken sentence well enough to fill in a send-out form.
 *
 * WHAT THIS IS AND IS NOT:
 *   It never acts. It reads a sentence, fills in what it is confident about, and hands the
 *   form back to the owner to look at. Everything it gets wrong is one tap to correct, and
 *   nothing moves until they press the button. That is the whole reason it is allowed to
 *   guess at all.
 *
 * WHY THE NAMES AND ACTIVITIES ARE PASSED IN:
 *   It matches against the Hub rather than against a dictionary. The vocabulary of a workshop
 *   is eleven names and five activities, which is a small enough world to be right in most
 *   of the time - and it is exactly the world the merge work made consistent.
 *
 * [TABLE OF CONTENTS]
 * 1. TYPES
 * 2. NUMBER WORDS
 * 3. PARSING
 */

// #region 1. Types

/**
 * What was understood. Any field may be null: a sentence that names no activity leaves the
 * activity blank rather than picking one, because a wrong guess silently accepted is worse
 * than a gap the owner can see.
 */
data class SpokenJob(
    val karigar: String? = null,
    val activity: String? = null,
    val quantity: Int? = null,
) {
    val isEmpty: Boolean get() = karigar == null && activity == null && quantity == null
}
// #endregion

// #region 2. Number words

/**
 * Spoken numbers, in the forms a dictation engine actually returns them.
 *
 * Transliteration is not standardised, so each number carries its common spellings rather
 * than one correct one. Hindi and Gujarati overlap heavily and share this table.
 *
 * ponytail: round numbers and one to twenty, not every number to a hundred. A workshop sends
 * ten, twenty, a dozen - not thirty-seven. Extend the table when a real sentence needs it.
 */
private val NUMBER_WORDS: Map<String, Int> = buildMap {
    fun put(value: Int, vararg words: String) = words.forEach { put(it, value) }
    put(1, "ek", "एक")
    put(2, "do", "be", "दो", "બે")
    put(3, "teen", "tran", "तीन", "ત્રણ")
    put(4, "char", "chaar", "चार", "ચાર")
    put(5, "panch", "paanch", "पांच", "पाँच", "પાંચ")
    put(6, "chhe", "che", "chha", "छह", "छे", "છ")
    put(7, "saat", "सात", "સાત")
    put(8, "aath", "आठ", "આઠ")
    put(9, "nau", "nav", "नौ", "નવ")
    put(10, "das", "dus", "दस", "દસ")
    put(11, "gyarah", "agiyar", "ग्यारह")
    // A dozen is how cloth is actually counted in the trade, so it earns its place here.
    put(12, "barah", "bar", "darjan", "dozen", "बारह", "दर्जन")
    put(13, "terah", "तेरह")
    put(14, "chaudah", "चौदह")
    put(15, "pandrah", "pandar", "पंद्रह")
    put(16, "solah", "सोलह")
    put(17, "satrah", "सत्रह")
    put(18, "atharah", "अठारह")
    put(19, "unnees", "उन्नीस")
    put(20, "bees", "vees", "bis", "बीस", "વીસ")
    put(25, "pachees", "pachis", "पच्चीस")
    put(30, "tees", "trees", "तीस", "ત્રીસ")
    put(40, "chalees", "chalis", "चालीस", "ચાલીસ")
    put(50, "pachaas", "pachas", "पचास", "પચાસ")
    put(60, "saath", "sath", "साठ")
    put(70, "sattar", "सत्तर")
    put(80, "assi", "अस्सी")
    put(90, "nabbe", "नब्बे")
    put(100, "sau", "so", "सौ", "સો")
}

/** Counted in dozens, so the number in front of these multiplies rather than stands alone. */
private val DOZEN_WORDS = setOf("darjan", "dozen", "दर्जन")

/** Words that are never a quantity, however much they look like one in a sentence. */
private val NOT_A_QUANTITY = setOf("piece", "pieces", "nag", "kapda", "piece's")
// #endregion

// #region 3. Parsing

/**
 * Read a spoken sentence into the fields of a send-out.
 *
 * "Deen Mohd ko bees piece printing ke liye diye" gives Deen Mohd., 20, Printing.
 *
 * Longest name first, so "Mohd. Yusuf" is not mistaken for a shorter name sitting inside it.
 * Digits beat words, because a dictation engine that returned a digit was more certain than
 * one that returned a word.
 */
fun parseSpokenJob(
    spoken: String,
    karigars: List<String>,
    activities: List<String>,
): SpokenJob {
    val said = matchKey(spoken)
    if (said.isEmpty()) return SpokenJob()

    val karigar = karigars
        .filter { it.isNotBlank() }
        .sortedByDescending { matchKey(it).length }
        .firstOrNull { said.contains(matchKey(it)) }

    // An activity matches on any word of its name, the same rule that auto-assigns a karigar
    // from a design's Work field - so "printing" finds "Block Printing".
    val activity = activities
        .filter { it.isNotBlank() }
        .sortedByDescending { matchKey(it).length }
        .firstOrNull { activityName ->
            val key = matchKey(activityName)
            said.contains(key) || key.split(" ").any { it.length > 3 && said.contains(it) }
        }

    return SpokenJob(karigar = karigar, activity = activity, quantity = readQuantity(said))
}

/** The number in a sentence, as a digit if there is one and as a word otherwise. */
private fun readQuantity(said: String): Int? {
    val words = said.split(" ").filter { it.isNotEmpty() }

    // A dozen is counted in dozens: "ek darjan" is twelve, not one, and "do darjan" is
    // twenty-four. Read before anything else, because the number in front of it belongs to
    // it rather than being the answer.
    val dozenAt = words.indexOfFirst { it in DOZEN_WORDS }
    if (dozenAt >= 0) {
        val before = words.getOrNull(dozenAt - 1)
        val many = before?.toIntOrNull() ?: before?.let { NUMBER_WORDS[it] } ?: 1
        return (many * 12).takeIf { it in 1..100_000 }
    }

    words.firstNotNullOfOrNull { word ->
        word.toIntOrNull()?.takeIf { it in 1..100_000 }
    }?.let { return it }

    // ponytail: "dhai sau" and other compounds are read as their larger part, which is wrong
    // in a way the owner sees in the box before anything moves. Fix when one is dictated.
    return words.firstNotNullOfOrNull { word ->
        if (word in NOT_A_QUANTITY) null else NUMBER_WORDS[word]
    }
}
// #endregion
