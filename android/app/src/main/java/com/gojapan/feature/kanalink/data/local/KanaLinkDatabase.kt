package com.gojapan.feature.kanalink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [KanaProgressEntity::class, StudySessionEntity::class, SessionItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class KanaLinkDatabase : RoomDatabase() {
    abstract fun progressDao(): KanaProgressDao
    abstract fun sessionDao(): StudySessionDao
    abstract fun sessionItemDao(): SessionItemDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Version 2 establishes the explicit migration baseline; schema is unchanged.
    }
}
