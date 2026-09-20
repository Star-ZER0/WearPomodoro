package cc.star0.wear.pomodoro.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import cc.star0.wear.pomodoro.MainActivity
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.NotificationStyle
import cc.star0.wear.pomodoro.model.PomodoroPhase
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.PomodoroState
import cc.star0.wear.pomodoro.text.durationInMinutes
import cc.star0.wear.pomodoro.text.formatDuration
import cc.star0.wear.pomodoro.text.formatRoundLabel
import cc.star0.wear.pomodoro.timer.PomodoroService

object PomodoroNotifications {
    const val ONGOING_NOTIFICATION_ID = 100
    const val LIVE_UPDATE_NOTIFICATION_ID = 101
    const val STANDARD_NOTIFICATION_ID = 102
    const val REMINDER_NOTIFICATION_ID = 200

    private val timerNotificationIds = setOf(ONGOING_NOTIFICATION_ID, LIVE_UPDATE_NOTIFICATION_ID, STANDARD_NOTIFICATION_ID)

    private const val ONGOING_CHANNEL_ID = "pomodoro_ongoing"
    private const val LIVE_UPDATE_CHANNEL_ID = "pomodoro_live_update"
    private const val STANDARD_CHANNEL_ID = "pomodoro_standard"
    private const val REMINDER_CHANNEL_ID = "pomodoro_reminder"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val timerChannels = NotificationStyle.entries.map { style ->
            NotificationChannel(
                channelId(style),
                context.getString(when (style) {
                    NotificationStyle.LiveUpdate -> R.string.setting_live_updates
                    NotificationStyle.OngoingActivity -> R.string.setting_ongoing_activity
                    NotificationStyle.Standard -> R.string.notification_style_standard
                }),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.timer_channel_description)
                setShowBadge(false)
                setSound(null, null)
            }
        }
        val reminder = NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannels(timerChannels + reminder)
    }

    private fun channelId(style: NotificationStyle): String = when (style) {
        NotificationStyle.LiveUpdate -> LIVE_UPDATE_CHANNEL_ID
        NotificationStyle.OngoingActivity -> ONGOING_CHANNEL_ID
        NotificationStyle.Standard -> STANDARD_CHANNEL_ID
    }

    private fun notificationId(style: NotificationStyle): Int = when (style) {
        NotificationStyle.LiveUpdate -> LIVE_UPDATE_NOTIFICATION_ID
        NotificationStyle.OngoingActivity -> ONGOING_NOTIFICATION_ID
        NotificationStyle.Standard -> STANDARD_NOTIFICATION_ID
    }

    // The first entry hosts the foreground service. Each other entry is posted separately.
    fun buildTimerNotifications(
        context: Context,
        state: PomodoroState,
        nowElapsedMillis: Long,
        settings: PomodoroSettings,
        stopConfirmationPending: Boolean = false,
        canPostLiveUpdates: Boolean = canPostLiveUpdates(context),
    ): Map<Int, Notification> {
        val styles = listOf(NotificationStyle.Standard, NotificationStyle.OngoingActivity, NotificationStyle.LiveUpdate)
            .filter(settings::isNotificationEnabled)
            // Wear can retain the old activity when an existing notification merely loses its
            // extension. Remove its notification entirely while paused/ready, as the API requires.
            .filter { it != NotificationStyle.OngoingActivity || state.hasActiveTimer() }
            // Keep service controls available when the only enabled display is temporarily absent.
            .ifEmpty { listOf(NotificationStyle.Standard) }
        return styles.associate { style ->
            notificationId(style) to buildOngoingNotification(
                context, state, nowElapsedMillis, stopConfirmationPending, style,
                canPostLiveUpdates = canPostLiveUpdates,
                serviceOnly = NotificationStyle.entries.none(settings::isNotificationEnabled),
            )
        }
    }

    fun buildOngoingNotification(
        context: Context,
        state: PomodoroState,
        nowElapsedMillis: Long,
        stopConfirmationPending: Boolean = false,
        style: NotificationStyle = NotificationStyle.OngoingActivity,
        canPostLiveUpdates: Boolean = canPostLiveUpdates(context),
        serviceOnly: Boolean = false,
    ): Notification {
        val remaining = state.remainingMillis(nowElapsedMillis)
        val resources = context.resources
        val phaseLabel = context.getString(
            if (state.phase == PomodoroPhase.ReadyToFocus) R.string.notification_ready_text else R.string.notification_running_text,
        )
        val roundLabel = formatRoundLabel(resources, state.completedFocusRounds + 1, state.effectiveSettings.focusRoundsBeforeLongBreak)
        val title = when (state.phase) {
            PomodoroPhase.ReadyToFocus -> context.getString(R.string.phase_ready)
            PomodoroPhase.Focus -> context.getString(R.string.notification_focus_title, roundLabel)
            PomodoroPhase.ShortBreak -> context.getString(R.string.phase_short_break)
            PomodoroPhase.LongBreak -> context.getString(R.string.phase_long_break)
        }
        val contentText = if (state.isRunning || state.phase == PomodoroPhase.ReadyToFocus) {
            phaseLabel
        } else {
            context.getString(R.string.notification_paused_text, formatDuration(resources, remaining))
        }

        val timerIntent = contentIntent(context)
        val builder = NotificationCompat.Builder(context, channelId(style))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon))
            .setContentTitle(title)
            .setContentText(
                if (stopConfirmationPending) context.getString(R.string.notification_stop_confirmation, contentText) else contentText,
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(timerIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state.isRunning) {
            builder
                .setWhen(System.currentTimeMillis() + remaining)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(0, context.getString(R.string.action_pause), servicePendingIntent(context, PomodoroService.ACTION_PAUSE))
        } else {
            val continueAction = if (state.phase == PomodoroPhase.ReadyToFocus) {
                PomodoroService.ACTION_START
            } else {
                PomodoroService.ACTION_RESUME
            }
            builder
                .setWhen(0)
                .setShowWhen(false)
                .setUsesChronometer(false)
                .addAction(
                    0,
                    context.getString(if (state.phase == PomodoroPhase.ReadyToFocus) R.string.action_start else R.string.action_continue),
                    servicePendingIntent(context, continueAction),
                )
        }
        builder.addAction(
            0,
            context.getString(if (stopConfirmationPending) R.string.action_confirm_stop else R.string.action_stop),
            servicePendingIntent(context, PomodoroService.ACTION_STOP),
        )

        val deadline = state.endAtElapsedMillis
        val activeTimer = !serviceOnly && state.hasActiveTimer()
        // Wear SysUI has crashed on duplicate entries when both APIs share one notification.
        // Keep their IDs, channels, and payloads separate even when both switches are enabled.
        val useLiveUpdate = activeTimer && style == NotificationStyle.LiveUpdate && canPostLiveUpdates
        builder.setRequestPromotedOngoing(useLiveUpdate)
        if (activeTimer && style == NotificationStyle.OngoingActivity) {
            val status = Status.Builder()
                .addTemplate(context.getString(R.string.ongoing_activity_status))
                .addPart(
                    "phase",
                    Status.TextPart(
                        context.getString(
                            when (state.phase) {
                                PomodoroPhase.Focus -> R.string.phase_focus
                                PomodoroPhase.ShortBreak -> R.string.phase_short_break
                                PomodoroPhase.LongBreak -> R.string.phase_long_break
                                PomodoroPhase.ReadyToFocus -> R.string.phase_ready
                            },
                        ),
                    ),
                )
                // Wear renders the countdown itself, using the same monotonic deadline as the timer.
                .addPart("time", Status.TimerPart(requireNotNull(deadline), -1L, state.totalMillis()))
                .build()
            OngoingActivity.Builder(context, ONGOING_NOTIFICATION_ID, builder)
                .setOngoingActivityId(ONGOING_NOTIFICATION_ID)
                .setAnimatedIcon(R.drawable.ic_ongoing_activity_animated)
                .setStaticIcon(R.drawable.ic_ongoing_activity)
                .setTitle(context.getString(R.string.app_name))
                .setContentDescription(context.getString(R.string.ongoing_activity_open_timer))
                .setTouchIntent(timerIntent)
                .setStatus(status)
                .build()
                .apply(context)
        }
        // Phase, actions, and countdown change together, so replace the full notification at
        // the same ID. buildTimerNotifications omits this ID entirely when the activity ends.
        if (serviceOnly) {
            builder.setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.timer_service_notification))
                .setUsesChronometer(false)
                .setShowWhen(false)
                .clearActions()
        }
        return builder.build()
    }

    fun updateAdditionalNotifications(context: Context, notifications: Map<Int, Notification>) {
        val manager = NotificationManagerCompat.from(context)
        val foregroundId = notifications.keys.first()
        if (
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()
        ) {
            notifications.forEach { (id, notification) ->
                if (id != foregroundId) manager.notify(id, notification)
            }
        }
        timerNotificationIds.filter { it !in notifications }.forEach(manager::cancel)
    }

    fun cancelTimerNotifications(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        timerNotificationIds.forEach(manager::cancel)
    }

    internal fun canPostLiveUpdates(context: Context): Boolean = Build.VERSION.SDK_INT >= 37 &&
        context.getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == true

    internal fun notificationAvailability(context: Context): Map<Int, Boolean> {
        val manager = NotificationManagerCompat.from(context)
        val allowed = manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        return NotificationStyle.entries.associate { style ->
            notificationId(style) to (allowed && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                manager.getNotificationChannel(channelId(style))?.importance != NotificationManager.IMPORTANCE_NONE))
        }
    }

    private fun PomodoroState.hasActiveTimer(): Boolean =
        isRunning && phase != PomodoroPhase.ReadyToFocus && endAtElapsedMillis != null

    fun notifyPhaseFinished(
        context: Context,
        oldState: PomodoroState,
        newState: PomodoroState,
    ) {
        val settings = newState.effectiveSettings
        val title: String
        val text: String
        if (oldState.phase == PomodoroPhase.Focus) {
            val isLongBreak = newState.phase == PomodoroPhase.LongBreak
            title = context.getString(if (isLongBreak) R.string.notification_long_break_started else R.string.notification_short_break_started)
            val minutes = durationInMinutes(if (isLongBreak) settings.longBreakDurationMillis else settings.shortBreakDurationMillis)
            text = context.resources.getQuantityString(R.plurals.notification_break_duration, minutes, minutes)
        } else {
            title = context.getString(R.string.notification_break_finished)
            text = context.getString(R.string.notification_next_focus, newState.completedFocusRounds + 1)
        }

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon))
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent(context))
            .build()

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TIMER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, PomodoroService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

}
