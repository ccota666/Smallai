package com.mimochat.app.ui.settings

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mimochat.app.ui.AppIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var showPage by remember { mutableStateOf("") }

    if (showPage.isNotEmpty()) {
        AboutDetailPage(page = showPage, onBack = { showPage = "" })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    AppIconButton(onClick = onBack, icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsNavCard("Small ai", "版本 1.0") { }
            SettingsNavCard("软件作者", "") { showPage = "author" }
            SettingsNavCard("隐私说明", "") { showPage = "privacy" }
            SettingsNavCard("开源代码许可", "") { showPage = "license" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutDetailPage(page: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (page) {
                        "author" -> "软件作者"
                        "privacy" -> "隐私说明"
                        "license" -> "开源代码许可"
                        else -> "关于"
                    })
                },
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
                    settings.javaScriptEnabled = false
                    setBackgroundColor(android.graphics.Color.WHITE)
                    webViewClient = WebViewClient()
                    val html = when (page) {
                        "author" -> authorHtml()
                        "privacy" -> privacyHtml()
                        "license" -> licenseHtml()
                        else -> "<p>...</p>"
                    }
                    loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                }
            }
        )
    }
}

private val aboutStyle = """
<style>
body{font-family:-apple-system,sans-serif;padding:16px;line-height:1.8;color:#222;background:#fff;}
h1{font-size:20px;margin:16px 0;}
h2{font-size:16px;margin:12px 0;}
p{margin:8px 0;}
</style>
"""

private fun authorHtml(): String = "<html><head><meta charset=\"utf-8\">$aboutStyle</head><body>" +
    "<h1>Small ai</h1>" +
    "<p>Small ai 是基于 MiMo API 的 Android 聊天应用。</p>" +
    "<h2>开发者</h2><p>TODO</p>" +
    "<h2>联系方式</h2><p>TODO</p>" +
    "</body></html>"

private fun privacyHtml(): String = "<html><head><meta charset=\"utf-8\">$aboutStyle</head><body>" +
    "<h1>隐私说明</h1>" +
    "<h2>数据收集</h2><p>本应用不收集任何个人数据。API Key 等敏感信息仅保存在设备本地。</p>" +
    "<h2>网络请求</h2><p>聊天内容仅发送至用户配置的 API 服务端。智能体任务在本地 Termux 环境中执行。</p>" +
    "<h2>权限使用</h2><p>存储、通知、悬浮窗、截屏等权限仅用于对应功能，不用于数据收集。</p>" +
    "<h2>第三方服务</h2><p>使用 MiMo API 等第三方模型服务时，相关数据使用受该服务的隐私政策约束。</p>" +
    "</body></html>"

private fun licenseHtml(): String = "<html><head><meta charset=\"utf-8\">$aboutStyle</head><body>" +
    "<h1>开源代码许可</h1>" +
    "<p>本应用使用了以下开源组件：</p>" +
    "<h2>Jetpack Compose</h2><p>Apache License 2.0</p>" +
    "<h2>Material Components for Android</h2><p>Apache License 2.0</p>" +
    "<h2>Room Database</h2><p>Apache License 2.0</p>" +
    "<h2>OkHttp</h2><p>Apache License 2.0</p>" +
    "<h2>KaTeX</h2><p>MIT License</p>" +
    "<h2>highlight.js</h2><p>BSD 3-Clause License</p>" +
    "<h2>Mermaid</h2><p>MIT License</p>" +
    "</body></html>"
