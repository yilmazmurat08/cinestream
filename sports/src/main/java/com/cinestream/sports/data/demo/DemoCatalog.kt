package com.cinestream.sports.data.demo

import com.cinestream.sports.model.Country
import com.cinestream.sports.model.CrestPattern
import com.cinestream.sports.model.CrestPattern.CHEVRON
import com.cinestream.sports.model.CrestPattern.HALVES
import com.cinestream.sports.model.CrestPattern.HOOPS
import com.cinestream.sports.model.CrestPattern.QUARTERS
import com.cinestream.sports.model.CrestPattern.ROUND
import com.cinestream.sports.model.CrestPattern.SASH
import com.cinestream.sports.model.CrestPattern.SOLID
import com.cinestream.sports.model.CrestPattern.STRIPES
import com.cinestream.sports.model.League
import com.cinestream.sports.model.Sport
import com.cinestream.sports.model.Team
import com.cinestream.sports.model.TeamColors
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

/**
 * Örnek veri kataloğu: ligler, takımlar ve haftalık program. Takım adları gerçek kulüplerdir ama sonuçlar,
 * oyuncular ve istatistikler uydurmadır. Armalar logo değil, takım renklerinden çizilen sade desenlerdir.
 */
internal object DemoCatalog {

    private val TR = Country("TR", "Türkiye")
    private val ENG = Country("GB-ENG", "İngiltere")
    private val ESP = Country("ES", "İspanya")
    private val ITA = Country("IT", "İtalya")
    private val GER = Country("DE", "Almanya")
    private val FRA = Country("FR", "Fransa")
    private val EUR = Country("EU", "Avrupa")
    private val USA = Country("US", "ABD")
    private val WORLD = Country("WORLD", "Dünya")

    class Schedule(
        val league: League,
        val teams: List<Team>,
        val perDay: Map<DayOfWeek, Int>,
        val times: List<LocalTime>,
        val channels: List<String>,
        /** Bu haftanın hafta numarası (puan durumunda oynanan maç sayısı = hafta - 1). */
        val week: Int
    )

    private val everyDay = DayOfWeek.entries

    /** Takımın kendi ligi: spor türü ve adı (takip edilenler ekranında gösterilir). */
    private val teamLeagues = mapOf(
        "tr-super" to (Sport.FOOTBALL to "Süper Lig"),
        "tr-1lig" to (Sport.FOOTBALL to "1. Lig"),
        "tr-2lig" to (Sport.FOOTBALL to "2. Lig"),
        "tr-3lig" to (Sport.FOOTBALL to "3. Lig"),
        "eng-pl" to (Sport.FOOTBALL to "Premier League"),
        "eng-ch" to (Sport.FOOTBALL to "Championship"),
        "esp-laliga" to (Sport.FOOTBALL to "LaLiga"),
        "ita-seriea" to (Sport.FOOTBALL to "Serie A"),
        "ger-bl" to (Sport.FOOTBALL to "Bundesliga"),
        "fra-l1" to (Sport.FOOTBALL to "Ligue 1"),
        "eur" to (Sport.FOOTBALL to "Avrupa"),
        "bsl" to (Sport.BASKETBALL to "Basketbol Süper Ligi"),
        "el-b" to (Sport.BASKETBALL to "EuroLeague"),
        "nba" to (Sport.BASKETBALL to "NBA"),
        "sultanlar" to (Sport.VOLLEYBALL to "Sultanlar Ligi"),
        "efeler" to (Sport.VOLLEYBALL to "Efeler Ligi"),
        "atp" to (Sport.TENNIS to "ATP"),
        "wta" to (Sport.TENNIS to "WTA")
    )

    private fun t(league: String, id: String, name: String, short: String, p: Long, s: Long, pattern: CrestPattern): Team {
        val (sport, leagueName) = teamLeagues.getValue(league)
        return Team(id, name, short, TeamColors(p, s, pattern), league, sport, leagueName)
    }

    private fun times(vararg hm: String) = hm.map { LocalTime.parse(it) }

