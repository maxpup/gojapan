package com.gojapan.feature.kanalink.data.course

import android.content.res.AssetManager
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.KanaId
import com.gojapan.feature.kanalink.domain.model.KanaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface KanaCourseRepository {
    suspend fun getCourse(): List<KanaItem>
}

class AssetKanaCourseRepository(
    private val assets: AssetManager,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : KanaCourseRepository {
    override suspend fun getCourse(): List<KanaItem> {
        val primary = runCatching { read("course/kana_v1.json") }
        return primary.getOrElse { read("course/kana_baseline_v1.json") }.also(::validateCourse)
    }

    private fun read(path: String): List<KanaItem> = assets.open(path).bufferedReader().use { reader ->
        json.decodeFromString<CourseFile>(reader.readText()).items.map(CourseItem::toDomain)
    }
}

@Serializable
internal data class CourseFile(val version: Int, val items: List<CourseItem>)

@Serializable
internal data class CourseItem(
    val id: String,
    val order: Int,
    val group: KanaGroup,
    val row: String,
    val hiragana: String,
    val katakana: String,
    val romaji: String,
    val word: String,
    val meaningZhCn: String,
    val audioKey: String,
) {
    fun toDomain() = KanaItem(
        KanaId(id), order, group, row, hiragana, katakana, romaji, word, meaningZhCn, audioKey,
    )
}

fun validateCourse(items: List<KanaItem>) {
    require(items.size == 104) { "Expected 104 kana, got ${items.size}" }
    require(items.map { it.id }.distinct().size == 104) { "Kana IDs must be unique" }
    require(items.map { it.order }.sorted() == (0 until 104).toList()) { "Course order must be 0..103" }
    val counts = items.groupingBy { it.group }.eachCount()
    require(counts[KanaGroup.SEION] == 46)
    require(counts[KanaGroup.DAKUON] == 20)
    require(counts[KanaGroup.HANDAKUON] == 5)
    require(counts[KanaGroup.YOON] == 33)
    require(items.all { it.hiragana.isNotBlank() && it.katakana.isNotBlank() && it.romaji.isNotBlank() })
}
