package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.IPTVItem
import com.example.ui.IPTVViewModel
import com.example.ui.theme.CineOrange
import com.example.ui.theme.CineRed
import com.example.ui.theme.LocalAppTheme
import com.example.ui.theme.isDark

import com.example.ui.theme.rememberAppAdaptiveLayout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchlistScreen(
    viewModel: IPTVViewModel,
    onSelectItem: (IPTVItem) -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adaptiveLayout = rememberAppAdaptiveLayout()
    val favorites by viewModel.favoritesWithSeries.collectAsState()
    val currentTheme = LocalAppTheme.current
    var selectedTab by remember { mutableStateOf("ALL") } // ALL, MOVIE, SERIES, LIVE
    var searchQuery by remember { mutableStateOf("") }

    val moviesCount = remember(favorites) { favorites.count { it.type == "MOVIE" } }
    val seriesCount = remember(favorites) { favorites.count { it.type == "SERIES" } }
    val liveCount = remember(favorites) { favorites.count { it.type == "LIVE" } }

    val filteredItems = remember(favorites, selectedTab, searchQuery) {
        favorites.filter { item ->
            val matchesTab = when (selectedTab) {
                "MOVIE" -> item.type == "MOVIE"
                "SERIES" -> item.type == "SERIES"
                "LIVE" -> item.type == "LIVE"
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                    item.cleanedName.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesQuery
        }
    }

    val contentColor = if (currentTheme.isDark) Color.White else Color(0xFF1E1E24)
    val cardBg = if (currentTheme.isDark) Color(0xFF1E1E24) else Color(0xFFF3F4F6)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = adaptiveLayout.screenPadding)
            .testTag("watchlist_screen_container")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(CineOrange, CineRed))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.watchlist_title),
                    color = contentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("watchlist_title")
                )
                Text(
                    text = stringResource(R.string.watchlist_count, favorites.size),
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search bar inside Watchlist if favorites exist
        if (favorites.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(R.string.watchlist_search_hint),
                        color = contentColor.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = CineOrange,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f),
                    unfocusedContainerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f),
                    focusedBorderColor = CineOrange,
                    unfocusedBorderColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f),
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("watchlist_search_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-filter tabs (Tümü, Filmler, Diziler, Canlı Yayın)
            val tabs = listOf(
                Triple("ALL", stringResource(R.string.watchlist_tab_all), favorites.size),
                Triple("MOVIE", stringResource(R.string.nav_movies), moviesCount),
                Triple("SERIES", stringResource(R.string.nav_series), seriesCount),
                Triple("LIVE", stringResource(R.string.watchlist_tab_live), liveCount)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                items(tabs, key = { it.first }) { (type, label, count) ->
                    val isSelected = selectedTab == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = type },
                        label = {
                            Text(
                                text = "$label ($count)",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CineOrange,
                            selectedLabelColor = Color.White,
                            containerColor = if (currentTheme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            labelColor = contentColor.copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color.Transparent else (if (currentTheme.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f))
                        ),
                        modifier = Modifier.testTag("watchlist_tab_$type")
                    )
                }
            }
        }

        // Main List Grid or Empty State
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        CineOrange.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(1.5.dp, CineOrange.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = CineOrange,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.watchlist_empty_title),
                        color = contentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.watchlist_empty_body),
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onExploreClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("watchlist_explore_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.watchlist_discover),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.watchlist_no_match),
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(adaptiveLayout.gridColumns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("watchlist_grid")
            ) {
                itemsIndexed(filteredItems, key = { index, item -> "${item.id}_$index" }) { _, item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(
                            0.8.dp,
                            if (currentTheme.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(com.example.ui.tv.tvRestorableFocus())
                            .combinedClickable(
                                onClick = { onSelectItem(item) },
                                onLongClick = { }
                            )
                            .testTag("watchlist_item_${item.id}")
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                if (!item.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.logoUrl,
                                        contentDescription = item.cleanedName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color(0xFF2C2D35), Color(0xFF14151B))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.cleanedName.take(1).uppercase(),
                                            color = CineOrange,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Rating Badge (Top Left)
                                if (item.rating > 0.0) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", item.rating),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Quick Delete / Un-favorite Button (Top Right)
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(item) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .testTag("remove_from_watchlist_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.watchlist_remove),
                                        tint = CineRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Text(
                                    text = item.cleanedName,
                                    color = contentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = if (item.type == "MOVIE") stringResource(R.string.watchlist_type_movie) else if (item.type == "SERIES") stringResource(R.string.watchlist_type_series) else stringResource(R.string.watchlist_type_live),
                                    color = CineOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
