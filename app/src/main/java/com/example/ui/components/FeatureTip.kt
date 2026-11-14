package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.R
import kotlinx.coroutines.delay

/**
 * Bir kez gösterilen bilgi balonları (kullanıcı isteği, 14 Kasım 2026): az bilinen özellikleri, kullanıcı o ekrana
 * ilk girdiğinde özelliğin yanında anlatır. Her balon cihazda bir kez görünür; aynı anda yalnızca bir balon açıktır.
 */
object FeatureTips {
    private const val PREFS = "feature_tips"
    const val COVER = "cover_change"
    const val FOLDERS = "folder_edit"
    const val GO_TO_MINUTE = "go_to_minute"
    const val EPG_REMINDER = "epg_reminder"
    const val NEW_EPISODE = "new_episode"
    const val AGE_LIMIT = "age_limit"
    const val SUBTITLES = "subtitles"
    const val RESUME = "resume_shelf"
    const val VISIBLE_MS = 12_000L

    /** Robolectric testlerinde varsayılan kapalı (eski ekran testleri balon beklemiyor); balon testleri açar. */
    @Volatile var enabled = android.os.Build.FINGERPRINT != "robolectric"

    /** Şu an açık balonun kimliği (aynı anda tek balon). */
    internal var active by mutableStateOf<String?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun seen(context: Context, id: String): Boolean = runCatching { prefs(context).getBoolean(id, false) }.getOrDefault(true)
    fun markSeen(context: Context, id: String) { runCatching { prefs(context).edit().putBoolean(id, true).apply() } }
    fun resetAll(context: Context) { runCatching { prefs(context).edit().clear().apply() }; active = null }
}

/** Balonu hedefin altına (yer yoksa üstüne) koyar, ekrandan taşırmaz. */
private class TipPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Hedef öğenin içine (ya da yanına) konur; balon o öğeye göre konumlanır. [show] false iken hiçbir şey yapmaz.
 * Balon odak almaz (TV'de kumanda ekranda kalır), 12 sn sonra ya da Geri ile kapanır; telefonda "Anladım" ile de.
 * [onClick] verilirse telefonda balona dokunmak o işi yapar (ör. düzenleme ekranını açar).
 */
@Composable
fun FeatureTip(id: String, text: String, show: Boolean = true, tvMode: Boolean? = null, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    var shownHere by remember(id) { mutableStateOf(false) }
    var closed by remember(id) { mutableStateOf(false) }
    val tvMode = tvMode ?: ((context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)
    val canOpen = show && !closed && FeatureTips.enabled && FeatureTips.active == null

    LaunchedEffect(id, canOpen) {
        if (!canOpen || shownHere) return@LaunchedEffect
        if (FeatureTips.seen(context, id)) { closed = true; return@LaunchedEffect }
        delay(700) // ekran yerleşsin
        // Aynı balon birden çok yerde olabilir (ör. her kanal kartında): yalnızca ilk gelen gösterir.
        if (FeatureTips.active != null || FeatureTips.seen(context, id)) { if (FeatureTips.active != id) closed = true; return@LaunchedEffect }
        FeatureTips.active = id
        FeatureTips.markSeen(context, id)
        shownHere = true
    }
    fun close() { closed = true; shownHere = false; if (FeatureTips.active == id) FeatureTips.active = null }
    DisposableEffect(id) { onDispose { if (FeatureTips.active == id) FeatureTips.active = null } }
    if (!shownHere || !show) return

    LaunchedEffect(Unit) { delay(FeatureTips.VISIBLE_MS); close() }
    androidx.activity.compose.BackHandler { close() }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    Popup(popupPositionProvider = remember(gap) { TipPositionProvider(gap) }, onDismissRequest = { close() },
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
        Surface(
            color = Color(0xF2150F22),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFD946EF).copy(alpha = 0.7f)),
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = 320.dp)
                .focusProperties { canFocus = false }
                .then(if (onClick != null && !tvMode) Modifier.clickable { close(); onClick() } else Modifier)
                .testTag("feature_tip_$id")
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = Color(0xFFD946EF), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                }
                if (!tvMode) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.intro_hint_ok), color = Color(0xFFD946EF), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.End).clickable { close() }.padding(start = 8.dp, top = 2.dp).testTag("feature_tip_ok_$id")
                    )
                }
            }
        }
    }
}

/** Favori düğmesine konur: dizi favoriye ilk eklendiği anda (boştan doluya geçince) balonu gösterir. */
@Composable
fun FavoriteAddedTip(id: String, text: String, isFavorite: Boolean, tvMode: Boolean? = null) {
    var previous by remember { mutableStateOf(isFavorite) }
    var justAdded by remember { mutableStateOf(false) }
    LaunchedEffect(isFavorite) {
        if (isFavorite && !previous) justAdded = true
        previous = isFavorite
    }
    FeatureTip(id, text, show = justAdded, tvMode = tvMode)
}
