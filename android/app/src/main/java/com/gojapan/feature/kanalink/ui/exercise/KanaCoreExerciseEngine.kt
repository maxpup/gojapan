package com.gojapan.feature.kanalink.ui.exercise

import com.gojapan.feature.kanalink.data.preferences.KanaSkillAnswer
import com.gojapan.feature.kanalink.data.preferences.KanaSkillDimension
import com.gojapan.feature.kanalink.domain.model.KanaItem

data class KanaExerciseQuestion(
    val id: String,
    val target: KanaItem,
    val dimension: KanaSkillDimension,
    val prompt: String,
    val options: List<String>,
    val correctAnswer: String,
)

data class KanaExerciseState(
    val questions: List<KanaExerciseQuestion>,
    val index: Int = 0,
    val selectedAnswer: String? = null,
    val answers: List<KanaSkillAnswer> = emptyList(),
    val completed: Boolean = false,
) {
    val current: KanaExerciseQuestion? get() = questions.getOrNull(index)
    val correctCount: Int get() = answers.count { it.correct }
    val accuracy: Int get() = if (answers.isEmpty()) 0 else correctCount * 100 / answers.size
    val passed: Boolean get() = completed && accuracy >= 80
}

class KanaCoreExerciseEngine(private val questions: List<KanaExerciseQuestion>) {
    init { require(questions.isNotEmpty()) }
    fun start() = KanaExerciseState(questions)

    fun answer(state: KanaExerciseState, answer: String): KanaExerciseState {
        check(state.selectedAnswer == null && !state.completed)
        val question = checkNotNull(state.current)
        require(answer in question.options)
        return state.copy(
            selectedAnswer = answer,
            answers = state.answers + KanaSkillAnswer(question.target.id, question.dimension, answer == question.correctAnswer),
        )
    }

    fun advance(state: KanaExerciseState): KanaExerciseState {
        check(state.selectedAnswer != null)
        val next = state.index + 1
        return state.copy(index = next, selectedAnswer = null, completed = next >= state.questions.size)
    }
}

object KanaCoreQuestionFactory {
    private val confusingIds = listOf("shi", "tsu", "so", "n", "a", "o", "sa", "ki")

    fun create(course: List<KanaItem>): List<KanaExerciseQuestion> {
        require(course.size >= 12)
        val byId = course.associateBy { it.id.value }
        val glyphTargets = confusingIds.mapNotNull(byId::get).take(4)
        require(glyphTargets.size == 4)
        val readingTargets = course.take(4)
        val listeningTargets = course.drop(4).take(4)
        return buildList {
            readingTargets.forEachIndexed { index, item ->
                add(
                    question(
                        item, KanaSkillDimension.READING, "${item.hiragana} 的读音是？",
                        options(item.romaji, course.map { it.romaji }, "reading-$index"), item.romaji,
                    ),
                )
            }
            listeningTargets.forEachIndexed { index, item ->
                add(
                    question(
                        item, KanaSkillDimension.LISTENING, "播放发音后选择对应的平假名",
                        options(item.hiragana, course.map { it.hiragana }, "listening-$index"), item.hiragana,
                    ),
                )
            }
            glyphTargets.forEachIndexed { index, item ->
                val pool = confusingIds.mapNotNull(byId::get).map { it.katakana }
                add(
                    question(
                        item, KanaSkillDimension.GLYPH, "选择与 ${item.romaji} 对应的片假名",
                        options(item.katakana, pool, "glyph-$index"), item.katakana,
                    ),
                )
            }
        }.interleaveDimensions()
    }

    private fun question(
        item: KanaItem,
        dimension: KanaSkillDimension,
        prompt: String,
        options: List<String>,
        answer: String,
    ) = KanaExerciseQuestion("${dimension.name}-${item.id.value}", item, dimension, prompt, options, answer)

    private fun options(answer: String, pool: List<String>, seed: String): List<String> =
        (pool.asSequence().filter { it != answer }.distinct().take(3).toList() + answer)
            .sortedBy { it.hashCode() xor seed.hashCode() }

    private fun List<KanaExerciseQuestion>.interleaveDimensions(): List<KanaExerciseQuestion> = buildList {
        repeat(4) { index ->
            KanaSkillDimension.entries.forEach { dimension -> add(this@interleaveDimensions.filter { it.dimension == dimension }[index]) }
        }
    }
}
