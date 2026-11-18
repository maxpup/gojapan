package com.gojapan.feature.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gojapan.feature.expense.Expense
import com.gojapan.feature.kanalink.data.local.KanaLinkDatabase
import com.gojapan.feature.travelplan.CurrentPlan
import com.gojapan.feature.travelplan.TravelDatabase
import com.gojapan.feature.travelplan.TravelPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class LocalBackupTest {
    private fun isolatedContext(base: Context, dir: File) = object : android.content.ContextWrapper(base) {
        override fun getNoBackupFilesDir() = File(dir, "private").apply { mkdirs() }
        override fun getFilesDir() = File(dir, "files").apply { mkdirs() }
        override fun getDatabasePath(name: String) = if (File(name).isAbsolute) File(name) else File(dir, "db/$name")
        override fun getApplicationInfo() = android.content.pm.ApplicationInfo(base.applicationInfo).apply { dataDir = dir.path }
    }

    @Test fun completeRestoreIsRepeatableAcrossAllStores() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val dir = java.nio.file.Files.createTempDirectory(base.cacheDir.toPath(), "all-stores").toFile()
        val source = isolatedContext(base, File(dir, "source"))
        val target = isolatedContext(base, File(dir, "destination"))
        val travel = Room.inMemoryDatabaseBuilder(base, TravelDatabase::class.java).build()
        val kana = Room.inMemoryDatabaseBuilder(base, KanaLinkDatabase::class.java).build()
        try {
            val plan = TravelPlan("local", 7, "one", 0, "京都", "日本", "Asia/Tokyo", "ja", null, null)
            travel.plans().insert(plan)
            travel.plans().select(CurrentPlan("local", 7))
            val expense = Expense("local", 7, "lunch", "2026-11-17", "午餐", "餐饮", "CNY", 5000)
            travel.expenses().save(listOf(expense))
            kana.openHelper.writableDatabase.execSQL("INSERT INTO kana_progress VALUES ('a',1,1,0,0,1,NULL,NULL,NULL,0,NULL)")
            val trip = """{"tripTitle":"京都","destination":"京都","days":[{"date":"第 1 天","title":"散步","summary":"","stops":[{"name":"京都站","googleAddress":"Kyoto Station"}],"todos":["☑ 车票"]}]}"""
            val data = mapOf("trip_planner" to ("active_trip_plan" to trip),
                "travel-library" to ("generated_expressions" to "[]"), "kana-link-settings" to ("course" to "hiragana"))
            for ((name, entry) in data) {
                val file = File(source.filesDir, "datastore/$name.preferences_pb")
                file.parentFile!!.mkdirs()
                val prefs = androidx.datastore.preferences.core.mutablePreferencesOf(androidx.datastore.preferences.core.stringPreferencesKey(entry.first) to entry.second)
                file.outputStream().use { androidx.datastore.preferences.core.PreferencesFileSerializer.writeTo(prefs, it) }
            }
            val history = """{"entries":[],"traces":[{"id":"v1","entryId":null}]}"""
            File(source.noBackupFilesDir, "personal-learning.json").writeText(history)
            val archive = Uri.fromFile(File(dir, "backup.zip"))
            LocalBackup(source, travel, kana).export(archive)
            val importer = LocalBackup(target, travel, kana)
            repeat(2) {
                File(target.noBackupFilesDir, "personal-learning.json").writeText("""{"entries":[]}""")
                importer.confirm(importer.inspect(archive))
                assertEquals("备份已恢复", finishPendingRestore(target))
                val restored = Room.databaseBuilder(base, TravelDatabase::class.java, target.getDatabasePath("travel-plans.db").path).build()
                try {
                    assertEquals(plan, restored.plans().get("local", 7))
                    assertEquals(listOf(expense), restored.expenses().observe("local", 7).first())
                } finally { restored.close() }
                val learned = Room.databaseBuilder(base, KanaLinkDatabase::class.java, target.getDatabasePath("kana-link.db").path).build()
                try {
                    learned.openHelper.readableDatabase.query("SELECT kana_id FROM kana_progress").use { c ->
                        assertTrue(c.moveToFirst()); assertEquals("a", c.getString(0)); assertFalse(c.moveToNext())
                    }
                } finally { learned.close() }
                data.keys.forEach { name ->
                    val path = "datastore/$name.preferences_pb"
                    assertArrayEquals(File(source.filesDir, path).readBytes(), File(target.filesDir, path).readBytes())
                }
                assertEquals("v1", com.gojapan.feature.personal.AtomicPersonalStorage(target).read().traces.single().id)
            }
        } finally { travel.close(); kana.close(); dir.deleteRecursively() }
    }

    @Test fun interruptedRestoreReturnsOriginalFilesBeforeOpeningStores() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val dir = java.nio.file.Files.createTempDirectory(base.cacheDir.toPath(), "restore-test").toFile()
        try {
            val isolated = isolatedContext(base, dir)
            val root = isolated.noBackupFilesDir
            File(root, "restore-pending").mkdir()
            val journal = File(root, "restore-rollback").apply { mkdir() }
            File(journal, "originals").writeText("personal-learning.json")
            File(journal, "personal-learning.json").writeText("before")
            File(root, "personal-learning.json").writeText("partial replacement")
            val introduced = isolated.getDatabasePath("travel-plans.db")
            introduced.parentFile!!.mkdirs()
            introduced.writeText("partial new file")
            assertEquals("恢复曾中断，已还原原有数据", finishPendingRestore(isolated))
            assertEquals("before", File(root, "personal-learning.json").readText())
            assertFalse(introduced.exists())
            assertNull(finishPendingRestore(isolated))
        } finally { dir.deleteRecursively() }
    }

    @Test fun prepareRestoresPlanOwnershipAndRejectsIncompleteArchives() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val travel = Room.inMemoryDatabaseBuilder(c, TravelDatabase::class.java).build()
        val kana = Room.inMemoryDatabaseBuilder(c, KanaLinkDatabase::class.java).build()
        val archive = File.createTempFile("roundtrip", ".zip", c.cacheDir)
        try {
            val backup = LocalBackup(c, travel, kana)
            val plan = TravelPlan("local", 10, "a", 0, "东京", "日本", "Asia/Tokyo", "ja", null, null)
            travel.plans().insert(plan)
            travel.plans().insert(plan.copy(id = 11, request = "b"))
            travel.plans().select(CurrentPlan("local", 11))
            val row = Expense("local", 10, "train", "2026-11-17", "地铁", "交通", "JPY", 200)
            travel.expenses().save(listOf(row))
            backup.export(Uri.fromFile(archive))
            ZipFile(archive).use { zip -> assertFalse(zip.entries().asSequence().any { it.name.contains("credential") }) }
            val preview = backup.inspect(Uri.fromFile(archive))
            try {
                assertEquals(2, preview.plans)
                assertEquals(1, preview.expenses)
                val restored = Room.databaseBuilder(c, TravelDatabase::class.java, File(preview.directory, "travel-plans.db").path).build()
                try {
                    assertEquals(11L, restored.plans().observe("local").first().single { it.selected }.plan.id)
                    assertEquals(listOf(row), restored.expenses().observe("local", 10).first())
                    assertTrue(restored.expenses().observe("local", 11).first().isEmpty())
                } finally { restored.close() }
            } finally { preview.directory.deleteRecursively() }
            archive.writeText("损坏的备份")
            assertTrue(runCatching { backup.inspect(Uri.fromFile(archive)) }.isFailure)
            assertEquals(plan, travel.plans().get("local", 10))
        } finally { archive.delete(); travel.close(); kana.close() }
    }
}
