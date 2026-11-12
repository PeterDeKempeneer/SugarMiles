package com.sugarmiles.app.workout

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** Must match the wear app's Sport enum names. */
enum class Sport(val label: String, val stravaType: String) {
    WALK("Walk", "Walk"),
    RUN("Run", "Run"),
    RIDE("Ride", "Ride"),
    SWIM("Swim", "Swim"),
    WORKOUT("Workout", "Workout"),
    WEIGHTS("Weight Training", "WeightTraining");

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: WORKOUT
    }
}

enum class UploadStatus { PENDING, UPLOADING, UPLOADED, FAILED }

data class WorkoutRecord(
    val id: String,
    val sport: Sport,
    val startMs: Long,
    val endMs: Long,
    val activeSec: Long,
    val distanceM: Double,
    val calories: Double,
    val avgHr: Int,
    val name: String,
    val isTest: Boolean = false,
    val status: UploadStatus = UploadStatus.PENDING,
    val uploadId: Long? = null,
    val activityId: Long? = null,
    val error: String? = null,
    val glucoseSummary: String? = null,
    val glucoseWhere: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("sport", sport.name).put("startMs", startMs).put("endMs", endMs)
        .put("activeSec", activeSec).put("distanceM", distanceM).put("calories", calories)
        .put("avgHr", avgHr).put("name", name).put("isTest", isTest).put("status", status.name)
        .put("uploadId", uploadId ?: JSONObject.NULL)
        .put("activityId", activityId ?: JSONObject.NULL)
        .put("error", error ?: JSONObject.NULL)
        .put("glucoseSummary", glucoseSummary ?: JSONObject.NULL)
        .put("glucoseWhere", glucoseWhere ?: JSONObject.NULL)

    companion object {
        fun fromJson(j: JSONObject) = WorkoutRecord(
            id = j.getString("id"),
            sport = Sport.from(j.optString("sport")),
            startMs = j.getLong("startMs"),
            endMs = j.getLong("endMs"),
            activeSec = j.optLong("activeSec"),
            distanceM = j.optDouble("distanceM", 0.0),
            calories = j.optDouble("calories", 0.0),
            avgHr = j.optInt("avgHr"),
            name = j.optString("name"),
            isTest = j.optBoolean("isTest"),
            status = runCatching { UploadStatus.valueOf(j.getString("status")) }.getOrDefault(UploadStatus.PENDING),
            uploadId = if (j.isNull("uploadId")) null else j.getLong("uploadId"),
            activityId = if (j.isNull("activityId")) null else j.getLong("activityId"),
            error = if (j.isNull("error")) null else j.getString("error"),
            glucoseSummary = if (j.isNull("glucoseSummary")) null else j.getString("glucoseSummary"),
            glucoseWhere = if (j.isNull("glucoseWhere")) null else j.getString("glucoseWhere"),
        )

        /** Strava-style default name, e.g. "Morning Run". */
        fun defaultName(sport: Sport, startMs: Long): String {
            val hour = Calendar.getInstance().apply { timeInMillis = startMs }.get(Calendar.HOUR_OF_DAY)
            val part = when (hour) {
                in 4..10 -> "Morning"
                in 11..13 -> "Lunch"
                in 14..17 -> "Afternoon"
                in 18..21 -> "Evening"
                else -> "Night"
            }
            return "$part ${sport.label}"
        }
    }
}

/** Workouts received from the watch, stored as <id>.json + <id>.tcx in app storage. */
object WorkoutStore {
    private val lock = Any()
    private val _all = MutableStateFlow<List<WorkoutRecord>>(emptyList())
    val all: StateFlow<List<WorkoutRecord>> = _all.asStateFlow()

    private fun dir(context: Context) = File(context.filesDir, "workouts").apply { mkdirs() }
    fun tcxFile(context: Context, id: String) = File(dir(context), "$id.tcx")
    private fun jsonFile(context: Context, id: String) = File(dir(context), "$id.json")

    fun load(context: Context) = synchronized(lock) {
        _all.value = dir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { runCatching { WorkoutRecord.fromJson(JSONObject(it.readText())) }.getOrNull() }
            .sortedByDescending { it.startMs }
    }

    fun get(context: Context, id: String): WorkoutRecord? = synchronized(lock) {
        val f = jsonFile(context, id)
        if (f.exists()) runCatching { WorkoutRecord.fromJson(JSONObject(f.readText())) }.getOrNull() else null
    }

    fun save(context: Context, record: WorkoutRecord) {
        synchronized(lock) { jsonFile(context, record.id).writeText(record.toJson().toString()) }
        load(context)
    }

    /** Stores a workout from the watch. Returns false if we already had it (duplicate delivery). */
    fun receive(context: Context, record: WorkoutRecord, tcx: ByteArray): Boolean {
        synchronized(lock) {
            if (jsonFile(context, record.id).exists()) return false
            tcxFile(context, record.id).writeBytes(tcx)
        }
        save(context, record)
        return true
    }
}
