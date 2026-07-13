package com.example.biap.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class GoogleDriveSyncManager {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

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