    // ---- Futbol takımları ----
    private val superLig = listOf(
        t("tr-super", "gs", "Galatasaray", "GS", 0xFFFDB913, 0xFFA90432, HALVES),
        t("tr-super", "fb", "Fenerbahçe", "FB", 0xFFFFED00, 0xFF00205B, STRIPES),
        t("tr-super", "bjk", "Beşiktaş", "BJK", 0xFF111111, 0xFFFFFFFF, STRIPES),
        t("tr-super", "ts", "Trabzonspor", "TS", 0xFF7A1E3A, 0xFF4DA8DA, HALVES),
        t("tr-super", "ibfk", "Başakşehir", "İBFK", 0xFFF37021, 0xFF14294B, HALVES),
        t("tr-super", "sam", "Samsunspor", "SAM", 0xFFE30613, 0xFFFFFFFF, SASH),
        t("tr-super", "goz", "Göztepe", "GÖZ", 0xFFFFD200, 0xFFD7141A, HALVES),
        t("tr-super", "eyp", "Eyüpspor", "EYP", 0xFF5B2C83, 0xFFF5C400, HALVES),
        t("tr-super", "kas", "Kasımpaşa", "KAS", 0xFF1B3F8B, 0xFFFFFFFF, SASH),
        t("tr-super", "kon", "Konyaspor", "KON", 0xFF00843D, 0xFFFFFFFF, HOOPS),
        t("tr-super", "ant", "Antalyaspor", "ANT", 0xFFE30613, 0xFFFFFFFF, STRIPES),
        t("tr-super", "aln", "Alanyaspor", "ALN", 0xFFF7941D, 0xFF00A651, HALVES),
        t("tr-super", "kay", "Kayserispor", "KAY", 0xFFFFD100, 0xFFE30613, HALVES),
        t("tr-super", "riz", "Rizespor", "RİZ", 0xFF00843D, 0xFF0057B8, HALVES),
        t("tr-super", "gfk", "Gaziantep FK", "GFK", 0xFFE30613, 0xFF111111, STRIPES),
        t("tr-super", "koc", "Kocaelispor", "KOC", 0xFF00843D, 0xFF111111, STRIPES),
        t("tr-super", "gb", "Gençlerbirliği", "GB", 0xFFE30613, 0xFF111111, HALVES),
        t("tr-super", "fkg", "Karagümrük", "FKG", 0xFFE30613, 0xFF111111, CHEVRON)
    )
    private val birinciLig = listOf(
        t("tr-1lig", "bod", "Bodrum FK", "BOD", 0xFF00843D, 0xFFFFFFFF, SOLID),
        t("tr-1lig", "sak", "Sakaryaspor", "SAK", 0xFF00843D, 0xFF111111, STRIPES),
        t("tr-1lig", "ban", "Bandırmaspor", "BAN", 0xFFE30613, 0xFF1B3F8B, HALVES),
        t("tr-1lig", "erz", "Erzurumspor", "ERZ", 0xFF0057B8, 0xFFFFFFFF, HOOPS),
        t("tr-1lig", "amd", "Amedspor", "AMD", 0xFFE30613, 0xFF00843D, HALVES),
        t("tr-1lig", "pen", "Pendikspor", "PEN", 0xFFE30613, 0xFFFFFFFF, SOLID),
        t("tr-1lig", "igd", "Iğdır FK", "IĞD", 0xFF0057B8, 0xFFE30613, HALVES),
        t("tr-1lig", "man", "Manisa FK", "MAN", 0xFF111111, 0xFFFFFFFF, HALVES),
        t("tr-1lig", "kec", "Keçiörengücü", "KEÇ", 0xFF5B2C83, 0xFFFFFFFF, SASH),
        t("tr-1lig", "bol", "Boluspor", "BOL", 0xFFE30613, 0xFFFFFFFF, HOOPS),
        t("tr-1lig", "siv", "Sivasspor", "SİV", 0xFFE30613, 0xFFFFFFFF, STRIPES),
        t("tr-1lig", "hat", "Hatayspor", "HAT", 0xFF7A1E3A, 0xFFFFFFFF, CHEVRON)
    )
    private val ikinciLig = listOf(
        t("tr-2lig", "bur", "Bursaspor", "BUR", 0xFF00843D, 0xFFFFFFFF, STRIPES),
        t("tr-2lig", "alt", "Altay", "ALT", 0xFF111111, 0xFFFFFFFF, STRIPES),
        t("tr-2lig", "sur", "Şanlıurfaspor", "ŞUR", 0xFFFFD200, 0xFF00843D, HALVES),
        t("tr-2lig", "buc", "Bucaspor", "BUC", 0xFFFFD200, 0xFF1B3F8B, HALVES),
        t("tr-2lig", "elz", "Elazığspor", "ELZ", 0xFF7A1E3A, 0xFFFFFFFF, SASH),
        t("tr-2lig", "mus", "Muşspor", "MUŞ", 0xFFE30613, 0xFFFFFFFF, SOLID),
        t("tr-2lig", "kir", "Kırklarelispor", "KIR", 0xFF0057B8, 0xFFFFFFFF, HOOPS),
        t("tr-2lig", "isp", "Isparta 32", "ISP", 0xFF111111, 0xFFE30613, HALVES),
        t("tr-2lig", "arn", "Arnavutköy", "ARN", 0xFF0057B8, 0xFFFFD200, HALVES),
        t("tr-2lig", "byc", "Beyoğlu Yeni Çarşı", "BYÇ", 0xFFE30613, 0xFF0057B8, QUARTERS)
    )
    private val ucuncuLig = listOf(
        t("tr-3lig", "ksk", "Karşıyaka", "KSK", 0xFF00843D, 0xFFE30613, HALVES),
        t("tr-3lig", "esk", "Eskişehirspor", "ESK", 0xFFE30613, 0xFF111111, STRIPES),
        t("tr-3lig", "kms", "Kahramanmaraşspor", "KMS", 0xFFE30613, 0xFF111111, HALVES),
        t("tr-3lig", "afy", "Afyonspor", "AFY", 0xFF5B2C83, 0xFFFFFFFF, SASH),
        t("tr-3lig", "ord", "Orduspor", "ORD", 0xFF5B2C83, 0xFFFFFFFF, STRIPES),
        t("tr-3lig", "edi", "Edirnespor", "EDİ", 0xFFE30613, 0xFFFFD200, HALVES),
        t("tr-3lig", "usk", "Uşakspor", "UŞK", 0xFF111111, 0xFFFFFFFF, HOOPS),
        t("tr-3lig", "blk", "Balıkesirspor", "BLK", 0xFFE30613, 0xFFFFFFFF, CHEVRON),
        t("tr-3lig", "gir", "Giresunspor", "GİR", 0xFF00843D, 0xFFFFFFFF, HOOPS),
        t("tr-3lig", "kst", "Kastamonuspor", "KST", 0xFFE30613, 0xFFFFFFFF, SOLID)
    )
    private val premierLeague = listOf(
        t("eng-pl", "ars", "Arsenal", "ARS", 0xFFEF0107, 0xFFFFFFFF, SOLID),
        t("eng-pl", "liv", "Liverpool", "LIV", 0xFFC8102E, 0xFFF6EB61, SOLID),
        t("eng-pl", "mci", "Manchester City", "MCI", 0xFF6CABDD, 0xFFFFFFFF, ROUND),
        t("eng-pl", "che", "Chelsea", "CHE", 0xFF034694, 0xFFFFFFFF, ROUND),
        t("eng-pl", "tot", "Tottenham", "TOT", 0xFFFFFFFF, 0xFF132257, SOLID),
        t("eng-pl", "mun", "Manchester United", "MUN", 0xFFDA291C, 0xFF111111, SOLID),
        t("eng-pl", "new", "Newcastle United", "NEW", 0xFF111111, 0xFFFFFFFF, STRIPES),
        t("eng-pl", "avl", "Aston Villa", "AVL", 0xFF670E36, 0xFF95BFE5, HALVES),
        t("eng-pl", "bha", "Brighton", "BHA", 0xFF0057B8, 0xFFFFFFFF, STRIPES),
        t("eng-pl", "cov", "Coventry", "COV", 0xFF7DB7E6, 0xFFFFFFFF, SOLID),
        t("eng-pl", "eve", "Everton", "EVE", 0xFF003399, 0xFFFFFFFF, SOLID),
        t("eng-pl", "whu", "West Ham", "WHU", 0xFF7A263A, 0xFF1BB1E7, HALVES)
    )
    private val championship = listOf(
        t("eng-ch", "shw", "Sheffield Wednesday", "SHW", 0xFF0057B8, 0xFFFFFFFF, STRIPES),
        t("eng-ch", "nor", "Norwich City", "NOR", 0xFFFFF200, 0xFF00A650, HALVES),
        t("eng-ch", "mbr", "Middlesbrough", "MID", 0xFFE30613, 0xFFFFFFFF, SOLID),
        t("eng-ch", "hul", "Hull City", "HUL", 0xFFF5A12D, 0xFF111111, STRIPES),
        t("eng-ch", "stk", "Stoke City", "STK", 0xFFE03A3E, 0xFFFFFFFF, STRIPES),
        t("eng-ch", "wat", "Watford", "WAT", 0xFFFBEE23, 0xFFED2127, HALVES),
        t("eng-ch", "der", "Derby County", "DER", 0xFFFFFFFF, 0xFF111111, SOLID),
        t("eng-ch", "mil", "Millwall", "MIL", 0xFF001D5E, 0xFFFFFFFF, SOLID)
    )
    private val laLiga = listOf(
        t("esp-laliga", "rma", "Real Madrid", "RMA", 0xFFFFFFFF, 0xFFFEBE10, SOLID),
        t("esp-laliga", "bar", "Barcelona", "BAR", 0xFFA50044, 0xFF004D98, STRIPES),
        t("esp-laliga", "atm", "Atlético Madrid", "ATM", 0xFFCB3524, 0xFFFFFFFF, STRIPES),
        t("esp-laliga", "sev", "Sevilla", "SEV", 0xFFFFFFFF, 0xFFD71920, SOLID),
        t("esp-laliga", "bet", "Real Betis", "BET", 0xFF00954C, 0xFFFFFFFF, STRIPES),
        t("esp-laliga", "vil", "Villarreal", "VIL", 0xFFFFE667, 0xFF005187, SOLID),
        t("esp-laliga", "rso", "Real Sociedad", "RSO", 0xFF0067B1, 0xFFFFFFFF, STRIPES),
        t("esp-laliga", "ath", "Athletic Club", "ATH", 0xFFEE2523, 0xFFFFFFFF, STRIPES),
        t("esp-laliga", "val", "Valencia", "VAL", 0xFFFFFFFF, 0xFF111111, SOLID),
        t("esp-laliga", "lev", "Levante", "LEV", 0xFFB4053F, 0xFF004F9E, HALVES)
    )
    private val serieA = listOf(
        t("ita-seriea", "int", "Inter", "INT", 0xFF0068A8, 0xFF111111, STRIPES),
        t("ita-seriea", "acm", "Milan", "ACM", 0xFFFB090B, 0xFF111111, STRIPES),
        t("ita-seriea", "juv", "Juventus", "JUV", 0xFF111111, 0xFFFFFFFF, STRIPES),
        t("ita-seriea", "nap", "Napoli", "NAP", 0xFF12A0D7, 0xFFFFFFFF, ROUND),
        t("ita-seriea", "rom", "Roma", "ROM", 0xFF8E1F2F, 0xFFF0BC42, HALVES),
        t("ita-seriea", "laz", "Lazio", "LAZ", 0xFF87D8F7, 0xFFFFFFFF, SOLID),
        t("ita-seriea", "ata", "Atalanta", "ATA", 0xFF1E71B8, 0xFF111111, ROUND),
        t("ita-seriea", "fio", "Fiorentina", "FIO", 0xFF482E92, 0xFFFFFFFF, SOLID),
        t("ita-seriea", "tor", "Torino", "TOR", 0xFF881F19, 0xFFFFFFFF, SOLID),
        t("ita-seriea", "ven", "Venezia", "VEN", 0xFFF7931E, 0xFF111111, QUARTERS),
        t("ita-seriea", "udi", "Udinese", "UDI", 0xFF111111, 0xFFFFFFFF, STRIPES),
        t("ita-seriea", "bfc", "Bologna", "BFC", 0xFF1A2F48, 0xFFA21C26, HALVES)
    )
    private val bundesliga = listOf(
        t("ger-bl", "fcb", "Bayern München", "FCB", 0xFFDC052D, 0xFFFFFFFF, ROUND),
        t("ger-bl", "bvb", "Borussia Dortmund", "BVB", 0xFFFDE100, 0xFF111111, ROUND),
        t("ger-bl", "b04", "Bayer Leverkusen", "B04", 0xFFE32221, 0xFF111111, HALVES),
        t("ger-bl", "rbl", "RB Leipzig", "RBL", 0xFFFFFFFF, 0xFFDD0741, SOLID),
        t("ger-bl", "sge", "Eintracht Frankfurt", "SGE", 0xFFE1000F, 0xFF111111, SOLID),
        t("ger-bl", "vfb", "VfB Stuttgart", "VFB", 0xFFFFFFFF, 0xFFE32219, HOOPS),
        t("ger-bl", "svw", "Werder Bremen", "SVW", 0xFF1D9053, 0xFFFFFFFF, SOLID),
        t("ger-bl", "scf", "SC Freiburg", "SCF", 0xFF111111, 0xFFE30613, SOLID)
    )
    private val ligue1 = listOf(
        t("fra-l1", "psg", "Paris Saint-Germain", "PSG", 0xFF004170, 0xFFDA291C, CHEVRON),
        t("fra-l1", "om", "Marseille", "OM", 0xFFFFFFFF, 0xFF2FAEE0, SOLID),
        t("fra-l1", "asm", "Monaco", "ASM", 0xFFE51B22, 0xFFFFFFFF, SASH),
        t("fra-l1", "ol", "Lyon", "OL", 0xFFFFFFFF, 0xFF1D3B8B, SOLID),
        t("fra-l1", "losc", "Lille", "LOSC", 0xFFE01E13, 0xFF20325F, SOLID),
        t("fra-l1", "ogcn", "Nice", "NICE", 0xFFE30613, 0xFF111111, STRIPES),
        t("fra-l1", "rcl", "Lens", "RCL", 0xFFFFD100, 0xFFE30613, HALVES),
        t("fra-l1", "srfc", "Rennes", "SRFC", 0xFFE30613, 0xFF111111, HALVES)
    )
    private val europeOthers = listOf(
        t("eur", "slb", "Benfica", "SLB", 0xFFE30613, 0xFFFFFFFF, SOLID),
        t("eur", "fcp", "Porto", "FCP", 0xFF0057B8, 0xFFFFFFFF, STRIPES),
        t("eur", "aja", "Ajax", "AJA", 0xFFFFFFFF, 0xFFD2122E, QUARTERS),
        t("eur", "psv", "PSV", "PSV", 0xFFE30613, 0xFFFFFFFF, STRIPES),
        t("eur", "cel", "Celtic", "CEL", 0xFF018749, 0xFFFFFFFF, HOOPS),
        t("eur", "clu", "Club Brugge", "CLU", 0xFF0057B8, 0xFF111111, STRIPES),
        t("eur", "scp", "Sporting CP", "SCP", 0xFF008057, 0xFFFFFFFF, HOOPS),
        t("eur", "sha", "Shakhtar", "SHA", 0xFFF26F21, 0xFF111111, STRIPES),
        t("eur", "rap", "Rapid Wien", "RAP", 0xFF00843D, 0xFFFFFFFF, HOOPS),
        t("eur", "rcs", "Strasbourg", "RCS", 0xFF009FE3, 0xFFFFFFFF, SOLID)
    )

