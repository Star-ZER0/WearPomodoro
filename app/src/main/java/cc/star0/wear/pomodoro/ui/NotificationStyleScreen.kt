package cc.star0.wear.pomodoro.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.NotificationStyle
import cc.star0.wear.pomodoro.model.PomodoroSettings

@StringRes
internal fun notificationStyleTitleRes(style: NotificationStyle): Int = when (style) {
    NotificationStyle.LiveUpdate -> R.string.setting_live_updates
    NotificationStyle.OngoingActivity -> R.string.setting_ongoing_activity
    NotificationStyle.Standard -> R.string.notification_style_standard
}

@Composable
fun NotificationStyleScreen(
    settings: PomodoroSettings,
    settingsLoaded: Boolean,
    onSettingsChange: (PomodoroSettings) -> Unit,
    onNavigateBack: () -> Unit,
) {
    SettingsListLayout(
        title = stringResource(R.string.setting_notification_style),
        onEdgeBack = onNavigateBack,
        screenStyle = settings.screenStyle,
    ) {
        for (style in NotificationStyle.entries) {
            item(key = style.name) {
                SwitchButton(
                    checked = settings.isNotificationEnabled(style),
                    onCheckedChange = { onSettingsChange(settings.withNotificationEnabled(style, it)) },
                    enabled = settingsLoaded,
                    label = { Text(stringResource(notificationStyleTitleRes(style))) },
                    secondaryLabel = {
                        Text(stringResource(when (style) {
                            NotificationStyle.LiveUpdate -> R.string.notification_live_updates_summary
                            NotificationStyle.OngoingActivity -> R.string.notification_ongoing_activity_summary
                            NotificationStyle.Standard -> R.string.notification_standard_summary
                        }))
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = transformation,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.notification_channels_summary),
                modifier = Modifier.fillMaxWidth().transformedContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
