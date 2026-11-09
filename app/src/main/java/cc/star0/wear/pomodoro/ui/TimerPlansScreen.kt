package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.Text
import cc.star0.wear.pomodoro.PomodoroViewModel
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.PomodoroSettings
import cc.star0.wear.pomodoro.model.TimerPlan
import cc.star0.wear.pomodoro.model.TimerPlanDraft
import cc.star0.wear.pomodoro.text.formatTimerPlan

private val TimerPlanDraftSaver = listSaver<TimerPlanDraft, String>(
    save = { it.encode() },
    restore = { TimerPlanDraft.decode(it) },
)

@Composable
internal fun timerPlanName(plan: TimerPlan): String = when {
    plan.name.isNotEmpty() -> plan.name
    plan.id == 0 -> stringResource(R.string.timer_plan_default)
    else -> stringResource(R.string.timer_plan_number, plan.id)
}

@Composable
fun TimerPlansScreen(viewModel: PomodoroViewModel, onDone: () -> Unit) {
    val settingsLoaded by viewModel.isInitialized.collectAsStateWithLifecycle()
    if (settingsLoaded) {
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val plans by viewModel.timerPlans.collectAsStateWithLifecycle()
        TimerPlansContent(settings, plans, viewModel::confirmTimerPlan, onDone)
    } else {
        SettingEditorLoading(onDone)
    }
}

@Composable
internal fun TimerPlansContent(
    settings: PomodoroSettings,
    plans: List<TimerPlan>,
    onConfirm: (List<TimerPlan>, Int?) -> Unit,
    onDone: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = TimerPlanDraftSaver) {
        mutableStateOf(TimerPlanDraft.create(settings, plans))
    }
    var managedPlanId by rememberSaveable { mutableStateOf<Int?>(null) }
    val resources = LocalResources.current
    SettingsListLayout(
        title = stringResource(R.string.timer_plans_title),
        modifier = Modifier.selectableGroup(),
        screenStyle = settings.screenStyle,
    ) {
        item(key = "current") {
            Text(
                text = stringResource(R.string.timer_plan_current, formatTimerPlan(resources, draft.current)),
                modifier = Modifier.fillMaxWidth().transformedContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        for (plan in draft.choices) {
            item(key = plan.id) {
                TimerPlanButton(
                    plan = plan,
                    isSelected = draft.selectedId == plan.id,
                    summary = formatTimerPlan(resources, plan),
                    onSelect = { draft = draft.copy(selectedId = plan.id) },
                    onManage = { managedPlanId = plan.id },
                )
            }
        }
        item(key = "save_current") {
            SettingButton(
                label = stringResource(R.string.timer_plan_save_current),
                value = draft.plans.firstOrNull { it.id == draft.pendingId }
                    ?.let { stringResource(R.string.timer_plan_pending, timerPlanName(it)) }
                    ?: stringResource(R.string.timer_plan_save_summary),
                onClick = { draft = draft.saveCurrent() },
                enabled = draft.pendingId == null,
                showNavigateNext = true,
            )
        }
        item(key = "manage_hint") {
            Text(
                text = stringResource(R.string.timer_plan_manage_hint),
                modifier = Modifier.fillMaxWidth().transformedContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        item(key = "hint") {
            Text(
                text = stringResource(R.string.timer_plan_apply_hint),
                modifier = Modifier.fillMaxWidth().transformedContent(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth().transformedContent().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                FilledIconButton(
                    onClick = onDone,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) { Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_changes)) }
                FilledIconButton(
                    onClick = {
                        onConfirm(draft.plans, draft.selected?.id)
                        onDone()
                    },
                ) { Icon(Icons.Filled.Check, stringResource(R.string.action_confirm_timer_plan)) }
            }
        }
    }
    draft.plans.firstOrNull { it.id == managedPlanId }?.let { plan ->
        TimerPlanManagementDialog(
            plan = plan,
            onRename = { name ->
                draft = draft.rename(plan.id, name)
                managedPlanId = null
            },
            onDelete = {
                draft = draft.delete(plan.id)
                managedPlanId = null
            },
            onDismissRequest = { managedPlanId = null },
        )
    }
}

@Composable
private fun SettingsListItemScope.TimerPlanButton(
    plan: TimerPlan,
    isSelected: Boolean,
    summary: String,
    onSelect: () -> Unit,
    onManage: () -> Unit,
) {
    val palette = RadioButtonDefaults.radioButtonColors()
    val controlColor = if (isSelected) palette.selectedControlColor else palette.unselectedControlColor
    // Button owns both gestures, preventing a long press from also selecting the plan.
    Button(
        onClick = onSelect,
        onLongClick = onManage,
        onLongClickLabel = stringResource(R.string.timer_plan_manage),
        shape = RadioButtonDefaults.radioButtonShape,
        contentPadding = RadioButtonDefaults.ContentPadding,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isSelected) palette.selectedContainerColor else palette.unselectedContainerColor,
            contentColor = if (isSelected) palette.selectedContentColor else palette.unselectedContentColor,
        ),
        modifier = Modifier.fillMaxWidth().transformedHeight()
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            },
        transformation = transformation,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(timerPlanName(plan), maxLines = Int.MAX_VALUE)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) palette.selectedSecondaryContentColor else palette.unselectedSecondaryContentColor,
                maxLines = Int.MAX_VALUE,
            )
        }
        Canvas(Modifier.size(24.dp)) {
            drawCircle(color = controlColor, radius = 9.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (isSelected) drawCircle(color = controlColor, radius = 5.dp.toPx())
        }
    }
}
