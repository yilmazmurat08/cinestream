package com.example.stress

import org.junit.Assume.assumeTrue

/** Stres testleri yalnızca `-Pstress=true` ile çalışır (normal test turunu yavaşlatmasın). */
internal fun requireStressMode() {
    assumeTrue("Stres testi: -Pstress=true ile çalıştırın", System.getProperty("cinestream.stress") == "true")
}

internal fun usedHeapMb(): Long {
    val rt = Runtime.getRuntime()
    System.gc()
    return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
}

internal inline fun <T> timed(label: String, block: () -> T): Pair<T, Double> {
    val start = System.nanoTime()
    val result = block()
    val seconds = (System.nanoTime() - start) / 1e9
    println("[STRES] $label: %.2f sn".format(seconds))
    return result to seconds
}
