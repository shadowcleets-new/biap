package com.sc.biap.data

import java.text.Normalizer

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
 */
private val WORD_NUMBERS: Map<String, Int> = buildMap {
    fun put(value: Int, vararg words: String) = words.forEach { put(it, value) }
    put(1, "ek", "एक", "એક")
    put(2, "do", "be", "दो", "બે")
    put(3, "teen", "tran", "तीन", "ત્રણ")
    put(4, "char", "chaar", "चार", "ચાર")
    put(5, "panch", "paanch", "पांच", "पाँच", "પાંચ")
    put(6, "chhe", "che", "chha", "छह", "छे", "छ", "છ")
    put(7, "saat", "सात", "સાત")
    put(8, "aath", "आठ", "આઠ")
    put(9, "nau", "nav", "नौ", "નવ")
    put(10, "das", "dus", "दस", "દસ")
    put(11, "gyarah", "agiyar", "agiyaras", "ग्यारह", "અગિયાર")
    put(12, "barah", "bar", "baar", "बारह", "બાર")
    put(13, "terah", "ter", "तेरह", "તેર")
    put(14, "chaudah", "chaud", "चौदह", "ચૌદ")
    put(15, "pandrah", "pandar", "पंद्रह", "पन्द्रह", "પંદર")
    put(16, "solah", "sol", "सोलह", "સોળ")
    put(17, "satrah", "सत्रह", "સત્તર")
    put(18, "atharah", "athar", "adhaar", "अठारह", "અઢાર")
    put(19, "unnees", "unnis", "ognis", "oganees", "उन्नीस", "ઓગણીસ")
    put(20, "bees", "vees", "bis", "vis", "बीस", "વીસ")
    put(24, "chaubees", "chaubis", "chovis", "चौबीस", "ચોવીસ")
    put(25, "pachees", "pachis", "pachhis", "पच्चीस", "પચ્ચીસ")
    put(30, "tees", "trees", "tris", "तीस", "ત્રીસ")
    put(35, "paintees", "paitis", "पैंतीस", "પાંત્રીસ")
    put(36, "chhattees", "chhattis", "छत्तीस", "છત્રીસ")
    put(40, "chalees", "chalis", "चालीस", "ચાલીસ")
    put(45, "paintalees", "paintalis", "पैंतालीस", "પિસ્તાળીસ")
    put(48, "adtalees", "artalis", "अड़तालीस", "અડતાળીસ")
    put(50, "pachaas", "pachas", "पचास", "પચાસ")
    put(55, "pachpan", "पचपन", "પંચાવન")
    put(60, "saath", "sath", "saith", "साठ", "સાઠ")
    put(65, "painsath", "पैंसठ", "પાંસઠ")
    put(70, "sattar", "sitter", "sittar", "सत्तर", "સિત્તેર")
    put(75, "pachhattar", "pachattar", "पचहत्तर", "પંચોતેર")
    put(80, "assi", "ensi", "ainsi", "अस्सी", "એંસી")
    put(85, "pachasi", "पचासी", "પંચાસી")
    put(90, "nabbe", "nevu", "नब्बे", "નેવું")
    put(95, "pachanave", "पचानवे", "પંચાણું")
}

/** Scale words that multiply a preceding number (or fraction) and may carry a smaller tail. */
private val SCALES: Map<String, Int> = buildMap {
    fun put(value: Int, vararg words: String) = words.forEach { put(it, value) }
    // A dozen is how cloth is actually counted in the trade, so it earns its place here.
    put(12, "darjan", "dozen", "dazan", "दर्जन", "ડઝન")
    put(100, "sau", "so", "सौ", "સો")
    put(1000, "hazaar", "hajar", "hajaar", "हजार", "हज़ार", "હજાર")
}

/**
 * Gujarati "so" (100) collides with the English filler "so" ("so Atul ko 35 piece").
 * Bare "so" is only allowed to begin a compound when followed by a word number < 100
 * ("so vees" = 120), never when followed by an unrelated word or digit ("so 35").
 */
private val AMBIGUOUS_SO = setOf("so")

/** Fixed fractional quantities expressed in quarter units (6 = 1.5, 10 = 2.5). */
private val FRACTION_FIXED_QUARTERS: Map<String, Int> = buildMap {
    fun put(quarters: Int, vararg words: String) = words.forEach { put(it, quarters) }
    put(6, "dedh", "derh", "dodh", "डेढ़", "डेढ", "દોઢ")
    put(10, "dhai", "adhai", "adhi", "ढाई", "अढ़ाई", "अढाई", "અઢી")
}

