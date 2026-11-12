package com.sugarmiles.app.glucose

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.abs

/** Data Layer path read by the watch. Keep in sync with the wear app. */
const val GLUCOSE_PATH = "/glucose/latest"

data class GlucoseReading(
    val mgdl: Int,
    val timeMs: Long,
    val trend: String,
    val source: String,
)

/**
 * Keeps the last few days of readings in a small CSV file, so the glucose summary for a
 * workout can be computed afterwards even if the watch was disconnected for a while.
 */
object GlucoseStore {
    private const val KEEP_MS = 3L * 24 * 60 * 60 * 1000
    private const val DUPLICATE_WINDOW_MS = 30_000L

    private val lock = Any()
    private var cache: MutableList<GlucoseReading>? = null

    private val _latest = MutableStateFlow<GlucoseReading?>(null)
    val latest: StateFlow<GlucoseReading?> = _latest.asStateFlow()

    private fun file(context: Context) = File(context.filesDir, "glucose.csv")

    private fun all(context: Context): MutableList<GlucoseReading> {
        cache?.let { return it }
        val list = mutableListOf<GlucoseReading>()
        val f = file(context)
        if (f.exists()) {
            f.forEachLine { line ->
                val parts = line.split(",", limit = 4)
                if (parts.size == 4) {
                    val t = parts[0].toLongOrNull()
                    val v = parts[1].toIntOrNull()
                    if (t != null && v != null) list += GlucoseReading(v, t, parts[2], parts[3])
                }
            }
        }
        list.sortBy { it.timeMs }
        cache = list
        _latest.value = list.lastOrNull()
        return list
    }

    fun load(context: Context) { synchronized(lock) { all(context) } }

    /** Returns false if this reading was already stored (xDrip+ can resend it). */
    fun add(context: Context, reading: GlucoseReading): Boolean = synchronized(lock) {
        val list = all(context)
        if (list.any { abs(it.timeMs - reading.timeMs) < DUPLICATE_WINDOW_MS }) return false

        list += reading
        list.sortBy { it.timeMs }
        val cutoff = System.currentTimeMillis() - KEEP_MS
        val f = file(context)
        if (list.first().timeMs < cutoff) {
            list.removeAll { it.timeMs < cutoff }
            f.writeText(list.joinToString("") { it.toCsv() })
        } else {
            f.appendText(reading.toCsv())
        }
        _latest.value = list.last()
        true
    }

    fun between(context: Context, fromMs: Long, toMs: Long): List<GlucoseReading> = synchronized(lock) {
        all(context).filter { it.timeMs in fromMs..toMs }
    }

    private fun GlucoseReading.toCsv() = "$timeMs,$mgdl,$trend,${source.replace(",", " ")}\n"

    /** Pushes the reading to the watch. Delivered right away when connected, otherwise on reconnect. */
    suspend fun sendToWatch(context: Context, reading: GlucoseReading) {
        runCatching {
            val request = PutDataMapRequest.create(GLUCOSE_PATH).apply {
                dataMap.putInt("mgdl", reading.mgdl)
                dataMap.putLong("time", reading.timeMs)
                dataMap.putString("trend", reading.trend)
                dataMap.putString("source", reading.source)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }
    }
}
