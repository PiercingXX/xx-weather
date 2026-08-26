package com.xx.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.xx.weather.MainActivity
import com.xx.weather.R
import com.xx.weather.data.Prefs
import com.xx.weather.data.WeatherRepository
import com.xx.weather.data.model.WeatherData
import com.xx.weather.theme.FOREGROUND_INK
import com.xx.weather.theme.FOREGROUND_WHITE
import com.xx.weather.theme.ThemeController
import com.xx.weather.ui.Fmt
import com.xx.weather.ui.components.ConditionIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home-screen widgets (classic AppWidgetProvider + RemoteViews).
 *
 * Updates:
 *  - system updatePeriodMillis floor (30 min)
 *  - immediately after each successful in-app refresh (WidgetUpdater.updateAll)
 *  - renders instantly from cache; falls back to a network refresh if cold
 */
object WidgetUpdater {

    private val HOUR_TIME_IDS = intArrayOf(
        R.id.widget_h0_time, R.id.widget_h1_time, R.id.widget_h2_time,
        R.id.widget_h3_time, R.id.widget_h4_time, R.id.widget_h5_time
    )
    private val HOUR_ICON_IDS = intArrayOf(
        R.id.widget_h0_icon, R.id.widget_h1_icon, R.id.widget_h2_icon,
        R.id.widget_h3_icon, R.id.widget_h4_icon, R.id.widget_h5_icon
    )
    private val HOUR_TEMP_IDS = intArrayOf(
        R.id.widget_h0_temp, R.id.widget_h1_temp, R.id.widget_h2_temp,
        R.id.widget_h3_temp, R.id.widget_h4_temp, R.id.widget_h5_temp
    )
    private val HOUR_POP_IDS = intArrayOf(
        R.id.widget_h0_pop, R.id.widget_h1_pop, R.id.widget_h2_pop,
        R.id.widget_h3_pop, R.id.widget_h4_pop, R.id.widget_h5_pop
    )

