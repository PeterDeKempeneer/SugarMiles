package com.sugarmiles.app.strava

import android.content.Context
import android.net.Uri
import com.sugarmiles.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Strava OAuth for a personal API app.
 * In https://www.strava.com/settings/api set "Authorization Callback Domain" to: localhost
 */
object StravaAuth {
    const val REDIRECT_URI = "sugarmiles://localhost/exchange_token"
    private const val PREFS = "strava"
    private val refreshLock = Mutex()

    private val _athleteName = MutableStateFlow<String?>(null)
    /** Non-null when connected. */
    val athleteName: StateFlow<String?> = _athleteName.asStateFlow()

    val isConfigured: Boolean get() = BuildConfig.STRAVA_CLIENT_ID.isNotBlank() && BuildConfig.STRAVA_CLIENT_SECRET.isNotBlank()

    fun load(context: Context) {
        val p = prefs(context)
        _athleteName.value = if (p.getString("refresh", null) != null) p.getString("athlete", "Strava athlete") else null
    }

    fun isConnected(context: Context) = prefs(context).getString("refresh", null) != null

    fun authorizeUri(): Uri = Uri.parse("https://www.strava.com/oauth/mobile/authorize").buildUpon()
        .appendQueryParameter("client_id", BuildConfig.STRAVA_CLIENT_ID)
        .appendQueryParameter("redirect_uri", REDIRECT_URI)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("approval_prompt", "auto")
        .appendQueryParameter("scope", "read,activity:read_all,activity:write")
        .build()

    suspend fun exchangeCode(context: Context, code: String) {
        val json = StravaClient.postForm(
            StravaClient.OAUTH_TOKEN_URL,
            mapOf(
                "client_id" to BuildConfig.STRAVA_CLIENT_ID,
                "client_secret" to BuildConfig.STRAVA_CLIENT_SECRET,
                "code" to code,
                "grant_type" to "authorization_code",
            ),
        )
        saveTokens(context, json)
    }

    /** Returns a valid access token, refreshing it if it expires within 5 minutes. */
    suspend fun validAccessToken(context: Context): String = refreshLock.withLock {
        val p = prefs(context)
        val refresh = p.getString("refresh", null) ?: throw StravaException("Not connected to Strava", retryable = false)
        val access = p.getString("access", null)
        val expiresAt = p.getLong("expiresAt", 0)
        if (access != null && expiresAt - 300 > System.currentTimeMillis() / 1000) return@withLock access

        val json = StravaClient.postForm(
            StravaClient.OAUTH_TOKEN_URL,
            mapOf(
                "client_id" to BuildConfig.STRAVA_CLIENT_ID,
                "client_secret" to BuildConfig.STRAVA_CLIENT_SECRET,
                "refresh_token" to refresh,
                "grant_type" to "refresh_token",
            ),
        )
        saveTokens(context, json)
        json.getString("access_token")
    }

    fun disconnect(context: Context) {
        prefs(context).edit().clear().apply()
        _athleteName.value = null
    }

    private fun saveTokens(context: Context, json: JSONObject) {
        val edit = prefs(context).edit()
            .putString("access", json.getString("access_token"))
            .putString("refresh", json.getString("refresh_token"))
            .putLong("expiresAt", json.getLong("expires_at"))
        json.optJSONObject("athlete")?.let { a ->
            val name = listOf(a.optString("firstname"), a.optString("lastname")).filter { it.isNotBlank() }.joinToString(" ")
            if (name.isNotBlank()) edit.putString("athlete", name)
        }
        edit.apply()
        _athleteName.value = prefs(context).getString("athlete", null)
            ?: json.optJSONObject("athlete")?.optString("firstname")
            ?: "Strava athlete"
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
