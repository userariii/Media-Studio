package com.android.multimediastudio.util

import java.util.Locale

object TitleUtils {
    /**
     * Extracts a clean, readable short title from a raw filename or media title.
     * Prefers TV Show Season/Episode identifiers (e.g., Vikings S01E01) or
     * Movie Titles with Years (e.g., Inception (2010)). Truncates noisy release group
     * information and quality tags.
     */
    fun formatShortTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return "Video Playback"

        // Strip file extensions if present
        val text = rawTitle.replace(Regex("(?i)\\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|3gp|ts)$"), "")

        // 1. Season and Episode Pattern: Vikings_S01E01_2013... or VikingsS01E01...
        val sxxexxRegex = Regex("(?i)^(.*?)[ ._\\-]*(S\\d{1,2}\\s*E\\d{1,2}|\\d{1,2}x\\d{1,2}|E\\d{1,2}|Episode\\s*\\d+)(.*)$")
        val sxxexxMatch = sxxexxRegex.find(text)
        if (sxxexxMatch != null) {
            val (showRaw, epRaw, _) = sxxexxMatch.destructured
            val cleanShow = showRaw
                .replace(Regex("[._\\-]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
            val cleanEp = epRaw
                .uppercase(Locale.ROOT)
                .replace(" ", "")

            if (cleanShow.isNotBlank()) {
                return "$cleanShow $cleanEp"
            }
        }

        // 2. Movie Title with Year e.g. Inception.2010.1080p
        val yearRegex = Regex("(?i)^(.*?)[ ._\\-]+(19\\d{2}|20\\d{2})[ ._\\-]+(.*)$")
        val yearMatch = yearRegex.find(text)
        if (yearMatch != null) {
            val (movieRaw, yearRaw, _) = yearMatch.destructured
            val cleanMovie = movieRaw
                .replace(Regex("[._\\-]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
            if (cleanMovie.isNotBlank()) {
                return "$cleanMovie ($yearRaw)"
            }
        }

        // 3. Clean quality tags, codec tags, brackets
        val cleaned = text
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
            .replace(
                Regex("(?i)\\b(1080p|720p|2160p|4k|480p|hevc|h264|x264|x265|blu-ray|bluray|web-dl|webrip|hdrip|dvdrip|10bit|aac|ac3|dts|5\\.1|7\\.1)\\b"),
                ""
            )
            .replace(Regex("[._\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.isNotBlank()) cleaned else rawTitle
    }
}
