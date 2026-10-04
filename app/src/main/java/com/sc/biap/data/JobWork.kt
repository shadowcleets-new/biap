package com.sc.biap.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * @file JobWork.kt
 * @description Everything that is with a karigar and has to come back.
 *
 * ONE RECORD, FOUR KINDS:
 *   A sample being made, a batch in production, a few pieces out for alteration and finished
 *   stock sent for extra work are the same sentence with different nouns - somebody has
 *   something of ours and we need it back. Modelling them separately would have meant four
 *   tables, four screens and four places to forget to check. They differ in exactly three
 *   ways, and each is a property of the kind rather than a different shape of record:
 *
 *     - whether sending takes pieces OFF the shelf
 *     - whether receiving puts pieces ON it
 *     - how long is too long
 *
 * WHY SENDING IS NOT ALWAYS A STOCK MOVEMENT:
 *   Commissioning new production takes nothing off the shelf, because the pieces do not exist
 *   yet - the karigar is being given an instruction, not goods. Only an alteration or extra
 *   work on finished pieces actually hands something over. Getting this backwards is what
 *   made "Send out" refuse to work on an empty size, which is precisely the size most likely
 *   to need making.
 *
 * [TABLE OF CONTENTS]
 * 1. KINDS
 * 2. THE RECORD
 * 3. CREATING AND RECEIVING
 * 4. READING THE LEDGER
 */

// #region 1. Kinds

@Serializable
enum class WorkKind {
    /** A design that does not exist yet: one piece, made to be looked at and judged. */
    SAMPLE,

    /** Pieces being made. Nothing leaves the shelf; what comes back joins it. */
    PRODUCTION,

    /** A few finished pieces that came back wrong, gone to be put right. */
    ALTERATION,

    /** Finished stock sent for further work - printing, dyeing - and expected back. */
    OUTWORK,
}

/**
 * Days before a lot is worth chasing.
 *
 * Per kind because the honest answer differs by an order of magnitude: three pieces being
 * restitched are late in a fortnight, a run of forty is not. One shared number would have had
 * to be the longest of them, which would mean the alterations - the ones that actually get
 * forgotten - never flagged at all.
 */
val WorkKind.overdueDays: Int
    get() = when (this) {
        WorkKind.ALTERATION -> 10
        WorkKind.SAMPLE -> 15
        WorkKind.OUTWORK -> 14
        WorkKind.PRODUCTION -> 30
    }

/** Sending hands over goods we already have, so the shelf goes down now. */
val WorkKind.takesFromStockOnSend: Boolean
    get() = this == WorkKind.ALTERATION || this == WorkKind.OUTWORK

/**
 * What comes back joins the shelf.
 *
 * Every kind but a sample. A sample is one piece made to be judged, not sold, and putting it
 * into stock would quietly offer a customer the thing the workshop is still deciding about.
 */
val WorkKind.addsToStockOnReceive: Boolean
    get() = this != WorkKind.SAMPLE

/** What the owner calls it. */
val WorkKind.label: String
    get() = when (this) {
        WorkKind.SAMPLE -> "Sample"
        WorkKind.PRODUCTION -> "Production"
        WorkKind.ALTERATION -> "Alteration"
        WorkKind.OUTWORK -> "Out for work"
    }

/** How a sample ended. */
@Serializable
enum class SampleOutcome { PENDING, APPROVED, REJECTED }
// #endregion

// #region 2. The record

/**
 * One consignment with one karigar.
 *
 * A production order covering four sizes is four of these sharing a [lotId], because sizes go
 * out and come back separately and a record that cannot say which size returned cannot put
 * the right stock back.
 */
