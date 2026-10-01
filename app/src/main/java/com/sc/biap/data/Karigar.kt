package com.sc.biap.data

// #region Imports
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
// #endregion

/**
 * @file Karigar.kt
 * @description The people who make the clothes, and what each of them does.
 *
 * [TABLE OF CONTENTS]
 * 1. MODEL
 * 2. THE STARTING LISTS
 * 3. MATCHING WORK TO AN ACTIVITY
 *
 * WHY A KARIGAR IS A RECORD RATHER THAN A STRING:
 *   A design has always held `karigars: List<String>` - names typed in freehand, per design.
 *   That is fine until the same person has to be recognised across designs, which is exactly
 *   what assigning work by activity needs: "Deen Mohd." typed on one design and "Deen Mohd"
 *   on another are two different people to the app and one person to the workshop. The Hub
 *   list is now the master, and a design points at names from it.
 *
 *   The design still stores names rather than ids. That is deliberate: a design is a
 *   historical record, and it should still read correctly if a karigar is later removed from
 *   the Hub. Renaming someone in the Hub does not rewrite old designs, for the same reason.
 */

// #region 1. Model

/**
 * One karigar, and the activities they are assigned to.
 *
 * [activities] is many-to-many by design - Deen Mohd. may do both printing and dyeing, and
 * printing may be done by several people. Both directions are real in a workshop.
 */
@Serializable
@Immutable
data class Karigar(
    val id: String,
    val name: String,
    val activities: List<String> = emptyList(),
    val updatedAt: Long = 0L,
) {
    /** Trimmed and case-folded, for comparing two spellings of the same person. */
    val key: String get() = name.trim().lowercase()
}

// #endregion

// #region 2. The starting lists

/**
 * The activities the workshop is organised around.
 *
 * Seeded rather than hardcoded: these live in their own table so the Hub can grow them
 * later without a migration. This list is only what a fresh database starts with.
 */
object Activities {
    const val MAKING = "Making (Cutting/Stitching)"
    const val KATHA = "Katha"
    const val EMBROIDERY = "Embroidery"
    const val DYEING = "Dyeing"
    const val PRINTING = "Printing"

    val DEFAULTS: List<String> = listOf(MAKING, KATHA, EMBROIDERY, DYEING, PRINTING)
}

/** The karigars a fresh database starts with. Added once, then owned by the Hub. */
val DEFAULT_KARIGARS: List<String> = listOf(
    "Umesh Devaliya",
    "Saleem Master",
    "Bablu",
    "Sandeep",
    "Dinesh Soni",
    "Babli",
    "Atul",
    "Israr",
    "Mohd. Yusuf",
    "Rakesh Vanra",
    "Deen Mohd.",
)

/**
 * The key two spellings of the same person share.
 *
 * Punctuation is dropped and runs of anything non-alphanumeric become one space, so
 * "Mohd. Yusuf", "Mohd Yusuf" and "mohd  yusuf" are one person. That matters because the
 * names on existing designs were typed by hand over months, while the Hub list was seeded
 * from one careful spelling - matching on the exact string would leave most of them
 * unlinked and quietly duplicate the workshop.
 *
 * It does NOT try to be clever beyond that: no initials, no fuzzy distance. Two names that
 * differ by an actual letter are two people, and merging them would be worse than leaving
 * them apart.
 */
internal fun matchKey(name: String): String =
    name.trim().lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

/** What linking a set of hand-typed names to the Hub would do. */
@Immutable
data class KarigarLinkPlan(
    /** Typed spelling to the Hub's spelling, for names that differ only in how they were written. */
    val renames: Map<String, String> = emptyMap(),
    /** Names on designs that nobody on the Hub matches; these join the Hub as they are. */
    val toAdd: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = renames.isEmpty() && toAdd.isEmpty()
}

/**
 * Work out how to tie hand-typed karigar names to the Hub list.
 *
 * Nothing is ever dropped. A name the Hub does not know joins the Hub rather than being
 * deleted off the design - a design is a record of who actually made it, and discarding a
 * name because the Hub had not heard of them would be losing history to tidy a list.
 */
fun planKarigarLinks(designNames: Collection<String>, hub: List<Karigar>): KarigarLinkPlan {
    val byKey = hub.associateBy { matchKey(it.name) }
    val renames = mutableMapOf<String, String>()
    val toAdd = mutableListOf<String>()
    val added = mutableSetOf<String>()

    for (raw in designNames) {
        val name = raw.trim()
        if (name.isEmpty()) continue
        val key = matchKey(name)
        val known = byKey[key]
        if (known != null) {
            if (known.name != name) renames[name] = known.name
        } else if (added.add(key)) {
            toAdd += name
        }
    }
    return KarigarLinkPlan(renames, toAdd)
}

