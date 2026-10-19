package com.example

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.epg.EpgSchedule
import com.example.data.model.EPGProgram
import com.example.data.model.IPTVGroup
import com.example.data.model.IPTVItem
import com.example.data.repository.IPTVRepository
import com.example.reminder.ProgramReminder
import com.example.reminder.ProgramReminderReceiver
import com.example.reminder.ProgramReminders
import com.example.ui.IPTVViewModel
import com.example.ui.screens.FolderDetailLayout
import com.example.ui.screens.PlayerScreen
import com.example.ui.tv.TvDevice
import com.example.ui.tv.TvPlayerUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Kanal kartındaki yayın akışı ve program hatırlatmaları (kullanıcı kararı, 19 Ekim 2026; onaylı taslak):
 * - EPG düğmesi yalnızca yayın akışı olan kanalda; dokununca kartın altında 12 saatlik akış açılır.
 * - Programa dokununca "Hatırlat (5 dk önce)"; kurulunca zil simgesi. Ücretsiz.
 * - Bildirim program başlamadan 5 dk önce gelir; dokununca kanal açılır. Yeniden başlatmada alarmlar yeniden kurulur.
 * - TV: kanal panelinde Sağ tuşu akışı yanda açar, Sol kapatır; OK hatırlatma kurar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "tr")
class EpgRemindersTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
    private val minute = 60_000L
    private val now = (System.currentTimeMillis() / minute) * minute

    @Before
    fun clear() {
        app.getSharedPreferences(ProgramReminders.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        TvDevice.overrideForTest = null
        db.close()
    }

    private fun channel(id: Int, name: String, tvgId: String?) = IPTVItem(
        id = id, playlistId = 1, name = name, cleanedName = name, logoUrl = null,
        streamUrl = "http://10.255.255.1/live/$id.ts", category = "EPG Ulusal", type = "LIVE", tvgId = tvgId
    )

    private fun program(title: String, startOffsetMin: Long, durationMin: Long): EPGProgram {
        val start = now + startOffsetMin * minute
        val end = start + durationMin * minute
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return EPGProgram(
            id = "$title-$start", channelId = 0, channelName = "", title = title, description = "", category = "",
            startTimeFormatted = fmt.format(java.util.Date(start)), endTimeFormatted = fmt.format(java.util.Date(end)),
            startEpochMillis = start, endEpochMillis = end
        )
    }

    private val schedule = listOf(
        program("Bitmiş Program", -120, 60),
        program("Öğle Kuşağı", -30, 60),
        program("Ana Haber", 30, 60),
        program("Akşam Dizisi", 90, 120),
        program("Çok İleride", 13 * 60, 60)
    )

    private fun press(key: Key) {
        @OptIn(ExperimentalTestApi::class)
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun schedule_keepsLiveAndNext12Hours_inOrder_withLiveProgress() {
        val window = EpgSchedule.window(schedule.shuffled(), now, 12)
        assertEquals(listOf("Öğle Kuşağı", "Ana Haber", "Akşam Dizisi"), window.map { it.title })
        assertTrue(window[0].isLiveNow)
        assertEquals(0.5f, window[0].progressPercent, 0.01f)
        assertFalse(window[1].isLiveNow)
        assertTrue(EpgSchedule.window(emptyList(), now, 12).isEmpty())
    }

    @Test
    fun reminder_isStored_alarmFiveMinutesBefore_removeCancels_pastRejected() {
        val start = now + 60 * minute
        val r = ProgramReminder(9001, "HD: TRT 1", "Ana Haber", start)
        assertTrue(ProgramReminders.add(app, r, now))
        assertTrue(ProgramReminders.isSet(app, 9001, start))
        val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        assertEquals(start - ProgramReminders.LEAD_TIME_MS, alarms.peekNextScheduledAlarm()?.triggerAtTime)

        ProgramReminders.remove(app, 9001, start)
        assertFalse(ProgramReminders.isSet(app, 9001, start))
        assertNull(alarms.peekNextScheduledAlarm())

        // Başlamış program kurulmaz; başlamış kayıtlar kendiliğinden silinir.
        assertFalse(ProgramReminders.add(app, r.copy(startMillis = now - minute), now))
        ProgramReminders.add(app, r.copy(startMillis = now + 10 * minute), now)
        assertTrue(ProgramReminders.all(app, now + 11 * minute).isEmpty())
    }

    @Test
    fun receiver_postsNotification_thatOpensTheChannel_thenForgetsReminder_bootReschedules() {
        val start = System.currentTimeMillis() + 4 * minute // bildirim zamanı geldi (5 dk'dan az kaldı)
        ProgramReminders.add(app, ProgramReminder(9002, "HD: TV 8", "Yarışma", start))
        val receiver = ProgramReminderReceiver()
        receiver.onReceive(app, Intent(ProgramReminderReceiver.ACTION_FIRE).putExtra(ProgramReminderReceiver.EXTRA_KEY, ProgramReminder.key(9002, start)))

        val nm = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        assertEquals(1, nm.allNotifications.size)
        val n = nm.allNotifications.first()
        assertTrue(n.extras.getString("android.title")!!.contains("Yarışma"))
        val open = shadowOf(n.contentIntent).savedIntent
        assertEquals(MainActivity.ACTION_OPEN_CHANNEL, open.action)
        assertEquals(9002, open.getIntExtra(MainActivity.EXTRA_OPEN_CHANNEL_ID, -1))
        assertFalse(ProgramReminders.isSet(app, 9002, start))

        // Erken (başka uygulamanın gönderdiği) yayın bildirim göstermez.
        val later = System.currentTimeMillis() + 120 * minute
        ProgramReminders.add(app, ProgramReminder(9003, "HD: TRT 2", "Belgesel", later))
        receiver.onReceive(app, Intent(ProgramReminderReceiver.ACTION_FIRE).putExtra(ProgramReminderReceiver.EXTRA_KEY, ProgramReminder.key(9003, later)))
        assertEquals(1, nm.allNotifications.size)

        // Telefon yeniden başlayınca alarm yeniden kurulur.
        val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        alarms.scheduledAlarms.toList().forEach { alarms.scheduledAlarms.remove(it) }
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(later - ProgramReminders.LEAD_TIME_MS, alarms.peekNextScheduledAlarm()?.triggerAtTime)
    }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp")
    fun phoneCard_epgOnlyWithSchedule_opensBelow_remindSetsBell() {
        val withEpg = channel(9101, "HD: TRT 1", "trt1.tr")
        val noEpg = channel(9102, "HD: ULUSAL INFO", null)
        runBlocking { db.iptvDao().insertItems(listOf(withEpg, noEpg)) }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        vm.setEpgProgramsForTest(mapOf("trt1.tr" to schedule))
        val group = IPTVGroup(id = "EPG Ulusal", name = "EPG Ulusal", type = "LIVE", items = listOf(withEpg, noEpg))
        var played: IPTVItem? = null
        compose.setContent { FolderDetailLayout(group = group, viewModel = vm, onPlayItem = { played = it }, onBackClick = {}) }
        compose.waitForIdle()

        // Uydurma izleyici sayısı yok; EPG yalnızca yayın akışı olan kanalda.
        compose.onNodeWithTag("live_epg_button_9101").assertExists()
        assertEquals(0, compose.onAllNodes(hasTestTag("live_epg_button_9102")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("live_play_button_9102").performClick()
        assertEquals(9102, played?.id)

        compose.onNodeWithTag("live_epg_button_9101").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("epg_dropdown_9101").assertExists()
        val anaHaber = schedule[2].startEpochMillis
        compose.onNodeWithTag("epg_program_9101_$anaHaber").performScrollTo().performClick()
        compose.onNodeWithTag("epg_remind_9101_$anaHaber").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("epg_bell_9101_$anaHaber"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(ProgramReminders.isSet(app, 9101, anaHaber))

        // Yayındaki program: "Şimdi izle" kanalı açar.
        played = null
        val live = schedule[1].startEpochMillis
        compose.onNodeWithTag("epg_program_9101_$live").performScrollTo().performClick()
        compose.onNodeWithTag("epg_watch_9101").performScrollTo().performClick()
        assertEquals(9101, played?.id)

        // Tekrar EPG: kapanır.
        compose.onNodeWithTag("live_epg_button_9101").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasTestTag("epg_dropdown_9101")).fetchSemanticsNodes().size)
    }

    @Test
    fun notificationTap_requestOpensThatChannel_onceOnly() = runBlocking {
        val ch = channel(9201, "HD: TRT 4K", null)
        db.iptvDao().insertItems(listOf(ch))
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        vm.requestOpenChannel(9201)
        assertEquals(9201, vm.openChannelRequest.value)
        assertEquals(9201, vm.consumeOpenChannelRequest()?.id)
        assertNull(vm.consumeOpenChannelRequest())
        // Liste değişmiş (kanal yok): uygulama yalnızca açılır.
        vm.requestOpenChannel(424242)
        assertNull(vm.consumeOpenChannelRequest())
    }

    @Test
    @Config(qualifiers = "tr-w960dp-h540dp-land-television")
    fun tv_rightOpensSchedule_okSetsReminder_leftCloses() {
        TvDevice.overrideForTest = true
        val a = channel(9301, "TV EPG Bir", "bir.tr")
        val b = channel(9302, "TV EPG İki", null)
        runBlocking { db.iptvDao().insertItems(listOf(a, b)) }
        val vm = IPTVViewModel(app, IPTVRepository(db.iptvDao(), db))
        vm.setEpgProgramsForTest(mapOf("bir.tr" to schedule))
        val ui = TvPlayerUiState().apply { launch = TvPlayerUiState.LAUNCH_ENTER }
        compose.setContent {
            PlayerScreen(
                item = a, siblingItems = emptyList(), onBack = {}, onPlayItem = {},
                onProgressUpdate = { _, _, _ -> }, iptvViewModel = vm, tvMode = true, tvUiState = ui
            )
        }
        for (i in 0 until 100) {
            if (compose.onAllNodes(hasTestTag("tv_channel_9301")).fetchSemanticsNodes().isNotEmpty()) break
            org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tv_channel_9301").assertIsFocused()

        press(Key.DirectionRight)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("tv_epg_column")).fetchSemanticsNodes().isNotEmpty() }
        val live = schedule[1].startEpochMillis
        val next = schedule[2].startEpochMillis
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("tv_epg_program_$live").assertIsFocused() }.isSuccess }
        press(Key.DirectionDown)
        compose.onNodeWithTag("tv_epg_program_$next").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(5_000) { ProgramReminders.isSet(app, 9301, next) }

        press(Key.DirectionLeft)
        assertEquals(0, compose.onAllNodes(hasTestTag("tv_epg_column")).fetchSemanticsNodes().size)
        compose.onNodeWithTag("tv_channel_9301").assertIsFocused()
        assertNotNull(ui)
    }
}
