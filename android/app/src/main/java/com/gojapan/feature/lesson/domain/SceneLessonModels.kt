package com.gojapan.feature.lesson.domain

import com.gojapan.feature.search.domain.TravelExpression
import com.gojapan.feature.search.domain.TravelExpressionSearch

enum class TravelScene(val label: String, val icon: String) {
    TRANSPORT("入境与交通", "🚉"),
    HOTEL("住宿", "🏨"),
    DINING("餐饮", "🍜"),
    SHOPPING("购物", "🛍"),
    SIGHTSEEING_EMERGENCY("观光与应急", "🗼"),
}

enum class LessonQuestionType(val label: String) {
    JAPANESE_TO_CHINESE("看词选义"),
    LISTENING("听音选择"),
    CHINESE_JAPANESE_MATCH("中日配对"),
    SCENE_RESPONSE("场景应答"),
}

data class SceneLesson(
    val id: String,
    val scene: TravelScene,
    val title: String,
    val mission: String,
    val expressions: List<TravelExpression>,
)

@kotlinx.serialization.Serializable
data class LessonQuestion(
    val id: String,
    val expressionId: String,
    val type: LessonQuestionType,
    val prompt: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val isRetry: Boolean = false,
    val isChallenge: Boolean = false,
)

@kotlinx.serialization.Serializable
data class LessonItemEvidence(
    val expressionId: String,
    val firstTryCorrect: Boolean? = null,
    val finalCorrect: Boolean = false,
    val replayCount: Int = 0,
    val consecutiveErrors: Int = 0,
    val enteredRetry: Boolean = false,
    val recentResults: List<Boolean> = emptyList(),
)

object SceneLessonCatalog {
    private val all = TravelExpressionSearch.reviewedExpressions.associateBy { it.id }
    private fun lesson(
        id: String,
        scene: TravelScene,
        title: String,
        mission: String,
        vararg expressionIds: String,
    ) = SceneLesson(id, scene, title, mission, expressionIds.map { checkNotNull(all[it]) { "Unknown expression $it" } })

    private val transportLessons = listOf(
        lesson("transport-station-basics", TravelScene.TRANSPORT, "车站问路与乘车", "找到出口、站台，并询问路线和换乘。", "station", "exit", "platform", "transfer", "directions", "ticket"),
        lesson("transport-airport-entry", TravelScene.TRANSPORT, "机场入境", "完成入境审查并找到机场交通。", "airport", "immigration", "exit", "bus", "taxi", "directions"),
        lesson("transport-bus-taxi", TravelScene.TRANSPORT, "巴士与出租车", "选择巴士或出租车前往目的地。", "bus", "taxi", "station", "ticket", "left", "right"),
        lesson("transport-find-way", TravelScene.TRANSPORT, "方向与出入口", "区分入口出口，并听懂左右方向。", "entrance", "exit", "left", "right", "directions", "station"),
        lesson("transport-last-train", TravelScene.TRANSPORT, "换乘与末班车", "确认换乘、站台和末班车时间。", "transfer", "platform", "last_train", "ticket", "station", "directions"),
    )
    private val hotelLessons = listOf(
        lesson("hotel-checkin", TravelScene.HOTEL, "预约与入住", "出示预约和护照，完成酒店入住。", "reservation", "passport", "checkin", "room", "room_key", "breakfast"),
        lesson("hotel-luggage", TravelScene.HOTEL, "行李寄存", "入住前后安全寄存行李。", "luggage", "reservation", "checkin", "checkout", "room_key", "passport"),
        lesson("hotel-facilities", TravelScene.HOTEL, "酒店设施咨询", "询问早餐、网络和客房设施。", "breakfast", "wifi", "room", "room_key", "hot_water", "checkin"),
        lesson("hotel-room-request", TravelScene.HOTEL, "客房需求", "提出无烟房、热水和网络需求。", "nonsmoking", "hot_water", "wifi", "room", "room_key", "luggage"),
        lesson("hotel-checkout", TravelScene.HOTEL, "退房与离店", "办理退房并安排离店行李。", "checkout", "room_key", "luggage", "passport", "reservation", "taxi"),
    )
    private val diningLessons = listOf(
        lesson("dining-enter", TravelScene.DINING, "入店与人数", "说明用餐人数并查看菜单。", "one_person", "two_people", "menu", "recommend", "water", "takeout"),
        lesson("dining-order", TravelScene.DINING, "点餐与推荐", "询问推荐并完成基础点餐。", "menu", "recommend", "water", "chopsticks", "delicious", "bill"),
        lesson("dining-diet", TravelScene.DINING, "忌口与过敏", "清楚说明过敏、素食和辣度需求。", "allergy", "vegetarian", "no_spicy", "menu", "water", "recommend"),
        lesson("dining-takeout", TravelScene.DINING, "堂食与外带", "说明人数、外带需求并索取餐具。", "takeout", "one_person", "two_people", "chopsticks", "water", "bill"),
        lesson("dining-payment", TravelScene.DINING, "结账与付款", "请求结账并选择现金或刷卡。", "bill", "cash", "card", "delicious", "receipt", "takeout"),
    )
    private val shoppingLessons = listOf(
        lesson("shopping-price", TravelScene.SHOPPING, "询价与挑选", "询问价格、尺码并礼貌浏览。", "price", "size", "smaller", "only_looking", "fitting", "bag"),
        lesson("shopping-fitting", TravelScene.SHOPPING, "试穿与尺码", "试穿并寻找更大或更小尺码。", "fitting", "size", "smaller", "price", "only_looking", "bag"),
        lesson("shopping-payment", TravelScene.SHOPPING, "现金与刷卡", "确认付款方式并索取小票。", "cash", "card", "receipt", "price", "bag", "taxfree"),
        lesson("shopping-taxfree", TravelScene.SHOPPING, "免税购物", "询问免税、付款和收据。", "taxfree", "passport", "cash", "card", "receipt", "price"),
        lesson("shopping-return", TravelScene.SHOPPING, "退换与包装", "处理退货、尺码和购物袋需求。", "refund", "receipt", "bag", "size", "smaller", "card"),
    )
    private val emergencyLessons = listOf(
        lesson("sightseeing-ticket", TravelScene.SIGHTSEEING_EMERGENCY, "景点购票", "购买门票并确认开放时间。", "admission", "opening_hours", "photo", "toilet", "price", "directions"),
        lesson("sightseeing-photo", TravelScene.SIGHTSEEING_EMERGENCY, "观光与拍照", "请人拍照并寻找景点设施。", "photo", "opening_hours", "toilet", "directions", "admission", "help"),
        lesson("emergency-lost", TravelScene.SIGHTSEEING_EMERGENCY, "迷路与失物", "迷路或遗失物品时寻求帮助。", "lost", "lost_item", "police", "help", "directions", "station"),
        lesson("emergency-medical", TravelScene.SIGHTSEEING_EMERGENCY, "身体不适与买药", "说明疼痛并寻找医院或药店。", "pain", "hospital", "pharmacy", "help", "toilet", "taxi"),
        lesson("emergency-urgent", TravelScene.SIGHTSEEING_EMERGENCY, "紧急求助", "在紧急情况下报警或呼叫救护车。", "ambulance", "help", "police", "hospital", "pain", "lost_item"),
    )

