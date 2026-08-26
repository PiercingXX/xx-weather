package com.xx.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xx.weather.R
import com.xx.weather.data.model.HourlyPoint
import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.Fmt

/** Horizontal hourly carousel — time, icon, temp, precip chance. */
@Composable
fun HourlyCard(data: WeatherData, units: Units, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        CardTitle("Hourly Forecast")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            itemsIndexed(data.hourly.take(24)) { index, hour ->
                HourColumn(index, hour, units)
            }
        }
    }
}

@Composable
private fun HourColumn(index: Int, hour: HourlyPoint, units: Units) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (index == 0) "Now" else Fmt.hourLabel(hour.time),
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(7.dp))
        ConditionIcon.Draw(hour.condition, hour.isDay, Modifier.size(26.dp))
        Spacer(Modifier.height(7.dp))
        Text(
            text = Fmt.temp(hour.tempF, units),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        val pop = hour.popPct ?: 0
        if (pop >= 30) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_drop),
                    contentDescription = null,
                    tint = Color(0xFF8AB4F8),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(text = "$pop%", color = Color(0xFF8AB4F8), fontSize = 11.sp)
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}
