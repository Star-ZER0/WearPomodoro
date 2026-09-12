package cc.star0.wear.pomodoro.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/** Compact spacing keeps the timer actions visible on small round screens. */
internal val ListItemSpacing = 4.dp

/** Keeps layout height and drawing in sync for list items without a Material surface. */
internal fun Modifier.transformedContent(
    itemScope: TransformingLazyColumnItemScope,
    transformationSpec: TransformationSpec,
): Modifier {
    val transformation = itemScope.SurfaceTransformation(transformationSpec)
    return this
        .graphicsLayer {
            with(transformation) { applyContainerTransformation() }
        }
        .transformedHeight(itemScope, transformationSpec)
        .graphicsLayer {
            with(transformation) { applyContentTransformation() }
            clip = true
        }
}
