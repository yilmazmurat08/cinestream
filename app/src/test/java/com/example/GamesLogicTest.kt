package com.example

import com.example.data.model.IPTVItem
import com.example.ui.features.games.GamesLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GamesLogicTest {

    private fun item(id: Int, type: String = "MOVIE", logo: String? = "http://x/$id.png", name: String = "Yapım $id", cat: String = "Aksiyon") =
        IPTVItem(id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = logo, streamUrl = "http://x/$id", category = cat, type = type)

    @Test
    fun swipeDeck_onlyPosteredMoviesAndSeries_noAdult_noDuplicates() {
        val items = listOf(
            item(1), item(2, logo = null), item(3, type = "LIVE"), item(4, type = "SERIES"),
            item(5, cat = "XXX"), item(6, name = "Yapım 1")
        )
        val deck = GamesLogic.swipeDeck(items, { it.category == "XXX" }, Random(1))
        assertEquals(setOf(1, 4), deck.map { it.id }.toSet())
    }

    @Test
    fun swipeDeck_isCapped() {
        val deck = GamesLogic.swipeDeck((1..500).map { item(it) }, { false }, Random(2))
        assertEquals(GamesLogic.DECK_SIZE, deck.size)
    }

    @Test
    fun wheelCategories_needThreePosters_andSkipAdult() {
        val map = mapOf(
            "Korku" to (1..3).map { item(it) },
            "Savaş" to listOf(item(10), item(11), item(12, logo = null)),
            "Yetişkin" to (20..25).map { item(it) },
            "" to (30..35).map { item(it) }
        )
        val cats = GamesLogic.wheelCategories(map, { it == "Yetişkin" }, Random(3))
        assertEquals(listOf("Korku"), cats)
    }

    @Test
    fun wheelCategories_atMostEight() {
        val map = (1..20).associate { c -> "Kategori $c" to (1..3).map { item(c * 10 + it) } }
        assertEquals(GamesLogic.WHEEL_SLICES, GamesLogic.wheelCategories(map, { false }, Random(4)).size)
    }

    @Test
    fun sliceAtTop_matchesDrawing() {
        // Dönmemiş çarkta saat 12'deki dilim 0; çark saat yönünde 1 dilim dönünce üstte son dilim olur.
        assertEquals(0, GamesLogic.sliceAtTop(0f, 8))
        assertEquals(7, GamesLogic.sliceAtTop(1f, 8))
        assertEquals(6, GamesLogic.sliceAtTop(46f, 8))
        assertEquals(5, GamesLogic.sliceAtTop(91f, 8))
        assertEquals(7, GamesLogic.sliceAtTop(360f * 5 + 10f, 8))
        for (a in 0 until 3600 step 7) {
            val idx = GamesLogic.sliceAtTop(a.toFloat(), 5)
            assertTrue(idx in 0 until 5)
        }
    }

    @Test
    fun questions_correctIsRealFact_wrongFromAnotherShow() {
        val target = GamesLogic.ShowFacts("La Brea", year = "2021", genre = "Bilim kurgu · Dram", leadActor = "Natalie Zea", seasons = 3)
        val others = listOf(
            GamesLogic.ShowFacts("Dizi A", year = "2017", genre = "Komedi", leadActor = "Ali Veli", seasons = 2),
            GamesLogic.ShowFacts("Dizi B", year = "2021", genre = null, leadActor = null, seasons = 3)
        )
        val qs = GamesLogic.questions(target, others, Random(5))
        assertEquals(4, qs.size)
        qs.forEach { q ->
            val wrong = if (q.correctIsA) q.optionB else q.optionA
            when (q.fact) {
                GamesLogic.Fact.YEAR -> { assertEquals("2021", q.correct); assertEquals("2017", wrong) }
                GamesLogic.Fact.GENRE -> { assertEquals("Bilim kurgu · Dram", q.correct); assertEquals("Komedi", wrong) }
                GamesLogic.Fact.LEAD_ACTOR -> { assertEquals("Natalie Zea", q.correct); assertEquals("Ali Veli", wrong) }
                GamesLogic.Fact.SEASONS -> { assertEquals("3", q.correct); assertEquals("2", wrong) }
            }
        }
    }

    @Test
    fun questions_skipFactWhenNoDifferentWrongAnswer() {
        val target = GamesLogic.ShowFacts("X", year = "2020")
        val others = listOf(GamesLogic.ShowFacts("Y", year = "2020"))
        assertTrue(GamesLogic.questions(target, others).isEmpty())
    }

    @Test
    fun quiz_threeLives_winOnCorrect() {
        val q = GamesLogic.Question(GamesLogic.Fact.YEAR, "2021", "2017", correctIsA = true)
        var s = GamesLogic.QuizState()
        s = GamesLogic.answer(s, q, choseA = false)
        assertEquals(2, s.livesLeft); assertTrue(s.lastWrong); assertFalse(s.finished)
        s = GamesLogic.answer(s, q, choseA = true)
        assertTrue(s.won); assertTrue(s.finished)
        assertEquals(s, GamesLogic.answer(s, q, choseA = false))

        var l = GamesLogic.QuizState()
        repeat(3) { l = GamesLogic.answer(l, q, choseA = false) }
        assertTrue(l.lost); assertEquals(0, l.livesLeft)
    }

    @Test
    fun puzzle_neverStartsSolved_andSwapsSolveIt() {
        for (seed in 0 until 200) {
            val order = GamesLogic.puzzleShuffle(9, Random(seed))
            assertEquals((0 until 9).toSet(), order.toSet())
            assertFalse(GamesLogic.isPuzzleSolved(order))
        }
        // Her parçayı kendi yerine taşıyan yer değiştirmelerle puzzle çözülür.
        var order = GamesLogic.puzzleShuffle(16, Random(7))
        for (pos in order.indices) {
            val from = order.indexOf(pos)
            order = GamesLogic.puzzleSwap(order, pos, from)
        }
        assertTrue(GamesLogic.isPuzzleSolved(order))
    }

    @Test
    fun puzzleSwap_ignoresInvalidPositions() {
        val order = listOf(1, 0, 2)
        assertEquals(listOf(0, 1, 2), GamesLogic.puzzleSwap(order, 0, 1))
        assertEquals(order, GamesLogic.puzzleSwap(order, 1, 1))
        assertEquals(order, GamesLogic.puzzleSwap(order, -1, 5))
        assertFalse(GamesLogic.isPuzzleSolved(emptyList()))
    }

    @Test
    fun yearOf_acceptsOnlyRealYears() {
        assertEquals("2021", GamesLogic.yearOf("2021-09-28"))
        assertEquals(null, GamesLogic.yearOf(""))
        assertEquals(null, GamesLogic.yearOf("abcd"))
        assertEquals(null, GamesLogic.yearOf("0001-01-01"))
    }
}
