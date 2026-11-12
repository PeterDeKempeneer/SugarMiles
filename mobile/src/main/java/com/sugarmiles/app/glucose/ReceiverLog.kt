package com.sugarmiles.app.glucose

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers the last broadcast the receiver saw, so setup problems are visible in the app. */
object ReceiverLog {
    data class Entry(val timeMs: Long, val action: String, val detail: String)

    private const val PREFS = "receiver_log"
    private val _last = MutableStateFlow<Entry?>(null)
    val last: StateFlow<Entry?> = _last.asStateFlow()

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = p.getLong("time", 0)
        _last.value = if (t > 0) Entry(t, p.getString("action", "") ?: "", p.getString("detail", "") ?: "") else null
    }

    fun record(context: Context, action: String, detail: String) {
        val entry = Entry(System.currentTimeMillis(), action, detail)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("time", entry.timeMs).putString("action", action).putString("detail", detail).apply()
        _last.value = entry
    }
}
