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
import cc.star0.wear.pomodoro.MainActivity
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroPhase
import cc.star0.wear.pomodoro.model.PomodoroState
import cc.star0.wear.pomodoro.text.durationInMinutes
import cc.star0.wear.pomodoro.text.formatDuration
import cc.star0.wear.pomodoro.text.formatRoundLabel
import cc.star0.wear.pomodoro.timer.PomodoroService

object PomodoroNotifications {
    const val ONGOING_NOTIFICATION_ID = 100
    const val REMINDER_NOTIFICATION_ID = 200

    private const val ONGOING_CHANNEL_ID = "pomodoro_ongoing"
    private const val REMINDER_CHANNEL_ID = "pomodoro_reminder"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val ongoing = NotificationChannel(
            ONGOING_CHANNEL_ID,
            context.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.timer_channel_description)
            setShowBadge(false)
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
        manager.createNotificationChannels(listOf(ongoing, reminder))
    }

    fun buildOngoingNotification(
        context: Context,
        state: PomodoroState,
        nowElapsedMillis: Long,
        stopConfirmationPending: Boolean = false,
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

        val builder = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(
                if (stopConfirmationPending) context.getString(R.string.notification_stop_confirmation, contentText) else contentText,
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent(context))

        if (state.isRunning) {
            builder
                .setWhen(System.currentTimeMillis() + remaining)
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
                .setUsesChronometer(false)
                .addAction(0, context.getString(R.string.action_continue), servicePendingIntent(context, continueAction))
        }
        builder.addAction(
            0,
            context.getString(if (stopConfirmationPending) R.string.action_confirm_stop else R.string.action_stop),
            servicePendingIntent(context, PomodoroService.ACTION_STOP),
        )
        return builder.build()
    }

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
