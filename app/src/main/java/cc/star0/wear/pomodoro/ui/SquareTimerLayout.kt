package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import cc.star0.wear.pomodoro.R

/** Keeps the round timer's visual hierarchy while using the full rectangular viewport. */
@Composable
internal fun SquareTimerLayout(
    phaseLabel: String,
    timeLabel: String,
    roundLabel: String,
    progress: () -> Float,
    accent: Color,
    modifier: Modifier = Modifier,
    controls: @Composable (Dp) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val shortSide = minOf(maxWidth, maxHeight)
        val buttonSize = (shortSide * 0.28f).coerceIn(48.dp, IconButtonDefaults.DefaultButtonSize)
        SquareTimerProgress(
            progress = progress,
            accent = accent,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(
                start = shortSide * 0.12f,
                end = shortSide * 0.12f,
                top = maxHeight * 0.15f,
                bottom = maxHeight * 0.12f,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            FittedText(
                text = phaseLabel,
                modifier = Modifier.fillMaxWidth().height(shortSide * 0.11f),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            FittedText(
                text = timeLabel,
                modifier = Modifier.fillMaxWidth().height(shortSide * 0.22f),
                style = MaterialTheme.typography.numeralLarge,
            )
            controls(buttonSize)
            FittedText(
                text = roundLabel,
                modifier = Modifier.fillMaxWidth().height(shortSide * 0.09f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SquareTimerProgress(
    progress: () -> Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.timer_progress_description)
    val trackColor = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier = modifier.semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
        }.drawWithCache {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val left = inset
            val top = inset
            val right = size.width - inset
            val bottom = size.height - inset
            val radius = minOf(24.dp.toPx(), (right - left) / 4f, (bottom - top) / 4f)
            val centerX = size.width / 2f
            // The open top leaves room for the same system clock used by the round timer.
            val halfGap = minOf(36.dp.toPx(), (right - left) / 2f - radius)
            val track = Path().apply {
                moveTo(centerX + halfGap, top)
                lineTo(right - radius, top)
                arcTo(Rect(right - 2 * radius, top, right, top + 2 * radius), -90f, 90f, false)
                lineTo(right, bottom - radius)
                arcTo(Rect(right - 2 * radius, bottom - 2 * radius, right, bottom), 0f, 90f, false)
                lineTo(left + radius, bottom)
                arcTo(Rect(left, bottom - 2 * radius, left + 2 * radius, bottom), 90f, 90f, false)
                lineTo(left, top + radius)
                arcTo(Rect(left, top, left + 2 * radius, top + 2 * radius), 180f, 90f, false)
                lineTo(centerX - halfGap, top)
            }
            val measure = PathMeasure().apply { setPath(track, false) }
            val indicator = Path()
            onDrawBehind {
                drawPath(track, trackColor, style = stroke)
                // Read during drawing so each timer tick reuses the cached outline.
                val fraction = progress().coerceIn(0f, 1f)
                if (fraction > 0f) {
                    indicator.rewind()
                    measure.getSegment(0f, measure.length * fraction, indicator)
                    drawPath(indicator, accent, style = stroke)
                }
            }
        },
    )
}
