package com.sugarmiles.app.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sugarmiles.app.AppSettings
import com.sugarmiles.app.R
import com.sugarmiles.app.SummaryTarget
import com.sugarmiles.app.glucose.GlucoseStore
import com.sugarmiles.app.glucose.GlucoseSummary
import com.sugarmiles.app.strava.StravaAuth
import com.sugarmiles.app.strava.StravaClient
import com.sugarmiles.app.strava.StravaException
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Uploads one workout to Strava, then sets sport type, name and the glucose summary.
 * Runs when there's network; retries with backoff on network/server errors.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UploadWorker"
        private const val CHANNEL_ID = "uploads"

        fun enqueue(context: Context, workoutId: String, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf("id" to workoutId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "upload-$workoutId",
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private val DUPLICATE_REGEX = Regex("""activities/(\d+)|activity (\d+)""")
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getString("id") ?: return Result.failure()
        AppSettings.load(ctx) // the worker may run in a fresh process
        var rec = WorkoutStore.get(ctx, id) ?: return Result.failure()
        if (rec.status == UploadStatus.UPLOADED) return Result.success()

        if (!StravaAuth.isConnected(ctx)) {
            WorkoutStore.save(ctx, rec.copy(status = UploadStatus.FAILED, error = "Not connected to Strava. Connect, then tap Retry."))
            return Result.failure()
        }

        return try {
            rec = rec.copy(status = UploadStatus.UPLOADING, error = null)
            WorkoutStore.save(ctx, rec)
            val token = StravaAuth.validAccessToken(ctx)

            // 1. Upload the file (or resume a previous upload) and wait until Strava has processed it.
            var activityId = rec.activityId
            if (activityId == null) {
                var uploadId = rec.uploadId
                if (uploadId == null) {
                    val upload = StravaClient.upload(token, WorkoutStore.tcxFile(ctx, id), rec.name, id)
                    uploadId = upload.getLong("id")
                    rec = rec.copy(uploadId = uploadId)
                    WorkoutStore.save(ctx, rec)
                }
                activityId = waitForActivity(token, uploadId)
                rec = rec.copy(activityId = activityId)
                WorkoutStore.save(ctx, rec)
            }

            // 2. Sport type, name and glucose summary.
            val readings = GlucoseStore.between(ctx, rec.startMs - 15 * 60_000, rec.endMs + 5 * 60_000)
            val summary = GlucoseSummary.build(readings, rec.startMs, rec.endMs)
            val target = AppSettings.summaryTarget.value
            val usePrivateNote = target == SummaryTarget.PRIVATE_NOTE_WITH_FALLBACK || target == SummaryTarget.PRIVATE_NOTE_ONLY

            val fields = JSONObject()
                .put("sport_type", rec.sport.stravaType)
                .put("name", rec.name)
            if (rec.isTest) fields.put("hide_from_home", true)
            if (summary != null) {
                if (usePrivateNote) fields.put("private_note", summary)
                if (target == SummaryTarget.DESCRIPTION) fields.put("description", summary)
            }
            val updated = StravaClient.updateActivity(token, activityId, fields)

            var where: String? = when {
                summary == null -> null
                target == SummaryTarget.DESCRIPTION -> "description"
                usePrivateNote -> "private note"
                else -> null
            }

            // 3. Did Strava keep the private note? (Writing it isn't officially documented.)
            if (summary != null && usePrivateNote) {
                val stored = updated.optString("private_note", "")
                val works = stored.trim() == summary.trim()
                AppSettings.setPrivateNoteWorks(ctx, works)
                if (!works) {
                    where = if (target == SummaryTarget.PRIVATE_NOTE_WITH_FALLBACK) {
                        StravaClient.updateActivity(token, activityId, JSONObject().put("description", summary))
                        "description (private note not supported)"
                    } else {
                        "nowhere (private note not supported)"
                    }
                }
            }

            rec = rec.copy(status = UploadStatus.UPLOADED, error = null, glucoseSummary = summary, glucoseWhere = where)
            WorkoutStore.save(ctx, rec)
            notifyUploaded(ctx, rec)
            Result.success()
        } catch (e: StravaException) {
            Log.w(TAG, "Upload failed", e)
            if (e.retryable && runAttemptCount < 8) {
                WorkoutStore.save(ctx, rec.copy(status = UploadStatus.PENDING, error = "${e.message} (will retry)"))
                Result.retry()
            } else {
                WorkoutStore.save(ctx, rec.copy(status = UploadStatus.FAILED, error = e.message))
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected upload error", e)
            WorkoutStore.save(ctx, rec.copy(status = UploadStatus.FAILED, error = e.message ?: e.javaClass.simpleName))
            Result.failure()
        }
    }

    /** Polls the upload until Strava created the activity. Handles "duplicate of activity X". */
    private suspend fun waitForActivity(token: String, uploadId: Long): Long {
        repeat(30) {
            val upload = StravaClient.getUpload(token, uploadId)
            if (!upload.isNull("activity_id")) return upload.getLong("activity_id")
            val error = if (upload.isNull("error")) "" else upload.getString("error")
            if (error.isNotBlank()) {
                DUPLICATE_REGEX.find(error)?.let { m ->
                    val dup = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toLongOrNull()
                    if (dup != null) return dup
                }
                throw StravaException("Strava rejected the file: $error", retryable = false)
            }
            delay(2000)
        }
        throw StravaException("Strava is still processing the upload", retryable = true)
    }

    private fun notifyUploaded(context: Context, rec: WorkoutRecord) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Strava uploads", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, rec.id.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.strava.com/activities/${rec.activityId}")),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("Uploaded: ${rec.name}")
            .setContentText(rec.glucoseSummary?.lineSequence()?.firstOrNull() ?: "Tap to open in Strava")
            .setStyle(NotificationCompat.BigTextStyle().bigText(rec.glucoseSummary ?: "Tap to open in Strava"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) {
            @android.annotation.SuppressLint("MissingPermission")
            runCatching { nm.notify(rec.id.hashCode(), notification) }
        }
    }
}
