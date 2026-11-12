package com.sugarmiles.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.sugarmiles.app.glucose.GlucoseReading
import com.sugarmiles.app.workout.Phase
import com.sugarmiles.app.workout.Sport
import com.sugarmiles.app.workout.WorkoutUi
import kotlinx.coroutines.delay
import java.util.Locale

private const val STALE_AFTER_MS = 12 * 60_000L

private val Green = Color(0xFF69F0AE)
private val Orange = Color(0xFFFFAB40)
private val Red = Color(0xFFFF5252)
private val Grey = Color(0xFF9E9E9E)
private val StravaOrange = Color(0xFFFC5200)

@Composable
private fun rememberNow(intervalMs: Long = 1000): Long {
    val now by produceState(System.currentTimeMillis(), intervalMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
    return now
}

fun glucoseColor(mgdl: Int, stale: Boolean): Color = when {
    stale -> Grey
    mgdl < 70 -> Red
    mgdl <= 180 -> Green
    mgdl <= 250 -> Orange
    else -> Red
}

private fun ageText(reading: GlucoseReading, now: Long): String {
    val min = ((now - reading.timeMs) / 60_000).coerceAtLeast(0)
    return if (min < 1) "now" else "${min}m"
}

// ---------------------------------------------------------------------------------------------

@Composable
fun SportPickerScreen(glucose: GlucoseReading?, onPick: (Sport) -> Unit) {
    val listState = rememberScalingLazyListState()
    val now = rememberNow(10_000)
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                if (glucose == null) {
                    Text("Waiting for glucose…", color = Grey, fontSize = 14.sp, textAlign = TextAlign.Center)
                } else {
                    val stale = now - glucose.timeMs > STALE_AFTER_MS
                    Text(
                        text = "${glucose.mgdl} ${glucose.trend}  ·  ${ageText(glucose, now)}",
                        color = glucoseColor(glucose.mgdl, stale),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            items(Sport.entries.size) { index ->
                val sport = Sport.entries[index]
                Chip(
                    onClick = { onPick(sport) },
                    label = { Text("${sport.emoji}  ${sport.label}") },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = StravaOrange),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun WorkoutScreen(
    workout: WorkoutUi,
    glucose: GlucoseReading?,
    ambient: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: (save: Boolean) -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    val now = rememberNow(if (ambient) 30_000 else 1000)
    val sport = workout.sport ?: Sport.WORKOUT
    val textColor = if (ambient) Grey else Color.White

    if (confirmStop && !ambient) {
        FinishDialog(
            onSave = { confirmStop = false; onFinish(true) },
            onDiscard = { confirmStop = false; onFinish(false) },
            onResume = { confirmStop = false; onResume() },
        )
        return
    }

    Scaffold(
        timeText = { if (!ambient) TimeText() },
        modifier = Modifier.background(Color.Black),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 28.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Glucose: the big number.
            if (glucose != null) {
                val stale = now - glucose.timeMs > STALE_AFTER_MS
                val color = if (ambient) Color.White else glucoseColor(glucose.mgdl, stale)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(glucose.mgdl.toString(), color = color, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(glucose.trend, color = color, fontSize = 26.sp)
                        Text(ageText(glucose, now), color = Grey, fontSize = 12.sp)
                    }
                }
            } else {
                Text("--- mg/dL", color = Grey, fontSize = 30.sp)
            }

            Text(
                text = formatDuration(workout.activeMs(now)),
                color = textColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("♥ ${workout.heartRate ?: "--"}", color = if (ambient) Grey else Red, fontSize = 18.sp)
                if (sport.showsDistance) {
                    Text(String.format(Locale.US, "%.2f km", (workout.distanceM ?: 0.0) / 1000), color = textColor, fontSize = 18.sp)
                }
            }
            if (sport.showsDistance) {
                Text(formatSpeed(sport, workout.speedMps), color = Grey, fontSize = 14.sp)
            }
            if (sport.usesGps && !workout.gpsFix && !ambient) {
                Text("Searching GPS…", color = Orange, fontSize = 12.sp)
            }

            when (workout.phase) {
                Phase.STARTING -> Text("Starting…", color = Grey, fontSize = 14.sp)
                Phase.ENDING -> Text("Saving…", color = Grey, fontSize = 14.sp)
                else -> if (!ambient) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (workout.phase == Phase.PAUSED) {
                            Button(onClick = onResume, colors = ButtonDefaults.primaryButtonColors(backgroundColor = Green), modifier = Modifier.size(ButtonDefaults.SmallButtonSize)) {
                                Text("▶", color = Color.Black)
                            }
                        } else {
                            Button(onClick = onPause, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(ButtonDefaults.SmallButtonSize)) {
                                Text("II")
                            }
                        }
                        Button(
                            onClick = { onPause(); confirmStop = true },
                            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Red),
                            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                        ) {
                            Text("■", color = Color.White)
                        }
                    }
                }
            }
            if (workout.phase == Phase.PAUSED) Text("Paused", color = Orange, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FinishDialog(onSave: () -> Unit, onDiscard: () -> Unit, onResume: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color.Black)) {
        item { Text("Finish workout?", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item {
            Chip(onClick = onSave, label = { Text("Save & upload") },
                colors = ChipDefaults.primaryChipColors(backgroundColor = StravaOrange), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = onResume, label = { Text("Resume") },
                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = onDiscard, label = { Text("Discard") },
                colors = ChipDefaults.secondaryChipColors(contentColor = Red), modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
fun ResultScreen(workout: WorkoutUi, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (icon, color) = when (workout.phase) {
                Phase.SAVED -> "✓" to Green
                Phase.ERROR -> "!" to Red
                else -> "–" to Grey
            }
            Text(icon, color = color, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(workout.message ?: "", textAlign = TextAlign.Center, fontSize = 14.sp)
            if (workout.phase == Phase.SAVED) {
                Text(formatDuration(workout.activeMsAtCheckpoint), color = Grey, fontSize = 14.sp)
            }
            Button(onClick = onDone, modifier = Modifier.size(ButtonDefaults.SmallButtonSize)) { Text("OK") }
        }
    }
}

// ---------------------------------------------------------------------------------------------

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatSpeed(sport: Sport, speedMps: Double?): String {
    if (speedMps == null || speedMps < 0.3) return if (sport == Sport.RIDE) "-- km/h" else "--:-- /km"
    if (sport == Sport.RIDE) return String.format(Locale.US, "%.1f km/h", speedMps * 3.6)
    val secPerKm = (1000 / speedMps).toInt()
    return String.format(Locale.US, "%d:%02d /km", secPerKm / 60, secPerKm % 60)
}
