package cc.star0.wear.pomodoro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import cc.star0.wear.pomodoro.PomodoroViewModel
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.permissions.AppPermissionReport
import cc.star0.wear.pomodoro.permissions.PermissionAction
import kotlinx.serialization.Serializable

@Serializable
private object HomeDestination : NavKey

@Serializable
private object PermissionsDestination : NavKey

@Serializable
private object AboutDestination : NavKey

@Serializable
private object DonationQrDestination : NavKey

@Serializable
private data class SettingEditorDestination(val kind: SettingKind) : NavKey

@Serializable
enum class SettingKind { Focus, ShortBreak, LongBreak, RoundsBeforeLongBreak }

@Composable
fun PomodoroApp(
    viewModel: PomodoroViewModel,
    permissionReport: AppPermissionReport,
    onPermissionAction: (PermissionAction) -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val settingsLoaded by viewModel.isInitialized.collectAsStateWithLifecycle()
    val settings = if (settingsLoaded) {
        // Begin collecting only after restore, so the first editable value is the saved one.
        val restoredSettings by viewModel.settings.collectAsStateWithLifecycle()
        restoredSettings
    } else {
        PomodoroSettings()
    }
    CompositionLocalProvider(LocalInteractionSettings provides settings) {
        MaterialTheme(colorScheme = ColorScheme()) {
            AppScaffold {
                val backStack = rememberNavBackStack(HomeDestination)
                val navigateBack: () -> Unit = {
                    if (backStack.size > 1) backStack.removeLastOrNull()
                }
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize().then(
                        if (settings.systemBackGestureEnabled) Modifier else Modifier.systemGestureExclusion(),
                    ),
                    onBack = navigateBack,
                    sceneStrategies = listOf(
                        rememberSwipeDismissableSceneStrategy(
                            // On API 36+, let the system-back guard below own platform back.
                            // A false scene flag would install a second no-op system handler.
                            isUserSwipeEnabled = !supportsIndependentSwipeBack || settings.composeSwipeBackEnabled,
                        ),
                    ),
                    entryProvider = entryProvider {
                        entry<HomeDestination> {
                            val pagerState = rememberPagerState(pageCount = { 3 })
                            HorizontalPagerScaffold(pagerState = pagerState) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    AnimatedPage(pageIndex = page, pagerState = pagerState) {
                                        when (page) {
                                            0 -> TimerScreen(viewModel)
                                            1 -> SettingsScreen(
                                                viewModel = viewModel,
                                                onEditSetting = { kind -> backStack.add(SettingEditorDestination(kind)) },
                                            )
                                            2 -> GeneralSettingsScreen(
                                                settings = settings,
                                                settingsLoaded = settingsLoaded,
                                                onSettingsChange = viewModel::updateSettings,
                                                permissionReport = permissionReport,
                                                onRefreshPermissions = onRefreshPermissions,
                                                onOpenPermissions = { backStack.add(PermissionsDestination) },
                                                onOpenAppSettings = { onPermissionAction(PermissionAction.AppSettings) },
                                                onAbout = { backStack.add(AboutDestination) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        entry<PermissionsDestination> {
                            PermissionsScreen(
                                report = permissionReport,
                                onPermissionAction = onPermissionAction,
                                onRefresh = onRefreshPermissions,
                                onNavigateBack = navigateBack,
                            )
                        }
                        entry<SettingEditorDestination> { destination ->
                            SettingEditorScreen(
                                viewModel = viewModel,
                                kind = destination.kind,
                                onDone = navigateBack,
                            )
                        }
                        entry<AboutDestination> {
                            AboutScreen(
                                onOpenUrl = onOpenUrl,
                                onShowDonationQr = { backStack.add(DonationQrDestination) },
                                onNavigateBack = navigateBack,
                            )
                        }
                        entry<DonationQrDestination> {
                            TipCodeScreen(onNavigateBack = navigateBack)
                        }
                    },
                )
                // Re-register after navigation changes so the system-back guard keeps priority.
                // Scene swipe and explicit cancel/save buttons call navigateBack directly.
                key(backStack.size, settings.systemBackGestureEnabled, settings.effectiveSwipeBackEnabled) {
                    BackHandler(enabled = !settings.systemBackGestureEnabled) {}
                }
            }
        }
    }
}
