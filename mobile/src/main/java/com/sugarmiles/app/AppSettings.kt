package com.sugarmiles.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SummaryTarget(val label: String, val explanation: String) {
    PRIVATE_NOTE_WITH_FALLBACK("Private note (fallback: description)", "Only you see it. If Strava ignores the private note, it goes into the description instead."),
    PRIVATE_NOTE_ONLY("Private note only", "Only you see it. Nothing is added if Strava ignores it."),
    DESCRIPTION("Description", "Visible to everyone who can see the activity."),
    OFF("Don't add to Strava", "Glucose summary is only kept in this app."),
}

object AppSettings {
    private const val PREFS = "settings"

    private val _summaryTarget = MutableStateFlow(SummaryTarget.PRIVATE_NOTE_WITH_FALLBACK)
    val summaryTarget: StateFlow<SummaryTarget> = _summaryTarget.asStateFlow()

    /** null = not tested yet, true = Strava stored the private note, false = it was ignored. */
    private val _privateNoteWorks = MutableStateFlow<Boolean?>(null)
    val privateNoteWorks: StateFlow<Boolean?> = _privateNoteWorks.asStateFlow()

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _summaryTarget.value = p.getString("summaryTarget", null)
            ?.let { runCatching { SummaryTarget.valueOf(it) }.getOrNull() }
            ?: SummaryTarget.PRIVATE_NOTE_WITH_FALLBACK
        _privateNoteWorks.value = if (p.contains("privateNoteWorks")) p.getBoolean("privateNoteWorks", false) else null
    }

    fun setSummaryTarget(context: Context, target: SummaryTarget) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("summaryTarget", target.name).apply()
        _summaryTarget.value = target
    }

    fun setPrivateNoteWorks(context: Context, works: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("privateNoteWorks", works).apply()
        _privateNoteWorks.value = works
    }
}
