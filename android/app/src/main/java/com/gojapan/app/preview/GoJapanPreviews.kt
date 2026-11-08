package com.gojapan.app.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.gojapan.app.theme.GoJapanTheme

/** Shared visual checks for screen previews; no application or ViewModel is required. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "手机 · 浅色", group = "GoJapan", widthDp = 393, heightDp = 851, locale = "zh")
@Preview(name = "手机 · 深色", group = "GoJapan", widthDp = 393, heightDp = 851, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "小屏 · 大字体", group = "GoJapan", widthDp = 360, heightDp = 800, locale = "zh", fontScale = 1.3f)
annotation class GoJapanPreviews

@Composable
fun GoJapanPreview(content: @Composable () -> Unit) {
    GoJapanTheme {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}
