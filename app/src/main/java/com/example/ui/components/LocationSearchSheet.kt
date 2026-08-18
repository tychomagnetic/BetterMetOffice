package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.LocationItem
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardWhite
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoHeroText
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocationSearchSheet(
    searchQuery: String,
    searchResults: List<LocationItem>,
    isSearching: Boolean,
    favoriteLocations: List<LocationItem>,
    currentSelectedId: String,
    onQueryChange: (String) -> Unit,
    onLocationSelect: (LocationItem) -> Unit,
    onGpsSelect: (Double, Double, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val dismissWithKeyboard: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    val selectWithKeyboard: (LocationItem) -> Unit = { item ->
        keyboardController?.hide()
        focusManager.clearFocus()
        onLocationSelect(item)
    }

    val gpsWithKeyboard: (Double, Double, String?) -> Unit = { lat, lon, name ->
        keyboardController?.hide()
        focusManager.clearFocus()
        onGpsSelect(lat, lon, name)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            fetchGpsLocation(context, gpsWithKeyboard)
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismissWithKeyboard,
        sheetState = sheetState,
        containerColor = BentoTile,
        modifier = modifier.testTag("location_search_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title
            Text(
                text = "Select Location",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Search UK town, city, or postcode...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = BentoPurplePrimary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = BentoTextSecondary
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BentoTextPrimary,
                    unfocusedTextColor = BentoTextPrimary,
                    focusedBorderColor = BentoPurplePrimary,
                    unfocusedBorderColor = BentoBorder,
                    focusedContainerColor = BentoCardWhite,
                    unfocusedContainerColor = BentoCardWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_search_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Use Current Location (GPS) Button
            Surface(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    val fineCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarseCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (fineCheck == PackageManager.PERMISSION_GRANTED || coarseCheck == PackageManager.PERMISSION_GRANTED) {
                        fetchGpsLocation(context, gpsWithKeyboard)
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = BentoHero,
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("use_current_location_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "GPS Location",
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Use Current Location (GPS)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = BentoHeroText
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Results or Presets
            if (isSearching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = BentoPurplePrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Searching locations...", color = BentoTextSecondary)
                }
            } else if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                Text(
                    text = "SEARCH RESULTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(searchResults) { loc ->
                        LocationItemRow(
                            item = loc,
                            isSelected = loc.id == currentSelectedId,
                            onClick = { selectWithKeyboard(loc) }
                        )
                        HorizontalDivider(color = BentoBorder.copy(alpha = 0.4f))
                    }
                }
            } else {
                // Favorites Section
                if (favoriteLocations.isNotEmpty()) {
                    Text(
                        text = "SAVED LOCATIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        favoriteLocations.forEach { loc ->
                            val isSelected = loc.id == currentSelectedId
                            Surface(
                                onClick = { selectWithKeyboard(loc) },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) BentoPurplePrimary else BentoCardWhite,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) BentoPurplePrimary else BentoBorder
                                ),
                                modifier = Modifier.testTag("saved_loc_${loc.name.lowercase()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color(0xFFE91E63),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = loc.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) Color.White else BentoTextPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Popular UK Cities Section
                Text(
                    text = "POPULAR UK CITIES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LocationItem.DEFAULT_LOCATIONS.forEach { loc ->
                        val isSelected = loc.id == currentSelectedId
                        Surface(
                            onClick = { selectWithKeyboard(loc) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) BentoPurplePrimary else BentoCardWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BentoPurplePrimary else BentoBorder
                            ),
                            modifier = Modifier.testTag("preset_loc_${loc.name.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else BentoPurplePrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = loc.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else BentoTextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationItemRow(
    item: LocationItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (isSelected) BentoPurplePrimary else BentoTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = BentoTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.region.isNullOrBlank() || !item.country.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(item.region, item.country).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = BentoTextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun fetchGpsLocation(
    context: Context,
    onGpsSelect: (Double, Double, String?) -> Unit
) {
    try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager != null) {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            for (provider in providers) {
                val loc: Location? = try {
                    if (locationManager.isProviderEnabled(provider)) {
                        locationManager.getLastKnownLocation(provider)
                    } else null
                } catch (_: SecurityException) {
                    null
                }
                if (loc != null) {
                    onGpsSelect(loc.latitude, loc.longitude, "My Location")
                    return
                }
            }
        }
    } catch (_: Exception) {
    }
    Toast.makeText(
        context,
        "Current location is unavailable. Check location access and try again.",
        Toast.LENGTH_LONG
    ).show()
}
