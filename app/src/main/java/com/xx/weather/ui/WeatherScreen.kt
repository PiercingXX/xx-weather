package com.xx.weather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xx.weather.R
import com.xx.weather.data.Prefs
import com.xx.weather.data.WeatherRepository
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.components.ConditionIcon
import com.xx.weather.ui.components.DailyCard
import com.xx.weather.ui.components.DetailsGrid
import com.xx.weather.ui.components.HourlyCard
import com.xx.weather.ui.components.WeatherBackground
import com.xx.weather.ui.theme.LocalWeatherPalette
import com.xx.weather.widget.WidgetUpdater
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Main screen: swipeable locations, collapsed list, full forecast, settings. */
@Composable
fun WeatherScreen(
    repository: WeatherRepository,
    context: android.content.Context,
    startTick: Long = 0L
) {
    var places by remember { mutableStateOf(Prefs.places(context)) }
    var selectedZip by remember { mutableStateOf(Prefs.selectedZip(context)) }
    var collapsed by remember { mutableStateOf(Prefs.collapsed(context)) }
    var dataByZip by remember { mutableStateOf<Map<String, WeatherData?>>(emptyMap()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsApplying by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var units by remember { mutableStateOf(Prefs.units(context)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val scope = rememberCoroutineScope()
    val palette = LocalWeatherPalette.current

    val pagerState = rememberPagerState(
        initialPage = places.indexOfFirst { it.zip == selectedZip }.coerceAtLeast(0),
        pageCount = { places.size.coerceAtLeast(1) }
    )

    fun applyResult(zip: String, result: WeatherRepository.RefreshResult) {
        val next = applyRefreshResult(ZipRefreshState(dataByZip, errors), zip, result)
        dataByZip = next.dataByZip
        errors = next.errors
        if (result is WeatherRepository.RefreshResult.Success &&
            zip == Prefs.selectedZip(context)
        ) {
            WidgetUpdater.updateAll(context, result.data)
        }
        loading = false
    }

    fun refreshPlace(place: Place, force: Boolean) {
        scope.launch {
            refreshing = true
            applyResult(place.zip, repository.refresh(force = force, place = place))
            places = Prefs.places(context)
            refreshing = false
        }
    }

    fun refreshVisible(force: Boolean) {
        if (refreshing && !force) return
        if (collapsed) {
            scope.launch {
                refreshing = true
                for (p in places) {
                    applyResult(p.zip, repository.refresh(force = force, place = p))
                }
                places = Prefs.places(context)
                refreshing = false
            }
        } else {
            val p = places.find { it.zip == selectedZip } ?: places.firstOrNull() ?: return
            refreshPlace(p, force)
        }
    }

    LaunchedEffect(startTick) {
        if (!shouldRefreshOnStartTick(startTick)) return@LaunchedEffect
        val list = Prefs.places(context)
        places = list
        selectedZip = Prefs.selectedZip(context)
        if (list.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        for (p in list) {
            repository.loadCached(p)?.let { dataByZip = dataByZip + (p.zip to it) }
        }
        if (dataByZip.isNotEmpty()) loading = false
        val selected = list.find { it.zip == selectedZip } ?: list.first()
        applyResult(selected.zip, repository.refresh(force = false, place = selected))
        for (p in list) {
            if (p.zip != selected.zip) {
                applyResult(p.zip, repository.refresh(force = false, place = p))
            }
        }
        places = Prefs.places(context)
        loading = false
    }

    LaunchedEffect(startTick) {
        while (true) {
            delay(15 * 60 * 1000L)
            refreshVisible(force = false)
        }
    }

    LaunchedEffect(selectedZip, collapsed, places) {
        if (collapsed || places.isEmpty()) return@LaunchedEffect
        val i = places.indexOfFirst { it.zip == selectedZip }
        if (i >= 0 && pagerState.currentPage != i) pagerState.scrollToPage(i)
    }

    LaunchedEffect(pagerState, places) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val p = places.getOrNull(page) ?: return@collect
            if (p.zip != selectedZip) {
                selectedZip = p.zip
                Prefs.setSelectedZip(context, p.zip)
                dataByZip[p.zip]?.let { WidgetUpdater.updateAll(context, it) }
            }
        }
    }

    val pagePlace = places.getOrNull(pagerState.currentPage)
        ?: places.find { it.zip == selectedZip }
        ?: places.firstOrNull()
    val pageData = pagePlace?.let { dataByZip[it.zip] }
    val headerPlace = if (collapsed) null else pagePlace
    val errorMsg = if (collapsed) null else pagePlace?.let { errors[it.zip] }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
        ) {
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
                            tint = palette.onBackground.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = when {
                                collapsed -> "Locations"
                                headerPlace != null -> headerPlace.displayName
                                else -> "Set your location"
                            },
                            color = palette.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!collapsed) {
                        pageData?.let { d ->
                            Text(
                                text = "Updated ${Fmt.updatedLabel(d.updatedAtEpochMs)} • ${d.source}",
                                color = palette.onBackground.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(
                            text = if (places.isEmpty()) "Add a ZIP code" else "${places.size} saved",
                            color = palette.onBackground.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }
                if (places.isNotEmpty()) {
                    IconButton(
                        if (collapsed) R.drawable.ic_place else R.drawable.ic_list,
                        if (collapsed) "Full forecast" else "Location list"
                    ) {
                        collapsed = !collapsed
                        Prefs.setCollapsed(context, collapsed)
                    }
                }
                if (collapsed) {
                    IconButton(R.drawable.ic_add, "Add location") { showSettings = true }
                }
                RefreshButton(refreshing) { refreshVisible(force = true) }
                IconButton(R.drawable.ic_settings, "Settings") { showSettings = true }
            }

            if (!collapsed && places.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    places.forEachIndexed { i, _ ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == pagerState.currentPage) 7.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    palette.onBackground.copy(
                                        alpha = if (i == pagerState.currentPage) 0.95f else 0.35f
                                    )
                                )
                        )
                    }
                }
            }

            errorMsg?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(text = "⚠ $msg", color = palette.error, fontSize = 13.sp)
            }

            when {
                loading && dataByZip.isEmpty() && places.isNotEmpty() -> {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.onBackground)
                    }
                }

                places.isEmpty() -> WelcomeContent(
                    hasError = errors.isNotEmpty(),
                    onSetZip = { showSettings = true },
                    modifier = Modifier.weight(1f)
                )

                collapsed -> LocationList(
                    places = places,
                    dataByZip = dataByZip,
                    errors = errors,
                    units = units,
                    onSelect = { place ->
                        selectedZip = place.zip
                        Prefs.setSelectedZip(context, place.zip)
                        collapsed = false
                        Prefs.setCollapsed(context, false)
                        dataByZip[place.zip]?.let { WidgetUpdater.updateAll(context, it) }
                    },
                    onRemove = { place ->
                        scope.launch {
                            repository.removeZip(place.zip)
                            places = Prefs.places(context)
                            selectedZip = Prefs.selectedZip(context)
                            dataByZip = dataByZip - place.zip
                            errors = errors - place.zip
                            if (places.isEmpty()) {
                                collapsed = false
                                Prefs.setCollapsed(context, false)
                                WidgetUpdater.updateAll(context, null)
                            } else {
                                dataByZip[selectedZip]?.let { WidgetUpdater.updateAll(context, it) }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1
                ) { page ->
                    val place = places.getOrNull(page)
                    val data = place?.let { dataByZip[it.zip] }
                    when {
                        data != null -> WeatherDetail(
                            data,
                            units,
                            onDayClick = { selectedDay = it },
                        )
                        place != null && !zipShowsSpinner(place.zip, dataByZip, errors) ->
                            ForecastUnavailable()
                        place != null -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = palette.onBackground)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            places = places,
            currentUnits = units,
            applying = settingsApplying,
            error = settingsError,
            onDismiss = {
                showSettings = false
                settingsError = null
            },
            onAdd = { zip, newUnits ->
                scope.launch {
                    settingsApplying = true
                    settingsError = null
                    try {
                        Prefs.setUnits(context, newUnits)
                        units = newUnits
                        val place = repository.addZip(zip)
                        places = Prefs.places(context)
                        selectedZip = place.zip
                        collapsed = false
                        Prefs.setCollapsed(context, false)
                        settingsApplying = false
                        showSettings = false
                        refreshPlace(place, force = true)
                    } catch (e: Exception) {
                        settingsApplying = false
                        settingsError = e.message ?: "Could not save ZIP code."
                    }
                }
            },
            onUnitsOnly = { newUnits ->
                Prefs.setUnits(context, newUnits)
                units = newUnits
                showSettings = false
                dataByZip[selectedZip]?.let { WidgetUpdater.updateAll(context, it) }
            },
            onRemove = { zip ->
                scope.launch {
                    repository.removeZip(zip)
                    places = Prefs.places(context)
                    selectedZip = Prefs.selectedZip(context)
                    dataByZip = dataByZip - zip
                    errors = errors - zip
                    if (places.isEmpty()) {
                        showSettings = false
                        WidgetUpdater.updateAll(context, null)
                    }
                }
            }
        )
    }

    val dayData = pageData
    val day = selectedDay
    if (day != null && dayData != null) {
        DayForecastDialog(
            data = dayData,
            date = day,
            units = units,
            onDismiss = { selectedDay = null },
        )
    }
}

@Composable
private fun WeatherDetail(
    data: WeatherData,
    units: Units,
    onDayClick: (LocalDate) -> Unit,
) {
    val palette = LocalWeatherPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(14.dp))
        HeroSection(data, units, onDateClick = onDayClick)
        Spacer(Modifier.height(22.dp))
        HourlyCard(data, units)
        Spacer(Modifier.height(12.dp))
        DailyCard(data, units, onDayClick = onDayClick)
        Spacer(Modifier.height(12.dp))
        DetailsGrid(data, units)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Forecast: NWS weather.gov • Open-Meteo (CC-BY 4.0)",
            color = palette.onBackground.copy(alpha = 0.45f),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val palette = LocalWeatherPalette.current
    androidx.compose.material3.IconButton(onClick = onClick) {
        if (refreshing) {
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
                tint = palette.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(22.dp)
                    .rotate(angle)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                tint = palette.onBackground,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun IconButton(@androidx.annotation.DrawableRes resId: Int, description: String, onClick: () -> Unit) {
    val palette = LocalWeatherPalette.current
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(resId),
            contentDescription = description,
            tint = palette.onBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun HeroSection(
    data: WeatherData,
    units: Units,
    onDateClick: (LocalDate) -> Unit,
) {
    val palette = LocalWeatherPalette.current
    val c = data.current
    val today = data.daily.firstOrNull()
    val todayDate = today?.date ?: LocalDate.now()
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        WeatherBackground(c.condition, Fmt.isDaytime(data), Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background.copy(alpha = 0.28f))
        )
        val heroFg = palette.onBackground
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = Fmt.fullDate(todayDate),
                color = heroFg,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onDateClick(todayDate) }
            )
            Text(
                text = c.conditionText,
                color = heroFg.copy(alpha = 0.92f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = Fmt.temp(c.tempF, units),
                    color = heroFg,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    lineHeight = 76.sp
                )
                Spacer(Modifier.width(8.dp))
                ConditionIcon.Draw(
                    c.condition,
                    Fmt.isDaytime(data),
                    Modifier
                        .size(36.dp)
                        .padding(top = 14.dp),
                    tint = heroFg
                )
            }
            if (today != null) {
                Text(
                    text = Fmt.hilo(today.hiF, today.loF, units),
                    color = heroFg.copy(alpha = 0.92f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            c.feelsLikeF?.let {
                Text(
                    text = "Feels like ${Fmt.temp(it, units)}",
                    color = heroFg.copy(alpha = 0.78f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ForecastUnavailable() {
    val palette = LocalWeatherPalette.current
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Couldn't load this forecast.",
            color = palette.onBackground.copy(alpha = 0.75f),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun WelcomeContent(hasError: Boolean, onSetZip: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWeatherPalette.current
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
                color = palette.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Enter a ZIP code for accurate NWS forecasts.\nNo location tracking, no Google services.",
                color = palette.onBackground.copy(alpha = 0.70f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSetZip,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.onBackground.copy(alpha = 0.18f))
            ) {
                Text("Set ZIP Code", color = palette.onBackground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
