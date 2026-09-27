package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.api.NetworkModule
import com.example.data.api.TmdbAuth
import com.example.data.model.IPTVItem
import com.example.data.model.tmdb.PersonCredit
import com.example.data.model.tmdb.PersonCreditsLookup
import com.example.data.model.tmdb.PersonCreditsOutcome
import com.example.data.model.tmdb.PersonWorksResult
import com.example.data.model.tmdb.TmdbCreditItem
import com.example.data.model.tmdb.TmdbPersonSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Oyuncu/yönetmen filmografisi: kişiyi TMDB'de bulur, film + dizi rollerini tek çağrıda
 * (person/{id}/combined_credits) getirir ve kütüphaneyle eşleştirir.
 * Anahtar her zaman TmdbAuth'tan gelir; Secrets'taki link gibi geçersiz değerler kullanılmaz.
 */
class PersonCreditsRepository(private val context: Context) {

    private val selfCharacterRegex = Regex("(?i)\\b(self|himself|herself|themselves|kendisi)\\b")
    private val nonFictionTvGenres = setOf(10763, 10764, 10767) // Haber, Reality, Talk show

    /** Talk show, ödül töreni, haber gibi kişinin "kendisi" olarak göründüğü kayıtlar filmografiye girmez. */
    private fun isSelfAppearance(credit: TmdbCreditItem): Boolean {
        val character = credit.character.orEmpty()
        if (character.isNotBlank() && selfCharacterRegex.containsMatchIn(character)) return true
        return credit.mediaType == "tv" && credit.genreIds.orEmpty().any { it in nonFictionTvGenres }
    }

    private fun toPersonCredit(credit: TmdbCreditItem): PersonCredit? {
        val type = when (credit.mediaType) {
            "movie" -> "movie"
            "tv" -> "tv"
            else -> return null
        }
        val localized = credit.displayTitle.trim()
        val original = credit.displayOriginalTitle.trim()
        if (localized.isEmpty() && original.isEmpty()) return null
        return PersonCredit(
            tmdbId = credit.id,
            mediaType = type,
            title = localized.ifEmpty { original },
            originalTitle = original.ifEmpty { localized },
            year = credit.yearInt,
            posterUrl = TMDBRepository.buildImageUrl(credit.posterPath, "w342"),
            popularity = credit.popularity ?: 0.0
        )
    }

    private fun departmentMatches(department: String?, wantsDirector: Boolean): Boolean {
        val dept = department.orEmpty()
        return if (wantsDirector) dept == "Directing" || dept == "Writing" || dept == "Creator" else dept == "Acting"
    }

    /** Yalnızca debug derlemede Ayarlar → Çökme Günlükleri'nde görünür. Anahtarın kendisi değil, sadece türü yazılır. */
    private fun writeDiag(message: String) =
        com.example.util.DiagnosticLog.write(context, "person_otherworks_diag_last", message, timestamped = false)

