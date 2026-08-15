package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApiDebugInfo
import com.example.data.model.CoordinateTestResult
import com.example.data.model.LocationItem
import com.example.data.model.WeatherDataSource
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardWhite
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoHeroText
import com.example.ui.theme.BentoPillAccent
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDebugSheet(
    isOpen: Boolean,
    debugInfo: ApiDebugInfo?,
    currentLocation: LocationItem,
    customLatInput: String,
    customLonInput: String,
    coordinateTestResult: CoordinateTestResult?,
    isTestingCoordinates: Boolean,
    rawGeocodingQueryInput: String,
    rawGeocodingResultJson: String?,
    rawGeocodingLocations: List<LocationItem>,
    isTestingGeocoding: Boolean,
    onClose: () -> Unit,
    onRefreshCurrent: () -> Unit,
    onUpdateCustomLat: (String) -> Unit,
    onUpdateCustomLon: (String) -> Unit,
    onNudgeCoordinates: (Double, Double) -> Unit,
    onRunCoordinateTest: (Double?, Double?) -> Unit,
    onUpdateGeocodingQuery: (String) -> Unit,
    onSelectLocationFromGeocode: (LocationItem) -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedJsonSubTab by remember { mutableIntStateOf(0) } // 0: Hourly, 1: 3-Hourly, 2: Daily, 3: Fallback
    var jsonSearchFilter by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = BentoTile,
        modifier = Modifier.testTag("api_debug_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BentoHero),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataObject,
                            contentDescription = null,
                            tint = BentoPurplePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "API & Coordinates Debugger",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary
                            )
                        )
                        Text(
                            text = "Inspect raw payload & verify lat/lon mapping",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BentoTextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshCurrent,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BentoCardWhite)
                            .border(1.dp, BentoBorder.copy(alpha = 0.5f), CircleShape)
                            .testTag("debug_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Forecast",
                            tint = BentoPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BentoCardWhite)
                            .border(1.dp, BentoBorder.copy(alpha = 0.5f), CircleShape)
                            .testTag("debug_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Debug Sheet",
                            tint = BentoTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current Location & Status Header Card
            LocationSummaryCard(
                location = currentLocation,
                debugInfo = debugInfo,
                onCopyUrl = {
                    val url = debugInfo?.requestUrl ?: ""
                    copyToClipboard(context, "API URL", url)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = BentoCardWhite,
                contentColor = BentoPurplePrimary,
                edgePadding = 8.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Raw API JSON", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Coordinate Sandbox", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Geocoding Inspector", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("Grid Explanation", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Content
            when (selectedTabIndex) {
                0 -> RawJsonViewerTab(
                    debugInfo = debugInfo,
                    selectedSubTab = selectedJsonSubTab,
                    onSelectSubTab = { selectedJsonSubTab = it },
                    filterText = jsonSearchFilter,
                    onFilterChange = { jsonSearchFilter = it },
                    onCopyJson = { jsonStr ->
                        copyToClipboard(context, "Raw API JSON", jsonStr)
                    }
                )
                1 -> CoordinateSandboxTab(
                    currentLocation = currentLocation,
                    customLatInput = customLatInput,
                    customLonInput = customLonInput,
                    testResult = coordinateTestResult,
                    isLoading = isTestingCoordinates,
                    onUpdateLat = onUpdateCustomLat,
                    onUpdateLon = onUpdateCustomLon,
                    onNudge = onNudgeCoordinates,
                    onRunTest = onRunCoordinateTest,
                    onCopyJson = { copyToClipboard(context, "Coordinate Test JSON", it) }
                )
                2 -> GeocodingInspectorTab(
                    queryInput = rawGeocodingQueryInput,
                    rawJson = rawGeocodingResultJson,
                    locations = rawGeocodingLocations,
                    isLoading = isTestingGeocoding,
                    onUpdateQuery = onUpdateGeocodingQuery,
                    onSelectLocation = { loc ->
                        onSelectLocationFromGeocode(loc)
                    },
                    onTestCoordinates = { lat, lon ->
                        onUpdateCustomLat(String.format(Locale.US, "%.4f", lat))
                        onUpdateCustomLon(String.format(Locale.US, "%.4f", lon))
                        selectedTabIndex = 1
                        onRunCoordinateTest(lat, lon)
                    },
                    onCopyJson = { copyToClipboard(context, "Geocoding JSON", it) }
                )
                3 -> GridResolutionExplanationTab()
            }
        }
    }
}

