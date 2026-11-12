package com.example.fit.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Why a file was refused. The app's data is never touched when this is thrown. */
class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION }
}

object BackupCodec {
    private val json = Json {
        encodeDefaults = true // "format" and "version" must be written
        ignoreUnknownKeys = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * Reads a backup file. `format` and `version` are checked before the rest, so a
     * file from a newer app version is reported as such rather than as broken.
     */
    fun decode(text: String): BackupFile {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: notABackup()
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != BACKUP_FORMAT) notABackup()
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: notABackup()
        if (version > BACKUP_VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        return runCatching { json.decodeFromJsonElement(BackupFile.serializer(), root) }.getOrNull() ?: notABackup()
    }

    private fun notABackup(): Nothing = throw BackupException(BackupException.Reason.NOT_A_BACKUP)
}
