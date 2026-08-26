package com.xx.weather.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.xx.weather.data.model.Condition
import kotlin.math.floor
import kotlin.math.sin

/**
 * Animated full-screen weather scene: gradient sky tracking condition/time,
 * drifting clouds, sun rays / moon + twinkling stars, falling rain/snow,
 * lightning flashes, fog bands.
 */
@Composable
fun WeatherBackground(
    condition: Condition,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "weatherBg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = LinearEasing)
        ),
        label = "cycle"
    )
    Canvas(modifier = modifier) {
        drawSky(condition, isDay)
        if (!isDay && condition in NIGHT_SKY_SET) drawStars(t)
        if (isDay && condition in SUNNY_SET) drawSun(t)
        if (!isDay && condition in SUNNY_SET) drawMoon()
        drawClouds(condition, t)
        when (condition) {
            Condition.DRIZZLE -> drawRain(t, 0.45f)
            Condition.SHOWERS -> drawRain(t, 0.75f)
            Condition.RAIN -> drawRain(t, 1.0f)
            Condition.HEAVY_RAIN -> drawRain(t, 1.6f)
            Condition.THUNDERSTORM -> {
                drawRain(t, 1.4f)
                drawLightning(t)
            }
            Condition.SNOW -> drawSnow(t, heavy = false)
            Condition.HEAVY_SNOW -> drawSnow(t, heavy = true)
            Condition.SLEET -> {
                drawRain(t, 0.6f)
                drawSnow(t, heavy = false)
            }
            Condition.FOG -> drawFog(t)
            Condition.WINDY -> drawWindStreaks(t)
            else -> Unit
        }
    }
}

// ------------------------------------------------------------- palettes

private val SUNNY_SET = setOf(
    Condition.CLEAR, Condition.MOSTLY_CLEAR, Condition.PARTLY_CLOUDY
)
private val NIGHT_SKY_SET = SUNNY_SET
private val CLOUDY_SET = setOf(Condition.CLOUDY, Condition.OVERCAST, Condition.WINDY)
private val RAINY_SET = setOf(
    Condition.DRIZZLE, Condition.RAIN, Condition.HEAVY_RAIN,
    Condition.SHOWERS, Condition.THUNDERSTORM
)
private val SNOWY_SET = setOf(Condition.SNOW, Condition.HEAVY_SNOW, Condition.SLEET)

private fun skyColors(condition: Condition, isDay: Boolean): List<Color> = when {
    condition == Condition.THUNDERSTORM ->
        listOf(Color(0xFF12151C), Color(0xFF272E3D), Color(0xFF3C4759))
    condition in RAINY_SET && !isDay ->
        listOf(Color(0xFF0A1120), Color(0xFF1B2940), Color(0xFF303F5C))
    condition in RAINY_SET ->
        listOf(Color(0xFF39587A), Color(0xFF5D7EA1), Color(0xFF8CA8C4))
    condition in SNOWY_SET && !isDay ->
        listOf(Color(0xFF131A28), Color(0xFF243149), Color(0xFF3A4A66))
    condition in SNOWY_SET ->
        listOf(Color(0xFF64809E), Color(0xFF93AAC4), Color(0xFFC6D5E4))
    condition in CLOUDY_SET && !isDay ->
        listOf(Color(0xFF0E1420), Color(0xFF212C42), Color(0xFF37455F))
    condition in CLOUDY_SET ->
        listOf(Color(0xFF54779E), Color(0xFF88A3BF), Color(0xFFB7C8D8))
    condition == Condition.FOG && !isDay ->
        listOf(Color(0xFF11161F), Color(0xFF232C3A), Color(0xFF39434F))
    condition == Condition.FOG ->
        listOf(Color(0xFF77828E), Color(0xFF9AA5B0), Color(0xFFC0C7CE))
    isDay ->
        listOf(Color(0xFF2E77D0), Color(0xFF63A4E8), Color(0xFFA8CDF2))
    else ->
        listOf(Color(0xFF050A18), Color(0xFF0E1B36), Color(0xFF1D3054))
}

// ------------------------------------------------------------- helpers

