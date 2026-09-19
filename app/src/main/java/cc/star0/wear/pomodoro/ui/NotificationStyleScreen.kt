package cc.star0.wear.pomodoro.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.NotificationStyle

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
        modifier = Modifier.selectableGroup(),
        screenStyle = settings.screenStyle,
    ) {
        for (style in NotificationStyle.entries) {
            item(key = style.name) {
                RadioButton(
                    selected = settings.notificationStyle == style,
                    onSelect = { onSettingsChange(settings.copy(notificationStyle = style)) },
                    enabled = settingsLoaded,
                    label = { Text(stringResource(notificationStyleTitleRes(style))) },
                    modifier = Modifier.fillMaxWidth().transformedHeight()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = transformation,
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.activity_display_exclusivity),
                modifier = Modifier.fillMaxWidth().transformedContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