    // ---- Basketbol ----
    private val bsl = listOf(
        t("bsl", "efes", "Anadolu Efes", "EFS", 0xFF003DA5, 0xFFE30613, HALVES),
        t("bsl", "fbb", "Fenerbahçe Beko", "FB", 0xFFFFED00, 0xFF00205B, STRIPES),
        t("bsl", "gsb", "Galatasaray", "GS", 0xFFFDB913, 0xFFA90432, HALVES),
        t("bsl", "bjkb", "Beşiktaş", "BJK", 0xFF111111, 0xFFFFFFFF, STRIPES),
        t("bsl", "ttk", "Türk Telekom", "TT", 0xFF00A0DF, 0xFFFFFFFF, SOLID),
        t("bsl", "kskb", "Karşıyaka", "KSK", 0xFF00843D, 0xFFE30613, HALVES),
        t("bsl", "bah", "Bahçeşehir Koleji", "BAH", 0xFF5B2C83, 0xFFFFFFFF, SASH),
        t("bsl", "tof", "Tofaş", "TOF", 0xFF0057B8, 0xFFFFFFFF, HOOPS),
        t("bsl", "dsk", "Darüşşafaka", "DSK", 0xFFFFD200, 0xFF111111, HALVES),
        t("bsl", "pet", "Petkim Spor", "PET", 0xFF00843D, 0xFFFFFFFF, SOLID)
    )
    private val euroleague = listOf(
        t("el-b", "rmab", "Real Madrid", "RMA", 0xFFFFFFFF, 0xFF5A3E8E, SOLID),
        t("el-b", "barb", "Barcelona", "BAR", 0xFFA50044, 0xFF004D98, STRIPES),
        t("el-b", "oly", "Olympiacos", "OLY", 0xFFE30613, 0xFFFFFFFF, STRIPES),
        t("el-b", "pao", "Panathinaikos", "PAO", 0xFF00843D, 0xFFFFFFFF, HOOPS),
        t("el-b", "asmb", "Monaco", "ASM", 0xFFE51B22, 0xFFFFFFFF, SASH),
        t("el-b", "par", "Partizan", "PAR", 0xFF111111, 0xFFFFFFFF, STRIPES),
        t("el-b", "zal", "Žalgiris", "ZAL", 0xFF00843D, 0xFFFFFFFF, SOLID),
        t("el-b", "vir", "Virtus Bologna", "VIR", 0xFF111111, 0xFFFFFFFF, CHEVRON)
    ) + bsl.filter { it.id == "efes" || it.id == "fbb" }
    private val nba = listOf(
        t("nba", "bos", "Boston Celtics", "BOS", 0xFF007A33, 0xFFFFFFFF, SOLID),
        t("nba", "lal", "LA Lakers", "LAL", 0xFF552583, 0xFFFDB927, HALVES),
        t("nba", "gsw", "Golden State Warriors", "GSW", 0xFF1D428A, 0xFFFFC72C, ROUND),
        t("nba", "nyk", "New York Knicks", "NYK", 0xFF006BB6, 0xFFF58426, HALVES),
        t("nba", "okc", "Oklahoma City Thunder", "OKC", 0xFF007AC1, 0xFFEF3B24, SASH),
        t("nba", "den", "Denver Nuggets", "DEN", 0xFF0E2240, 0xFFFEC524, HOOPS),
        t("nba", "mil", "Milwaukee Bucks", "MIL", 0xFF00471B, 0xFFEEE1C6, SOLID),
        t("nba", "phi", "Philadelphia 76ers", "PHI", 0xFF006BB6, 0xFFED174C, HALVES)
    )

