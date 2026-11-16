package com.gojapan.feature.search.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.gojapan.feature.search.domain.TravelExpression

@Composable
fun ExpressionDetails(expression: TravelExpression, favorite: Boolean, toggleFavorite: () -> Unit) {
    var open by rememberSaveable(expression.id) { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("查看详情") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(expression.japanese) },
        text = { Text(listOf(expression.kana, expression.romaji, expression.chinese,
            "${expression.scene} · ${expression.politeness}").joinToString("\n")) },
        confirmButton = { TextButton(onClick = toggleFavorite) { Text(if (favorite) "取消收藏" else "收藏") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("返回") } },
    )
}
