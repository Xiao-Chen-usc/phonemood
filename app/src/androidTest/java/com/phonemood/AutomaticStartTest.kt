package com.phonemood

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.phonemood.data.*
import com.phonemood.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class AutomaticStartTest {
    @Test fun permissionStartsOnceAndNeverOverridesAManualPause() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PhoneMoodDatabase::class.java).build()
        val repository = Repository(context, db, SettingsStore(context))
        try {
            db.dao().saveState(MonitorState())
            repository.enableOnFirstPermission(false)
            assertFalse(db.dao().state()!!.monitoringEnabled)
            assertNull(db.dao().state()!!.firstStartedUtc)
            repository.enableOnFirstPermission(true)
            val started = db.dao().state()!!
            assertTrue(started.monitoringEnabled)
            assertNotNull(started.firstStartedUtc)
            val events = db.dao().configurations().size
            repository.enableOnFirstPermission(true)
            assertEquals(events, db.dao().configurations().size)
            repository.configure(repository.configuration().copy(enabled = false))
            repository.enableOnFirstPermission(true)
            assertFalse(db.dao().state()!!.monitoringEnabled)
            assertEquals(started.firstStartedUtc, db.dao().state()!!.firstStartedUtc)
        } finally { db.close() }
    }
}
