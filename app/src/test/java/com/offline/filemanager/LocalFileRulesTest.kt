package com.offline.filemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFileRulesTest {
    @Test fun detectsMediaExtensionsCaseInsensitively() {
        assertTrue(LocalFileRules.isVideo("movie.MP4"))
        assertTrue(LocalFileRules.isVideo("clip.webm"))
        assertFalse(LocalFileRules.isVideo("song.mp3"))
        assertTrue(LocalFileRules.isAudio("voice.FLAC"))
        assertTrue(LocalFileRules.isImage("holiday.JPEG"))
        assertTrue(LocalFileRules.isImage("wallpaper.webp"))
    }

    @Test fun identifiesArchivesAndDocuments() {
        assertTrue(LocalFileRules.isZipArchive("backup.zip"))
        assertFalse(LocalFileRules.isZipArchive("backup.7z"))
        assertTrue(LocalFileRules.isPdf("contract.PDF"))
        assertTrue(LocalFileRules.isTextDocument("settings.JSON"))
        assertTrue(LocalFileRules.isDocument("sheet.xlsx"))
    }

    @Test fun extractsExtensionWithoutAssumingCase() {
        assertEquals("pdf", LocalFileRules.extensionOf("report.PDF"))
        assertEquals("", LocalFileRules.extensionOf("README"))
    }

    @Test fun rejectsZipSlipAndAbsoluteArchivePaths() {
        assertFalse(LocalFileRules.isSafeArchivePath("../outside.txt"))
        assertFalse(LocalFileRules.isSafeArchivePath("folder/../../outside.txt"))
        assertFalse(LocalFileRules.isSafeArchivePath("/absolute/path.txt"))
        assertFalse(LocalFileRules.isSafeArchivePath("C:\\temp\\payload.txt"))
        assertFalse(LocalFileRules.isSafeArchivePath("folder//payload.txt"))
        assertFalse(LocalFileRules.isSafeArchivePath("folder//"))
        assertFalse(LocalFileRules.isSafeArchivePath("folder\\\\payload.txt"))
        assertTrue(LocalFileRules.isSafeArchivePath("photos/image.jpg"))
        assertTrue(LocalFileRules.isSafeArchivePath("docs/"))
    }

    @Test fun normalizesUnsafeFileNames() {
        assertEquals("a_b_c.txt", LocalFileRules.safeFileName("a/b\\c.txt"))
        assertEquals("ملف", LocalFileRules.safeFileName(" .. "))
        assertEquals("untitled", LocalFileRules.safeFileName("", "untitled"))
    }
}
