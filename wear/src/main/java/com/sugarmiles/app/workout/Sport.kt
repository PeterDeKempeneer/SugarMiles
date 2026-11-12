package com.sugarmiles.app.workout

import androidx.health.services.client.data.ExerciseType

/**
 * Sports offered on the watch. The enum *name* is sent to the phone, which maps it to
 * a Strava sport_type, so keep names in sync with the phone's Sport enum.
 */
enum class Sport(
    val label: String,
    val emoji: String,
    val exerciseType: ExerciseType,
    val usesGps: Boolean,
    val tcxSport: String,
    val showsDistance: Boolean,
) {
    WALK("Walk", "🚶", ExerciseType.WALKING, usesGps = true, tcxSport = "Other", showsDistance = true),
    RUN("Run", "🏃", ExerciseType.RUNNING, usesGps = true, tcxSport = "Running", showsDistance = true),
    RIDE("Ride", "🚴", ExerciseType.BIKING, usesGps = true, tcxSport = "Biking", showsDistance = true),
    SWIM("Swim", "🏊", ExerciseType.SWIMMING_POOL, usesGps = false, tcxSport = "Other", showsDistance = false),
    WORKOUT("Workout", "💪", ExerciseType.WORKOUT, usesGps = false, tcxSport = "Other", showsDistance = false),
    WEIGHTS("Weight training", "🏋️", ExerciseType.WEIGHTLIFTING, usesGps = false, tcxSport = "Other", showsDistance = false),
}
