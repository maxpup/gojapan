package com.gojapan.feature.search.domain

data class ExpressionToken(val start: Int, val end: Int, val surface: String, val reading: String, val meaning: String, val partOfSpeech: String)
data class GrammarNote(val fragment: String, val explanation: String, val example: String, val translation: String)

/** Editorial content, never inferred from arbitrary input. Offsets reference the unchanged Japanese text. */
object ExpressionLearning {
    private val phrases = mapOf(
        "toilet" to "トイレ/トイレ/洗手间/名词|は/わ/提示话题/助词|どこ/どこ/哪里/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "hospital" to "病院/びょういん/医院/名词|は/わ/提示话题/助词|どこ/どこ/哪里/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "police" to "交番/こうばん/派出所/名词|は/わ/提示话题/助词|どこ/どこ/哪里/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "pharmacy" to "薬局/やっきょく/药店/名词|は/わ/提示话题/助词|どこ/どこ/哪里/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "price" to "これ/これ/这个/代词|は/わ/提示话题/助词|いくら/いくら/多少钱/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "breakfast" to "朝食/ちょうしょく/早餐/名词|は/わ/提示话题/助词|何時/なんじ/几点/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "last_train" to "終電/しゅうでん/末班车/名词|は/わ/提示话题/助词|何時/なんじ/几点/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "recommend" to "おすすめ/おすすめ/推荐/名词|は/わ/提示话题/助词|何/なん/什么/疑问词|です/です/礼貌判断/助动词|か/か/疑问/助词",
        "menu" to "メニュー/メニュー/菜单/名词|を/お/标记宾语/助词|お願いします/おねがいします/拜托您/礼貌请求",
        "bill" to "お会計/おかいけい/结账/名词|を/お/标记宾语/助词|お願いします/おねがいします/拜托您/礼貌请求",
        "checkin" to "チェックイン/チェックイン/入住手续/名词|を/お/标记宾语/助词|お願いします/おねがいします/拜托您/礼貌请求",
        "checkout" to "チェックアウト/チェックアウト/退房手续/名词|を/お/标记宾语/助词|お願いします/おねがいします/拜托您/礼貌请求",
        "water" to "お水/おみず/水/名词|を/お/标记宾语/助词|お願いします/おねがいします/拜托您/礼貌请求",
        "chopsticks" to "箸/はし/筷子/名词|を/お/标记宾语/助词|ください/ください/请给我/请求表达",
        "receipt" to "レシート/レシート/小票/名词|を/お/标记宾语/助词|ください/ください/请给我/请求表达",
        "bag" to "袋/ふくろ/袋子/名词|を/お/标记宾语/助词|ください/ください/请给我/请求表达",
        "photo" to "写真/しゃしん/照片/名词|を/お/标记宾语/助词|撮って/とって/拍摄（て形）/动词|もらえます/もらえます/能请别人为我做/动词可能形|か/か/疑问/助词",
        "luggage" to "荷物/にもつ/行李/名词|を/お/标记宾语/助词|預かって/あずかって/保管（て形）/动词|もらえます/もらえます/能请别人为我做/动词可能形|か/か/疑问/助词",
        "help" to "助けて/たすけて/帮助（て形）/动词|ください/ください/请您/请求表达",
        "cash" to "現金/げんきん/现金/名词|で/で/以此方式/助词|払います/はらいます/支付/动词礼貌形",
        "card" to "カード/カード/信用卡/名词|は/わ/提示话题/助词|使えます/つかえます/能够使用/动词可能形|か/か/疑问/助词",
        "taxfree" to "免税/めんぜい/免税/名词|できます/できます/能够办理/动词可能表达|か/か/疑问/助词",
        "allergy" to "アレルギー/アレルギー/过敏/名词|が/が/标记存在的事物/助词|あります/あります/有/动词礼貌形",
        "lost" to "道/みち/道路/名词|に/に/标记涉及的对象/助词|迷いました/まよいました/迷路了/动词过去礼貌形",
        "pain" to "ここ/ここ/这里/代词|が/が/标记主语/助词|痛い/いたい/疼痛/形容词|です/です/礼貌表达/助动词",
        "one_person" to "一人/ひとり/一位/数量词|です/です/礼貌判断/助动词",
        "two_people" to "二人/ふたり/两位/数量词|です/です/礼貌判断/助动词",
        "delicious" to "おいしい/おいしい/好吃/形容词|です/です/礼貌表达/助动词",
    )

    fun tokens(expression: TravelExpression): List<ExpressionToken> {
        val data = phrases[expression.id] ?: return if (expression.politeness == "中性") {
            listOf(ExpressionToken(0, expression.japanese.length, expression.japanese, expression.kana, expression.chinese, "名词"))
        } else emptyList()
        var start = 0
        val result = data.split('|').map { entry ->
            val fields = entry.split('/')
            ExpressionToken(start, start + fields[0].length, fields[0], fields[1], fields[2], fields[3]).also { start = it.end }
        }
        return result.takeIf { it.joinToString("") { token -> token.surface } == expression.japanese }.orEmpty()
    }

    fun grammar(expression: TravelExpression): List<GrammarNote> {
        val text = expression.japanese
        if (tokens(expression).isEmpty()) return emptyList()
        return buildList {
            if (text.contains("は")) add(GrammarNote("は", "话题助词，读作 wa。提示接下来要说明或询问的对象。", "出口はどこですか", "出口在哪里？将话题替换为“出口”。"))
            if (text.contains("どこ")) add(GrammarNote("どこ", "询问地点的疑问词，表示“哪里”。", "駅はどこですか", "车站在哪里？用于询问车站位置。"))
            if (text.contains("ですか")) add(GrammarNote("ですか", "です使问句礼貌，句末的か表示疑问。", "トイレはどこですか", "洗手间在哪里？在公共场所礼貌问路。"))
            if (text.contains("を")) add(GrammarNote("を", "宾语助词，读作 o，标记请求或动作涉及的对象。", "お水をお願いします", "请给我水。把请求的对象替换成水。"))
            if (text.contains("お願いします")) add(GrammarNote("お願いします", "固定礼貌请求表达；前接“名词＋を”表示请对方提供或办理。", "メニューをお願いします", "请给我菜单。在餐厅提出请求。"))
            if (text.contains("ください")) add(GrammarNote("ください", "名词＋を＋ください表示索取；动词て形＋ください表示请求对方做某事。", "箸をください", "请给我筷子。用于索取物品。"))
            if (text.contains("もらえますか")) add(GrammarNote("もらえますか", "动词て形＋もらえますか，礼貌询问能否请对方为自己做某事。", "写真を撮ってもらえますか", "可以帮我拍照吗？撮って是撮る的て形。"))
        }
    }
}
