package com.phonemood

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.phonemood.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun versionOneFactsSurviveUpgrade() = verifyUpgrade(1)
    @Test fun versionTwoFactsSurviveUpgradeWithoutInventingCoverage() = verifyUpgrade(2)
    @Test fun versionThreeFactsSurviveTheRepeatDeliveryColumns() = verifyUpgrade(3)
    private fun verifyUpgrade(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-overlay-test.db"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.phonemood.data.PhoneMoodDatabase/$version.json").bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject["database"]!!.jsonObject }
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            for (entity in schema["entities"]!!.jsonArray) {
                val e = entity.jsonObject; val table = e["tableName"]!!.jsonPrimitive.content
                legacy.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { index -> legacy.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema["setupQueries"]!!.jsonArray.forEach { legacy.execSQL(it.jsonPrimitive.content) }
            legacy.execSQL("INSERT INTO MoodCheckpoint VALUES ('old-check','old-session',30,1000,'test.app','UTC','ANSWERED',1001)")
            legacy.execSQL("INSERT INTO MoodResponse VALUES ('old-check',1002,7,'UTC')")
            legacy.version = version
        }
        val upgraded = Room.databaseBuilder(context, PhoneMoodDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        try {
            assertEquals(7, upgraded.dao().responses().single().score)
            assertEquals("old-check", upgraded.dao().checkpoints().single().checkpointId)
            assertTrue(upgraded.dao().promptStates().isEmpty())
            upgraded.dao().savePromptState(MoodPromptState("old-check", dismissed = true))
            assertTrue(upgraded.dao().promptState("old-check")!!.dismissed)
            assertEquals(0, upgraded.dao().promptState("old-check")!!.notifyCount)
            assertNull(upgraded.dao().promptState("old-check")!!.lastNotifiedUtc)
            assertTrue(upgraded.dao().coverage(0,10_000).isEmpty())
            upgraded.dao().saveCoverage(UsageCoverageEvidence(1000,2000,2000))
            assertEquals(1000L,upgraded.dao().coverage(0,10_000).single().startUtc)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
