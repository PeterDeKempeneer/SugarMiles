package com.sugarmiles.app.strava

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class StravaException(message: String, val retryable: Boolean) : Exception(message)

/** Thin wrapper around the few Strava API calls we need. */
object StravaClient {
    // Strava announced a new base URL (https://api-v3.strava.com) available from 4 Jan 2027.
    // The current one keeps working for now; switch here when needed.
    private const val API = "https://www.strava.com/api/v3"
    const val OAUTH_TOKEN_URL = "https://www.strava.com/oauth/token"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    /** POST /uploads. Returns the upload object (id, status, error, activity_id). */
    suspend fun upload(token: String, tcx: File, name: String, externalId: String): JSONObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("data_type", "tcx")
            .addFormDataPart("name", name)
            .addFormDataPart("external_id", externalId)
            .addFormDataPart("file", tcx.name, tcx.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        return execute(authed(token, "$API/uploads").post(body).build())
    }

    /** GET /uploads/{id} to check processing status. */
    suspend fun getUpload(token: String, uploadId: Long): JSONObject =
        execute(authed(token, "$API/uploads/$uploadId").get().build())

    /** PUT /activities/{id}: sport type, name, description, private note, etc. */
    suspend fun updateActivity(token: String, activityId: Long, fields: JSONObject): JSONObject {
        val body: RequestBody = fields.toString().toRequestBody("application/json".toMediaType())
        return execute(authed(token, "$API/activities/$activityId").put(body).build())
    }

    private fun authed(token: String, url: String) =
        Request.Builder().url(url).header("Authorization", "Bearer $token")

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request).execute()
        } catch (e: java.io.IOException) {
            throw StravaException("Network error: ${e.message}", retryable = true)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val retryable = it.code == 429 || it.code >= 500
                val detail = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                throw StravaException("Strava ${it.code}: ${detail.ifEmpty { text.take(200) }}", retryable)
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
}
