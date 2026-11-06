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

    // 拦截系统返回：子页返回关于主页
    androidx.activity.compose.BackHandler(enabled = showPage.isNotEmpty()) {
        showPage = ""
    }

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
            SettingsNavCard("Small ai", "应用介绍、版本 1.0") { showPage = "about" }
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
                        "about" -> "Small ai"
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
                        "about" -> aboutHtml()
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

private fun aboutHtml(): String = "<html><head><meta charset=\"utf-8\">$aboutStyle</head><body>" +
    "<h1>Small ai</h1>" +
    "<p>适配 MiMo API 的 Android AI 聊天应用，支持多模型对话、本地智能体执行、屏幕问答。</p>" +
    "<h2>特性</h2>" +
    "<ul>" +
    "<li>多模型对话，Markdown/公式/图表渲染</li>" +
    "<li>智能体模式：本地 MiMoCode 执行任务，实时展示过程</li>" +
    "<li>屏幕问答：悬浮球截屏提问，通知返回答案</li>" +
    "<li>隐私优先：无隐藏提示词，数据本地存储</li>" +
    "</ul>" +
    "<h2>版本</h2><p>1.0.0</p>" +
    "<h2>开源</h2><p><a href=\"https://github.com/ccota666/Smallai\">github.com/ccota666/Smallai</a></p>" +
    "</body></html>"

private fun authorHtml(): String = "<html><head><meta charset=\"utf-8\">$aboutStyle</head><body>" +
    "<div style=\"position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);text-align:center;\">" +
    "<p style=\"font-size:17px;line-height:2.2;margin:0;\">" +
    "MiMo-V2.6-Pro<br/>" +
    "MiMo-V2.6-Flash<br/>" +
    "DeepSeek-V4.1-Flash<br/>" +
    "<span style=\"color:#2F6FED;\">MiMo Desktop</span><br/>" +
    "<span style=\"color:#2F6FED;\">DeepSeek Harness</span><br/>" +
    "ccota</p></div>" +
    "</body></html>"

private fun privacyHtml(): String = "<html><head><meta charset=\"utf-8\">$aboutStyle</head><body>" +
    "<h1>隐私说明</h1>" +
    "<h2>数据收集</h2>" +
    "<p>本应用<b>不收集、不上传</b>你的任何个人信息，所有数据保存在本地。网络仅用于调用模型 API，不进行任何其他联网活动。</p>" +
    "<h2>提示词透明</h2>" +
    "<p>未向模型添加<b>任何隐藏提示词</b>，所有提示词均可在 [设置-高级] 查看与修改。</p>" +
    "<h2>权限使用</h2>" +
    "<p>存储、通知、悬浮窗、截屏等权限仅用于对应功能，不用于数据收集。</p>" +
    "<h2>第三方服务</h2>" +
    "<p>使用 MiMo API 等第三方模型服务时，相关数据使用受该服务的隐私政策约束。</p>" +
    "<h2>免责声明</h2>" +
    "<p>AI 生成内容可能有误，请注意核实与甄别，仅供参考。</p>" +
    "<p>本应用为个人学习项目，开发过程使用了 AI 辅助，仅作为交流分享。作者水平有限，不保证无 bug、无功能异常，请自行妥善备份聊天记录等重要数据。</p>" +
    "<p>请遵守大模型官网使用条例与文档，以及所调用第三方 API 服务的隐私政策。模型调用产生的费用，具体价格以各平台官网定价为准。</p>" +
    "<p>请勿从其他途径下载应用。</p>" +
    "</body></html>"

private fun licenseHtml(): String = "<html><head><meta charset=\"utf-8\">$aboutStyle</head><body>" +
    "<h1>开源代码许可</h1>" +
    "<p>本应用使用了以下开源组件，感谢各开源社区的贡献：</p>" +
    "<h2>UI 框架</h2>" +
    "<p>Jetpack Compose — Apache License 2.0</p>" +
    "<p>Material 3 / Material Icons — Apache License 2.0</p>" +
    "<p>Navigation Compose — Apache License 2.0</p>" +
    "<h2>数据与网络</h2>" +
    "<p>Room Database — Apache License 2.0</p>" +
    "<p>DataStore Preferences — Apache License 2.0</p>" +
    "<p>OkHttp — Apache License 2.0</p>" +
    "<h2>运行时</h2>" +
    "<p>Kotlin Coroutines — Apache License 2.0</p>" +
    "<p>AndroidX (Core / Lifecycle / Activity) — Apache License 2.0</p>" +
    "<h2>Web 渲染库</h2>" +
    "<p>KaTeX（含字体）— MIT License</p>" +
    "<p>highlight.js — BSD 3-Clause License</p>" +
    "<p>Mermaid — MIT License</p>" +
    "<h2>完整许可文本</h2>" +
    "<p>各组件的完整许可文本请访问其官方仓库或文档。</p>" +
    "<p style=\"color:#999;font-size:12px;\">注：本页内容由 AI 生成，如有疏漏十分抱歉。</p>" +
    "</body></html>"
