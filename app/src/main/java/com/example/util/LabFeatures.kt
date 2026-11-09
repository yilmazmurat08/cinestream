package com.example.util

import com.example.BuildConfig

/**
 * Deneme özellikleri: yalnızca test sürümlerinde (FEATURE_LAB) açık; mağaza sürümünde kapalı. Test sürümünde satın
 * alma çalışmadığı için PRO deneme özellikleri orada herkese açıktır; mağazaya alınırken PRO'ya bağlanır.
 */
object LabFeatures {
    val enabled: Boolean get() = BuildConfig.FEATURE_LAB

    fun proFeature(isProUser: Boolean): Boolean = enabled && (isProUser || BuildConfig.FEATURE_LAB)
}
