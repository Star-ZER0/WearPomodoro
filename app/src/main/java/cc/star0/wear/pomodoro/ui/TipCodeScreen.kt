package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cc.star0.wear.pomodoro.R

@Composable
fun TipCodeScreen(onNavigateBack: () -> Unit) {
    BrightCodeScreen(onNavigateBack) {
        Image(
            painter = painterResource(R.drawable.tipcode),
            contentDescription = stringResource(R.string.donation_qr_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
