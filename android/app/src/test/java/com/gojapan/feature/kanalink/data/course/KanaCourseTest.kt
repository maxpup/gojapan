package com.gojapan.feature.kanalink.data.course

import com.gojapan.feature.kanalink.domain.model.KanaGroup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KanaCourseTest {
    private val items = Json.decodeFromString<CourseFile>(
        File("src/main/assets/course/kana_v1.json").readText(),
    ).items.map(CourseItem::toDomain)

    @Test
    fun courseIsCompleteAndValid() {
        validateCourse(items)
        assertEquals(46, items.count { it.group == KanaGroup.SEION })
        assertEquals(20, items.count { it.group == KanaGroup.DAKUON })
        assertEquals(5, items.count { it.group == KanaGroup.HANDAKUON })
        assertEquals(33, items.count { it.group == KanaGroup.YOON })
    }

    @Test
    fun ambiguousRomajiHaveStableDistinctIds() {
        assertEquals("ji", items.single { it.hiragana == "じ" }.id.value)
        assertEquals("ji-di", items.single { it.hiragana == "ぢ" }.id.value)
        assertEquals("zu", items.single { it.hiragana == "ず" }.id.value)
        assertEquals("zu-du", items.single { it.hiragana == "づ" }.id.value)
        assertNotEquals(items.single { it.hiragana == "じ" }.id, items.single { it.hiragana == "ぢ" }.id)
    }

    @Test
    fun everyCourseItemHasExactlyOneBundledAudioFile() {
        val audioDirectory = File("src/main/res/raw")
        val expected = items.map { "${it.audioKey}.m4a" }.sorted()
        val actual = audioDirectory.listFiles()
            .orEmpty()
            .filter { it.extension == "m4a" }
            .onEach { assertTrue("Empty audio file: ${it.name}", it.length() > 0L) }
            .map(File::getName)
            .sorted()

        assertEquals(expected, actual)
    }
}
