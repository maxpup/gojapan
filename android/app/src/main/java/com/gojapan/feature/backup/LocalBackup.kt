package com.gojapan.feature.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gojapan.feature.kanalink.data.local.KanaLinkDatabase
import com.gojapan.feature.personal.*
import com.gojapan.feature.travelplan.TravelDatabase
import com.gojapan.feature.trip.model.TripPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.*

private const val MAX_BACKUP_BYTES = 128L * 1024 * 1024

private val tables = mapOf(
    "travel-plans.db" to listOf("TravelPlan", "CurrentPlan", "Expense"),
    "kana-link.db" to listOf("kana_progress", "study_session", "session_item"),
)
private val stores = listOf("trip_planner", "travel-library", "kana-link-settings")
private val preferences = listOf("text_guides_v1", "map_preferences")

private fun targets(c: Context) = buildMap {
    tables.keys.forEach { put(it, c.getDatabasePath(it)) }
    stores.forEach { put("$it.preferences_pb", File(c.filesDir, "datastore/$it.preferences_pb")) }
    preferences.forEach { put("$it.xml", File(c.applicationInfo.dataDir, "shared_prefs/$it.xml")) }
    put("personal-learning.json", File(c.noBackupFilesDir, "personal-learning.json"))
}

private fun digest(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

// Only data values enter the archive. The installed Room schema supplies all SQL identifiers.
private fun snapshot(db: SupportSQLiteDatabase, names: List<String>): JSONObject {
    val result = JSONObject()
    db.beginTransactionNonExclusive()
    try {
        for (name in names) db.query("SELECT * FROM `$name`").use { cursor ->
            val rows = JSONArray()
            while (cursor.moveToNext()) {
                val row = JSONObject()
                cursor.columnNames.forEachIndexed { i, key -> row.put(key, if (cursor.isNull(i)) JSONObject.NULL else cursor.getString(i)) }
                rows.put(row)
            }
            result.put(name, rows)
        }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
    return result
}

private fun populate(db: SupportSQLiteDatabase, names: List<String>, data: JSONObject) {
    require(data.keys().asSequence().toSet() == names.toSet())
    db.beginTransaction()
    try {
        for (name in names) {
            val columns = db.query("SELECT * FROM `$name` LIMIT 0").use { it.columnNames.toList() }
            val fields = columns.joinToString { "`$it`" }
            val params = columns.joinToString { "?" }
            db.compileStatement("INSERT INTO `$name` ($fields) VALUES ($params)").use { stmt ->
                val rows = data.getJSONArray(name)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    require(row.keys().asSequence().toSet() == columns.toSet())
                    columns.forEachIndexed { index, key ->
                        if (row.isNull(key)) stmt.bindNull(index + 1) else stmt.bindString(index + 1, row.getString(key))
                    }
                    stmt.executeInsert()
                }
            }
        }
        db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "备份中的记录关联无效" } }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}

