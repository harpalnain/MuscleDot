package com.example.fit.data.backup

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val BUFFER_BYTES = 64 * 1024

/**
 * All of [input] as text, or null when it holds more than [maxBytes]: a backup is
 * small text, and anything bigger is some other file that is not read into memory.
 * Reads with the plain `read` loop that every supported Android version has.
 */
fun readTextCapped(input: InputStream, maxBytes: Int): String? {
    val collected = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (collected.size() + count > maxBytes) return null
        collected.write(buffer, 0, count)
    }
    return collected.toString(Charsets.UTF_8.name())
}

/**
 * Writes [text] to the stream [open] gives. A file that cannot be opened is an
 * [IOException], so it is never reported as a saved backup.
 */
fun writeTextTo(open: () -> OutputStream?, text: String) {
    val output = open() ?: throw IOException("The file could not be opened for writing")
    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

/**
 * What an import that failed half way reports. A refusal stays as it is and a
 * cancelled import stays cancelled; anything else means the file did not hold
 * together (a bad date, a reference to something it does not contain).
 */
fun importFailure(e: Exception): Exception = when (e) {
    is CancellationException, is BackupException -> e
    else -> BackupException(BackupException.Reason.NOT_A_BACKUP)
}
