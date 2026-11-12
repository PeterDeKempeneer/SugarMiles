package com.sugarmiles.app.nightscout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NightscoutException(message: String, val retryable: Boolean) : Exception(message)

/** Minimal Nightscout API v1 client: create a treatment, authenticated with an access token. */
object NightscoutClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    /** Checks that the site is reachable and the token is known. Never throws. */
    suspend fun verify(config: NsConfig): String = try {
        val body = execute(Request.Builder().url(url(config, "/api/v1/verifyauth")).get().build())
        val json = JSONObject(body)
        val msg = json.optJSONObject("message") ?: json
        val known = msg.optString("rolefound") == "FOUND" || msg.optString("message") == "OK" ||
            msg.optBoolean("canRead") || msg.optBoolean("canWrite")
        if (known) "Connected ✓ (token recognized)"
        else "Nightscout reached, but the token wasn't recognized."
    } catch (e: Exception) {
        "Couldn't connect: ${e.message}"
    }

    suspend fun create(config: NsConfig, treatment: JSONObject) {
        execute(Request.Builder().url(url(config, "/api/v1/treatments")).post(treatment.toString().toRequestBody(JSON)).build())
    }

    private fun url(config: NsConfig, path: String): HttpUrl {
        var base = config.url.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "https://$base"
        return (base + path).toHttpUrl().newBuilder()
            .addQueryParameter("token", config.token.trim())
            .build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request).execute()
        } catch (e: java.io.IOException) {
            throw NightscoutException("Network error: ${e.message}", retryable = true)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val retryable = it.code == 429 || it.code >= 500
                throw NightscoutException("Nightscout ${it.code}: ${text.take(200)}", retryable)
            }
            text
        }
    }
}
