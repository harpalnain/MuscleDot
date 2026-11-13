package com.example.fit.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.AppContainer
import com.example.fit.data.backup.BackupCodec
import com.example.fit.data.seedTestExercises
import com.example.fit.domain.Profile
import com.example.fit.ui.profile.BackupMessage
import com.example.fit.ui.profile.ProfileViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.LocalDateTime

/** Backup export and import through the Profile ViewModel, against a real in-memory database. */
@RunWith(AndroidJUnit4::class)
class ProfileViewModelTest {
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0))
    private lateinit var c: AppContainer
    private lateinit var vm: ProfileViewModel
    private val store = ViewModelStore()

    private suspend fun nextMessage() = withTimeout(5_000) { vm.backupMessages.first() }
    private suspend fun awaitImportWaiting(waiting: Boolean) = withTimeout(5_000) { vm.importWaiting.first { it == waiting } }
    private suspend fun storedName() = c.profile.observeProfile().first().name

    /** A backup of the app as set up: the profile is named Sam. */
    private suspend fun backupText(): String {
        var text: String? = null
        vm.exportBackup { text = it }
        assertEquals(BackupMessage.SAVED, nextMessage())
        return text!!
    }

    @Before
    fun setUp() = runBlocking {
        c = AppContainer(ApplicationProvider.getApplicationContext(), inMemory = true, clock = clock)
        seedTestExercises(c.db)
        c.profile.saveProfile(Profile(name = "Sam", welcomeDone = true))
        val factory = viewModelFactory { initializer { ProfileViewModel(c.profile, c.backup, c.today, c.clock) } }
        vm = ViewModelProvider(store, factory)[ProfileViewModel::class.java]
    }

    @After
    fun tearDown() {
        // Stop the ViewModel before its database goes away, as happens in the app.
        store.clear()
        c.db.close()
    }

    @Test
    fun export_handsTheBackupToTheWriter_andSaysSaved(): Unit = runBlocking {
        assertEquals("Sam", BackupCodec.decode(backupText()).profile.name)
    }

    @Test
    fun export_thatCannotBeWritten_saysSoInsteadOfSaved(): Unit = runBlocking {
        vm.exportBackup { throw IOException("disk full") }
        assertEquals(BackupMessage.SAVE_FAILED, nextMessage())
    }

    @Test
    fun import_asksFirst_thenReplacesEverything(): Unit = runBlocking {
        val text = backupText()
        c.profile.saveProfile(Profile(name = "Changed", welcomeDone = true))
        vm.readImport { text }
        awaitImportWaiting(true)
        assertEquals("Changed", storedName()) // nothing is replaced before the answer
        vm.confirmImport()
        assertEquals(BackupMessage.RESTORED, nextMessage())
        assertEquals("Sam", storedName())
        awaitImportWaiting(false)
    }

    @Test
    fun import_cancelled_changesNothing(): Unit = runBlocking {
        val text = backupText()
        c.profile.saveProfile(Profile(name = "Changed", welcomeDone = true))
        vm.readImport { text }
        awaitImportWaiting(true)
        vm.cancelImport()
        awaitImportWaiting(false)
        vm.confirmImport() // a stray second tap: there is nothing waiting any more
        assertEquals("Changed", storedName())
    }

    @Test
    fun import_ofAFileThatCannotBeRead_orIsNotABackup_saysWhich_andChangesNothing(): Unit = runBlocking {
        vm.readImport { throw IOException("provider offline") }
        assertEquals(BackupMessage.READ_FAILED, nextMessage())
        vm.readImport { null } // too big to be a backup
        assertEquals(BackupMessage.NOT_A_BACKUP, nextMessage())
        vm.readImport { "hello" }
        awaitImportWaiting(true)
        vm.confirmImport()
        assertEquals(BackupMessage.NOT_A_BACKUP, nextMessage())
        assertEquals("Sam", storedName())
    }
}
