package com.gojapan.feature.kanalink.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun corruptDatabaseIsBackedUpAndRemoved() {
        val name = "recovery-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.outputStream().use { it.write("not-a-sqlite-database".toByteArray()) }

        assertTrue(DatabaseRecovery.recoverIfCorrupt(context, name))
        assertFalse(file.exists())
        assertTrue(
            context.filesDir.resolve("kana-link-recovery").listFiles().orEmpty()
                .any { it.name.startsWith("$name.") && it.name.endsWith(".corrupt") },
        )
    }
}
