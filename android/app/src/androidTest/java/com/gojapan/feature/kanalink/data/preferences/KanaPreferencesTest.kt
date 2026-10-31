package com.gojapan.feature.kanalink.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.PairMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KanaPreferencesTest {
    @Test
    fun customSelectionAndSwitchesSurviveNewReadsIncludingEmptySelection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { context.preferencesDataStoreFile("test-${UUID.randomUUID()}") },
        )
        val preferences = KanaPreferences(store)
        val expected = KanaSettings(
            preferredMode = PairMode.HIRAGANA_KATAKANA,
            selectedGroups = emptySet(),
            voiceEnabled = false,
            soundEffectEnabled = false,
            reviewNotificationEnabled = true,
        )

        preferences.update(expected)

        assertEquals(expected, preferences.settings.first())
        preferences.update(expected.copy(selectedGroups = setOf(KanaGroup.YOON)))
        assertEquals(setOf(KanaGroup.YOON), preferences.settings.first().selectedGroups)
    }
}
