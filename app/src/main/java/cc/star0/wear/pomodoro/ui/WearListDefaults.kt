package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.touchTargetAwareSize
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.ScreenStyle

internal val LocalScreenStyle = compositionLocalOf { ScreenStyle.Round }

/** Compact spacing for settings lists on small watch screens. */
internal val ListItemSpacing = 4.dp

/** Shares item content while each list retains its own layout and scrolling implementation. */
internal class SettingsListScope(
    private val addItem: (Any?, @Composable SettingsListItemScope.() -> Unit) -> Unit,
) {
    fun item(key: Any? = null, content: @Composable SettingsListItemScope.() -> Unit) {
        addItem(key, content)
    }
}

/** Round lists transform their items; square lists leave height and drawing unchanged. */
internal class SettingsListItemScope(
    private val roundScope: TransformingLazyColumnItemScope? = null,
    private val transformationSpec: TransformationSpec? = null,
) {
    val transformation: SurfaceTransformation? = if (roundScope != null && transformationSpec != null) {
        roundScope.SurfaceTransformation(transformationSpec)
    } else {
        null
    }

    fun Modifier.transformedHeight(): Modifier =
        if (roundScope != null && transformationSpec != null) {
            transformedHeight(roundScope, transformationSpec)
        } else {
            this
        }

    fun Modifier.minimumVerticalContentPadding(padding: Dp): Modifier =
        if (roundScope != null) {
            with(roundScope) { minimumVerticalContentPadding(padding) }
        } else {
            this
        }

    /** Keeps layout height and drawing in sync for items without a Material surface. */
    fun Modifier.transformedContent(): Modifier {
        val surfaceTransformation = transformation ?: return this
        return graphicsLayer {
            with(surfaceTransformation) { applyContainerTransformation() }
        }.transformedHeight().graphicsLayer {
            with(surfaceTransformation) { applyContentTransformation() }
            clip = true
        }
    }
}

@Composable
internal fun SettingsListLayout(
    title: String,
    onEdgeBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    screenStyle: ScreenStyle = LocalScreenStyle.current,
    content: SettingsListScope.() -> Unit,
) {
    // Keep both saveable states so toggling styles does not discard either list's position.
    val roundState = rememberTransformingLazyColumnState()
    val squareState = rememberLazyListState()
    val isRound = screenStyle == ScreenStyle.Round
    val scrollState = if (isRound) roundState else squareState
    val scrollInfoProvider = if (isRound) ScrollInfoProvider(roundState) else ScrollInfoProvider(squareState)
    val transformationSpec = rememberTransformationSpec()
    val overscrollEffect = rememberOverscrollEffect()
    val focusRequester = remember { FocusRequester() }
    val scrollIndicator: @Composable BoxScope.() -> Unit = {
        if (!LocalScrollCaptureInProgress.current) {
            if (isRound) {
                ScrollIndicator(roundState)
            } else {
                SquareScrollIndicator(
                    state = squareState,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                )
            }
        }
    }
    val listItems: SettingsListScope.() -> Unit = {
        item {
            ListHeader(
                modifier = Modifier.fillMaxWidth().transformedHeight()
                    .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                transformation = transformation,
            ) { Text(title) }
        }
        content()
    }
    val listContent: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        if (isRound) {
            TransformingLazyColumn(
                state = roundState,
                contentPadding = contentPadding,
                modifier = modifier.fillMaxSize(),
                overscrollEffect = overscrollEffect,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                val roundListScope = this
                SettingsListScope { key, itemContent ->
                    roundListScope.item(key = key) {
                        SettingsListItemScope(this, transformationSpec).itemContent()
                    }
                }.listItems()
            }
        } else {
            LazyColumn(
                state = squareState,
                contentPadding = contentPadding,
                modifier = modifier.fillMaxSize()
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(squareState),
                        focusRequester = focusRequester,
                        overscrollEffect = overscrollEffect,
                    ),
                overscrollEffect = overscrollEffect,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                val squareListScope = this
                SettingsListScope { key, itemContent ->
                    squareListScope.item(key = key) { SettingsListItemScope().itemContent() }
                }.listItems()
                if (onEdgeBack != null) {
                    // A regular footer keeps the square-screen button circular while scrolling.
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FilledIconButton(
                                onClick = onEdgeBack,
                                modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.SmallButtonSize),
                                shapes = IconButtonDefaults.shapes(CircleShape),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back_to_general_settings),
                                    modifier = Modifier.size(IconButtonDefaults.SmallIconSize),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (isRound && onEdgeBack != null) {
        ScreenScaffold(
            scrollInfoProvider = scrollInfoProvider,
            scrollIndicator = scrollIndicator,
            overscrollEffect = overscrollEffect,
            edgeButton = {
                EdgeButton(
                    onClick = onEdgeBack,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.scrollable(
                        state = scrollState,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = overscrollEffect,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back_to_general_settings),
                        modifier = Modifier.size(EdgeButtonDefaults.SmallIconSize),
                    )
                }
            },
            content = listContent,
        )
    } else {
        ScreenScaffold(
            scrollInfoProvider = scrollInfoProvider,
            scrollIndicator = scrollIndicator,
            overscrollEffect = overscrollEffect,
            content = listContent,
        )
    }
}
