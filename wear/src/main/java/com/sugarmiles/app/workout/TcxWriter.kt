package com.sugarmiles.app.workout

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TreeMap

data class HrSample(val timeMs: Long, val bpm: Int)
data class LocSample(val timeMs: Long, val lat: Double, val lon: Double, val altitude: Double)

/**
 * Writes a Garmin TCX file, which Strava accepts on its upload endpoint. GPS points make
 * Strava draw the map; HR-only points are fine for swims and gym workouts.
 * The real Strava sport type is set by the phone after upload.
 */
object TcxWriter {

    private class Point(var lat: Double? = null, var lon: Double? = null, var alt: Double? = null, var hr: Int? = null)

    fun build(
        sport: Sport,
        startMs: Long,
        activeSeconds: Long,
        distanceM: Double,
        calories: Double,
        hr: List<HrSample>,
        locations: List<LocSample>,
    ): String {
        // Merge HR and GPS into one point per second.
        val points = TreeMap<Long, Point>()
        for (l in locations) {
            val p = points.getOrPut(l.timeMs / 1000) { Point() }
            p.lat = l.lat; p.lon = l.lon
            if (!l.altitude.isNaN()) p.alt = l.altitude
        }
        for (h in hr) {
            points.getOrPut(h.timeMs / 1000) { Point() }.hr = h.bpm
        }
        // No sensor data at all: add time-only start/end points so Strava still gets the duration.
        if (points.isEmpty()) {
            points[startMs / 1000] = Point()
            points[startMs / 1000 + activeSeconds] = Point()
        }

        val avgHr = if (hr.isNotEmpty()) hr.map { it.bpm }.average().toInt() else null
        val maxHr = hr.maxOfOrNull { it.bpm }

        // No whitespace before <?xml — Strava rejects TCX files that start with it.
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""").append('\n')
        sb.append("<Activities>\n")
        sb.append("""<Activity Sport="${sport.tcxSport}">""").append('\n')
        sb.append("<Id>").append(iso(startMs)).append("</Id>\n")
        sb.append("""<Lap StartTime="${iso(startMs)}">""").append('\n')
        sb.append("<TotalTimeSeconds>").append(activeSeconds).append("</TotalTimeSeconds>\n")
        sb.append("<DistanceMeters>").append(fmt(distanceM, 1)).append("</DistanceMeters>\n")
        sb.append("<Calories>").append(calories.toInt().coerceIn(0, 65535)).append("</Calories>\n")
        if (avgHr != null) sb.append("<AverageHeartRateBpm><Value>").append(avgHr).append("</Value></AverageHeartRateBpm>\n")
        if (maxHr != null) sb.append("<MaximumHeartRateBpm><Value>").append(maxHr).append("</Value></MaximumHeartRateBpm>\n")
        sb.append("<Intensity>Active</Intensity>\n")
        sb.append("<TriggerMethod>Manual</TriggerMethod>\n")
        sb.append("<Track>\n")
        for ((second, p) in points) {
            sb.append("<Trackpoint><Time>").append(iso(second * 1000)).append("</Time>")
            if (p.lat != null && p.lon != null) {
                sb.append("<Position><LatitudeDegrees>").append(fmt(p.lat!!, 7))
                    .append("</LatitudeDegrees><LongitudeDegrees>").append(fmt(p.lon!!, 7))
                    .append("</LongitudeDegrees></Position>")
            }
            p.alt?.let { sb.append("<AltitudeMeters>").append(fmt(it, 1)).append("</AltitudeMeters>") }
            p.hr?.let { sb.append("<HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>") }
            sb.append("</Trackpoint>\n")
        }
        sb.append("</Track>\n</Lap>\n</Activity>\n</Activities>\n</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    private fun iso(ms: Long): String = Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString()
    private fun fmt(v: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", v)
}
