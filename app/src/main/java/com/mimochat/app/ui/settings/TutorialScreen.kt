package com.mimochat.app.ui.settings

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
                    AppIconButton(onClick = onBack, icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back")
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
                    setBackgroundColor(android.graphics.Color.WHITE)
                    webViewClient = WebViewClient()
                    val html = loadTutorialHtml(tutorialId)
                    loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                }
            }
        )
    }
}

private fun loadTutorialHtml(id: String): String {
    return when (id) {
        "api" -> apiTutorialHtml()
        "mimocode" -> mimocodeTutorialHtml()
        "screenqa" -> screenQaTutorialHtml()
        else -> "<h2>教程内容待更新</h2>"
    }
}

private fun img(name: String, w: String = ""): String {
    val style = if (w.isNotEmpty()) "max-width:${w}px;width:100%" else "max-width:100%"
    return "<img src=\"file:///android_asset/tutorials/$name\" style=\"$style;border-radius:8px;margin:8px 0;display:block;\"/>"
}

private val htmlHead = """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{font-family:-apple-system,sans-serif;padding:16px;line-height:1.8;color:#222;background:#fff;}
h1{font-size:20px;margin:24px 0 12px;}
h2{font-size:17px;margin:20px 0 10px;}
a{color:#2F6FED;}
li{margin:6px 0;}
img{border-radius:8px;}
</style></head><body>
"""

private val htmlTail = "</body></html>"

private fun apiTutorialHtml(): String = htmlHead + """
<h2>1. 进入Xiaomi Mimo开放平台官网<a href="https://platform.xiaomimimo.com/">platform.xiaomimimo.com</a></h2>
<h2>2. 进入控制台</h2>
<p><b>桌面端：</b></p>
""" + img("PixPin_2026-11-02_09-34-18.png", "286") + """
<p><b>手机端：</b></p>
""" + img("PixPin_2026-11-02_09-40-28.png", "386") + """
<h2>3. 登录获取 API Keys</h2>
""" + img("PixPin_2026-11-02_12-53-24.png") + """
<p><b>API Keys</b> 是使用账户余额按量计费使用，API Key获取：</p>
""" + img("PixPin_2026-11-02_09-59-15.png", "189") + """
<p><b>Token Plan</b> 是订阅使用，注意下方使用条例可能有使用风险，建议只在 mimo code 使用，API Keys获取：</p>
""" + img("PixPin_2026-11-02_10-02-13.png", "273") + """
<h2>4. 填入应用，即可正常与大模型对话</h2>
""" + img("PixPin_2026-11-02_12-51-03.png") + """
<h1>附：调用其他模型（如deepseek）</h1>
<h2>1. 进入deepseek官网获取api keys，查看官网文档修改 Base URL，并填入模型名，即可使用</h2>
""" + img("PixPin_2026-11-02_13-09-07.png", "262") + htmlTail

private fun mimocodeTutorialHtml(): String = htmlHead + "<h2>教程内容待更新</h2>" + htmlTail

private fun screenQaTutorialHtml(): String = htmlHead + "<h2>教程内容待更新</h2>" + htmlTail
