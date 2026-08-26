package com.xx.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xx.weather.R
import com.xx.weather.data.Prefs
import com.xx.weather.data.WeatherRepository
import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.components.ConditionIcon
import com.xx.weather.ui.components.DailyCard
import com.xx.weather.ui.components.DetailsGrid
import com.xx.weather.ui.components.HourlyCard
import com.xx.weather.ui.components.WeatherBackground
import com.xx.weather.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** Main screen: animated backdrop, hero conditions, forecast cards, settings. */
@Composable
fun WeatherScreen(
    repository: WeatherRepository,
    context: android.content.Context,
    startTick: Long = 0L
) {
    var data by remember { mutableStateOf<WeatherData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsApplying by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var units by remember { mutableStateOf(Prefs.units(context)) }
    val scope = rememberCoroutineScope()

    fun applyResult(result: WeatherRepository.RefreshResult) {
        when (result) {
            is WeatherRepository.RefreshResult.Success -> {
                data = result.data
                errorMsg = null
                loading = false
                WidgetUpdater.updateAll(context, result.data)
            }
            is WeatherRepository.RefreshResult.Failure -> {
                errorMsg = result.message
                if (result.stale != null) data = result.stale
                loading = false
            }
        }
    }

    fun refresh(force: Boolean) {
        // Ignore redundant taps; forced refreshes (e.g. after ZIP change) always run.
        if (refreshing && !force) return
        scope.launch {
            refreshing = true
            val result = repository.refresh(force)
            applyResult(result)
            refreshing = false
        }
    }

    // Initial load AND every ON_START re-entry (MainActivity bumps startTick):
    // paint cache instantly, then ALWAYS run a force=false refresh so the
    // repository's 15-min fresh window decides whether the network is needed.
    // Composition effects run after onStart, so the first start already has
    // tick >= 1 — cold open and re-entry share one path, no double-fire.
    LaunchedEffect(startTick) {
        val cached = repository.loadCached()
        if (cached != null) {
            data = cached
            loading = false
        }
        val result = repository.refresh(force = false)
        applyResult(result)
        loading = false
    }

    val place = data?.place ?: Prefs.place(context)
    val condition = data?.current?.condition ?: Condition.CLEAR
    val isDay = data?.let { Fmt.isDaytime(it) } ?: true

    Box(Modifier.fillMaxSize()) {
        WeatherBackground(condition, isDay, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
        ) {
            // ---------------- Header ----------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_place),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = place?.let {
                                if (it.state.isBlank()) it.city else "${it.city}, ${it.state}"
                            } ?: "Set your location",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    data?.let { d ->
                        Text(
                            text = "Updated ${Fmt.updatedLabel(d.updatedAtEpochMs)} • ${d.source}",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }
                RefreshButton(refreshing) { refresh(force = true) }
                IconButton(R.drawable.ic_settings, "Settings") { showSettings = true }
            }

            errorMsg?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "⚠ $msg",
                    color = Color(0xFFFFB4AB),
                    fontSize = 13.sp
                )
            }

            // ---------------- Body ----------------
            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }

                data == null -> WelcomeContent(
                    hasError = errorMsg != null,
                    onSetZip = { showSettings = true },
                    modifier = Modifier.weight(1f)
                )

                else -> Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(14.dp))
                    HeroSection(data!!, units)
                    Spacer(Modifier.height(22.dp))
                    HourlyCard(data!!, units)
                    Spacer(Modifier.height(12.dp))
                    DailyCard(data!!, units)
                    Spacer(Modifier.height(12.dp))
                    DetailsGrid(data!!, units)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Forecast: NWS weather.gov • Open-Meteo (CC-BY 4.0)",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentZip = place?.zip,
            currentUnits = units,
            applying = settingsApplying,
            error = settingsError,
            onDismiss = {
                showSettings = false
                settingsError = null
            },
            onApply = { zip, newUnits ->
                scope.launch {
                    settingsApplying = true
                    settingsError = null
                    try {
                        if (zip != place?.zip) {
                            // Past this line Prefs hold the new ZIP: drop the
                            // old city on screen and in widgets immediately, so
                            // a failed refresh shows the welcome/error state,
                            // never the previous city's forecast.
                            repository.setZip(zip)
                            data = null
                            errorMsg = null
                            loading = true
                            WidgetUpdater.updateAll(context, null)
                        }
                        Prefs.setUnits(context, newUnits)
                        units = newUnits
                        settingsApplying = false
                        showSettings = false
                        refresh(force = true)
                    } catch (e: Exception) {
                        // setZip failed before saving: old city + caches stay.
                        settingsApplying = false
                        settingsError = e.message ?: "Could not save ZIP code."
                    }
                }
            }
        )
    }
}

// ------------------------------------------------------------ pieces

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        if (refreshing) {
            // The infinite transition only exists while spinning — no idle-frame cost.
            val spin = rememberInfiniteTransition(label = "spin")
            val angle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "angle"
            )
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = "Refreshing",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(22.dp)
                    .rotate(angle)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun IconButton(@androidx.annotation.DrawableRes resId: Int, description: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(resId),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun HeroSection(data: WeatherData, units: Units) {
    val c = data.current
    val today = data.daily.firstOrNull()
    Column {
        Text(
            text = c.conditionText,
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = Fmt.temp(c.tempF, units),
                color = Color.White,
                fontSize = 92.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-3).sp,
                lineHeight = 96.sp
            )
            Spacer(Modifier.width(8.dp))
            ConditionIcon.Draw(
                c.condition,
                Fmt.isDaytime(data),
                Modifier
                    .size(42.dp)
                    .padding(top = 18.dp)
            )
        }
        if (today != null) {
            Text(
                text = Fmt.hilo(today.hiF, today.loF, units),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }
        c.feelsLikeF?.let {
            Text(
                text = "Feels like ${Fmt.temp(it, units)}",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun WelcomeContent(hasError: Boolean, onSetZip: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(110.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (hasError) "Can't reach the sky" else "Welcome to XX Weather",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Enter a ZIP code for accurate NWS forecasts.\nNo location tracking, no Google services.",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSetZip,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f))
            ) {
                Text("Set ZIP Code", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
