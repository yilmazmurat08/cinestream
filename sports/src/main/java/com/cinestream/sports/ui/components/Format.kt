package com.cinestream.sports.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.cinestream.sports.R
import com.cinestream.sports.model.Match
import com.cinestream.sports.model.MatchStatus
import com.cinestream.sports.model.Sport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal fun sportIcon(sport: Sport): ImageVector = when (sport) {
    Sport.FOOTBALL -> Icons.Filled.SportsSoccer
    Sport.BASKETBALL -> Icons.Filled.SportsBasketball
    Sport.VOLLEYBALL -> Icons.Filled.SportsVolleyball
    Sport.TENNIS -> Icons.Filled.SportsTennis
}

@Composable
internal fun sportName(sport: Sport): String = stringResource(
    when (sport) {
        Sport.FOOTBALL -> R.string.sports_sport_football
        Sport.BASKETBALL -> R.string.sports_sport_basketball
        Sport.VOLLEYBALL -> R.string.sports_sport_volleyball
        Sport.TENNIS -> R.string.sports_sport_tennis
    }
)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatTime(epochMillis: Long, zone: ZoneId): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

internal fun localDate(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

private fun String.capitalized(locale: Locale) = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** Gün seçicinin üst satırı: Dün / Bugün / Yarın ya da gün adı. */
@Composable
internal fun dayTitle(day: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, day)) {
    -1L -> stringResource(R.string.sports_yesterday)
    0L -> stringResource(R.string.sports_today)
    1L -> stringResource(R.string.sports_tomorrow)
    else -> currentLocale().let { DateTimeFormatter.ofPattern("EEEE", it).format(day).capitalized(it) }
}

/** Gün seçicinin alt satırı: "Pzt 12". */
@Composable
internal fun daySubtitle(day: LocalDate): String =
    currentLocale().let { DateTimeFormatter.ofPattern("EEE d", it).format(day).capitalized(it) }

/** "12 Ekim Pazartesi". */
@Composable
internal fun longDate(epochMillis: Long, zone: ZoneId): String =
    currentLocale().let { DateTimeFormatter.ofPattern("d MMMM EEEE", it).format(Instant.ofEpochMilli(epochMillis).atZone(zone)) }

/** "Pzt 12 Eki · 20:00". */
@Composable
internal fun shortDateTime(epochMillis: Long, zone: ZoneId): String = currentLocale().let {
    DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", it).format(Instant.ofEpochMilli(epochMillis).atZone(zone)).capitalized(it)
}

/** Canlı / bitti durum etiketi; başlamamış maçta null (saat gösterilir). */
@Composable
internal fun statusText(match: Match, short: Boolean = false): String? = when (match.status) {
    MatchStatus.LIVE -> when (match.sport) {
        Sport.FOOTBALL -> stringResource(R.string.sports_minute, match.minute ?: 0)
        Sport.BASKETBALL -> stringResource(if (short) R.string.sports_quarter_short else R.string.sports_quarter, match.period ?: 1)
        Sport.VOLLEYBALL, Sport.TENNIS -> stringResource(R.string.sports_set, match.period ?: 1)
    }
    MatchStatus.HALF_TIME -> stringResource(R.string.sports_half_time)
    MatchStatus.FINISHED -> stringResource(R.string.sports_full_time)
    MatchStatus.POSTPONED -> stringResource(R.string.sports_postponed)
    MatchStatus.SCHEDULED -> null
}

private val channelPrefix = Regex("^\\s*(\\[[^\\]]*\\]|[A-Za-z]{2,4}\\s*[|:\\-]+|\\|+)\\s*")

/** Kanal adını kartta okunur yapar: "TR | BEIN SPORTS 1 HD" → "BEIN SPORTS 1 HD". */
internal fun cleanChannelName(name: String): String {
    var s = name
    repeat(3) { s = s.replace(channelPrefix, "") }
    return s.trim().ifEmpty { name }
}
