package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.WeatherIconType
import com.example.ui.theme.RainCyan
import com.example.ui.theme.SolarGold
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom-drawn weather condition icon with distinct visual tiers:
 * - Increasing raindrops beneath clouds indicating precipitation level (drizzle -> light -> heavy)
 * - Layered sun and moon for day/night showers and intervals
 * - Crystal snowflakes for light vs heavy snow
 * - Dynamic lightning for thunderstorms
 * - Soft layered atmospheric mist bars
 */
@Composable
fun WeatherIconView(
    iconType: WeatherIconType,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color? = null
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = iconType.name }
    ) {
        val w = this.size.width
        val h = this.size.height

        when (iconType) {
            WeatherIconType.CLEAR_DAY -> {
                drawSunIcon(w, h, tint ?: SolarGold, hasRays = true)
            }
            WeatherIconType.CLEAR_NIGHT -> {
                drawMoonIcon(w, h, tint ?: Color(0xFFF59E0B), withStars = true)
            }
            WeatherIconType.PARTLY_CLOUDY_DAY -> {
                // Sun peeking top-left, cloud in foreground
                drawSunPartial(w, h, tint ?: SolarGold)
                drawCloud(
                    cx = w * 0.54f,
                    cy = h * 0.58f,
                    width = w * 0.76f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFFB0BEC5)
                )
            }
            WeatherIconType.PARTLY_CLOUDY_NIGHT -> {
                // Moon peeking top-left, cloud in foreground
                drawMoonPartial(w, h, tint ?: Color(0xFFF59E0B))
                drawCloud(
                    cx = w * 0.54f,
                    cy = h * 0.58f,
                    width = w * 0.76f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF90A4AE)
                )
            }
            WeatherIconType.CLOUDY -> {
                // Dual cloud layers for depth
                drawCloud(
                    cx = w * 0.42f,
                    cy = h * 0.42f,
                    width = w * 0.65f,
                    height = h * 0.38f,
                    color = (tint ?: Color(0xFF90A4AE)).copy(alpha = 0.65f)
                )
                drawCloud(
                    cx = w * 0.54f,
                    cy = h * 0.55f,
                    width = w * 0.78f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFFB0BEC5)
                )
            }
            WeatherIconType.OVERCAST -> {
                // Darker, heavier cloud mass
                drawCloud(
                    cx = w * 0.40f,
                    cy = h * 0.40f,
                    width = w * 0.70f,
                    height = h * 0.40f,
                    color = (tint ?: Color(0xFF64748B)).copy(alpha = 0.8f)
                )
                drawCloud(
                    cx = w * 0.52f,
                    cy = h * 0.56f,
                    width = w * 0.82f,
                    height = h * 0.46f,
                    color = tint ?: Color(0xFF78909C)
                )
            }
            WeatherIconType.MIST, WeatherIconType.FOG -> {
                drawFogLines(w, h, tint ?: Color(0xFF90A4AE))
            }
            WeatherIconType.DRIZZLE -> {
                // Cloud with 1 subtle raindrop beneath
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.38f,
                    width = w * 0.78f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF90A4AE)
                )
                val rainColor = tint ?: RainCyan.copy(alpha = 0.85f)
                drawRaindrop(cx = w * 0.50f, topY = h * 0.66f, length = h * 0.22f, color = rainColor, strokeWidth = w * 0.08f)
            }
            WeatherIconType.LIGHT_RAIN -> {
                // Cloud with 2 distinct raindrops beneath
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.38f,
                    width = w * 0.78f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF78909C)
                )
                val rainColor = tint ?: RainCyan
                drawRaindrop(cx = w * 0.38f, topY = h * 0.66f, length = h * 0.24f, color = rainColor, strokeWidth = w * 0.08f)
                drawRaindrop(cx = w * 0.62f, topY = h * 0.66f, length = h * 0.24f, color = rainColor, strokeWidth = w * 0.08f)
            }
            WeatherIconType.RAIN_SHOWER_DAY -> {
                // Sun top-left + cloud + 2 rain drops
                drawSunPartial(w, h, tint ?: SolarGold)
                drawCloud(
                    cx = w * 0.52f,
                    cy = h * 0.42f,
                    width = w * 0.74f,
                    height = h * 0.42f,
                    color = tint ?: Color(0xFF90A4AE)
                )
                val rainColor = tint ?: RainCyan
                drawRaindrop(cx = w * 0.40f, topY = h * 0.68f, length = h * 0.22f, color = rainColor, strokeWidth = w * 0.075f)
                drawRaindrop(cx = w * 0.64f, topY = h * 0.68f, length = h * 0.22f, color = rainColor, strokeWidth = w * 0.075f)
            }
            WeatherIconType.RAIN_SHOWER_NIGHT -> {
                // Moon top-left + cloud + 2 rain drops
                drawMoonPartial(w, h, tint ?: Color(0xFFF59E0B))
                drawCloud(
                    cx = w * 0.52f,
                    cy = h * 0.42f,
                    width = w * 0.74f,
                    height = h * 0.42f,
                    color = tint ?: Color(0xFF78909C)
                )
                val rainColor = tint ?: Color(0xFF38BDF8)
                drawRaindrop(cx = w * 0.40f, topY = h * 0.68f, length = h * 0.22f, color = rainColor, strokeWidth = w * 0.075f)
                drawRaindrop(cx = w * 0.64f, topY = h * 0.68f, length = h * 0.22f, color = rainColor, strokeWidth = w * 0.075f)
            }
            WeatherIconType.HEAVY_RAIN_SHOWER_DAY -> {
                // Heavy showers retain the sun cue that distinguishes them
                // from persistent heavy rain.
                drawSunPartial(w, h, tint ?: SolarGold)
                drawCloud(
                    cx = w * 0.52f,
                    cy = h * 0.38f,
                    width = w * 0.78f,
                    height = h * 0.42f,
                    color = tint ?: Color(0xFF546E7A)
                )
                val rainColor = tint ?: Color(0xFF0288D1)
                drawRaindrop(cx = w * 0.27f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
                drawRaindrop(cx = w * 0.43f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
                drawRaindrop(cx = w * 0.59f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
                drawRaindrop(cx = w * 0.75f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
            }
            WeatherIconType.HEAVY_RAIN_SHOWER_NIGHT -> {
                // The corresponding moon cue preserves the night shower type.
                drawMoonPartial(w, h, tint ?: Color(0xFFF59E0B))
                drawCloud(
                    cx = w * 0.52f,
                    cy = h * 0.38f,
                    width = w * 0.78f,
                    height = h * 0.42f,
                    color = tint ?: Color(0xFF455A64)
                )
                val rainColor = tint ?: Color(0xFF0288D1)
                drawRaindrop(cx = w * 0.27f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
                drawRaindrop(cx = w * 0.43f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
                drawRaindrop(cx = w * 0.59f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
                drawRaindrop(cx = w * 0.75f, topY = h * 0.65f, length = h * 0.25f, color = rainColor, strokeWidth = w * 0.08f)
            }
            WeatherIconType.HEAVY_RAIN -> {
                // Darker cloud with 4 dense, slanted raindrops showing heavy precipitation
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.36f,
                    width = w * 0.82f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF546E7A)
                )
                val heavyRainColor = tint ?: Color(0xFF0288D1)
                drawRaindrop(cx = w * 0.26f, topY = h * 0.64f, length = h * 0.26f, color = heavyRainColor, strokeWidth = w * 0.085f)
                drawRaindrop(cx = w * 0.42f, topY = h * 0.64f, length = h * 0.26f, color = heavyRainColor, strokeWidth = w * 0.085f)
                drawRaindrop(cx = w * 0.58f, topY = h * 0.64f, length = h * 0.26f, color = heavyRainColor, strokeWidth = w * 0.085f)
                drawRaindrop(cx = w * 0.74f, topY = h * 0.64f, length = h * 0.26f, color = heavyRainColor, strokeWidth = w * 0.085f)
            }
            WeatherIconType.SLEET, WeatherIconType.SLEET_DAY, WeatherIconType.SLEET_NIGHT -> {
                // Cloud with alternating raindrops and ice pellets
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.38f,
                    width = w * 0.78f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF78909C)
                )
                drawRaindrop(cx = w * 0.34f, topY = h * 0.66f, length = h * 0.22f, color = tint ?: RainCyan, strokeWidth = w * 0.075f)
                drawIcePellet(cx = w * 0.54f, cy = h * 0.77f, radius = w * 0.06f, color = tint ?: Color(0xFFE0F7FA))
                drawRaindrop(cx = w * 0.70f, topY = h * 0.66f, length = h * 0.22f, color = tint ?: RainCyan, strokeWidth = w * 0.075f)
            }
            WeatherIconType.HAIL -> {
                // Cloud with multiple hail diamond pellets
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.38f,
                    width = w * 0.78f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF607D8B)
                )
                val hailColor = tint ?: Color(0xFFB3E5FC)
                drawIcePellet(cx = w * 0.30f, cy = h * 0.74f, radius = w * 0.065f, color = hailColor)
                drawIcePellet(cx = w * 0.50f, cy = h * 0.78f, radius = w * 0.075f, color = hailColor)
                drawIcePellet(cx = w * 0.70f, cy = h * 0.74f, radius = w * 0.065f, color = hailColor)
            }
            WeatherIconType.SNOW, WeatherIconType.SNOW_DAY, WeatherIconType.SNOW_NIGHT -> {
                // Cloud with 2 crystal snowflakes beneath
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.38f,
                    width = w * 0.78f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF78909C)
                )
                val snowColor = tint ?: Color(0xFFE0F7FA)
                drawSnowflake(cx = w * 0.36f, cy = h * 0.76f, radius = w * 0.12f, color = snowColor)
                drawSnowflake(cx = w * 0.64f, cy = h * 0.76f, radius = w * 0.12f, color = snowColor)
            }
            WeatherIconType.HEAVY_SNOW, WeatherIconType.HEAVY_SNOW_DAY, WeatherIconType.HEAVY_SNOW_NIGHT -> {
                // Dense cloud with 3 crystal snowflakes beneath
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.36f,
                    width = w * 0.82f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF546E7A)
                )
                val snowColor = tint ?: Color(0xFFE0F7FA)
                drawSnowflake(cx = w * 0.28f, cy = h * 0.74f, radius = w * 0.11f, color = snowColor)
                drawSnowflake(cx = w * 0.50f, cy = h * 0.78f, radius = w * 0.12f, color = snowColor)
                drawSnowflake(cx = w * 0.72f, cy = h * 0.74f, radius = w * 0.11f, color = snowColor)
            }
            WeatherIconType.THUNDERSTORM, WeatherIconType.THUNDERSTORM_DAY, WeatherIconType.THUNDERSTORM_NIGHT -> {
                // Dark storm cloud + lightning bolt + raindrops
                drawCloud(
                    cx = w * 0.50f,
                    cy = h * 0.36f,
                    width = w * 0.82f,
                    height = h * 0.44f,
                    color = tint ?: Color(0xFF455A64)
                )
                // Lightning bolt
                drawLightningBolt(
                    startX = w * 0.50f,
                    startY = h * 0.48f,
                    width = w * 0.24f,
                    height = h * 0.46f,
                    color = Color(0xFFFFD54F)
                )
                // Raindrops to the sides of lightning
                val rainColor = tint ?: RainCyan
                drawRaindrop(cx = w * 0.26f, topY = h * 0.66f, length = h * 0.22f, color = rainColor, strokeWidth = w * 0.075f)
                drawRaindrop(cx = w * 0.74f, topY = h * 0.66f, length = h * 0.22f, color = rainColor, strokeWidth = w * 0.075f)
            }
        }
    }
}

