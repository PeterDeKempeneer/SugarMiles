package com.sugarmiles.app.nightscout

import android.content.Context
import com.sugarmiles.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NsConfig(val enabled: Boolean, val url: String, val token: String) {
    val isUsable: Boolean get() = enabled && url.isNotBlank() && token.isNotBlank()
}

object NightscoutSettings {
    private const val PREFS = "nightscout"
    private val DEFAULT_URL = BuildConfig.NIGHTSCOUT_URL
    private const val MAX_POSTED = 200

    private val _config = MutableStateFlow(NsConfig(false, DEFAULT_URL, ""))
    val config: StateFlow<NsConfig> = _config.asStateFlow()

    fun load(context: Context) {
        val p = prefs(context)
        _config.value = NsConfig(
            enabled = p.getBoolean("enabled", false),
            url = p.getString("url", DEFAULT_URL) ?: DEFAULT_URL,
            token = p.getString("token", "") ?: "",
        )
    }

    fun save(context: Context, config: NsConfig) {
        prefs(context).edit()
            .putBoolean("enabled", config.enabled)
            .putString("url", config.url)
            .putString("token", config.token)
            .apply()
        _config.value = config
    }

    /** Workouts already posted to Nightscout, so a retry never creates a second entry. */
    fun markPosted(context: Context, workoutId: String) {
        val p = prefs(context)
        val ids = (p.getString("posted", "") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()
        if (workoutId !in ids) ids += workoutId
        p.edit().putString("posted", ids.takeLast(MAX_POSTED).joinToString(",")).apply()
    }

    fun isPosted(context: Context, workoutId: String): Boolean =
        (prefs(context).getString("posted", "") ?: "").split(',').contains(workoutId)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
