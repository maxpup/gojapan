package com.gojapan.feature.kanalink.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KanaLinkMigrationTest {
    private val databaseName = "migration-test"
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KanaLinkDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesProgressAndSessions() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                "INSERT INTO kana_progress VALUES " +
                    "('a',1,1,0,0,0,'INDEPENDENT',100,200,0,NULL)",
            )
            execSQL(
                "INSERT INTO study_session VALUES " +
                    "('s1',100,'2026-08-23','Asia/Shanghai',30,'HIRAGANA_ROMAJI','DAILY',1,0,0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT seen_count, level FROM kana_progress WHERE kana_id='a'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM study_session").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }
}