    // ---- Voleybol ----
    private val sultanlar = listOf(
        t("sultanlar", "vkf", "VakıfBank", "VKF", 0xFFFFD200, 0xFF111111, HALVES),
        t("sultanlar", "ecz", "Eczacıbaşı", "ECZ", 0xFFE30613, 0xFFFFFFFF, SOLID),
        t("sultanlar", "fbv", "Fenerbahçe Medicana", "FB", 0xFFFFED00, 0xFF00205B, STRIPES),
        t("sultanlar", "gsv", "Galatasaray Daikin", "GS", 0xFFFDB913, 0xFFA90432, HALVES),
        t("sultanlar", "thy", "Türk Hava Yolları", "THY", 0xFFE30613, 0xFFFFFFFF, CHEVRON),
        t("sultanlar", "nil", "Nilüfer Belediyespor", "NİL", 0xFF0057B8, 0xFFFFFFFF, HOOPS),
        t("sultanlar", "ara", "Aras Kargo", "ARA", 0xFF00843D, 0xFFFFFFFF, SASH),
        t("sultanlar", "kuz", "Kuzeyboru", "KUZ", 0xFF1B3F8B, 0xFFFFFFFF, SOLID)
    )
    private val efeler = listOf(
        t("efeler", "hlk", "Halkbank", "HLK", 0xFF0057B8, 0xFFFFFFFF, SOLID),
        t("efeler", "zir", "Ziraat Bankkart", "ZİR", 0xFFE30613, 0xFFFFFFFF, SOLID),
        t("efeler", "fbe", "Fenerbahçe", "FB", 0xFFFFED00, 0xFF00205B, STRIPES),
        t("efeler", "gse", "Galatasaray", "GS", 0xFFFDB913, 0xFFA90432, HALVES),
        t("efeler", "ark", "Arkas Spor", "ARK", 0xFF0057B8, 0xFFFFD200, HALVES),
        t("efeler", "tot2", "Spor Toto", "TOT", 0xFF00843D, 0xFFFFFFFF, HOOPS),
        t("efeler", "ciz", "Cizre Belediyespor", "CİZ", 0xFFE30613, 0xFF00843D, HALVES),
        t("efeler", "akk", "Akkuş Belediyespor", "AKK", 0xFF5B2C83, 0xFFFFFFFF, SASH)
    )

