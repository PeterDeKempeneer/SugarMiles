package com.sugarmiles.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import com.sugarmiles.app.glucose.GlucoseRepository
import com.sugarmiles.app.workout.ExerciseService
import com.sugarmiles.app.workout.Phase
import com.sugarmiles.app.workout.WorkoutRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Ambient mode keeps the workout screen visible (dimmed) when you lower your wrist,
    // instead of dropping back to the watch face.
    private val isAmbient = mutableStateOf(false)
    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) { isAmbient.value = true }
        override fun onExitAmbient() { isAmbient.value = false }
        override fun onUpdateAmbient() {}
    }
    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        requestPermissions()

        setContent {
            MaterialTheme {
                val workout by WorkoutRepository.state.collectAsStateWithLifecycle()
                val glucose by GlucoseRepository.latest.collectAsStateWithLifecycle()
                val ambient = isAmbient.value

                when (workout.phase) {
                    Phase.IDLE -> SportPickerScreen(
                        glucose = glucose,
                        onPick = { sport ->
                            // Starting without sensor permission can crash the foreground service.
                            if (hasSensorPermission()) ExerciseService.start(this, sport) else requestPermissions()
                        },
                    )
                    Phase.STARTING, Phase.ACTIVE, Phase.PAUSED, Phase.ENDING -> WorkoutScreen(
                        workout = workout,
                        glucose = glucose,
                        ambient = ambient,
                        onPause = { ExerciseService.pause(this) },
                        onResume = { ExerciseService.resume(this) },
                        onFinish = { save -> ExerciseService.finish(this, save) },
                    )
                    Phase.SAVED, Phase.DISCARDED, Phase.ERROR -> ResultScreen(
                        workout = workout,
                        onDone = { WorkoutRepository.reset() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { GlucoseRepository.refresh(this@MainActivity) }
    }

    private fun hasSensorPermission(): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.BODY_SENSORS) ||
            granted("android.permission.health.READ_HEART_RATE") ||
            granted(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 36) perms += "android.permission.health.READ_HEART_RATE"
        permissionLauncher.launch(perms.toTypedArray())
    }
}
