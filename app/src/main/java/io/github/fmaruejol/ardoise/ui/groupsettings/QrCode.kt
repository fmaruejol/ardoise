package io.github.fmaruejol.ardoise.ui.groupsettings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Draws [text] as a QR code. The scanner is zxing-cpp, which reads and does not
 * write, so the encoder is the original ZXing. The symbology is the same, so
 * this and the web client's code are interchangeable.
 *
 * Returns null rather than throwing: a code that will not render is still a
 * group with a link to share.
 */
internal fun qrCodeBitmap(
    text: String,
    sizePx: Int,
    foreground: Int,
    background: Int,
): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(
            // A group URL is short, so the highest correction level costs
            // little and survives being read off a screen at an angle.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        ),
    )

    val bitmap = createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val row = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[row + x] = if (matrix.get(x, y)) foreground else background
        }
    }
    bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    bitmap.asImageBitmap()
}.getOrNull()

/** Modules of quiet zone. Below four, some readers refuse the code. */
private const val QUIET_ZONE_MODULES = 4
