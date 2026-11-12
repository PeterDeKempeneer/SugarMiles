package com.sugarmiles.app.glucose

import android.content.Context
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Data Layer path the phone writes the latest reading to. Keep in sync with the phone app. */
const val GLUCOSE_PATH = "/glucose/latest"

data class GlucoseReading(
    val mgdl: Int,
    val timeMs: Long,
    val trend: String,   // arrow character, e.g. "→"
    val source: String,  // e.g. "xDrip+"
)

/** Latest glucose reading received from the phone, shared by the UI and the workout service. */
object GlucoseRepository {
    private val _latest = MutableStateFlow<GlucoseReading?>(null)
    val latest: StateFlow<GlucoseReading?> = _latest.asStateFlow()

    fun update(reading: GlucoseReading) {
        val current = _latest.value
        if (current == null || reading.timeMs >= current.timeMs) _latest.value = reading
    }

    fun fromDataMap(map: DataMap): GlucoseReading? {
        if (!map.containsKey("mgdl")) return null
        return GlucoseReading(
            mgdl = map.getInt("mgdl"),
            timeMs = map.getLong("time"),
            trend = map.getString("trend") ?: "",
            source = map.getString("source") ?: "",
        )
    }

    /** Pull the last reading the phone synced (e.g. when the app starts). */
    suspend fun refresh(context: Context) {
        runCatching {
            val buffer = Wearable.getDataClient(context).dataItems.await()
            try {
                for (item in buffer) {
                    if (item.uri.path == GLUCOSE_PATH) {
                        fromDataMap(DataMapItem.fromDataItem(item).dataMap)?.let(::update)
                    }
                }
            } finally {
                buffer.release()
            }
        }
    }
}
