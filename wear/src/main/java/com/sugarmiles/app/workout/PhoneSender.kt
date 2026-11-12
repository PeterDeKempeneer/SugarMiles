package com.sugarmiles.app.workout

import android.content.Context
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Data Layer path prefix for finished workouts. Keep in sync with the phone app. */
const val WORKOUT_PATH_PREFIX = "/workout/"

/**
 * Hands the finished workout to the Data Layer. If the phone is out of range, the item is
 * queued and delivered automatically once the watch reconnects; the phone deletes it after saving.
 */
object PhoneSender {
    suspend fun sendWorkout(
        context: Context,
        id: String,
        sport: Sport,
        startMs: Long,
        endMs: Long,
        activeSeconds: Long,
        distanceM: Double,
        calories: Double,
        avgHr: Int,
        tcx: ByteArray,
    ) {
        val request = PutDataMapRequest.create(WORKOUT_PATH_PREFIX + id).apply {
            dataMap.putString("id", id)
            dataMap.putString("sport", sport.name)
            dataMap.putLong("start", startMs)
            dataMap.putLong("end", endMs)
            dataMap.putLong("activeSec", activeSeconds)
            dataMap.putDouble("distance", distanceM)
            dataMap.putDouble("calories", calories)
            dataMap.putInt("avgHr", avgHr)
            dataMap.putAsset("tcx", Asset.createFromBytes(tcx))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}
