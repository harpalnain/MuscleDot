package com.example.fit.data.backup

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class BackupFilesTest {
    /** A stream that hands out one byte per read, as a slow document provider may. */
    private class Trickle(private val bytes: ByteArray) : InputStream() {
        private var next = 0
        override fun read(): Int = if (next < bytes.size) bytes[next++].toInt() and 0xFF else -1
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (next >= bytes.size) return -1
            buffer[offset] = bytes[next++]
            return 1
        }
    }

    @Test
    fun readsAllTheText() = assertEquals("héllo ✓", readTextCapped("héllo ✓".byteInputStream(), 100))

    @Test
    fun readsTextLongerThanOneBuffer() {
        val text = "0123456789".repeat(10_000)
        assertEquals(text, readTextCapped(text.byteInputStream(), 200_000))
    }

    @Test
    fun readsFromAStreamThatGivesAFewBytesAtATime() =
        assertEquals("slow but whole", readTextCapped(Trickle("slow but whole".toByteArray()), 100))

    @Test
    fun textExactlyAtTheLimitIsRead() = assertEquals("12345", readTextCapped("12345".byteInputStream(), 5))

    @Test
    fun moreThanTheLimitIsRefused() = assertNull(readTextCapped("123456".byteInputStream(), 5))

    @Test
    fun emptyFileIsEmptyText() = assertEquals("", readTextCapped(ByteArrayInputStream(ByteArray(0)), 5))

    @Test
    fun writesTheText() {
        val out = ByteArrayOutputStream()
        writeTextTo({ out }, "héllo ✓")
        assertEquals("héllo ✓", out.toString("UTF-8"))
    }

    @Test
    fun aFileThatCannotBeOpenedIsAnError_notASilentSuccess() {
        assertThrows(IOException::class.java) { writeTextTo({ null }, "data") }
    }

    @Test
    fun importFailure_keepsCancellationAndBackupErrors_andCallsTheRestNotABackup() {
        val cancelled = CancellationException("stopped")
        assertSame(cancelled, importFailure(cancelled))
        val newer = BackupException(BackupException.Reason.NEWER_VERSION)
        assertSame(newer, importFailure(newer))
        val other = importFailure(IllegalStateException("FOREIGN KEY constraint failed"))
        assertTrue(other is BackupException && other.reason == BackupException.Reason.NOT_A_BACKUP)
    }
}
