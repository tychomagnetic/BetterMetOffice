package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PressureUnit
import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.data.repository.ApiKeyTestResult
import com.example.ui.components.ApiDebugSheet
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoHeroText
import com.example.ui.theme.BentoPillAccent
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var apiKeyInput by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var clientSecretInput by remember(uiState.clientSecret) { mutableStateOf(uiState.clientSecret) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isSecretVisible by remember { mutableStateOf(false) }
    var isHelpExpanded by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Sources",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Weather",
                            tint = BentoPurplePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BentoTile.copy(alpha = 0.95f)
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        containerColor = BentoHero,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Section 1: Active Data Source Selection
            SettingsCard(
                title = "Data Source Selection",
                icon = Icons.Default.Public,
                subtitle = "Toggle between official UK Met Office data and open meteorological data"
            ) {
                // Met Office DataHub Option
                val hasApiKey = uiState.apiKey.isNotBlank()
                Surface(
                    onClick = {
                        viewModel.toggleDataSource(useMetOffice = true)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (uiState.useMetOfficeSource) Color(0xFFEDE7F6) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (uiState.useMetOfficeSource) BentoPurplePrimary else BentoBorder.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("source_met_office_option")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (uiState.useMetOfficeSource) BentoPurplePrimary else Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (uiState.useMetOfficeSource) Color.White else BentoTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Met Office DataHub API",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = BentoTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (hasApiKey) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = "Key Set",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFFF3E0)
                                    ) {
                                        Text(
                                            text = "Key Needed",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFFE65100),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Official UK Site-Specific 1-hourly & 3-hourly forecast models",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Open Data Option
                Surface(
                    onClick = {
                        viewModel.toggleDataSource(useMetOffice = false)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (!uiState.useMetOfficeSource) Color(0xFFE0F2FE) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (!uiState.useMetOfficeSource) Color(0xFF0284C7) else BentoBorder.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("source_open_data_option")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (!uiState.useMetOfficeSource) Color(0xFF0284C7) else Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = if (!uiState.useMetOfficeSource) Color.White else BentoTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open Meteorological Model",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                            )
                            Text(
                                text = "Global high-resolution numerical weather prediction (No key required)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Section 2: Met Office API Key Configuration
            SettingsCard(
                title = "Met Office API Key Configuration",
                icon = Icons.Default.Key,
                subtitle = "Enter your credentials from the Met Office Weather DataHub portal"
            ) {
                // Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (uiState.apiKey.isNotBlank()) Color(0xFFE8F5E9) else Color(0xFFFFF8E1))
                        .border(
                            1.dp,
                            if (uiState.apiKey.isNotBlank()) Color(0xFFA5D6A7) else Color(0xFFFFE082),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.apiKey.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (uiState.apiKey.isNotBlank()) Color(0xFF2E7D32) else Color(0xFFF57F17),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.apiKey.isNotBlank()) {
                            "API Key Configured (${uiState.apiKey.take(4)}...${uiState.apiKey.takeLast(4)})"
                        } else {
                            "No API key saved — Using open meteorological fallback"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (uiState.apiKey.isNotBlank()) Color(0xFF1B5E20) else Color(0xFFE65100),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // API Key Field
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key / Client ID") },
                    placeholder = { Text("e.g. 5a1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d") },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (apiKeyInput.isNotEmpty()) {
                                IconButton(onClick = { apiKeyInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = BentoTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isApiKeyVisible) "Hide Key" else "Show Key",
                                    tint = BentoTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BentoPurplePrimary,
                        unfocusedBorderColor = BentoBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = BentoHero
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("api_key_text_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Client Secret (Optional)
                OutlinedTextField(
                    value = clientSecretInput,
                    onValueChange = { clientSecretInput = it },
                    label = { Text("Client Secret (Optional)") },
                    placeholder = { Text("Only if your DataHub plan requires it") },
                    singleLine = true,
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                            Icon(
                                imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isSecretVisible) "Hide Secret" else "Show Secret",
                                tint = BentoTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BentoPurplePrimary,
                        unfocusedBorderColor = BentoBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = BentoHero
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    modifier = Modifier.fillMaxWidth().testTag("client_secret_text_field")
                )

                // Test Connection Status Banner
                if (uiState.apiKeyTestStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    when (val status = uiState.apiKeyTestStatus) {
                        is ApiKeyTestResult.Success -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFE8F5E9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5D6A7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF1B5E20),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                        is ApiKeyTestResult.Error -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFEBEE),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = Color(0xFFC62828),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status.message,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFB71C1C),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                        null -> {}
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Test Button
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.testApiKey(apiKeyInput, clientSecretInput)
                        },
                        enabled = apiKeyInput.isNotBlank() && !uiState.isTestingApiKey,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("test_api_key_button")
                    ) {
                        if (uiState.isTestingApiKey) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = BentoPurplePrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Text("Test")
                        }
                    }

                    // Save Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.saveApiKey(apiKeyInput, clientSecretInput)
                            Toast.makeText(context, "API Key Saved & Applied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BentoPurplePrimary
                        ),
                        modifier = Modifier.weight(1.3f).testTag("save_api_key_button")
                    ) {
                        Text("Save & Apply")
                    }

                    // Clear Button (if key is configured)
                    if (uiState.apiKey.isNotBlank() || apiKeyInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                apiKeyInput = ""
                                clientSecretInput = ""
                                viewModel.clearApiKey()
                                Toast.makeText(context, "API Key cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFEBEE))
                                .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(12.dp))
                                .testTag("clear_api_key_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear API Key",
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Expandable Help / Tutorial for getting API Key
                Surface(
                    onClick = { isHelpExpanded = !isHelpExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = BentoHero,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = BentoPurplePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "How to get a free Met Office API key",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = BentoPurplePrimary
                                    )
                                )
                            }
                            Text(
                                text = if (isHelpExpanded) "Hide" else "Show",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = BentoTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        AnimatedVisibility(visible = isHelpExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = "1. Visit the Met Office Weather DataHub portal:\n   https://datahub.metoffice.gov.uk/\n2. Register a free developer account.\n3. Create an application and subscribe to the 'Site-Specific' API plan (free tier gives 360 requests/min).\n4. Copy your Client ID (API Key) and paste it above.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = BentoTextPrimary,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://datahub.metoffice.gov.uk/"))
                                        context.startActivity(browserIntent)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open DataHub Portal in Browser", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Units of Measurement
            SettingsCard(
                title = "Units of Measurement",
                icon = Icons.Default.Tune,
                subtitle = "Customize temperature, wind speed, and pressure units"
            ) {
                // Temperature Units
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnitOptionButton(
                        label = "Celsius (°C)",
                        selected = uiState.tempUnit == TemperatureUnit.CELSIUS,
                        onClick = { viewModel.setTemperatureUnit(TemperatureUnit.CELSIUS) },
                        modifier = Modifier.weight(1f).testTag("temp_unit_celsius")
                    )
                    UnitOptionButton(
                        label = "Fahrenheit (°F)",
                        selected = uiState.tempUnit == TemperatureUnit.FAHRENHEIT,
                        onClick = { viewModel.setTemperatureUnit(TemperatureUnit.FAHRENHEIT) },
                        modifier = Modifier.weight(1f).testTag("temp_unit_fahrenheit")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Wind Speed Units
                Text(
                    text = "Wind Speed",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnitOptionButton(
                        label = "mph",
                        selected = uiState.windUnit == WindSpeedUnit.MPH,
                        onClick = { viewModel.setWindSpeedUnit(WindSpeedUnit.MPH) },
                        modifier = Modifier.weight(1f).testTag("wind_unit_mph")
                    )
                    UnitOptionButton(
                        label = "km/h",
                        selected = uiState.windUnit == WindSpeedUnit.KPH,
                        onClick = { viewModel.setWindSpeedUnit(WindSpeedUnit.KPH) },
                        modifier = Modifier.weight(1f).testTag("wind_unit_kph")
                    )
                    UnitOptionButton(
                        label = "knots",
                        selected = uiState.windUnit == WindSpeedUnit.KNOTS,
                        onClick = { viewModel.setWindSpeedUnit(WindSpeedUnit.KNOTS) },
                        modifier = Modifier.weight(1f).testTag("wind_unit_knots")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pressure Units
                Text(
                    text = "Atmospheric Pressure",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnitOptionButton(
                        label = "hPa",
                        selected = uiState.pressureUnit == PressureUnit.HPA,
                        onClick = { viewModel.setPressureUnit(PressureUnit.HPA) },
                        modifier = Modifier.weight(1f).testTag("pressure_unit_hpa")
                    )
                    UnitOptionButton(
                        label = "mbar",
                        selected = uiState.pressureUnit == PressureUnit.MBAR,
                        onClick = { viewModel.setPressureUnit(PressureUnit.MBAR) },
                        modifier = Modifier.weight(1f).testTag("pressure_unit_mbar")
                    )
                    UnitOptionButton(
                        label = "inHg",
                        selected = uiState.pressureUnit == PressureUnit.INHG,
                        onClick = { viewModel.setPressureUnit(PressureUnit.INHG) },
                        modifier = Modifier.weight(1f).testTag("pressure_unit_inhg")
                    )
                }
            }

            // Section 4: Developer Diagnostics & Raw Debugger
            SettingsCard(
                title = "Diagnostics & Raw API Inspector",
                icon = Icons.Default.DataObject,
                subtitle = "Inspect raw network responses, HTTP status, and coordinates"
            ) {
                val debug = uiState.debugInfo
                if (debug != null) {
                    val isSuccess = debug.httpStatusCode in 200..299 && debug.errorDetails == null
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoHero)
                            .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Last Endpoint Status",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ) {
                                Text(
                                    text = if (isSuccess) "HTTP ${debug.httpStatusCode} OK" else "HTTP ${debug.httpStatusCode} Error",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Source: ${debug.dataSource.displayName}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BentoTextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                        Text(
                            text = "Latency: ${debug.responseTimeMs} ms • Updated: ${debug.timestamp}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BentoTextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = { viewModel.openDebugSheet() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BentoTile,
                        contentColor = BentoPurplePrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoPurplePrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("open_debug_inspector_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DataObject,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Deep API & Coordinates Inspector", fontWeight = FontWeight.SemiBold)
                }
            }

            // Section 5: Home Screen Widget Sync
            SettingsCard(
                title = "Home Screen Widget",
                icon = Icons.Default.Widgets,
                subtitle = "48-hour interactive forecast strip with 3-hour shift controls"
            ) {
                Text(
                    text = "The Glance widget displays 5 hourly periods with instantaneous < and > navigation and direct app launching.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = BentoTextSecondary,
                        fontSize = 12.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.syncWidgetNow()
                        Toast.makeText(context, "Widget state refreshed!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("sync_widget_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Home Screen Widget Now")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal Debug Inspector Sheet (if triggered from Settings)
    if (uiState.isDebugSheetOpen) {
        ApiDebugSheet(
            isOpen = uiState.isDebugSheetOpen,
            debugInfo = uiState.debugInfo,
            currentLocation = uiState.selectedLocation,
            customLatInput = uiState.customLatInput,
            customLonInput = uiState.customLonInput,
            coordinateTestResult = uiState.coordinateTestResult,
            isTestingCoordinates = uiState.isTestingCoordinates,
            rawGeocodingQueryInput = uiState.rawGeocodingQueryInput,
            rawGeocodingResultJson = uiState.rawGeocodingResultJson,
            rawGeocodingLocations = uiState.rawGeocodingLocations,
            isTestingGeocoding = uiState.isTestingGeocoding,
            onClose = { viewModel.closeDebugSheet() },
            onRefreshCurrent = { viewModel.loadWeather(isRefresh = true) },
            onUpdateCustomLat = { viewModel.updateCustomLat(it) },
            onUpdateCustomLon = { viewModel.updateCustomLon(it) },
            onNudgeCoordinates = { latDelta, lonDelta -> viewModel.nudgeCoordinates(latDelta, lonDelta) },
            onRunCoordinateTest = { lat, lon -> viewModel.runCoordinateTest(lat, lon) },
            onUpdateGeocodingQuery = { viewModel.updateRawGeocodingQuery(it) },
            onSelectLocationFromGeocode = { loc ->
                viewModel.selectLocation(loc)
                viewModel.closeDebugSheet()
            }
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoTile),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(BentoPurplePrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = BentoTextSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BentoBorder.copy(alpha = 0.4f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
fun UnitOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) BentoPurplePrimary else BentoHero,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) BentoPurplePrimary else BentoBorder.copy(alpha = 0.6f)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else BentoTextPrimary,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}
