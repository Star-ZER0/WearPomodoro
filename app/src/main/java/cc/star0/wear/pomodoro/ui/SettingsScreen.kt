package cc.star0.wear.pomodoro.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.PomodoroViewModel
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.text.formatMinutes

@StringRes
internal fun settingTitleRes(kind: SettingKind): Int = when (kind) {
    SettingKind.Focus -> R.string.setting_focus_duration
    SettingKind.ShortBreak -> R.string.setting_short_break_duration
    SettingKind.LongBreak -> R.string.setting_long_break_duration
    SettingKind.RoundsBeforeLongBreak -> R.string.setting_long_break_rounds
    SettingKind.SquareTimerCornerRadius -> R.string.setting_square_timer_corner_radius
}

internal data class SettingPalette(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val onAccent: Color,
)

@Composable
internal fun settingPalette(kind: SettingKind): SettingPalette {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        SettingKind.Focus -> SettingPalette(colors.primaryContainer, colors.onPrimaryContainer, colors.primary, colors.onPrimary)
        SettingKind.ShortBreak -> SettingPalette(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary, colors.onTertiary)
        SettingKind.LongBreak -> SettingPalette(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary, colors.onSecondary)
        SettingKind.RoundsBeforeLongBreak,
        SettingKind.SquareTimerCornerRadius -> SettingPalette(colors.surfaceContainerHigh, colors.onSurface, colors.primary, colors.onPrimary)
    }
}

@Composable
fun SettingsScreen(
    viewModel: PomodoroViewModel,
    onEditSetting: (SettingKind) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsListContent(settings, onEditSetting)
}

@Composable
fun SettingsListContent(
    settings: PomodoroSettings,
    onEditSetting: (SettingKind) -> Unit,
) {
    val resources = LocalResources.current
    SettingsListLayout(
        title = stringResource(R.string.timer_settings_title),
        screenStyle = settings.screenStyle,
    ) {
        for (kind in SettingKind.entries) {
            // Appearance is edited from General settings, not timer settings.
            if (kind == SettingKind.SquareTimerCornerRadius) continue
            item(key = kind.name) {
                val value = when (kind) {
                    SettingKind.Focus -> formatMinutes(resources, settings.focusDurationMillis)
                    SettingKind.ShortBreak -> formatMinutes(resources, settings.shortBreakDurationMillis)
                    SettingKind.LongBreak -> formatMinutes(resources, settings.longBreakDurationMillis)
                    SettingKind.SquareTimerCornerRadius -> stringResource(R.string.corner_radius_value, settings.squareTimerCornerRadiusDp)
                    SettingKind.RoundsBeforeLongBreak ->
                        if (settings.focusRoundsBeforeLongBreak == PomodoroSettings.NEVER_LONG_BREAK) {
                            stringResource(R.string.long_break_never_summary)
                        } else {
                            pluralStringResource(R.plurals.long_break_interval, settings.focusRoundsBeforeLongBreak, settings.focusRoundsBeforeLongBreak)
                        }
                }
                val palette = settingPalette(kind)
                SettingButton(
                    label = stringResource(settingTitleRes(kind)),
                    value = value,
                    onClick = { onEditSetting(kind) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = palette.container,
                        contentColor = palette.onContainer,
                        secondaryContentColor = palette.onContainer,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun SettingsListItemScope.SettingButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    enabled: Boolean = true,
    showNavigateNext: Boolean = false,
) {
    Button(
        onClick = onClick,
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(label)
                if (showNavigateNext) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.action_open),
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        },
        secondaryLabel = { Text(value) },
        colors = colors,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
            .transformedHeight()
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = transformation,
    )
}
