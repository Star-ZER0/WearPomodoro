package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.R

@Composable
fun AboutScreen(
    onOpenUrl: (String) -> Unit,
    onShowDonationQr: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val appName = stringResource(R.string.app_name)
    val authorName = stringResource(R.string.author_name)
    val personalUrl = stringResource(R.string.personal_website_url)
    val projectUrl = stringResource(R.string.project_website_url)
    SettingsListLayout(title = stringResource(R.string.about_title), onEdgeBack = onNavigateBack) { transformationSpec ->
        item(key = "app") {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .transformedContent(this, transformationSpec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(CircleShape)
                        .background(colorResource(R.color.ic_launcher_background)),
                )
                Text(
                    text = stringResource(R.string.about_ai_assistance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item(key = "project_website") {
            SettingButton(
                label = stringResource(R.string.project_website_title),
                value = stringResource(R.string.project_website_label),
                onClick = { onOpenUrl(projectUrl) },
                transformationSpec = transformationSpec,
                showNavigateNext = true,
            )
        }
        item(key = "author") {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .transformedContent(this, transformationSpec)
                    .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.author_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Image(
                    painter = painterResource(R.drawable.star_zer0),
                    contentDescription = stringResource(R.string.author_avatar_description, authorName),
                    modifier = Modifier.size(88.dp).clip(CircleShape),
                )
                Text(
                    text = stringResource(R.string.author_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item(key = "personal_website") {
            SettingButton(
                label = stringResource(R.string.personal_website_title),
                value = stringResource(R.string.personal_website_label),
                onClick = { onOpenUrl(personalUrl) },
                transformationSpec = transformationSpec,
                showNavigateNext = true,
            )
        }
        item(key = "donation") {
            SettingButton(
                label = stringResource(R.string.donation_title),
                value = stringResource(R.string.donation_subtitle),
                onClick = onShowDonationQr,
                transformationSpec = transformationSpec,
                showNavigateNext = true,
            )
        }
    }
}
