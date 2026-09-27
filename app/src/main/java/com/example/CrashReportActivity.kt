package com.example

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.util.CrashRecoveryManager

/**
 * Önceki çalıştırma çöktüyse açılışta gösterilen hata raporu. Bilerek Compose kullanmaz ve hiçbir veri
 * yüklemez: uygulamanın geri kalanı her açılışta çökse bile rapor okunabilsin (telefon ve TV kumandası).
 * Kullanıcı ekranın fotoğrafını çekip geliştiriciye gönderebilir.
 */
class CrashReportActivity : Activity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.example.util.LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashRecoveryManager.pendingCrashReport(this)
        val log = try {
            report?.readLines()?.take(150)?.joinToString("\n").orEmpty()
        } catch (e: Throwable) {
            ""
        }

        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

        val title = TextView(this).apply {
            text = getString(R.string.crash_report_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = TextView(this).apply {
            text = getString(R.string.crash_report_body)
            setTextColor(Color.parseColor("#CFC8E6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(0, dp(8), 0, dp(12))
        }
        val logText = TextView(this).apply {
            text = log.ifBlank { getString(R.string.crash_report_empty) }
            setTextColor(Color.parseColor("#FFD7F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            isFocusable = true // TV kumandası ile kaydırılabilsin
            setBackgroundColor(Color.parseColor("#1B1030"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(logText)
        }
        val open = Button(this).apply {
            text = getString(R.string.crash_report_open)
            setOnClickListener {
                CrashRecoveryManager.clearPendingCrashReport(this@CrashReportActivity)
                startActivity(Intent(this@CrashReportActivity, MainActivity::class.java))
                finish()
            }
        }
        val close = Button(this).apply {
            text = getString(R.string.crash_report_close)
            setOnClickListener {
                CrashRecoveryManager.clearPendingCrashReport(this@CrashReportActivity)
                finish()
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), 0, 0)
            addView(close)
            addView(open)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E071A"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            fitsSystemWindows = true
            addView(title)
            addView(body)
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }
        setContentView(root)
        open.requestFocus()
    }
}
