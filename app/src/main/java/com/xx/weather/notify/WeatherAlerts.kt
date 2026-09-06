package com.xx.weather.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xx.weather.MainActivity
import com.xx.weather.R
import com.xx.weather.data.AlertEvaluator
import com.xx.weather.data.Prefs
import com.xx.weather.data.model.WeatherData

/**
 * Posts at most one notification per trigger per fetch window.
 * Failures (permission, GrapheneOS network revoke, OEM notify bugs) are swallowed
 * so a refresh never crashes and never pretends it just updated.
 */
object WeatherAlerts {

    const val CHANNEL_ID = "weather_alerts"

    fun notificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Weather alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Optional rain and temperature alerts for saved ZIP codes"
            },
        )
    }

    fun onWeather(context: Context, data: WeatherData) {
        try {
            if (!Prefs.alertsEnabled(context)) return
            if (!notificationsGranted(context)) return
            val zip = data.place.zip
            val triggers = Prefs.alertTriggers(context, zip) ?: return
            if (!triggers.anyEnabled) return
            val fires = AlertEvaluator.evaluate(
                zip = zip,
                city = data.place.city,
                hourly = data.hourly,
                currentTempF = data.current.tempF,
                fetchedAtEpochMs = data.updatedAtEpochMs,
                nowEpochMs = System.currentTimeMillis(),
                triggers = triggers,
                alreadyNotified = Prefs.alertDedupeKeys(context),
            )
            if (fires.isEmpty()) return
            ensureChannel(context)
            for (fire in fires) post(context, fire)
            Prefs.addAlertDedupeKeys(context, fires.map { it.key })
        } catch (_: Exception) {
            // Never fail a forecast refresh because notify/permission blew up.
        }
    }

    private fun post(context: Context, fire: AlertEvaluator.Fire) {
        val tap = PendingIntent.getActivity(
            context,
            fire.key.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ZIP, fire.zip)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(fire.title)
            .setContentText(fire.text)
            .setStyle(Notification.BigTextStyle().bigText(fire.text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifyId(fire.zip, fire.kind), notification)
    }

    private fun notifyId(zip: String, kind: AlertEvaluator.Kind): Int =
        31 * zip.hashCode() + kind.ordinal
}
