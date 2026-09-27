package com.example

import androidx.compose.ui.test.MainTestClock
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick

/**
 * İlk açılıştaki görünüm modu seçimini (varsa) yapar ve seçim ekranı kapanana kadar bekler
 * (seçim DataStore'a arka planda yazılır). Seçilince bir daha sorulmaz.
 */
internal fun SemanticsNodeInteractionsProvider.chooseViewModeIfAsked(
    tag: String = "mode_card_phone",
    clock: MainTestClock? = null
) {
    if (onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()) return
    onNode(hasTestTag(tag)).performClick()
    repeat(100) {
        clock?.advanceTimeBy(100)
        Thread.sleep(20)
        if (onAllNodes(hasTestTag("mode_selection_screen")).fetchSemanticsNodes().isEmpty()) {
            clock?.advanceTimeBy(1_000)
            return
        }
    }
    error("Görünüm modu seçildikten sonra seçim ekranı kapanmadı")
}
