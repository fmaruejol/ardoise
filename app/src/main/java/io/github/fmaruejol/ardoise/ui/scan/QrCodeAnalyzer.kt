package io.github.fmaruejol.ardoise.ui.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds a QR code in the camera preview.
 *
 * [onScanned] is called on the analysis executor, not the main thread.
 */
internal class QrCodeAnalyzer(private val onScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = BarcodeReader(
        BarcodeReader.Options(
            // Only QR: Spliit shares a group as a URL in one, and a group code
            // is usually read off another phone's screen, small and reflective.
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
        ),
    )

    private val gate = ScanGate()

    override fun analyze(image: ImageProxy) {
        // Closing the frame is what lets the next one through.
        image.use {
            if (gate.isClosed) return@use
            val texts = reader.read(it).map { result -> result.text.orEmpty() }
            gate.accept(texts)?.let(onScanned)
        }
    }
}

/**
 * Decides which decoded text to report. A code stays in view for many frames,
 * so only the first counts, or the same group is added over and over.
 * A blank result is the reader saying it found a code it could not read.
 */
internal class ScanGate {
    private val done = AtomicBoolean(false)

    val isClosed: Boolean get() = done.get()

    fun accept(texts: List<String>): String? {
        if (done.get()) return null
        val text = texts.firstOrNull { it.isNotBlank() } ?: return null
        // Two analysis threads are never used at once, but this is the one
        // thing here that would silently duplicate a group if that changed.
        return if (done.compareAndSet(false, true)) text else null
    }
}
