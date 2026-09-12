package cc.star0.wear.pomodoro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.star0.wear.pomodoro.model.PomodoroPhase
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.PomodoroState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pomodoroDataStore by preferencesDataStore(name = "pomodoro")

/** Persists settings and timer state so the timer survives process death. */
class PomodoroStore(context: Context) {
    private val dataStore = context.pomodoroDataStore

    private object Keys {
        val focusDuration = longPreferencesKey("focus_duration_millis")
        val shortBreakDuration = longPreferencesKey("short_break_duration_millis")
        val longBreakDuration = longPreferencesKey("long_break_duration_millis")
        val roundsBeforeLongBreak = intPreferencesKey("focus_rounds_before_long_break")
        val systemBackGesture = booleanPreferencesKey("system_back_gesture_enabled")
        val composeSwipeBack = booleanPreferencesKey("compose_swipe_back_enabled")

        val phase = stringPreferencesKey("timer_phase")
        val isRunning = booleanPreferencesKey("timer_is_running")
        val endAtElapsed = longPreferencesKey("timer_end_at_elapsed_millis")
        val remainingWhenPaused = longPreferencesKey("timer_remaining_when_paused")
        val completedRounds = intPreferencesKey("timer_completed_focus_rounds")
        val activeFocusDuration = longPreferencesKey("active_focus_duration_millis")
        val activeShortBreakDuration = longPreferencesKey("active_short_break_duration_millis")
        val activeLongBreakDuration = longPreferencesKey("active_long_break_duration_millis")
        val activeRoundsBeforeLongBreak = intPreferencesKey("active_rounds_before_long_break")
    }

    val settings: Flow<PomodoroSettings> = dataStore.data.map { preferences ->
        readSettingsFrom(preferences)
    }

    suspend fun loadSettings(): PomodoroSettings = readSettingsFrom(dataStore.data.first())

    suspend fun saveSettings(settings: PomodoroSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.focusDuration] = settings.focusDurationMillis
            preferences[Keys.shortBreakDuration] = settings.shortBreakDurationMillis
            preferences[Keys.longBreakDuration] = settings.longBreakDurationMillis
            preferences[Keys.roundsBeforeLongBreak] = settings.focusRoundsBeforeLongBreak
            preferences[Keys.systemBackGesture] = settings.systemBackGestureEnabled
            preferences[Keys.composeSwipeBack] = settings.composeSwipeBackEnabled
        }
    }

    suspend fun loadState(defaultSettings: PomodoroSettings): PomodoroState {
        val preferences = dataStore.data.first()
        val phase = preferences[Keys.phase]?.let { saved ->
            PomodoroPhase.entries.firstOrNull { it.name == saved }
        } ?: PomodoroPhase.ReadyToFocus
        val activeSettings = if (
            preferences.contains(Keys.activeFocusDuration) &&
            preferences.contains(Keys.activeShortBreakDuration) &&
            preferences.contains(Keys.activeLongBreakDuration) &&
            preferences.contains(Keys.activeRoundsBeforeLongBreak)
        ) {
            PomodoroSettings(
                focusDurationMillis = preferences[Keys.activeFocusDuration] ?: defaultSettings.focusDurationMillis,
                shortBreakDurationMillis = preferences[Keys.activeShortBreakDuration] ?: defaultSettings.shortBreakDurationMillis,
                longBreakDurationMillis = preferences[Keys.activeLongBreakDuration] ?: defaultSettings.longBreakDurationMillis,
                focusRoundsBeforeLongBreak = preferences[Keys.activeRoundsBeforeLongBreak] ?: defaultSettings.focusRoundsBeforeLongBreak,
            ).sanitized()
        } else {
            null
        }
        val settings = activeSettings ?: defaultSettings
        val running = preferences[Keys.isRunning] ?: false
        val remaining = preferences[Keys.remainingWhenPaused]
            ?: settings.focusDurationMillis
        return PomodoroState(
            phase = phase,
            isRunning = running,
            endAtElapsedMillis = preferences[Keys.endAtElapsed],
            remainingMillisWhenPaused = remaining.coerceAtLeast(0L),
            completedFocusRounds = (preferences[Keys.completedRounds] ?: 0).coerceAtLeast(0),
            activeSettings = activeSettings,
        )
    }

    suspend fun saveState(state: PomodoroState) {
        dataStore.edit { preferences ->
            preferences[Keys.phase] = state.phase.name
            preferences[Keys.isRunning] = state.isRunning
            state.endAtElapsedMillis?.let { preferences[Keys.endAtElapsed] = it }
                ?: preferences.remove(Keys.endAtElapsed)
            preferences[Keys.remainingWhenPaused] = state.remainingMillisWhenPaused
            preferences[Keys.completedRounds] = state.completedFocusRounds
            state.activeSettings?.let { active ->
                preferences[Keys.activeFocusDuration] = active.focusDurationMillis
                preferences[Keys.activeShortBreakDuration] = active.shortBreakDurationMillis
                preferences[Keys.activeLongBreakDuration] = active.longBreakDurationMillis
                preferences[Keys.activeRoundsBeforeLongBreak] = active.focusRoundsBeforeLongBreak
            } ?: run {
                preferences.remove(Keys.activeFocusDuration)
                preferences.remove(Keys.activeShortBreakDuration)
                preferences.remove(Keys.activeLongBreakDuration)
                preferences.remove(Keys.activeRoundsBeforeLongBreak)
            }
        }
    }

    private fun readSettingsFrom(
        preferences: androidx.datastore.preferences.core.Preferences,
    ): PomodoroSettings = PomodoroSettings(
        focusDurationMillis = preferences[Keys.focusDuration] ?: (25 * PomodoroSettings.MILLIS_PER_MINUTE),
        shortBreakDurationMillis = preferences[Keys.shortBreakDuration] ?: (5 * PomodoroSettings.MILLIS_PER_MINUTE),
        longBreakDurationMillis = preferences[Keys.longBreakDuration] ?: (15 * PomodoroSettings.MILLIS_PER_MINUTE),
        focusRoundsBeforeLongBreak = preferences[Keys.roundsBeforeLongBreak] ?: 4,
        systemBackGestureEnabled = preferences[Keys.systemBackGesture] ?: true,
        composeSwipeBackEnabled = preferences[Keys.composeSwipeBack] ?: true,
    ).sanitized()
}
