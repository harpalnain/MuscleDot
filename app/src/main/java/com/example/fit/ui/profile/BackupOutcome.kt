package com.example.fit.ui.profile

import com.example.fit.data.backup.BackupException
import kotlinx.coroutines.CancellationException

/** What happened to an export or import, for a message to the user. */
enum class BackupMessage { SAVED, SAVE_FAILED, RESTORED, READ_FAILED, NOT_A_BACKUP, NEWER_VERSION }

/** What reading the chosen file gave: its text, or the reason there is none. */
sealed interface BackupRead {
    data class Text(val text: String) : BackupRead
    data class Failed(val message: BackupMessage) : BackupRead
}

/**
 * Saves a backup: [export] gives its text and [write] puts it in the chosen file.
 * A file that could not be written is reported, never passed off as saved.
 */
suspend fun saveBackup(export: suspend () -> String, write: suspend (String) -> Unit): BackupMessage = try {
    write(export())
    BackupMessage.SAVED
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    BackupMessage.SAVE_FAILED
}

/** Reads the chosen file with [read], which gives null for a file too big to be a backup. */
suspend fun readBackup(read: suspend () -> String?): BackupRead = try {
    read()?.let { BackupRead.Text(it) } ?: BackupRead.Failed(BackupMessage.NOT_A_BACKUP)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    BackupRead.Failed(BackupMessage.READ_FAILED)
}

/** Replaces the app's data through [import], which throws [BackupException] for a file it refuses. */
suspend fun restoreBackup(import: suspend () -> Unit): BackupMessage = try {
    import()
    BackupMessage.RESTORED
} catch (e: BackupException) {
    when (e.reason) {
        BackupException.Reason.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
        BackupException.Reason.NEWER_VERSION -> BackupMessage.NEWER_VERSION
    }
}
