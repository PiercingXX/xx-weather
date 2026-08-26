package com.xx.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xx.weather.data.model.Units

/**
 * Single-ZIP configuration + unit toggle. This is the app's only setup step —
 * no location permission, no Google Play Services (GrapheneOS-friendly).
 */
@Composable
fun SettingsDialog(
    currentZip: String?,
    currentUnits: Units,
    applying: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onApply: (zip: String, units: Units) -> Unit
) {
    var zip by remember { mutableStateOf(currentZip ?: "") }
    var units by remember { mutableStateOf(currentUnits) }

    Dialog(onDismissRequest = { if (!applying) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF161B24)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))

                Text(
                    "ZIP CODE",
                    color = Color.White.copy(alpha = 0.65f),
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
                    placeholder = { Text("e.g. 80202", color = Color.White.copy(alpha = 0.4f)) },
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
                    color = Color.White.copy(alpha = 0.65f),
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

                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !applying) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { onApply(zip, units) },
                        enabled = !applying && zip.length == 5
                    ) {
                        if (applying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving")
                        } else {
                            Text("Save")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
