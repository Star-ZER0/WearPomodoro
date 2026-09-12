package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.AlertDialogContent
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import cc.star0.wear.pomodoro.R

@Composable
internal fun StopConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val settings = LocalInteractionSettings.current
    // Wear AlertDialog always enables its own swipe handler. Own that layer here so
    // the dialog window can follow the same back settings as navigation destinations.
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = settings.systemBackGestureEnabled,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        SwipeToDismissBox(
            onDismissed = onDismissRequest,
            userSwipeEnabled = settings.effectiveSwipeBackEnabled,
            modifier = Modifier.fillMaxSize().then(
                if (settings.systemBackGestureEnabled) Modifier else Modifier.systemGestureExclusion(),
            ),
        ) { isBackground ->
            if (!isBackground) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AlertDialogContent(
                        title = { Text(stringResource(R.string.stop_confirmation_title)) },
                        text = { Text(stringResource(R.string.stop_confirmation_message)) },
                        transformationSpec = rememberTransformationSpec(),
                        confirmButton = {
                            AlertDialogDefaults.ConfirmButton(
                                onClick = onConfirm,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) { Icon(Icons.Filled.Check, stringResource(R.string.action_confirm_stop)) }
                        },
                        dismissButton = {
                            AlertDialogDefaults.DismissButton(onClick = onDismissRequest) {
                                Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_stop))
                            }
                        },
                    )
                }
            }
        }
    }
}
