package com.example

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.util.CrashRecoveryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Çökmeden sonraki açılışta hata raporu gösterilmeli (TV dahil), bir kez gösterildikten sonra tekrar çıkmamalı. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReportFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun afterACrash_nextLaunchShowsTheReport_thenNormalStartResumes() {
        CrashRecoveryManager.handleUncaughtException(context, Thread.currentThread(), IllegalStateException("TV açılış hatası testi"))
        assertNotNull(CrashRecoveryManager.pendingCrashReport(context))

        // Uygulama açılınca MainActivity hiçbir şey yüklemeden rapor ekranına yönlendirir.
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertTrue(main.isFinishing)
        assertEquals(CrashReportActivity::class.java.name, shadowOf(main).nextStartedActivity.component?.className)

        // Rapor ekranı hata kaydını gösterir.
        val report = Robolectric.buildActivity(CrashReportActivity::class.java).setup().get()
        val root = report.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout
        val log = ((root.getChildAt(2) as ScrollView).getChildAt(0) as TextView).text.toString()
        assertTrue(log.contains("TV açılış hatası testi"))

        // "Uygulamayı aç" raporu kapatır ve uygulamayı başlatır; rapor bir daha gösterilmez.
        val buttons = root.getChildAt(3) as LinearLayout
        (buttons.getChildAt(1) as Button).performClick()
        assertNull(CrashRecoveryManager.pendingCrashReport(context))
        assertEquals(MainActivity::class.java.name, shadowOf(report).nextStartedActivity.component?.className)
    }

    /** Kullanıcı rapor ekranını düğmeye basmadan (Geri tuşu, kaydırma) kapatsa da rapor tekrar çıkmaz. */
    @Test
    fun reportClosedWithBack_isNotShownAgain() {
        CrashRecoveryManager.handleUncaughtException(context, Thread.currentThread(), IllegalStateException("geri tuşu testi"))
        assertNotNull(CrashRecoveryManager.pendingCrashReport(context))
        val controller = Robolectric.buildActivity(CrashReportActivity::class.java).setup()
        controller.get().onBackPressed()
        controller.pause().stop().destroy()
        assertNull(CrashRecoveryManager.pendingCrashReport(context))
    }

    /** Uygulama güncellendikten sonra eski sürümün çökme raporu gösterilmez. */
    @Test
    fun reportFromBeforeAnUpdate_isIgnored() {
        CrashRecoveryManager.handleUncaughtException(context, Thread.currentThread(), IllegalStateException("eski sürüm hatası"))
        val file = CrashRecoveryManager.pendingCrashReport(context)!!
        file.setLastModified(System.currentTimeMillis() - 60 * 60_000L)
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime = System.currentTimeMillis()
        shadowOf(context.packageManager).installPackage(info)
        assertNull(CrashRecoveryManager.pendingCrashReport(context))
    }
}
