package com.gojapan.feature.trip.ui

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

@Composable
internal fun LinkedNoteText(text: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val color = MaterialTheme.colorScheme.primary
    val annotated = remember(text, color, uriHandler, context) {
        // Share sheets often append instructions after Chinese punctuation without a space.
        // Replace delimiters only in the detection copy to preserve text and UTF-16 offsets.
        val detected = SpannableString(text.map { char ->
            if (char in "，。；！？、【】「」“”‘’（）") ' ' else char
        }.joinToString(""))
        Linkify.addLinks(detected, Linkify.WEB_URLS)
        buildAnnotatedString {
            append(text)
            detected.getSpans(0, detected.length, URLSpan::class.java).forEach { span ->
                val url = span.url
                if (url.startsWith("https://", true) || url.startsWith("http://", true)) {
                    addLink(LinkAnnotation.Url(url,
                        styles = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = {
                            try { uriHandler.openUri(url) }
                            catch (e: IllegalArgumentException) {
                                Toast.makeText(context, "无法打开链接，请检查是否安装浏览器", Toast.LENGTH_SHORT).show()
                            }
                        }), detected.getSpanStart(span), detected.getSpanEnd(span))
                }
            }
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium)
}
