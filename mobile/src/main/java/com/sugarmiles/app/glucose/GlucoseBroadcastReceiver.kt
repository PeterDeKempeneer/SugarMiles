package com.sugarmiles.app.glucose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Receives glucose readings from xDrip+ (Inter-app settings → Broadcast locally,
 * Identify receiver = com.sugarmiles.app) and relays them to the watch.
 */
class GlucoseBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: "?"
        if (intent.getBooleanExtra(TEST_EXTRA, false)) {
            ReceiverLog.record(app, action, "Test broadcast received ✓ (receiver works)")
            return
        }
        val reading = parse(intent)
        if (reading == null) {
            val keys = intent.extras?.keySet()?.joinToString(", ") ?: "no extras"
            ReceiverLog.record(app, action, "Received but couldn't read a value. Extras: $keys")
            return
        }
        ReceiverLog.record(app, action, "${reading.mgdl} mg/dL from ${reading.source}")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (GlucoseStore.add(app, reading)) GlucoseStore.sendToWatch(app, reading)
            } catch (e: Exception) {
                Log.w("GlucoseReceiver", "Failed to handle reading", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val XDRIP_ACTION = "com.eveningoutpost.dexdrip.BgEstimate"
        const val TEST_EXTRA = "com.sugarmiles.app.TEST"

        fun parse(intent: Intent): GlucoseReading? {
            val extras = intent.extras ?: return null
            val now = System.currentTimeMillis()
            return when (intent.action) {
                XDRIP_ACTION -> {
                    // xDrip+ always broadcasts mg/dL, whatever unit it displays.
                    val bg = extras.getDouble("com.eveningoutpost.dexdrip.Extras.BgEstimate", 0.0)
                    if (bg <= 0) return null
                    GlucoseReading(
                        mgdl = bg.roundToInt(),
                        timeMs = extras.getLong("com.eveningoutpost.dexdrip.Extras.Time", now),
                        trend = arrowFromSlopeName(extras.getString("com.eveningoutpost.dexdrip.Extras.BgSlopeName")),
                        source = "xDrip+",
                    )
                }
                else -> null
            }
        }

        fun arrowFromSlopeName(name: String?): String = when (name) {
            "DoubleUp" -> "⇈"
            "SingleUp" -> "↑"
            "FortyFiveUp" -> "↗"
            "Flat" -> "→"
            "FortyFiveDown" -> "↘"
            "SingleDown" -> "↓"
            "DoubleDown" -> "⇊"
            else -> ""
        }
    }
}
