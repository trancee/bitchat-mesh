package com.bitchat.android.features.file

import android.content.ContextWrapper
import com.bitchat.android.model.BitchatFilePacket
import com.bitchat.android.model.BitchatMessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class FileUtilsTests {
    private class TestContext(
        private val cache: File,
        private val files: File
    ) : ContextWrapper(null) {
        override fun getCacheDir(): File = cache
        override fun getFilesDir(): File = files
    }

    @Test
    fun getMimeTypeFromExtensionMapsKnownTypes() {
        assertEquals("text/plain", FileUtils.getMimeTypeFromExtension("note.txt"))
        assertEquals("image/png", FileUtils.getMimeTypeFromExtension("photo.png"))
        assertEquals("application/octet-stream", FileUtils.getMimeTypeFromExtension("archive.bin"))
    }

    @Test
    fun formatFileSizeRoundsToUnits() {
        assertEquals("1.0 KB", FileUtils.formatFileSize(1024))
        assertEquals("1.0 MB", FileUtils.formatFileSize(1024 * 1024))
    }

    @Test
    fun isFileViewableChecksExtensions() {
        assertTrue(FileUtils.isFileViewable("doc.pdf"))
        assertTrue(FileUtils.isFileViewable("image.JPG"))
    }

    @Test
    fun messageTypeForMimeClassifiesMedia() {
        assertEquals(BitchatMessageType.Image, FileUtils.messageTypeForMime("image/png"))
        assertEquals(BitchatMessageType.Audio, FileUtils.messageTypeForMime("audio/mpeg"))
        assertEquals(BitchatMessageType.File, FileUtils.messageTypeForMime("application/octet-stream"))
    }

    @Test
    fun saveIncomingFileWritesToCacheAndEnsuresUniqueness() {
        val cacheDir = Files.createTempDirectory("bitchat-cache").toFile()
        val filesDir = Files.createTempDirectory("bitchat-files").toFile()
        val context = TestContext(cacheDir, filesDir)

        val packet = BitchatFilePacket(
            fileName = "report.txt",
            fileSize = 3,
            mimeType = "text/plain",
            content = byteArrayOf(0x01, 0x02, 0x03)
        )

        val firstPath = FileUtils.saveIncomingFile(context, packet)
        val secondPath = FileUtils.saveIncomingFile(context, packet)

        assertTrue(firstPath.isNotBlank())
        assertTrue(File(firstPath).exists())
        assertTrue(secondPath.isNotBlank())
        assertTrue(File(secondPath).exists())
        assertNotEquals(firstPath, secondPath)
    }

    @Test
    fun clearAllMediaRemovesFilesAndCache() {
        val cacheDir = Files.createTempDirectory("bitchat-cache").toFile()
        val filesDir = Files.createTempDirectory("bitchat-files").toFile()
        val context = TestContext(cacheDir, filesDir)

        val incoming = File(filesDir, "files/incoming").apply { mkdirs() }
        File(incoming, "note.txt").writeText("data")
        val outgoing = File(filesDir, "files/outgoing").apply { mkdirs() }
        File(outgoing, "note2.txt").writeText("data")
        val cacheIncoming = File(cacheDir, "files/incoming").apply { mkdirs() }
        File(cacheIncoming, "cache.txt").writeText("data")

        FileUtils.clearAllMedia(context)

        assertTrue(!incoming.exists())
        assertTrue(!outgoing.exists())
        assertTrue(!cacheIncoming.exists())
        assertTrue(!cacheDir.exists() || cacheDir.listFiles().isNullOrEmpty())
    }
}
