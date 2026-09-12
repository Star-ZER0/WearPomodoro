package cc.star0.wear.pomodoro.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cc.star0.wear.pomodoro.PomodoroApplication
import cc.star0.wear.pomodoro.notifications.PomodoroNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Keeps the persistent notification alive and receives timer actions and exact alarms. */
class PomodoroService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopConfirmationPending = false
    private var stopConfirmationJob: Job? = null
    private val controller by lazy {
        (application as PomodoroApplication).pomodoroController
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        serviceScope.launch {
            controller.state.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val action = intent?.action
        serviceScope.launch {
            controller.awaitInitialized()
            var stopped = false
            when (action) {
                ACTION_START -> {
                    clearStopConfirmation()
                    controller.startFocus()
                }
                ACTION_PAUSE -> {
                    clearStopConfirmation()
                    controller.pause()
                }
                ACTION_RESUME -> {
                    clearStopConfirmation()
                    controller.resume()
                }
                ACTION_PHASE_FINISHED -> {
                    clearStopConfirmation()
                    controller.completePhase()
                }
                ACTION_STOP -> {
                    if (stopConfirmationPending) {
                        clearStopConfirmation()
                        controller.stop()
                        stopped = true
                    } else {
                        stopConfirmationPending = true
                        scheduleStopConfirmationTimeout()
                    }
                }
                ACTION_STOP_CONFIRMED -> {
                    clearStopConfirmation()
                    controller.stop()
                    stopped = true
                }
            }
            val state = controller.state.value
            updateNotification(state)
            if (stopped) {
                ServiceCompat.stopForeground(this@PomodoroService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopConfirmationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotification(controller.state.value)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        showForegroundNotification(controller.state.value)
    }

    private fun updateNotification(state: cc.star0.wear.pomodoro.model.PomodoroState) {
        showForegroundNotification(state)
    }

    private fun scheduleStopConfirmationTimeout() {
        stopConfirmationJob?.cancel()
        stopConfirmationJob = serviceScope.launch {
            delay(STOP_CONFIRMATION_TIMEOUT_MILLIS.milliseconds)
            stopConfirmationPending = false
            stopConfirmationJob = null
            updateNotification(controller.state.value)
        }
    }

    private fun clearStopConfirmation() {
        stopConfirmationPending = false
        stopConfirmationJob?.cancel()
        stopConfirmationJob = null
    }

    private fun showForegroundNotification(state: cc.star0.wear.pomodoro.model.PomodoroState) {
        val notification = PomodoroNotifications.buildOngoingNotification(
            this,
            state,
            SystemClock.elapsedRealtime(),
            stopConfirmationPending,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            PomodoroNotifications.ONGOING_NOTIFICATION_ID,
            notification,
            type,
        )
    }

    companion object {
        const val ACTION_START = "cc.star0.wear.pomodoro.action.START"
        const val ACTION_PAUSE = "cc.star0.wear.pomodoro.action.PAUSE"
        const val ACTION_RESUME = "cc.star0.wear.pomodoro.action.RESUME"
        const val ACTION_STOP = "cc.star0.wear.pomodoro.action.STOP"
        const val ACTION_STOP_CONFIRMED = "cc.star0.wear.pomodoro.action.STOP_CONFIRMED"
        const val ACTION_PHASE_FINISHED = "cc.star0.wear.pomodoro.action.PHASE_FINISHED"
        const val ACTION_RESTORE = "cc.star0.wear.pomodoro.action.RESTORE"
        private const val STOP_CONFIRMATION_TIMEOUT_MILLIS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, PomodoroService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, PomodoroService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun ensureRunning(context: Context) {
            val intent = Intent(context, PomodoroService::class.java).setAction(ACTION_RESTORE)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
