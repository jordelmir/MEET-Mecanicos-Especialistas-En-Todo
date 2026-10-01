package cr.dragon.calc.graphing

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue

@Stable
class GraphViewport(
    initialXMin: Double = -10.0,
    initialXMax: Double = 10.0,
    initialYMin: Double = -10.0,
    initialYMax: Double = 10.0
) {
    var xMin by mutableDoubleStateOf(initialXMin)
        private set
    var xMax by mutableDoubleStateOf(initialXMax)
        private set
    var yMin by mutableDoubleStateOf(initialYMin)
        private set
    var yMax by mutableDoubleStateOf(initialYMax)
        private set

    val width: Double get() = xMax - xMin
    val height: Double get() = yMax - yMin

    // Transformations from Math to Pixel
    fun toPixelX(mathX: Double, screenWidth: Float): Float {
        return ((mathX - xMin) / width * screenWidth).toFloat()
    }

    fun toPixelY(mathY: Double, screenHeight: Float): Float {
        // Pixel Y is inverted (0 is at the top)
        return screenHeight - ((mathY - yMin) / height * screenHeight).toFloat()
    }

    // Transformations from Pixel to Math
    fun toMathX(pixelX: Float, screenWidth: Float): Double {
        return xMin + (pixelX / screenWidth) * width
    }

    fun toMathY(pixelY: Float, screenHeight: Float): Double {
        return yMin + ((screenHeight - pixelY) / screenHeight) * height
    }

    // Interaction Gestures
    fun applyPan(dxPixel: Float, dyPixel: Float, screenWidth: Float, screenHeight: Float) {
        if (screenWidth <= 0f || screenHeight <= 0f) return

        val dxMath = (dxPixel / screenWidth) * width
        val dyMath = (dyPixel / screenHeight) * height

        xMin -= dxMath
        xMax -= dxMath
        // dy is positive when swiping down (which means viewport moves UP in math Y)
        yMin += dyMath
        yMax += dyMath
    }

    fun applyZoom(scale: Float, focusXPixel: Float, focusYPixel: Float, screenWidth: Float, screenHeight: Float) {
        if (scale == 1.0f || scale <= 0f || screenWidth <= 0f || screenHeight <= 0f) return

        // Find the math coordinates of the focal point
        val focusMathX = toMathX(focusXPixel, screenWidth)
        val focusMathY = toMathY(focusYPixel, screenHeight)

        // Calculate new dimensions (zoom in -> smaller width, zoom out -> larger width)
        val newWidth = width / scale
        val newHeight = height / scale

        // Calculate the old ratio of focal point
        val ratioX = (focusMathX - xMin) / width
        val ratioY = (focusMathY - yMin) / height

        // Calculate new boundaries keeping focal point at same ratio
        xMin = focusMathX - (newWidth * ratioX)
        xMax = xMin + newWidth

        yMin = focusMathY - (newHeight * ratioY)
        yMax = yMin + newHeight
    }
}
