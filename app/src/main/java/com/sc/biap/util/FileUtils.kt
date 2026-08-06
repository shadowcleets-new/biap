package com.sc.biap.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileUtils {
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