    val scenes: Map<TravelScene, List<SceneLesson>> = mapOf(
        TravelScene.TRANSPORT to transportLessons,
        TravelScene.HOTEL to hotelLessons,
        TravelScene.DINING to diningLessons,
        TravelScene.SHOPPING to shoppingLessons,
        TravelScene.SIGHTSEEING_EMERGENCY to emergencyLessons,
    )
    val allLessons: List<SceneLesson> = TravelScene.entries.flatMap { scenes.getValue(it) }
    val transport: SceneLesson = transportLessons.first()
}

object SceneQuestionFactory {
    fun initialQuestions(lesson: SceneLesson): List<LessonQuestion> {
        val types = listOf(
            LessonQuestionType.JAPANESE_TO_CHINESE,
            LessonQuestionType.LISTENING,
            LessonQuestionType.CHINESE_JAPANESE_MATCH,
            LessonQuestionType.SCENE_RESPONSE,
        )
        return List(12) { index ->
            val expression = lesson.expressions[index % lesson.expressions.size]
            create(
                lesson,
                expression,
                types[index % types.size],
                occurrence = index / lesson.expressions.size,
                isChallenge = index >= 8,
            )
        }
    }

    fun retryQuestion(lesson: SceneLesson, original: LessonQuestion, retryNumber: Int): LessonQuestion {
        val expression = lesson.expressions.first { it.id == original.expressionId }
        val nextType = LessonQuestionType.entries[(original.type.ordinal + retryNumber) % LessonQuestionType.entries.size]
        return create(lesson, expression, nextType, occurrence = retryNumber + 2, isRetry = true)
    }

    private fun create(
        lesson: SceneLesson,
        expression: TravelExpression,
        type: LessonQuestionType,
        occurrence: Int,
        isRetry: Boolean = false,
        isChallenge: Boolean = false,
    ): LessonQuestion {
        val distractors = (lesson.expressions + TravelExpressionSearch.reviewedExpressions)
            .distinctBy { it.id }
            .filter { it.id != expression.id }
        val japaneseOptions = stableOptions(expression.japanese, distractors.map { it.japanese }, expression.id + occurrence)
        val chineseOptions = stableOptions(expression.chinese, distractors.map { it.chinese }, expression.id + occurrence)
        val (prompt, options, answer) = when (type) {
            LessonQuestionType.JAPANESE_TO_CHINESE -> Triple(expression.japanese, chineseOptions, expression.chinese)
            LessonQuestionType.LISTENING -> Triple("点击播放，然后选择听到内容的中文含义", chineseOptions, expression.chinese)
            LessonQuestionType.CHINESE_JAPANESE_MATCH -> Triple("为“${expression.chinese}”匹配日语", japaneseOptions, expression.japanese)
            LessonQuestionType.SCENE_RESPONSE -> Triple(scenePrompt(expression), japaneseOptions, expression.japanese)
        }
        return LessonQuestion(
            id = "${expression.id}-${type.name}-$occurrence-${if (isRetry) "retry" else "main"}",
            expressionId = expression.id,
            type = type,
            prompt = prompt,
            options = options,
            correctAnswer = answer,
            explanation = "${expression.japanese}（${expression.kana}）表示“${expression.chinese}”。",
            isRetry = isRetry,
            isChallenge = isChallenge,
        )
    }

    private fun stableOptions(answer: String, distractors: List<String>, seed: String): List<String> =
        (distractors.distinct().take(3) + answer).sortedBy { it.hashCode() xor seed.hashCode() }

    private fun scenePrompt(expression: TravelExpression): String = when (expression.id) {
        "directions" -> "你不知道目的地怎么走，应该说："
        "ticket" -> "你需要买车票，应选择与车票有关的词："
        "transfer" -> "你想确认换乘信息，应选择："
        "exit" -> "你正在寻找车站出口，应选择："
        "platform" -> "你正在寻找乘车站台，应选择："
        else -> "在${expression.scene}场景中，你想表达“${expression.chinese}”，应选择："
    }
}
