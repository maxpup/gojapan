package com.gojapan.feature.lesson.domain

import com.gojapan.feature.search.domain.TravelExpression

internal data class LessonWord(val text: String, val reading: String, val meaning: String)

// Fixed lesson content. Each entry contains the written form, reading, and contextual gloss.
internal object LessonWords {
    private const val topic = "は/わ/主题助词"
    private const val objectMarker = "を/お/宾语助词"
    private const val subject = "が/が/主语助词"
    private const val question = "か/か/疑问助词"
    private const val polite = "です/です/礼貌句尾"
    private const val request = "お願いします/おねがいします/拜托了"
    private const val please = "ください/ください/请"
    private const val where = "どこ/どこ/哪里"
    private const val time = "何時/なんじ/几点"
    private const val have = "あります/あります/有"
    private const val size = "サイズ/サイズ/尺码"
    private const val more = "もっと/もっと/更"
    private const val can = "できます/できます/可以"
    private const val favor = "もらえます/もらえます/能请对方帮忙"
    private const val by = "で/で/表示方式"

    private val phrases = mapOf(
        "directions" to "どう/どう/怎么 行けば/いけば/如果去 いい/いい/好 $polite $question",
        "checkin" to "チェックイン/チェックイン/入住 $objectMarker $request",
        "checkout" to "チェックアウト/チェックアウト/退房 $objectMarker $request",
        "luggage" to "荷物/にもつ/行李 $objectMarker 預かって/あずかって/代为保管 $favor $question",
        "breakfast" to "朝食/ちょうしょく/早餐 $topic $time $polite $question",
        "last_train" to "終電/しゅうでん/末班车 $topic $time $polite $question",
        "menu" to "メニュー/メニュー/菜单 $objectMarker $request",
        "recommend" to "おすすめ/おすすめ/推荐 $topic 何/なん/什么 $polite $question",
        "bill" to "お会計/おかいけい/结账 $objectMarker $request",
        "allergy" to "アレルギー/アレルギー/过敏 $subject $have",
        "takeout" to "持ち帰り/もちかえり/外带 $by $request",
        "price" to "これ/これ/这个 $topic いくら/いくら/多少钱 $polite $question",
        "size" to "$more 大きい/おおきい/大的 $size $topic $have $question",
        "smaller" to "$more 小さい/ちいさい/小的 $size $topic $have $question",
        "taxfree" to "免税/めんぜい/免税 $can $question",
        "refund" to "返品/へんぴん/退货 $can $question",
        "toilet" to "トイレ/トイレ/洗手间 $topic $where $polite $question",
        "hospital" to "病院/びょういん/医院 $topic $where $polite $question",
        "police" to "交番/こうばん/警察岗亭 $topic $where $polite $question",
        "pharmacy" to "薬局/やっきょく/药店 $topic $where $polite $question",
        "help" to "助けて/たすけて/帮助 $please",
        "photo" to "写真/しゃしん/照片 $objectMarker 撮って/とって/拍摄 $favor $question",
        "reservation" to "予約/よやく/预订 しています/しています/已办理",
        "wifi" to "Wi-Fi/ワイファイ/无线网络 $topic $have $question",
        "nonsmoking" to "禁煙/きんえん/禁烟 の/の/的 部屋/へや/房间 $objectMarker $request",
        "hot_water" to "お湯/おゆ/热水 $subject 出ません/でません/出不来",
        "water" to "お水/おみず/水 $objectMarker $request",
        "one_person" to "一人/ひとり/一位 $polite",
        "two_people" to "二人/ふたり/两位 $polite",
        "vegetarian" to "肉/にく/肉 $objectMarker 食べません/たべません/不吃",
        "no_spicy" to "辛く/からく/辣 しないで/しないで/不要弄成 $please",
        "delicious" to "おいしい/おいしい/好吃 $polite",
        "chopsticks" to "箸/はし/筷子 $objectMarker $please",
        "cash" to "現金/げんきん/现金 $by 払います/はらいます/付款",
        "card" to "カード/カード/卡 $topic 使えます/つかえます/能用 $question",
        "fitting" to "試着/しちゃく/试穿 して/して/做 も/も/即使 いい/いい/可以 $polite $question",
        "receipt" to "レシート/レシート/小票 $objectMarker $please",
        "bag" to "袋/ふくろ/袋子 $objectMarker $please",
        "only_looking" to "見ている/みている/在看 だけ/だけ/只是 $polite",
        "opening_hours" to "$time まで/まで/到…为止 開いています/あいています/开着 $question",
        "admission" to "入場券/にゅうじょうけん/门票 $objectMarker 一枚/いちまい/一张 $please",
        "lost" to "道/みち/路 に/に/表示迷失的场所 迷いました/まよいました/迷路了",
        "pain" to "ここ/ここ/这里 $subject 痛い/いたい/疼 $polite",
        "lost_item" to "財布/さいふ/钱包 $objectMarker なくしました/なくしました/弄丢了",
        "ambulance" to "救急車/きゅうきゅうしゃ/救护车 $objectMarker 呼んで/よんで/叫来 $please",
        "immigration" to "入国/にゅうこく/入境 審査/しんさ/审查",
        "room_key" to "ルーム/ルーム/房间 キー/キー/钥匙",
    ).mapValues { (_, phrase) ->
        phrase.split(' ').map { entry ->
            val (text, reading, meaning) = entry.split('/')
            LessonWord(text, reading, meaning)
        }
    }

    fun forExpression(expression: TravelExpression): List<LessonWord> =
        phrases[expression.id]?.takeIf { words -> words.joinToString("") { it.text } == expression.japanese }
            ?: listOf(LessonWord(expression.japanese, expression.kana, expression.chinese))
}
