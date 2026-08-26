package com.xx.weather

import android.app.Application
import com.xx.weather.theme.ThemeController
import com.xx.weather.widget.RefreshScheduler

class WeatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeController.init(this)
        RefreshScheduler.ensure(this)
    }
}
