package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.wear.compose.material3.MaterialTheme

/** Fits short clock and control labels inside the round display, including large system fonts. */
@Composable
internal fun FittedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = TextAlign.Center, lineHeight = TextUnit.Unspecified),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MaterialTheme.typography.bodyExtraSmall.fontSize,
            maxFontSize = style.fontSize,
        ),
    )
}
