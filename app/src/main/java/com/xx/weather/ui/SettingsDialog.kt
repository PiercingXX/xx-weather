package com.xx.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.app.Activity
import com.xx.weather.R
import com.xx.weather.log.LogsUi
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.Units
import com.xx.weather.ui.theme.LocalWeatherPalette

data class AlertSettingsUi(
    val enabled: Boolean,
    val denied: Boolean,
    val zipLabel: String?,
    val precipEnabled: Boolean,
    val precipHours: Int,
    val tempEnabled: Boolean,
    val tempAtOrBelowF: Int,
)

/**
 * Units + add a ZIP + optional weather alerts. Saved locations can be removed
 * here or from the list view. Alert changes persist immediately.
 */
@Composable
fun SettingsDialog(
    places: List<Place>,
    currentUnits: Units,
    applying: Boolean,
    error: String?,
    alerts: AlertSettingsUi,
    onDismiss: () -> Unit,
    onAdd: (zip: String, units: Units) -> Unit,
    onUnitsOnly: (units: Units) -> Unit,
    onRemove: (zip: String) -> Unit,
    onAlertsEnabledChange: (Boolean) -> Unit,
    onPrecipEnabledChange: (Boolean) -> Unit,
    onPrecipHoursChange: (Int) -> Unit,
    onTempEnabledChange: (Boolean) -> Unit,
    onTempThresholdChange: (Int) -> Unit,
) {
    var zip by remember { mutableStateOf("") }
    var units by remember { mutableStateOf(currentUnits) }

    val palette = LocalWeatherPalette.current
    val context = LocalContext.current
    Dialog(onDismissRequest = { if (!applying) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = palette.background
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Settings", color = palette.onBackground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))

                if (places.isNotEmpty()) {
                    Text(
                        "LOCATIONS",
                        color = palette.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    places.forEach { place ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(place.displayName, color = palette.onBackground, fontSize = 15.sp)
                                Text(place.zip, color = palette.faint, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = { onRemove(place.zip) },
                                enabled = !applying
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Remove ${place.displayName}",
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Text(
                    "ADD ZIP CODE",
                    color = palette.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = zip,
                    onValueChange = { value ->
                        if (value.length <= 5 && value.all { it.isDigit() }) zip = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !applying,
                    isError = error != null,
                    placeholder = { Text("e.g. 00802", color = palette.faint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "TEMPERATURE",
                    color = palette.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = units == Units.FAHRENHEIT,
                        onClick = { units = Units.FAHRENHEIT },
                        label = { Text("°F") },
                        enabled = !applying
                    )
                    FilterChip(
                        selected = units == Units.CELSIUS,
                        onClick = { units = Units.CELSIUS },
                        label = { Text("°C") },
                        enabled = !applying
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "WEATHER ALERTS",
                    color = palette.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Enable alerts",
                        color = palette.onBackground,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = alerts.enabled,
                        onCheckedChange = onAlertsEnabledChange,
                        enabled = !applying
                    )
                }
                Text(
                    text = when {
                        alerts.denied ->
                            "Notification permission denied. Alerts stay off. Forecasts still work."
                        alerts.enabled && alerts.zipLabel != null ->
                            "Watching ${alerts.zipLabel}. Same 15-minute refresh — no extra polling."
                        alerts.enabled ->
                            "Save a ZIP to attach rain and freeze triggers. Forecasts still work."
                        else ->
                            "Optional. Forecasts work if you leave this off or deny notification permission."
                    },
                    color = palette.faint,
                    fontSize = 12.sp
                )
                if (alerts.enabled) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Rain in the next N hours", color = palette.onBackground, fontSize = 15.sp)
                            Text("PoP 50%+ or falling precip", color = palette.faint, fontSize = 12.sp)
                        }
                        Switch(
                            checked = alerts.precipEnabled,
                            onCheckedChange = onPrecipEnabledChange,
                            enabled = !applying
                        )
                    }
                    if (alerts.precipEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 6, 12).forEach { hours ->
                                FilterChip(
                                    selected = alerts.precipHours == hours,
                                    onClick = { onPrecipHoursChange(hours) },
                                    label = { Text("${hours}h") },
                                    enabled = !applying
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Temperature at or below", color = palette.onBackground, fontSize = 15.sp)
                            Text("Threshold is °F", color = palette.faint, fontSize = 12.sp)
                        }
                        Switch(
                            checked = alerts.tempEnabled,
                            onCheckedChange = onTempEnabledChange,
                            enabled = !applying
                        )
                    }
                    if (alerts.tempEnabled) {
                        Spacer(Modifier.height(6.dp))
                        var tempInput by remember(alerts.tempAtOrBelowF) {
                            mutableStateOf(alerts.tempAtOrBelowF.toString())
                        }
                        OutlinedTextField(
                            value = tempInput,
                            onValueChange = { value ->
                                if (value.length <= 4 && value.all { it == '-' || it.isDigit() }) {
                                    tempInput = value
                                    value.toIntOrNull()?.let { onTempThresholdChange(it.coerceIn(-80, 140)) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !applying,
                            suffix = { Text("°F", color = palette.faint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                TextButton(onClick = { (context as? Activity)?.let { LogsUi.show(it) } }) {
                    Text("Logs", color = palette.muted)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !applying) {
                        Text("Cancel", color = palette.muted)
                    }
                    Spacer(Modifier.width(6.dp))
                    val canSave = !applying && (zip.length == 5 || (zip.isEmpty() && units != currentUnits))
                    Button(
                        onClick = {
                            if (zip.length == 5) onAdd(zip, units) else onUnitsOnly(units)
                        },
                        enabled = canSave
                    ) {
                        if (applying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = palette.onBackground
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving")
                        } else {
                            Text(if (zip.length == 5) "Add" else "Save")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
