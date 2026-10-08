package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

/**
 * Yapay zekâ yanıtları için uygulama içi bildirim (Google Play "AI-Generated Content" politikası):
 * her yanıtın altında "yapay zekâ üretti, hatalı olabilir" notu ve uygulamadan çıkmadan kullanılabilen
 * "Bildir" eylemi bulunur. Bildirilen yanıt bu cihazda gizlenir.
 */
enum class AiReportReason(val code: String, val labelRes: Int) {
    HARMFUL("harmful", R.string.ai_report_reason_harmful),
    SEXUAL("sexual", R.string.ai_report_reason_sexual),
    WRONG("wrong", R.string.ai_report_reason_wrong),
    OFF_TOPIC("off_topic", R.string.ai_report_reason_off_topic),
    OTHER("other", R.string.ai_report_reason_other)
}

data class AiReport(val reason: AiReportReason, val note: String, val response: String, val screen: String)

/** Bildirilen yanıtların kimlikleri (yanıt metninin özeti) cihazda tutulur; içerik yeniden gösterilmez. */
object AiReportStore {
    private const val PREFS = "ai_reports"
    private const val KEY_HIDDEN = "hidden"

    fun idOf(response: String): String = Integer.toHexString(response.trim().hashCode())

    fun isHidden(context: Context, response: String): Boolean =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet())?.contains(idOf(response)) == true

    fun hide(context: Context, response: String) {
        val p = prefs(context)
        val set = (p.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()).toMutableSet()
        set += idOf(response)
        p.edit().putStringSet(KEY_HIDDEN, set.toList().takeLast(500).toSet()).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Bildirimi geliştiriciye iletir. [FORM_ACTION_URL] tanımlıysa (Google Form "formResponse" adresi) arka planda
 * gönderilir, kullanıcı uygulamadan çıkmaz. Tanımlı değilse veya gönderim başarısızsa hazır doldurulmuş e-posta açılır.
 */
object AiReportSender {
    /** "Cinestream Ai bildirimleri" Google Formu (herkese açık, oturum açma istemez). */
    const val FORM_ACTION_URL = "https://docs.google.com/forms/d/e/1FAIpQLScktJisHhDoExfqnoGYcMG2YghS-EUlJq1SciDz1lWReI19xA/formResponse"
    const val FIELD_REASON = "entry.333790031"   // Gerekçe
    const val FIELD_NOTE = "entry.43921070"      // Not
    const val FIELD_RESPONSE = "entry.867632422" // Yanıt
    const val FIELD_META = "entry.439200195"     // Sürüm (ekran · sürüm)
    const val DEVELOPER_EMAIL = "yilmazmurat08@gmail.com"

    suspend fun sendInApp(report: AiReport): Boolean = sendTo(FORM_ACTION_URL, report)

    /** Bildirimi verilen form adresine gönderir (testte sahte sunucuya yönlendirilir). */
    internal suspend fun sendTo(url: String, report: AiReport): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        try {
            val body = FormBody.Builder()
                .add(FIELD_REASON, report.reason.code)
                .add(FIELD_NOTE, report.note.take(1000))
                .add(FIELD_RESPONSE, report.response.take(4000))
                .add(FIELD_META, "${report.screen} · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                .build()
            val request = Request.Builder().url(url).post(body).build()
            com.example.data.api.NetworkModule.okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    fun openEmail(context: Context, report: AiReport): Boolean = try {
        val body = buildString {
            append("Reason: ").append(report.reason.code).append('\n')
            append("Note: ").append(report.note).append('\n')
            append("Screen: ").append(report.screen).append(" · v").append(BuildConfig.VERSION_NAME).append('\n')
            append("\nAI response:\n").append(report.response.take(4000))
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_EMAIL"))
            .putExtra(Intent.EXTRA_SUBJECT, "CineStream AI report")
            .putExtra(Intent.EXTRA_TEXT, body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Yapay zekâ yanıtını gösterir; altında AI notu ve "Bildir" vardır. Bildirilen yanıt yerine kısa bir bilgi
 * satırı gösterilir.
 */
@Composable
fun AiReportableContent(
    response: String,
    screen: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hidden by remember(response) { mutableStateOf(AiReportStore.isHidden(context, response)) }
    Column(modifier) {
        if (hidden) {
            Text(
                text = stringResource(R.string.ai_report_hidden),
                color = textColor.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.testTag("ai_report_hidden")
            )
        } else {
            content()
            AiGeneratedFooter(response = response, screen = screen, textColor = textColor, onReported = { hidden = true })
        }
    }
}

@Composable
fun AiGeneratedFooter(response: String, screen: String, textColor: Color = Color.White, onReported: () -> Unit = {}) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.ai_generated_notice),
            color = textColor.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { showDialog = true }, modifier = Modifier.testTag("ai_report_button")) {
            Icon(Icons.Outlined.Flag, contentDescription = null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ai_report_action), color = textColor.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
    if (showDialog) {
        AiReportDialog(
            response = response,
            screen = screen,
            onDismiss = { showDialog = false },
            onReported = { showDialog = false; onReported() }
        )
    }
}

@Composable
private fun AiReportDialog(response: String, screen: String, onDismiss: () -> Unit, onReported: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by rememberSaveable { mutableStateOf(AiReportReason.HARMFUL) }
    var note by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.ai_report_title)) },
        text = {
            Column {
                AiReportReason.entries.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reason = r }
                            .testTag("ai_report_reason_${r.code}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(stringResource(r.labelRes), fontSize = 14.sp)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(1000) },
                    label = { Text(stringResource(R.string.ai_report_note_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("ai_report_note"),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                modifier = Modifier.testTag("ai_report_send"),
                onClick = {
                    sending = true
                    val report = AiReport(reason, note.trim(), response, screen)
                    scope.launch {
                        val sent = AiReportSender.sendInApp(report)
                        AiReportStore.hide(context, response)
                        if (!sent) AiReportSender.openEmail(context, report)
                        Toast.makeText(context, context.getString(R.string.ai_report_thanks), Toast.LENGTH_SHORT).show()
                        sending = false
                        onReported()
                    }
                }
            ) { Text(stringResource(R.string.ai_report_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.cancel)) } }
    )
}
