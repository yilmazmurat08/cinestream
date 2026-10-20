package com.example.ui.features.games

import com.example.data.model.IPTVItem
import kotlin.random.Random

/**
 * Oyunların ekrandan bağımsız kuralları (test edilebilir). Hepsi kullanıcının kendi listesinden beslenir;
 * uygulamaya hazır içerik konmaz.
 */
object GamesLogic {

    const val DECK_SIZE = 40
    const val WHEEL_SLICES = 8
    const val WHEEL_PICKS = 3
    const val QUIZ_LIVES = 3

    /** Kaydırma destesi: posteri olan film ve diziler, karışık; yetişkin içerik yok, aynı yapım bir kez. */
    fun swipeDeck(items: List<IPTVItem>, isAdult: (IPTVItem) -> Boolean, random: Random = Random.Default): List<IPTVItem> =
        items.asSequence()
            .filter { (it.type == "MOVIE" || it.type == "SERIES") && !it.logoUrl.isNullOrBlank() && !isAdult(it) }
            .distinctBy { it.cleanedName.ifBlank { it.name }.lowercase() }
            .toList()
            .shuffled(random)
            .take(DECK_SIZE)

    /** Çark dilimleri: en az [WHEEL_PICKS] posterli filmi olan kategoriler, rastgele en çok [WHEEL_SLICES] tane. */
    fun wheelCategories(
        moviesByCategory: Map<String, List<IPTVItem>>,
        isAdultCategory: (String) -> Boolean,
        random: Random = Random.Default
    ): List<String> =
        moviesByCategory.entries
            .filter { (cat, list) -> cat.isNotBlank() && !isAdultCategory(cat) && list.count { !it.logoUrl.isNullOrBlank() } >= WHEEL_PICKS }
            .map { it.key }
            .shuffled(random)
            .take(WHEEL_SLICES)

    /** Çarkın durduğu kategoriden 3 rastgele posterli film. */
    fun wheelPicks(movies: List<IPTVItem>, isAdult: (IPTVItem) -> Boolean, random: Random = Random.Default): List<IPTVItem> =
        movies.filter { !it.logoUrl.isNullOrBlank() && !isAdult(it) }.shuffled(random).take(WHEEL_PICKS)

    /** Çark [slices] dilimdeyken [angleDegrees] açıyla durunca üstteki (saat 12) dilimin sırası. */
    fun sliceAtTop(angleDegrees: Float, slices: Int): Int {
        if (slices <= 0) return 0
        val sweep = 360f / slices
        // Dilim 0 saat 12'den başlar ve saat yönünde ilerler; çark saat yönünde döner.
        val pointer = ((360f - (angleDegrees % 360f)) % 360f + 360f) % 360f
        return (pointer / sweep).toInt().coerceIn(0, slices - 1)
    }

    // ---- Poster puzzle ----
    // order[konum] = o konumdaki parçanın asıl sırası. Her parça kendi sırasındaysa puzzle çözülmüştür.

    /** Karışık başlangıç; hiçbir zaman çözülmüş hâlde başlamaz. */
    fun puzzleShuffle(pieces: Int, random: Random = Random.Default): List<Int> {
        if (pieces < 2) return (0 until pieces).toList()
        var order: List<Int>
        do {
            order = (0 until pieces).shuffled(random)
        } while (isPuzzleSolved(order))
        return order
    }

    fun isPuzzleSolved(order: List<Int>): Boolean = order.isNotEmpty() && order.withIndex().all { it.index == it.value }

    /** İki konumdaki parçaların yerini değiştirir. */
    fun puzzleSwap(order: List<Int>, a: Int, b: Int): List<Int> {
        if (a == b || a !in order.indices || b !in order.indices) return order
        return order.toMutableList().also { it[a] = order[b]; it[b] = order[a] }
    }

    // ---- Dizi bilgi yarışması ----

    /** Bir dizinin soru üretmeye yarayan gerçek bilgileri (TMDB, yoksa listenin kendi bilgisi). */
    data class ShowFacts(
        val title: String,
        val year: String? = null,
        val genre: String? = null,
        val leadActor: String? = null,
        val seasons: Int? = null
    )

    enum class Fact { YEAR, GENRE, LEAD_ACTOR, SEASONS }

    /** Soru: doğru şık her zaman dizinin gerçek bilgisi; yanlış şık listedeki başka bir diziden. */
    data class Question(val fact: Fact, val optionA: String, val optionB: String, val correctIsA: Boolean) {
        val correct: String get() = if (correctIsA) optionA else optionB
    }

    fun yearOf(releaseDate: String?): String? =
        releaseDate?.trim()?.take(4)?.takeIf { y -> y.length == 4 && y.all { it.isDigit() } && y.toInt() in 1900..2100 }

    private fun value(f: ShowFacts, fact: Fact): String? = when (fact) {
        Fact.YEAR -> f.year
        Fact.GENRE -> f.genre
        Fact.LEAD_ACTOR -> f.leadActor
        Fact.SEASONS -> f.seasons?.takeIf { it > 0 }?.toString()
    }?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Hedef dizi için sorular. Her bilgi türünden en çok bir soru; yanlış şık, aynı bilgisi farklı olan başka bir
     * diziden alınır. Spoiler yok: konu ve son bölüm bilgisi kullanılmaz.
     */
    fun questions(target: ShowFacts, others: List<ShowFacts>, random: Random = Random.Default): List<Question> =
        Fact.entries.mapNotNull { fact ->
            val right = value(target, fact) ?: return@mapNotNull null
            val wrong = others.asSequence()
                .filter { it.title != target.title }
                .mapNotNull { value(it, fact) }
                .filter { !it.equals(right, ignoreCase = true) }
                .distinct()
                .toList()
                .randomOrNull(random) ?: return@mapNotNull null
            val correctIsA = random.nextBoolean()
            Question(fact, if (correctIsA) right else wrong, if (correctIsA) wrong else right, correctIsA)
        }.shuffled(random)

    /** Yarışma durumu: üç hak, doğru cevapta kazanılır, üç yanlışta biter. */
    data class QuizState(val index: Int = 0, val wrong: Int = 0, val won: Boolean = false, val lastWrong: Boolean = false) {
        val lost: Boolean get() = wrong >= QUIZ_LIVES
        val finished: Boolean get() = won || lost
        val livesLeft: Int get() = (QUIZ_LIVES - wrong).coerceAtLeast(0)
    }

    fun answer(state: QuizState, question: Question, choseA: Boolean): QuizState = when {
        state.finished -> state
        choseA == question.correctIsA -> state.copy(won = true, lastWrong = false)
        else -> state.copy(index = state.index + 1, wrong = state.wrong + 1, lastWrong = true)
    }
}
