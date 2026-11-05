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
<p style="color:#888;font-size:13px;">仅供参考，请仔细阅读官网文档、使用条例与<u>**定价**</u>，以实际为准</p>
<h2>1. 进入 Xiaomi Mimo 开放平台官网 <a href="https://platform.xiaomimimo.com">platform.xiaomimimo.com</a></h2>
<h2>2. 进入控制台</h2>
<p><b>桌面端：</b></p>
""" + img("PixPin_2026-11-02_09-34-18.png", "286") + """
<p><b>手机端：</b></p>
""" + img("PixPin_2026-11-02_09-40-28.png", "386") + """
<h2>3. 登录获取 API Keys</h2>
""" + img("PixPin_2026-11-02_12-53-24.png") + """
<p><b>API Keys</b> 是使用账户余额按量计费使用，API Key 获取：</p>
""" + img("PixPin_2026-11-02_09-59-15.png", "189") + """
<p><b>Token Plan</b> 是订阅使用，注意下方使用条例可能有使用风险，建议只在 mimo code 使用，API Keys 获取：</p>
""" + img("PixPin_2026-11-02_10-02-13.png", "273") + """
<h2>4. 填入应用，即可正常与大模型对话</h2>
""" + img("PixPin_2026-11-02_12-51-03.png") + """
<h2>5. 联网搜索</h2>
<p>如需使用联网搜索，需在开放平台开通联网服务插件</p>
""" + img("PixPin_2026-11-05_11-10-04.png", "359") + """
<h1>附：调用其他模型（如 deepseek）</h1>
<h2>1. 进入 deepseek 官网获取 api keys，查看官网文档修改 Base URL，并填入模型名，即可使用</h2>
""" + img("PixPin_2026-11-02_13-09-07.png", "262") + htmlTail

private fun mimocodeTutorialHtml(): String = htmlHead + """
<p style="color:#888;font-size:13px;">部署可能需要一定的网络环境<br/>注意：该页面可以<b>左右滑动</b></p>
<h2>1. 从 F-Droid/GitHub 下载并安装 Termux</h2>
<p>请勿下载谷歌商店版，该版本不支持其他应用调用</p>
<h2>2. 执行命令</h2>
<p><b>***允许其他应用调用***</b></p>
<pre><code>mkdir -p ~/.termux
echo "allow-external-apps = true" &gt;&gt; ~/.termux/termux.properties
termux-reload-settings</code></pre>
<p><b>授权存储</b></p>
<pre><code>termux-setup-storage</code></pre>
<p><b>***更新包索引，安装 proot-distro***</b></p>
<pre><code>pkg update
pkg install -y proot-distro</code></pre>
<p><b>装 Ubuntu</b></p>
<pre><code>proot-distro install ubuntu</code></pre>
<p><b>进入 Ubuntu</b></p>
<pre><code>proot-distro login ubuntu</code></pre>
<p>应该看到 <code>root@localhost:~#</code></p>
<h2>Ubuntu 里装 MiMoCode</h2>
<p><b>装 curl</b></p>
<pre><code>apt update &amp;&amp; apt install -y curl</code></pre>
<p><b>运行官方安装脚本</b></p>
<pre><code>curl -fsSL https://mimo.xiaomi.com/install | bash</code></pre>
<p><b>加 PATH</b></p>
<pre><code>echo 'export PATH=${'$'}HOME/.mimocode/bin:${'$'}PATH' &gt;&gt; ~/.bashrc
source ~/.bashrc
mimo --version</code></pre>
<p>出版本号（如 <code>0.1.16</code>）就成功了。</p>
<p><b>登录并验证</b></p>
<pre><code>mimo auth login</code></pre>
<p>然后选择模型提供商，按提示授权/填写 key。</p>
<p><b>注意</b>授权网页中按量付费 和 token plan 选项，选择需要使用的</p>
""" + img("PixPin_2026-11-05_11-04-31.png", "230") + """
<h1>使用</h1>
<p>（建议先设置 termux 后台限制为无限制）</p>
<ul>
<li><b>打开 termux</b>在后台</li>
<li>在聊天框选中<b>智能体按键</b></li>
<li>顶部选择使用<b>支持的模型</b>（该模型与对话页面选择的模型，使用的是 mimo code 中的 api key，非界面设置的 api key）</li>
<li><b>发送</b>即可开始任务</li>
</ul>
""" + htmlTail

private fun screenQaTutorialHtml(): String = htmlHead + """
<h2>1. 新建方案，设置名称/提示词/使用模型/图片压缩率</h2>
<h2>2. 选择方案</h2>
<h2>3. 点击启动悬浮球，按提示授权权限，注意共享屏幕选择整个屏幕</h2>
<h2>4. 启动悬浮球后，点击悬浮球，自动截取屏幕上传截图与提示词给模型，然后通过系统弹窗返回答案</h2>
<h2>5. 长按悬浮球即可关闭</h2>
""" + htmlTail
