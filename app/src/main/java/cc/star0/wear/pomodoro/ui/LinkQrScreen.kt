package cc.star0.wear.pomodoro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cc.star0.wear.pomodoro.R
import cc.star0.wear.pomodoro.model.ScreenStyle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun LinkQrScreen(url: String, onNavigateBack: () -> Unit) {
    val matrix = remember(url) {
        QRCodeWriter().encode(
            url, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            ),
        )
    }
    // Honor the app's screen style even if the device reports a different physical shape.
    val round = LocalScreenStyle.current == ScreenStyle.Round
    val description = stringResource(R.string.link_qr_description, url)
    BrightCodeScreen(onNavigateBack) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
            val shortSide = size.minDimension
            // The encoded two-module border supplies the inset from both long edges in square mode.
            val side = floor(if (round) shortSide / sqrt(2f) else shortSide)
            val moduleSize = side / matrix.width
            if (moduleSize >= 1f) {
                val left = floor((size.width - side) / 2f)
                val top = floor((size.height - side) / 2f)
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix[x, y]) {
                            // Align shared edges to pixels so adjacent modules leave no gaps.
                            val x0 = (x * moduleSize).roundToInt().toFloat()
                            val y0 = (y * moduleSize).roundToInt().toFloat()
                            val x1 = ((x + 1) * moduleSize).roundToInt().toFloat()
                            val y1 = ((y + 1) * moduleSize).roundToInt().toFloat()
                            drawRect(Color.Black, Offset(left + x0, top + y0), Size(x1 - x0, y1 - y0))
                        }
                    }
                }
            }
        }
    }
}
