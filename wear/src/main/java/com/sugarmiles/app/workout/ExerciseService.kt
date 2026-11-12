package com.sugarmiles.app.workout

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.sugarmiles.app.R
import com.sugarmiles.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Foreground service that owns the Health Services exercise. Keeps recording with the screen
 * off and while the app is in the background, and sends the result to the phone at the end.
 */
class ExerciseService : Service() {

    companion object {
        private const val TAG = "ExerciseService"
        private const val ACTION_START = "start"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_FINISH = "finish"
        private const val EXTRA_SPORT = "sport"
        private const val EXTRA_SAVE = "save"
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context, sport: Sport) = send(context, ACTION_START) { putExtra(EXTRA_SPORT, sport.name) }
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun finish(context: Context, save: Boolean) = send(context, ACTION_FINISH) { putExtra(EXTRA_SAVE, save) }

        private fun send(context: Context, action: String, extras: Intent.() -> Unit = {}) {
            val intent = Intent(context, ExerciseService::class.java).setAction(action).apply(extras)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }

    private var sport: Sport = Sport.WORKOUT
    private var workoutId = ""
    private var startMs = 0L
    private var saveOnEnd = true
    private var finalized = false
    private var started = false

    private val hrSamples = ArrayList<HrSample>()
    private val locSamples = ArrayList<LocSample>()
    private var totalDistance = 0.0
    private var totalCalories = 0.0
    private var activeMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!started) {
                    started = true
                    sport = intent.getStringExtra(EXTRA_SPORT)?.let { runCatching { Sport.valueOf(it) }.getOrNull() } ?: Sport.WORKOUT
                    if (goForeground()) scope.launch { startExercise() }
                }
            }
            ACTION_PAUSE -> scope.launch { runCatching { exerciseClient.pauseExerciseAsync().await() } }
            ACTION_RESUME -> scope.launch { runCatching { exerciseClient.resumeExerciseAsync().await() } }
            ACTION_FINISH -> {
                saveOnEnd = intent.getBooleanExtra(EXTRA_SAVE, true)
                WorkoutRepository.update { it.copy(phase = Phase.ENDING) }
                scope.launch {
                    runCatching { exerciseClient.endExerciseAsync().await() }
                        .onFailure { Log.w(TAG, "endExercise failed", it); finalizeWorkout() }
                }
            }
            null -> stopSelf() // restarted by the system without a workout; nothing to do
        }
        return START_NOT_STICKY
    }

    private suspend fun startExercise() {
        WorkoutRepository.set(WorkoutUi(phase = Phase.STARTING, sport = sport))
        try {
            val capabilities = exerciseClient.getCapabilitiesAsync().await()
            val type = if (sport.exerciseType in capabilities.supportedExerciseTypes) sport.exerciseType else ExerciseType.WORKOUT
            val supported = capabilities.getExerciseTypeCapabilities(type).supportedDataTypes

            val wanted = setOf<DataType<*, *>>(
                DataType.HEART_RATE_BPM,
                DataType.CALORIES_TOTAL,
                DataType.DISTANCE_TOTAL,
                DataType.SPEED,
                DataType.LOCATION,
            )
            val useGps = sport.usesGps && DataType.LOCATION in supported && hasLocationPermission()
            val dataTypes = wanted.filter { it in supported && (useGps || it != DataType.LOCATION) }.toSet()

            val builder = ExerciseConfig.builder(type)
                .setDataTypes(dataTypes)
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(useGps)
            if (type == ExerciseType.SWIMMING_POOL) builder.setSwimmingPoolLengthMeters(25f)

            exerciseClient.setUpdateCallback(callback)
            exerciseClient.startExerciseAsync(builder.build()).await()

            if (finalized) {
                // Finish was tapped while still starting: don't leave an orphaned exercise running.
                runCatching { exerciseClient.endExerciseAsync().await() }
                return
            }
            workoutId = UUID.randomUUID().toString()
            startMs = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start exercise", e)
            WorkoutRepository.set(WorkoutUi(phase = Phase.ERROR, sport = sport, message = "Couldn't start: ${e.message}"))
            shutdown()
        }
    }

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) = handleUpdate(update)
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "Callback registration failed", throwable)
        }
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            if (availability is LocationAvailability) {
                val fix = availability == LocationAvailability.ACQUIRED_TETHERED ||
                    availability == LocationAvailability.ACQUIRED_UNTETHERED
                WorkoutRepository.update { it.copy(gpsFix = fix) }
            }
        }
    }

    private fun handleUpdate(update: ExerciseUpdate) {
        if (finalized) return
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        val metrics = update.latestMetrics

        var latestHr: Int? = null
        for (point in metrics.getData(DataType.HEART_RATE_BPM)) {
            val bpm = point.value.roundToInt()
            if (bpm > 0) {
                hrSamples.add(HrSample(point.getTimeInstant(bootInstant).toEpochMilli(), bpm))
                latestHr = bpm
            }
        }
        for (point in metrics.getData(DataType.LOCATION)) {
            val loc = point.value
            locSamples.add(LocSample(point.getTimeInstant(bootInstant).toEpochMilli(), loc.latitude, loc.longitude, loc.altitude))
        }
        metrics.getData(DataType.DISTANCE_TOTAL)?.let { totalDistance = it.total }
        metrics.getData(DataType.CALORIES_TOTAL)?.let { totalCalories = it.total }
        val speed = metrics.getData(DataType.SPEED).lastOrNull()?.value

        val checkpoint = update.activeDurationCheckpoint
        val state = update.exerciseStateInfo.state
        if (checkpoint != null) activeMs = checkpoint.activeDuration.toMillis()

        WorkoutRepository.update { ui ->
            ui.copy(
                phase = when {
                    state.isEnded -> Phase.ENDING
                    ui.phase == Phase.ENDING -> Phase.ENDING
                    state.isPaused -> Phase.PAUSED
                    else -> Phase.ACTIVE
                },
                activeMsAtCheckpoint = checkpoint?.activeDuration?.toMillis() ?: ui.activeMsAtCheckpoint,
                checkpointMs = checkpoint?.time?.toEpochMilli() ?: ui.checkpointMs,
                heartRate = latestHr ?: ui.heartRate,
                distanceM = if (sport.showsDistance) totalDistance else null,
                calories = totalCalories,
                speedMps = speed ?: ui.speedMps,
            )
        }

        if (state.isEnded) scope.launch { finalizeWorkout() }
    }

    private suspend fun finalizeWorkout() {
        if (finalized) return
        finalized = true
        runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }

        val endMs = System.currentTimeMillis()
        // Save if anything was recorded, or if it lasted at least a minute (e.g. heart rate unavailable).
        val hasData = hrSamples.isNotEmpty() || locSamples.isNotEmpty() || activeMs >= 60_000
        if (saveOnEnd && hasData && startMs > 0) {
            try {
                val activeSeconds = (activeMs / 1000).coerceAtLeast(1)
                val tcx = TcxWriter.build(sport, startMs, activeSeconds, totalDistance, totalCalories, hrSamples, locSamples)
                PhoneSender.sendWorkout(
                    context = this,
                    id = workoutId,
                    sport = sport,
                    startMs = startMs,
                    endMs = endMs,
                    activeSeconds = activeSeconds,
                    distanceM = totalDistance,
                    calories = totalCalories,
                    avgHr = if (hrSamples.isEmpty()) 0 else hrSamples.map { it.bpm }.average().toInt(),
                    tcx = tcx.toByteArray(Charsets.UTF_8),
                )
                WorkoutRepository.update { it.copy(phase = Phase.SAVED, message = "Sent to phone for Strava upload") }
            } catch (e: Exception) {
                Log.e(TAG, "Saving workout failed", e)
                WorkoutRepository.update { it.copy(phase = Phase.ERROR, message = "Saving failed: ${e.message}") }
            }
        } else {
            WorkoutRepository.update {
                it.copy(phase = Phase.DISCARDED, message = if (saveOnEnd) "Shorter than 1 minute with no data, not saved" else "Workout discarded")
            }
        }
        shutdown()
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- Foreground notification + "ongoing activity" chip on the watch face -----------------

    /** Returns false if the service couldn't go to the foreground (usually missing permissions). */
    private fun goForeground(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Workout", NotificationManager.IMPORTANCE_DEFAULT))

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setContentTitle("${sport.label} in progress")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        OngoingActivity.Builder(this, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_stat_drop)
            .setTouchIntent(openApp)
            .setStatus(Status.Builder().addTemplate(sport.label).build())
            .build()
            .apply(this)

        val gps = sport.usesGps && hasLocationPermission()
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or (if (gps) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } else {
            if (gps) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), type)
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            WorkoutRepository.set(WorkoutUi(phase = Phase.ERROR, sport = sport, message = "Missing permissions. Allow sensors + location and try again."))
            stopSelf()
            false
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
