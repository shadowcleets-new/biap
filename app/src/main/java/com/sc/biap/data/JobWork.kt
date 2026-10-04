package com.sc.biap.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * @file JobWork.kt
 * @description Goods that have left the premises for a karigar to work on, and what came back.
 *
 * WHY THIS IS NOT A STOCK MOVEMENT:
 *   Twenty pieces at Deen Mohd's for printing are not sold, not lost and not on the shelf.
 *   Counting them as stock means the shelf lies; removing them means the pieces vanish from
 *   the catalogue while they still exist and are still owed back. So they are tracked in
 *   their own ledger, and the design reports them as OUT rather than as gone.
 *
 * WHY SHORT RETURNS ARE A FIRST-CLASS CASE:
 *   Sending twenty and receiving eighteen is normal and is exactly what goes unnoticed for
 *   months. A job is therefore never silently closed by arithmetic: what came back is
 *   recorded as it happens, and the difference stays visible until the owner says the job
 *   is finished.
 *
 * [TABLE OF CONTENTS]
 * 1. TYPES
 * 2. CREATING AND RECEIVING
 * 3. READING THE LEDGER
 */

// #region 1. Types

/**
 * One consignment: these pieces, of this design and size, with this karigar, for this activity.
 *
 * Size is carried because a workshop sends sizes separately and gets them back separately,
 * and a job that cannot say which size came back cannot restore the right stock.
 */
@Serializable
data class JobWork(
    val id: String = UUID.randomUUID().toString(),
    val designId: String,
    val size: String,
    /** Who has them. The karigar's NAME, matching the Hub, for the reason designs do. */
    val karigar: String,
    /** What they are doing - one of the Hub's activities. */
    val activity: String,
    val sentQuantity: Int,
    val receivedQuantity: Int = 0,
    val sentAt: Long,
    /** When the last pieces came back, or 0 while any are still out. */
    val lastReceivedAt: Long = 0L,
    /**
     * Closed by the owner, not by arithmetic.
     *
     * A job short by two is finished when they say the two are not coming - writing itself
     * off the moment the count matched would hide exactly the loss worth seeing.
     */
    val closedAt: Long = 0L,
    val note: String = "",
    val updatedAt: Long = sentAt,
) {
    /** Still at the karigar's. Never negative, even if more came back than went out. */
    val outstanding: Int get() = (sentQuantity - receivedQuantity).coerceAtLeast(0)

    val isOpen: Boolean get() = closedAt == 0L

    /** Everything came back. A job can be complete and still open, awaiting nothing. */
    val isComplete: Boolean get() = receivedQuantity >= sentQuantity

    /** Closed with pieces never accounted for - the number worth showing the owner. */
    val shortfall: Int get() = if (isOpen) 0 else (sentQuantity - receivedQuantity).coerceAtLeast(0)

    fun daysOut(now: Long): Int = ((now - sentAt).coerceAtLeast(0L) / 86_400_000L).toInt()
}

/** One karigar's open work, for the board that answers "where are my goods". */
data class KarigarLoad(
    val karigar: String,
    val pieces: Int,
    val jobs: Int,
    /** The longest any of these has been out, which is the one worth asking about. */
    val oldestDaysOut: Int,
)
// #endregion

// #region 2. Creating and receiving

/** Beyond this, a consignment is worth asking about rather than scrolling past. */
const val JOB_OVERDUE_DAYS = 14

/**
 * Record pieces coming back.
 *
 * Returns the job as it now stands, or null when the quantity makes no sense. Receiving more
 * than was sent is rejected rather than clamped: it means a miscount somewhere, and quietly
 * accepting it would put stock on the shelf that never existed.
 */
fun receive(job: JobWork, quantity: Int, now: Long): JobWork? {
    if (quantity <= 0) return null
    if (job.receivedQuantity + quantity > job.sentQuantity) return null
    return job.copy(
        receivedQuantity = job.receivedQuantity + quantity,
        lastReceivedAt = now,
        updatedAt = now,
    )
}

/** Finish a job, whatever is or is not back. The shortfall is kept, not erased. */
fun close(job: JobWork, now: Long): JobWork =
    if (!job.isOpen) job else job.copy(closedAt = now, updatedAt = now)
// #endregion

// #region 3. Reading the ledger

/** Open jobs only: what is genuinely out of the building right now. */
fun openJobs(jobs: List<JobWork>): List<JobWork> =
    jobs.filter { it.isOpen && it.outstanding > 0 }

/** How much is out, per karigar, worst-delayed first. */
fun loadByKarigar(jobs: List<JobWork>, now: Long): List<KarigarLoad> =
    openJobs(jobs)
        .groupBy { it.karigar }
        .map { (karigar, theirs) ->
            KarigarLoad(
                karigar = karigar,
                pieces = theirs.sumOf { it.outstanding },
                jobs = theirs.size,
                oldestDaysOut = theirs.maxOf { it.daysOut(now) },
            )
        }
        .sortedWith(compareByDescending<KarigarLoad> { it.oldestDaysOut }.thenByDescending { it.pieces })

/** Out long enough to be worth chasing. */
fun overdueJobs(jobs: List<JobWork>, now: Long, days: Int = JOB_OVERDUE_DAYS): List<JobWork> =
    openJobs(jobs).filter { it.daysOut(now) >= days }.sortedByDescending { it.daysOut(now) }

/** Pieces of one design currently out, by size - what the shelf is missing but still owns. */
fun outBySize(jobs: List<JobWork>, designId: String): Map<String, Int> =
    openJobs(jobs)
        .filter { it.designId == designId }
        .groupBy { it.size }
        .mapValues { (_, theirs) -> theirs.sumOf { it.outstanding } }

/** Jobs closed with pieces missing - the losses, once the owner has called them losses. */
fun shortfalls(jobs: List<JobWork>): List<JobWork> =
    jobs.filter { !it.isOpen && it.shortfall > 0 }.sortedByDescending { it.closedAt }
// #endregion
