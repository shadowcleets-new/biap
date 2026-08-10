package com.sc.biap.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** An appDataFolder entry: Drive's opaque id plus the name we chose for it. */
data class DriveFile(val id: String, val name: String)

class GoogleDriveSyncManager {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val TAG = "DriveSync"
    }

    /**
     * Search for a file by name in the appDataFolder on Google Drive.
     * Returns the file ID if found, or null if not found.
     */
    suspend fun searchFile(accessToken: String, fileName: String = "bi_data.json.enc"): String? = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode("name='$fileName'", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                val jsonObject = json.parseToJsonElement(bodyString).jsonObject
                val files = jsonObject["files"]?.jsonArray ?: return@withContext null
                if (files.isNotEmpty()) {
                    return@withContext files[0].jsonObject["id"]?.jsonPrimitive?.content
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Every appDataFolder file whose name starts with [namePrefix], newest name last.
     *
     * Used for image retention: photo files are named per version, so listing by design
     * prefix yields that design's version history without tracking it anywhere else.
     */
    suspend fun listFilesByPrefix(
        accessToken: String,
        namePrefix: String
    ): List<DriveFile> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode("name contains '$namePrefix'", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&q=$encodedQuery&fields=files(id,name)&pageSize=1000"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val files = json.parseToJsonElement(bodyString).jsonObject["files"]?.jsonArray
                    ?: return@withContext emptyList()
                return@withContext files.mapNotNull { element ->
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    DriveFile(id, name)
                }.sortedBy { it.name }
            }
        } catch (e: Exception) {
            Log.w(TAG, "listFilesByPrefix($namePrefix) failed: ${e.message}")
        }
        emptyList()
    }

    /**
     * Upload raw bytes as a NEW appDataFolder file. Returns the new file ID.
     *
     * Single-request multipart rather than the two-step create-then-upload used for the
     * JSON: a photo that created its metadata and then failed mid-upload would leave an
     * empty file behind carrying a name that a design might later be pointed at.
     */
    suspend fun uploadBinaryFile(
        accessToken: String,
        bytes: ByteArray,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        val metadata = """{"name":"$fileName","parents":["appDataFolder"]}"""
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful) {
                    Log.w(TAG, "uploadBinaryFile($fileName) HTTP ${response.code}: $bodyString")
                    return@withContext null
                }
                return@withContext json.parseToJsonElement(bodyString ?: return@withContext null)
                    .jsonObject["id"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            Log.w(TAG, "uploadBinaryFile($fileName) failed: ${e.message}")
        }
        null
    }

    /** Raw bytes of an appDataFolder file. */
    suspend fun downloadBinaryFile(
        accessToken: String,
        fileId: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "downloadBinaryFile($fileId) HTTP ${response.code}")
                    return@withContext null
                }
                return@withContext response.body?.bytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "downloadBinaryFile($fileId) failed: ${e.message}")
        }
        null
    }

    /** Best-effort delete, used only for retention of superseded photo versions. */
    suspend fun deleteFile(accessToken: String, fileId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "deleteFile($fileId) failed: ${e.message}")
            false
        }
    }

    /**
     * Download the file content from Google Drive by its file ID.
     * Returns the raw string content.
     */
    suspend fun downloadFile(accessToken: String, fileId: String): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext response.body?.string()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Upload data to Google Drive.
     * If existingFileId is null, it creates the file metadata first, then uploads the media.
     * Returns the file ID on success, or null on failure.
     */
    suspend fun uploadFile(
        accessToken: String,
        content: String,
        existingFileId: String?,
        fileName: String = "bi_data.json.enc"
    ): String? = withContext(Dispatchers.IO) {
        var fileId = existingFileId

        if (fileId == null) {
            // Step 1: Create metadata
            val url = "https://www.googleapis.com/drive/v3/files"
            val metadataJson = """
                {
                  "name": "$fileName",
                  "parents": ["appDataFolder"]
                }
            """.trimIndent()
            val requestBody = metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bodyString = response.body?.string() ?: return@withContext null
                    val responseJson = json.parseToJsonElement(bodyString).jsonObject
                    fileId = responseJson["id"]?.jsonPrimitive?.content
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }

        val targetFileId = fileId ?: return@withContext null

        // Step 2: Upload media content to the file ID
        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$targetFileId?uploadType=media"
        val mediaBody = content.toRequestBody("application/octet-stream".toMediaType())
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", "Bearer $accessToken")
            .patch(mediaBody)
            .build()

        try {
            client.newCall(uploadRequest).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext targetFileId
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Renames a Drive file (metadata-only PATCH). Used to quarantine unreadable payloads.
     */
    suspend fun renameFile(accessToken: String, fileId: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val body = """{"name": "$newName"}""".toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .patch(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }
}
