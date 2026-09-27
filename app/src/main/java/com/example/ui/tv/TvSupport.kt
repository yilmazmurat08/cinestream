package com.example.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android TV desteği: cihaz tespiti, kumandayla gezinirken görünür odak, detay panelinde odağın
 * panel içinde kalması ve oynatıcıda kumanda tuşları. Telefon ve tablette hiçbir şeyi değiştirmez.
 */
object TvDevice {
    @Volatile
    private var cached: Boolean? = null

    /** TV (Android TV / Google TV / TV kutusu) mu? Sonuç önbelleğe alınır. */
    fun isTv(context: Context): Boolean {
        cached?.let { return it }
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val result = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        cached = result
        return result
    }
}

private val TvFocusRingColor = Color(0xFFE879F9)

/**
 * TV'de odaklanan her tıklanabilir öğe hafifçe büyür ve etrafında mor bir çerçeve çıkar.
 * Kumandayla gezinirken hangi öğenin seçili olduğu uzaktan net görünür.
 */
object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class TvFocusIndicationNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {
    private var focusCount = 0
    private var pressCount = 0

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release -> pressCount = (pressCount - 1).coerceAtLeast(0)
                    is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (focusCount > 0) {
            scale(1.05f) {
                this@draw.drawContent()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.06f),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
                drawRoundRect(
                    color = TvFocusRingColor,
                    style = Stroke(width = 3.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
        } else {
            drawContent()
        }
        if (pressCount > 0) {
            drawRect(color = Color.White.copy(alpha = 0.10f))
        }
    }
}

/**
 * Uygulama temasının içine yerleşir. TV'de tüm tıklanabilir öğelere TV odak görünümünü verir ve
 * Material düğmelerinin odak vurgusunu belirginleştirir; telefonda içeriği olduğu gibi gösterir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvideTvFocus(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTv = remember { TvDevice.isTv(context) }
    if (isTv) {
        CompositionLocalProvider(
            LocalIndication provides TvFocusIndication,
            LocalRippleConfiguration provides RippleConfiguration(
                color = TvFocusRingColor,
                rippleAlpha = RippleAlpha(
                    draggedAlpha = 0.16f,
                    focusedAlpha = 0.40f,
                    hoveredAlpha = 0.08f,
                    pressedAlpha = 0.24f
                )
            ),
            content = content
        )
    } else {
        content()
    }
}

/**
 * Detay paneli için: panel açılınca odak panelin içine taşınır ve kumandayla panelin dışına
 * (arkadaki ana sayfaya) çıkılamaz. key değişince (panelde başka yapım açılınca) odak yeniden ayarlanır.
 * Telefonda etkisizdir.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun tvModalFocus(key: Any?): Modifier = Modifier.composed {
    val context = LocalContext.current
    if (!remember { TvDevice.isTv(context) }) return@composed Modifier
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        delay(350)
        try {
            requester.requestFocus()
        } catch (_: Exception) {
        }
    }
    Modifier
        .focusRequester(requester)
        .focusProperties { exit = { FocusRequester.Cancel } }
        .focusGroup()
}

/**
 * Oynatıcı ekranı için kumanda tuşları. Kontroller gizliyken: OK = kontrolleri göster,
 * sol/sağ = 10 sn geri/ileri, yukarı/aşağı = kontrolleri göster. Kontroller açıkken tuşlar
 * düğmeler arasında gezinmek için normal çalışır ve her basış kontrollerin gizlenme süresini yeniler.
 * Medya tuşları her zaman çalışır.
 */
fun tvPlayerKeys(
    focusRequester: FocusRequester,
    isOverlayHidden: () -> Boolean,
    onShowControls: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onUserActivity: () -> Unit = {}
): Modifier = Modifier
    .focusRequester(focusRequester)
    .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        onUserActivity()
        when (event.key) {
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                onPlayPause()
                true
            }
            Key.MediaFastForward -> {
                onSeek(10_000L)
                true
            }
            Key.MediaRewind -> {
                onSeek(-10_000L)
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                if (isOverlayHidden()) {
                    onShowControls()
                    true
                } else {
                    false
                }
            Key.DirectionLeft ->
                if (isOverlayHidden()) {
                    onSeek(-10_000L)
                    true
                } else {
                    false
                }
            Key.DirectionRight ->
                if (isOverlayHidden()) {
                    onSeek(10_000L)
                    true
                } else {
                    false
                }
            Key.DirectionUp, Key.DirectionDown ->
                if (isOverlayHidden()) {
                    onShowControls()
                    true
                } else {
                    false
                }
            else -> false
        }
    }
    .focusable()
