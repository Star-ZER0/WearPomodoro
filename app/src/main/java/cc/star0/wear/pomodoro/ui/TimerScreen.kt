package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import cc.star0.wear.pomodoro.PomodoroViewModel
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroPhase
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.PomodoroState
import cc.star0.wear.pomodoro.text.formatDuration
import cc.star0.wear.pomodoro.text.formatRoundLabel
import cc.star0.wear.pomodoro.timer.PomodoroService
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TimerScreen(viewModel: PomodoroViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.isRunning) {
        if (state.isRunning) PomodoroService.ensureRunning(context.applicationContext)
    }
    TimerContent(
        state = state,
        settings = settings,
        onStart = viewModel::startFocus,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onStop = viewModel::stop,
    )
}

@Composable
fun TimerContent(
    state: PomodoroState,
    settings: PomodoroSettings,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val resources = LocalResources.current
    val progressDescription = stringResource(R.string.timer_progress_description)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isScreenActive = LocalScreenIsActive.current
    var nowElapsed by remember(state.isRunning, state.endAtElapsedMillis, state.phase) {
        mutableLongStateOf(android.os.SystemClock.elapsedRealtime())
    }
    LaunchedEffect(lifecycle, isScreenActive, state.isRunning, state.endAtElapsedMillis, state.phase) {
        if (!isScreenActive) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Re-read the elapsed clock immediately when returning to the visible timer page.
            while (true) {
                nowElapsed = android.os.SystemClock.elapsedRealtime()
                val nextTick = timerDisplay(state, settings, nowElapsed).nextTickDelayMillis
                if (nextTick == 0L) break
                delay(nextTick.milliseconds)
            }
        }
    }
    val ready = state.phase == PomodoroPhase.ReadyToFocus
    val paused = !ready && !state.isRunning
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.phase, state.isRunning) {
        if (!paused) confirmStop = false
    }
    val display = timerDisplay(state, settings, nowElapsed)
    // Wear's indicator observes state reads inside its progress lambda with snapshotFlow.
    // Keep the state object stable so the indicator can observe every subsequent second.
    val progressState = rememberUpdatedState(display.progress)
    val phase = stringResource(
        when (state.phase) {
            PomodoroPhase.ReadyToFocus -> R.string.phase_ready
            PomodoroPhase.Focus -> R.string.phase_focus
            PomodoroPhase.ShortBreak -> R.string.phase_short_break
            PomodoroPhase.LongBreak -> R.string.phase_long_break
        },
    )
    val palette = settingPalette(
        when (state.phase) {
            PomodoroPhase.ShortBreak -> SettingKind.ShortBreak
            PomodoroPhase.LongBreak -> SettingKind.LongBreak
            else -> SettingKind.Focus
        },
    )
    val rounds = if (ready) settings.focusRoundsBeforeLongBreak else state.effectiveSettings.focusRoundsBeforeLongBreak
    val round = if (ready || state.phase == PomodoroPhase.Focus) state.completedFocusRounds + 1 else state.completedFocusRounds

    ScreenScaffold(contentPadding = PaddingValues(0.dp), timeText = { TimeText() }) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            val diameter = minOf(maxWidth, maxHeight)
            val buttonSize = minOf(IconButtonDefaults.DefaultButtonSize, diameter * 0.28f)
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progressState.value },
                    // A single arc with a 60-degree opening centered at the top for the clock.
                    startAngle = 300f,
                    endAngle = 240f,
                    strokeWidth = 8.dp,
                    enabled = true,
                    allowProgressOverflow = false,
                    modifier = Modifier.fillMaxSize()
                        .padding(CircularProgressIndicatorDefaults.FullScreenPadding + 4.dp)
                        .semantics { contentDescription = progressDescription },
                    colors = ProgressIndicatorDefaults.colors(indicatorColor = palette.accent),
                )
                FittedText(
                    text = if (paused) stringResource(R.string.phase_paused, phase) else phase,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset(y = diameter * 0.15f).fillMaxWidth(0.68f).height(diameter * 0.11f),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent,
                )
                FittedText(
                    text = formatDuration(resources, display.remainingMillis),
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset(y = diameter * 0.28f).fillMaxWidth(0.72f).height(diameter * 0.21f),
                    style = MaterialTheme.typography.numeralLarge,
                )
                Row(
                    modifier = Modifier.offset(y = diameter * 0.115f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = when {
                            ready -> onStart
                            paused -> onResume
                            else -> onPause
                        },
                        modifier = Modifier.size(buttonSize),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent,
                        ),
                    ) {
                        if (state.isRunning) {
                            Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.timer_pause_description), Modifier.size(28.dp))
                        } else {
                            Icon(
                                Icons.Filled.PlayArrow,
                                stringResource(if (ready) R.string.timer_start_description else R.string.timer_resume_description),
                                Modifier.size(30.dp),
                            )
                        }
                    }
                    if (paused) {
                        FilledIconButton(
                            onClick = { confirmStop = true },
                            modifier = Modifier.size(buttonSize),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(painterResource(R.drawable.ic_stop), stringResource(R.string.timer_stop_description), Modifier.size(28.dp))
                        }
                    }
                }
                FittedText(
                    text = formatRoundLabel(resources, round, rounds),
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset(y = diameter * 0.79f).fillMaxWidth(0.58f).height(diameter * 0.09f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmStop && paused) {
        StopConfirmationDialog(
            onDismissRequest = { confirmStop = false },
            onConfirm = {
                confirmStop = false
                onStop()
            },
        )
    }
}
