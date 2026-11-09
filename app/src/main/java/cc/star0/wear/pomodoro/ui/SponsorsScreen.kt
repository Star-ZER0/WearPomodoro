package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.data.loadSponsors

@Composable
fun SponsorsScreen(onNavigateBack: () -> Unit) {
    val resources = LocalResources.current
    val sponsors = remember(resources) { loadSponsors(resources) }
    SettingsListLayout(
        title = stringResource(R.string.sponsors_title),
        onEdgeBack = onNavigateBack,
        edgeBackDescription = R.string.action_back_to_about,
    ) {
        item(key = "thanks") {
            Text(
                text = stringResource(R.string.sponsors_thanks),
                modifier = Modifier.fillMaxWidth().transformedContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        sponsors.forEachIndexed { index, sponsor ->
            item(key = "sponsor_$index") {
                Column(
                    modifier = Modifier.fillMaxWidth().transformedContent()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(sponsor.username, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.sponsor_amount, sponsor.amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
