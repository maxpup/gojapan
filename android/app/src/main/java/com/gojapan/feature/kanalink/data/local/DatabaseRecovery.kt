package com.gojapan.feature.kanalink.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object DatabaseRecovery {
    const val DATABASE_NAME = "kana-link.db"

    fun recoverIfCorrupt(context: Context, databaseName: String = DATABASE_NAME): Boolean {
        val databaseFile = context.getDatabasePath(databaseName)
        if (!databaseFile.exists()) return false
        val healthy = runCatching {
            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("PRAGMA integrity_check(1)", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
        if (healthy) return false

        val recoveryDirectory = File(context.filesDir, "kana-link-recovery").apply { mkdirs() }
        val suffix = System.currentTimeMillis().toString()
        listOf(databaseFile, File("${databaseFile.path}-wal"), File("${databaseFile.path}-shm"))
            .filter(File::exists)
            .forEach { source -> source.copyTo(File(recoveryDirectory, "${source.name}.$suffix.corrupt")) }
        context.deleteDatabase(databaseName)
        return true
    }
}