/** Quarter-unit offsets applied to the following base ("sawa" = +1/4, "saadhe" = +1/2, "paune" = -1/4). */
private val FRACTION_PREFIX_DELTA_QUARTERS: Map<String, Int> = buildMap {
    fun put(delta: Int, vararg words: String) = words.forEach { put(it, delta) }
    put(1, "sawa", "sava", "सवा", "સવા")
    put(2, "saadhe", "sadhe", "sada", "saada", "साढ़े", "साढे", "સાડા")
    put(-1, "paune", "pauna", "pone", "pona", "pono", "पौने", "पौना", "પોણા", "પોણે", "પોણો")
}

/** Words that are never a quantity, however much they look like one in a sentence. */
private val NOT_A_QUANTITY = setOf("piece", "pieces", "nag", "kapda", "piece's")

private const val MAX_QUANTITY = 100_000
// #endregion

// #region 3. Parsing

/**
 * Read a spoken sentence into the fields of a send-out.
 *
 * "Deen Mohd ko bees piece printing ke liye diye" gives Deen Mohd., 20, Printing.
 *
 * Longest name first, so "Mohd. Yusuf" is not mistaken for a shorter name sitting inside it.
 * Compound expressions ("dhai sau", "ek sau bees", "do darjan") beat standalone digits, and
 * digits beat standalone words, because a dictation engine that returned a digit was more
 * certain than one that returned a word.
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

    return SpokenJob(karigar = karigar, activity = activity, quantity = readQuantity(spoken))
}

/**
 * Split spoken text into lowercase NFC-normalized words while preserving Unicode combining
 * marks (`\p{M}`) so Devanagari and Gujarati matras (e.g. "डेढ़", "ढाई", "પોણા") stay intact.
 */
private fun voiceWords(spoken: String): List<String> =
    Normalizer.normalize(spoken, Normalizer.Form.NFC)
        .trim()
        .lowercase()
        .replace(Regex("""(?<=\d),(?=\d)"""), "")
        .split(Regex("""[^\p{L}\p{M}\p{N}]+"""))
        .filter { it.isNotEmpty() }

/** Read the quantity in a sentence: compounds first, then standalone digits, then single words. */
private fun readQuantity(spoken: String): Int? {
    val words = voiceWords(spoken)
    if (words.isEmpty()) return null

    // Track spans invalidated by a standalone fraction with no scale (e.g. "paune do" without
    // "sau"/"darjan") so the trailing number is not misread as a bare whole quantity.
    val suppressed = BooleanArray(words.size)

    var i = 0
    while (i < words.size) {
        val parsed = parseCompoundAt(words, i)
        if (parsed != null) {
            val (value, consumed) = parsed
            if (value != null && value in 1..MAX_QUANTITY) return value
            for (k in i until (i + consumed).coerceAtMost(words.size)) {
                suppressed[k] = true
            }
            i += consumed
        } else {
            i++
        }
    }

    words.indices.firstNotNullOfOrNull { idx ->
        if (suppressed[idx]) null
        else words[idx].toIntOrNull()?.takeIf { it in 1..MAX_QUANTITY }
    }?.let { return it }

    return words.indices.firstNotNullOfOrNull { idx ->
        if (suppressed[idx]) null
        else {
            val word = words[idx]
            if (word in NOT_A_QUANTITY) null else WORD_NUMBERS[word] ?: SCALES[word]
        }
    }?.takeIf { it in 1..MAX_QUANTITY }
}

private fun countToken(word: String): Int? {
    if (word in NOT_A_QUANTITY) return null
    return word.toIntOrNull()?.takeIf { it in 1..MAX_QUANTITY } ?: WORD_NUMBERS[word]
}

/**
 * Try to parse a fraction or multi-word scale expression starting at [start].
 *
 * Returns `Pair(valueOrNull, wordsConsumed)` when [start] starts a compound/fraction phrase,
 * or `null` when [words][start] is not a compound/fraction starter.
 */
