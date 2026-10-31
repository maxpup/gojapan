package com.gojapan

import android.app.Application
import android.widget.Toast
import com.gojapan.feature.kanalink.data.local.DatabaseRecovery
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GoJapanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (DatabaseRecovery.recoverIfCorrupt(this)) {
            Toast.makeText(this, "学习记录异常，已保留副本并恢复为空历史", Toast.LENGTH_LONG).show()
        }
    }
}
