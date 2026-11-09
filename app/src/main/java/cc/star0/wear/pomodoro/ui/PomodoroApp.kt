package cc.star0.wear.pomodoro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import cc.star0.wear.pomodoro.PomodoroViewModel
import cc.star0.wear.pomodoro.data.PROJECT_LICENSE_ID
import cc.star0.wear.pomodoro.model.ScreenStyle
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
private object OpenSourceLicensesDestination : NavKey

@Serializable
private data class LicenseDetailDestination(val componentId: String) : NavKey

@Serializable
private object DonationQrDestination : NavKey

@Serializable
private data class LinkQrDestination(val url: String) : NavKey

@Serializable
private object SponsorsDestination : NavKey

@Serializable
private object TimerPlansDestination : NavKey

@Serializable
private object ScreenStyleDestination : NavKey

@Serializable
private object NotificationStyleDestination : NavKey

@Serializable
private data class SettingEditorDestination(val kind: SettingKind) : NavKey

@Serializable
enum class SettingKind { Focus, ShortBreak, LongBreak, RoundsBeforeLongBreak, SquareTimerCornerRadius }

/** Top time display: curved [TimeText] on round screens, straight centered label on square. */
@Composable
internal fun ScreenTimeText() {
    if (LocalScreenStyle.current == ScreenStyle.Round) {
        TimeText()
    } else {
        val timeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat())
        val timeStyle = TimeTextDefaults.timeTextStyle()
        Box(
            modifier = Modifier.fillMaxWidth().padding(TimeTextDefaults.ContentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = timeSource.currentTime(),
                modifier =
                    Modifier
                        .background(TimeTextDefaults.backgroundColor(), CircleShape)
                        .padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = timeStyle.color,
                fontSize = timeStyle.fontSize,
                fontFamily = timeStyle.fontFamily,
                fontWeight = timeStyle.fontWeight,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PomodoroApp(
    viewModel: PomodoroViewModel,
    permissionReport: AppPermissionReport,
    onPermissionAction: (PermissionAction) -> Unit,
    onRefreshPermissions: () -> Unit,
    onOpenUrl: (String) -> Unit,
    openTimerRequested: Boolean,
    onTimerOpened: () -> Unit,
) {
    val settingsLoaded by viewModel.isInitialized.collectAsStateWithLifecycle()
    val settings = if (settingsLoaded) {
        // Begin collecting only after restore, so the first editable value is the saved one.
        val restoredSettings by viewModel.settings.collectAsStateWithLifecycle()
        restoredSettings
    } else {
        PomodoroSettings()
    }
    val effectiveScreenStyle = settings.screenStyle.resolve(LocalConfiguration.current.isScreenRound)
    CompositionLocalProvider(
        LocalInteractionSettings provides settings,
        LocalScreenStyle provides effectiveScreenStyle,
    ) {
        MaterialTheme(colorScheme = ColorScheme()) {
            AppScaffold(timeText = { ScreenTimeText() }) {
                val backStack = rememberNavBackStack(HomeDestination)
                val pagerState = rememberPagerState(pageCount = { 3 })
                LaunchedEffect(openTimerRequested) {
                    if (openTimerRequested) {
                        while (backStack.size > 1) backStack.removeLastOrNull()
                        pagerState.scrollToPage(0)
                        onTimerOpened()
                    }
                }
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
                            HorizontalPagerScaffold(
                                pagerState = pagerState,
                                pageIndicator = {
                                    if (effectiveScreenStyle == ScreenStyle.Round) {
                                        HorizontalPageIndicator(pagerState)
                                    } else {
                                        SquarePageIndicator(pagerState)
                                    }
                                },
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    val pageContent: @Composable () -> Unit = {
                                        when (page) {
                                            0 -> TimerScreen(viewModel)
                                            1 -> SettingsScreen(
                                                viewModel = viewModel,
                                                onEditSetting = { kind -> backStack.add(SettingEditorDestination(kind)) },
                                                onOpenTimerPlans = { backStack.add(TimerPlansDestination) },
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
                                                onScreenStyle = { backStack.add(ScreenStyleDestination) },
                                                onSquareTimerCornerRadius = { backStack.add(SettingEditorDestination(SettingKind.SquareTimerCornerRadius)) },
                                                onNotificationStyle = { backStack.add(NotificationStyleDestination) },
                                            )
                                        }
                                    }
                                    if (effectiveScreenStyle == ScreenStyle.Round) {
                                        AnimatedPage(pageIndex = page, pagerState = pagerState, content = pageContent)
                                    } else {
                                        pageContent()
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
                        entry<ScreenStyleDestination> {
                            ScreenStyleScreen(
                                settings = settings,
                                settingsLoaded = settingsLoaded,
                                onSettingsChange = viewModel::updateSettings,
                                onNavigateBack = navigateBack,
                            )
                        }
                        entry<NotificationStyleDestination> {
                            NotificationStyleScreen(
                                settings = settings,
                                settingsLoaded = settingsLoaded,
                                onSettingsChange = viewModel::updateSettings,
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
                                onShowLinkQr = { backStack.add(LinkQrDestination(it)) },
                                onShowOpenSourceLicenses = { backStack.add(OpenSourceLicensesDestination) },
                                onShowProjectLicense = { backStack.add(LicenseDetailDestination(PROJECT_LICENSE_ID)) },
                                onShowDonationQr = { backStack.add(DonationQrDestination) },
                                onShowSponsors = { backStack.add(SponsorsDestination) },
                                onNavigateBack = navigateBack,
                            )
                        }
                        entry<DonationQrDestination> {
                            TipCodeScreen(onNavigateBack = navigateBack)
                        }
                        entry<LinkQrDestination> { destination ->
                            LinkQrScreen(url = destination.url, onNavigateBack = navigateBack)
                        }
                        entry<OpenSourceLicensesDestination> {
                            OpenSourceLicensesScreen(
                                onOpenLicense = { backStack.add(LicenseDetailDestination(it)) },
                                onNavigateBack = navigateBack,
                            )
                        }
                        entry<LicenseDetailDestination> { destination ->
                            LicenseDetailScreen(destination.componentId, onNavigateBack = navigateBack)
                        }
                        entry<SponsorsDestination> {
                            SponsorsScreen(onNavigateBack = navigateBack)
                        }
                        entry<TimerPlansDestination> {
                            TimerPlansScreen(viewModel = viewModel, onDone = navigateBack)
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
