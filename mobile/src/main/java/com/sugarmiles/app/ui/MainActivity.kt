package com.sugarmiles.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sugarmiles.app.AppSettings
import com.sugarmiles.app.SummaryTarget
import com.sugarmiles.app.glucose.GlucoseReading
import com.sugarmiles.app.glucose.ReceiverLog
import com.sugarmiles.app.nightscout.NightscoutClient
import com.sugarmiles.app.nightscout.NightscoutSettings
import com.sugarmiles.app.glucose.GlucoseStore
import com.sugarmiles.app.strava.StravaAuth
import com.sugarmiles.app.workout.UploadStatus
import com.sugarmiles.app.workout.UploadWorker
import com.sugarmiles.app.workout.WorkoutRecord
import com.sugarmiles.app.workout.WorkoutStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StravaOrange = Color(0xFFFC5200)

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var batteryOk = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        StravaAuth.load(this)
        ReceiverLog.load(this)
        NightscoutSettings.load(this)
        lifecycleScope.launch(Dispatchers.IO) {
            GlucoseStore.load(this@MainActivity)
            WorkoutStore.load(this@MainActivity)
        }
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleOAuthRedirect(intent)

        setContent {
            val dark = isSystemInDarkTheme()
            val scheme: ColorScheme = if (dark) darkColorScheme(primary = StravaOrange) else lightColorScheme(primary = StravaOrange)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { HomeScreen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        batteryOk.value = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        lifecycleScope.launch(Dispatchers.IO) { WorkoutStore.load(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "sugarmiles") return
        setIntent(Intent()) // don't re-use the one-time code when reopened from recents
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val scope = uri.getQueryParameter("scope").orEmpty()
        if (error != null || code == null) {
            toast("Strava login cancelled")
            return
        }
        lifecycleScope.launch {
            try {
                StravaAuth.exchangeCode(this@MainActivity, code)
                if (!scope.contains("activity:write")) {
                    toast("Connected, but uploading wasn't allowed. Connect again and tick 'Upload activities'.")
                } else {
                    toast("Connected to Strava")
                }
            } catch (e: Exception) {
                toast("Strava login failed: ${e.message}")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------------------------------

    @Composable
    private fun HomeScreen() {
        val athlete by StravaAuth.athleteName.collectAsStateWithLifecycle()
        val glucose by GlucoseStore.latest.collectAsStateWithLifecycle()
        val workouts by WorkoutStore.all.collectAsStateWithLifecycle()
        val target by AppSettings.summaryTarget.collectAsStateWithLifecycle()
        val privateNoteWorks by AppSettings.privateNoteWorks.collectAsStateWithLifecycle()

        LazyColumn(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("SugarMiles", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            }
            item { StravaCard(athlete) }
            item { GlucoseCard(glucose) }
            item { SummaryTargetCard(target, privateNoteWorks) }
            item { NightscoutCard() }
            if (!batteryOk.value) item { BatteryCard() }
            item {
                Text("Workouts", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }
            if (workouts.isEmpty()) {
                item { Text("No workouts yet. Finish one on the watch and it shows up here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(workouts, key = { it.id }) { WorkoutCard(it) }
            item { Spacer(Modifier.padding(16.dp)) }
        }
    }

    @Composable
    private fun SectionCard(title: String, content: @Composable () -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                content()
            }
        }
    }

    @Composable
    private fun StravaCard(athlete: String?) {
        SectionCard("Strava") {
            when {
                !StravaAuth.isConfigured -> Text(
                    "Add strava.clientId and strava.clientSecret to local.properties and rebuild.",
                    color = MaterialTheme.colorScheme.error,
                )
                athlete == null -> Button(onClick = {
                    startActivity(Intent(Intent.ACTION_VIEW, StravaAuth.authorizeUri()))
                }) { Text("Connect Strava") }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connected as $athlete", modifier = Modifier.weight(1f))
                    TextButton(onClick = { StravaAuth.disconnect(this@MainActivity) }) { Text("Disconnect") }
                }
            }
        }
    }

    @Composable
    private fun GlucoseCard(glucose: GlucoseReading?) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }

        SectionCard("Glucose") {
            if (glucose == null) {
                Text("No readings received yet.")
                Text(
                    "xDrip+: Settings → Inter-app settings → Broadcast locally ON, and Identify receiver = com.sugarmiles.app",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            } else {
                val ageMin = (now - glucose.timeMs) / 60_000
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${glucose.mgdl} ${glucose.trend}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("mg/dL · ${if (ageMin < 1) "just now" else "$ageMin min ago"} · ${glucose.source}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("Forwarded to the watch automatically.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Diagnostics: what did the receiver last see?
            val lastBroadcast by ReceiverLog.last.collectAsStateWithLifecycle()
            val diag = lastBroadcast?.let {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it.timeMs))
                "Last broadcast: $time\n${it.action}\n${it.detail}"
            } ?: "Last broadcast: none received yet"
            Text(diag, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun SummaryTargetCard(target: SummaryTarget, privateNoteWorks: Boolean?) {
        SectionCard("Glucose summary on Strava") {
            SummaryTarget.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { AppSettings.setSummaryTarget(this@MainActivity, option) },
                ) {
                    RadioButton(selected = option == target, onClick = { AppSettings.setSummaryTarget(this@MainActivity, option) })
                    Column {
                        Text(option.label)
                        Text(option.explanation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val status = when (privateNoteWorks) {
                null -> "Private note support: checked on your next upload."
                true -> "Private note support: works ✓"
                false -> "Private note support: Strava ignored it ✗"
            }
            Text(status, fontSize = 13.sp)
        }
    }

    @Composable
    private fun NightscoutCard() {
        val config by NightscoutSettings.config.collectAsStateWithLifecycle()
        var url by remember(config.url) { mutableStateOf(config.url) }
        var token by remember(config.token) { mutableStateOf(config.token) }
        var status by remember { mutableStateOf<String?>(null) }
        var showToken by remember { mutableStateOf(false) }

        SectionCard("Nightscout") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Add workouts as Exercise")
                    Text(
                        "When you save a workout, adds one Exercise entry at its start time with duration and a summary.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { on ->
                        NightscoutSettings.save(this@MainActivity, config.copy(enabled = on, url = url.trim(), token = token.trim()))
                    },
                )
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Nightscout URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Access token (careportal role)") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                val updated = config.copy(url = url.trim(), token = token.trim())
                NightscoutSettings.save(this@MainActivity, updated)
                status = "Checking…"
                lifecycleScope.launch { status = NightscoutClient.verify(updated) }
            }) { Text("Save & test") }
            status?.let { Text(it, fontSize = 13.sp) }
        }
    }

    @Composable
    private fun BatteryCard() {
        SectionCard("Run in background") {
            Text("Samsung may stop SugarMiles in the background, which breaks glucose forwarding and uploads.")
            Button(onClick = {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }) { Text("Allow background use") }
        }
    }


    @Composable
    private fun WorkoutCard(w: WorkoutRecord) {
        val date = remember(w.startMs) { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(w.startMs)) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(w.name, fontWeight = FontWeight.SemiBold)
                val details = buildList {
                    add(date)
                    add(formatDuration(w.activeSec))
                    if (w.distanceM > 0) add(String.format(Locale.US, "%.2f km", w.distanceM / 1000))
                    if (w.avgHr > 0) add("♥ ${w.avgHr}")
                }.joinToString(" · ")
                Text(details, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                w.glucoseSummary?.let { Text(it, fontSize = 13.sp) }
                w.glucoseWhere?.let { Text("Glucose added to: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                val (statusText, statusColor) = when (w.status) {
                    UploadStatus.PENDING -> "Waiting to upload" to MaterialTheme.colorScheme.onSurfaceVariant
                    UploadStatus.UPLOADING -> "Uploading…" to MaterialTheme.colorScheme.onSurfaceVariant
                    UploadStatus.UPLOADED -> "On Strava ✓" to Color(0xFF2E7D32)
                    UploadStatus.FAILED -> "Upload failed" to MaterialTheme.colorScheme.error
                }
                Text(statusText, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                w.error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (w.status != UploadStatus.UPLOADED) {
                        OutlinedButton(onClick = { UploadWorker.enqueue(this@MainActivity, w.id, replace = true) }) { Text("Retry") }
                    }
                    if (w.activityId != null) {
                        TextButton(onClick = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.strava.com/activities/${w.activityId}")))
                        }) { Text("Open in Strava") }
                    }
                }
            }
        }
    }

    private fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }
}
