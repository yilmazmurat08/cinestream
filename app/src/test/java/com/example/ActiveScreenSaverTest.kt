package com.example

import androidx.compose.runtime.saveable.SaverScope
import com.example.data.model.IPTVItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Süreç ölümünden sonra açık ekran (oynatıcıdaki içerik dahil) geri yüklenebilmeli. */
class ActiveScreenSaverTest {

    private val scope = SaverScope { true }

    /** Bundle'ın yaptığı gibi serileştirip geri okur. */
    private fun roundTrip(screen: ActiveScreen): ActiveScreen {
        val saved = with(ActiveScreen.Saver) { scope.save(screen) }!!
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(saved) }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).readObject()
        return ActiveScreen.Saver.restore(restored)!!
    }

    @Test
    fun simpleScreens_roundTrip() {
        assertEquals(ActiveScreen.Dashboard, roundTrip(ActiveScreen.Dashboard))
        assertEquals(ActiveScreen.MultiScreen, roundTrip(ActiveScreen.MultiScreen))
        assertEquals(ActiveScreen.MovieFinderChat, roundTrip(ActiveScreen.MovieFinderChat))
    }

    @Test
    fun player_keepsItem() {
        val item = IPTVItem(
            id = 42, playlistId = 1, name = "TRT 1 HD", cleanedName = "TRT 1",
            logoUrl = null, streamUrl = "http://example.com/live/1.ts", category = "Ulusal", type = "LIVE",
            season = null, episode = null, tvgId = "trt1"
        )
        assertEquals(ActiveScreen.Player(item), roundTrip(ActiveScreen.Player(item)))
    }

    @Test
    fun unknownValue_fallsBackToDashboard() {
        assertEquals(ActiveScreen.Dashboard, ActiveScreen.Saver.restore(listOf("bilinmeyen")))
        assertEquals(ActiveScreen.Dashboard, ActiveScreen.Saver.restore(listOf("player")))
    }
}
