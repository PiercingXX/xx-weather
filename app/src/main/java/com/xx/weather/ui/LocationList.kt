package com.xx.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xx.weather.R
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.components.ConditionIcon
import com.xx.weather.ui.theme.LocalWeatherPalette

/** Collapsed locations list: tap a row for full detail. */
@Composable
fun LocationList(
    places: List<Place>,
    dataByZip: Map<String, WeatherData>,
    units: Units,
    onSelect: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
    ) {
        items(places, key = { it.zip }) { place ->
            LocationRow(
                place = place,
                data = dataByZip[place.zip],
                units = units,
                onSelect = { onSelect(place) },
                onRemove = { onRemove(place) }
            )
        }
    }
}

@Composable
private fun LocationRow(
    place: Place,
    data: WeatherData?,
    units: Units,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = LocalWeatherPalette.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (data != null) {
                ConditionIcon.Draw(
                    data.current.condition,
                    Fmt.isDaytime(data),
                    Modifier.size(36.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_place),
                    contentDescription = null,
                    tint = palette.muted,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = place.displayName,
                    color = palette.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = data?.current?.conditionText ?: "Loading…",
                    color = palette.muted,
                    fontSize = 13.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Fmt.temp(data?.current?.tempF, units),
                    color = palette.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                data?.daily?.firstOrNull()?.let { day ->
                    Text(
                        text = Fmt.hilo(day.hiF, day.loF, units),
                        color = palette.muted,
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Remove ${place.displayName}",
                    tint = palette.faint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