class LocalBackup @javax.inject.Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val travel: TravelDatabase,
    private val kana: KanaLinkDatabase,
) {
    private val files get() = targets(context)
    fun export(uri: Uri) {
        val dir = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "export").toFile()
        try {
            val hashes = JSONObject()
            for ((name, file) in files) {
                val out = File(dir, name)
                when {
                    name in tables -> {
                        val db = if (name == "travel-plans.db") travel else kana
                        out.writeText(snapshot(db.openHelper.writableDatabase, tables.getValue(name)).toString())
                    }
                    name == "personal-learning.json" -> out.writeText(personalJson.encodeToString(PersonalSnapshot.serializer(), AtomicPersonalStorage(context).read()))
                    name.endsWith(".xml") -> {
                        val prefs = context.getSharedPreferences(name.removeSuffix(".xml"), Context.MODE_PRIVATE)
                        check(prefs.edit().commit())
                        if (file.exists()) file.copyTo(out) else out.writeText("<map/>")
                    }
                    file.exists() -> file.copyTo(out)
                    else -> out.writeBytes(byteArrayOf())
                }
                hashes.put(name, digest(out))
            }
            File(dir, "manifest.json").writeText(JSONObject().put("format", "GoJapan").put("version", 1)
                .put("created", System.currentTimeMillis()).put("files", hashes).toString())
            require(dir.listFiles()!!.sumOf { it.length() } <= MAX_BACKUP_BYTES) { "备份解压后超过 128 MB" }
            ZipOutputStream(requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).buffered()).use { zip ->
                dir.listFiles()!!.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } finally { dir.deleteRecursively() }
    }

    data class Preview(val directory: File, val created: Long, val plans: Int, val expenses: Int)

    suspend fun inspect(uri: Uri): Preview {
        val dir = java.nio.file.Files.createTempDirectory(context.noBackupFilesDir.toPath(), "inspect-").toFile()
        try {
            val allowed = files.keys + "manifest.json"
            val seen = mutableSetOf<String>()
            var size = 0L
            ZipInputStream(requireNotNull(context.contentResolver.openInputStream(uri)).buffered()).use { zip ->
                val buf = ByteArray(8192)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entry.name in allowed && seen.add(entry.name)) { "备份包含未知或重复文件" }
                    File(dir, entry.name).outputStream().use { output ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n
                            require(size <= MAX_BACKUP_BYTES) { "备份解压后超过 128 MB" }
                            output.write(buf, 0, n)
                        }
                    }
                }
            }
            require(seen == allowed) { "备份文件不完整" }
            val manifest = JSONObject(File(dir, "manifest.json").readText())
            require(manifest.getString("format") == "GoJapan" && manifest.getInt("version") == 1) { "不支持此备份版本" }
            val hashes = manifest.getJSONObject("files")
            files.keys.forEach { require(digest(File(dir, it)) == hashes.getString(it)) { "备份校验失败" } }
            stores.forEach { store ->
                val prefs = File(dir, "$store.preferences_pb").inputStream().use { PreferencesFileSerializer.readFrom(it) }
                if (store == "trip_planner") prefs[stringPreferencesKey("active_trip_plan")]?.let {
                    personalJson.decodeFromString(TripPlan.serializer(), it)
                }
            }
            personalJson.decodeFromString(PersonalSnapshot.serializer(), File(dir, "personal-learning.json").readText())
            preferences.forEach { name ->
                File(dir, "$name.xml").inputStream().use { input ->
                    val parser = android.util.Xml.newPullParser()
                    parser.setInput(input, "UTF-8")
                    require(parser.nextTag() == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "map")
                    while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) { }
                }
            }
            var plans = 0
            var expenses = 0
            for ((name, names) in tables) {
                val file = File(dir, name)
                val rows = JSONObject(file.readText())
                val temp = File(dir, "prepared-$name")
                val builder = if (name == "travel-plans.db") {
                    plans = rows.getJSONArray("TravelPlan").length()
                    expenses = rows.getJSONArray("Expense").length()
                    Room.databaseBuilder(context, TravelDatabase::class.java, temp.path)
                } else Room.databaseBuilder(context, KanaLinkDatabase::class.java, temp.path)
                val database = builder.setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
                try { populate(database.openHelper.writableDatabase, names, rows) } finally { database.close() }
                java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            return Preview(dir, manifest.getLong("created"), plans, expenses)
        } catch (e: Throwable) { dir.deleteRecursively(); throw e }
    }

    fun confirm(preview: Preview) {
        check(preview.directory.renameTo(File(context.noBackupFilesDir, "restore-pending"))) { "无法准备恢复，请重新打开应用" }
    }
}

// All paths share the app-private filesystem. Move files before opening Room or DataStore;
// the original-file inventory makes rollback repeatable even if the process dies mid-move.
fun finishPendingRestore(context: Context): String? {
    val root = context.noBackupFilesDir
    val pending = File(root, "restore-pending")
    val rollback = File(root, "restore-rollback")
    val done = File(root, "restore-done")
    if (!pending.exists()) {
        rollback.deleteRecursively()
        done.deleteRecursively()
        root.listFiles()?.filter { it.name.startsWith("inspect-") }?.forEach { it.deleteRecursively() }
        return null
    }
    val files = targets(context)
    val affected = files.toMutableMap()
    for ((name, file) in files) {
        val suffixes = if (name in tables) listOf("-wal", "-shm", "-journal") else listOf(".bak", ".new")
        suffixes.forEach { affected[name + it] = File(file.path + it) }
    }
    val inventory = File(rollback, "originals")
    fun move(source: File, target: File) {
        java.nio.file.Files.move(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    fun undo() {
        if (inventory.exists()) {
            val existing = inventory.readLines().toSet()
            for ((name, target) in affected) {
                val old = File(rollback, name)
                if (old.exists()) move(old, target)
                else if (name !in existing) java.nio.file.Files.deleteIfExists(target.toPath())
            }
        }
        // Retire the pending operation before deleting its recovery journal.
        move(pending, done)
        rollback.deleteRecursively()
        done.deleteRecursively()
    }
    if (inventory.exists()) {
        undo()
        return "恢复曾中断，已还原原有数据"
    }
    return try {
        done.deleteRecursively()
        rollback.mkdirs()
        val originals = affected.filterValues { it.exists() }
        val temp = File(rollback, "originals.tmp")
        temp.writeText(originals.keys.joinToString("\n"))
        move(temp, inventory)
        originals.forEach { (name, file) -> move(file, File(rollback, name)) }
        files.forEach { (name, target) ->
            target.parentFile!!.mkdirs()
            move(File(pending, name), target)
        }
        move(pending, done)
        rollback.deleteRecursively()
        done.deleteRecursively()
        "备份已恢复"
    } catch (e: Exception) {
        undo()
        "恢复失败，已保留原有数据"
    }
}
