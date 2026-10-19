package com.mimochat.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 整段聊天一个 WebView。
 * - 代码/公式/Mermaid 顶栏：复制 + 全屏（内容级全屏，可缩放+横屏）
 * - 链接跳系统浏览器
 * - 跟随：只有用户 touch/wheel 才停；程序滚动不停
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatListWebView(
    messagesJson: String,
    streamingJson: String,
    dark: Boolean,
    boldColor: Color,
    fontSizeSp: Float,
    topInsetPx: Int,
    bottomInsetPx: Int,
    scrollTrigger: Long = 0L,
    jumpMessageId: Long = 0L,
    fullscreen: Boolean = false,
    modifier: Modifier = Modifier,
    onScrollAtBottom: (Boolean) -> Unit = {},
    onFullscreen: (Boolean) -> Unit = {},
    onAction: (action: String, messageId: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val html = remember(dark, boldColor, fontSizeSp, topInsetPx, bottomInsetPx) {
        chatShellHtml(dark, boldColor, fontSizeSp, topInsetPx, bottomInsetPx)
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var lastFollowTrigger by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                // 聊天界面禁止捏合缩放；全屏用自定义 pinch，不靠 WebView zoom
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "renderChat($messagesJson, $streamingJson);",
                            null
                        )
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val u = request?.url?.toString() ?: return false
                        if (u.startsWith("http://") || u.startsWith("https://")) {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u)))
                            }
                            return true
                        }
                        return false
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onAction(action: String, messageId: Long) {
                        onAction(action, messageId)
                    }

                    @JavascriptInterface
                    fun setOrientation(degrees: Int) {
                        (ctx as? Activity)?.runOnUiThread {
                            ctx.requestedOrientation =
                                if (degrees == 90 || degrees == 270)
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                else
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }

                    @JavascriptInterface
                    fun setFullscreen(on: Boolean) {
                        onFullscreen(on)
                    }

                    @JavascriptInterface
                    fun exitFullscreen() {
                        onFullscreen(false)
                    }

                    @JavascriptInterface
                    fun onScroll(atBottom: Boolean) {
                        onScrollAtBottom(atBottom)
                    }

                    @JavascriptInterface
                    fun copyText(text: String) {
                        (ctx as? Activity)?.runOnUiThread {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText("code", text)
                            )
                        }
                    }

                    @JavascriptInterface
                    fun openUrl(url: String) {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                    }
                }, "Android")
                loadDataWithBaseURL(
                    "file:///android_asset/", html, "text/html", "utf-8", null
                )
            }
        },
        update = { wv ->
            wv.evaluateJavascript(
                "window.__update && window.__update($messagesJson, $streamingJson);",
                null
            )
            // 关键：scrollTrigger 必须「变化才触发一次」。
            // update 每次重组都会跑，若只判断 !=0 就会每帧调 __follow()，
            // 用户取消跟随后立刻被拉回来。
            if (scrollTrigger != 0L && scrollTrigger != lastFollowTrigger) {
                lastFollowTrigger = scrollTrigger
                wv.evaluateJavascript(
                    "window.__follow && window.__follow();", null
                )
            }
            // 安卓返回键退出全屏：同步到 JS 层
            if (!fullscreen) {
                wv.evaluateJavascript(
                    "(function(){var f=document.getElementById('fs');"
                        + "if(f&&f.classList.contains('on')&&window.closeFs)closeFs();})();",
                    null
                )
            }
            if (jumpMessageId != 0L) {
                wv.evaluateJavascript(
                    "window.__jump && window.__jump($jumpMessageId);", null
                )
            }
        }
    )
}

