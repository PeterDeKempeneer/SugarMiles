package com.sugarmiles.app.glucose

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Woken up by the system whenever the phone pushes a new glucose reading. */
class GlucoseListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == GLUCOSE_PATH) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                GlucoseRepository.fromDataMap(map)?.let(GlucoseRepository::update)
            }
        }
    }
}
