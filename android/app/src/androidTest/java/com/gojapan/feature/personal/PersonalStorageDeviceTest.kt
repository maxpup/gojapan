package com.gojapan.feature.personal

import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalStorageDeviceTest {
    @Test fun atomicSnapshotSurvivesReopenAndInterruptedWrite() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val folder = File(base.cacheDir, "personal-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = folder }
        try {
            val snapshot = PersonalSnapshot(traces = listOf(ProcessingTrace("device-offline-trace", steps = listOf(ProcessingStep("本地写入", 1, 2, "成功", output = "持久化验证")))))
            AtomicPersonalStorage(context).write(snapshot)
            assertEquals(snapshot, AtomicPersonalStorage(context).read())
            // A process dying before finishWrite leaves .new; reopening must use committed state.
            File(folder, "personal-learning.json.new").writeText("{broken")
            assertEquals(snapshot, AtomicPersonalStorage(context).read())
            assertFalse(File(folder, "personal-learning.json.new").exists())
        } finally { folder.deleteRecursively() }
    }
}
