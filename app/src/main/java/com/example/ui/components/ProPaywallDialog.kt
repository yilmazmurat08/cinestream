package com.example.ui.components

import android.app.Activity
import com.example.util.findActivity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.SubscriptionManager

enum class ProPackageType(
    val productId: String,
    val title: String,
    val priceText: String,
    val periodText: String,
    val badgeText: String? = null
) {
    MONTHLY(
        productId = SubscriptionManager.PRODUCT_MONTHLY,
        title = "Aylık Paket",
        priceText = "₺49,99",
        periodText = "/ ay"
    ),
    YEARLY(
        productId = SubscriptionManager.PRODUCT_YEARLY,
        title = "Yıllık Paket",
        priceText = "₺399,99",
        periodText = "/ yıl (₺33,33 / ay)",
        badgeText = "En Popüler • %40 İndirim"
    ),
    LIFETIME(
        productId = SubscriptionManager.PRODUCT_LIFETIME,
        title = "Ömür Boyu Paket",
        priceText = "₺799,99",
        periodText = "Tek Seferlik Ödeme"
    )
}

@Composable
fun ProPaywallDialog(
    reasonMessage: String? = null,
    onDismiss: () -> Unit,
    onPurchaseSuccess: () -> Unit
) {
    val layout = rememberAppAdaptiveLayout()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var selectedPackage by remember { mutableStateOf(ProPackageType.YEARLY) }
    var isLoading by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = layout.dialogMaxWidth)
                .heightIn(max = if (layout.isLandscape) 420.dp else 680.dp)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(CineOrange, Color(0xFFFF007A), Color(0xFF9D00FF))
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .testTag("pro_paywall_dialog"),
            color = Color(0xFF140D2B) // Deep dark purple / neon background
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Background ambient glow gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    CineOrange.copy(alpha = 0.25f),
                                    Color(0xFF9D00FF).copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Crown / PRO Badge Header Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(CineOrange, Color(0xFFFF007A)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "PRO",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title
                    Text(
                        text = "CineStream PRO'ya Yükselt",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Reason / Alert notice if present
                    if (!reasonMessage.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CineOrange.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, CineOrange.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = CineOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reasonMessage,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CineOrange,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            text = "Aşağıdaki ayrıcalıklarla kısıntısız sinema ve canlı TV deneyimini başlatın.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Features List Section
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProFeatureRow(text = "Sınırsız Canlı TV, Film ve Dizi İzleme")
                            ProFeatureRow(text = "AI Destekli Bölüm Özetleri")
                            ProFeatureRow(text = "4K YAPAY ZEKÂ DESTEKLİ Trend Vitrini")
                            ProFeatureRow(text = "Sınırsız Radyo & Reklamsız Deneyim")
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Bir Paket Seçin",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3 Package Options (Radio Cards)
                    ProPackageType.entries.forEach { pkg ->
                        ProPackageRadioCard(
                            packageType = pkg,
                            isSelected = (selectedPackage == pkg),
                            onSelect = { selectedPackage = pkg }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button [Devam Et / Satın Al]
                    Button(
                        onClick = {
                            if (activity != null) {
                                isLoading = true
                                SubscriptionManager.purchasePackage(
                                    activity = activity,
                                    productId = selectedPackage.productId,
                                    onSuccess = {
                                        isLoading = false
                                        Toast.makeText(context, "CineStream PRO Aktif Edildi!", Toast.LENGTH_LONG).show()
                                        onPurchaseSuccess()
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        Toast.makeText(context, "Satın alma hatası: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                onPurchaseSuccess()
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Unspecified
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(
                                Brush.horizontalGradient(listOf(CineOrange, Color(0xFFE91E63))),
                                RoundedCornerShape(16.dp)
                            )
                            .testTag("paywall_purchase_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Devam Et / Satın Al (${selectedPackage.priceText})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Restore Purchase Link
                    Text(
                        text = "Satın Alımları Geri Yükle (Restore Purchase)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                isLoading = true
                                SubscriptionManager.restorePurchases(
                                    context = context,
                                    onSuccess = { isPro ->
                                        isLoading = false
                                        if (isPro) {
                                            Toast.makeText(context, "Satın alımlarınız başarıyla geri yüklendi!", Toast.LENGTH_LONG).show()
                                            onPurchaseSuccess()
                                        } else {
                                            Toast.makeText(context, "Geçerli bir PRO aboneliği bulunamadı.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        Toast.makeText(context, "Geri yükleme hatası: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            .padding(8.dp)
                            .testTag("paywall_restore_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E676).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun ProPackageRadioCard(
    packageType: ProPackageType,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) CineOrange else Color.White.copy(alpha = 0.15f)
    val containerBg = if (isSelected) CineOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("package_card_${packageType.productId}")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Package badge if present
            packageType.badgeText?.let { badge ->
                Surface(
                    color = CineOrange,
                    shape = RoundedCornerShape(bottomStart = 10.dp, topEnd = 16.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio Circle
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (isSelected) CineOrange else Color.White.copy(alpha = 0.4f),
                            CircleShape
                        )
                        .background(if (isSelected) CineOrange else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = packageType.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = packageType.periodText,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = packageType.priceText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) CineOrange else Color.White
                )
            }
        }
    }
}
