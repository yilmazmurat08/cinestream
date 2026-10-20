package com.cinestream.sports

import com.cinestream.sports.data.demo.DemoChannels
import com.cinestream.sports.data.demo.DemoEpgLookup
import com.cinestream.sports.data.demo.DemoSportsDataSource
import com.cinestream.sports.model.Match
import com.cinestream.sports.notify.ReminderScheduler
import com.cinestream.sports.settings.InMemorySportsSettingsStore
import com.cinestream.sports.settings.SportsSettingsStore
import com.cinestream.sports.ui.SportsDependencies
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Testlerde sabit an: 12 Ekim 2026 Pazartesi, 20:40 (Türkiye). */
internal object TestEnv {
    val zone: ZoneId = ZoneId.of("Europe/Istanbul")
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-12T17:40:00Z"), zone)
    val today: LocalDate = LocalDate.of(2026, 10, 12)

    fun deps(
        settings: SportsSettingsStore = InMemorySportsSettingsStore(),
        reminders: ReminderScheduler = ReminderScheduler.None,
        io: CoroutineDispatcher = Dispatchers.Unconfined
    ): SportsDependencies {
        val source = DemoSportsDataSource(clock)
        return SportsDependencies(
            dataSource = source,
            settings = settings,
            channels = DemoChannels,
            epg = DemoEpgLookup(source, zone),
            reminders = reminders,
            clock = clock,
            zone = zone,
            io = io
        )
    }
}

internal class FakeReminders : ReminderScheduler {
    val scheduled = mutableListOf<Pair<String, Int>>()
    val cancelled = mutableListOf<String>()
    override fun schedule(match: Match, minutesBefore: Int) {
        scheduled += match.id to minutesBefore
    }

    override fun cancel(matchId: String) {
        cancelled += matchId
    }
}
