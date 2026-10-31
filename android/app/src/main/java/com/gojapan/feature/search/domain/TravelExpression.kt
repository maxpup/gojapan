package com.gojapan.feature.search.domain

data class TravelExpression(
    val id: String,
    val japanese: String,
    val kana: String,
    val romaji: String,
    val chinese: String,
    val scene: String,
    val politeness: String,
    val aliases: Set<String> = emptySet(),
)

