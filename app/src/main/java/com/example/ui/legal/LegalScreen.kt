package com.example.ui.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.legal.LegalDoc
import com.example.data.legal.LegalDocument
import com.example.data.legal.LegalDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uygulamanın web sürümündeki yasal sayfalarla aynı renkler (koyu mor zemin, pembe-mor vurgu). */
private object LegalColors {
    val Background = Color(0xFF0D0A16)
    val BackgroundTop = Color(0xFF1A1330)
    val Panel = Color(0xFF17122A)
    val PanelAlt = Color(0xFF1E1832)
    val Border = Color(0xFF2E2547)
    val Text = Color(0xFFEDE9F7)
    val TextDim = Color(0xFFA79FC4)
    val TextFaint = Color(0xFF756D93)
    val Accent = Color(0xFFE93DE0)
    val AccentSoft = Color(0xFFC084FC)
}

/**
 * Hizmet Şartları / Gizlilik Politikası ekranı (telefon ve TV). TV'de her bölüm odaklanabilir; kumandanın
 * yukarı/aşağı tuşlarıyla bölümler arasında gezilir ve sayfa kendiliğinden kayar. Geri tuşu ekranı kapatır.
 */
@Composable
fun LegalScreen(doc: LegalDoc, language: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val content by produceState<LegalDocument?>(initialValue = null, doc, language) {
        value = withContext(Dispatchers.IO) { LegalDocuments.load(context, doc, language) }
    }
    BackHandler(onBack = onClose)
    val firstFocus = remember { FocusRequester() }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to LegalColors.BackgroundTop, 0.45f to LegalColors.Background))
            .testTag("legal_screen_${doc.name.lowercase()}")
    ) {
        val document = content
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("legal_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.legal_back), tint = LegalColors.Text)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Brush.linearGradient(listOf(LegalColors.Accent, Color(0xFF7C3AED)))),
                        contentAlignment = Alignment.Center
                    ) { Text("C", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        buildAnnotatedString {
                            append("Cine")
                            withStyle(SpanStyle(color = LegalColors.AccentSoft)) { append("Stream") }
                        },
                        color = LegalColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(18.dp))

                if (document == null) {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LegalColors.Accent)
                    }
                } else {
                    Text(document.title, color = LegalColors.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(document.updated, color = LegalColors.TextFaint, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    LegalBlock(Modifier.focusRequester(firstFocus)) {
                        LegalText(document.intro, LegalColors.TextDim)
                    }
                    Spacer(Modifier.height(12.dp))
                    LegalBlock(highlight = true) {
                        LegalText(document.highlight, LegalColors.Text, fontWeight = FontWeight.Medium)
                    }
                    document.sections.forEachIndexed { index, section ->
                        Spacer(Modifier.height(12.dp))
                        LegalBlock(Modifier.testTag("legal_section_$index")) {
                            Text(section.heading, color = LegalColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            section.paragraphs.forEach { Spacer(Modifier.height(8.dp)); LegalText(it, LegalColors.TextDim) }
                            if (section.bullets.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                section.bullets.forEach { bullet ->
                                    Row(Modifier.padding(top = 6.dp)) {
                                        Box(
                                            Modifier
                                                .padding(top = 8.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(LegalColors.Accent)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        LegalText(bullet, LegalColors.TextDim)
                                    }
                                }
                            }
                            section.after.forEach { Spacer(Modifier.height(8.dp)); LegalText(it, LegalColors.TextDim) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "© 2026 CineStream",
                        color = LegalColors.TextFaint, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        // TV'de kumanda odağı ilk karttan başlar; telefonda dokunmatik kullanıldığı için odak verilmez.
        val tvControls = com.example.ui.tv.TvUiMode.active || remember { com.example.ui.tv.TvDevice.isTv(context) }
        LaunchedEffect(document != null) {
            if (document != null && tvControls) com.example.ui.tv.requestFocusWhenReady(firstFocus)
        }
    }
}

/** Bölüm kartı: TV'de odaklanınca kenarlığı parlar (kumandayla okunan yer belli olsun). */
@Composable
private fun LegalBlock(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlight) LegalColors.PanelAlt else LegalColors.Panel)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> LegalColors.Accent
                    highlight -> LegalColors.AccentSoft.copy(alpha = 0.45f)
                    else -> LegalColors.Border
                },
                shape = shape
            )
            .focusable(interactionSource = interaction)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) { content() }
}

/** Metin: "**kalın**" kısımlar kalın, adresler dokunulabilir bağlantı olarak çizilir. */
@Composable
private fun LegalText(text: String, color: Color, fontWeight: FontWeight = FontWeight.Normal) {
    Text(legalAnnotated(text), color = color, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = fontWeight)
}

private val URL_REGEX = Regex("""https?://[^\s)]+""")

internal fun legalAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = LegalColors.AccentSoft, textDecoration = TextDecoration.Underline))
    text.split("**").forEachIndexed { i, part ->
        val bold = i % 2 == 1
        var last = 0
        fun plain(s: String) {
            if (s.isEmpty()) return
            if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = LegalColors.Text)) { append(s) } else append(s)
        }
        URL_REGEX.findAll(part).forEach { m ->
            plain(part.substring(last, m.range.first))
            withLink(LinkAnnotation.Url(m.value, linkStyle)) { append(m.value) }
            last = m.range.last + 1
        }
        plain(part.substring(last))
    }
}
