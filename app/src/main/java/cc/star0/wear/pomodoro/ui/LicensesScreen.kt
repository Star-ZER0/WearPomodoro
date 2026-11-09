package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.data.PROJECT_LICENSE_ID
import cc.star0.wear.pomodoro.data.loadLicenseComponents
import cc.star0.wear.pomodoro.text.licenseTextBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun OpenSourceLicensesScreen(onOpenLicense: (String) -> Unit, onNavigateBack: () -> Unit) {
    val resources = LocalResources.current
    val components = remember(resources) { loadLicenseComponents(resources) }
    SettingsListLayout(
        title = stringResource(R.string.open_source_licenses_title),
        onEdgeBack = onNavigateBack,
        edgeBackDescription = R.string.action_back_to_about,
    ) {
        for (component in components.filter { it.id != PROJECT_LICENSE_ID }) {
            item(key = component.id) {
                SettingButton(
                    label = component.name,
                    value = component.license,
                    onClick = { onOpenLicense(component.id) },
                    showNavigateNext = true,
                )
            }
        }
    }
}

private sealed interface LicenseTextState {
    data object Loading : LicenseTextState
    data object Unavailable : LicenseTextState
    data class Ready(val blocks: List<String>) : LicenseTextState
}

@Composable
fun LicenseDetailScreen(componentId: String, onNavigateBack: () -> Unit) {
    val resources = LocalResources.current
    val component = remember(resources, componentId) {
        loadLicenseComponents(resources).firstOrNull { it.id == componentId }
    }
    val document by produceState<LicenseTextState>(LicenseTextState.Loading, resources, componentId) {
        value = LicenseTextState.Loading
        value = withContext(Dispatchers.IO) {
            if (component == null) {
                LicenseTextState.Unavailable
            } else {
                try {
                    resources.assets.open("licenses/${component.file}").bufferedReader(Charsets.UTF_8).use {
                        LicenseTextState.Ready(licenseTextBlocks(it.readText()))
                    }
                } catch (_: IOException) {
                    LicenseTextState.Unavailable
                }
            }
        }
    }
    val isProject = componentId == PROJECT_LICENSE_ID
    SettingsListLayout(
        title = if (isProject) stringResource(R.string.project_license_title)
            else component?.name ?: stringResource(R.string.open_source_licenses_title),
        onEdgeBack = onNavigateBack,
        edgeBackDescription = if (isProject) R.string.action_back_to_about else R.string.action_back_to_licenses,
    ) {
        component?.let {
            item(key = "license_name") {
                Text(
                    text = it.license,
                    modifier = Modifier.fillMaxWidth().transformedContent(),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        when (val state = document) {
            LicenseTextState.Loading, LicenseTextState.Unavailable -> item(key = "status") {
                Text(
                    text = stringResource(
                        if (state == LicenseTextState.Loading) R.string.license_loading else R.string.license_unavailable,
                    ),
                    modifier = Modifier.fillMaxWidth().transformedContent(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            is LicenseTextState.Ready -> state.blocks.forEachIndexed { index, block ->
                item(key = "text_$index") {
                    Text(
                        text = block,
                        modifier = Modifier.fillMaxWidth().transformedContent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
        }
    }
}
