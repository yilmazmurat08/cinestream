package com.example.util

import androidx.annotation.DrawableRes
import com.example.R
import java.util.Locale

/**
 * Film ve dizi klasörlerinin kart görseli. Klasör adındaki kelimeye bakılır: önce tür (aksiyon, korku…), sonra
 * özel klasörler (4K, yerli, klasik…). Marka logosu ya da ona benzeyen görsel
 * kullanılmaz. Hiçbirine uymayan klasöre (platform klasörleri dahil) genel sinema görseli gelir.
 */
object CategoryArt {
    private val GENRES: List<Pair<Int, List<String>>> = listOf(
        R.drawable.genre_animation to listOf("animasyon", "animation", "anime", "çizgi", "cizgi", "cartoon"),
        R.drawable.genre_kids to listOf("çocuk", "cocuk", "kids", "kid ", "children"),
        R.drawable.genre_family to listOf("aile", "family"),
        R.drawable.genre_documentary to listOf("belgesel", "documentary", "docu"),
        R.drawable.genre_horror to listOf("korku", "horror"),
        R.drawable.genre_mystery to listOf("gizem", "mystery", "esrarengiz"),
        R.drawable.genre_thriller to listOf("gerilim", "thriller", "suspense"),
        R.drawable.genre_scifi to listOf("bilim kurgu", "bilimkurgu", "sci fi", "scifi", "science fiction"),
        R.drawable.genre_fantasy to listOf("fantastik", "fantasy", "fantezi"),
        R.drawable.genre_war to listOf("savaş", "savas", " war", "war ", "military"),
        R.drawable.genre_crime to listOf("suç", "suc ", "polisiye", "crime", "mafya", "gangster"),
        R.drawable.genre_romance to listOf("romantik", "romance", "romantizm", "aşk"),
        R.drawable.genre_comedy to listOf("komedi", "comedy"),
        R.drawable.genre_adventure to listOf("macera", "adventure"),
        R.drawable.genre_action to listOf("aksiyon", "action"),
        R.drawable.genre_drama to listOf("dram", "drama"),
    )
    private val SPECIAL: List<Pair<Int, List<String>>> = listOf(
        R.drawable.genre_new to listOf("yeni", "vizyon", " new ", "latest", "sinemada", "in cinema"),
        R.drawable.genre_4k to listOf("4k", "uhd", "2160"),
        R.drawable.genre_local to listOf("yerli", "türk", "turk"),
        R.drawable.genre_classic to listOf("klasik", "classic", "nostalji", "yesilcam", "yeşilçam"),
        R.drawable.genre_awards to listOf("imdb", "oscar", "ödül", "odul", "best", "top 10", "top10", "en iyi"),
    )
    private val LIVE: List<Pair<Int, List<String>>> = listOf(
        R.drawable.live_news to listOf("haber", "news"),
        R.drawable.live_sports to listOf("spor", "sport", "futbol", "football"),
        R.drawable.live_music to listOf("müzik", "muzik", "music"),
        R.drawable.live_religious to listOf(" dini", "religious", "islam", "kuran", "quran"),
        R.drawable.live_relax to listOf("relax", "doğa", "doga", "nature", "ambient", "manzara"),
        R.drawable.live_radio to listOf("radyo", "radio"),
        R.drawable.genre_documentary to listOf("belgesel", "documentary", "docu"),
        R.drawable.genre_kids to listOf("çocuk", "cocuk", "kids", "cartoon", "çizgi", "cizgi"),
        R.drawable.genre_general to listOf("sinema", "cinema", "film", "movie"),
    )

    /** Yetişkin klasörleri (film, dizi, canlı): insan içermeyen neon "18+" tabelası. */
    @DrawableRes
    val ADULT: Int = R.drawable.adult_neon

    /** Canlı klasör görseli: haber, spor, müzik…; eşleşmeyen klasöre genel televizyon görseli gelir. */
    @DrawableRes
    fun forLive(name: String): Int {
        val n = normalize(name)
        return LIVE.firstOrNull { (_, keys) -> keys.any { it in n } }?.first ?: R.drawable.live_tv
    }

    private fun normalize(name: String): String =
        // "AKSİYON" küçük harfe "aksi̇yon" (noktalı i) olarak döner; nokta atılır, ı da i sayılır.
        " " + name.lowercase(Locale.ROOT).replace("\u0307", "").replace('ı', 'i')
            .replace('|', ' ').replace('_', ' ').replace('-', ' ') + " "

    /** Kart görseli. Platform klasörleri (Netflix, Disney…) dahil eşleşmeyen her klasör genel görseli alır. */
    @DrawableRes
    fun forFolder(name: String): Int {
        val n = normalize(name)
        GENRES.firstOrNull { (_, keys) -> keys.any { it in n } }?.let { return it.first }
        SPECIAL.firstOrNull { (_, keys) -> keys.any { it in n } }?.let { return it.first }
        return R.drawable.genre_general
    }
}
