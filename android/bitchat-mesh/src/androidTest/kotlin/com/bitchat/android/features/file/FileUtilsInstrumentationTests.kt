package com.bitchat.android.features.file

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileUtilsInstrumentationTests {
    @Test
    fun saveFileFromUriCopiesContent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "source.txt")
        source.writeText("hello")

        val uri = Uri.fromFile(source)
        val savedPath = FileUtils.saveFileFromUri(context, uri, "note.txt")

        assertNotNull(savedPath)
        val savedFile = File(savedPath!!)
        assertTrue(savedFile.exists())
        assertEquals("hello", savedFile.readText())
    }

    @Test
    fun copyFileForSendingPreservesNameAndCreatesUniqueCopy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "report.txt")
        source.writeText("data")

        val uri = Uri.fromFile(source)
        val firstPath = FileUtils.copyFileForSending(context, uri, "report.txt")
        val secondPath = FileUtils.copyFileForSending(context, uri, "report.txt")

        assertNotNull(firstPath)
        assertNotNull(secondPath)
        assertNotEquals(firstPath, secondPath)
        assertTrue(File(firstPath!!).exists())
        assertTrue(File(secondPath!!).exists())
    }
}
