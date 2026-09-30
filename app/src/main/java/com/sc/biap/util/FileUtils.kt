package com.sc.biap.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileUtils {

    /** Matches the cache-path entry in res/xml/file_paths.xml. */
    private const val CAMERA_TEMP_DIR = "camera"

    /**
     * Resolves a design image path robustly.
     * Stored paths can be absolute (e.g. legacy/local path) or relative filename.
     * If the path is absolute and the file exists, returns it.
     * Otherwise, looks for a file with the same name in context.filesDir.
     */
    fun resolveImageFile(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        return if (file.isAbsolute) {
            if (file.exists()) {
                file
            } else {
                File(context.filesDir, file.name)
            }
        } else {
            File(context.filesDir, path)
        }
    }

    /** Stands in for "resolved, and it is not there" - ConcurrentHashMap cannot hold null. */
    private val MISSING = File("")
    private val resolvedImages = java.util.concurrent.ConcurrentHashMap<String, File>()

    /**
     * [resolveImageFile], but the answer is remembered and it only counts a file that exists.
     *
     * WHY THIS IS CACHED:
     *   It is called from composition. Resolving needs File.exists() - a disk stat on the
     *   very thread that draws frames - and every catalogue row does it. Rows are disposed
     *   and rebuilt constantly while scrolling, so the same handful of paths were stat-ed
     *   again and again mid-scroll.
     *
     * WHY CACHING CANNOT GO WRONG HERE:
     *   A given path's answer does not change under us. A photo that is added or replaced is
     *   written under a NEW name, so it arrives as a new key rather than a stale hit. A
     *   cached "present" stays true because the only thing that deletes local photographs is
     *   OrphanImageCleanup, which by definition only removes files no design refers to. And a
     *   cached "absent" is harmless even if it were wrong: the caller falls through to the
     *   uploaded copy, which is the correct behaviour anyway and still shows the photograph.
     */
    fun resolveExistingImageFile(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        resolvedImages[path]?.let { return if (it === MISSING) null else it }
        val resolved = resolveImageFile(context, path)?.takeIf { it.exists() }
        // Bounded: a runaway catalogue should not pin an unbounded map in memory.
        if (resolvedImages.size < 512) resolvedImages[path] = resolved ?: MISSING
        return resolved
    }

    /**
     * Copies a Uri content to local app filesDir asynchronously on IO dispatcher,
     * closing the input stream to prevent file descriptor leaks.
     */
    suspend fun copyUriToInternalStorage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                file.name
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * A throwaway file in the cache for the camera to write its capture into.
     *
     * The camera app writes to a Uri we hand it rather than returning bytes, so the
     * destination has to exist as a shareable FileProvider Uri before the intent is
     * launched. The capture is copied into filesDir on success; the cache copy is
     * disposable and may be reclaimed by the system at any time.
     */
    fun createCameraTempUri(context: Context): Uri {
        // Kept in its own subfolder so the FileProvider grants access to exactly this
        // directory rather than the whole cache. Nothing else is shareable out of the app.
        val cameraDir = File(context.cacheDir, CAMERA_TEMP_DIR).apply { mkdirs() }
        val file = File(cameraDir, "temp_camera_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Formats prices represented as Long cents/paise into clean strings.
     */
    fun formatPrice(priceInCents: Long): String {
        val doubleVal = priceInCents / 100.0
        return if (priceInCents % 100 == 0L) {
            "%.0f".format(doubleVal)
        } else {
            "%.2f".format(doubleVal)
        }
    }

    /**
     * Safely parses a double price string into a Long representation of cents/paise
     * using Math.round to prevent floating-point precision truncation issues.
     */
    fun parsePriceToCents(priceString: String): Long {
        val parsedDouble = priceString.toDoubleOrNull() ?: 0.0
        return Math.round(parsedDouble * 100)
    }
}

