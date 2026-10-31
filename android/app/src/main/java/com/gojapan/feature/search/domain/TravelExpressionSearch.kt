package com.gojapan.feature.search.domain

object TravelExpressionSearch {
    val reviewedExpressions = listOf(
        TravelExpression("station", "駅", "えき", "eki", "车站", "交通", "中性", setOf("火车站", "地铁站")),
        TravelExpression("exit", "出口", "でぐち", "deguchi", "出口", "交通", "中性", setOf("出站口", "出去")),
        TravelExpression("platform", "ホーム", "ホーム", "hoomu", "站台", "交通", "中性", setOf("月台", "乘车站台")),
        TravelExpression("transfer", "乗り換え", "のりかえ", "norikae", "换乘", "交通", "中性", setOf("转车", "换车")),
        TravelExpression("directions", "どう行けばいいですか", "どういけばいいですか", "dou ikeba ii desu ka", "怎么走？", "交通", "礼貌", setOf("怎么去", "如何走", "问路")),
        TravelExpression("ticket", "切符", "きっぷ", "kippu", "车票", "交通", "中性", setOf("票", "买票")),
        TravelExpression("checkin", "チェックインをお願いします", "チェックインをおねがいします", "chekkuin o onegai shimasu", "请帮我办理入住", "住宿", "礼貌", setOf("入住", "办理入住", "登记入住")),
        TravelExpression("luggage", "荷物を預かってもらえますか", "にもつをあずかってもらえますか", "nimotsu o azukatte moraemasu ka", "可以寄存行李吗？", "住宿", "礼貌", setOf("寄存行李", "存包", "行李寄存")),
        TravelExpression("breakfast", "朝食は何時ですか", "ちょうしょくはなんじですか", "choushoku wa nanji desu ka", "早餐几点开始？", "住宿", "礼貌", setOf("早餐时间", "几点吃早餐")),
        TravelExpression("menu", "メニューをお願いします", "メニューをおねがいします", "menyuu o onegai shimasu", "请给我菜单", "餐饮", "礼貌", setOf("菜单", "看菜单")),
        TravelExpression("recommend", "おすすめは何ですか", "おすすめはなんですか", "osusume wa nan desu ka", "有什么推荐？", "餐饮", "礼貌", setOf("推荐菜", "招牌菜", "推荐")),
        TravelExpression("bill", "お会計をお願いします", "おかいけいをおねがいします", "okaikei o onegai shimasu", "请结账", "餐饮", "礼貌", setOf("买单", "结账", "付款")),
        TravelExpression("allergy", "アレルギーがあります", "アレルギーがあります", "arerugii ga arimasu", "我有食物过敏", "餐饮", "礼貌", setOf("过敏", "食物过敏", "忌口")),
        TravelExpression("takeout", "持ち帰りでお願いします", "もちかえりでおねがいします", "mochikaeri de onegai shimasu", "请打包带走", "餐饮", "礼貌", setOf("外带", "打包", "带走")),
        TravelExpression("price", "これはいくらですか", "これはいくらですか", "kore wa ikura desu ka", "这个多少钱？", "购物", "礼貌", setOf("价格", "多少钱", "询价")),
        TravelExpression("size", "もっと大きいサイズはありますか", "もっとおおきいサイズはありますか", "motto ookii saizu wa arimasu ka", "有更大号吗？", "购物", "礼貌", setOf("大一码", "尺码", "大号")),
        TravelExpression("taxfree", "免税できますか", "めんぜいできますか", "menzei dekimasu ka", "可以免税吗？", "购物", "礼貌", setOf("退税", "免税", "tax free")),
        TravelExpression("toilet", "トイレはどこですか", "トイレはどこですか", "toire wa doko desu ka", "洗手间在哪里？", "应急", "礼貌", setOf("厕所", "卫生间", "洗手间")),
        TravelExpression("help", "助けてください", "たすけてください", "tasukete kudasai", "请帮帮我", "应急", "礼貌", setOf("救命", "求助", "帮帮我")),
        TravelExpression("hospital", "病院はどこですか", "びょういんはどこですか", "byouin wa doko desu ka", "医院在哪里？", "应急", "礼貌", setOf("医院", "看病", "诊所")),
        TravelExpression("photo", "写真を撮ってもらえますか", "しゃしんをとってもらえますか", "shashin o totte moraemasu ka", "可以帮我拍照吗？", "观光", "礼貌", setOf("拍照", "帮我拍照", "合影")),
        TravelExpression("airport", "空港", "くうこう", "kuukou", "机场", "交通", "中性", setOf("飞机场", "航空站")),
        TravelExpression("immigration", "入国審査", "にゅうこくしんさ", "nyuukoku shinsa", "入境审查", "交通", "中性", setOf("入境", "海关入境")),
        TravelExpression("bus", "バス", "バス", "basu", "公交车", "交通", "中性", setOf("巴士", "公交")),
        TravelExpression("taxi", "タクシー", "タクシー", "takushii", "出租车", "交通", "中性", setOf("打车", "的士")),
        TravelExpression("entrance", "入口", "いりぐち", "iriguchi", "入口", "交通", "中性", setOf("进口", "进入")),
        TravelExpression("left", "左", "ひだり", "hidari", "左边", "交通", "中性", setOf("向左", "左面")),
        TravelExpression("right", "右", "みぎ", "migi", "右边", "交通", "中性", setOf("向右", "右面")),
        TravelExpression("last_train", "終電は何時ですか", "しゅうでんはなんじですか", "shuuden wa nanji desu ka", "末班车几点？", "交通", "礼貌", setOf("末班车", "最后一班车")),
        TravelExpression("reservation", "予約しています", "よやくしています", "yoyaku shiteimasu", "我有预订", "住宿", "礼貌", setOf("预订", "预约酒店", "订房")),
        TravelExpression("passport", "パスポート", "パスポート", "pasupooto", "护照", "住宿", "中性", setOf("passport")),
        TravelExpression("room", "部屋", "へや", "heya", "房间", "住宿", "中性", setOf("客房", "酒店房间")),
        TravelExpression("wifi", "Wi-Fiはありますか", "ワイファイはありますか", "waifai wa arimasu ka", "有无线网络吗？", "住宿", "礼貌", setOf("无线网", "网络", "wifi")),
        TravelExpression("room_key", "ルームキー", "ルームキー", "ruumu kii", "房卡", "住宿", "中性", setOf("钥匙", "酒店钥匙")),
        TravelExpression("checkout", "チェックアウトをお願いします", "チェックアウトをおねがいします", "chekkuauto o onegai shimasu", "请帮我退房", "住宿", "礼貌", setOf("退房", "办理退房")),
        TravelExpression("nonsmoking", "禁煙の部屋をお願いします", "きんえんのへやをおねがいします", "kinen no heya o onegai shimasu", "请给我无烟房", "住宿", "礼貌", setOf("无烟房", "禁烟房")),
        TravelExpression("hot_water", "お湯が出ません", "おゆがでません", "oyu ga demasen", "没有热水", "住宿", "礼貌", setOf("热水坏了", "没热水")),
        TravelExpression("water", "お水をお願いします", "おみずをおねがいします", "omizu o onegai shimasu", "请给我水", "餐饮", "礼貌", setOf("水", "要水")),
        TravelExpression("one_person", "一人です", "ひとりです", "hitori desu", "一位", "餐饮", "礼貌", setOf("一个人", "一人用餐")),
        TravelExpression("two_people", "二人です", "ふたりです", "futari desu", "两位", "餐饮", "礼貌", setOf("两个人", "二人用餐")),
        TravelExpression("vegetarian", "肉を食べません", "にくをたべません", "niku o tabemasen", "我不吃肉", "餐饮", "礼貌", setOf("素食", "不吃肉")),
        TravelExpression("no_spicy", "辛くしないでください", "からくしないでください", "karaku shinaide kudasai", "请不要做辣", "餐饮", "礼貌", setOf("不要辣", "不辣", "免辣")),
        TravelExpression("delicious", "おいしいです", "おいしいです", "oishii desu", "很好吃", "餐饮", "礼貌", setOf("好吃", "美味")),
        TravelExpression("chopsticks", "箸をください", "はしをください", "hashi o kudasai", "请给我筷子", "餐饮", "礼貌", setOf("筷子", "要筷子")),
        TravelExpression("cash", "現金で払います", "げんきんではらいます", "genkin de haraimasu", "我用现金付款", "购物", "礼貌", setOf("现金", "付现金")),
        TravelExpression("card", "カードは使えますか", "カードはつかえますか", "kaado wa tsukaemasu ka", "可以刷卡吗？", "购物", "礼貌", setOf("信用卡", "刷卡", "银行卡")),
        TravelExpression("fitting", "試着してもいいですか", "しちゃくしてもいいですか", "shichaku shite mo ii desu ka", "可以试穿吗？", "购物", "礼貌", setOf("试穿", "试衣服")),
        TravelExpression("smaller", "もっと小さいサイズはありますか", "もっとちいさいサイズはありますか", "motto chiisai saizu wa arimasu ka", "有更小号吗？", "购物", "礼貌", setOf("小一码", "小号")),
        TravelExpression("receipt", "レシートをください", "レシートをください", "reshiito o kudasai", "请给我小票", "购物", "礼貌", setOf("收据", "发票", "小票")),
        TravelExpression("bag", "袋をください", "ふくろをください", "fukuro o kudasai", "请给我袋子", "购物", "礼貌", setOf("购物袋", "袋子")),
        TravelExpression("only_looking", "見ているだけです", "みているだけです", "miteiru dake desu", "我只是看看", "购物", "礼貌", setOf("随便看看", "只看看")),
        TravelExpression("refund", "返品できますか", "へんぴんできますか", "henpin dekimasu ka", "可以退货吗？", "购物", "礼貌", setOf("退货", "退款")),
        TravelExpression("opening_hours", "何時まで開いていますか", "なんじまであいていますか", "nanji made aiteimasu ka", "营业到几点？", "观光", "礼貌", setOf("营业时间", "几点关门")),
        TravelExpression("admission", "入場券を一枚ください", "にゅうじょうけんをいちまいください", "nyuujouken o ichimai kudasai", "请给我一张门票", "观光", "礼貌", setOf("门票", "一张票")),
        TravelExpression("lost", "道に迷いました", "みちにまよいました", "michi ni mayoimashita", "我迷路了", "应急", "礼貌", setOf("迷路", "走丢")),
        TravelExpression("police", "交番はどこですか", "こうばんはどこですか", "kouban wa doko desu ka", "派出所在哪里？", "应急", "礼貌", setOf("警察局", "派出所", "找警察")),
        TravelExpression("pain", "ここが痛いです", "ここがいたいです", "koko ga itai desu", "这里疼", "应急", "礼貌", setOf("疼痛", "这里痛", "不舒服")),
        TravelExpression("pharmacy", "薬局はどこですか", "やっきょくはどこですか", "yakkyoku wa doko desu ka", "药店在哪里？", "应急", "礼貌", setOf("药房", "买药", "药店")),
        TravelExpression("lost_item", "財布をなくしました", "さいふをなくしました", "saifu o nakushimashita", "我的钱包丢了", "应急", "礼貌", setOf("丢钱包", "失物", "东西丢了")),
        TravelExpression("ambulance", "救急車を呼んでください", "きゅうきゅうしゃをよんでください", "kyuukyuusha o yonde kudasai", "请叫救护车", "应急", "礼貌", setOf("救护车", "叫救护车")),
    )

    fun search(rawQuery: String): List<TravelExpression> {
        val query = normalize(rawQuery)
        if (query.isEmpty()) return emptyList()
        return reviewedExpressions
            .mapNotNull { expression ->
                val fields = listOf(expression.chinese, expression.japanese, expression.kana, expression.romaji) + expression.aliases
                val normalized = fields.map(::normalize)
                val score = when {
                    normalized.any { it == query } -> 0
                    normalize(expression.chinese).contains(query) -> 1
                    normalized.any { it.contains(query) || (it.length >= 2 && query.contains(it)) } -> 2
                    else -> return@mapNotNull null
                }
                expression to score
            }
            .sortedWith(compareBy<Pair<TravelExpression, Int>> { it.second }.thenBy { it.first.id })
            .map { it.first }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("[\\s？?。！!]"), "")
}
