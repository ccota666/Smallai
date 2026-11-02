package com.mimochat.app.ui.settings

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mimochat.app.ui.AppIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    title: String,
    tutorialId: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    AppIconButton(onClick = onBack, icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "\u8fd4\u56de")
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = WebViewClient()
                    val html = loadTutorialHtml(ctx, tutorialId)
                    loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun loadTutorialHtml(context: android.content.Context, id: String): String {
    return when (id) {
        "api" -> apiTutorialHtml()
        else -> "<h2>\u6559\u7a0b\u5185\u5bb9\u5f85\u66f4\u65b0</h2>"
    }
}

private fun apiTutorialHtml(): String {
    val img = { name: String, width: String ->
        if (width.isNotEmpty()) "<img src=\"file:///android_asset/tutorials/$name\" style=\"max-width:${width}px;width:100%;border-radius:8px;margin:8px 0;\"/>"
        else "<img src=\"file:///android_asset/tutorials/$name\" style=\"max-width:100%;border-radius:8px;margin:8px 0;\"/>"
    }
    return """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:-apple-system,sans-serif;padding:16px;line-height:1.7;color:#222;background:#fff;}
h2{font-size:18px;margin:20px 0 10px;}
h1{font-size:20px;margin:24px 0 12px;}
a{color:#2F6FED;}
li{margin:6px 0;}
img{display:block;margin:8px 0;}
</style></head><body>

<h2>1. \u8fdb\u5165 Xiaomi Mimo \u5f00\u653e\u5e73\u53f0\u5b98\u7f51<a href="https://platform.xiaomimimo.com/">platform.xiaomimimo.com</a></h2>

<h2>2. \u8fdb\u5165\u63a7\u5236\u53f0</h2>
<p><b>\u684c\u9762\u7aef\uff1a</b></p>
${img("PixPin_2026-11-02_09-34-18.png", "286")}
<p><b>\u624b\u673a\u7aef\uff1a</b></p>
${img("PixPin_2026-11-02_09-40-28.png", "386")}

<h2>3. \u767b\u5f55\u83b7\u53d6 API Keys</h2>
${img("PixPin_2026-11-02_12-53-24.png", "")}
<p><b>API Keys</b>\u662f\u4f7f\u7528\u8d26\u6237\u4f59\u989d\u6309\u91cf\u8ba1\u8d39\u4f7f\u7528\uff0cAPI Key\u83b7\u53d6\uff1a</p>
${img("PixPin_2026-11-02_09-59-15.png", "189")}
<p><b>Token Plan</b> \u662f\u8ba2\u9605\u4f7f\u7528\uff0c\u6ce8\u610f\u4e0b\u65b9\u4f7f\u7528\u6761\u4f8b\u53ef\u80fd\u6709\u4f7f\u7528\u98ce\u9669\uff0c\u5efa\u8bae\u53ea\u5728 mimo code \u4f7f\u7528\uff0cAPI Keys\u83b7\u53d6\uff1a</p>
${img("PixPin_2026-11-02_10-02-13.png", "273")}

<h2>4. \u586b\u5165\u5e94\u7528\uff0c\u5373\u53ef\u6b63\u5e38\u4e0e\u5927\u6a21\u578b\u5bf9\u8bdd</h2>
${img("PixPin_2026-11-02_12-51-03.png", "")}

<h1>\u9644\uff1a\u8c03\u7528\u5176\u4ed6\u6a21\u578b\uff08\u5982 deepseek\uff09</h1>

<h2>1. \u8fdb\u5165 deepseek \u5b98\u7f51\u83b7\u53d6 api keys\uff0c\u67e5\u770b\u5b98\u7f51\u6587\u6863\u4fee\u6539 Base URL\uff0c\u5e76\u586b\u5165\u6a21\u578b\u540d\uff0c\u5373\u53ef\u4f7f\u7528</h2>
${img("PixPin_2026-11-02_13-09-07.png", "262")}

</body></html>"""
}
