package cc.star0.wear.pomodoro.ui

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import cc.star0.wear.pomodoro.model.PomodoroSettings

internal val LocalInteractionSettings = staticCompositionLocalOf { PomodoroSettings() }

// Wear Navigation uses platform predictive back instead of a Compose swipe from API 36.
internal val supportsIndependentSwipeBack: Boolean
    get() = Build.VERSION.SDK_INT < 36

internal val PomodoroSettings.effectiveSwipeBackEnabled: Boolean
    get() = if (supportsIndependentSwipeBack) composeSwipeBackEnabled else systemBackGestureEnabled
