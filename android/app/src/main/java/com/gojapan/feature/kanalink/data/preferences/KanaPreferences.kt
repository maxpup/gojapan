package com.gojapan.feature.kanalink.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gojapan.feature.kanalink.domain.model.KanaGroup
import com.gojapan.feature.kanalink.domain.model.PairMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.gojapan.feature.kanalink.domain.model.KanaId

enum class KanaSkillDimension { READING, LISTENING, GLYPH }

data class KanaSkillEvidence(
    val kanaId: KanaId,
    val dimension: KanaSkillDimension,
    val attempts: Int = 0,
    val correct: Int = 0,
    val consecutiveCorrect: Int = 0,
    val mastery: Double = 0.0,
    val lastAnsweredAt: Long? = null,
    val nextReviewAt: Long? = null,
)

data class KanaSkillAnswer(val kanaId: KanaId, val dimension: KanaSkillDimension, val correct: Boolean)

data class KanaSettings(
    val preferredMode: PairMode = PairMode.HIRAGANA_ROMAJI,
    val selectedGroups: Set<KanaGroup> = KanaGroup.entries.toSet(),
    val voiceEnabled: Boolean = true,
    val soundEffectEnabled: Boolean = true,
    val reviewNotificationEnabled: Boolean = false,
    val reduceMotionEnabled: Boolean = false,
)

class KanaPreferences(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<KanaSettings> = dataStore.data.map { values ->
        KanaSettings(
            preferredMode = values[MODE]?.let { runCatching { PairMode.valueOf(it) }.getOrNull() }
                ?: PairMode.HIRAGANA_ROMAJI,
            selectedGroups = values[GROUPS]?.split(',')?.mapNotNull {
                runCatching { KanaGroup.valueOf(it) }.getOrNull()
            }?.toSet() ?: KanaGroup.entries.toSet(),
            voiceEnabled = values[VOICE] ?: true,
            soundEffectEnabled = values[SOUND] ?: true,
            reviewNotificationEnabled = values[NOTIFICATIONS] ?: false,
            reduceMotionEnabled = values[REDUCE_MOTION] ?: false,
        )
    }
    val skillEvidence: Flow<Map<Pair<KanaId, KanaSkillDimension>, KanaSkillEvidence>> = dataStore.data.map { values ->
        KanaSkillCodec.decode(values[SKILL_EVIDENCE].orEmpty()).associateBy { it.kanaId to it.dimension }
    }

    suspend fun update(settings: KanaSettings) {
        dataStore.edit { values ->
            values[MODE] = settings.preferredMode.name
            values[GROUPS] = settings.selectedGroups.joinToString(",") { it.name }
            values[VOICE] = settings.voiceEnabled
            values[SOUND] = settings.soundEffectEnabled
            values[NOTIFICATIONS] = settings.reviewNotificationEnabled
            values[REDUCE_MOTION] = settings.reduceMotionEnabled
        }
    }

    suspend fun recordSkillAnswers(answers: List<KanaSkillAnswer>, answeredAt: Long) {
        if (answers.isEmpty()) return
        dataStore.edit { values ->
            val evidence = KanaSkillCodec.decode(values[SKILL_EVIDENCE].orEmpty())
                .associateByTo(mutableMapOf()) { it.kanaId to it.dimension }
            answers.forEach { answer ->
                val key = answer.kanaId to answer.dimension
                val old = evidence[key] ?: KanaSkillEvidence(answer.kanaId, answer.dimension)
                evidence[key] = KanaSkillUpdater.apply(old, answer.correct, answeredAt)
            }
            values[SKILL_EVIDENCE] = KanaSkillCodec.encode(evidence.values)
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("preferred_mode")
        val GROUPS = stringPreferencesKey("selected_groups")
        val VOICE = booleanPreferencesKey("voice_enabled")
        val SOUND = booleanPreferencesKey("sound_effect_enabled")
        val NOTIFICATIONS = booleanPreferencesKey("review_notification_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion_enabled")
        val SKILL_EVIDENCE = stringPreferencesKey("kana_skill_evidence_v1")
    }
}

object KanaSkillUpdater {
    fun apply(old: KanaSkillEvidence, correct: Boolean, answeredAt: Long): KanaSkillEvidence {
        val streak = if (correct) old.consecutiveCorrect + 1 else 0
        val gain = if (correct) if (streak >= 2) 0.2 else 0.1 else -0.15
        return old.copy(
            attempts = old.attempts + 1,
            correct = old.correct + if (correct) 1 else 0,
            consecutiveCorrect = streak,
            mastery = (old.mastery + gain).coerceIn(0.0, 1.0),
            lastAnsweredAt = answeredAt,
            nextReviewAt = answeredAt + if (correct) 3 * 86_400_000L else 10 * 60_000L,
        )
    }
}

object KanaSkillCodec {
    fun encode(items: Collection<KanaSkillEvidence>): String = items
        .sortedWith(compareBy<KanaSkillEvidence> { it.kanaId.value }.thenBy { it.dimension.name })
        .joinToString("\n") {
            listOf(
                it.kanaId.value, it.dimension.name, it.attempts, it.correct, it.consecutiveCorrect,
                it.mastery, it.lastAnsweredAt ?: "", it.nextReviewAt ?: "",
            ).joinToString("|")
        }

    fun decode(raw: String): List<KanaSkillEvidence> = raw.lineSequence().mapNotNull { line ->
        val p = line.split('|')
        if (p.size != 8) return@mapNotNull null
        val id = runCatching { KanaId(p[0]) }.getOrNull() ?: return@mapNotNull null
        val dimension = runCatching { KanaSkillDimension.valueOf(p[1]) }.getOrNull() ?: return@mapNotNull null
        KanaSkillEvidence(
            id,
            dimension,
            p[2].toIntOrNull() ?: return@mapNotNull null,
            p[3].toIntOrNull() ?: return@mapNotNull null,
            p[4].toIntOrNull() ?: return@mapNotNull null,
            p[5].toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: return@mapNotNull null,
            p[6].takeIf(String::isNotEmpty)?.toLongOrNull(),
            p[7].takeIf(String::isNotEmpty)?.toLongOrNull(),
        )
    }.toList()
}
