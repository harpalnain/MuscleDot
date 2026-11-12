package com.example.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Unit tests run with the module directory `app/` as the working directory. */
class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun requestsNoPermission() = assertFalse(manifest.contains("uses-permission"))

    @Test
    fun backupIsOff() = assertTrue(manifest.contains("android:allowBackup=\"false\""))

    @Test
    fun dataExtractionExcludesDatabaseForBothChannels() {
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertEquals(2, Regex("<exclude domain=\"database\"").findAll(rules).count())
    }
}