/** The design's list after linking: canonical spellings, de-duplicated, order kept. */
fun applyKarigarLinks(names: List<String>, plan: KarigarLinkPlan): List<String> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<String>()
    for (raw in names) {
        val name = raw.trim()
        if (name.isEmpty()) continue
        val canonical = plan.renames[name] ?: name
        if (seen.add(matchKey(canonical))) out += canonical
    }
    return out
}

/**
 * How likely two names are to be the same person, for ordering the merge picker.
 *
 * NOT a matcher. Nothing is merged on this score - the owner still chooses - so it is free
 * to be generous in a way [matchKey] must not be. Its only job is to put the obvious
 * candidate first so the answer is on screen rather than down a scroll.
 *
 * What it rewards, in the order these actually come up in a workshop:
 *   - one name being the start of the other: "Umesh" against "Umesh Devaliya"
 *   - a whole word in common: "Salim Master" against "Saleem Master"
 *   - a first name a typo away: "Salim" against "Saleem"
 */
fun mergeCandidateScore(from: String, candidate: String): Int {
    val a = matchKey(from)
    val b = matchKey(candidate)
    if (a.isEmpty() || b.isEmpty()) return 0

    var score = 0
    if (a == b) return 1000
    if (a.startsWith(b) || b.startsWith(a)) score += 100

    val wordsA = a.split(" ").filter { it.isNotEmpty() }
    val wordsB = b.split(" ").filter { it.isNotEmpty() }
    score += wordsA.count { it in wordsB } * 30

    // A first name one or two letters out - "salim" against "saleem" - is the common case
    // for a name written down by two different people.
    val firstA = wordsA.firstOrNull().orEmpty()
    val firstB = wordsB.firstOrNull().orEmpty()
    if (firstA.isNotEmpty() && firstB.isNotEmpty() && firstA != firstB) {
        val d = editDistance(firstA, firstB)
        if (d <= 2 && d < maxOf(firstA.length, firstB.length)) score += (3 - d) * 25
    }
    return score
}

/** Above this, a candidate is worth calling out rather than leaving in the long list. */
const val LIKELY_MERGE_SCORE = 40

/** The merge picker's order: most likely first, then everyone else alphabetically. */
fun rankMergeTargets(from: String, candidates: List<Karigar>): List<Karigar> =
    candidates.sortedWith(
        compareByDescending<Karigar> { mergeCandidateScore(from, it.name) }
            .thenBy { it.name.lowercase() }
    )

/** Plain Levenshtein, on names short enough that nothing cleverer is worth the code. */
internal fun editDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    val cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        prev = cur.copyOf()
    }
    return prev[b.length]
}

// #endregion

// #region 3. Matching work to an activity

/**
 * Words too short or too common to identify an activity on their own.
 *
 * Without this, the bracketed part of "Making (Cutting/Stitching)" contributes "and"-sized
 * tokens that match almost any work value and assign half the workshop to every design.
 */
private const val MIN_TOKEN = 4

/** Splits an activity name into the words that could plausibly appear in a work value. */
internal fun activityTokens(activity: String): List<String> =
    activity.split(Regex("[^\\p{L}]+"))
        .map { it.trim().lowercase() }
        .filter { it.length >= MIN_TOKEN }

/**
 * Does this work value describe this activity?
 *
 * Substring on whole words rather than equality, because the work written on a design is
 * more specific than the activity: "Block Printing" is printing, "Hand Embroidery" is
 * embroidery, "Stitching" is part of making. Matching on tokens taken FROM the activity
 * means the activity list stays the vocabulary and the work value can be as descriptive as
 * the owner likes.
 */
fun workMatchesActivity(work: String, activity: String): Boolean {
    val w = work.trim().lowercase()
    if (w.isEmpty()) return false
    val tokens = activityTokens(activity)
    if (tokens.isEmpty()) return false
    return tokens.any { w.contains(it) }
}

/**
 * Is this custom field one that describes work being done?
 *
 * The defaults are "Top - Work", "Bottom - Work" and "Dupatta - Work", but the owner can add
 * their own fields, so this recognises the word rather than the three exact keys.
 */
fun isWorkField(fieldName: String): Boolean =
    fieldName.trim().lowercase().contains("work")

/**
 * Every karigar the Hub implies for a design, from the work written on it.
 *
 * Returns NAMES, in the Hub's own spelling, de-duplicated and in Hub order so the result is
 * stable rather than dependent on which field happened to be read first.
 *
 * Note this only ever ADDS. Callers must not use it to clear a design's karigars: someone
 * assigned by hand is a deliberate act, and a work value being edited is not a statement
 * that the person who was doing it has stopped.
 */
fun karigarsForWork(
    customFields: Map<String, String>,
    karigars: List<Karigar>,
): List<String> {
    val works = customFields
        .filterKeys { isWorkField(it) }
        .values
        .filter { it.isNotBlank() }
    if (works.isEmpty()) return emptyList()

    return karigars
        .filter { karigar ->
            karigar.activities.any { activity ->
                works.any { work -> workMatchesActivity(work, activity) }
            }
        }
        .map { it.name }
        .distinct()
}

// #endregion