@Serializable
data class JobWork(
    val id: String = UUID.randomUUID().toString(),
    val kind: WorkKind = WorkKind.OUTWORK,

    /**
     * Groups the sizes of one order into one card.
     *
     * Defaults to the record's own id, so a job that is only one size is simply a lot of one
     * and needs no special case anywhere.
     */
    val lotId: String = id,

    /** Empty for a sample, whose design does not exist yet. See [designName]. */
    val designId: String = "",

    /**
     * The working name of a sample, before there is a design to point at.
     *
     * A sample is an idea with a photograph. It earns a catalogue entry when it is approved,
     * not before - half the ideas discussed never become anything, and a catalogue full of
     * things that were never made is worse than no record of them.
     */
    val designName: String = "",

    val size: String = "",
    val karigar: String,
    /** What they are doing - one of the Hub's activities. */
    val activity: String = "",

    val sentQuantity: Int,
    val receivedQuantity: Int = 0,

    /** Production only. Suggested automatically, overridable, as batches are everywhere else. */
    val batchNumber: Int = 0,

    val sentAt: Long,
    val lastReceivedAt: Long = 0L,

    /**
     * Closed by the owner, not by arithmetic.
     *
     * A job short by two is finished when they say the two are not coming - writing itself
     * off the moment the count matched would hide exactly the loss worth seeing.
     */
    val closedAt: Long = 0L,

    /** Samples only. */
    val outcome: SampleOutcome = SampleOutcome.PENDING,

    /** A photograph of the defect, or of the sample. Worth more than a field describing it. */
    val photoPath: String? = null,
    val photoRef: String? = null,

    /** A spoken note, because the people using this will not type one. */
    val voicePath: String? = null,
    val voiceRef: String? = null,

    val note: String = "",
    val updatedAt: Long = sentAt,
) {
    /** Still with the karigar. Never negative, even if more came back than went out. */
    val outstanding: Int get() = (sentQuantity - receivedQuantity).coerceAtLeast(0)

    val isOpen: Boolean get() = closedAt == 0L

    /** Everything came back. A lot can be complete and still open, awaiting nothing. */
    val isComplete: Boolean get() = receivedQuantity >= sentQuantity

    /** Closed with pieces never accounted for - the number worth showing the owner. */
    val shortfall: Int get() = if (isOpen) 0 else (sentQuantity - receivedQuantity).coerceAtLeast(0)

    fun daysOut(now: Long): Int = ((now - sentAt).coerceAtLeast(0L) / 86_400_000L).toInt()

    /** Been too long for THIS kind of work. */
    fun isOverdue(now: Long): Boolean = isOpen && outstanding > 0 && daysOut(now) >= kind.overdueDays

    /** What to call it on screen: a real design's name, or a sample's working one. */
    fun titleOr(designName: String?): String =
        designName?.takeIf { it.isNotBlank() } ?: this.designName.ifBlank { "Untitled" }
}

/** One karigar's open work, for the board that answers "where are my goods". */
data class KarigarLoad(
    val karigar: String,
    val pieces: Int,
    val jobs: Int,
    /** The longest any of these has been out, which is the one worth asking about. */
    val oldestDaysOut: Int,
)

/** The sizes of one order, shown as a single card. */
data class WorkLot(
    val lotId: String,
    val kind: WorkKind,
    val karigar: String,
    val designId: String,
    val designName: String,
    val activity: String,
    val batchNumber: Int,
    val rows: List<JobWork>,
) {
    val sentAt: Long get() = rows.minOf { it.sentAt }
    val outstanding: Int get() = rows.sumOf { it.outstanding }
    val sent: Int get() = rows.sumOf { it.sentQuantity }
    val received: Int get() = rows.sumOf { it.receivedQuantity }
    val isOpen: Boolean get() = rows.any { it.isOpen }
    val photoPath: String? get() = rows.firstNotNullOfOrNull { it.photoPath }
    val voicePath: String? get() = rows.firstNotNullOfOrNull { it.voicePath }
    val note: String get() = rows.firstOrNull { it.note.isNotBlank() }?.note.orEmpty()
    val outcome: SampleOutcome get() = rows.first().outcome

    fun daysOut(now: Long): Int = ((now - sentAt).coerceAtLeast(0L) / 86_400_000L).toInt()
    fun isOverdue(now: Long): Boolean = isOpen && outstanding > 0 && daysOut(now) >= kind.overdueDays

    /** "L 8 · XL 8 · XXL 4", or empty when the lot has no sizes worth listing. */
    val sizeBreakdown: String
        get() = rows.filter { it.size.isNotBlank() }
            .joinToString("  ") { "${it.size} ${it.outstanding.takeIf { q -> q > 0 } ?: it.sentQuantity}" }
}
// #endregion