/** Deterministic pseudo-random in [0,1) from an integer seed. */
private fun hash(seed: Int): Float {
    val x = sin(seed * 127.1) * 43758.5453
    return (x - floor(x)).toFloat()
}

// ------------------------------------------------------------- layers

private fun DrawScope.drawSky(condition: Condition, isDay: Boolean) {
    drawRect(
        Brush.verticalGradient(skyColors(condition, isDay)),
        size = size
    )
}

private fun DrawScope.drawStars(t: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 90) {
        val x = hash(i) * w
        val y = hash(i + 100) * h * 0.55f
        val r = (1f + hash(i + 200) * 1.8f) * (w / 1080f).coerceAtLeast(0.7f)
        val speed = 0.5f + hash(i + 7) * 1.5f
        val twinkle = (sin((t * 2f * Math.PI.toFloat() * speed) + hash(i + 13) * 6.28f) + 1f) / 2f
        drawCircle(
            Color.White.copy(alpha = 0.15f + 0.55f * twinkle),
            radius = r,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawSun(t: Float) {
    val cx = size.width * 0.74f
    val cy = size.height * 0.15f
    val r = size.minDimension * 0.055f
    // Layered glow
    drawCircle(Color(0x22FFE9A8), radius = r * 2.6f, center = Offset(cx, cy))
    drawCircle(Color(0x33FFE9A8), radius = r * 1.9f, center = Offset(cx, cy))
    drawCircle(Color(0x55FFF3C4), radius = r * 1.35f, center = Offset(cx, cy))
    // Slowly rotating rays
    rotate(degrees = t * 360f, pivot = Offset(cx, cy)) {
        for (i in 0 until 12) {
            val angle = Math.PI * 2.0 * i / 12.0
            val cos = kotlin.math.cos(angle).toFloat()
            val sinA = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = Color(0x40FFF3C4),
                start = Offset(cx + cos * r * 1.6f, cy + sinA * r * 1.6f),
                end = Offset(cx + cos * r * 2.3f, cy + sinA * r * 2.3f),
                strokeWidth = r * 0.14f
            )
        }
    }
    drawCircle(Color(0xFFFFE9A8), radius = r, center = Offset(cx, cy))
    drawCircle(Color(0xFFFFF6D8), radius = r * 0.72f, center = Offset(cx, cy))
}

private fun DrawScope.drawMoon() {
    val cx = size.width * 0.74f
    val cy = size.height * 0.13f
    val r = size.minDimension * 0.05f
    drawCircle(Color(0x22F0EDE4), radius = r * 2.0f, center = Offset(cx, cy))
    drawCircle(Color(0xFFF0EDE4), radius = r, center = Offset(cx, cy))
    // Crescent: overlay sky-colored disc offset toward upper-right
    drawCircle(Color(0xFF0A1024), radius = r * 0.92f, center = Offset(cx + r * 0.5f, cy - r * 0.22f))
}

private fun DrawScope.drawClouds(condition: Condition, t: Float) {
    val count = when {
        condition in SUNNY_SET -> 2
        condition in CLOUDY_SET -> 4
        condition in RAINY_SET || condition in SNOWY_SET || condition == Condition.FOG -> 5
        else -> 0
    }
    if (count == 0) return
    val baseColor = when {
        condition == Condition.THUNDERSTORM -> Color(0xFF3A4250).copy(alpha = 0.55f)
        condition in RAINY_SET -> Color(0xFFB9C4D2).copy(alpha = 0.34f)
        condition in SNOWY_SET -> Color(0xFFE4ECF4).copy(alpha = 0.38f)
        condition in CLOUDY_SET -> Color.White.copy(alpha = 0.26f)
        else -> Color.White.copy(alpha = 0.17f)
    }
    val w = size.width
    val h = size.height
    for (i in 0 until count) {
        val scale = 0.6f + hash(i + 57) * 0.9f
        val cw = w * 0.30f * scale
        val ch = cw * 0.42f
        val y = h * (0.06f + 0.30f * hash(i + 31))
        val speed = 0.10f + 0.22f * hash(i + 83)
        val x = ((hash(i) * w + t * w * speed) % (w + cw)) - cw
        val c = Offset(x + cw * 0.5f, y + ch * 0.5f)
        // Puffy cloud: three circles + rounded base
        drawCircle(baseColor, radius = ch * 0.62f, center = Offset(c.x - cw * 0.22f, c.y + ch * 0.08f))
        drawCircle(baseColor, radius = ch * 0.82f, center = Offset(c.x, c.y - ch * 0.18f))
        drawCircle(baseColor, radius = ch * 0.58f, center = Offset(c.x + cw * 0.24f, c.y + ch * 0.10f))
        drawRoundRect(
            color = baseColor,
            topLeft = Offset(c.x - cw * 0.42f, c.y + ch * 0.05f),
            size = Size(cw * 0.84f, ch * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(ch * 0.3f, ch * 0.3f)
        )
    }
}

private fun DrawScope.drawRain(t: Float, intensity: Float) {
    val drops = (70 * intensity).toInt().coerceIn(20, 130)
    val w = size.width
    val h = size.height
    val stroke = 1.6f * (w / 1080f).coerceAtLeast(0.8f)
    for (i in 0 until drops) {
        val x = hash(i) * w
        val speed = 6f + hash(i + 3) * 5f
        val y = ((hash(i + 5) * h) + t * h * speed) % h
        val len = h * 0.022f * (0.8f + hash(i + 9) * 0.7f)
        val slant = w * 0.010f
        drawLine(
            Color(0xFFCFE3F5).copy(alpha = 0.28f + 0.18f * hash(i + 11)),
            start = Offset(x, y),
            end = Offset(x - slant, y + len),
            strokeWidth = stroke
        )
    }
}

private fun DrawScope.drawSnow(t: Float, heavy: Boolean) {
    val flakes = if (heavy) 100 else 60
    val w = size.width
    val h = size.height
    for (i in 0 until flakes) {
        val sway = sin((t * 2f * Math.PI.toFloat()) + i) * w * 0.012f
        val x = hash(i) * w + sway
        val speed = 1.6f + hash(i + 3) * 1.8f
        val y = ((hash(i + 5) * h) + t * h * speed) % h
        val r = (1.5f + hash(i + 7) * 2.6f) * (w / 1080f).coerceAtLeast(0.8f)
        drawCircle(
            Color.White.copy(alpha = 0.45f + 0.35f * hash(i + 17)),
            radius = r,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawLightning(t: Float) {
    // Two deterministic flashes per 60s cycle
    val phase = (t * 6f) % 1f
    if (phase < 0.05f) {
        val alpha = ((0.05f - phase) / 0.05f) * 0.20f
        drawRect(Color.White.copy(alpha = alpha), size = size)
    }
    val phase2 = (t * 6f + 0.5f) % 1f
    if (phase2 < 0.04f) {
        val alpha = ((0.04f - phase2) / 0.04f) * 0.14f
        drawRect(Color.White.copy(alpha = alpha), size = size)
    }
}

private fun DrawScope.drawFog(t: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 4) {
        val bandH = h * 0.055f
        val y = h * (0.30f + 0.16f * i)
        val travel = w * (0.06f + 0.035f * i)
        val x = ((t * travel * 10f) % (w * 1.5f)) - w * 0.75f
        drawRoundRect(
            Color(0xFFCBD3DB).copy(alpha = 0.09f + 0.02f * i),
            topLeft = Offset(x, y),
            size = Size(w * 1.2f, bandH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bandH, bandH)
        )
    }
}

private fun DrawScope.drawWindStreaks(t: Float) {
    val w = size.width
    val h = size.height
    for (i in 0 until 8) {
        val y = h * (0.12f + 0.7f * hash(i))
        val speed = 2.5f + hash(i + 21) * 2.5f
        val x = ((hash(i + 5) * w + t * w * speed) % (w * 1.4f)) - w * 0.2f
        drawLine(
            Color.White.copy(alpha = 0.10f + 0.06f * hash(i + 9)),
            start = Offset(x, y),
            end = Offset(x + w * 0.16f, y + h * 0.008f * (hash(i + 31) - 0.5f)),
            strokeWidth = 2f
        )
    }
}