private fun chatShellHtml(
    dark: Boolean,
    boldColor: Color,
    fontSizeSp: Float,
    topInsetPx: Int,
    bottomInsetPx: Int
): String {
    val fg = if (dark) "#E8E8E8" else "#1C1C1E"
    val muted = if (dark) "#9E9E9E" else "#6E6E73"
    val userBg = if (dark) "#2E5A8A" else "#D6E8FF"
    val userFg = if (dark) "#FFFFFF" else "#10243A"
    val codeBg = if (dark) "#2A2A2A" else "#F2F2F4"
    val borderC = if (dark) "#333333" else "#E2E2E7"
    val quoteBg = if (dark) "#222222" else "#F6F6F8"
    val thinkBg = if (dark) "#1A2332" else "#EEF4FF"
    val fsBg = if (dark) "#111111" else "#FFFFFF"
    // 主题色 = 按钮同色（主色）
    val accent = if (dark) "#7EB6FF" else "#2F6FED"
    // 加粗：theme=主色 / rainbow=渐变 / colorful=按分段换色 / 自定义色值
    val boldCss = when {
        boldColor == Color.Unspecified -> "theme"
        (boldColor.toArgb() and 0xFFFFFF) == 0x7F7F7F -> "rainbow"
        (boldColor.toArgb() and 0xFFFFFF) == 0x5F5F5F -> "colorful"
        else -> String.format("#%06X", 0xFFFFFF and boldColor.toArgb())
    }
    val darkFlag = if (dark) "1" else "0"
    val topCss = topInsetPx.coerceAtLeast(0)
    val botCss = bottomInsetPx.coerceAtLeast(0)

    return """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script>
<script src="highlight.min.js"></script>
<script src="mermaid.min.js"></script>
<style>
*{box-sizing:border-box;}
html,body{margin:0;padding:0;background:transparent;color:$fg;
  overflow-x:hidden;
  font-family:-apple-system,'PingFang SC','Noto Sans SC',sans-serif;
  font-size:${fontSizeSp}px;line-height:1.7;--bc:$boldCss;--ac:$accent;}
#list{padding:${(topCss*0.6).toInt()}px 14px ${(botCss*0.7).toInt()}px 14px;overflow-x:hidden;}
.msg{margin:10px 0;}
.msg:first-child{margin-top:-12px;}
.msg:last-child{margin-bottom:-8px;}
.content > *:first-child{margin-top:2px;}
.content > *:last-child{margin-bottom:2px;}
.msg.user{display:flex;justify-content:flex-end;}
.msg.user .bubble{max-width:88%;background:$userBg;color:$userFg;
  padding:9px 13px;border-radius:18px;border-bottom-right-radius:6px;
  word-break:break-word;}
.msg.ai .bubble{max-width:100%;padding:0;border:none;background:transparent;}
.content{margin:4px 0;overflow-wrap:break-word;}
.content h1,.content h2,.content h3,.content h4,.content h5,.content h6{
  margin:12px 0 7px;line-height:1.35;font-weight:700;color:inherit;}
.content h1{font-size:1.3em;} .content h2{font-size:1.2em;}
.content h3{font-size:1.1em;} .content h4{font-size:1.04em;}
.content p{margin:12px 0;}
.content ul,.content ol{margin:10px 0 10px 0;padding:0;list-style:none;}
.content li{margin:6px 0;padding:8px 12px;position:relative;
  background:rgba(128,128,128,0.07);border-radius:10px;}
.content li::before{display:none;}
.content code{font-family:ui-monospace,Consolas,monospace;font-size:0.88em;
  background:$codeBg;padding:1px 5px;border-radius:4px;}
.box{margin:10px 0;border:1px solid $borderC;border-radius:12px;
  overflow:hidden;background:$codeBg;}
.box .bhead{display:flex;align-items:center;justify-content:space-between;
  padding:5px 8px 5px 12px;background:rgba(128,128,128,0.12);
  border-bottom:1px solid $borderC;font-size:12px;color:$muted;}
.box .bhead .ops{display:flex;gap:6px;}
.box .bhead button{border:1px solid $borderC;background:transparent;color:$muted;
  border-radius:8px;padding:3px 10px;font-size:12px;cursor:pointer;}
.box .bhead button:active{background:rgba(128,128,128,0.2);}
.box pre{margin:0;padding:10px 12px;overflow-x:auto;}
.codebody{overflow:hidden;position:relative;}
.codebody.clamped{max-height:calc(1.55em * 6 + 22px);}
.codebody.clamped .fade{position:absolute;left:0;right:0;bottom:0;height:36px;
  background:linear-gradient(transparent,${codeBg});pointer-events:none;}
.codebox-wrap{position:relative;}
.expand-btn{position:absolute;right:8px;bottom:6px;z-index:5;
  border:1px solid ${borderC};background:${codeBg};color:${muted};
  border-radius:8px;padding:3px 10px;font-size:11px;cursor:pointer;}
.box pre code{background:transparent;padding:0;font-size:12.5px;line-height:1.55;}
.box .mathpad{padding:12px;overflow-x:auto;text-align:center;}
.box .mmpad{padding:10px;overflow-x:auto;}
.content blockquote{margin:9px 0;padding:7px 13px;border-left:3px solid $borderC;
  background:$quoteBg;color:$muted;border-radius:0 10px 10px 0;}
.content hr.rule{border:none;height:0;margin:28px 2px 28px 2px;
  box-shadow:0 1px 2px rgba(0,0,0,0.18);border-top:1px solid rgba(128,128,128,0.35);}
.content table{border-collapse:collapse;width:100%;margin:9px 0;font-size:0.92em;}
.content th,.content td{border:1px solid $borderC;padding:6px 9px;text-align:left;}
.content th{background:$codeBg;font-weight:600;}
.content strong{font-weight:700;color:var(--bc,inherit);}
.content a{color:var(--ac);}
.sec-0{color:#2F6FED;} .sec-1{color:#C678DD;} .sec-2{color:#E67E22;}
.sec-3{color:#27AE60;} .sec-4{color:#E74C3C;} .sec-5{color:#16A085;}
.content strong.rainbow{background:linear-gradient(90deg,
  #FF6B6B,#FEC89A,#95D5B2,#4D96FF,#B197FC);
  -webkit-background-clip:text;background-clip:text;color:transparent;}
.content a{color:var(--ac);text-decoration:underline;}
.content img{max-width:100%;border-radius:10px;}
/* 全屏层 */
#fs{position:fixed;inset:0;background:$fsBg;z-index:9999;display:none;
  flex-direction:column;}
#fs.on{display:flex;}
#fs .fs-top{display:flex;justify-content:space-between;align-items:center;
  padding:36px 16px 10px;gap:10px;flex-wrap:wrap;
  padding-top:calc(36px + env(safe-area-inset-top,0px));
  position:relative;z-index:20;background:inherit;}
#fs .fs-ops{display:flex;gap:8px;flex-wrap:wrap;}
#fs .fs-ops button{min-height:40px;min-width:64px;font-size:14px;}
#fs .fs-body{padding:6px 10px 40px;}
#fs .mathpad,#fs .mmpad,#fs pre{max-width:100%;width:100%;box-sizing:border-box;}
#fs img{max-width:100%;max-height:70vh;object-fit:contain;}
#fs .mermaid svg{max-width:100%;height:auto;}
#fs .mathpad{text-align:left;overflow-x:auto;}
#fs .katex-display{margin:10px 0;overflow-x:auto;overflow-y:hidden;}
#fs .mermaid{width:100%;min-width:0;}
#fs .fs-top .ttl{font-size:13px;color:$muted;}
#fs .fs-body{flex:1;overflow:auto;padding:8px 16px 30px;touch-action:none;}
#fs .fs-body.zoomed{transform-origin:0 0;}
#fs pre{background:$codeBg;padding:14px;border-radius:12px;overflow:auto;
  font-size:14px;line-height:1.6;}
#fs .mathpad,#fs .mmpad{padding:10px;overflow-x:auto;}
#fs button{border:1px solid $borderC;background:transparent;color:$fg;
  border-radius:10px;padding:6px 14px;font-size:13px;cursor:pointer;}
.reason{margin:10px 0;border:1px solid $borderC;border-radius:12px;
  background:${if (dark) "#2A2A2A" else "#FFFFFF"};font-size:0.92em;overflow:hidden;}
.reason summary{cursor:pointer;user-select:none;padding:9px 12px;
  color:$muted;font-size:12.5px;}
.reason summary::marker{color:$muted;}
.reason pre{white-space:pre-wrap;margin:0;padding:10px 12px 12px;
  font-family:inherit;color:$muted;
  border-top:1px solid $borderC;background:${if (dark) "#2A2A2A" else "#FFFFFF"};}
.cites{margin-top:10px;padding-top:7px;border-top:1px solid $borderC;}
.cites summary{cursor:pointer;font-size:12px;color:$muted;user-select:none;}
.cites .t{font-size:12px;color:$muted;}
.cites-body{margin-top:6px;}
.cites .c{font-size:12px;color:$muted;margin:3px 0;cursor:pointer;}
.cites .c .title{color:var(--ac);}
.att-row{display:flex;gap:8px;flex-wrap:wrap;margin:8px 0;}
.att{display:flex;flex-direction:column;align-items:center;width:76px;}
.att img{width:64px;height:64px;object-fit:cover;border-radius:10px;}
.att .tag{font-size:10px;color:inherit;opacity:.75;margin-top:2px;}
.att .name{font-size:10px;color:inherit;opacity:.75;max-width:76px;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.actions{display:flex;gap:6px;margin-top:9px;flex-wrap:wrap;}
.actions button{border:1px solid $borderC;background:transparent;color:$muted;
  border-radius:12px;padding:4px 12px;font-size:12px;cursor:pointer;}
.pending{color:$muted;font-style:italic;}
/* 高亮 */
.hljs-keyword,.hljs-selector-tag,.hljs-literal,.hljs-type,.hljs-name,
.hljs-strong{color:#C678DD;}
.hljs-string,.hljs-attr{color:#98C379;}
.hljs-number,.hljs-symbol{color:#D19A66;}
.hljs-comment{color:#7F848E;font-style:italic;}
.hljs-title,.hljs-section,.hljs-function .hljs-title{color:#61AFEF;}
.hljs-built_in{color:#E6C07B;}
.hljs{background:transparent;}
</style>
</head>
<body>
<div id="list"></div>
<div id="fs">
  <div class="fs-top">
    <span class="ttl" id="fsTtl"></span>
    <span class="fs-ops">
      <button id="rotBtn" onclick="toggleRotate()">横屏</button>
      <button onclick="closeFs()">退出</button>
    </span>
  </div>
  <div class="fs-body" id="fsBody"></div>
</div>
<div id="confirmBox" style="display:none;position:fixed;inset:0;z-index:10000;
  background:rgba(0,0,0,0.35);align-items:center;justify-content:center;">
  <div style="background:#fff;border-radius:14px;padding:18px 16px;width:78%;max-width:320px;
    box-shadow:0 8px 24px rgba(0,0,0,0.18);">
    <div id="confirmText" style="font-size:15px;color:#1C1C1E;margin-bottom:14px;"></div>
    <div style="display:flex;gap:10px;justify-content:flex-end;">
      <button id="confirmCancel" style="border:1px solid #E2E2E7;background:transparent;
        color:#6E6E73;border-radius:10px;padding:8px 16px;font-size:13px;">取消</button>
      <button id="confirmOk" style="border:none;background:#2F6FED;color:#fff;
        border-radius:10px;padding:8px 16px;font-size:13px;">确定</button>
    </div>
  </div>
</div>
<script>
var D = String.fromCharCode(36);
var fsScale = 1;

function esc(s){
  return String(s == null ? '' : s)
    .split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;');
}

var fsLandscape = false;
function askConfirm(msg, onYes){
  var box = document.getElementById('confirmBox');
  var t = document.getElementById('confirmText');
  var ok = document.getElementById('confirmOk');
  var no = document.getElementById('confirmCancel');
  if (!box) { if (onYes) onYes(); return; }
  t.textContent = msg;
  box.style.display = 'flex';
  function done(yes){
    box.style.display = 'none';
    ok.onclick = null; no.onclick = null;
    if (yes && onYes) onYes();
  }
  ok.onclick = function(){ done(true); };
  no.onclick = function(){ done(false); };
}
function openFs(title, html){
  fsLandscape = false;
  document.getElementById('fsTtl').textContent = title;
  document.getElementById('rotBtn').textContent = '横屏';
  var b = document.getElementById('fsBody');
  b.innerHTML = html;
  resetFsView();
  document.getElementById('fs').classList.add('on');
  renderMath(b, true); renderCode(b, true);
  bindFsGestures();
  try { if (window.mermaid && b.querySelector('.mermaid'))
    mermaid.run({querySelector:'#fsBody .mermaid'}); } catch(e) {}
  try { Android.setOrientation(0); } catch(e) {}
  try { Android.setFullscreen(true); } catch(e) {}
}
function toggleRotate(){
  fsLandscape = !fsLandscape;
  document.getElementById('rotBtn').textContent = fsLandscape ? '竖屏' : '横屏';
  try { Android.setOrientation(fsLandscape ? 90 : 0); } catch(e) {}
  // 横屏后强制重新排版，避免公式/图被裁
  setTimeout(function(){
    resetFsView();
  }, 320);
}
/* 全屏缩放 —— 相册式（照片放大逻辑）：
   - transform-origin 0 0，配 translate+scale
   - 绕捏合中点缩放，内容不会「往上跑」
   - 不改 width，避免重排导致位移/提前消失
   - 1x=铺满屏，最大 6x；放大后可单指平移 */
var fsS = 1, fsX = 0, fsY = 0;
var pinchDist = 0, panLast = null;

function fsBody(){ return document.getElementById('fsBody'); }

function clampFs(){
  var b = fsBody();
  if (!b) return;
  // offsetWidth/Height 是未缩放的布局尺寸
  var w = b.offsetWidth * fsS;
  var h = b.scrollHeight * fsS;
  var vw = window.innerWidth, vh = window.innerHeight;
  var minX = Math.min(0, vw - w);
  var minY = Math.min(0, vh - h);
  if (fsX > 0) fsX = 0;
  if (fsY > 0) fsY = 0;
  if (fsX < minX) fsX = minX;
  if (fsY < minY) fsY = minY;
}

function applyFsTransform(){
  var b = fsBody();
  if (!b) return;
  clampFs();
  b.style.transformOrigin = '0 0';
  b.style.transform = 'translate(' + fsX + 'px,' + fsY + 'px) scale(' + fsS + ')';
}

function resetFsView(){
  fsS = 1; fsX = 0; fsY = 0; pinchDist = 0; panLast = null;
  var b = fsBody();
  if (b){
    b.style.transform = '';
    b.style.transformOrigin = '';
    b.style.width = '';
  }
}

function distOf(t){
  var dx = t[0].clientX - t[1].clientX;
  var dy = t[0].clientY - t[1].clientY;
  return Math.sqrt(dx*dx + dy*dy) || 1;
}

function bindFsGestures(){
  var el = document.getElementById('fsBody');
  if (!el || el.__gest) return;
  el.__gest = true;

  el.addEventListener('touchstart', function(e){
    if (e.touches.length === 2){
      pinchDist = distOf(e.touches);
      panLast = null;
    } else if (e.touches.length === 1 && fsS > 1.02){
      panLast = {x: e.touches[0].clientX, y: e.touches[0].clientY};
    }
  }, {passive:true});

  el.addEventListener('touchmove', function(e){
    if (e.touches.length === 2 && pinchDist > 0){
      e.preventDefault();
      var d = distOf(e.touches);
      var midX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
      var midY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
      var s0 = fsS;
      var s1 = Math.min(6, Math.max(1, s0 * (d / pinchDist)));
      if (s1 !== s0){
        // 绕捏合中点：保持指下内容不动（相册式）
        fsX = midX - (midX - fsX) * (s1 / s0);
        fsY = midY - (midY - fsY) * (s1 / s0);
        fsS = s1;
      }
      pinchDist = d;
      applyFsTransform();
    } else if (e.touches.length === 1 && fsS > 1.02 && panLast){
      e.preventDefault();
      fsX += e.touches[0].clientX - panLast.x;
      fsY += e.touches[0].clientY - panLast.y;
      panLast = {x: e.touches[0].clientX, y: e.touches[0].clientY};
      applyFsTransform();
    }
  }, {passive:false});

  el.addEventListener('touchend', function(){ pinchDist = 0; panLast = null; }, {passive:true});
  el.addEventListener('touchcancel', function(){ pinchDist = 0; panLast = null; }, {passive:true});
}

function closeFs(){
  document.getElementById('fs').classList.remove('on');
  document.getElementById('fsBody').innerHTML = '';
  resetFsView();
  try { Android.setOrientation(0); } catch(e) {}
  try { Android.setFullscreen(false); } catch(e) {}
}

/* 顶栏：复制 + 全屏 */
function headHtml(label, copyText, fsHtml, fsTitle){
  return '<div class="bhead"><span>' + esc(label) + '</span>'
    + '<div class="ops">'
    + '<button data-act="copy" data-t="' + esc(copyText) + '">复制</button>'
    + '<button data-act="fs" data-title="' + esc(fsTitle || label) + '">全屏</button>'
    + '</div></div>';
}
function bindBoxOps(){
  var heads = document.querySelectorAll('.box .bhead');
  for (var i = 0; i < heads.length; i++){
    (function(head){
      var box = head.parentNode;
      var body = box.lastChild;
      var copyBtn = head.querySelector('button[data-act=copy]');
      var fsBtn = head.querySelector('button[data-act=fs]');
      // 展开按钮在代码框右下角
      var bodyEl = box.querySelector('.codebody');
      if (bodyEl && bodyEl.classList.contains('clamped')){
        var exp = document.createElement('button');
        exp.className = 'expand-btn';
        exp.textContent = ' 展开';
        exp.onclick = function(e){
          e.stopPropagation();
          var on = bodyEl.classList.toggle('clamped');
          var fade = bodyEl.querySelector('.fade');
          if (fade) fade.style.display = on ? '' : 'none';
          exp.textContent = on ? ' 展开' : '收起';
        };
        box.appendChild(exp);
      }
      if (copyBtn){
        copyBtn.onclick = function(e){
          e.stopPropagation();
          var t = copyBtn.getAttribute('data-t') || '';
          try { Android.copyText(t); } catch(err) {}
          copyBtn.textContent = '已复制';
          setTimeout(function(){ copyBtn.textContent = '复制'; }, 1200);
        };
      }
      if (fsBtn){
        fsBtn.onclick = function(e){
          e.stopPropagation();
          var t = fsBtn.getAttribute('data-title') || '';
          // 直接取已渲染的 body，不走 data 属性
          var real = body ? body.innerHTML : '';
          openFs(t, real);
        };
      }
    })(heads[i]);
  }
}

/* ---------- inline markdown ---------- */
function replaceBetween(t, mark, open, close){
  var out = '', i = 0;
  while (i < t.length){
    var s = t.indexOf(mark, i);
    if (s < 0){ out += t.slice(i); break; }
    var e = t.indexOf(mark, s + mark.length);
    if (e < 0){ out += t.slice(i); break; }
    out += t.slice(i, s) + open + t.slice(s + mark.length, e) + close;
    i = e + mark.length;
  }
  return out;
}
function replaceInlineMath(t){
  var out = '', i = 0;
  while (i < t.length){
    if (t.charAt(i) === D){
      if (t.charAt(i+1) === D){ out += D + D; i += 2; continue; }
      var e = t.indexOf(D, i + 1);
      if (e > i + 1){
        out += '<span class="math" data-tex="' + esc(t.slice(i+1, e)) + '"></span>';
        i = e + 1; continue;
      }
    }
    out += t.charAt(i); i++;
  }
  return out;
}
function replaceBold(t){
  var out = '', i = 0;
  while (i < t.length){
    var s1 = t.indexOf('**', i);
    if (s1 < 0){ out += t.slice(i); break; }
    var e1 = t.indexOf('**', s1 + 2);
    if (e1 < 0){ out += t.slice(i); break; }
    var inner = t.slice(s1 + 2, e1);
    var open = '<strong';
    var mode = (typeof BOLD_MODE !== 'undefined') ? BOLD_MODE : 'theme';
    if (mode === 'rainbow') {
      open += ' class="rainbow"';
    } else if (mode === 'colorful') {
      // 按当前 h/--- 分段序号取色
      var idx = (window.__secIdx | 0) % SEC_COLORS.length;
      open += ' style="color:' + SEC_COLORS[idx] + '"';
    } else if (mode && mode !== 'theme' && mode !== 'inherit') {
      open += ' style="color:' + mode + '"';
    } else {
      // 主题色：加粗用墨色（黑），不是主色
      open += ' style="color:inherit;font-weight:700"';
    }
    open += '>';
    out += t.slice(i, s1) + open + inner + '</strong>';
    i = e1 + 2;
  }
  return out;
}
function replaceLink(t){
  var out = '', i = 0;
  while (i < t.length){
    if (t.charAt(i) === '['){
      var mid = t.indexOf('](', i);
      var end = mid >= 0 ? t.indexOf(')', mid + 2) : -1;
      if (mid > i && end > mid){
        var url = t.slice(mid + 2, end);
        out += '<a href="' + esc(url) + '" data-u="' + esc(url) + '">'
             + t.slice(i + 1, mid) + '</a>';
        i = end + 1; continue;
      }
    }
    out += t.charAt(i); i++;
  }
  return out;
}
var BOLD_MODE = 'inherit';
function inlineMd(t){
  t = esc(t);
  t = replaceInlineMath(t);
  var parts = t.split('`'), out = '';
  for (var i = 0; i < parts.length; i++)
    out += (i % 2 === 1) ? '<code>' + parts[i] + '</code>' : parts[i];
  t = out;
  t = replaceBold(t);
  t = replaceBetween(t, '*', '<em>', '</em>');
  t = replaceLink(t);
  return t;
}

/* ---------- line classifiers ---------- */
function trim(s){ return String(s).replace(/^\s+/, '').replace(/\s+$/, ''); }
function isFence(line){ return trim(line).indexOf('```') === 0; }
function isHr(line){
  var t = trim(line); if (t.length < 3) return false;
  for (var i = 0; i < t.length; i++){
    var c = t.charAt(i);
    if (c !== '-' && c !== '*' && c !== '_') return false;
  }
  return true;
}
function isHeading(line){
  var t = trim(line), n = 0;
  while (n < t.length && t.charAt(n) === '#') n++;
  return n >= 1 && n <= 6 && n < t.length && t.charAt(n) === ' ';
}
function isQuote(line){ return trim(line).charAt(0) === '>'; }
function isUl(line){
  var t = trim(line);
  if (t.length < 2) return false;
  var c = t.charAt(0);
  return (c === '-' || c === '*' || c === '+') && t.charAt(1) === ' ';
}
function isOl(line){
  var t = trim(line);
  if (!t.length || t.charAt(0) < '0' || t.charAt(0) > '9') return false;
  var i = 0;
  while (i < t.length && t.charAt(i) >= '0' && t.charAt(i) <= '9') i++;
  return i < t.length && (t.charAt(i) === '.' || t.charAt(i) === ')')
    && i + 1 < t.length && t.charAt(i + 1) === ' ';
}
function isTableSep(line){
  var t = trim(line); if (!t.length) return false;
  var hasDash = false;
  for (var i = 0; i < t.length; i++){
    var c = t.charAt(i);
    if (c === '-') hasDash = true;
    else if (c !== '|' && c !== ':' && c !== ' ') return false;
  }
  return hasDash;
}
function splitRow(line){
  var t = trim(line);
  if (t.charAt(0) === '|') t = t.slice(1);
  if (t.charAt(t.length - 1) === '|') t = t.slice(0, -1);
  var parts = t.split('|');
  for (var i = 0; i < parts.length; i++) parts[i] = trim(parts[i]);
  return parts;
}
function isDisplayMathStart(line){ return trim(line).indexOf(D + D) === 0; }

/* ---------- block markdown ---------- */
var SEC_COLORS = ['#2F6FED','#C678DD','#E67E22','#27AE60','#E74C3C','#16A085'];
function blockMd(src){
  var out = '';
  var lines = String(src == null ? '' : src).split('\n');
  var i = 0;
  function flushList(buf, tag){
    if (!buf.length) return '';
    var h = '<' + tag + '>';
    for (var k = 0; k < buf.length; k++) h += '<li>' + inlineMd(buf[k]) + '</li>';
    return h + '</' + tag + '>';
  }
  while (i < lines.length){
    var line = lines[i];
    if (isFence(line)){
      var info = trim(line).slice(3).replace(/^\s+/, '').replace(/\s+$/, '');
      i++;
      var code = [];
      while (i < lines.length && !isFence(lines[i])){ code.push(lines[i]); i++; }
      i++;
      var body = code.join('\n');
      var lang = info || 'code';
      if (info === 'mermaid'){
        var mbox = document.createElement('div');
        mbox.className = 'box';
        mbox.innerHTML = headHtml('mermaid', body,
          '<div class="mmpad"><div class="mermaid">' + esc(body) + '</div></div>', 'Mermaid 图');
        var mbody = document.createElement('div');
        mbody.className = 'mmpad';
        mbody.innerHTML = '<div class="mermaid">' + esc(body) + '</div>';
        mbox.appendChild(mbody);
        out += mbox.outerHTML;
      } else {
        var lineN = code.length;
        var clamped = lineN > 6 && !window.__noFold;
        var cbox = document.createElement('div');
        cbox.className = 'box codebox-wrap';
        cbox.innerHTML = headHtml(lang, body,
          '<pre><code class="language-' + esc(lang) + '">' + esc(body) + '</code></pre>',
          lang + ' 代码');
        var cbody = document.createElement('div');
        cbody.className = 'codebody' + (clamped ? ' clamped' : '');
        cbody.innerHTML = '<pre><code class="language-' + esc(lang) + '">'
          + esc(body) + '</code></pre>'
          + (clamped ? '<div class="fade"></div>' : '');
        cbox.appendChild(cbody);
        out += cbox.outerHTML;
      }
      continue;
    }
    if (isDisplayMathStart(line)){
      var tr = trim(line);
      var tex = '';
      if (tr.length > 4 && tr.lastIndexOf(D + D) > 1){
        tex = tr.slice(2, tr.lastIndexOf(D + D)); i++;
      } else {
        i++;
        var acc = [];
        while (i < lines.length){
          if (trim(lines[i]) === D + D){ i++; break; }
          acc.push(lines[i]); i++;
        }
        tex = acc.join('\n');
      }
      var mb = document.createElement('div');
      mb.className = 'box';
      mb.innerHTML = headHtml('公式', tex,
        '<div class="mathpad"><div class="mathb" data-tex="' + esc(tex) + '"></div></div>',
        '公式');
      var mbp = document.createElement('div');
      mbp.className = 'mathpad';
      mbp.innerHTML = '<div class="mathb" data-tex="' + esc(tex) + '"></div>';
      mb.appendChild(mbp);
      out += mb.outerHTML;
      continue;
    }
    if (isHr(line)){ out += '<hr class="rule"/>'; i++; continue; }
    if (isHeading(line)){
      var t2 = trim(line), n = 0;
      while (n < t2.length && t2.charAt(n) === '#') n++;
      window.__secIdx = (window.__secIdx | 0);
      if (n <= 2) window.__secIdx = (window.__secIdx + 1) % 6;
      var sc = (window.__secIdx | 0) % 6;
      var hstyle = '';
      var bmH = (typeof BOLD_MODE !== 'undefined') ? BOLD_MODE : 'theme';
      if (bmH === 'colorful' || bmH === 'rainbow') {
        hstyle = ' style="color:' + SEC_COLORS[sc] + '"';
      } else if (bmH && bmH !== 'theme' && bmH !== 'inherit') {
        hstyle = ' style="color:' + bmH + '"';
      } else {
        // 主题色：标题=墨色（黑），不用蓝
        hstyle = ' style="color:inherit"';
      }
      out += '<h' + n + hstyle + '>'
           + inlineMd(t2.slice(n).replace(/^\s+/, '')) + '</h' + n + '>';
      i++; continue;
    }
    if (line.indexOf('|') >= 0 && i + 1 < lines.length && isTableSep(lines[i+1])){
      var headers = splitRow(line); i += 2;
      var rows = [];
      while (i < lines.length && lines[i].indexOf('|') >= 0 && trim(lines[i]) !== ''){
        rows.push(splitRow(lines[i])); i++;
      }
      var th = '';
      for (var c = 0; c < headers.length; c++) th += '<th>' + inlineMd(headers[c]) + '</th>';
      var tb = '';
      for (var r = 0; r < rows.length; r++){
        tb += '<tr>';
        for (var c2 = 0; c2 < headers.length; c2++)
          tb += '<td>' + inlineMd(rows[r][c2] == null ? '' : rows[r][c2]) + '</td>';
        tb += '</tr>';
      }
      out += '<table><thead><tr>' + th + '</tr></thead><tbody>' + tb + '</tbody></table>';
      continue;
    }
    if (isQuote(line)){
      var q = [];
      while (i < lines.length && isQuote(lines[i])){
        q.push(trim(lines[i]).replace(/^>\s?/, '')); i++;
      }
      out += '<blockquote>' + inlineMd(q.join(' ')) + '</blockquote>';
      continue;
    }
    if (isUl(line)){
      var ul = [];
      while (i < lines.length && isUl(lines[i])){ ul.push(trim(lines[i]).slice(2)); i++; }
      out += flushList(ul, 'ul'); continue;
    }
    if (isOl(line)){
      var ol = [];
      while (i < lines.length && isOl(lines[i])){
        var ot = trim(lines[i]), oi = 0;
        while (oi < ot.length && ot.charAt(oi) >= '0' && ot.charAt(oi) <= '9') oi++;
        ol.push(ot.slice(oi + 2)); i++;
      }
      out += flushList(ol, 'ol'); continue;
    }
    if (trim(line) === ''){ i++; continue; }
    var para = [line]; i++;
    while (i < lines.length){
      var l = lines[i];
      if (trim(l) === '') break;
      if (isFence(l) || isHr(l) || isHeading(l) || isQuote(l) || isUl(l) || isOl(l)) break;
      if (isDisplayMathStart(l)) break;
      if (l.indexOf('|') >= 0 && i + 1 < lines.length && isTableSep(lines[i+1])) break;
      para.push(l); i++;
    }
    out += '<p>' + inlineMd(para.join('\n')).split('\n').join('<br/>') + '</p>';
  }
  return out;
}

function renderMath(root, isFs){
  try {
    var inl = root.querySelectorAll('span.math');
    for (var i = 0; i < inl.length; i++)
      katex.render(inl[i].getAttribute('data-tex') || '', inl[i],
        {throwOnError:false, output:'html'});
    var blks = root.querySelectorAll('.mathb');
    for (var j = 0; j < blks.length; j++)
      katex.render(blks[j].getAttribute('data-tex') || '', blks[j],
        {displayMode:true, throwOnError:false, output:'html'});
  } catch(e) {}
}
function renderCode(root){
  try {
    if (!window.hljs) return;
    var codes = root.querySelectorAll('pre code');
    for (var i = 0; i < codes.length; i++){
      if (!codes[i].classList.contains('hljs')) hljs.highlightElement(codes[i]);
    }
  } catch(e) {}
}
function renderMermaid(){
  try {
    if (!window.mermaid) return;
    mermaid.initialize({startOnLoad:false,
      theme: document.body.getAttribute('data-dark') === '1' ? 'dark' : 'default'});
    var nodes = document.querySelectorAll('.mermaid');
    if (nodes.length) mermaid.run({querySelector:'.mermaid'});
  } catch(e) {}
}

function shortName(name){
  name = String(name == null ? '附件' : name);
  if (name.length <= 16) return name;
  return name.slice(0, 10) + '…' + name.slice(-4);
}
function attHtml(a){
  var tag = a.type === 'image' ? '图片' : (a.type === 'text' ? '文本' : '文件');
  var name = esc(shortName(a.name));
  if (a.type === 'image' && a.path)
    return '<div class="att"><img src="file://' + esc(a.path) + '"/>'
      + '<div class="tag">' + tag + '</div><div class="name">' + name + '</div></div>';
  return '<div class="att"><div style="width:64px;height:64px;border-radius:10px;'
    + 'background:rgba(128,128,128,0.18);display:flex;align-items:center;'
    + 'justify-content:center;font-size:12px;">' + tag + '</div>'
    + '<div class="name">' + name + '</div></div>';
}
function msgHtml(m){
  var isUser = m.role === 'user';
  var h = '<div class="msg ' + (isUser ? 'user' : 'ai') + '" id="msg-' + m.id + '">';
  h += '<div class="bubble">';
  if (m.attachments && m.attachments.length){
    if (m.attachments.length > 1)
      h += '<div style="font-size:11px;opacity:.75;margin-bottom:4px">共 '
        + m.attachments.length + ' 个附件</div>';
    h += '<div class="att-row">';
    for (var i = 0; i < m.attachments.length; i++) h += attHtml(m.attachments[i]);
    h += '</div>';
  }
  if (m.reasoning && String(m.reasoning).length){
    var open = m.thinking ? ' open' : '';
    var secs = m.thinkingMs ? ' · ' + (m.thinkingMs / 1000).toFixed(1) + 's' : '';
    h += '<details class="reason"' + open + '><summary>思考过程' + secs
      + '</summary><pre>' + esc(m.reasoning) + '</pre></details>';
  }
  if (m.content && String(m.content).length)
    h += '<div class="content">' + blockMd(m.content) + '</div>';
  else if (m.pending)
    h += '<div class="pending">正在思考…</div>';
  if (m.citations && m.citations.length){
    h += '<details class="cites"><summary>参考来源（' + m.citations.length + '）</summary><div class="cites-body">';
    for (var j = 0; j < m.citations.length; j++){
      var c = m.citations[j];
      h += '<div class="c" data-u="' + esc(c.url) + '">'
        + '<span class="title">' + esc(c.title || c.url) + '</span>'
        + (c.siteName ? ' · ' + esc(c.siteName) : '')
        + '<br/>' + esc(c.url) + '</div>';
    }
    h += '</div></details>';
  }
  if (!isUser && !m.pending && m.content){
    h += '<div class="actions">'
      + '<button onclick="Android.onAction(&quot;copy&quot;,' + m.id + ')">复制</button>'
      + '<button onclick="askConfirm(&quot;重新生成将覆盖当前回答？&quot;,function(){Android.onAction(&quot;regen&quot;,' + m.id + ')})">重新生成</button>'
      + '<button onclick="askConfirm(&quot;删除这条问答？不可恢复&quot;,function(){Android.onAction(&quot;delete&quot;,' + m.id + ')})">删除</button>'
      + '</div>';
  }
  return h + '</div></div>';
}
function streamHtml(s){
  if (!s || !s.active) return '';
  window.__noFold = true;
  var h = '<div class="msg ai" id="msg-stream"><div class="bubble">';
  if (s.reasoning && String(s.reasoning).length){
    h += '<details class="reason" open><summary>思考中 · '
      + Number(s.seconds || 0).toFixed(1) + 's</summary><pre>'
      + esc(s.reasoning) + '</pre></details>';
  }
  if (s.content && String(s.content).length)
    h += '<div class="content">' + blockMd(s.content) + '</div>';
  else
    h += '<div class="pending">正在思考…</div>';
  return h + '</div></div>';
}

function renderChat(msgs, streaming){
  var el = document.getElementById('list');
  if (!el) return;
  if (typeof msgs === 'string') msgs = JSON.parse(msgs);
  if (typeof streaming === 'string') streaming = JSON.parse(streaming);
  var prevTop = window.scrollY;
  var h = '';
  for (var i = 0; i < msgs.length; i++) h += msgHtml(msgs[i]);
  h += streamHtml(streaming);
  el.innerHTML = h;
  renderMath(el); renderCode(el); renderMermaid();
  bindBoxOps(); bindLinks();
  // 跟随：贴底；用户已操作：恢复原滚动位置，绝不抢
  window.__progUntil = Date.now() + 80;
  if (window.__followMode) {
    window.scrollTo(0, document.body.scrollHeight);
  } else {
    window.scrollTo(0, prevTop);
  }
  requestAnimationFrame(function(){
    setTimeout(function(){
      window.__progUntil = 0;
function isProg(){ return Date.now() < window.__progUntil; }
      notifyBottom();
    }, 30);
  });
}
function bindLinks(){
  var links = document.querySelectorAll('a[data-u]');
  for (var i = 0; i < links.length; i++){
    links[i].onclick = function(e){
      e.preventDefault();
      try { Android.openUrl(this.getAttribute('data-u')); } catch(err){}
      return false;
    };
  }
  var cites = document.querySelectorAll('.cites .c[data-u]');
  for (var j = 0; j < cites.length; j++){
    cites[j].onclick = function(){
      try { Android.openUrl(this.getAttribute('data-u')); } catch(err){}
    };
  }
}
/* 跟随状态机（唯一实现）
/* 状态机（严格按需求）：
   [*] --> Following: 用户提问
   Following --流式--> Following: 自动滚到底
   Following --用户滑动--> NotFollowing
   NotFollowing --流式--> NotFollowing: 保持位置
   NotFollowing --点返回底部--> Following
   Following --用户再滑动--> NotFollowing
*/
window.__followMode = true;
window.__progUntil = 0;
function isProg(){ return Date.now() < window.__progUntil; }

function atBottom(){
  return (window.innerHeight + window.scrollY) >= (document.body.scrollHeight - 48);
}
function notifyBottom(){
  var near = atBottom();
  try { Android.onScroll(near && window.__followMode); } catch(e) {}
}

/* —— 用户一操作就取消跟随（不依赖“是否离开底部”）——
   流式刷新快时 atBottom 会抖，猜位置不可靠；
   手势/滚轮/键盘是用户真实意图，立刻生效。 */
var _ty = null, _moved = false;
function cancelFollow(){
  // 用户手势必须无条件取消跟随。
  // 不能用 __progScroll 判断：流式每个 token 都会把它置 true，手势就永远被忽略。
  window.__followMode = false;
  window.__progUntil = 0;
function isProg(){ return Date.now() < window.__progUntil; }
}

// 手指一碰屏幕就取消跟随，不需要「使劲滑」
document.addEventListener('touchstart', function(e){
  if (e.touches.length === 1){
    _ty = e.touches[0].clientY;
    _moved = false;
    cancelFollow();
  }
}, {passive:true});

document.addEventListener('touchmove', function(e){
  if (e.touches.length === 1 && _ty != null){
    if (Math.abs(e.touches[0].clientY - _ty) > 2) {
      _moved = true;
      cancelFollow();
    }
  }
  notifyBottom();
}, {passive:true});

document.addEventListener('touchend', function(){ _ty = null; }, {passive:true});

document.addEventListener('wheel', function(e){
  if (e.deltaY !== 0) cancelFollow();
  notifyBottom();
}, {passive:true});

document.addEventListener('keydown', function(e){
  if (isProg()) return;
  var k = e.key;
  if (k === 'ArrowUp' || k === 'ArrowDown' || k === 'PageUp' ||
      k === 'PageDown' || k === 'Home' || k === 'End' || k === ' ') {
    cancelFollow();
  }
}, {passive:true});

window.addEventListener('scroll', notifyBottom, {passive:true});

// 点「返回底部」或用户提问 → 恢复跟随
window.__follow = function(){
  window.__followMode = true;
  window.__progUntil = Date.now() + 80;
  window.scrollTo(0, document.body.scrollHeight);
  requestAnimationFrame(function(){
    setTimeout(function(){
      window.__progUntil = 0;
function isProg(){ return Date.now() < window.__progUntil; }
      notifyBottom();
    }, 30);
  });
};

window.__jump = function(id){
  var el = document.getElementById('msg-' + id);
  if (el) el.scrollIntoView({behavior:'smooth', block:'start'});
};
window.__lastMsgCount = -1;
window.__update = function(msgs, streaming){
  if (typeof msgs === 'string') msgs = JSON.parse(msgs);
  if (typeof streaming === 'string') streaming = JSON.parse(streaming);
  // 流式中且消息条数没变：只替换最后一个 stream 节点，不动历史，不抢滚动
  var streamNode = document.getElementById('msg-stream');
  if (streamNode && streaming && streaming.active && msgs.length === window.__lastMsgCount){
    var prevTop = window.scrollY;
    window.__progUntil = Date.now() + 80;   // DOM 更新期间的 scroll 不算用户操作
    streamNode.outerHTML = streamHtml(streaming);
    var list = document.getElementById('list');
    renderMath(list); renderCode(list);
    bindBoxOps(); bindLinks();
    if (window.__followMode) {
      window.scrollTo(0, document.body.scrollHeight);
    } else {
      window.scrollTo(0, prevTop);
    }
    requestAnimationFrame(function(){
      setTimeout(function(){
        window.__progUntil = 0;
function isProg(){ return Date.now() < window.__progUntil; }
        notifyBottom();
      }, 30);
    });
    return;
  }
  window.__lastMsgCount = msgs.length;
  renderChat(msgs, streaming);
};
document.body.setAttribute('data-dark', '$darkFlag');
try {
  var bc = getComputedStyle(document.body).getPropertyValue('--bc').trim();
  if (bc) BOLD_MODE = bc;
} catch(e) {}
</script>
</body>
</html>
"""
}
