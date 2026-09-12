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
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.permissions.AppPermissionReport
import cc.star0.wear.pomodoro.permissions.PermissionAction
import cc.star0.wear.pomodoro.permissions.PermissionStatus

@Composable
fun PermissionsScreen(
    report: AppPermissionReport,
    onPermissionAction: (PermissionAction) -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }
    SettingsListLayout(title = stringResource(R.string.permissions_title), onEdgeBack = onNavigateBack) { transformationSpec ->
        item {
            Text(
                text = if (report.isLoaded) {
                    stringResource(
                        R.string.permissions_summary,
                        pluralStringResource(R.plurals.permissions_granted_count, report.grantedCount, report.grantedCount),
                        pluralStringResource(R.plurals.permissions_pending_count, report.missingCount, report.missingCount),
                    )
                } else {
                    stringResource(R.string.permissions_loading)
                },
                modifier = Modifier.fillMaxWidth().transformedContent(this, transformationSpec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        item {
            SettingButton(
                label = stringResource(R.string.permissions_refresh),
                value = stringResource(R.string.permissions_refresh_summary),
                onClick = onRefresh,
                transformationSpec = transformationSpec,
            )
        }
        for (status in listOf(PermissionStatus.Missing, PermissionStatus.Granted, PermissionStatus.NotRequired)) {
            val permissions = report.permissions.filter { it.status == status }
            if (permissions.isNotEmpty()) {
                val statusLabelRes = when (status) {
                    PermissionStatus.Missing -> R.string.permission_status_missing
                    PermissionStatus.Granted -> R.string.permission_status_granted
                    PermissionStatus.NotRequired -> R.string.permission_status_not_required
                }
                item(key = status.name) {
                    Text(
                        text = stringResource(statusLabelRes),
                        modifier = Modifier.fillMaxWidth().transformedContent(this, transformationSpec),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = if (status == PermissionStatus.Missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (permission in permissions) {
                    item(key = permission.name) {
                        SettingButton(
                            label = permission.title,
                            value = stringResource(R.string.permission_status_description, stringResource(statusLabelRes), permission.description),
                            onClick = { onPermissionAction(permission.action) },
                            enabled = status != PermissionStatus.NotRequired,
                            transformationSpec = transformationSpec,
                            colors = if (status == PermissionStatus.Missing) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                        )
                    }
                }
            }
        }
    }
}