    /**
     * Aynı isimli kişiler karışmasın diye önce tam isim eşleşmesi, sonra meslek (oyuncu/yönetmen),
     * en son popülerlik dikkate alınır.
     */
    suspend fun lookupPersonCredits(
        personName: String,
        role: String,
        tmdbApiKey: String
    ): PersonCreditsOutcome = withContext(Dispatchers.IO) {
        val query = PersonWorksMatcher.primaryName(personName)
        val apiKey = TmdbAuth.resolve(tmdbApiKey)
        if (apiKey.isBlank()) {
            writeDiag(
                "DİĞER YAPIMLAR: Geçerli TMDB anahtarı yok.\nKişi: $query\n" +
                "Ayarlardan gelen: ${TmdbAuth.describe(tmdbApiKey)}\n" +
                "Secrets/.env (BuildConfig): ${TmdbAuth.describe(BuildConfig.TMDB_API_KEY)}"
            )
            return@withContext PersonCreditsOutcome.Failed("TMDB anahtarı tanımlı değil")
        }
        try {
            val results = NetworkModule.tmdbService.searchPerson(query = query, apiKey = apiKey).results
                .filter { !it.name.isNullOrBlank() }
            val wanted = PersonWorksMatcher.fold(query)
            val wantsDirector = role.contains("yönetmen", ignoreCase = true) ||
                role.contains("yaratıcı", ignoreCase = true) ||
                role.contains("director", ignoreCase = true)
            val person = results.maxWithOrNull(
                compareBy<TmdbPersonSearchResult>(
                    { if (PersonWorksMatcher.fold(it.name) == wanted) 1 else 0 },
                    { if (departmentMatches(it.knownForDepartment, wantsDirector)) 1 else 0 },
                    { it.popularity ?: 0.0 }
                )
            )
            if (person == null) {
                writeDiag("DİĞER YAPIMLAR: TMDB'de kişi bulunamadı.\nAranan: $query")
                return@withContext PersonCreditsOutcome.NotFound
            }

            val response = NetworkModule.tmdbService.getPersonCombinedCredits(personId = person.id, apiKey = apiKey)
            val credits = (response.cast + response.crew)
                .asSequence()
                .filter { !isSelfAppearance(it) }
                .mapNotNull { toPersonCredit(it) }
                .distinctBy { it.key }
                .toList()

            PersonCreditsOutcome.Found(
                PersonCreditsLookup(
                    personId = person.id,
                    name = person.name ?: query,
                    profileUrl = TMDBRepository.buildImageUrl(person.profilePath, TMDBRepository.SIZE_PROFILE),
                    credits = credits
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: retrofit2.HttpException) {
            val reason = if (e.code() == 401) "TMDB anahtarı reddedildi (401)" else "TMDB hatası (HTTP ${e.code()})"
            writeDiag(
                "DİĞER YAPIMLAR: $reason\nKişi: $query\n" +
                "Kullanılan: ${TmdbAuth.describe(apiKey)}\n" +
                "Secrets/.env (BuildConfig): ${TmdbAuth.describe(BuildConfig.TMDB_API_KEY)}\n" +
                "Çözüm: themoviedb.org → Ayarlar → API sayfasındaki 32 karakterlik 'API Anahtarı'nı ya da " +
                "'API Okuma Erişim Jetonu'nu TMDB_API_KEY'e yapıştır (sayfanın linkini değil)."
            )
            PersonCreditsOutcome.Failed(reason)
        } catch (e: java.io.IOException) {
            writeDiag("DİĞER YAPIMLAR: Ağ hatası\nKişi: $query\n${e.javaClass.simpleName}: ${e.message}")
            PersonCreditsOutcome.Failed("İnternet bağlantısı yok")
        } catch (e: Exception) {
            writeDiag("DİĞER YAPIMLAR: Beklenmeyen hata\nKişi: $query\n${e.javaClass.simpleName}: ${e.message}")
            PersonCreditsOutcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------------
    // Kadro satırındaki oyuncu/yönetmen fotoğrafı
    // ------------------------------------------------------------------

    /** İsim → fotoğraf adresi. "" = TMDB'de bu kişinin fotoğrafı yok (bu oturumda tekrar sorulmaz). */
    private val photoCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Kadro avatarı için tek TMDB araması yeterli: arama sonucu profile_path'i zaten içeriyor.
     * Bulunan fotoğraf kişi önbelleğine (Room) de yazılır, bir daha aranmaz. Ağ hatasında "yok" diye
     * işaretlenmez, sonraki açılışta yeniden denenir. Eski yol oturum başına tek deneme hakkı veriyordu;
     * o deneme ekran yeniden çizilirken iptal olursa avatar baş harfte kalıyordu.
     */
    suspend fun lookupPhoto(personName: String, role: String): String? = withContext(Dispatchers.IO) {
        val name = PersonWorksMatcher.primaryName(personName)
        val key = name.lowercase(java.util.Locale.ROOT).trim()
        if (key.isBlank()) return@withContext null
        photoCache[key]?.let { return@withContext it.ifEmpty { null } }

        val dao = com.example.data.db.AppDatabase.getDatabase(context).iptvDao()
        val cached = try {
            dao.getPersonDetails(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val cachedUrl = cached?.imageUrl
        if (!cachedUrl.isNullOrBlank()) {
            photoCache[key] = cachedUrl
            return@withContext cachedUrl
        }

        val apiKey = TmdbAuth.resolve()
        if (apiKey.isBlank()) return@withContext null
        val person = try {
            val results = NetworkModule.tmdbService.searchPerson(query = name, apiKey = apiKey).results
                .filter { !it.name.isNullOrBlank() }
            val wanted = PersonWorksMatcher.fold(name)
            val wantsDirector = role.contains("yönetmen", ignoreCase = true) ||
                role.contains("yaratıcı", ignoreCase = true) ||
                role.contains("director", ignoreCase = true)
            results.maxWithOrNull(
                compareBy<TmdbPersonSearchResult>(
                    { if (PersonWorksMatcher.fold(it.name) == wanted) 1 else 0 },
                    { if (departmentMatches(it.knownForDepartment, wantsDirector)) 1 else 0 },
                    { it.popularity ?: 0.0 }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext null
        }

        val url = TMDBRepository.buildImageUrl(person?.profilePath, TMDBRepository.SIZE_PROFILE)
        photoCache[key] = url.orEmpty()
        if (!url.isNullOrBlank()) {
            try {
                val entity = cached?.copy(imageUrl = url) ?: com.example.data.model.PersonDetailsEntity(
                    name = key,
                    displayName = person?.name ?: name,
                    role = if (role.contains("yönetmen", ignoreCase = true)) "Yönetmen" else "Oyuncu",
                    biography = "",
                    imageUrl = url
                )
                dao.insertPersonDetails(entity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        url
    }

    suspend fun matchToLibrary(
        lookup: PersonCreditsLookup,
        libraryItems: List<IPTVItem>,
        excludeItemId: Int
    ): PersonWorksResult = withContext(Dispatchers.Default) {
        val result = PersonWorksMatcher.match(lookup, libraryItems, excludeItemId)
        writeDiag(
            "DİĞER YAPIMLAR eşleştirme sonucu\n" +
            "Kişi: ${lookup.name} (TMDB id=${lookup.personId})\n" +
            "TMDB yapım sayısı: ${lookup.credits.size}\n" +
            "Taranan kütüphane öğesi: ${libraryItems.size}\n" +
            "Kütüphanede bulunan: ${result.inLibrary.size}\n" +
            "Bulunanlar: ${result.inLibrary.take(15).map { "${it.item.cleanedName} = ${it.credit?.title}" }}\n" +
            "İlk 10 TMDB başlığı: ${lookup.credits.take(10).map { "${it.title} / ${it.originalTitle}" }}\n" +
            "İlk 10 kütüphane başlığı: ${libraryItems.take(10).map { it.cleanedName }}"
        )
        result
    }
}
