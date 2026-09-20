package cc.star0.wear.pomodoro.timer

import android.app.Service
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cc.star0.wear.pomodoro.PomodoroApplication
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.PomodoroState
import cc.star0.wear.pomodoro.notifications.PomodoroNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Keeps the persistent notification alive and receives timer actions and exact alarms. */
class PomodoroService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stopConfirmationDeadline: Long? = null
    private val stopConfirmationPending: Boolean
        get() = stopConfirmationDeadline?.let { SystemClock.elapsedRealtime() < it } == true
    private var stopConfirmationJob: Job? = null
    private var lastNotificationSnapshot: NotificationSnapshot? = null
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshNotification(force = intent.action == Intent.ACTION_TIME_CHANGED)
        }
    }
    private val controller by lazy {
        (application as PomodoroApplication).pomodoroController
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            refreshReceiver,
            IntentFilter(ACTION_REFRESH_NOTIFICATIONS).apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    addAction(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED)
                    addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
                }
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        startInForeground()
        serviceScope.launch {
            // Display preferences apply immediately, independently of the session duration snapshot.
            combine(controller.state, controller.settings) { state, _ -> state }.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                    val previous = controller.state.value
                    controller.completePhase()
                    if (controller.state.value != previous) clearStopConfirmation()
                }
                ACTION_STOP -> {
                    if (stopConfirmationPending) {
                        clearStopConfirmation()
                        controller.stop()
                        stopped = true
                    } else {
                        stopConfirmationDeadline = SystemClock.elapsedRealtime() + STOP_CONFIRMATION_TIMEOUT_MILLIS
                        scheduleStopConfirmationTimeout()
                    }
                }
                ACTION_STOP_CONFIRMED -> {
                    clearStopConfirmation()
                    controller.stop()
                    stopped = true
                }
            }
            if (stopped) {
                serviceScope.cancel()
                ServiceCompat.stopForeground(this@PomodoroService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                PomodoroNotifications.cancelTimerNotifications(this@PomodoroService)
                stopSelf()
            } else if (action == ACTION_RESTORE) {
                refreshNotification()
            } else {
                updateNotification(controller.state.value)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(refreshReceiver)
        stopConfirmationJob?.cancel()
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        PomodoroNotifications.cancelTimerNotifications(this)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        showForegroundNotification(controller.state.value, force = true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        showForegroundNotification(controller.state.value)
    }

    private fun updateNotification(state: PomodoroState) {
        showForegroundNotification(state)
    }

    private fun scheduleStopConfirmationTimeout() {
        stopConfirmationJob?.cancel()
        stopConfirmationJob = serviceScope.launch {
            delay(STOP_CONFIRMATION_TIMEOUT_MILLIS.milliseconds)
            stopConfirmationDeadline = null
            stopConfirmationJob = null
            updateNotification(controller.state.value)
        }
    }

    private fun clearStopConfirmation() {
        stopConfirmationDeadline = null
        stopConfirmationJob?.cancel()
        stopConfirmationJob = null
    }

    private fun showForegroundNotification(state: PomodoroState, force: Boolean = false) {
        val snapshot = NotificationSnapshot(
            state, controller.settings.value, stopConfirmationPending,
            PomodoroNotifications.notificationAvailability(this),
            PomodoroNotifications.canPostLiveUpdates(this),
        )
        // Commands and the StateFlow collector can report the same transition. Reposting all
        // three displays each time exceeds Android's notification rate limit and drops updates.
        if (!force && snapshot == lastNotificationSnapshot) return
        val notifications = PomodoroNotifications.buildTimerNotifications(
            this,
            state,
            SystemClock.elapsedRealtime(),
            settings = snapshot.settings,
            stopConfirmationPending = stopConfirmationPending,
            canPostLiveUpdates = snapshot.canPostLiveUpdates,
        )
        val foreground = notifications.entries.first()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            foreground.key,
            foreground.value,
            type,
        )
        // Switching foreground IDs can remove the old one; publish additional entries afterwards.
        PomodoroNotifications.updateAdditionalNotifications(this, notifications)
        lastNotificationSnapshot = snapshot
    }

    private fun refreshNotification(force: Boolean = false) {
        // Access changes are part of the snapshot. Do not treat a user-dismissed Live Update
        // as missing data and repost it just because the app becomes visible again.
        showForegroundNotification(controller.state.value, force = force)
    }

    private data class NotificationSnapshot(
        val state: PomodoroState,
        val settings: PomodoroSettings,
        val stopConfirmationPending: Boolean,
        val availability: Map<Int, Boolean>,
        val canPostLiveUpdates: Boolean,
    )

    companion object {
        const val ACTION_START = "cc.star0.wear.pomodoro.action.START"
        const val ACTION_PAUSE = "cc.star0.wear.pomodoro.action.PAUSE"
        const val ACTION_RESUME = "cc.star0.wear.pomodoro.action.RESUME"
        const val ACTION_STOP = "cc.star0.wear.pomodoro.action.STOP"
        const val ACTION_STOP_CONFIRMED = "cc.star0.wear.pomodoro.action.STOP_CONFIRMED"
        const val ACTION_PHASE_FINISHED = "cc.star0.wear.pomodoro.action.PHASE_FINISHED"
        const val ACTION_RESTORE = "cc.star0.wear.pomodoro.action.RESTORE"
        private const val STOP_CONFIRMATION_TIMEOUT_MILLIS = 10_000L
        private const val ACTION_REFRESH_NOTIFICATIONS = "cc.star0.wear.pomodoro.action.REFRESH_NOTIFICATIONS"

        fun refreshNotifications(context: Context) {
            // Refresh an existing service after returning from system notification settings.
            // A broadcast does not start a service or recreate notifications after Stop.
            context.sendBroadcast(Intent(ACTION_REFRESH_NOTIFICATIONS).setPackage(context.packageName))
        }

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
