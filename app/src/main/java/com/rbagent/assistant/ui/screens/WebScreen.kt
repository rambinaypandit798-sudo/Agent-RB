package com.rbagent.assistant.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen() {
    val context = LocalContext.current
    var addressBar by remember { mutableStateOf("https://www.google.com") }
    var committedUrl by remember { mutableStateOf(addressBar) }
    var pageTitle by remember { mutableStateOf("Web") }
    var progress by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 96.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FrostedSlateHeavy)
                .border(1.dp, FrostedBorder)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color(0x3338BDF8))
                    .border(1.dp, NeonCyan.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Language, null, tint = NeonCyan, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pageTitle, color = TextPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(committedUrl, color = SlateMuted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // ── URL bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B1220))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallIconButton(Icons.Filled.ArrowBack, enabled = canGoBack) {
                webView?.goBack()
            }
            Spacer(Modifier.width(6.dp))
            SmallIconButton(Icons.Filled.ArrowForward, enabled = canGoForward) {
                webView?.goForward()
            }
            Spacer(Modifier.width(6.dp))

            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, FrostedBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = addressBar,
                    onValueChange = { addressBar = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(NeonCyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        committedUrl = normalizeUrl(addressBar)
                        addressBar = committedUrl
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (addressBar.isEmpty()) Text("Enter URL…", color = SlateMuted, fontSize = 13.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(6.dp))
            SmallIconButton(Icons.Filled.Refresh) { webView?.reload() }
        }

        // ── Progress ──
        if (progress in 1..99) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF1E293B))) {
                Box(
                    Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(NeonCyan)
                )
            }
        }

        // ── WebView ──
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (url != null) {
                                committedUrl = url
                                if (addressBar != url) addressBar = url
                            }
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                            view?.title?.let { if (it.isNotBlank()) pageTitle = it }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            progress = newProgress
                        }
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            if (!title.isNullOrBlank()) pageTitle = title
                        }
                    }
                    webView = this
                    loadUrl(committedUrl)
                }
            },
            update = { wv ->
                webView = wv
                if (wv.url != committedUrl) wv.loadUrl(committedUrl)
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape)
            .background(if (enabled) Color(0xFF1E293B) else Color(0xFF141B2A))
            .border(1.dp, if (enabled) FrostedBorder else Color(0x1AE0F2FE), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) NeonCyan else SlateMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun normalizeUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return "https://www.google.com"
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (t.contains(' ') || (!t.contains('.') && !t.startsWith("localhost"))) {
        return "https://www.google.com/search?q=" + android.net.Uri.encode(t)
    }
    return "https://$t"
}
