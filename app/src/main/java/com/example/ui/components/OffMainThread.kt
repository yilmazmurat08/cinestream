package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bu sayıdan küçük listeler ana iş parçacığında hesaplanır (fark edilmeyecek kadar hızlı). */
private const val INLINE_WORK_LIMIT = 2_000

/**
 * Büyük IPTV listeleri (40.000+ öğe) üzerinde filtreleme, gruplama ve sıralama gibi işleri
 * ana iş parçacığını kilitlemeden yapar. Küçük listelerde sonuç ilk karede hazırdır; büyük
 * listelerde ilk karede [fallback] gösterilir ve hesap Dispatchers.Default üzerinde yapılır.
 * Anahtarlar değişince önceki sonuç, yenisi hazır olana kadar ekranda kalır.
 */
@Composable
fun <T> rememberComputedOffMain(
    vararg keys: Any?,
    workSize: Int,
    fallback: T,
    compute: () -> T
): T {
    val inline = workSize <= INLINE_WORK_LIMIT
    val initial = remember { if (inline) compute() else fallback }
    return produceState(initialValue = initial, *keys) {
        value = if (inline) compute() else withContext(Dispatchers.Default) { compute() }
    }.value
}