private fun parseCompoundAt(words: List<String>, start: Int): Pair<Int?, Int>? {
    val w0 = words[start]

    // 1. Fixed fractions: "dedh sau" = 150, "dhai sau" = 250, "dedh darjan" = 18, "dhai 100" = 250.
    FRACTION_FIXED_QUARTERS[w0]?.let { quarters ->
        val w1 = words.getOrNull(start + 1)
        val scale = w1?.let { SCALES[it] }
        if (scale != null) {
            val value = (quarters * scale) / 4
            return value to 2
        }
        val roundDigit = w1?.toIntOrNull()?.takeIf { it == 100 || it == 1000 }
        if (roundDigit != null) {
            return ((quarters * roundDigit) / 4) to 2
        }
        return null to 1
    }

    // 2. Prefix fractions: "sawa sau" (125), "pauna sau" (75), "paune do sau" (175), "saadhe teen sau" (350).
    FRACTION_PREFIX_DELTA_QUARTERS[w0]?.let { delta ->
        val w1 = words.getOrNull(start + 1) ?: return null to 1
        val directScale = SCALES[w1]
        if (directScale != null) {
            val quarters = 4 + delta
            return ((quarters * directScale) / 4) to 2
        }
        val count = countToken(w1)
        if (count != null) {
            val w2 = words.getOrNull(start + 2)
            val nextScale = w2?.let { SCALES[it] }
            if (nextScale != null) {
                val quarters = 4 * count + delta
                val value = if (quarters > 0) (quarters * nextScale) / 4 else null
                return value to 3
            }
            // Dictation engines often convert "paune do sau" -> "paune 200" or "sawa sau" -> "sawa 100".
            if (w1.toIntOrNull() == count && count >= 100 && count % 100 == 0) {
                val step = if (count % 1000 == 0) 1000 else 100
                val value = count + (delta * step) / 4
                return value.takeIf { it > 0 } to 2
            }
            return null to 2
        }
        return null to 1
    }

    // 3. Explicit "<count> <scale> ..." ("ek sau bees", "do darjan", "3 darjan", "ek hazaar do sau pachaas").
    val leadCount = countToken(w0)
    val w1 = words.getOrNull(start + 1)
    if (leadCount != null && w1 != null && w1 in SCALES) {
        return parseScaleFrom(words, count = leadCount, scaleIdx = start + 1, startIdx = start)
    }

    // 4. Bare scale starting a compound ("darjan", "sau bees", "so vees", "hazaar do sau").
    if (w0 in SCALES) {
        val scale = SCALES.getValue(w0)
        if (scale == 12) return 12 to 1
        if (w0 in AMBIGUOUS_SO) {
            // Only treat bare "so" as 100 here when followed by a word number < 100 (e.g. "so vees").
            val nextWord = words.getOrNull(start + 1) ?: return null
            val nextVal = if (nextWord in NOT_A_QUANTITY) null else WORD_NUMBERS[nextWord]
            if (nextVal != null && nextVal in 1..99) {
                return (100 + nextVal) to 2
            }
            return null
        }
        if (scale == 100) {
            val nextVal = words.getOrNull(start + 1)?.let { countToken(it) }
            if (nextVal != null && nextVal in 1..99) {
                return (100 + nextVal) to 2
            }
            return null
        }
        if (scale == 1000) {
            return parseScaleFrom(words, count = 1, scaleIdx = start, startIdx = start)
        }
    }

    return null
}

private fun parseScaleFrom(
    words: List<String>,
    count: Int,
    scaleIdx: Int,
    startIdx: Int,
): Pair<Int?, Int> {
    val scale = SCALES.getValue(words[scaleIdx])
    if (scale == 12) {
        return (count * 12) to (scaleIdx - startIdx + 1)
    }
    if (scale == 100) {
        var total = count * 100
        var pos = scaleIdx + 1
        val tail = words.getOrNull(pos)?.let { countToken(it) }
        if (tail != null && tail in 1..99) {
            total += tail
            pos++
        }
        return total to (pos - startIdx)
    }
    // scale == 1000
    var total = count * 1000
    var pos = scaleIdx + 1
    if (pos < words.size) {
        val sub = parseCompoundAt(words, pos)
        val subVal = sub?.first
        if (subVal != null && subVal in 1..999) {
            total += subVal
            pos += sub.second
        } else {
            val tail = countToken(words[pos])
            if (tail != null && tail in 1..999) {
                total += tail
                pos++
            }
        }
    }
    return total to (pos - startIdx)
}
// #endregion
