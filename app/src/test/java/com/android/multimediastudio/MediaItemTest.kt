package com.android.multimediastudio

import com.android.multimediastudio.data.model.MediaItem
import com.android.multimediastudio.data.model.MediaType
import com.android.multimediastudio.data.model.sanitizeAlbum
import com.android.multimediastudio.data.model.sanitizeArtist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemTest {

    @Test
    fun getFormattedDuration_formatsUnderOneHourCorrectly() {
        val item = MediaItem(
            id = 1L,
            title = "Test",
            contentUri = null,
            durationMs = 185000L, // 3 mins 5 secs
            sizeBytes = 1000L,
            mimeType = "video/mp4",
            dateModified = 0L,
            thumbnailUri = null,
            mediaType = MediaType.VIDEO
        )

        assertEquals("03:05", item.getFormattedDuration())
    }

    @Test
    fun getFormattedDuration_formatsOverOneHourCorrectly() {
        val item = MediaItem(
            id = 1L,
            title = "Test",
            contentUri = null,
            durationMs = 3725000L, // 1 hour 2 mins 5 secs
            sizeBytes = 1000L,
            mimeType = "video/mp4",
            dateModified = 0L,
            thumbnailUri = null,
            mediaType = MediaType.VIDEO
        )

        assertEquals("1:02:05", item.getFormattedDuration())
    }

    @Test
    fun getFormattedDuration_handlesZeroDuration() {
        val item = MediaItem(
            id = 1L,
            title = "Test",
            contentUri = null,
            durationMs = 0L,
            sizeBytes = 1000L,
            mimeType = "audio/mp3",
            dateModified = 0L,
            thumbnailUri = null,
            mediaType = MediaType.AUDIO
        )

        assertEquals("00:00", item.getFormattedDuration())
    }

    @Test
    fun getFormattedSize_formatsBytesMegabytesAndGigabytes() {
        val itemKB = MediaItem(
            id = 1L, title = "Test", contentUri = null, durationMs = 0L,
            sizeBytes = 2048L, mimeType = "audio/mp3", dateModified = 0L,
            thumbnailUri = null, mediaType = MediaType.AUDIO
        )
        assertEquals("2 KB", itemKB.getFormattedSize())

        val itemMB = MediaItem(
            id = 2L, title = "Test", contentUri = null, durationMs = 0L,
            sizeBytes = 15728640L, mimeType = "video/mp4", dateModified = 0L,
            thumbnailUri = null, mediaType = MediaType.VIDEO
        )
        assertEquals("15.0 MB", itemMB.getFormattedSize())

        val itemGB = MediaItem(
            id = 3L, title = "Test", contentUri = null, durationMs = 0L,
            sizeBytes = 2684354560L, mimeType = "video/mp4", dateModified = 0L,
            thumbnailUri = null, mediaType = MediaType.VIDEO
        )
        assertEquals("2.50 GB", itemGB.getFormattedSize())
    }

    @Test
    fun sanitizeArtist_cleansUnknownAndBlankStrings() {
        assertNull(sanitizeArtist("<unknown>"))
        assertNull(sanitizeArtist("<Unknown>"))
        assertNull(sanitizeArtist("Unknown"))
        assertNull(sanitizeArtist("Unknown Artist"))
        assertNull(sanitizeArtist("   "))
        assertNull(sanitizeArtist(null))
        assertEquals("Queen", sanitizeArtist("  Queen  "))
    }

    @Test
    fun sanitizeAlbum_cleansUnknownAndBlankStrings() {
        assertNull(sanitizeAlbum("<unknown>"))
        assertNull(sanitizeAlbum("<Unknown>"))
        assertNull(sanitizeAlbum("Unknown"))
        assertNull(sanitizeAlbum("Unknown Album"))
        assertNull(sanitizeAlbum("   "))
        assertNull(sanitizeAlbum(null))
        assertEquals("A Night at the Opera", sanitizeAlbum("  A Night at the Opera  "))
    }

    @Test
    fun getFormattedSubtitle_formatsBothArtistAndAlbum() {
        val item = MediaItem(
            id = 1L,
            title = "Bohemian Rhapsody",
            mediaType = MediaType.AUDIO,
            artist = "Queen",
            album = "A Night at the Opera"
        )
        assertEquals("Queen • A Night at the Opera Folder", item.getFormattedSubtitle())
    }

    @Test
    fun getFormattedSubtitle_formatsAlbumOnly() {
        val itemMusic = MediaItem(
            id = 1L,
            title = "Track 01",
            mediaType = MediaType.AUDIO,
            artist = "<unknown>",
            album = "Music"
        )
        assertEquals("Music Folder", itemMusic.getFormattedSubtitle())

        val itemDownload = MediaItem(
            id = 2L,
            title = "Track 02",
            mediaType = MediaType.AUDIO,
            artist = null,
            album = "Download"
        )
        assertEquals("Download Folder", itemDownload.getFormattedSubtitle())

        val itemAlreadyFolder = MediaItem(
            id = 3L,
            title = "Track 03",
            mediaType = MediaType.AUDIO,
            artist = null,
            album = "Music Folder"
        )
        assertEquals("Music Folder", itemAlreadyFolder.getFormattedSubtitle())

        val itemAlreadyFolderLower = MediaItem(
            id = 4L,
            title = "Track 04",
            mediaType = MediaType.AUDIO,
            artist = null,
            album = "Download folder"
        )
        assertEquals("Download folder", itemAlreadyFolderLower.getFormattedSubtitle())
    }

    @Test
    fun getFormattedSubtitle_formatsArtistOnly() {
        val item = MediaItem(
            id = 1L,
            title = "Cruel Summer",
            mediaType = MediaType.AUDIO,
            artist = "Taylor Swift",
            album = "<unknown>"
        )
        assertEquals("Taylor Swift", item.getFormattedSubtitle())
    }

    @Test
    fun getFormattedSubtitle_fallsBackWhenNeitherExists() {
        val audioItem = MediaItem(
            id = 1L,
            title = "Audio Track",
            mediaType = MediaType.AUDIO,
            artist = "<unknown>",
            album = "Unknown Album"
        )
        assertEquals("Local Audio Folder", audioItem.getFormattedSubtitle())

        val videoItem = MediaItem(
            id = 2L,
            title = "Video Clip",
            mediaType = MediaType.VIDEO,
            artist = "<unknown>",
            album = null
        )
        assertNull(videoItem.getFormattedSubtitle())
    }

    @Test
    fun isSocialMedia_detectsKeywordsAndWhatsAppFilenames() {
        val whatsappFile = MediaItem(
            id = 1L,
            title = "VID-20260101-WA0001.mp4",
            mediaType = MediaType.VIDEO
        )
        val telegramFile = MediaItem(
            id = 2L,
            title = "clip",
            relativePath = "Movies/Telegram/",
            mediaType = MediaType.VIDEO
        )
        val regularMovie = MediaItem(
            id = 3L,
            title = "Nature Documentary",
            relativePath = "Movies/",
            mediaType = MediaType.VIDEO
        )

        org.junit.Assert.assertTrue(whatsappFile.isSocialMedia())
        org.junit.Assert.assertTrue(telegramFile.isSocialMedia())
        org.junit.Assert.assertFalse(regularMovie.isSocialMedia())
    }

    @Test
    fun isReelOrShort_usesResolutionAndKeywordsCorrectly() {
        val portraitVideo = MediaItem(
            id = 1L,
            title = "Clip",
            resolution = "1080x1920",
            mediaType = MediaType.VIDEO
        )
        val landscapeVideo = MediaItem(
            id = 2L,
            title = "Short Film",
            resolution = "1920x1080",
            mediaType = MediaType.VIDEO
        )
        val keywordReel = MediaItem(
            id = 3L,
            title = "Holiday_Reels",
            resolution = null,
            mediaType = MediaType.VIDEO
        )
        val audioFile = MediaItem(
            id = 4L,
            title = "Reels Audio",
            resolution = "1080x1920",
            mediaType = MediaType.AUDIO
        )

        org.junit.Assert.assertTrue(portraitVideo.isReelOrShort())
        org.junit.Assert.assertFalse(landscapeVideo.isReelOrShort())
        org.junit.Assert.assertTrue(keywordReel.isReelOrShort())
        org.junit.Assert.assertFalse(audioFile.isReelOrShort())
    }
}
