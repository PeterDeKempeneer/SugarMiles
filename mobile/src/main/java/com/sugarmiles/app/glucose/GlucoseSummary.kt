package com.sugarmiles.app.glucose

import kotlin.math.abs
import kotlin.math.roundToInt

object GlucoseSummary {
    private const val LOW = 70
    private const val HIGH = 180

    /**
     * Example:
     *   🩸 Glucose 142 → 118 mg/dL (-24)
     *   Min 104 · Max 151 · Avg 127
     *   In range (70-180): 100%
     */
    fun build(readings: List<GlucoseReading>, startMs: Long, endMs: Long): String? {
        val window = readings.filter { it.timeMs in (startMs - 10 * 60_000)..(endMs + 3 * 60_000) }
        if (window.isEmpty()) return null

        val atStart = window.minBy { abs(it.timeMs - startMs) }
        val atEnd = window.minBy { abs(it.timeMs - endMs) }
        val during = window.filter { it.timeMs in startMs..endMs }.ifEmpty { window }

        val min = during.minOf { it.mgdl }
        val max = during.maxOf { it.mgdl }
        val avg = during.map { it.mgdl }.average().roundToInt()
        val inRange = during.count { it.mgdl in LOW..HIGH } * 100 / during.size
        val delta = atEnd.mgdl - atStart.mgdl
        val deltaText = if (delta > 0) "+$delta" else delta.toString()

        return "🩸 Glucose ${atStart.mgdl} → ${atEnd.mgdl} mg/dL ($deltaText)\n" +
            "Min $min · Max $max · Avg $avg\n" +
            "In range ($LOW-$HIGH): $inRange%"
    }
    /** Glucose closest to the start and end of the workout, or null if there's no data. */
    fun startEnd(readings: List<GlucoseReading>, startMs: Long, endMs: Long): Pair<Int, Int>? {
        val window = readings.filter { it.timeMs in (startMs - 10 * 60_000)..(endMs + 3 * 60_000) }
        if (window.isEmpty()) return null
        val atStart = window.minBy { abs(it.timeMs - startMs) }
        val atEnd = window.minBy { abs(it.timeMs - endMs) }
        return atStart.mgdl to atEnd.mgdl
    }
}
