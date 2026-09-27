package com.example.ui.components
import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.IPTVItem
import com.example.data.repository.FeaturedMovie
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.CineOrange
import com.example.ui.theme.DeepPurpleBg
import com.example.ui.theme.MidPurpleBg
import com.example.ui.theme.rememberAppAdaptiveLayout

/**
 * 12 Saatte bir güncellenen M3U Öne Çıkan Film / Netflix tarzı Hero Banner
 */
@Composable
fun FeaturedMovieCard(
    featuredMovie: FeaturedMovie?,
    onPlayClick: (IPTVItem) -> Unit,
    onInfoClick: (IPTVItem) -> Unit = {},
    favorites: List<IPTVItem> = emptyList(),
    onToggleFavorite: (IPTVItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (featuredMovie == null) return

    val item = featuredMovie.iptvItem ?: IPTVItem(
        id = featuredMovie.title.hashCode(),
        playlistId = 0,
        name = featuredMovie.title,
        cleanedName = featuredMovie.title,
        streamUrl = featuredMovie.streamUrl,
        logoUrl = featuredMovie.backdropUrl ?: featuredMovie.posterUrl,
        category = featuredMovie.category ?: "Öne Çıkan",
        type = "MOVIE",
        summary = featuredMovie.overview ?: "",
        rating = featuredMovie.imdbRating?.toDoubleOrNull() ?: 8.0,
        releaseDate = featuredMovie.releaseDate ?: ""
    )

    val isInFavorites = favorites.any { it.id == item.id || it.name.equals(item.name, ignoreCase = true) }
    val backdropUrl = featuredMovie.backdropUrl ?: featuredMovie.posterUrl ?: item.logoUrl

    HeroBannerCard(
        title = featuredMovie.title.ifBlank { item.cleanedName },
        backdropUrl = backdropUrl,
        overview = featuredMovie.overview,
        rating = featuredMovie.imdbRating,
        category = featuredMovie.category,
        year = featuredMovie.releaseDate?.take(4),
        isFavorite = isInFavorites,
        onPlayClick = { onPlayClick(item) },
        onInfoClick = { onInfoClick(item) },
        onFavoriteClick = { onToggleFavorite(item) },
        modifier = modifier
    )
}

/**
 * Öne Çıkan alanı: aynı [FeaturedMovieCard] görünümü ve boyutuyla birden çok filmi gösterir.
 * - Her [autoAdvanceMillis] (5 sn) sonra kendiliğinden bir sonraki filme geçer.
 * - Parmakla sola/sağa kaydırılarak değiştirilebilir; kaydırınca 5 sn'lik süre baştan başlar.
 * - Sonsuz döner (son filmden sonra ilk film gelir).
 * - Kumanda/klavye odağı alanın içindeyken (TV) otomatik geçiş durur ki odaklı düğme kaybolmasın.
 * Yetişkin içerik filtresi slaytları hazırlayan ViewModel'de (heroSlides) uygulanır.
 */
@Composable
fun FeaturedHeroCarousel(
    slides: List<FeaturedMovie>,
    onPlayClick: (IPTVItem) -> Unit,
    onInfoClick: (IPTVItem) -> Unit = {},
    favorites: List<IPTVItem> = emptyList(),
    onToggleFavorite: (IPTVItem) -> Unit = {},
    modifier: Modifier = Modifier,
    autoAdvanceMillis: Long = 5_000L
) {
    if (slides.isEmpty()) return
    if (slides.size == 1) {
        FeaturedMovieCard(
            featuredMovie = slides[0],
            onPlayClick = onPlayClick,
            onInfoClick = onInfoClick,
            favorites = favorites,
            onToggleFavorite = onToggleFavorite,
            modifier = modifier
        )
        return
    }

    // Sonsuz döngü için çok büyük sayfa sayısı; ortadan, ilk filme denk gelen sayfadan başlanır.
    val pageCount = Int.MAX_VALUE
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = (pageCount / 2).let { it - it % slides.size }
    ) { pageCount }
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var hasFocusInside by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.settledPage, isDragged, hasFocusInside, slides.size) {
        if (isDragged || hasFocusInside) return@LaunchedEffect
        kotlinx.coroutines.delay(autoAdvanceMillis)
        pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(600))
    }

    androidx.compose.foundation.pager.HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = modifier
            .onFocusChanged { hasFocusInside = it.hasFocus }
            .testTag("hero_carousel")
    ) { page ->
        FeaturedMovieCard(
            featuredMovie = slides[page % slides.size],
            onPlayClick = onPlayClick,
            onInfoClick = onInfoClick,
            favorites = favorites,
            onToggleFavorite = onToggleFavorite,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Netflix / Disney+ tarzı Hero Banner: tam genişlikte backdrop, alt kısımda
 * karanlıktan şeffafa gradient, üzerinde tür/puan etiketleri, başlık, özet ve
 * "Oynat" + "Detay" aksiyon butonları. Favori ve yenile ikonları sağ üstte
 * arka planla bütünleşik şekilde durur.
 */
@Composable
fun HeroBannerCard(
    title: String,
    backdropUrl: String?,
    overview: String?,
    rating: String?,
    category: String?,
    year: String?,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(520.dp)
            .testTag("hero_banner_card")
    ) {
        // --- Arka Plan Backdrop ---
        AsyncImage(
            model = backdropUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // --- Alt Karartma Gradienti (metinlerin okunurluğu için) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.96f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // --- Üst Karartma (durum çubuğu / üst ikonların okunurluğu için) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        // --- Sağ Üst: Favori (arka planla bütünleşik, çerçevesiz). İçerikler kendiliğinden değiştiği için
        // eski "yenile" düğmesi kaldırıldı. ---
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .testTag("featured_favorite_button")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorilere Ekle",
                    tint = if (isFavorite) CineOrange else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // --- Sol Üst: "Öne Çıkan" rozeti ---
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = CineOrange,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.featured_badge),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }

        // --- Alt İçerik: Etiketler + Başlık + Özet + Butonlar ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Tür / Puan / Yıl etiket satırı
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!rating.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFFC107), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (!year.isNullOrBlank()) {
                    Text(
                        text = year,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (!category.isNullOrBlank()) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Başlık
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Özet
            if (!overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Aksiyon Butonları: Oynat (dolgulu, öncelikli) + Detay (dış hatlı)
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("featured_play_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_play),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedButton(
                    onClick = onInfoClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_details),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
