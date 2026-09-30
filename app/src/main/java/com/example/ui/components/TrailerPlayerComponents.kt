package com.example.ui.components
import com.example.R

import com.example.ui.theme.DeepPurpleBg
import com.example.ui.theme.MidPurpleBg
import com.example.ui.theme.NeonPink
import com.example.ui.theme.PinkToPurpleGradient
import java.util.Locale


/**
 * Parses YouTube Video ID from full URLs, embed links, iframe tags, or raw video IDs.
 */
fun extractYouTubeVideoId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()

    // 1. Extract src attribute if it's an iframe tag
    val cleanUrl = if (trimmed.contains("<iframe", ignoreCase = true) && trimmed.contains("src=", ignoreCase = true)) {
        val regex = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        regex.find(trimmed)?.groupValues?.get(1) ?: trimmed
    } else {
        trimmed
    }

    // 2. Matching standard YouTube patterns (watch?v=, embed/, v/, shorts/, shortlink)
    val patterns = listOf(
        "(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?|shorts)\\/|\\S*?[?&]v=)|youtu\\.be\\/)([a-zA-Z0-9_-]{8,15})".toRegex(),
        """(?:youtube\.com/embed/|youtube\.com/v/|youtube\.com/shorts/|youtu\.be/|youtube\.com/watch\?v=)([a-zA-Z0-9_-]{8,15})""".toRegex()
    )

    for (pattern in patterns) {
        val matchResult = pattern.find(cleanUrl)
        if (matchResult != null) {
            val id = matchResult.groupValues[1]
            if (id.isNotBlank()) return id
        }
    }

    // 3. Raw Video ID check (no slashes, no spaces, length between 8 and 15 chars)
    if (!cleanUrl.contains("/") && !cleanUrl.contains(" ") && cleanUrl.length in 8..15) {
        return cleanUrl
    }

    return null
}

/**
 * Converts any YouTube input (raw ID, embed, iframe, URL) into a standard YouTube watch URL.
 */
fun formatYouTubeWatchUrl(input: String?): String? {
    val videoId = extractYouTubeVideoId(input) ?: return null
    return "https://www.youtube.com/watch?v=$videoId"
}
