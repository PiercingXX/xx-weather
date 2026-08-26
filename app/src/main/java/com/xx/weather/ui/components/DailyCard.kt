package com.xx.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xx.weather.R
import com.xx.weather.data.model.DailyPoint
import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.Fmt
import com.xx.weather.ui.theme.LocalWeatherPalette
import java.time.LocalDate

/**
 * Signature Pixel-style 10-day strip: tall rounded pills, each with the day,
 * condition, precip chance, and a low→high range bar scaled to the week.
 */
@Composable
fun DailyCard(
    data: WeatherData,
    units: Units,
    modifier: Modifier = Modifier,
    onDayClick: (LocalDate) -> Unit = {},
) {
    val days = data.daily.take(10)
    if (days.isEmpty()) return
    val weekMin = days.minOf { it.loF }
    val weekMax = days.mapNotNull { it.hiF }.maxOrNull()
    val span = ((weekMax ?: weekMin) - weekMin).coerceAtLeast(1.0)

    GlassCard(modifier) {
        CardTitle("10-Day Forecast")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            itemsIndexed(days) { index, day ->
                DayPill(index, day, weekMin, span, units, onClick = { onDayClick(day.date) })
            }
        }
    }
}

private val RANGE_GRADIENT = Brush.verticalGradient(
    listOf(Color(0xFF7EB3F0), Color(0xFFF2A65A))
)

@Composable
private fun DayPill(
    index: Int,
    day: DailyPoint,
    weekMin: Double,
    span: Double,
    units: Units,
    onClick: () -> Unit,
) {
    val palette = LocalWeatherPalette.current
    Column(
        modifier = Modifier
            .width(86.dp)
            .height(206.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.background.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (index == 0) "Today" else Fmt.dayLabel(day.date),
            color = palette.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(9.dp))
        ConditionIcon.Draw(day.condition, isDay = true, Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        val pop = day.popPct ?: 0
        if (pop >= 30) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_drop),
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text("$pop%", color = palette.accent, fontSize = 11.sp)
            }
        } else {
            Text(" ", fontSize = 11.sp)
        }

        Spacer(Modifier.weight(1f))

        // Vertical range bar positioned within the week's overall range.
        // A night-only date has no high: collapse the bar onto the low rather
        // than inventing one.
        val hiBound = day.hiF ?: day.loF
        val loFrac = ((day.loF - weekMin) / span).coerceIn(0.0, 1.0)
        val hiFrac = ((hiBound - weekMin) / span).coerceIn(0.0, 1.0)
        val barHeight = 64.dp
        Box(
            Modifier
                .width(6.dp)
                .height(barHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.onSurface.copy(alpha = 0.15f))
        ) {
            val segTop = (barHeight * (1.0 - hiFrac).toFloat())
            val segHeight = barHeight * (hiFrac - loFrac).toFloat().coerceAtLeast(0.06f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = segTop)
                    .height(segHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(RANGE_GRADIENT)
            )
        }

        Spacer(Modifier.height(9.dp))
        // Omit the high entirely on night-only dates instead of printing "--".
        day.hiF?.let {
            Text(
                Fmt.temp(it, units),
                color = palette.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(Fmt.temp(day.loF, units), color = palette.muted, fontSize = 14.sp)
    }
}