    fun updateAll(context: Context, data: WeatherData?) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        updateProvider(context, mgr, ComponentName(context, CompactWeatherWidget::class.java), wide = false, data = data)
        updateProvider(context, mgr, ComponentName(context, WideWeatherWidget::class.java), wide = true, data = data)
    }

    private fun updateProvider(
        context: Context,
        mgr: AppWidgetManager,
        provider: ComponentName,
        wide: Boolean,
        data: WeatherData?
    ) {
        val ids = mgr.getAppWidgetIds(provider) ?: return
        for (id in ids) {
            mgr.updateAppWidget(id, views(context, wide, data))
        }
    }

    fun views(context: Context, wide: Boolean, data: WeatherData?): android.widget.RemoteViews {
        if (data == null) return errorViews(context, wide)

        val rv = android.widget.RemoteViews(
            context.packageName,
            if (wide) R.layout.widget_wide else R.layout.widget_compact
        )
        val units = Prefs.units(context)
        val current = data.current
        val today = data.daily.firstOrNull()

        rv.setTextViewText(R.id.widget_temp, Fmt.temp(current.tempF, units))
        rv.setTextViewText(R.id.widget_cond, current.conditionText)
        // Shared formatter omits "H:" on night-only dates instead of "H:--".
        rv.setTextViewText(
            R.id.widget_hilo,
            if (today != null) Fmt.hilo(today.hiF, today.loF, units) else ""
        )
        rv.setImageViewResource(
            R.id.widget_icon,
            ConditionIcon.res(current.condition, Fmt.isDaytime(data))
        )

        if (wide) {
            val state = data.place.state
            rv.setTextViewText(
                R.id.widget_loc,
                if (state.isBlank()) data.place.city else "${data.place.city}, $state"
            )
            rv.setTextViewText(R.id.widget_updated, "Updated ${Fmt.updatedLabel(data.updatedAtEpochMs)}")

            val hours = data.hourly.take(6)
            for (i in 0 until 6) {
                val hour = hours.getOrNull(i)
                if (hour == null) {
                    rv.setTextViewText(HOUR_TIME_IDS[i], "")
                    rv.setTextViewText(HOUR_TEMP_IDS[i], "")
                    rv.setTextViewText(HOUR_POP_IDS[i], "")
                    rv.setViewVisibility(HOUR_ICON_IDS[i], android.view.View.INVISIBLE)
                } else {
                    rv.setTextViewText(HOUR_TIME_IDS[i], Fmt.hourLabel(hour.time))
                    rv.setTextViewText(HOUR_TEMP_IDS[i], Fmt.temp(hour.tempF, units))
                    rv.setTextViewText(HOUR_POP_IDS[i], hour.popPct?.takeIf { it >= 30 }?.let { "$it%" } ?: "")
                    rv.setImageViewResource(HOUR_ICON_IDS[i], ConditionIcon.res(hour.condition, hour.isDay))
                    rv.setViewVisibility(HOUR_ICON_IDS[i], android.view.View.VISIBLE)
                }
            }
        }

        paintTheme(rv, context, wide)
        rv.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return rv
    }

    private fun paintTheme(rv: android.widget.RemoteViews, context: Context, wide: Boolean) {
        val theme = ThemeController.current(context)
        val bg = theme.background.toInt()
        val fg = (if (theme.isDark) FOREGROUND_WHITE else FOREGROUND_INK).toInt()
        val muted = (fg and 0x00FFFFFF) or (0xB3 shl 24)
        val faint = (fg and 0x00FFFFFF) or (0x80 shl 24)
        rv.setInt(R.id.widget_root, "setBackgroundColor", bg)
        rv.setTextColor(R.id.widget_temp, fg)
        rv.setTextColor(R.id.widget_cond, muted)
        rv.setTextColor(R.id.widget_hilo, faint)
        rv.setInt(R.id.widget_icon, "setColorFilter", fg)
        if (wide) {
            rv.setTextColor(R.id.widget_loc, fg)
            rv.setTextColor(R.id.widget_updated, faint)
            for (i in 0 until 6) {
                rv.setTextColor(HOUR_TIME_IDS[i], muted)
                rv.setTextColor(HOUR_TEMP_IDS[i], fg)
                rv.setInt(HOUR_ICON_IDS[i], "setColorFilter", fg)
            }
        }
    }

    private fun errorViews(context: Context, wide: Boolean): android.widget.RemoteViews {
        val layout = if (wide) R.layout.widget_wide else R.layout.widget_compact
        val rv = android.widget.RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.widget_temp, "--")
        rv.setTextViewText(R.id.widget_cond, context.getString(R.string.widget_no_data))
        rv.setTextViewText(R.id.widget_hilo, "")
        if (wide) {
            rv.setTextViewText(R.id.widget_loc, "XX Weather")
            rv.setTextViewText(R.id.widget_updated, "")
            for (i in 0 until 6) {
                rv.setTextViewText(HOUR_TIME_IDS[i], "")
                rv.setTextViewText(HOUR_TEMP_IDS[i], "")
                rv.setTextViewText(HOUR_POP_IDS[i], "")
                rv.setViewVisibility(HOUR_ICON_IDS[i], android.view.View.INVISIBLE)
            }
        }
        paintTheme(rv, context, wide)
        rv.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return rv
    }

    private fun tapIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

abstract class BaseWeatherWidget : AppWidgetProvider() {
    protected abstract val wide: Boolean

    override fun onEnabled(context: Context) {
        RefreshScheduler.ensure(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val callback = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = WeatherRepository(context.applicationContext)
                // Render last-good cache first so a saved ZIP never flashes
                // the "Tap to set ZIP" placeholder.
                val cached = repo.loadCached()
                val fresh = cached != null && WeatherRepository.isFresh(
                    fetchedAtEpochMs = cached.updatedAtEpochMs,
                    nowEpochMs = System.currentTimeMillis()
                )
                var data = cached
                if (!fresh) {
                    // WHY force=false + budget: the non-forced path re-checks
                    // freshness UNDER the process-wide mutex, so when both
                    // widgets update in the same burst the second sees the
                    // first's fresh stamp and serves cache instead of
                    // double-fetching; missing cache still triggers network.
                    // The explicit 8s budget is what actually bounds network
                    // work — Http.get blocks on socket reads that
                    // withTimeoutOrNull cannot cancel, so the deadline is
                    // enforced via OS-honored connect/read timeouts. The outer
                    // 8.5s guard stays as belt-and-suspenders. On timeout or
                    // failure data stays null/cached: last-good render kept,
                    // next tick retries.
                    withTimeoutOrNull(8_500L) {
                        when (val result = repo.refresh(force = false, budgetMs = 8_000L)) {
                            is WeatherRepository.RefreshResult.Success -> data = result.data
                            else -> {}
                        }
                    }
                }
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, WidgetUpdater.views(context, wide, data))
                }
            } catch (_: Exception) {
                // Leave widgets untouched on transient failure; next cycle retries.
            } finally {
                callback.finish()
            }
        }
    }
}

class CompactWeatherWidget : BaseWeatherWidget() {
    override val wide = false
}

class WideWeatherWidget : BaseWeatherWidget() {
    override val wide = true
}
