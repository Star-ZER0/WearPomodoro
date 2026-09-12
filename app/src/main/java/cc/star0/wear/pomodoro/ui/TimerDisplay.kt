package cc.star0.wear.pomodoro.ui

import cc.star0.wear.pomodoro.model.PomodoroPhase
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.PomodoroState

internal data class TimerDisplay(
    val remainingMillis: Long,
    val progress: Float,
    val nextTickDelayMillis: Long,
)

/** The label and ring share the same rounded second; wakeup follow the actual deadline. */
internal fun timerDisplay(
    state: PomodoroState,
    settings: PomodoroSettings,
    nowElapsedMillis: Long,
): TimerDisplay {
    val ready = state.phase == PomodoroPhase.ReadyToFocus
    val remaining = (if (ready) settings.focusDurationMillis else state.remainingMillis(nowElapsedMillis))
        .coerceAtLeast(0L)
    val total = if (ready) settings.focusDurationMillis else state.totalMillis()
    val displayedRemaining = ((remaining + 999L) / 1_000L) * 1_000L
    return TimerDisplay(
        remainingMillis = displayedRemaining,
        progress = if (ready || total <= 0L) 0f else
            (1f - displayedRemaining.toFloat() / total.toFloat()).coerceIn(0f, 1f),
        nextTickDelayMillis = if (!ready && state.isRunning && remaining > 0L) {
            // A resumed timer can have a partial second left. Align to its next displayed second.
            (remaining - 1L) % 1_000L + 1L
        } else {
            0L
        },
    )
}
