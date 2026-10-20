package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R

// Onaylı taslak renkleri (docs/tasarim/ozellik-taslaklari/Main.dc.html).
private val FrostedBarColor = Color(0x9E2A2838)        // rgba(42,40,56,0.62)
private val FrostedBorderColor = Color(0x29FFFFFF)     // 1 px rgba(255,255,255,0.16)
// Taslakta 0.82 + bulanıklık; Compose'da arka plan bulanıklığı yok, arkadaki yazı karışmasın diye daha koyu.
private val FrostedMenuColor = Color(0xFF262432)
private val SelectedTabColor = Color(0x1FFFFFFF)       // seçili sekme hafif vurgulu
private val InactiveTabText = Color(0xFFC9C3D3)
private val MenuMutedText = Color(0xFFA79FB5)
private val AvatarBg = Color(0xFF3B2A55)
private val AvatarIcon = Color(0xFFE9D9F5)
private val Accent = Color(0xFFD946EF)
private val GoldTag = Color(0xFFD4A853)

private data class FrostedTab(val filterType: String, val title: String, val active: ImageVector, val inactive: ImageVector)

/**
 * Yeni alt bar (telefon): Ana Sayfa, Filmler, Diziler, Canlı TV ve en sağda profil resmi.
 * Profil resmine dokununca barın üstünde buzlu menü açılır (Ayarlar, Profil, Oyunlar, Spor, Listem).
 * [BuildConfig.NEW_BOTTOM_BAR] kapalıysa HomeScreen eski alt barı kullanır.
 */
@Composable
fun FrostedBottomBar(
    selectedTypeFilter: String,
    photoUrl: String?,
    stripColor: Color,
    menuOpen: Boolean,
    onSelectTab: (String) -> Unit,
    onToggleMenu: () -> Unit,
) {
    val tabs = listOf(
        FrostedTab("ALL", stringResource(R.string.nav_home), Icons.Filled.Home, Icons.Outlined.Home),
        FrostedTab("MOVIE", stringResource(R.string.nav_movies), Icons.Filled.Movie, Icons.Outlined.Movie),
        FrostedTab("SERIES", stringResource(R.string.nav_series), Icons.Filled.Tv, Icons.Outlined.Tv),
        FrostedTab("LIVE", stringResource(R.string.nav_bar_live), Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
    )
    // Ayarlar ve Listem profil menüsünden açılır: o sayfalardayken profil düğmesi vurgulanır.
    val profileActive = menuOpen || selectedTypeFilter in setOf("SETTINGS", "WATCHLIST", "GAMES", "SPORTS")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(stripColor)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("frosted_bottom_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(FrostedBarColor)
                .border(1.dp, FrostedBorderColor, RoundedCornerShape(34.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTypeFilter == tab.filterType
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(SelectedTabColor)
                                    .border(1.dp, FrostedBorderColor, RoundedCornerShape(24.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            // Telefonda görünüm aynı (vurgu yok); TV'de kumanda odağı görünür.
                            indication = com.example.ui.tv.tvFocusIndicationOrNull()
                        ) { onSelectTab(tab.filterType) }
                        .testTag("bottom_nav_${tab.filterType}")
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.active else tab.inactive,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else InactiveTabText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color.White else InactiveTabText,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val menuLabel = stringResource(R.string.profile_menu_open)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(AvatarBg)
                    .border(2.dp, if (profileActive) Accent else Color(0x40FFFFFF), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = com.example.ui.tv.tvFocusIndicationOrNull()
                    ) { onToggleMenu() }
                    .semantics { contentDescription = menuLabel }
                    .testTag("bottom_nav_profile_menu")
            ) {
                ProfileAvatar(photoUrl = photoUrl, iconSize = 26)
            }
        }
    }
}

@Composable
private fun ProfileAvatar(photoUrl: String?, iconSize: Int) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )
    } else {
        // Profil resmi yoksa insan figürü.
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = AvatarIcon,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

/** Profil düğmesinin açtığı buzlu menü. Arkaya dokununca ya da Geri tuşuyla kapanır. */
@Composable
fun FrostedProfileMenu(
    displayName: String,
    photoUrl: String?,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onGames: () -> Unit,
    onSport: () -> Unit,
    onMyList: () -> Unit,
    showGames: Boolean = true,
    showSport: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Surface(
                color = FrostedMenuColor,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Menü alt barın hemen üstünde açılır (bar yüksekliği + boşluk).
                    .padding(start = 14.dp, end = 14.dp, bottom = 92.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* menünün içine dokunmak menüyü kapatmaz */ }
                    .testTag("frosted_profile_menu")
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(46.dp).clip(CircleShape).background(AvatarBg)
                        ) {
                            ProfileAvatar(photoUrl = photoUrl, iconSize = 24)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = displayName,
                            color = Color(0xFFF4F1F8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = Color(0x14FFFFFF))
                    MenuRow(Icons.Filled.Settings, stringResource(R.string.settings_title), pro = false, tag = "menu_settings", onClick = onSettings)
                    MenuRow(Icons.Filled.Person, stringResource(R.string.nav_profile), pro = false, tag = "menu_profile", onClick = onProfile)
                    if (showGames) MenuRow(Icons.Filled.SportsEsports, stringResource(R.string.profile_menu_games), pro = true, tag = "menu_games", onClick = onGames)
                    if (showSport) MenuRow(Icons.Filled.SportsSoccer, stringResource(R.string.profile_menu_sport), pro = true, tag = "menu_sport", onClick = onSport)
                    MenuRow(Icons.Filled.Bookmark, stringResource(R.string.profile_menu_my_list), pro = false, tag = "menu_my_list", onClick = onMyList)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, pro: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = com.example.ui.tv.tvFocusIndicationOrNull()
            ) { onClick() }
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFF4F1F8), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = Color(0xFFF4F1F8),
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )
        if (pro) {
            Text(
                text = stringResource(R.string.profile_menu_pro_tag),
                color = Color(0xFF1A0F24),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(GoldTag)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MenuMutedText,
            modifier = Modifier.size(20.dp)
        )
    }
}
