package cc.star0.wear.pomodoro.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.material3.PageIndicatorDefaults
import androidx.wear.compose.material3.ScrollIndicatorDefaults
import kotlin.math.floor

/** Uses Wear's indicator colors, rounded ends, gaps and motion on a straight track. */
@Composable
internal fun SquareScrollIndicator(state: LazyListState, modifier: Modifier = Modifier) {
    val colors = ScrollIndicatorDefaults.colors()
    val strokeWidth = if (LocalConfiguration.current.screenWidthDp >= 225) 6.dp else 5.dp
    val metrics by remember(state) {
        derivedStateOf {
            val indicator = state.scrollIndicatorState
            val contentSize = indicator?.contentSize ?: 0
            val viewportSize = indicator?.viewportSize ?: 0
            val offset = indicator?.scrollOffset ?: 0
            if ((!state.canScrollBackward && !state.canScrollForward) ||
                contentSize <= viewportSize || viewportSize <= 0 ||
                contentSize == Int.MAX_VALUE || viewportSize == Int.MAX_VALUE || offset == Int.MAX_VALUE
            ) {
                null
            } else {
                val position = when {
                    !state.canScrollBackward -> 0f
                    !state.canScrollForward -> 1f
                    else -> (offset.toFloat() / (contentSize - viewportSize)).coerceIn(0f, 1f)
                }
                position to (viewportSize.toFloat() / contentSize).coerceIn(0.3f, 0.7f)
            }
        }
    }
    val currentMetrics = metrics ?: return
    val position by animateFloatAsState(
        targetValue = currentMetrics.first,
        animationSpec = ScrollIndicatorDefaults.PositionAnimationSpec,
        label = "squareScrollPosition",
    )
    val fraction by animateFloatAsState(
        targetValue = currentMetrics.second,
        animationSpec = ScrollIndicatorDefaults.PositionAnimationSpec,
        label = "squareScrollSize",
    )
    Canvas(modifier.size(width = strokeWidth, height = 50.dp + strokeWidth)) {
        val stroke = strokeWidth.toPx()
        val top = stroke / 2f
        val bottom = size.height - top
        val trackLength = bottom - top
        val thumbStart = top + position * (1f - fraction) * trackLength
        val thumbEnd = thumbStart + fraction * trackLength
        // Include both rounded caps in the same 3dp visible gap used by Wear's arc.
        val gap = 3.dp.toPx() + stroke
        if (thumbStart - gap > top) {
            drawLine(colors.trackColor, Offset(center.x, top), Offset(center.x, thumbStart - gap), stroke, StrokeCap.Round)
        }
        if (thumbEnd + gap < bottom) {
            drawLine(colors.trackColor, Offset(center.x, thumbEnd + gap), Offset(center.x, bottom), stroke, StrokeCap.Round)
        }
        drawLine(colors.indicatorColor, Offset(center.x, thumbStart), Offset(center.x, thumbEnd), stroke, StrokeCap.Round)
    }
}

/** The home pager's three Wear-style dots and stretching selection, laid out horizontally. */
@Composable
internal fun SquarePageIndicator(state: PagerState, modifier: Modifier = Modifier) {
    val pageCount = state.pageCount
    if (pageCount <= 1) return
    val selectedColor = PageIndicatorDefaults.selectedColor
    val unselectedColor = PageIndicatorDefaults.unselectedColor
    val backgroundColor = PageIndicatorDefaults.backgroundColor
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Match Wear's 6dp dots, 4dp spacing and 3dp background padding.
    Canvas(modifier.size(width = (pageCount * 10 + 2).dp, height = 12.dp)) {
        val radius = 3.dp.toPx()
        val step = 10.dp.toPx()
        fun pageX(page: Float): Float {
            val x = 6.dp.toPx() + page * step
            return if (isRtl) size.width - x else x
        }
        drawRoundRect(backgroundColor, cornerRadius = CornerRadius(size.height / 2f))
        repeat(pageCount) { page ->
            drawCircle(unselectedColor, radius, Offset(pageX(page.toFloat()), center.y))
        }
        // Reading the pager in the draw phase follows the swipe without recomposing the pages.
        val pagePosition = (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, pageCount - 1f)
        val page = floor(pagePosition)
        val offset = pagePosition - page
        val start = pageX(page + (offset * 2f - 1f).coerceIn(0f, 1f))
        val end = pageX(page + (offset * 2f).coerceIn(0f, 1f))
        drawRoundRect(
            color = selectedColor,
            topLeft = Offset(minOf(start, end) - radius, center.y - radius),
            size = Size(kotlin.math.abs(end - start) + radius * 2f, radius * 2f),
            cornerRadius = CornerRadius(radius),
        )
    }
}
