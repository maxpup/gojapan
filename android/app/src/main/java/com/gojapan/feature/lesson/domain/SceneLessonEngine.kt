package com.gojapan.feature.lesson.domain

data class LessonEngineState(
    val questions: List<LessonQuestion>,
    val currentIndex: Int = 0,
    val selectedAnswer: String? = null,
    val lastAnswerCorrect: Boolean? = null,
    val evidence: Map<String, LessonItemEvidence> = emptyMap(),
    val initialCorrect: Int = 0,
    val initialAnswered: Int = 0,
    val challengeCorrect: Int = 0,
    val challengeAnswered: Int = 0,
    val completed: Boolean = false,
) {
    val currentQuestion: LessonQuestion? get() = questions.getOrNull(currentIndex)
    val initialAccuracy: Int get() = if (initialAnswered == 0) 0 else initialCorrect * 100 / initialAnswered
    val challengeAccuracy: Int get() = if (challengeAnswered == 0) 0 else challengeCorrect * 100 / challengeAnswered
    val passed: Boolean get() = completed && initialAccuracy >= 80 && challengeAccuracy >= 80
}

class SceneLessonEngine(private val lesson: SceneLesson) {
    fun start(): LessonEngineState = LessonEngineState(SceneQuestionFactory.initialQuestions(lesson))

    fun replayAudio(state: LessonEngineState): LessonEngineState {
        val question = state.currentQuestion ?: return state
        val old = state.evidence[question.expressionId] ?: LessonItemEvidence(question.expressionId)
        return state.copy(evidence = state.evidence + (question.expressionId to old.copy(replayCount = old.replayCount + 1)))
    }

    fun answer(state: LessonEngineState, answer: String): LessonEngineState {
        check(state.selectedAnswer == null && !state.completed)
        val question = checkNotNull(state.currentQuestion)
        val correct = answer == question.correctAnswer
        val old = state.evidence[question.expressionId] ?: LessonItemEvidence(question.expressionId)
        val first = old.firstTryCorrect ?: correct
        val updated = old.copy(
            firstTryCorrect = first,
            finalCorrect = correct,
            consecutiveErrors = if (correct) 0 else old.consecutiveErrors + 1,
            enteredRetry = old.enteredRetry || (!correct && !question.isRetry),
            recentResults = (old.recentResults + correct).takeLast(5),
        )
        return state.copy(
            selectedAnswer = answer,
            lastAnswerCorrect = correct,
            evidence = state.evidence + (question.expressionId to updated),
            initialCorrect = state.initialCorrect + if (!question.isRetry && !question.isChallenge && correct) 1 else 0,
            initialAnswered = state.initialAnswered + if (!question.isRetry && !question.isChallenge) 1 else 0,
            challengeCorrect = state.challengeCorrect + if (question.isChallenge && correct) 1 else 0,
            challengeAnswered = state.challengeAnswered + if (question.isChallenge) 1 else 0,
        )
    }

    fun advance(state: LessonEngineState): LessonEngineState {
        check(state.selectedAnswer != null)
        val question = checkNotNull(state.currentQuestion)
        var questions = state.questions
        if (state.lastAnswerCorrect == false && !question.isRetry) {
            questions = questions + SceneQuestionFactory.retryQuestion(lesson, question, retryNumber = 1)
        }
        val next = state.currentIndex + 1
        return state.copy(
            questions = questions,
            currentIndex = next,
            selectedAnswer = null,
            lastAnswerCorrect = null,
            completed = next >= questions.size,
        )
    }
}
