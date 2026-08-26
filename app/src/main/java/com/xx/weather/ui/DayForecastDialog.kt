package com.xx.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xx.weather.R
import com.xx.weather.data.model.DailyPoint
import com.xx.weather.data.model.HourlyPoint
import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.components.ConditionIcon
import com.xx.weather.ui.theme.LocalWeatherPalette
import java.time.LocalDate

/** Hourly forecast for one calendar day, opened by tapping the date or a day pill. */
@Composable
fun DayForecastDialog(
    data: WeatherData,
    date: LocalDate,
    units: Units,
    onDismiss: () -> Unit,
) {
    val palette = LocalWeatherPalette.current
    val hours = data.hourlyOn(date)
    val day: DailyPoint? = data.daily.firstOrNull { it.date == date }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = palette.background
        ) {
            Column(
                Modifier
                    .padding(22.dp)
                    .heightIn(max = 560.dp)
            ) {
                Text(
                    text = Fmt.fullDate(date),
                    color = palette.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (day != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            Fmt.hilo(day.hiF, day.loF, units).ifBlank { null },
                            day.popPct?.let { "$it% precip" },
                            day.uvMax?.let { "UV ${Fmt.uvText(it)}" },
                        ).joinToString(" · "),
                        color = palette.muted,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (hours.isEmpty()) {
                    Text(
                        "No hourly forecast for this day.",
                        color = palette.muted,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(hours) { index, hour ->
                            HourRow(index, hour, units)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = palette.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun HourRow(index: Int, hour: HourlyPoint, units: Units) {
    val palette = LocalWeatherPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (index == 0 && hour.time.hour == java.time.LocalDateTime.now().hour) {
                "Now"
            } else {
                Fmt.hourLabel(hour.time)
            },
            color = palette.muted,
            fontSize = 13.sp,
            modifier = Modifier.width(64.dp)
        )
        ConditionIcon.Draw(hour.condition, hour.isDay, Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = Fmt.temp(hour.tempF, units),
            color = palette.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp)
        )
        val pop = hour.popPct ?: 0
        if (pop >= 20) {
            Icon(
                painter = painterResource(R.drawable.ic_drop),
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text("$pop%", color = palette.accent, fontSize = 12.sp)
        }
    }
}
