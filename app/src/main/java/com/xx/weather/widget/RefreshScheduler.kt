package com.xx.weather.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xx.weather.data.Prefs
import com.xx.weather.data.WeatherRepository
import java.util.concurrent.TimeUnit

/** 15-minute stale-check refresh for the selected ZIP and both widgets. */
object RefreshScheduler {
    const val UNIQUE_NAME = "xx_weather_refresh"
    const val INTERVAL_MINUTES = 15L

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val place = Prefs.place(app) ?: return Result.success()
        return when (val result = WeatherRepository(app).refresh(force = false, place = place)) {
            is WeatherRepository.RefreshResult.Success -> {
                WidgetUpdater.updateAll(app, result.data)
                Result.success()
            }
            is WeatherRepository.RefreshResult.Failure -> {
                result.stale?.let { WidgetUpdater.updateAll(app, it) }
                Result.retry()
            }
        }
    }
}
