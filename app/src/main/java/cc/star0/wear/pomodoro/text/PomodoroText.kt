package cc.star0.wear.pomodoro.text

import android.content.res.Resources
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroSettings
import kotlin.math.roundToInt

/** Shared by Compose and notifications; use the context's configured app locale. */
fun formatRoundLabel(resources: Resources, round: Int, interval: Int): String =
    if (interval == PomodoroSettings.NEVER_LONG_BREAK) {
        resources.getString(R.string.round_number, round)
    } else {
        resources.getString(R.string.round_number_of, round, interval)
    }

fun formatDuration(resources: Resources, millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        resources.getString(R.string.duration_hms, hours, minutes, seconds)
    } else {
        resources.getString(R.string.duration_ms, minutes, seconds)
    }
}

fun durationInMinutes(millis: Long): Int =
    (millis / PomodoroSettings.MILLIS_PER_MINUTE.toFloat()).roundToInt()

fun formatMinutes(resources: Resources, millis: Long): String {
    val minutes = durationInMinutes(millis)
    return resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
}
