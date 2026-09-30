package com.sc.biap.ui.main

// #region Imports
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
// #endregion

// #region Constants
private const val DAY_MS = 86_400_000L
private const val HALF_DAY_MS = 43_200_000L

/** Beyond this, a count of days stops being easier to read than the date itself. */
private const val DAYS_BEFORE_SHOWING_A_DATE = 7L
// #endregion

// #region Public API

/**
 * The sync half of the catalogue header: "SYNCED AT 7:45 PM", "SYNCED 3 DAYS AGO".
 *
 * WHY THIS IS NOT JUST A CLOCK TIME:
 *   The header used to print "SYNCED 09:12" and nothing else. A bare time is only
 *   unambiguous on the day it happened - after that it reads as though the sync were
 *   recent when it might be a fortnight old, which is the same class of lie as the
 *   header reporting a success it never observed. So the time is shown only while it
 *   still identifies a moment on its own, and anything older says how old it is.
 *
 * WHY CALENDAR DAYS RATHER THAN ELAPSED HOURS:
 *   "Within 24 hours" and "today" disagree for half of every day. A sync at 11pm read
 *   back at 1am is 2 hours old but belongs to yesterday, and printing "AT 11:00 PM"
 *   for it invites reading it as tonight. Bucketing by calendar day means the words
 *   match what the owner would say out loud, and yesterday keeps its time because the
 *   day name has already removed the ambiguity.
 *
 * @param syncedAt when a sync last completed, or 0 if one never has.
 * @param now the current wall-clock time, passed in so this stays testable.
 * @param use24Hour the device's own clock preference, so this does not overrule it.
 */
fun syncStamp(
    syncedAt: Long,
    now: Long,
    use24Hour: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    if (syncedAt <= 0L) return "NOT SYNCED YET"

    val days = calendarDaysBetween(syncedAt, now)
    return when {
        // Negative means the sync is stamped in the future - a clock correction or a
        // flight across timezones. Treating it as "just now" is the one reading that is
        // never alarming and never wrong by much.
        days <= 0L -> "SYNCED AT ${timeOf(syncedAt, use24Hour, locale)}"
        days == 1L -> "SYNCED YESTERDAY AT ${timeOf(syncedAt, use24Hour, locale)}"
        days < DAYS_BEFORE_SHOWING_A_DATE -> "SYNCED $days DAYS AGO"
        else -> "SYNCED ON ${SimpleDateFormat("dd/MM/yyyy", locale).format(Date(syncedAt))}"
    }
}
// #endregion

// #region Helpers

private fun timeOf(at: Long, use24Hour: Boolean, locale: Locale): String {
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    // Uppercased so "7:45 pm" matches the header's voice; a 24-hour stamp is unaffected.
    return SimpleDateFormat(pattern, locale).format(Date(at)).uppercase(locale)
}

/**
 * Whole calendar days between two instants, in the device's own timezone.
 *
 * Rounded to the nearest day rather than truncated: across a daylight-saving boundary two
 * midnights are 23 or 25 hours apart, and truncating that turns "yesterday" into "today".
 * India has no such boundary, but a phone that crosses one should not start misreporting.
 */
private fun calendarDaysBetween(then: Long, now: Long): Long =
    (startOfDay(now) - startOfDay(then) + HALF_DAY_MS).floorDiv(DAY_MS)

private fun startOfDay(at: Long): Long = Calendar.getInstance().apply {
    timeInMillis = at
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
// #endregion
