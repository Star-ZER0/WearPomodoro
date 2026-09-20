package cc.star0.wear.pomodoro.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import cc.star0.wear.pomodoro.data.PomodoroStore
import cc.star0.wear.pomodoro.model.PomodoroEngine
import cc.star0.wear.pomodoro.model.PomodoroPhase
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.PomodoroState
import cc.star0.wear.pomodoro.notifications.PomodoroNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** Owns timer transitions, persistence, exact alarms, and reminder notifications. */
class PomodoroController(
    private val context: Context,
    private val externalScope: CoroutineScope,
) {
    // First used by the background restore below, not by Application.onCreate on the UI thread.
    private val store by lazy { PomodoroStore(context) }
    private val mutex = Mutex()
    private val alarmManager by lazy { context.getSystemService<AlarmManager>() }
    private val _settings = MutableStateFlow(PomodoroSettings())
    private val _state = MutableStateFlow(PomodoroState.initial())
    private val _isInitialized = MutableStateFlow(false)

    val settings: StateFlow<PomodoroSettings> = _settings.asStateFlow()
    val state: StateFlow<PomodoroState> = _state.asStateFlow()
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        externalScope.launch {
            val savedSettings = store.loadSettings()
            val savedState = store.loadState(savedSettings)
            _settings.value = savedSettings
            _state.value = normalizeRestoredState(savedState, savedSettings)
            _isInitialized.value = true

            val current = _state.value
            if (current.isRunning) {
                scheduleAlarm(current)
            } else {
                cancelAlarm()
            }
            store.saveState(current)
        }
    }

    suspend fun awaitInitialized(timeoutMillis: Long = 5_000L) {
        if (!_isInitialized.value) {
            withTimeoutOrNull(timeoutMillis.milliseconds) {
                _isInitialized.first { it }
            }
        }
    }

    fun startFocus() = transition {
        if (it.isRunning || it.phase != PomodoroPhase.ReadyToFocus) return@transition it
        val settings = _settings.value
        PomodoroEngine.startFocus(it, settings, SystemClock.elapsedRealtime())
    }

    fun pause() = transition {
        PomodoroEngine.pause(it, SystemClock.elapsedRealtime())
    }

    fun resume() = transition {
        PomodoroEngine.resume(it, SystemClock.elapsedRealtime())
    }

    fun stop() = transition {
        PomodoroEngine.stop(_settings.value)
    }

    fun completePhase() = transition {
        val newState = PomodoroEngine.completePhase(it, SystemClock.elapsedRealtime())
        if (newState != null) {
            PomodoroNotifications.notifyPhaseFinished(context, it, newState)
        }
        newState ?: it
    }

    fun updateSettings(newSettings: PomodoroSettings) {
        if (!_isInitialized.value) return
        val sanitized = newSettings.sanitized()
        _settings.value = sanitized
        externalScope.launch {
            store.saveSettings(sanitized)
        }
        val previous = _state.value
        _state.update { current -> PomodoroEngine.applySettings(current, sanitized) }
        val current = _state.value
        if (current != previous) {
            externalScope.launch { store.saveState(current) }
        }
    }

    suspend fun resetAfterReboot() {
        awaitInitialized()
        mutex.withLock {
            cancelAlarm()
            val settings = _settings.value
            _state.value = PomodoroState.initial(settings)
            store.saveState(_state.value)
        }
    }

    private fun transition(update: (PomodoroState) -> PomodoroState) {
        if (!_isInitialized.value) return
        val oldState = _state.value
        val newState = update(oldState)
        if (newState == oldState) {
            return
        }
        _state.value = newState
        if (newState.isRunning && newState.endAtElapsedMillis != null) {
            scheduleAlarm(newState)
        } else {
            cancelAlarm()
        }
        externalScope.launch {
            store.saveState(newState)
        }
    }

    private fun normalizeRestoredState(
        restoredState: PomodoroState,
        settings: PomodoroSettings,
    ): PomodoroState {
        // The persisted preference decides future breaks even if an older timer snapshot differs.
        val savedState = PomodoroEngine.applySettings(restoredState, settings)
        if (!savedState.isRunning) {
            return savedState.copy(
                endAtElapsedMillis = null,
                activeSettings = savedState.activeSettings?.sanitized(),
            )
        }
        if (savedState.endAtElapsedMillis == null) {
            return PomodoroEngine.pause(savedState, SystemClock.elapsedRealtime())
        }
        val now = SystemClock.elapsedRealtime()
        if (savedState.endAtElapsedMillis > now) {
            return savedState
        }
        val completed = PomodoroEngine.completePhase(savedState, now)
        if (completed != null) {
            PomodoroNotifications.notifyPhaseFinished(context, savedState, completed)
            return completed
        }
        return PomodoroState.initial(settings)
    }

    private fun scheduleAlarm(state: PomodoroState) {
        val endAt = state.endAtElapsedMillis ?: return
        val manager = alarmManager ?: return
        val pendingIntent = phaseFinishedPendingIntent
        manager.cancel(pendingIntent)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms() ->
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    endAt,
                    pendingIntent,
                )
            else ->
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    endAt,
                    pendingIntent,
                )
        }
    }

    private fun cancelAlarm() {
        alarmManager?.cancel(phaseFinishedPendingIntent)
    }

    private val phaseFinishedPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, PomodoroService::class.java).apply {
            action = PomodoroService.ACTION_PHASE_FINISHED
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, REQUEST_CODE_PHASE, intent, flags)
        } else {
            PendingIntent.getService(context, REQUEST_CODE_PHASE, intent, flags)
        }
    }

    private companion object {
        const val REQUEST_CODE_PHASE = 1001
    }
}
