package com.sc.biap.util

// #region Imports
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
// #endregion

/**
 * @file VoiceNotes.kt
 * @description Recording and playing a spoken note.
 *
 * WHY SPEAKING RATHER THAN TYPING:
 *   "The dupatta fall has come open on the left side" is a sentence nobody in a workshop is
 *   going to type on a phone, and the note that does not get typed is the one that has to be
 *   remembered instead. Spoken, it takes four seconds.
 *
 * AAC IN AN MP4 CONTAINER, MONO, 32 kbps:
 *   About 4 KB a second, so a twenty-second note is under 100 KB. That matters because every
 *   note syncs to Drive for everyone on the account; at WAV quality the same note would be
 *   about 2 MB and the sync would become something people avoid running.
 */
object VoiceNotes {

    private const val DIR = "voice_notes"

    /** Where a note lives. Internal storage, so it is private and goes with an uninstall. */
    fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun newFile(context: Context): File =
        File(directory(context), "vn_${System.currentTimeMillis()}.m4a")

    fun resolve(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        val direct = File(path)
        if (direct.exists()) return direct
        // Stored as a bare name on a device that has since changed its files directory.
        return File(directory(context), direct.name).takeIf { it.exists() }
    }

    /**
     * A recorder ready to start, writing into [file].
     *
     * The no-argument constructor is deprecated from API 31 but is the only one that exists
     * below it, and this app still runs on 24.
     */
    @Suppress("DEPRECATION")
    fun recorder(context: Context, file: File): MediaRecorder {
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        return rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioEncodingBitRate(32_000)
            setAudioSamplingRate(22_050)
            setOutputFile(file.absolutePath)
        }
    }

    /** How long a note runs, in seconds, or 0 when it cannot be read. */
    fun durationSeconds(context: Context, path: String?): Int {
        val file = resolve(context, path) ?: return 0
        return runCatching {
            MediaPlayer().use { player ->
                player.setDataSource(file.absolutePath)
                player.prepare()
                (player.duration + 500) / 1000
            }
        }.getOrDefault(0)
    }

    /** Delete a note's file. Failing to is not worth reporting - it is a cache miss at worst. */
    fun delete(context: Context, path: String?) {
        runCatching { resolve(context, path)?.delete() }
    }

    private inline fun <T> MediaPlayer.use(block: (MediaPlayer) -> T): T =
        try {
            block(this)
        } finally {
            runCatching { release() }
        }
}