// #region 3. Creating and receiving

/** Kept for the screens that still speak of a single overdue threshold. */
const val JOB_OVERDUE_DAYS = 14

/**
 * Record pieces coming back.
 *
 * Returns the row as it now stands, or null when the quantity makes no sense. Receiving more
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

/** Judge a sample. Either answer closes it; only approval leads anywhere. */
fun decideSample(job: JobWork, approved: Boolean, now: Long): JobWork = job.copy(
    outcome = if (approved) SampleOutcome.APPROVED else SampleOutcome.REJECTED,
    closedAt = now,
    updatedAt = now,
)
// #endregion

// #region 4. Reading the ledger

/** Open rows only: what is genuinely out of the building right now. */
fun openJobs(jobs: List<JobWork>): List<JobWork> =
    jobs.filter { it.isOpen && it.outstanding > 0 }

/**
 * The open work as cards, worst first.
 *
 * Overdue before merely old, because the list is read to find what needs chasing and sorting
 * by age alone buries a week-late alteration under a month-old production run that is fine.
 */
fun openLots(jobs: List<JobWork>, now: Long): List<WorkLot> =
    openJobs(jobs)
        .groupBy { it.lotId }
        .map { (lotId, rows) ->
            val first = rows.minByOrNull { it.sentAt } ?: rows.first()
            WorkLot(
                lotId = lotId,
                kind = first.kind,
                karigar = first.karigar,
                designId = first.designId,
                designName = first.designName,
                activity = first.activity,
                batchNumber = first.batchNumber,
                rows = rows.sortedBy { it.sentAt },
            )
        }
        .sortedWith(
            compareByDescending<WorkLot> { it.isOverdue(now) }
                .thenByDescending { it.daysOut(now) }
        )

/** How much is out, per karigar, worst-delayed first. */
fun loadByKarigar(jobs: List<JobWork>, now: Long): List<KarigarLoad> =
    openJobs(jobs)
        .groupBy { it.karigar }
        .map { (karigar, theirs) ->
            KarigarLoad(
                karigar = karigar,
                pieces = theirs.sumOf { it.outstanding },
                jobs = theirs.map { it.lotId }.distinct().size,
                oldestDaysOut = theirs.maxOf { it.daysOut(now) },
            )
        }
        .sortedWith(compareByDescending<KarigarLoad> { it.oldestDaysOut }.thenByDescending { it.pieces })

/** Out long enough to be worth chasing, each judged by its own kind's patience. */
fun overdueLots(lots: List<WorkLot>, now: Long): List<WorkLot> = lots.filter { it.isOverdue(now) }

/**
 * Pieces of one design currently out, by size.
 *
 * Only the kinds that actually took pieces off the shelf. Counting a production order here
 * would claim the workshop is holding stock that has not been made yet.
 */
fun outBySize(jobs: List<JobWork>, designId: String): Map<String, Int> =
    openJobs(jobs)
        .filter { it.designId == designId && it.kind.takesFromStockOnSend && it.size.isNotBlank() }
        .groupBy { it.size }
        .mapValues { (_, theirs) -> theirs.sumOf { it.outstanding } }

/** Pieces of one design being MADE, by size - what is coming rather than what is missing. */
fun incomingBySize(jobs: List<JobWork>, designId: String): Map<String, Int> =
    openJobs(jobs)
        .filter { it.designId == designId && !it.kind.takesFromStockOnSend && it.size.isNotBlank() }
        .groupBy { it.size }
        .mapValues { (_, theirs) -> theirs.sumOf { it.outstanding } }

/** Jobs closed with pieces missing - the losses, once the owner has called them losses. */
fun shortfalls(jobs: List<JobWork>): List<JobWork> =
    jobs.filter { !it.isOpen && it.shortfall > 0 && it.kind != WorkKind.SAMPLE }
        .sortedByDescending { it.closedAt }

/** Samples still waiting to be judged. */
fun pendingSamples(jobs: List<JobWork>): List<JobWork> =
    jobs.filter { it.kind == WorkKind.SAMPLE && it.outcome == SampleOutcome.PENDING }
        .sortedBy { it.sentAt }
// #endregion
