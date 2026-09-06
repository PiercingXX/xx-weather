package com.xx.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.xx.weather.data.WeatherRepository
import com.xx.weather.theme.ThemeController
import com.xx.weather.ui.WeatherScreen
import com.xx.weather.ui.theme.XXWeatherTheme

class MainActivity : ComponentActivity() {

    /**
     * Starts at 0 so the first `setContent` composition does not fetch.
     * `onStart` is the first increment; WeatherScreen refreshes only when
     * the tick is greater than 0 (and again on later resumes).
     */
    private val startTick = mutableStateOf(0L)

    override fun onStart() {
        super.onStart()
        startTick.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by ThemeController.theme.collectAsState()
            XXWeatherTheme(theme) {
                val appContext = applicationContext
                val repository = remember { WeatherRepository(appContext) }
                WeatherScreen(
                    repository = repository,
                    context = appContext,
                    startTick = startTick.value
                )
            }
        }
    }
}
