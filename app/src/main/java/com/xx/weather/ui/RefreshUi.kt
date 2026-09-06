package com.xx.weather.ui

import com.xx.weather.data.WeatherRepository
import com.xx.weather.data.model.WeatherData

/** In-memory ZIP maps after a refresh. Null values are failure placeholders. */
data class ZipRefreshState(
    val dataByZip: Map<String, WeatherData?> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
)

/**
 * Merge one [WeatherRepository.RefreshResult] into the pager maps.
 *
 * Failure with no stale still inserts `zip to null` so the pager can tell
 * "tried and failed" from "still loading". Last-good in-memory data is kept
 * when a later refresh fails without a disk stale payload.
 */
fun applyRefreshResult(
    state: ZipRefreshState,
    zip: String,
    result: WeatherRepository.RefreshResult,
): ZipRefreshState = when (result) {
    is WeatherRepository.RefreshResult.Success -> ZipRefreshState(
        dataByZip = state.dataByZip + (zip to result.data),
        errors = state.errors - zip,
    )
    is WeatherRepository.RefreshResult.Failure -> ZipRefreshState(
        dataByZip = when {
            result.stale != null -> state.dataByZip + (zip to result.stale)
            zip in state.dataByZip -> state.dataByZip
            else -> state.dataByZip + (zip to null)
        },
        errors = state.errors + (zip to result.message),
    )
}

/** Spinner only before [applyRefreshResult] has run for [zip]. */
fun zipShowsSpinner(
    zip: String,
    dataByZip: Map<String, WeatherData?>,
    errors: Map<String, String>,
): Boolean = zip !in dataByZip && zip !in errors

/**
 * `startTick` is 0 during the first `setContent` composition; `onStart`
 * increments it. Fetch only when the tick is past that initial 0.
 */
fun shouldRefreshOnStartTick(startTick: Long): Boolean = startTick > 0L
