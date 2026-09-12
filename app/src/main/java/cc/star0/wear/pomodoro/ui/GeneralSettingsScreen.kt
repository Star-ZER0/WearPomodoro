package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.permissions.AppPermissionReport

@Composable
fun GeneralSettingsScreen(
    settings: PomodoroSettings,
    onSettingsChange: (PomodoroSettings) -> Unit,
    permissionReport: AppPermissionReport,
    onRefreshPermissions: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAbout: () -> Unit = {},
    settingsLoaded: Boolean = true,
) {
    LaunchedEffect(Unit) { onRefreshPermissions() }
    SettingsListLayout(title = stringResource(R.string.general_settings_title)) { transformationSpec ->
        item {
            SwitchButton(
                checked = settings.systemBackGestureEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(systemBackGestureEnabled = it)) },
                enabled = settingsLoaded,
                label = { Text(stringResource(R.string.setting_system_back)) },
                secondaryLabel = {
                    Text(stringResource(if (settings.systemBackGestureEnabled) R.string.system_back_enabled else R.string.system_back_disabled))
                },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
        item {
            SwitchButton(
                checked = settings.effectiveSwipeBackEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(composeSwipeBackEnabled = it)) },
                enabled = settingsLoaded && supportsIndependentSwipeBack,
                label = { Text(stringResource(R.string.setting_swipe_back)) },
                secondaryLabel = {
                    Text(stringResource(
                        if (supportsIndependentSwipeBack) R.string.swipe_back_summary
                        else R.string.swipe_back_system_controlled_summary,
                    ))
                },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
        item {
            SettingButton(
                label = stringResource(R.string.permissions_title),
                value = when {
                    !permissionReport.isLoaded -> stringResource(R.string.permissions_loading)
                    permissionReport.missingCount == 0 -> stringResource(R.string.permissions_all_granted)
                    else -> pluralStringResource(R.plurals.permissions_missing_count, permissionReport.missingCount, permissionReport.missingCount)
                },
                onClick = onOpenPermissions,
                transformationSpec = transformationSpec,
            )
        }
        item {
            SettingButton(
                label = stringResource(R.string.system_app_settings_title),
                value = stringResource(R.string.system_app_settings_summary),
                onClick = onOpenAppSettings,
                transformationSpec = transformationSpec,
            )
        }
        item {
            Text(
                text = if (permissionReport.versionName.isEmpty()) {
                    stringResource(R.string.app_name)
                } else {
                    stringResource(R.string.app_version, stringResource(R.string.app_name), permissionReport.versionName)
                },
                modifier = Modifier.fillMaxWidth().transformedContent(this, transformationSpec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        item {
            SettingButton(
                label = stringResource(R.string.about_app_title),
                value = stringResource(R.string.author_credit),
                onClick = onAbout,
                transformationSpec = transformationSpec,
            )
        }
    }
}
