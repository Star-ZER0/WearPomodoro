package cc.star0.wear.pomodoro.model

/** Timer phases used by both the foreground service and the UI. */
enum class PomodoroPhase {
    ReadyToFocus,
    Focus,
    ShortBreak,
    LongBreak,
}

enum class ScreenStyle {
    Round,
    Square,
}

enum class NotificationStyle {
    LiveUpdate,
    OngoingActivity,
    Standard,
}

/** Timer and interface settings. Durations are stored in milliseconds. */
data class PomodoroSettings(
    val focusDurationMillis: Long = 25.minutesInMillis,
    val shortBreakDurationMillis: Long = 5.minutesInMillis,
    val longBreakDurationMillis: Long = 15.minutesInMillis,
    val focusRoundsBeforeLongBreak: Int = 4,
    val systemBackGestureEnabled: Boolean = true,
    val composeSwipeBackEnabled: Boolean = true,
    val screenStyle: ScreenStyle = ScreenStyle.Round,
    val squareTimerCornerRadiusDp: Int = DEFAULT_SQUARE_TIMER_CORNER_RADIUS_DP,
    val liveUpdateNotificationEnabled: Boolean = false,
    val ongoingActivityNotificationEnabled: Boolean = true,
    val standardNotificationEnabled: Boolean = false,
) {
    fun isNotificationEnabled(style: NotificationStyle): Boolean = when (style) {
        NotificationStyle.LiveUpdate -> liveUpdateNotificationEnabled
        NotificationStyle.OngoingActivity -> ongoingActivityNotificationEnabled
        NotificationStyle.Standard -> standardNotificationEnabled
    }

    fun withNotificationEnabled(style: NotificationStyle, enabled: Boolean): PomodoroSettings = when (style) {
        NotificationStyle.LiveUpdate -> copy(liveUpdateNotificationEnabled = enabled)
        NotificationStyle.OngoingActivity -> copy(ongoingActivityNotificationEnabled = enabled)
        NotificationStyle.Standard -> copy(standardNotificationEnabled = enabled)
    }

    fun sanitized(): PomodoroSettings = copy(
        squareTimerCornerRadiusDp = squareTimerCornerRadiusDp.coerceIn(1, 100),
        focusDurationMillis = focusDurationMillis.coerceIn(1.minutesInMillis, MAX_DURATION_MINUTES.minutesInMillis),
        shortBreakDurationMillis = shortBreakDurationMillis.coerceIn(1.minutesInMillis, MAX_DURATION_MINUTES.minutesInMillis),
        longBreakDurationMillis = longBreakDurationMillis.coerceIn(1.minutesInMillis, MAX_DURATION_MINUTES.minutesInMillis),
        focusRoundsBeforeLongBreak = if (focusRoundsBeforeLongBreak == NEVER_LONG_BREAK) {
            NEVER_LONG_BREAK
        } else {
            focusRoundsBeforeLongBreak.coerceIn(2, 12)
        },
    )

    companion object {
        const val DEFAULT_SQUARE_TIMER_CORNER_RADIUS_DP = 24
        const val MILLIS_PER_MINUTE = 60_000L
        const val MAX_DURATION_MINUTES = 23 * 60 + 59
        const val NEVER_LONG_BREAK = 0
        val LongBreakRoundOptions: List<Int> = (2..12).toList() + NEVER_LONG_BREAK
        val Int.minutesInMillis: Long
            get() = this * MILLIS_PER_MINUTE
    }
}

/** The complete, restorable timer state. Elapsed timestamps use SystemClock.elapsedRealtime(). */
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.ReadyToFocus,
    val isRunning: Boolean = false,
    val endAtElapsedMillis: Long? = null,
    val remainingMillisWhenPaused: Long,
    val completedFocusRounds: Int = 0,
    val activeSettings: PomodoroSettings? = null,
) {
    val effectiveSettings: PomodoroSettings
        get() = activeSettings ?: PomodoroSettings()

    fun remainingMillis(nowElapsedMillis: Long): Long = when {
        !isRunning -> remainingMillisWhenPaused
        endAtElapsedMillis == null -> remainingMillisWhenPaused
        else -> (endAtElapsedMillis - nowElapsedMillis).coerceAtLeast(0L)
    }

    fun totalMillis(): Long = when (phase) {
        PomodoroPhase.ReadyToFocus -> effectiveSettings.focusDurationMillis
        PomodoroPhase.Focus -> effectiveSettings.focusDurationMillis
        PomodoroPhase.ShortBreak -> effectiveSettings.shortBreakDurationMillis
        PomodoroPhase.LongBreak -> effectiveSettings.longBreakDurationMillis
    }

    companion object {
        fun initial(settings: PomodoroSettings = PomodoroSettings()) = PomodoroState(
            remainingMillisWhenPaused = settings.focusDurationMillis,
        )
    }
}

