package cc.star0.wear.pomodoro.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.ScreenStyle

@StringRes
internal fun screenStyleTitleRes(style: ScreenStyle): Int = when (style) {
    ScreenStyle.Round -> R.string.screen_style_round
    ScreenStyle.Square -> R.string.screen_style_square
}

@Composable
fun ScreenStyleScreen(
    settings: PomodoroSettings,
    settingsLoaded: Boolean,
    onSettingsChange: (PomodoroSettings) -> Unit,
    onNavigateBack: () -> Unit,
) {
    SettingsListLayout(
        title = stringResource(R.string.setting_screen_style),
        onEdgeBack = onNavigateBack,
        modifier = Modifier.selectableGroup(),
        screenStyle = settings.screenStyle,
    ) {
        for (style in ScreenStyle.entries) {
            item(key = style.name) {
                RadioButton(
                    selected = settings.screenStyle == style,
                    onSelect = { onSettingsChange(settings.copy(screenStyle = style)) },
                    enabled = settingsLoaded,
                    label = { Text(stringResource(screenStyleTitleRes(style))) },
                    modifier = Modifier.fillMaxWidth().transformedHeight()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = transformation,
                )
            }
        }
    }
}
