package com.sc.biap.ui

import com.sc.biap.ui.main.syncStamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/**
 * The header spent every cold start claiming "NOT SYNCED YET" regardless of when the last
 * sync was, because the time was only ever held in memory. With it persisted, the label has
 * to say something sensible about an arbitrarily old timestamp rather than a bare clock
 * time that silently stops meaning anything after midnight.
 */
class SyncStampTest {

    private val locale = Locale.UK

    /** A fixed instant, built through Calendar so the test agrees with the default timezone. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private val now = at(2026, 3, 26, 14, 30)   // 26 Mar 2026, 2:30pm

    @Test
    fun `never synced says so rather than inventing a time`() {
        assertEquals("NOT SYNCED YET", syncStamp(0L, now, use24Hour = false, locale = locale))
    }

    @Test
    fun `today shows the time, in the device's clock format`() {
        val earlier = at(2026, 3, 26, 19, 45)
        assertEquals("SYNCED AT 7:45 PM", syncStamp(earlier, earlier, false, locale))
        assertEquals("SYNCED AT 19:45", syncStamp(earlier, earlier, true, locale))
    }

    @Test
    fun `yesterday keeps its time because the day name removes the ambiguity`() {
        val yesterday = at(2026, 3, 25, 23, 5)
        assertEquals("SYNCED YESTERDAY AT 11:05 PM", syncStamp(yesterday, now, false, locale))
    }

    @Test
    fun `a sync two hours ago but on yesterday's date is not called today`() {
        // 1am, having synced at 11pm the night before: two hours elapsed, but a bare
        // "AT 11:00 PM" would read as tonight.
        val lateLastNight = at(2026, 3, 25, 23, 0)
        val earlyHours = at(2026, 3, 26, 1, 0)
        assertEquals(
            "SYNCED YESTERDAY AT 11:00 PM",
            syncStamp(lateLastNight, earlyHours, false, locale),
        )
    }

    @Test
    fun `a few days back counts the days`() {
        assertEquals("SYNCED 2 DAYS AGO", syncStamp(at(2026, 3, 24, 9, 0), now, false, locale))
        assertEquals("SYNCED 6 DAYS AGO", syncStamp(at(2026, 3, 20, 9, 0), now, false, locale))
    }

    @Test
    fun `a week or more shows the date instead of a day count`() {
        assertEquals("SYNCED ON 19/03/2026", syncStamp(at(2026, 3, 19, 9, 0), now, false, locale))
        assertEquals("SYNCED ON 02/01/2026", syncStamp(at(2026, 1, 2, 9, 0), now, false, locale))
    }

    @Test
    fun `the day boundary is calendar-based, not a rolling 24 hours`() {
        // 00:01 today against 23:59 yesterday is two minutes, and still yesterday.
        val justBeforeMidnight = at(2026, 3, 25, 23, 59)
        val justAfterMidnight = at(2026, 3, 26, 0, 1)
        assertEquals(
            "SYNCED YESTERDAY AT 11:59 PM",
            syncStamp(justBeforeMidnight, justAfterMidnight, false, locale),
        )
    }

    @Test
    fun `a timestamp in the future is treated as just now, not as a date`() {
        // Clock correction or a flight west. Anything but a recent-looking label would alarm.
        val future = at(2026, 3, 27, 10, 0)
        assertEquals("SYNCED AT 10:00 AM", syncStamp(future, now, false, locale))
    }
}
