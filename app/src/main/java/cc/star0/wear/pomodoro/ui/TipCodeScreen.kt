package cc.star0.wear.pomodoro.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import cc.star0.wear.pomodoro.R

@Composable
fun TipCodeScreen(onNavigateBack: () -> Unit) {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(view, window) {
        val wasKeepingScreenOn = view.keepScreenOn
        val previousBrightness = window?.attributes?.screenBrightness
        view.keepScreenOn = true
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
        }
        onDispose {
            view.keepScreenOn = wasKeepingScreenOn
            if (window != null && previousBrightness != null) {
                window.attributes = window.attributes.apply {
                    screenBrightness = previousBrightness
                }
            }
        }
    }

    ScreenScaffold(contentPadding = PaddingValues(0.dp), timeText = {}) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.White)
                .clickable(
                    onClickLabel = stringResource(R.string.action_back_to_about),
                    role = Role.Button,
                    onClick = onNavigateBack,
                ),
        ) {
            Image(
                painter = painterResource(R.drawable.tipcode),
                contentDescription = stringResource(R.string.donation_qr_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
