package com.example.data.repository

object ChannelLogoMapper {

    /**
     * SADECE M3U'nun sağladığı gerçek logo/poster URL'sini kullanır. Gerçek
     * veri yoksa null döner — SafeAsyncImage kendi dürüst placeholder
     * durumunu gösterir, asla alakasız/sahte bir görsel gösterilmez.
     */
    fun getLogoForChannel(cleanedName: String, category: String, originalLogoUrl: String?): String? {
        if (!originalLogoUrl.isNullOrEmpty() && originalLogoUrl.trim().startsWith("http", ignoreCase = true)) {
            return originalLogoUrl.trim()
        }
        return null
    }
}

