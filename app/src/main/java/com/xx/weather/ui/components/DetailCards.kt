package com.xx.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xx.weather.R
import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.Fmt
import com.xx.weather.ui.theme.LocalWeatherPalette
import java.util.Locale
import kotlin.math.roundToInt

/** Two-column grid of detail tiles: feels-like, humidity, wind, UV, etc. */
@Composable
fun DetailsGrid(data: WeatherData, units: Units, modifier: Modifier = Modifier) {
    val c = data.current
    val tiles = listOf(
        DetailTileData(
            "Feels Like",
            Fmt.temp(c.feelsLikeF, units),
            "Actual ${Fmt.temp(c.tempF, units)}"
        ),
        DetailTileData(
            "Humidity",
            c.humidityPct?.let { "$it%" } ?: "--",
            c.dewpointF?.let { "Dew point ${Fmt.temp(it, units)}" } ?: ""
        ),
        DetailTileData(
            "Wind",
            c.windMph?.let { "${it.roundToInt()} mph" } ?: "--",
            c.windDirDeg?.let { Fmt.windDir(it) } ?: "",
            arrowDeg = c.windDirDeg
        ),
        DetailTileData(
            "UV Index",
            c.uvIndex?.let { Fmt.uvText(it) } ?: "--",
            Fmt.uvLabel(c.uvIndex)
        ),
        DetailTileData(
            "Pressure",
            c.pressureInHg?.let { String.format(Locale.US, "%.2f inHg", it) } ?: "--",
            // Only station pressure (no MSL field) gets the honest label;
            // msl readings really are sea-level values.
            if (c.pressureStationLevel) "Station" else "Sea level"
        ),
        DetailTileData(
            "Visibility",
            c.visibilityMi?.let { String.format(Locale.US, "%.1f mi", it) } ?: "--",
            ""
        ),
        DetailTileData(
            "Sunrise",
            data.sun?.sunrise?.let { Fmt.clockLabel(it) } ?: "--",
            data.sun?.sunset?.let { "Sunset ${Fmt.clockLabel(it)}" } ?: ""
        ),
        DetailTileData(
            "Precip Chance",
            c.popPct?.let { "$it%" } ?: "--",
            "Next 12 hours"
        )
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    DetailTile(tile, Modifier.weight(1f))
                }
            }
        }
    }
}

private data class DetailTileData(
    val label: String,
    val value: String,
    val sub: String,
    val arrowDeg: Int? = null
)

@Composable
private fun DetailTile(tile: DetailTileData, modifier: Modifier = Modifier) {
    val palette = LocalWeatherPalette.current
    Column(
        modifier = modifier
            .height(98.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tile.arrowDeg != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up),
                    contentDescription = null,
                    tint = palette.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(12.dp)
                        .rotate((tile.arrowDeg + 180f))
                )
                Spacer(Modifier.size(5.dp))
            }
            Text(
                text = tile.label.uppercase(),
                color = palette.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Text(tile.value, color = palette.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (tile.sub.isNotEmpty()) {
            Text(tile.sub, color = palette.muted.copy(alpha = 0.85f), fontSize = 11.sp)
        }
    }
}
