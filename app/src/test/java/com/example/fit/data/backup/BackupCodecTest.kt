package com.example.fit.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {
    private fun reasonOf(text: String) =
        runCatching { BackupCodec.decode(text) }.exceptionOrNull().let { (it as BackupException).reason }

    @Test
    fun rejectsThingsThatAreNotBackups() {
        val notBackups = listOf(
            "",
            "hello",
            "[1,2]",
            "{\"format\":\"something-else\",\"version\":1}",
            "{\"format\":\"overload-backup\",\"version\":1}",
        )
        for (text in notBackups) assertEquals(text, BackupException.Reason.NOT_A_BACKUP, reasonOf(text))
    }

    @Test
    fun rejectsANewerVersion() =
        assertEquals(BackupException.Reason.NEWER_VERSION, reasonOf("{\"format\":\"overload-backup\",\"version\":2}"))

    @Test
    fun fileName() = assertEquals("muscledot-backup-2026-11-12.json", backupFileName(LocalDate.of(2026, 11, 12)))
}
