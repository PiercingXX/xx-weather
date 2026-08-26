package com.xx.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.xx.weather.data.WeatherRepository
import com.xx.weather.ui.WeatherScreen
import com.xx.weather.ui.theme.XXWeatherTheme

class MainActivity : ComponentActivity() {

    /** Bumps on every ON_START so WeatherScreen re-runs its stale-check refresh. */
    private val startTick = mutableStateOf(0L)

    override fun onStart() {
        super.onStart()
        startTick.value++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XXWeatherTheme {
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