    // ---- Tenis (oyuncular uydurmadır) ----
    private val atp = listOf(
        t("atp", "p1", "A. Moreau", "MOR", 0xFF0055A4, 0xFFFFFFFF, HALVES),
        t("atp", "p2", "L. Fischer", "FIS", 0xFF111111, 0xFFDD0000, HOOPS),
        t("atp", "p3", "D. Yılmaz", "YIL", 0xFFE30A17, 0xFFFFFFFF, SOLID),
        t("atp", "p4", "M. Rossi", "ROS", 0xFF009246, 0xFFCE2B37, HALVES),
        t("atp", "p5", "J. Walker", "WAL", 0xFF012169, 0xFFC8102E, QUARTERS),
        t("atp", "p6", "C. Navarro", "NAV", 0xFFAA151B, 0xFFF1BF00, HOOPS)
    )
    private val wta = listOf(
        t("wta", "w1", "E. Kaya", "KAY", 0xFFE30A17, 0xFFFFFFFF, SOLID),
        t("wta", "w2", "S. Laurent", "LAU", 0xFF0055A4, 0xFFFFFFFF, HALVES),
        t("wta", "w3", "N. Becker", "BEC", 0xFF111111, 0xFFFFCE00, HOOPS),
        t("wta", "w4", "I. García", "GAR", 0xFFAA151B, 0xFFF1BF00, HOOPS)
    )

