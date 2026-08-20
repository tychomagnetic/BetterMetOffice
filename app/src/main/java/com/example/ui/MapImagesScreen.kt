package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapImagesScreen(
    viewModel: MapImagesViewModel,
    onBack: () -> Unit,
    onConfigureKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadCatalog() }
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Weather maps", fontWeight = FontWeight.Bold, color = BentoTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to weather", tint = BentoPurplePrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadCatalog(forceRefresh = true) },
                        enabled = !state.isLoadingCatalog && state.apiKeyConfigured,
                        modifier = Modifier.testTag("map_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, "Check for latest map run", tint = BentoPurplePrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoTile.copy(alpha = 0.98f)),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.availableOrders.size > 1) {
                MapDropdown(
                    label = "Map order",
                    selected = state.selectedOrderId,
                    options = state.availableOrders.map { it.orderId },
                    optionLabel = { id -> state.availableOrders.firstOrNull { it.orderId == id }?.name ?: id },
                    onSelect = viewModel::selectOrder
                )
            }

            if (state.layers.isNotEmpty()) {
                MapDropdown(
                    label = "Map type",
                    selected = state.selectedLayerId,
                    options = state.layers,
                    optionLabel = ::friendlyLayerName,
                    onSelect = viewModel::selectLayer,
                    modifier = Modifier.testTag("map_type_selector")
                )
            }

            val selectedFrame = state.selectedFrame
            val availableDays = state.leadTimes.mapNotNull { forecastDate(state.runDateTime, it) }.distinct()
            val selectedDay = selectedFrame?.let { forecastDate(it.runDateTime, it.leadTimeHours) }
            if (selectedFrame != null && selectedDay != null) {
                MapDropdown(
                    label = "Forecast day",
                    selected = selectedDay,
                    options = availableDays,
                    optionLabel = ::forecastDayLabel,
                    onSelect = { day ->
                        viewModel.selectDay(
                            state.leadTimes.filter { forecastDate(state.runDateTime, it) == day }
                        )
                    },
                    modifier = Modifier.testTag("map_day_selector")
                )
                Text(
                    text = "Model run ${formatRunTime(state.runDateTime)} · +${state.selectedLeadTimeHours}h",
                    style = MaterialTheme.typography.bodySmall,
                    color = BentoTextSecondary
                )
            }

            state.warningMessage?.let { MessageSurface(it, Color(0xFFFFF8E1), Color(0xFFE65100)) }
            state.errorMessage?.let { MessageSurface(it, Color(0xFFFFEBEE), Color(0xFFB71C1C)) }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFE9EDF2),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(800f / 875f),
                        contentAlignment = Alignment.Center
                    ) {
                        state.bitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "${friendlyLayerName(state.selectedLayerId)} forecast map",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (state.isLoadingCatalog || state.isLoadingImage) {
                            CircularProgressIndicator(color = BentoPurplePrimary)
                        } else if (state.bitmap == null && state.errorMessage == null) {
                            Text("No map image available", color = BentoTextSecondary)
                        }
                    }
                    if (selectedDay != null) {
                        val dayIndex = availableDays.indexOf(selectedDay)
                        val dayLeadTimes = state.leadTimes.filter { forecastDate(state.runDateTime, it) == selectedDay }
                        val previousDayLeads = availableDays.getOrNull(dayIndex - 1)?.let { day ->
                            state.leadTimes.filter { forecastDate(state.runDateTime, it) == day }
                        }.orEmpty()
                        val nextDayLeads = availableDays.getOrNull(dayIndex + 1)?.let { day ->
                            state.leadTimes.filter { forecastDate(state.runDateTime, it) == day }
                        }.orEmpty()
                        MapTimelineSlider(
                            runDateTime = state.runDateTime,
                            leadTimes = dayLeadTimes,
                            selectedLeadTime = state.selectedLeadTimeHours,
                            onSelect = viewModel::selectLeadTime,
                            onPreviousDay = previousDayLeads.takeIf { it.isNotEmpty() }?.let { leads ->
                                { viewModel.selectDay(leads) }
                            },
                            onNextDay = nextDayLeads.takeIf { it.isNotEmpty() }?.let { leads ->
                                { viewModel.selectDay(leads) }
                            },
                            isPreloading = state.isPreloadingDay,
                            loadedCount = state.preloadedFrameCount,
                            totalCount = state.dayFrameCount
                        )
                    }
                }
            }

            if (!state.apiKeyConfigured) {
                Button(onClick = onConfigureKey, modifier = Modifier.fillMaxWidth()) {
                    Text("Configure Map Images API key")
                }
            }
            Text(
                text = "The app checks for a new manifest once after each 00:00 or 12:00 UTC run boundary. Downloaded run-stamped frames are reused; the refresh button forces an early check.",
                style = MaterialTheme.typography.bodySmall,
                color = BentoTextSecondary
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> MapDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = BentoTextPrimary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = BentoTextPrimary,
                fontWeight = FontWeight.SemiBold
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = BentoTextPrimary,
                unfocusedTextColor = BentoTextPrimary,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = BentoPurplePrimary,
                unfocusedBorderColor = BentoBorder,
                focusedTrailingIconColor = BentoPurplePrimary,
                unfocusedTrailingIconColor = BentoTextPrimary
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), color = BentoTextPrimary) },
                    onClick = { expanded = false; onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun MapTimelineSlider(
    runDateTime: String,
    leadTimes: List<Int>,
    selectedLeadTime: Int,
    onSelect: (Int) -> Unit,
    onPreviousDay: (() -> Unit)?,
    onNextDay: (() -> Unit)?,
    isPreloading: Boolean,
    loadedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    if (leadTimes.isEmpty()) return
    val selectedIndex = leadTimes.indexOf(selectedLeadTime).coerceAtLeast(0)
    var sliderPosition by remember(leadTimes) { mutableStateOf(selectedIndex.toFloat()) }
    LaunchedEffect(selectedLeadTime, leadTimes) {
        val newIndex = leadTimes.indexOf(selectedLeadTime)
        if (newIndex >= 0) sliderPosition = newIndex.toFloat()
    }
    val previewIndex = sliderPosition.roundToInt().coerceIn(0, leadTimes.lastIndex)
    val previewLead = leadTimes[previewIndex]

    Surface(
        color = Color(0xD9212933),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = forecastTimeLabel(runDateTime, previewLead),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (isPreloading) {
                Text(
                    text = "Loading day maps $loadedCount/$totalCount",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (previewIndex > 0) onSelect(leadTimes[previewIndex - 1]) else onPreviousDay?.invoke()
                    },
                    enabled = previewIndex > 0 || onPreviousDay != null,
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Default.ChevronLeft, "Previous map time", tint = Color.White) }
                Slider(
                    value = sliderPosition,
                    onValueChange = { value ->
                        sliderPosition = value
                        val lead = leadTimes[value.roundToInt().coerceIn(0, leadTimes.lastIndex)]
                        if (lead != selectedLeadTime) onSelect(lead)
                    },
                    onValueChangeFinished = {},
                    valueRange = 0f..leadTimes.lastIndex.coerceAtLeast(1).toFloat(),
                    steps = (leadTimes.size - 2).coerceAtLeast(0),
                    enabled = leadTimes.size > 1,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF64B5F6),
                        inactiveTrackColor = Color.White.copy(alpha = 0.45f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f).testTag("map_timeline_slider")
                )
                IconButton(
                    onClick = {
                        if (previewIndex < leadTimes.lastIndex) onSelect(leadTimes[previewIndex + 1]) else onNextDay?.invoke()
                    },
                    enabled = previewIndex < leadTimes.lastIndex || onNextDay != null,
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Default.ChevronRight, "Next map time", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun MessageSurface(message: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = background, modifier = Modifier.fillMaxWidth()) {
        Text(message, color = foreground, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

private fun friendlyLayerName(layer: String): String = when (layer) {
    "total_precipitation_rate" -> "Precipitation rate"
    "cloud_amount_total" -> "Cloud cover"
    "temperature_at_surface" -> "Surface temperature"
    "mean_sea_level_pressure" -> "Mean sea-level pressure"
    else -> layer.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private val mapZone: ZoneId = ZoneId.of("Europe/London")

private fun forecastInstant(runDateTime: String, lead: Int): Instant =
    Instant.parse(runDateTime).plusSeconds(lead * 3600L)

private fun forecastDate(runDateTime: String, lead: Int): LocalDate? = runCatching {
    forecastInstant(runDateTime, lead).atZone(mapZone).toLocalDate()
}.getOrNull()

private fun forecastDayLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))

private fun forecastTimeLabel(runDateTime: String, lead: Int): String = runCatching {
    forecastInstant(runDateTime, lead)
        .atZone(mapZone)
        .format(DateTimeFormatter.ofPattern("EEEE d MMMM · HH:mm z"))
}.getOrDefault("+${lead}h")

private fun formatRunTime(runDateTime: String): String = runCatching {
    Instant.parse(runDateTime).atZone(ZoneId.of("UTC"))
        .format(DateTimeFormatter.ofPattern("d MMM HH:mm 'UTC'"))
}.getOrDefault(runDateTime)
