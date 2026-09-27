package com.example.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AccentNeonPurple
import com.example.ui.theme.DeepPurpleBg
import com.example.ui.theme.MidPurpleBg
import com.example.ui.theme.MutedPurpleText
import com.example.ui.theme.PalePurpleText
import com.example.ui.theme.WhiteText

/**
 * TV arayüzünün tasarım değerleri. Renkler telefon temasından (Color.kt) gelir; cam değerleri telefondaki alt
 * gezinme çubuğunun cam stilinin aynısıdır (MidPurpleBg yarı saydam + mor degrade ince kenarlık).
 * Temada karşılığı olmayan tonlar burada TV'ye özel değer olarak tanımlıdır; telefon renkleri değişmez.
 */
object TvTheme {
    val Background = DeepPurpleBg
    val TextPrimary = WhiteText
    val TextSecondary = PalePurpleText
    val TextMuted = MutedPurpleText
    val Accent = AccentNeonPurple

    /** Telefondaki alt çubukla aynı cam yüzey. */
    val Glass = MidPurpleBg.copy(alpha = 0.85f)

    /** TV'ye özel: video/görsel üstündeki koyu cam paneller (blur yerine yarı saydam katman). */
    val GlassDark = DeepPurpleBg.copy(alpha = 0.78f)

    /** TV'ye özel: odaklı öğenin hafif mor tonu. */
    val FocusTint = AccentNeonPurple.copy(alpha = 0.14f)

    /** TV'ye özel: odak parlaması (mevcut TV odak halkası rengi). */
    val FocusGlow = Color(0xFFE879F9)

    /** Telefondaki alt çubuğun kenarlığı. */
    val GlassBorder = Brush.verticalGradient(listOf(AccentNeonPurple.copy(alpha = 0.25f), AccentNeonPurple.copy(alpha = 0.05f)))

    val CardShape = RoundedCornerShape(24.dp)
    val RowShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(50)

    /** Odaklı öğenin büyüme oranı (~%8). */
    const val FocusScale = 1.08f
}

/** Telefondaki cam stilinde, odaklanamayan yüzey (panel, çubuk). */
fun Modifier.tvGlass(shape: Shape = TvTheme.CardShape, color: Color = TvTheme.Glass): Modifier =
    this.clip(shape).background(color).border(BorderStroke(1.dp, TvTheme.GlassBorder), shape)

/**
 * Kumanda ve dokunmayla seçilebilen cam yüzey. Odaklanınca Apple TV tarzı: hafifçe büyür (%8), ince mor parlak
 * çerçeve ve hafif mor ton alır, yumuşak mor gölgeyle öne çıkar (3 metreden seçilebilir).
 * `content` odak durumunu alır (ör. ikon rengini değiştirmek için).
 */
@Composable
fun TvGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TvTheme.CardShape,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    focusScale: Float = TvTheme.FocusScale,
    glassColor: Color = TvTheme.Glass,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(160), label = "tvFocusScale")
    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 18.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = TvTheme.FocusGlow,
                spotColor = TvTheme.FocusGlow
            )
            .clip(shape)
            .background(glassColor)
            .background(if (focused) TvTheme.FocusTint else Color.Transparent)
            .border(
                if (focused) BorderStroke(2.dp, TvTheme.FocusGlow) else BorderStroke(1.dp, TvTheme.GlassBorder),
                shape
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .tvClickable(interaction, onClick)
    ) {
        content(focused)
    }
}

/**
 * Tek odak hedefli tıklama: kumanda/klavye (OK, Enter) ve dokunma ile tetiklenir, her giriş modunda
 * odaklanabilir (Compose'un `clickable`'ı dokunmatik modda odaklanamaz; TV'de algılanan seçeneğin hazır
 * odaklı gelmesi için gerekli). Erişilebilirlik için düğme olarak işaretlenir.
 */
fun Modifier.tvClickable(interactionSource: MutableInteractionSource, onClick: () -> Unit): Modifier = this
    .semantics {
        role = Role.Button
        onClick { onClick(); true }
    }
    .onKeyEvent { event ->
        val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (isSelectKey && event.type == KeyEventType.KeyUp) {
            onClick()
            true
        } else {
            isSelectKey
        }
    }
    .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
    .focusable(interactionSource = interactionSource)

/**
 * Odağı [requester]'a verir; hedef henüz yerleşmediyse (ör. BoxWithConstraints/lazy liste içeriği) birkaç kare
 * bekleyip tekrar dener. TV ekranlarının ilk odağı bununla verilir.
 */
suspend fun requestFocusWhenReady(requester: FocusRequester, maxFrames: Int = 30) {
    repeat(maxFrames) {
        val ok = try {
            requester.requestFocus()
            true
        } catch (e: IllegalStateException) {
            false
        }
        if (ok) return
        androidx.compose.runtime.withFrameNanos { }
    }
}