    private val allFootballTeams = superLig + birinciLig + ikinciLig + ucuncuLig + premierLeague + championship +
        laLiga + serieA + bundesliga + ligue1 + europeOthers

    private fun pick(vararg ids: String): List<Team> = ids.map { id -> allFootballTeams.first { it.id == id } }

    private fun league(id: String, sport: Sport, name: String, country: Country, major: Boolean, priority: Int) =
        League(id, sport, name, country, major, priority)

    val schedules: List<Schedule> = listOf(
        // Futbol
        Schedule(
            league("tr-super", Sport.FOOTBALL, "Süper Lig", TR, true, 1), superLig,
            mapOf(FRIDAY to 1, SATURDAY to 3, SUNDAY to 3, MONDAY to 2), times("19:00", "20:00", "17:00"),
            listOf("beIN SPORTS 1", "beIN SPORTS 2", "beIN SPORTS 3"), 8
        ),
        Schedule(
            league("uefa-cl", Sport.FOOTBALL, "Şampiyonlar Ligi", EUR, true, 2),
            pick("gs", "rma", "bar", "int", "fcb", "psg", "ars", "liv", "mci", "bvb", "nap", "slb", "atm", "juv", "psv", "fb"),
            mapOf(TUESDAY to 4, WEDNESDAY to 4), times("19:45", "22:00", "22:00", "22:00"),
            listOf("TRT 1", "Tabii Spor"), 3
        ),
        Schedule(
            league("uefa-el", Sport.FOOTBALL, "Avrupa Ligi", EUR, true, 3),
            pick("bjk", "laz", "rom", "fcp", "aja", "cel", "ol", "bet", "avl", "sge", "scp", "ts"),
            mapOf(THURSDAY to 4), times("19:45", "22:00"), listOf("Exxen Spor 1", "Exxen Spor 2"), 3
        ),
        Schedule(
            league("uefa-ecl", Sport.FOOTBALL, "Konferans Ligi", EUR, false, 4),
            pick("ibfk", "fio", "sha", "clu", "rap", "rcs", "che", "sam"),
            mapOf(THURSDAY to 3), times("19:45", "22:00"), listOf("Exxen Spor 3"), 3
        ),
        Schedule(
            league("tr-1lig", Sport.FOOTBALL, "1. Lig", TR, false, 5), birinciLig,
            mapOf(SATURDAY to 2, SUNDAY to 3, MONDAY to 2), times("13:30", "16:00", "19:00"),
            listOf("TRT Spor", "TRT Spor Yıldız"), 9
        ),
        Schedule(
            league("tr-2lig", Sport.FOOTBALL, "2. Lig", TR, false, 6), ikinciLig,
            mapOf(SATURDAY to 3, SUNDAY to 3, MONDAY to 1), times("13:00", "15:00", "16:00"), emptyList(), 7
        ),
        Schedule(
            league("tr-3lig", Sport.FOOTBALL, "3. Lig", TR, false, 7), ucuncuLig,
            mapOf(SATURDAY to 2, SUNDAY to 3, MONDAY to 2), times("13:00", "15:00"), emptyList(), 7
        ),
        Schedule(
            league("eng-pl", Sport.FOOTBALL, "Premier League", ENG, true, 10), premierLeague,
            mapOf(SATURDAY to 4, SUNDAY to 3, MONDAY to 1), times("22:00", "17:00", "19:30", "14:30"),
            listOf("beIN SPORTS 3", "beIN SPORTS 4"), 8
        ),
        Schedule(
            league("esp-laliga", Sport.FOOTBALL, "LaLiga", ESP, true, 11), laLiga,
            mapOf(FRIDAY to 1, SATURDAY to 3, SUNDAY to 3, MONDAY to 1), times("22:00", "17:15", "19:30"),
            listOf("S Sport", "S Sport 2"), 9
        ),
        Schedule(
            league("ita-seriea", Sport.FOOTBALL, "Serie A", ITA, true, 12), serieA,
            mapOf(SATURDAY to 2, SUNDAY to 3, MONDAY to 2), times("19:30", "21:45", "16:00"),
            listOf("S Sport 2", "S Sport"), 7
        ),
        Schedule(
            league("ger-bl", Sport.FOOTBALL, "Bundesliga", GER, true, 13), bundesliga,
            mapOf(FRIDAY to 1, SATURDAY to 3, SUNDAY to 2), times("16:30", "19:30", "21:30"),
            listOf("Tivibu Spor 2", "Tivibu Spor 3"), 7
        ),
        Schedule(
            league("fra-l1", Sport.FOOTBALL, "Ligue 1", FRA, true, 14), ligue1,
            mapOf(FRIDAY to 1, SATURDAY to 2, SUNDAY to 3), times("21:45", "18:00", "16:00"),
            listOf("beIN SPORTS 4", "beIN SPORTS 5"), 8
        ),
        Schedule(
            league("eng-ch", Sport.FOOTBALL, "Championship", ENG, false, 15), championship,
            mapOf(SATURDAY to 3, TUESDAY to 2, WEDNESDAY to 2), times("17:00", "21:45"), listOf("beIN SPORTS 4"), 11
        ),
        // Basketbol
        Schedule(
            league("bsl", Sport.BASKETBALL, "Basketbol Süper Ligi", TR, true, 1), bsl,
            mapOf(SATURDAY to 2, SUNDAY to 3, MONDAY to 1), times("19:00", "15:00", "17:00"),
            listOf("beIN SPORTS 5", "A Spor"), 5
        ),
        Schedule(
            league("euroleague", Sport.BASKETBALL, "EuroLeague", EUR, true, 2), euroleague,
            mapOf(TUESDAY to 2, THURSDAY to 3, FRIDAY to 2), times("20:45", "21:30", "20:00"),
            listOf("S Sport", "S Sport 2"), 4
        ),
        Schedule(
            league("nba", Sport.BASKETBALL, "NBA", USA, true, 3), nba,
            everyDay.associateWith { 3 }, times("02:30", "04:00", "05:30"), listOf("S Sport 2"), 3
        ),
        // Voleybol
        Schedule(
            league("sultanlar", Sport.VOLLEYBALL, "Sultanlar Ligi", TR, true, 1), sultanlar,
            mapOf(FRIDAY to 1, SATURDAY to 2, SUNDAY to 2, MONDAY to 1), times("18:00", "14:00", "16:00"),
            listOf("TRT Spor Yıldız", "TRT Spor"), 4
        ),
        Schedule(
            league("efeler", Sport.VOLLEYBALL, "Efeler Ligi", TR, true, 2), efeler,
            mapOf(SATURDAY to 2, SUNDAY to 2), times("13:00", "15:30"), listOf("TRT Spor Yıldız"), 4
        ),
        // Tenis
        Schedule(
            league("atp", Sport.TENNIS, "ATP 500", WORLD, true, 1), atp,
            everyDay.associateWith { 3 }, times("12:00", "14:30", "17:00"), listOf("S Sport 2"), 1
        ),
        Schedule(
            league("wta", Sport.TENNIS, "WTA 500", WORLD, true, 2), wta,
            everyDay.associateWith { 2 }, times("13:00", "16:00"), listOf("S Sport 2"), 1
        )
    )