// ---------------------------------------------------------
// Draw Scope Helpers
// ---------------------------------------------------------

private fun DrawScope.drawSunIcon(w: Float, h: Float, color: Color, hasRays: Boolean) {
    val cx = w / 2f
    val cy = h / 2f
    val radius = w * 0.24f

    if (hasRays) {
        val numRays = 8
        val innerR = w * 0.34f
        val outerR = w * 0.46f
        val strokeWidth = w * 0.07f

        for (i in 0 until numRays) {
            val angle = (i * 360f / numRays) * (Math.PI.toFloat() / 180f)
            val startX = cx + innerR * cos(angle)
            val startY = cy + innerR * sin(angle)
            val endX = cx + outerR * cos(angle)
            val endY = cy + outerR * sin(angle)

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }

    drawCircle(
        color = color,
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawSunPartial(w: Float, h: Float, color: Color) {
    val cx = w * 0.30f
    val cy = h * 0.30f
    val radius = w * 0.22f

    // Partial rays pointing up and left
    val rayAngles = listOf(-135f, -90f, -45f, 180f, -180f)
    val innerR = radius * 1.30f
    val outerR = radius * 1.70f
    val strokeWidth = w * 0.065f

    for (deg in rayAngles) {
        val rad = deg * (Math.PI.toFloat() / 180f)
        val startX = cx + innerR * cos(rad)
        val startY = cy + innerR * sin(rad)
        val endX = cx + outerR * cos(rad)
        val endY = cy + outerR * sin(rad)

        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = color,
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawMoonIcon(w: Float, h: Float, color: Color, withStars: Boolean) {
    val cx = w * 0.44f
    val cy = h * 0.50f
    val r = w * 0.36f

    // Classic crescent moon opening towards the upper right
    val moonPath = Path().apply {
        // Top horn tip
        moveTo(cx + r * 0.25f, cy - r * 0.95f)
        // Outer curved back sweeping around the left side
        cubicTo(
            cx - r * 1.25f, cy - r * 0.95f,
            cx - r * 1.25f, cy + r * 0.95f,
            cx + r * 0.25f, cy + r * 0.95f
        )
        // Inner scooped bowl of the crescent sweeping back to top horn
        cubicTo(
            cx - r * 0.35f, cy + r * 0.65f,
            cx - r * 0.35f, cy - r * 0.65f,
            cx + r * 0.25f, cy - r * 0.95f
        )
        close()
    }

    drawPath(path = moonPath, color = color)

    if (withStars) {
        // Sparkling stars in the night sky
        drawStar(cx = w * 0.78f, cy = h * 0.28f, size = w * 0.085f, color = color)
        drawStar(cx = w * 0.70f, cy = h * 0.68f, size = w * 0.065f, color = color.copy(alpha = 0.85f))
    }
}

private fun DrawScope.drawMoonPartial(w: Float, h: Float, color: Color) {
    val cx = w * 0.30f
    val cy = h * 0.32f
    val r = w * 0.25f

    val moonPath = Path().apply {
        moveTo(cx + r * 0.25f, cy - r * 0.95f)
        cubicTo(
            cx - r * 1.25f, cy - r * 0.95f,
            cx - r * 1.25f, cy + r * 0.95f,
            cx + r * 0.25f, cy + r * 0.95f
        )
        cubicTo(
            cx - r * 0.35f, cy + r * 0.65f,
            cx - r * 0.35f, cy - r * 0.65f,
            cx + r * 0.25f, cy - r * 0.95f
        )
        close()
    }
    drawPath(path = moonPath, color = color)
}

private fun DrawScope.drawStar(cx: Float, cy: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx, cy - size)
        quadraticTo(cx, cy, cx + size, cy)
        quadraticTo(cx, cy, cx, cy + size)
        quadraticTo(cx, cy, cx - size, cy)
        quadraticTo(cx, cy, cx, cy - size)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawCloud(
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    color: Color
) {
    val left = cx - width / 2f
    val right = cx + width / 2f
    val bottom = cy + height / 2f
    val baseRadius = height * 0.36f

    val path = Path().apply {
        // Base flat bottom pill
        moveTo(left + baseRadius, bottom)
        lineTo(right - baseRadius, bottom)
        // Right puff arc
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                right - baseRadius * 2f,
                bottom - baseRadius * 2f,
                right,
                bottom
            ),
            startAngleDegrees = 90f,
            sweepAngleDegrees = -160f,
            forceMoveTo = false
        )
        // Top right dome
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                cx + width * 0.02f - height * 0.40f,
                bottom - height * 0.95f,
                cx + width * 0.02f + height * 0.40f,
                bottom - height * 0.15f
            ),
            startAngleDegrees = 20f,
            sweepAngleDegrees = -140f,
            forceMoveTo = false
        )
        // Top main central dome
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                cx - width * 0.22f - height * 0.45f,
                bottom - height * 1.05f,
                cx - width * 0.22f + height * 0.45f,
                bottom - height * 0.15f
            ),
            startAngleDegrees = -30f,
            sweepAngleDegrees = -135f,
            forceMoveTo = false
        )
        // Left puff arc
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                left,
                bottom - baseRadius * 2f,
                left + baseRadius * 2f,
                bottom
            ),
            startAngleDegrees = 210f,
            sweepAngleDegrees = -120f,
            forceMoveTo = false
        )
        close()
    }

    drawPath(path = path, color = color)
}

