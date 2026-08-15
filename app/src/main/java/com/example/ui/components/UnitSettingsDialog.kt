package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PressureUnit
import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile

@Composable
fun UnitSettingsDialog(
    currentTempUnit: TemperatureUnit,
    currentWindUnit: WindSpeedUnit,
    currentPressureUnit: PressureUnit,
    onTempUnitChange: (TemperatureUnit) -> Unit,
    onWindUnitChange: (WindSpeedUnit) -> Unit,
    onPressureUnitChange: (PressureUnit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = BentoTile,
        modifier = modifier.testTag("units_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = BentoPurplePrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Forecast Units",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Temperature Units
                Text(
                    text = "TEMPERATURE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                TemperatureUnit.entries.forEach { unit ->
                    UnitOptionRow(
                        label = when (unit) {
                            TemperatureUnit.CELSIUS -> "Celsius (°C)"
                            TemperatureUnit.FAHRENHEIT -> "Fahrenheit (°F)"
                        },
                        isSelected = currentTempUnit == unit,
                        onClick = { onTempUnitChange(unit) }
                    )
                }

                HorizontalDivider(
                    color = BentoBorder.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Wind Speed Units
                Text(
                    text = "WIND SPEED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                WindSpeedUnit.entries.forEach { unit ->
                    UnitOptionRow(
                        label = when (unit) {
                            WindSpeedUnit.MPH -> "Miles per hour (mph)"
                            WindSpeedUnit.KPH -> "Kilometers per hour (km/h)"
                            WindSpeedUnit.KNOTS -> "Knots (kts)"
                        },
                        isSelected = currentWindUnit == unit,
                        onClick = { onWindUnitChange(unit) }
                    )
                }

                HorizontalDivider(
                    color = BentoBorder.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Pressure Units
                Text(
                    text = "AIR PRESSURE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                PressureUnit.entries.forEach { unit ->
                    UnitOptionRow(
                        label = when (unit) {
                            PressureUnit.HPA -> "Hectopascals (hPa)"
                            PressureUnit.MBAR -> "Millibars (mbar)"
                            PressureUnit.INHG -> "Inches of Mercury (inHg)"
                        },
                        isSelected = currentPressureUnit == unit,
                        onClick = { onPressureUnitChange(unit) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BentoPurplePrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
fun UnitOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) BentoPurplePrimary else BentoTextPrimary
            )
        )
        Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) BentoPurplePrimary else BentoTextSecondary
        )
    }
}