@Composable
private fun LocationSummaryCard(
    location: LocationItem,
    debugInfo: ApiDebugInfo?,
    onCopyUrl: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BentoCardWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${location.name}${location.region?.let { ", $it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (debugInfo?.dataSource == WeatherDataSource.MET_OFFICE_DATAHUB) Color(0xFFE8F5E9) else BentoHero
                ) {
                    Text(
                        text = if (debugInfo?.dataSource == WeatherDataSource.MET_OFFICE_DATAHUB) "Met Office DataHub" else "Open-Meteo Model",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (debugInfo?.dataSource == WeatherDataSource.MET_OFFICE_DATAHUB) Color(0xFF2E7D32) else BentoHeroText,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Coordinate Precision Specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queried Coords: Lat ${String.format(Locale.US, "%.4f", location.latitude)}°, Lon ${String.format(Locale.US, "%.4f", location.longitude)}°",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = BentoTextSecondary,
                        fontSize = 11.sp
                    )
                )

                if (debugInfo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (debugInfo.httpStatusCode == 200) Color(0xFF4CAF50) else Color(0xFFE53935))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "HTTP ${debugInfo.httpStatusCode} (${debugInfo.responseTimeMs}ms)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (debugInfo.httpStatusCode == 200) Color(0xFF2E7D32) else Color(0xFFC62828),
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }

            if (debugInfo?.serverResolvedLat != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Server Grid Point: Lat ${String.format(Locale.US, "%.4f", debugInfo.serverResolvedLat)}°, Lon ${String.format(Locale.US, "%.4f", debugInfo.serverResolvedLon ?: 0.0)}° ${debugInfo.serverResolvedName?.let { "($it)" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = BentoPurplePrimary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun RawJsonViewerTab(
    debugInfo: ApiDebugInfo?,
    selectedSubTab: Int,
    onSelectSubTab: (Int) -> Unit,
    filterText: String,
    onFilterChange: (String) -> Unit,
    onCopyJson: (String) -> Unit
) {
    val isMetOffice = debugInfo?.dataSource == WeatherDataSource.MET_OFFICE_DATAHUB

    val rawJson = when {
        isMetOffice -> when (selectedSubTab) {
            0 -> debugInfo?.rawJsonHourly ?: "No Hourly Data Received"
            1 -> debugInfo?.rawJsonThreeHourly ?: "No 3-Hourly Data Received"
            2 -> debugInfo?.rawJsonDaily ?: "No Daily Data Received"
            else -> debugInfo?.rawJsonHourly ?: ""
        }
        else -> debugInfo?.rawJsonFallback ?: "No Raw Weather Data Available"
    }

    val filteredJson = remember(rawJson, filterText) {
        if (filterText.isBlank()) rawJson
        else {
            val lines = rawJson.lines()
            val matched = lines.filter { it.contains(filterText, ignoreCase = true) }
            if (matched.isEmpty()) "// No lines matched '$filterText'\n// Total payload length: ${rawJson.length} chars"
            else "// Showing ${matched.size} matching lines out of ${lines.size}:\n" + matched.joinToString("\n")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Sub-tabs for Met Office endpoints
        if (isMetOffice) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Hourly Point (48h)", "3-Hourly Point (7d)", "Daily Point (7d)").forEachIndexed { idx, title ->
                    val isSelected = selectedSubTab == idx
                    Surface(
                        onClick = { onSelectSubTab(idx) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BentoPurplePrimary else BentoCardWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BentoPurplePrimary else BentoBorder.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else BentoTextPrimary,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Search & Copy Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterText,
                onValueChange = onFilterChange,
                placeholder = { Text("Filter JSON (e.g. temperature, time)...", fontSize = 11.5.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (filterText.isNotEmpty()) {
                        IconButton(onClick = { onFilterChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BentoCardWhite,
                    unfocusedContainerColor = BentoCardWhite,
                    focusedBorderColor = BentoPurplePrimary,
                    unfocusedBorderColor = BentoBorder
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = { onCopyJson(rawJson) },
                shape = RoundedCornerShape(12.dp),
                color = BentoHero,
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f)),
                modifier = Modifier.height(46.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BentoHeroText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BentoHeroText))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // JSON text container
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E1E24),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val scrollState = rememberScrollState()
            val hScrollState = rememberScrollState()

            Box(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = filteredJson,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF81D4FA),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .horizontalScroll(hScrollState)
                )
            }
        }
    }
}

@Composable
private fun CoordinateSandboxTab(
    currentLocation: LocationItem,
    customLatInput: String,
    customLonInput: String,
    testResult: CoordinateTestResult?,
    isLoading: Boolean,
    onUpdateLat: (String) -> Unit,
    onUpdateLon: (String) -> Unit,
    onNudge: (Double, Double) -> Unit,
    onRunTest: (Double?, Double?) -> Unit,
    onCopyJson: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BentoCardWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Coordinate Testing Sandbox",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    )
                    Text(
                        text = "Modify latitude and longitude directly to test whether the API returns different grid values or identical values for close coordinates.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = BentoTextSecondary,
                            fontSize = 11.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Lat / Lon input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customLatInput,
                            onValueChange = onUpdateLat,
                            label = { Text("Latitude", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customLonInput,
                            onValueChange = onUpdateLon,
                            label = { Text("Longitude", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nudge buttons row
                    Text(
                        text = "Nudge Coordinates:",
                        style = MaterialTheme.typography.labelSmall.copy(color = BentoTextSecondary, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NudgeChip(label = "+0.02° Lat (~2km N)", onClick = { onNudge(0.02, 0.0) })
                        NudgeChip(label = "+0.10° Lat (~11km N)", onClick = { onNudge(0.10, 0.0) })
                        NudgeChip(label = "+0.50° Lat (~55km N)", onClick = { onNudge(0.50, 0.0) })
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick UK Presets
                    Text(
                        text = "Or Test Specific UK Locations:",
                        style = MaterialTheme.typography.labelSmall.copy(color = BentoTextSecondary, fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(LocationItem.DEFAULT_LOCATIONS) { loc ->
                            Surface(
                                onClick = {
                                    onUpdateLat(String.format(Locale.US, "%.4f", loc.latitude))
                                    onUpdateLon(String.format(Locale.US, "%.4f", loc.longitude))
                                    onRunTest(loc.latitude, loc.longitude)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = BentoHero.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = loc.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = BentoHeroText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Execute Request Button
                    Surface(
                        onClick = { onRunTest(null, null) },
                        shape = RoundedCornerShape(12.dp),
                        color = BentoPurplePrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Querying Weather API...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Execute Live API Query", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Test Result Card
        if (testResult != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BentoCardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Query Response Summary",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (testResult.isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "HTTP ${testResult.httpStatusCode} (${testResult.responseTimeMs}ms)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (testResult.isError) Color(0xFFC62828) else Color(0xFF2E7D32)
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Query: Lat ${testResult.latitude}°, Lon ${testResult.longitude}°",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = BentoTextSecondary)
                        )
                        if (testResult.serverResolvedLat != null) {
                            Text(
                                text = "Server Grid Cell: Lat ${String.format(Locale.US, "%.4f", testResult.serverResolvedLat)}°, Lon ${String.format(Locale.US, "%.4f", testResult.serverResolvedLon ?: 0.0)}° (Elev: ${testResult.serverElevation ?: 0.0}m)",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = BentoPurplePrimary, fontWeight = FontWeight.Bold)
                            )
                        }
                        if (testResult.currentTempCelsius != null) {
                            Text(
                                text = "Current Temperature: ${testResult.currentTempCelsius}°C",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            )
                        }

                        if (testResult.timeSeriesSample.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Next Hours Sample:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            testResult.timeSeriesSample.forEach { sample ->
                                Text(
                                    text = "• $sample",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = BentoTextSecondary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Copy raw JSON button
                        Surface(
                            onClick = { onCopyJson(testResult.rawJson) },
                            shape = RoundedCornerShape(10.dp),
                            color = BentoHero,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BentoHeroText, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Sandbox Raw JSON Response", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BentoHeroText))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NudgeChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = BentoTile,
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = BentoPurplePrimary,
                fontSize = 10.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun GeocodingInspectorTab(
    queryInput: String,
    rawJson: String?,
    locations: List<LocationItem>,
    isLoading: Boolean,
    onUpdateQuery: (String) -> Unit,
    onSelectLocation: (LocationItem) -> Unit,
    onTestCoordinates: (Double, Double) -> Unit,
    onCopyJson: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = queryInput,
            onValueChange = onUpdateQuery,
            placeholder = { Text("Search any UK town/city (e.g. Manchester, Salford)...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = BentoCardWhite,
                unfocusedContainerColor = BentoCardWhite,
                focusedBorderColor = BentoPurplePrimary,
                unfocusedBorderColor = BentoBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resolving geocoding coordinates...", style = MaterialTheme.typography.bodySmall.copy(color = BentoTextSecondary))
            }
        }

        if (locations.isNotEmpty()) {
            Text(
                text = "Resolved Candidate Coordinates (${locations.size} places):",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BentoTextPrimary)
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(locations) { loc ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BentoCardWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${loc.name}${loc.region?.let { ", $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                                )
                                Text(
                                    text = "Lat: ${String.format(Locale.US, "%.5f", loc.latitude)}°, Lon: ${String.format(Locale.US, "%.5f", loc.longitude)}°",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = BentoPurplePrimary)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    onClick = { onTestCoordinates(loc.latitude, loc.longitude) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = BentoHero
                                ) {
                                    Text(
                                        text = "Test Coords",
                                        style = MaterialTheme.typography.labelSmall.copy(color = BentoHeroText, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    onClick = { onSelectLocation(loc) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = BentoPurplePrimary
                                ) {
                                    Text(
                                        text = "Select",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Raw Geocoding JSON Viewer
        if (rawJson != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Raw Geocoding Server JSON:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = BentoTextSecondary))
                IconButton(onClick = { onCopyJson(rawJson) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = BentoPurplePrimary, modifier = Modifier.size(14.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E24),
                modifier = Modifier.fillMaxWidth().weight(0.55f)
            ) {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = rawJson,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF81D4FA),
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        ),
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridResolutionExplanationTab() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BentoCardWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = BentoPurplePrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Why Do Nearby Locations Return Identical Forecasts?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "1. Atmospheric Numerical Weather Prediction (NWP) Models operate on discrete spatial grids rather than continuous infinite resolution.",
                        style = MaterialTheme.typography.bodySmall.copy(color = BentoTextSecondary, lineHeight = 18.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "2. UK Met Office Unified Model resolutions:\n" +
                                "• UKV High-Resolution Model: ~1.5 km to 2.0 km grid cells across the UK\n" +
                                "• Met Office Global / DataHub Site-Specific Model: ~10 km grid points\n" +
                                "• Open-Meteo ECMWF / GFS: 4 km to 11 km resolution",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = BentoPurplePrimary, lineHeight = 17.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "3. Result:\nIf two search locations (such as 'Manchester City Centre' and 'Salford Quays', or 'Westminster' and 'Camden') are less than ~5-10 km apart, the API maps both coordinate requests to the exact same or adjacent grid point. The raw server response will therefore contain identical temperature and rain metrics until moving further across grid boundaries.",
                        style = MaterialTheme.typography.bodySmall.copy(color = BentoTextSecondary, lineHeight = 18.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider(color = BentoBorder.copy(alpha = 0.4f))

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Verification Checklist:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = BentoTextPrimary, fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ChecklistRow(text = "App calculates and transmits exact decimal coordinates (Lat & Lon) to 4+ decimal places.")
                    ChecklistRow(text = "Each location is resolved via Open-Meteo & Met Office geocoders with unique geographical identifiers.")
                    ChecklistRow(text = "The Coordinate Sandbox tab above allows you to nudge coordinates by 10km-50km to observe when the API crosses into adjacent weather grid boxes.")
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(color = BentoTextPrimary, fontSize = 11.5.sp))
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
