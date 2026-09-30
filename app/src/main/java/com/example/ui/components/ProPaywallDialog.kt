package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.legal.LegalDoc
import com.example.ui.theme.CineOrange
import com.example.ui.theme.rememberAppAdaptiveLayout
import com.example.util.ProOffer
import com.example.util.PurchaseOutcome
import com.example.util.SubscriptionManager
import com.example.util.findActivity

/** Paywall'daki paketler (gösterim sırası: en avantajlı yıllık önce). */
enum class ProPackageType(val productId: String, val titleRes: Int, val periodRes: Int) {
    YEARLY(SubscriptionManager.PRODUCT_YEARLY, R.string.paywall_yearly, R.string.paywall_per_year),
    MONTHLY(SubscriptionManager.PRODUCT_MONTHLY, R.string.paywall_monthly, R.string.paywall_per_month),
    LIFETIME(SubscriptionManager.PRODUCT_LIFETIME, R.string.paywall_lifetime, R.string.paywall_one_time)
}

/**
 * CineStream PRO satın alma ekranı (telefon ve TV). Fiyatlar Google Play'den okunur; PRO yalnızca Google Play
 * satın alımı tamamlanınca açılır. PRO kullanıcıya abonelik yönetimi gösterilir.
 */
@Composable
fun ProPaywallDialog(
    reasonMessage: String? = null,
    isPro: Boolean,
    onDismiss: () -> Unit,
    onOpenLegal: (LegalDoc) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val offers by SubscriptionManager.offers.collectAsState()
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { SubscriptionManager.refresh() }

    fun toast(res: Int, vararg args: Any) = Toast.makeText(context, context.getString(res, *args), Toast.LENGTH_LONG).show()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PaywallContent(
            reasonMessage = reasonMessage,
            isPro = isPro,
            offers = offers,
            busy = busy,
            onDismiss = onDismiss,
            onBuy = { productId ->
                if (activity == null) {
                    toast(R.string.paywall_unavailable)
                    return@PaywallContent
                }
                busy = true
                SubscriptionManager.purchase(activity, productId) { outcome ->
                    busy = false
                    when (outcome) {
                        PurchaseOutcome.Success -> {
                            toast(R.string.paywall_success)
                            onDismiss()
                        }
                        PurchaseOutcome.Pending -> toast(R.string.paywall_pending)
                        PurchaseOutcome.AlreadyOwned -> toast(R.string.paywall_already_owned)
                        PurchaseOutcome.Unavailable -> toast(R.string.paywall_unavailable)
                        PurchaseOutcome.Cancelled -> Unit
                        is PurchaseOutcome.Failed -> toast(R.string.paywall_failed, outcome.code)
                    }
                }
            },
            onRestore = {
                busy = true
                SubscriptionManager.restore { result ->
                    busy = false
                    when (result) {
                        true -> {
                            toast(R.string.paywall_restored)
                            onDismiss()
                        }
                        false -> toast(R.string.paywall_restore_none)
                        null -> toast(R.string.paywall_unavailable)
                    }
                }
            },
            onManage = {
                // Google Play'in bu uygulamaya ait abonelik sayfası (iptal / ödeme yöntemi).
                val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { toast(R.string.paywall_unavailable) }
            },
            onOpenLegal = { doc ->
                onDismiss()
                onOpenLegal(doc)
            }
        )
    }
}

