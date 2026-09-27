package com.example.data.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * Oyuncu avatarları için: önbellekte fotoğrafsız (Gemini'den gelmiş) kayıt varsa TMDB'ye
 * oturum başına bir kez daha sorulmasına izin verir. Aksi halde avatar kalıcı olarak baş harfte kalıyordu.
 */
object PersonPhotoRetry {
    private val attempted: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Aynı kişi için bu oturumda ilk çağrıda true, sonrakilerde false döner. */
    fun shouldRetry(normalizedName: String): Boolean = attempted.add(normalizedName)
}
