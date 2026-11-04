package net.padalton.doormutex.android.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.padalton.doormutex.android.data.Light

/**
 * The traffic light as on the device. Blinking patterns blink here (the
 * app is open); widgets show them statically with a marker instead.
 * `red_yellow_alternate` alternates red and yellow, the others switch
 * their lamps on and off together.
 */
@Composable
fun TrafficLight(
    light: Light,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    dimmed: Boolean = false,
) {
    val phase by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BLINK_MS, easing = { if (it < 0.5f) 0f else 1f }), RepeatMode.Restart),
        label = "blink-phase",
    )
    val firstHalf = !light.blinks || dimmed || phase < 0.5f
    val red: Boolean
    val yellow: Boolean
    if (light == Light.RED_YELLOW_ALTERNATE) {
        red = firstHalf
        yellow = !firstHalf || dimmed
    } else {
        red = light.red && firstHalf
        yellow = light.yellow && firstHalf
    }
    val green = light.green && firstHalf
    val alpha = if (dimmed) 0.4f else 1f

    Canvas(modifier.size(width = height * 0.42f, height = height)) {
        drawRoundRect(Color(LampColors.HOUSING), cornerRadius = CornerRadius(size.width * 0.3f))
        val r = size.width * 0.32f
        val cx = size.width / 2
        listOf(red to LampColors.RED, yellow to LampColors.YELLOW, green to LampColors.GREEN).forEachIndexed { i, (on, c) ->
            val cy = size.height * (i * 2 + 1) / 6f
            drawCircle(Color(if (on) c else LampColors.UNLIT).copy(alpha = alpha), r, Offset(cx, cy))
        }
    }
}

private const val BLINK_MS = 1000

@Preview
@Composable
private fun TrafficLightPreview() {
    TrafficLight(Light.RED_YELLOW)
}
