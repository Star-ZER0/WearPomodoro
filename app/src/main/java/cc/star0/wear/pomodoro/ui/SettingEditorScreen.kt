package cc.star0.wear.pomodoro.ui

import androidx.annotation.PluralsRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.PickerGroup
import androidx.wear.compose.material3.PickerState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import cc.star0.wear.pomodoro.PomodoroViewModel
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroSettings

@Composable
fun SettingEditorScreen(
    viewModel: PomodoroViewModel,
    kind: SettingKind,
    onDone: () -> Unit,
) {
    val settingsLoaded by viewModel.isInitialized.collectAsStateWithLifecycle()
    if (settingsLoaded) {
        // Creating the collector here reads StateFlow.value after restore has completed.
        // The pickers can then keep the user's draft without reacting to later settings updates.
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        SettingEditorContent(kind, settings, viewModel::updateSettings, onDone)
    } else {
        SettingEditorLoading(onDone)
    }
}

@Composable
private fun SettingEditorLoading(onCancel: () -> Unit) {
    ScreenScaffold(contentPadding = PaddingValues(0.dp), timeText = {}) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.settings_loading), textAlign = TextAlign.Center)
            FilledIconButton(
                onClick = onCancel,
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_changes)) }
        }
    }
}

/** Compact, cyclic pickers: HH:mm with minutes focused, or one focused rounds column. */
@Composable
fun SettingEditorContent(
    kind: SettingKind,
    settings: PomodoroSettings,
    onValueChange: (PomodoroSettings) -> Unit,
    onDone: () -> Unit = {},
) {
    val initialSettings = remember(kind) { settings }
    val rounds = kind == SettingKind.RoundsBeforeLongBreak
    val roundOptions = PomodoroSettings.LongBreakRoundOptions
    val initialMinutes = when (kind) {
        SettingKind.Focus -> initialSettings.focusDurationMillis
        SettingKind.ShortBreak -> initialSettings.shortBreakDurationMillis
        SettingKind.LongBreak -> initialSettings.longBreakDurationMillis
        SettingKind.RoundsBeforeLongBreak -> 0L
    }.div(PomodoroSettings.MILLIS_PER_MINUTE).toInt().coerceIn(0, PomodoroSettings.MAX_DURATION_MINUTES)
    val firstState = rememberPickerState(
        initialNumberOfOptions = if (rounds) roundOptions.size else 24,
        initiallySelectedIndex = if (rounds) {
            roundOptions.indexOf(initialSettings.sanitized().focusRoundsBeforeLongBreak).coerceAtLeast(0)
        } else initialMinutes / 60,
        shouldRepeatOptions = true,
    )
    val minuteState = rememberPickerState(60, initialMinutes % 60, shouldRepeatOptions = true)
    var selectedColumn by rememberSaveable(kind) { mutableIntStateOf(if (rounds) 0 else 1) }
    val selectedMinutes = firstState.selectedOptionIndex * 60 + minuteState.selectedOptionIndex
    val canSave = rounds || selectedMinutes > 0
    val palette = settingPalette(kind)
    val title = stringResource(settingTitleRes(kind))
    val unit = stringResource(
        when {
            rounds -> R.string.unit_rounds
            selectedColumn == 0 -> R.string.unit_hours
            else -> R.string.unit_minutes
        },
    )

    ScreenScaffold(contentPadding = PaddingValues(0.dp), timeText = {}) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Keep the requested compact picker layout even on a larger watch display.
            val diameter = minOf(maxWidth, maxHeight, 210.dp)
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                FittedText(
                    text = if (canSave) stringResource(R.string.setting_editor_title, title, unit) else stringResource(R.string.duration_minimum_error),
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset(y = diameter * 0.105f).fillMaxWidth(0.8f).height(diameter * 0.12f),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSave) palette.accent else MaterialTheme.colorScheme.error,
                )
                PickerGroup(
                    selectedPickerState = if (selectedColumn == 0) firstState else minuteState,
                    autoCenter = false,
                    modifier = Modifier.offset(y = -diameter * 0.055f),
                ) {
                    CompactPickerColumn(
                        state = firstState,
                        selected = selectedColumn == 0,
                        onSelected = { selectedColumn = 0 },
                        palette = palette,
                        modifier = Modifier.size(if (rounds) 88.dp else 62.dp, diameter * 0.43f),
                        valueDescriptionRes = if (rounds) R.plurals.round_count else R.plurals.duration_hours,
                        numberOffset = if (rounds) 2 else 0,
                        padNumber = !rounds,
                        neverOptionIndex = if (rounds) roundOptions.lastIndex else null,
                    )
                    if (!rounds) {
                        Text(stringResource(R.string.time_separator), style = MaterialTheme.typography.numeralSmall)
                        CompactPickerColumn(
                            state = minuteState,
                            selected = selectedColumn == 1,
                            onSelected = { selectedColumn = 1 },
                            palette = palette,
                            modifier = Modifier.size(62.dp, diameter * 0.43f),
                            valueDescriptionRes = R.plurals.duration_minutes,
                            numberOffset = 0,
                            padNumber = true,
                        )
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = diameter * 0.68f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledIconButton(
                        onClick = onDone,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_changes)) }
                    FilledIconButton(
                        onClick = {
                            if (canSave) {
                                val value = selectedMinutes * PomodoroSettings.MILLIS_PER_MINUTE
                                onValueChange(
                                    when (kind) {
                                        SettingKind.Focus -> settings.copy(focusDurationMillis = value)
                                        SettingKind.ShortBreak -> settings.copy(shortBreakDurationMillis = value)
                                        SettingKind.LongBreak -> settings.copy(longBreakDurationMillis = value)
                                        SettingKind.RoundsBeforeLongBreak -> settings.copy(focusRoundsBeforeLongBreak = roundOptions[firstState.selectedOptionIndex])
                                    },
                                )
                                onDone()
                            }
                        },
                        enabled = canSave,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent,
                        ),
                    ) { Icon(Icons.Filled.Check, stringResource(R.string.action_save_settings)) }
                }
            }
        }
    }
}

@Composable
private fun CompactPickerColumn(
    state: PickerState,
    selected: Boolean,
    onSelected: () -> Unit,
    palette: SettingPalette,
    modifier: Modifier,
    @PluralsRes valueDescriptionRes: Int,
    numberOffset: Int,
    padNumber: Boolean,
    neverOptionIndex: Int? = null,
) {
    val resources = LocalResources.current
    Picker(
        state = state,
        readOnly = !selected,
        onSelected = onSelected,
        contentDescription = {
            if (state.selectedOptionIndex == neverOptionIndex) {
                resources.getString(R.string.long_break_never_description)
            } else {
                val number = state.selectedOptionIndex + numberOffset
                resources.getQuantityString(valueDescriptionRes, number, number)
            }
        },
        modifier = modifier
            .hierarchicalFocusGroup(active = selected)
            .requestFocusOnHierarchyActive()
            .pointerInput(selected) {
                if (!selected) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onSelected()
                    }
                }
            },
        verticalSpacing = 4.dp,
    ) { index ->
        val number = index + numberOffset
        FittedText(
            text = when {
                index == neverOptionIndex -> stringResource(R.string.long_break_never)
                padNumber -> stringResource(R.string.picker_two_digits, number)
                else -> number.toString()
            },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            style = MaterialTheme.typography.numeralMedium,
            color = if (selected) palette.accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}
