package com.sugarmiles.app.workout

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.sugarmiles.app.nightscout.NightscoutWorker
import java.util.concurrent.TimeUnit

/** Woken up by the system when the watch hands over a finished workout. */
class WorkoutListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val dataClient = Wearable.getDataClient(this)
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            val path = item.uri.path ?: continue
            try {
                when {
                    path.startsWith("/workout/") -> handleFinished(item)
                    else -> continue
                }
                // Handled, so the watch's copy can go.
                Tasks.await(dataClient.deleteDataItems(item.uri), 30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.e("WorkoutListener", "Failed to handle ${item.uri}", e)
            }
        }
    }

    private fun handleFinished(item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        val asset = map.getAsset("tcx") ?: return
        // This callback runs on a background thread, so blocking here is fine.
        val fd = Tasks.await(Wearable.getDataClient(this).getFdForAsset(asset), 60, TimeUnit.SECONDS)
        val tcx = fd.inputStream.use { it.readBytes() }

        val sport = Sport.from(map.getString("sport"))
        val start = map.getLong("start")
        val record = WorkoutRecord(
            id = map.getString("id") ?: item.uri.lastPathSegment ?: return,
            sport = sport,
            startMs = start,
            endMs = map.getLong("end"),
            activeSec = map.getLong("activeSec"),
            distanceM = map.getDouble("distance"),
            calories = map.getDouble("calories"),
            avgHr = map.getInt("avgHr"),
            name = WorkoutRecord.defaultName(sport, start),
        )
        if (WorkoutStore.receive(this, record, tcx)) {
            UploadWorker.enqueue(this, record.id)
            NightscoutWorker.enqueue(this, record.id)
        }
    }
}