/** Paywall içeriği (test edilebilir: paketler ve geri çağrılar dışarıdan verilir). */
@Composable
internal fun PaywallContent(
    reasonMessage: String?,
    isPro: Boolean,
    offers: Map<String, ProOffer>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onBuy: (String) -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit,
    onOpenLegal: (LegalDoc) -> Unit
) {
    val layout = rememberAppAdaptiveLayout()
    var selected by remember { mutableStateOf(ProPackageType.YEARLY) }
    val firstFocus = remember { FocusRequester() }
    val savings = remember(offers) {
        val m = offers[SubscriptionManager.PRODUCT_MONTHLY]?.priceMicros ?: 0L
        val y = offers[SubscriptionManager.PRODUCT_YEARLY]?.priceMicros ?: 0L
        SubscriptionManager.yearlySavingsPercent(m, y)
    }
    val context = LocalContext.current
    // TV kumandasıyla açıldığında odak ilk pakette (veya PRO ise "Aboneliği yönet"te) başlar.
    val tvControls = com.example.ui.tv.TvUiMode.active || remember { com.example.ui.tv.TvDevice.isTv(context) }
    LaunchedEffect(Unit) { if (tvControls) com.example.ui.tv.requestFocusWhenReady(firstFocus) }

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = layout.dialogMaxWidth)
            .heightIn(max = if (layout.isLandscape) 520.dp else 720.dp)
            .wrapContentHeight()
            .padding(vertical = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(
                BorderStroke(1.5.dp, Brush.linearGradient(listOf(CineOrange, Color(0xFFFF007A), Color(0xFF9D00FF)))),
                RoundedCornerShape(24.dp)
            )
            .testTag("pro_paywall_dialog"),
        color = Color(0xFF140D2B)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Brush.verticalGradient(listOf(CineOrange.copy(alpha = 0.25f), Color(0xFF9D00FF).copy(alpha = 0.15f), Color.Transparent)))
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).testTag("paywall_close")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.paywall_close), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(CineOrange, Color(0xFFFF007A)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (isPro) R.string.paywall_pro_active_title else R.string.paywall_title),
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                if (!isPro && !reasonMessage.isNullOrEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CineOrange.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("paywall_reason")
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = CineOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(reasonMessage, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CineOrange)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        stringResource(if (isPro) R.string.paywall_pro_active_desc else R.string.paywall_subtitle),
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProFeatureRow(stringResource(R.string.pro_point_unlimited))
                        ProFeatureRow(stringResource(R.string.pro_point_devices))
                        ProFeatureRow(stringResource(R.string.pro_point_account))
                    }
                }
                Spacer(Modifier.height(18.dp))

                if (isPro) {
                    Button(
                        onClick = onManage,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CineOrange),
                        modifier = Modifier.fillMaxWidth().height(50.dp).focusRequester(firstFocus).testTag("paywall_manage")
                    ) { Text(stringResource(R.string.paywall_manage), fontWeight = FontWeight.Bold, color = Color.White) }
                } else {
                    Text(
                        stringResource(R.string.paywall_choose), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f), modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    ProPackageType.entries.forEachIndexed { index, pkg ->
                        ProPackageRadioCard(
                            packageType = pkg,
                            offer = offers[pkg.productId],
                            badge = if (pkg == ProPackageType.YEARLY) savings?.let { stringResource(R.string.paywall_save_percent, it) } else null,
                            isSelected = selected == pkg,
                            onSelect = { selected = pkg },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (offers.isEmpty()) {
                        Text(
                            stringResource(R.string.paywall_prices_loading), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag("paywall_prices_loading")
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val selectedOffer = offers[selected.productId]
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onBuy(selected.productId) },
                        enabled = !busy && selectedOffer != null,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Unspecified, disabledContainerColor = Color.White.copy(alpha = 0.12f)),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                if (selectedOffer != null) Brush.horizontalGradient(listOf(CineOrange, Color(0xFFE91E63)))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("paywall_purchase_button")
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (selectedOffer != null) stringResource(R.string.paywall_buy, selectedOffer.formattedPrice) else stringResource(pkgTitle(selected)),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.paywall_auto_renew), fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center, lineHeight = 15.sp, modifier = Modifier.testTag("paywall_auto_renew")
                    )
                    TextButton(onClick = onRestore, enabled = !busy, modifier = Modifier.testTag("paywall_restore_button")) {
                        Text(stringResource(R.string.paywall_restore), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                    }
                }
                com.example.ui.legal.LegalLinks(onOpen = onOpenLegal, modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

private fun pkgTitle(pkg: ProPackageType): Int = pkg.titleRes

@Composable
private fun ProFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF00E676).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun ProPackageRadioCard(
    packageType: ProPackageType,
    offer: ProOffer?,
    badge: String?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) CineOrange else Color.White.copy(alpha = 0.15f)
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) CineOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = modifier.fillMaxWidth().testTag("package_card_${packageType.productId}")
    ) {
        Box(Modifier.fillMaxWidth()) {
            badge?.let {
                Surface(color = CineOrange, shape = RoundedCornerShape(bottomStart = 10.dp, topEnd = 16.dp), modifier = Modifier.align(Alignment.TopEnd)) {
                    Text(it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (isSelected) CineOrange else Color.White.copy(alpha = 0.4f), CircleShape)
                        .background(if (isSelected) CineOrange else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) { if (isSelected) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(packageType.titleRes), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(stringResource(packageType.periodRes), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                }
                Text(
                    offer?.formattedPrice ?: "—",
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) CineOrange else Color.White,
                    modifier = Modifier.testTag("package_price_${packageType.productId}")
                )
            }
        }
    }
}
