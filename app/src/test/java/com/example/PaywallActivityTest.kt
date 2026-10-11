package com.example

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.ui.components.rememberHostActivity
import com.example.util.LocaleHelper
import com.example.util.findActivity
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uygulama dili için LocalContext, Activity'den türetilmiş ayrı bir bağlamla (createConfigurationContext) değiştirilir.
 * Bu bağlamdan Activity bulunamadığı için PRO satın alma ekranı Google Play'i hiç açmadan "Satın alma şu an
 * kullanılamıyor" diyordu. Satın alma için Activity, ekranın kendi görünümünden bulunmalı.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaywallActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hostActivity_isFound_evenWhenContextIsLocalized() {
        var fromContext: Activity? = null
        var host: Activity? = null
        compose.setContent {
            val localized = LocaleHelper.updateResources(compose.activity, "tr")
            CompositionLocalProvider(LocalContext provides localized) {
                fromContext = LocalContext.current.findActivity()
                host = rememberHostActivity()
            }
        }
        compose.waitForIdle()
        assertNull("dil bağlamından Activity bulunamıyor (hatanın nedeni)", fromContext)
        assertSame("satın alma için Activity bulunmalı", compose.activity, host)
    }
}
