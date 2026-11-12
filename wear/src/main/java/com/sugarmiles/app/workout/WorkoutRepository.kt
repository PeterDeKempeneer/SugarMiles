package com.sugarmiles.app.workout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Phase { IDLE, STARTING, ACTIVE, PAUSED, ENDING, SAVED, DISCARDED, ERROR }

data class WorkoutUi(
    val phase: Phase = Phase.IDLE,
    val sport: Sport? = null,
    /** Active (non-paused) time at [checkpointMs]. */
    val activeMsAtCheckpoint: Long = 0,
    /** Wall-clock time of the checkpoint; the timer runs from here while ACTIVE. */
    val checkpointMs: Long = 0,
    val heartRate: Int? = null,
    val distanceM: Double? = null,
    val calories: Double? = null,
    val speedMps: Double? = null,
    val gpsFix: Boolean = false,
    val message: String? = null,
) {
    fun activeMs(nowMs: Long): Long =
        if (phase == Phase.ACTIVE && checkpointMs > 0) activeMsAtCheckpoint + (nowMs - checkpointMs)
        else activeMsAtCheckpoint
}

/** Single source of truth for the running workout, written by ExerciseService, read by the UI. */
object WorkoutRepository {
    private val _state = MutableStateFlow(WorkoutUi())
    val state: StateFlow<WorkoutUi> = _state.asStateFlow()

    fun set(newState: WorkoutUi) { _state.value = newState }
    fun update(block: (WorkoutUi) -> WorkoutUi) { _state.value = block(_state.value) }
    fun reset() { _state.value = WorkoutUi() }
}