private fun DrawScope.drawRaindrop(
    cx: Float,
    topY: Float,
    length: Float,
    color: Color,
    strokeWidth: Float
) {
    // 20 degree slant rain line with rounded caps
    val slantOffset = length * 0.35f
    drawLine(
        color = color,
        start = Offset(cx + slantOffset * 0.5f, topY),
        end = Offset(cx - slantOffset * 0.5f, topY + length),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawIcePellet(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color
) {
    // Diamond shape pellet
    val path = Path().apply {
        moveTo(cx, cy - radius * 1.2f)
        lineTo(cx + radius, cy)
        lineTo(cx, cy + radius * 1.2f)
        lineTo(cx - radius, cy)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawSnowflake(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color
) {
    val strokeWidth = radius * 0.32f
    // 6-spoke snowflake
    for (i in 0 until 3) {
        val angle = i * 60f * (Math.PI.toFloat() / 180f)
        val dx = radius * cos(angle)
        val dy = radius * sin(angle)
        drawLine(
            color = color,
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
    drawCircle(
        color = color,
        radius = radius * 0.25f,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawLightningBolt(
    startX: Float,
    startY: Float,
    width: Float,
    height: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(startX + width * 0.2f, startY)
        lineTo(startX - width * 0.3f, startY + height * 0.45f)
        lineTo(startX + width * 0.05f, startY + height * 0.45f)
        lineTo(startX - width * 0.25f, startY + height)
        lineTo(startX + width * 0.4f, startY + height * 0.40f)
        lineTo(startX + width * 0.05f, startY + height * 0.40f)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawFogLines(w: Float, h: Float, color: Color) {
    val strokeWidth = h * 0.085f
    val lineSpecs = listOf(
        Triple(w * 0.18f, w * 0.82f, h * 0.32f),
        Triple(w * 0.10f, w * 0.90f, h * 0.50f),
        Triple(w * 0.22f, w * 0.78f, h * 0.68f)
    )

    for ((x1, x2, y) in lineSpecs) {
        drawLine(
            color = color,
            start = Offset(x1, y),
            end = Offset(x2, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
