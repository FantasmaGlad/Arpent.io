package com.fanta.androidsport.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorWheel(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var wheelCenter by remember { mutableStateOf(Offset.Zero) }
    var wheelRadius by remember { mutableStateOf(0f) }

    val colors = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
    }
    val brush = remember {
        Brush.sweepGradient(colors)
    }

    fun getColorAtPoint(offset: Offset, center: Offset, radius: Float): Color? {
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > radius || distance < 0.01f) return null

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) {
            angle += 360f
        }

        val saturation = (distance / radius).coerceIn(0f, 1f)
        val hsv = floatArrayOf(angle, saturation, 1.0f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            val color = getColorAtPoint(change.position, wheelCenter, wheelRadius)
                            if (color != null) onColorSelected(color)
                            change.consume()
                        }
                    }
                }
            }
    ) {
        wheelCenter = center
        wheelRadius = size.minDimension / 2f

        drawCircle(
            brush = brush,
            radius = wheelRadius,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = wheelRadius
            ),
            radius = wheelRadius,
            center = center
        )

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColor.toArgb(), hsv)
        val hue = hsv[0]
        val saturation = hsv[1]

        val angleRad = Math.toRadians(hue.toDouble())
        val indicatorX = center.x + cos(angleRad).toFloat() * saturation * wheelRadius
        val indicatorY = center.y + sin(angleRad).toFloat() * saturation * wheelRadius

        drawCircle(
            color = Color.Black,
            radius = 10f,
            center = Offset(indicatorX, indicatorY),
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = Color.White,
            radius = 8f,
            center = Offset(indicatorX, indicatorY)
        )
    }
}
