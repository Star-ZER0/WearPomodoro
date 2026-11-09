package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.TimerPlan

private enum class PlanAction { Menu, Rename, Delete }

@Composable
internal fun TimerPlanManagementDialog(
    plan: TimerPlan,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var action by rememberSaveable(plan.id) { mutableStateOf(PlanAction.Menu) }
    val settings = LocalInteractionSettings.current
    val navigateBack: () -> Unit = {
        if (action == PlanAction.Menu) onDismissRequest() else action = PlanAction.Menu
    }
    // Match the stop dialog's system-back and swipe settings, including explicit cancel controls.
    Dialog(
        onDismissRequest = navigateBack,
        properties = DialogProperties(
            dismissOnBackPress = settings.systemBackGestureEnabled,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        SwipeToDismissBox(
            onDismissed = navigateBack,
            state = key(action) { rememberSwipeToDismissBoxState() },
            userSwipeEnabled = settings.effectiveSwipeBackEnabled,
            modifier = Modifier.fillMaxSize().then(
                if (settings.systemBackGestureEnabled) Modifier else Modifier.systemGestureExclusion(),
            ),
        ) { isBackground ->
            if (!isBackground) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (action) {
                        PlanAction.Menu -> SettingsListLayout(
                            title = timerPlanName(plan),
                            onEdgeBack = onDismissRequest,
                            edgeBackDescription = R.string.action_back_to_timer_plans,
                        ) {
                            item(key = "rename") {
                                SettingButton(
                                    label = stringResource(R.string.timer_plan_rename),
                                    value = stringResource(R.string.timer_plan_rename_summary),
                                    onClick = { action = PlanAction.Rename },
                                    showNavigateNext = true,
                                )
                            }
                            item(key = "delete") {
                                SettingButton(
                                    label = stringResource(R.string.timer_plan_delete),
                                    value = stringResource(R.string.timer_plan_delete_summary),
                                    onClick = { action = PlanAction.Delete },
                                    showNavigateNext = true,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                )
                            }
                        }
                        PlanAction.Rename -> TimerPlanNameEditor(
                            plan = plan,
                            onSave = onRename,
                            onCancel = navigateBack,
                        )
                        PlanAction.Delete -> AlertDialogContent(
                            title = { Text(stringResource(R.string.timer_plan_delete_title)) },
                            text = { Text(stringResource(R.string.timer_plan_delete_message, timerPlanName(plan))) },
                            transformationSpec = rememberTransformationSpec(),
                            confirmButton = {
                                AlertDialogDefaults.ConfirmButton(
                                    onClick = onDelete,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                ) { Icon(Icons.Filled.Check, stringResource(R.string.timer_plan_delete)) }
                            },
                            dismissButton = {
                                AlertDialogDefaults.DismissButton(onClick = navigateBack) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_changes))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerPlanNameEditor(plan: TimerPlan, onSave: (String) -> Unit, onCancel: () -> Unit) {
    val initialName = timerPlanName(plan)
    var input by rememberSaveable(plan.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, selection = TextRange(0, initialName.length)))
    }
    var requestedFocus by rememberSaveable(plan.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val canSave = TimerPlan.isValidName(input.text)
    val nameLabel = stringResource(R.string.timer_plan_name)
    val save: () -> Unit = {
        if (canSave) {
            keyboard?.hide()
            onSave(TimerPlan.normalizedName(input.text))
        }
    }
    SettingsListLayout(
        title = stringResource(R.string.timer_plan_rename),
        modifier = Modifier.imePadding(),
    ) {
        item(key = "name") {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().transformedContent().focusRequester(focusRequester)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .semantics { contentDescription = nameLabel },
                decorationBox = { field ->
                    Box {
                        if (input.text.isEmpty()) {
                            Text(nameLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        field()
                    }
                },
            )
            LaunchedEffect(Unit) {
                if (!requestedFocus) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                    requestedFocus = true
                }
            }
        }
        item(key = "name_hint") {
            Text(
                text = pluralStringResource(R.plurals.timer_plan_name_hint, TimerPlan.MAX_NAME_LENGTH, TimerPlan.MAX_NAME_LENGTH),
                modifier = Modifier.fillMaxWidth().transformedContent(),
                style = MaterialTheme.typography.bodySmall,
                color = if (canSave) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        item(key = "name_actions") {
            Row(
                modifier = Modifier.fillMaxWidth().transformedContent().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                FilledIconButton(
                    onClick = {
                        keyboard?.hide()
                        onCancel()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_changes)) }
                FilledIconButton(onClick = save, enabled = canSave) {
                    Icon(Icons.Filled.Check, stringResource(R.string.timer_plan_save_name))
                }
            }
        }
    }
}
