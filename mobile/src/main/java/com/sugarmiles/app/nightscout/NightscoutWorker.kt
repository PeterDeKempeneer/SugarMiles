package com.sugarmiles.app.nightscout

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sugarmiles.app.glucose.GlucoseStore
import com.sugarmiles.app.glucose.GlucoseSummary
import com.sugarmiles.app.workout.Sport
import com.sugarmiles.app.workout.WorkoutRecord
import com.sugarmiles.app.workout.WorkoutStore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Posts one Nightscout "Exercise" treatment for a saved workout: created at the workout's start
 * time, with its duration and a summary note. Needs only create permission (careportal role).
 * Retries when offline; never posts the same workout twice.
 */
class NightscoutWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NightscoutWorker"

        fun enqueue(context: Context, workoutId: String) {
            val request = OneTimeWorkRequestBuilder<NightscoutWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf("id" to workoutId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("ns-$workoutId", ExistingWorkPolicy.KEEP, request)
        }

        /** ISO 8601 in UTC, the format Nightscout stores. */
        fun createdAt(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(ms / 1000 * 1000))

        private fun emoji(sport: Sport) = when (sport) {
            Sport.WALK -> "🚶"
            Sport.RUN -> "🏃"
            Sport.RIDE -> "🚴"
            Sport.SWIM -> "🏊"
            Sport.WORKOUT -> "💪"
            Sport.WEIGHTS -> "🏋️"
        }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        NightscoutSettings.load(ctx)
        val config = NightscoutSettings.config.value
        if (!config.isUsable) return Result.success()

        val id = inputData.getString("id") ?: return Result.failure()
        if (NightscoutSettings.isPosted(ctx, id)) return Result.success()
        val rec = WorkoutStore.get(ctx, id) ?: return Result.failure()

        return try {
            val minutes = ((rec.endMs - rec.startMs) / 60_000.0).roundToInt().coerceAtLeast(1)
            NightscoutClient.create(config, treatment(createdAt(rec.startMs), minutes, endNote(ctx, rec)))
            NightscoutSettings.markPosted(ctx, id)
            Result.success()
        } catch (e: NightscoutException) {
            Log.w(TAG, "Nightscout post failed for $id", e)
            if (e.retryable && runAttemptCount < 8) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Nightscout error", e)
            Result.failure()
        }
    }

    private fun treatment(createdAt: String, minutes: Int, notes: String) = JSONObject()
        .put("eventType", "Exercise")
        .put("created_at", createdAt)
        .put("duration", minutes)
        .put("notes", notes)
        .put("enteredBy", "SugarMiles")

    /** e.g. "🏃 Run · 42:10 · 7.20 km · avg HR 148 · glucose 142 → 118 mg/dL" */
    private fun endNote(ctx: Context, rec: WorkoutRecord): String {
        val parts = mutableListOf("${emoji(rec.sport)} ${rec.sport.label}", formatDuration(rec.activeSec))
        if (rec.distanceM > 0) parts += String.format(Locale.US, "%.2f km", rec.distanceM / 1000)
        if (rec.avgHr > 0) parts += "avg HR ${rec.avgHr}"
        val readings = GlucoseStore.between(ctx, rec.startMs - 15 * 60_000, rec.endMs + 5 * 60_000)
        GlucoseSummary.startEnd(readings, rec.startMs, rec.endMs)?.let { (from, to) -> parts += "glucose $from → $to mg/dL" }
        return parts.joinToString(" · ")
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }
}
