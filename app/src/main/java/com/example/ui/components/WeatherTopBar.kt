package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationItem
import com.example.data.model.WeatherDataSource
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile

@Composable
fun WeatherTopBar(
    location: LocationItem,
    dataSource: WeatherDataSource?,
    useMetOfficeSource: Boolean,
    hasApiKey: Boolean,
    isRefreshing: Boolean,
    onLocationClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDataSourceToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = location.isFavorite
) {
    val rotation by if (isRefreshing) {
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotate")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing)
            ),
            label = "rotation"
        )
    } else {
        rememberInfiniteTransition(label = "static").animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1)),
            label = "static_rot"
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Main row: Location selector capsule, Favorites, Search, Refresh, Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location button capsule
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BentoTile)
                    .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { onLocationClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .weight(1f, fill = false)
                    .testTag("location_selector_button")
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = BentoPurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Location",
                            tint = BentoTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (!location.region.isNullOrBlank() || !location.country.isNullOrBlank()) {
                        Text(
                            text = location.region ?: location.country ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Icons Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Favorite Toggle with bouncy animation
                val heartScale by animateFloatAsState(
                    targetValue = if (isFavorite) 1.25f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "heart_scale"
                )
                val heartTint by animateColorAsState(
                    targetValue = if (isFavorite) Color(0xFFE91E63) else BentoTextSecondary,
                    animationSpec = tween(durationMillis = 220),
                    label = "heart_tint"
                )
                val heartBg by animateColorAsState(
                    targetValue = if (isFavorite) Color(0xFFFFEBEE) else BentoTile,
                    animationSpec = tween(durationMillis = 220),
                    label = "heart_bg"
                )
                val heartBorder by animateColorAsState(
                    targetValue = if (isFavorite) Color(0xFFE91E63).copy(alpha = 0.45f) else BentoBorder.copy(alpha = 0.5f),
                    animationSpec = tween(durationMillis = 220),
                    label = "heart_border"
                )

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(heartBg)
                        .border(1.dp, heartBorder, CircleShape)
                        .testTag("favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = heartTint,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                }

                // Search location
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BentoTile)
                        .border(1.dp, BentoBorder.copy(alpha = 0.5f), CircleShape)
                        .testTag("search_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Locations",
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Refresh Button
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BentoTile)
                        .border(1.dp, BentoBorder.copy(alpha = 0.5f), CircleShape)
                        .testTag("refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Forecast",
                        tint = BentoPurplePrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation)
                    )
                }

                // Settings Button (Replaced debug & units buttons)
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BentoTile)
                        .border(1.dp, BentoBorder.copy(alpha = 0.5f), CircleShape)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings & Units",
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Data Source Toggle Segment: Met Office DataHub vs Open Data
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BentoTile)
                .border(1.dp, BentoBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(3.dp)
                .testTag("data_source_toggle_container"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Met Office Button
            val isMetOfficeSelected = useMetOfficeSource
            Surface(
                onClick = {
                    if (!isMetOfficeSelected) {
                        onDataSourceToggle(true)
                    }
                },
                shape = RoundedCornerShape(13.dp),
                color = if (isMetOfficeSelected) BentoPurplePrimary else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("toggle_met_office_button")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isMetOfficeSelected) Color.White else BentoTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Met Office DataHub",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isMetOfficeSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMetOfficeSelected) Color.White else BentoTextPrimary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            // Open Data Button
            val isOpenDataSelected = !useMetOfficeSource
            Surface(
                onClick = {
                    if (!isOpenDataSelected) {
                        onDataSourceToggle(false)
                    }
                },
                shape = RoundedCornerShape(13.dp),
                color = if (isOpenDataSelected) Color(0xFF0284C7) else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("toggle_open_data_button")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = if (isOpenDataSelected) Color.White else BentoTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Data",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isOpenDataSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isOpenDataSelected) Color.White else BentoTextPrimary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Helpful alert if Met Office is selected but API key is missing
        if (useMetOfficeSource && !hasApiKey) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF8E1))
                    .border(1.dp, Color(0xFFFFE082), RoundedCornerShape(12.dp))
                    .clickable { onSettingsClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Met Office API key needed for UK official model",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFE65100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Configure →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                )
            }
        }
    }
}