/** Pure transition logic. Keeping it separate makes pause, restore, and long-break rules testable. */
object PomodoroEngine {
    /** Apply the next-break schedule immediately without changing an active phase's duration. */
    fun applySettings(state: PomodoroState, settings: PomodoroSettings): PomodoroState =
        if (state.phase == PomodoroPhase.ReadyToFocus && !state.isRunning) {
            state.copy(remainingMillisWhenPaused = settings.focusDurationMillis, activeSettings = null)
        } else {
            state.copy(
                activeSettings = state.effectiveSettings.copy(
                    focusRoundsBeforeLongBreak = settings.focusRoundsBeforeLongBreak,
                ),
            )
        }

    fun startFocus(
        state: PomodoroState,
        settings: PomodoroSettings,
        nowElapsedMillis: Long,
    ): PomodoroState = PomodoroState(
        phase = PomodoroPhase.Focus,
        isRunning = true,
        endAtElapsedMillis = nowElapsedMillis + settings.focusDurationMillis,
        remainingMillisWhenPaused = settings.focusDurationMillis,
        completedFocusRounds = state.completedFocusRounds,
        activeSettings = settings,
    )

    fun pause(state: PomodoroState, nowElapsedMillis: Long): PomodoroState {
        if (!state.isRunning) return state
        return state.copy(
            isRunning = false,
            remainingMillisWhenPaused = state.remainingMillis(nowElapsedMillis),
            endAtElapsedMillis = null,
        )
    }

    fun resume(state: PomodoroState, nowElapsedMillis: Long): PomodoroState {
        if (state.isRunning || state.phase == PomodoroPhase.ReadyToFocus) return state
        val remaining = state.remainingMillisWhenPaused.coerceAtLeast(0L)
        return state.copy(
            isRunning = true,
            endAtElapsedMillis = nowElapsedMillis + remaining,
            remainingMillisWhenPaused = remaining,
        )
    }

    fun stop(settings: PomodoroSettings): PomodoroState =
        PomodoroState.initial(settings)

    /** Returns null when a duplicate alarm arrives after the phase has already changed. */
    fun completePhase(
        state: PomodoroState,
        nowElapsedMillis: Long,
    ): PomodoroState? {
        if (!state.isRunning) return null
        val deadline = state.endAtElapsedMillis ?: return null
        if (nowElapsedMillis < deadline) return null
        val settings = state.effectiveSettings
        return when (state.phase) {
            PomodoroPhase.ReadyToFocus -> null
            PomodoroPhase.Focus -> {
                val completed = state.completedFocusRounds + 1
                val interval = settings.focusRoundsBeforeLongBreak
                val isLongBreak = interval > 0 && completed % interval == 0
                val duration = if (isLongBreak) {
                    settings.longBreakDurationMillis
                } else {
                    settings.shortBreakDurationMillis
                }
                state.copy(
                    phase = if (isLongBreak) PomodoroPhase.LongBreak else PomodoroPhase.ShortBreak,
                    completedFocusRounds = completed,
                    endAtElapsedMillis = nowElapsedMillis + duration,
                    remainingMillisWhenPaused = duration,
                    activeSettings = settings,
                )
            }
            PomodoroPhase.ShortBreak,
            PomodoroPhase.LongBreak,
            -> PomodoroState(
                phase = PomodoroPhase.ReadyToFocus,
                remainingMillisWhenPaused = settings.focusDurationMillis,
                completedFocusRounds = if (state.phase == PomodoroPhase.LongBreak) {
                    0
                } else {
                    state.completedFocusRounds
                },
                activeSettings = null,
            )
        }
    }
}
