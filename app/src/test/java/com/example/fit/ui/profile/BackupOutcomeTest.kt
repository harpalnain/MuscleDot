package com.example.fit.ui.profile

import com.example.fit.data.backup.BackupException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BackupOutcomeTest {
    @Test
    fun save_writesTheExportedText() = runTest {
        var written: String? = null
        assertEquals(BackupMessage.SAVED, saveBackup(export = { "data" }, write = { written = it }))
        assertEquals("data", written)
    }

    @Test
    fun save_reportsAFileThatCouldNotBeWritten() = runTest {
        assertEquals(BackupMessage.SAVE_FAILED, saveBackup({ "data" }, { throw IOException("disk full") }))
        assertEquals(BackupMessage.SAVE_FAILED, saveBackup({ "data" }, { throw SecurityException("permission gone") }))
    }

    @Test
    fun save_passesCancellationOn() = runTest {
        val failure = runCatching { saveBackup({ "data" }, { throw CancellationException("stopped") }) }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun read_givesTheText() = runTest {
        assertEquals(BackupRead.Text("data"), readBackup { "data" })
    }

    @Test
    fun read_tooBigIsNotABackup_andAnUnreadableFileSaysSo() = runTest {
        assertEquals(BackupRead.Failed(BackupMessage.NOT_A_BACKUP), readBackup { null })
        assertEquals(BackupRead.Failed(BackupMessage.READ_FAILED), readBackup { throw IOException("provider offline") })
        assertEquals(BackupRead.Failed(BackupMessage.READ_FAILED), readBackup { throw SecurityException("permission gone") })
    }

    @Test
    fun read_passesCancellationOn() = runTest {
        val failure = runCatching { readBackup { throw CancellationException("stopped") } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun restore_reportsWhatHappened() = runTest {
        assertEquals(BackupMessage.RESTORED, restoreBackup { })
        assertEquals(
            BackupMessage.NOT_A_BACKUP,
            restoreBackup { throw BackupException(BackupException.Reason.NOT_A_BACKUP) },
        )
        assertEquals(
            BackupMessage.NEWER_VERSION,
            restoreBackup { throw BackupException(BackupException.Reason.NEWER_VERSION) },
        )
    }
}
