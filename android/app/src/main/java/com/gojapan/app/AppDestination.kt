package com.gojapan.app

enum class AppDestination(val label: String, val iconLabel: String) {
    EXPENSE("记账", "账"),
    HOME("首页", "⌂"),
    LEARN_JAPANESE("语言", "语"),
    REVIEW("复习", "↻"),
    TRIPS("行程", "旅"),
    SEARCH("搜索", "⌕"),
    FAVORITES("收藏", "★");
    companion object { val tabs = listOf(TRIPS, LEARN_JAPANESE, EXPENSE) }
}
