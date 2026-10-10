package com.example.player

import androidx.media3.common.Tracks

/**
 * Ses/altyazı seçim listesinde gösterilecek kanalların sırası.
 *
 * Cihazın çözemediği kanal (ör. çözücüsü olmayan DTS/TrueHD) listede görünürse seçilince ses kesiliyor ya da içerik
 * oynamıyordu. Kapasiteyi aşsa da çözülebilen kanallar kalır; hiç çalınamayanlar sunulmaz.
 */
fun selectableTrackIndices(group: Tracks.Group): List<Int> =
    (0 until group.length).filter { group.isTrackSupported(it, true) }
