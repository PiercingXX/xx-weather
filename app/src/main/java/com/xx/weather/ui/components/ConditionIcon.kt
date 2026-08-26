package com.xx.weather.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.xx.weather.R
import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.Conditions

/** Maps normalized conditions to our hand-drawn vector assets. */
object ConditionIcon {

    @DrawableRes
    fun res(condition: Condition, isDay: Boolean): Int = when (condition) {
        Condition.CLEAR -> if (isDay) R.drawable.ic_w_clear else R.drawable.ic_night_clear
        Condition.MOSTLY_CLEAR -> if (isDay) R.drawable.ic_w_mostly_clear else R.drawable.ic_night_clear
        Condition.PARTLY_CLOUDY -> R.drawable.ic_w_partly
        Condition.CLOUDY -> R.drawable.ic_w_cloudy
        Condition.OVERCAST -> R.drawable.ic_w_overcast
        Condition.FOG -> R.drawable.ic_w_fog
        Condition.DRIZZLE -> R.drawable.ic_w_drizzle
        Condition.RAIN -> R.drawable.ic_w_rain
        Condition.HEAVY_RAIN -> R.drawable.ic_w_heavy_rain
        Condition.SHOWERS -> R.drawable.ic_w_showers
        Condition.THUNDERSTORM -> R.drawable.ic_w_tstorm
        Condition.SNOW -> R.drawable.ic_w_snow
        Condition.HEAVY_SNOW -> R.drawable.ic_w_heavy_snow
        Condition.SLEET -> R.drawable.ic_w_sleet
        Condition.WINDY -> R.drawable.ic_w_wind
    }

    @Composable
    fun Draw(
        condition: Condition,
        isDay: Boolean,
        modifier: Modifier = Modifier,
        tint: Color = Color.White
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(res(condition, isDay)),
            contentDescription = Conditions.label(condition),
            modifier = modifier,
            tint = tint
        )
    }
}