    val allTeams: List<Team> = schedules.flatMap { it.teams }.distinctBy { it.id }

    fun schedule(leagueId: String): Schedule? = schedules.firstOrNull { it.league.id == leagueId }

    private val surnames = mapOf(
        "tr" to listOf("Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Aydın", "Öztürk", "Arslan", "Doğan", "Kılıç", "Aslan", "Polat", "Erdem", "Koç"),
        "eng" to listOf("Smith", "Walker", "Taylor", "Davies", "Evans", "Wilson", "Hughes", "Clarke", "Turner", "Hall"),
        "esp" to listOf("García", "Martínez", "López", "Sánchez", "Romero", "Navarro", "Torres", "Ruiz", "Ortega"),
        "ita" to listOf("Rossi", "Bianchi", "Romano", "Colombo", "Ricci", "Greco", "Bruno", "Conti", "Marino"),
        "ger" to listOf("Müller", "Schmidt", "Fischer", "Weber", "Becker", "Wagner", "Hoffmann", "Koch"),
        "fra" to listOf("Martin", "Bernard", "Dubois", "Moreau", "Laurent", "Lefèvre", "Girard", "Roux"),
        "eur" to listOf("Silva", "Jansen", "Novak", "Costa", "Peeters", "Horvat", "Pereira", "Bakker")
    )
    private val initials = "ABCDEFGHKLMNORSTY"

    /** Uydurma oyuncu adı: takımın ülkesine uygun soyad + baş harf. */
    fun playerName(team: Team, slot: Int): String {
        val country = when (val prefix = team.leagueId.substringBefore('-')) {
            "bsl", "sultanlar", "efeler" -> "tr"
            else -> prefix
        }
        val pool = surnames[country] ?: surnames.getValue("eur")
        val h = Math.floorMod(team.id.hashCode() * 31 + slot * 7919, Int.MAX_VALUE)
        return "${initials[h % initials.length]}. ${pool[(h / 7) % pool.size]}"
    }
}
