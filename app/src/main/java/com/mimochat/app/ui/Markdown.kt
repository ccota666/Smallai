package com.mimochat.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONObject

// 阈值：超过则降级为「按钮 + 摘要」，不再内嵌 WebView
private const val CODE_WEBVIEW_MAX_LINES = 40
private const val CODE_WEBVIEW_MAX_CHARS = 2000
private const val MATH_WEBVIEW_MAX_CHARS = 160

/** 聊天列表滚动中为 true — WebView 延后创建，避免滚动掉帧。 */
val LocalChatListScrolling = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 加粗/关键词颜色；Unspecified 表示跟随主题 */
val LocalBoldColor = androidx.compose.runtime.staticCompositionLocalOf { Color.Unspecified }

// ---------- Block model & parser ----------

sealed class Block {
    data class Text(val content: String) : Block()
    data class Code(val language: String, val code: String) : Block()
    data class Mermaid(val code: String) : Block()
    data class Math(val latex: String) : Block()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Block()
    data object Rule : Block()
}

object MarkdownParser {
    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = markdown.split("\n")
        var i = 0
        val textBuf = StringBuilder()

        fun flushText() {
            val t = textBuf.toString().trimEnd('\n')
            if (t.isNotBlank()) {
                // 内联公式留在文本块里，由 TextBlock 整段渲染（避免每个公式一个 WebView）
                blocks.add(Block.Text(t))
            }
            textBuf.clear()
        }

        fun isRule(line: String): Boolean {
            val t = line.trim()
            if (t.length < 3) return false
            if (!t.all { it == '-' || it == '*' || it == '_' }) return false
            return t.count { it == '-' } >= 3 || t.count { it == '*' } >= 3 || t.count { it == '_' } >= 3
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            // 去掉引用前缀，便于识别 > $$...$$ / > | 表格 等
            val bare = stripQuote(line)

            // 代码围栏：开栏 ```lang，闭栏必须是纯 ```，避免代码体里出现 ``` 时提前闭合
            val fence = Regex("^```\\s*(.*)$").find(line)
            if (fence != null) {
                flushText()
                val lang = fence.groupValues[1].trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !Regex("^```\\s*$").matches(lines[i])) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                i++
                if (lang.equals("mermaid", true)) {
                    blocks.add(Block.Mermaid(code.toString().trimEnd('\n')))
                } else {
                    blocks.add(Block.Code(lang, code.toString().trimEnd('\n')))
                }
                continue
            }

            // 块级公式 $$（含 > $$…$$ 引用块内）
            if (bare.startsWith("$$")) {
                if (bare.length > 4 && bare.endsWith("$$")) {
                    flushText()
                    blocks.add(Block.Math(bare.removePrefix("$$").removeSuffix("$$").trim()))
                    i++
                    continue
                }
                flushText()
                val math = StringBuilder()
                i++
                while (i < lines.size && !stripQuote(lines[i]).endsWith("$$")) {
                    math.append(stripQuote(lines[i])).append('\n')
                    i++
                }
                i++
                blocks.add(Block.Math(math.toString().trim()))
                continue
            }

            // GFM 表格：当前行含 |，且下一行是分隔行
            if (trimmed.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                flushText()
                val headers = splitTableRow(lines[i])
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trim().contains('|') && lines[i].isNotBlank()) {
                    rows.add(splitTableRow(lines[i]))
                    i++
                }
                blocks.add(Block.Table(headers, rows))
                continue
            }

            // 水平分割线 --- / *** / ___
            if (isRule(line)) {
                flushText()
                blocks.add(Block.Rule)
                i++
                continue
            }

            textBuf.append(line).append('\n')
            i++
        }
        flushText()
        return blocks
    }

    /** 去掉引用标记，返回正文内容（用于 $$ / 表格识别）。 */
    private fun stripQuote(line: String): String {
        var t = line.trim()
        while (t.startsWith(">")) {
            t = t.removePrefix(">").trim()
        }
        return t
    }

    private fun isTableSeparator(line: String): Boolean {
        val t = stripQuote(line)
        if (!t.contains('-')) return false
        return t.all { it == '|' || it == '-' || it == ':' || it == ' ' }
    }

    private fun splitTableRow(line: String): List<String> {
        var t = stripQuote(line)
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.substring(0, t.length - 1)
        return t.split("|").map { it.trim() }
    }

    fun hasInlineMath(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            if (text[i] == '$' && i + 1 < text.length && !text[i + 1].isWhitespace() && text[i + 1] != '$') {
                val end = findInlineMathEnd(text, i + 1)
                if (end > i + 1) return true
            }
            i++
        }
        return false
    }

    fun hasMathInTable(headers: List<String>, rows: List<List<String>>): Boolean {
        if (headers.any { hasInlineMath(it) }) return true
        return rows.any { r -> r.any { hasInlineMath(it) } }
    }
}

// ---------- Top-level markdown renderer ----------

private fun blocksHaveMath(blocks: List<Block>): Boolean {
    for (b in blocks) {
        when (b) {
            is Block.Math -> return true
            is Block.Text -> if (MarkdownParser.hasInlineMath(b.content)) return true
            is Block.Table -> if (MarkdownParser.hasMathInTable(b.headers, b.rows)) return true
            else -> {}
        }
    }
    return false
}

@Composable
fun MarkdownText(
    content: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    boldColor: Color = Color.Unspecified
) {
    val blocks = remember(content) { MarkdownParser.parse(content) }
    val hasMath = remember(blocks) { blocksHaveMath(blocks) }

    androidx.compose.runtime.CompositionLocalProvider(LocalBoldColor provides boldColor) {
    // 策略：
    // 1) 流式中 / 无公式 → Compose 原生块（代码也不开 WebView），滚动零负担
    // 2) 含公式且已结束 → 整回答（除 Mermaid）一个 WebView，KaTeX+highlight 一次加载
    if (live || !hasMath) {
        Column(modifier = modifier.fillMaxWidth()) {
            for (b in blocks) {
                when (b) {
                    is Block.Text -> TextBlock(b.content)
                    is Block.Code -> CodeBlock(b.code, b.language, dark, live)
                    is Block.Mermaid -> MermaidBlock(b.code, dark, live)
                    is Block.Math -> MathBlock(b.latex, dark, live)
                    is Block.Table -> TableBlock(b.headers, b.rows, dark, live)
                    is Block.Rule -> RuleDivider()
                }
            }
        }
    } else {
    // 含公式：按 Mermaid 切段，非 Mermaid 段合并成单 WebView
    val segments = remember(blocks) {
        val segs = mutableListOf<List<Block>>()
        val group = mutableListOf<Block>()
        for (b in blocks) {
            if (b is Block.Mermaid || b is Block.Rule) {
                if (group.isNotEmpty()) { segs.add(group.toList()); group.clear() }
                segs.add(listOf(b))
            } else {
                group.add(b)
            }
        }
        if (group.isNotEmpty()) segs.add(group.toList())
        segs
    }
    Column(modifier = modifier.fillMaxWidth()) {
        for (seg in segments) {
            if (seg.size == 1 && seg[0] is Block.Mermaid) {
                MermaidBlock((seg[0] as Block.Mermaid).code, dark, live = false)
            } else if (seg.size == 1 && seg[0] is Block.Rule) {
                RuleDivider()
            } else {
                MessageRichWeb(seg, dark, Modifier.fillMaxWidth().padding(vertical = 4.dp), boldColor)
            }
        }
    }
    }
    }
}

/** 渐变浅色横线：两端淡出、与两侧留白、上下加宽间距 */
@Composable
fun RuleDivider() {
    val colors = MaterialTheme.colorScheme
    val line = colors.outline.copy(alpha = 0.45f)
    Spacer(Modifier.height(14.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(2.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        line.copy(alpha = 0f),
                        line,
                        line,
                        line.copy(alpha = 0f)
                    ),
                    startX = 0f,
                    endX = Float.POSITIVE_INFINITY
                ),
                shape = RoundedCornerShape(1.dp)
            )
    )
    Spacer(Modifier.height(14.dp))
}

// ---------- Inline formatting (Compose path) ----------

@Composable
private fun TextBlock(content: String) {
    TextBlockCompose(content)
}

@Composable
private fun TextBlockCompose(content: String) {
    val colors = MaterialTheme.colorScheme
    val monoColor = colors.primary
    val monoBg = colors.primaryContainer.copy(alpha = 0.55f)
    val mathColor = colors.primary
    val mathBg = colors.primaryContainer.copy(alpha = 0.35f)
    val linkColor = colors.primary
    val mathFontSize = MaterialTheme.typography.bodyMedium.fontSize

    val annotated = buildAnnotatedString {
        val lines = content.split("\n")
        for ((idx, raw) in lines.withIndex()) {
            var line = raw
            var headingLevel = 0
            var bullet: String? = null
            var quote = false

            val h = Regex("^(#{1,6})\\s+(.*)$").find(line)
            if (h != null) {
                headingLevel = h.groupValues[1].length
                line = h.groupValues[2]
            } else {
                val b = Regex("^\\s*[-*]\\s+(.*)$").find(line)
                if (b != null) {
                    bullet = "•  "
                    line = b.groupValues[1]
                } else if (line.trimStart().startsWith("> ")) {
                    quote = true
                    line = line.trimStart().removePrefix("> ")
                } else if (line.trimStart().startsWith(">")) {
                    quote = true
                    line = line.trimStart().removePrefix(">").trimStart()
                }
            }

            val baseStyle = when {
                headingLevel == 1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                headingLevel >= 2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                quote -> SpanStyle(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)
                else -> SpanStyle(color = colors.onSurface)
            }

            if (quote) {
                withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append("│ ") }
            } else if (bullet != null) {
                withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(bullet) }
            }

            withStyle(baseStyle) {
                appendInline(this, line, monoColor, monoBg, mathColor, mathBg, linkColor, mathFontSize, LocalBoldColor.current)
            }
            if (idx != lines.size - 1) append('\n')
        }
    }

    SelectionContainer {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
    }
}

/**
 * 内联格式：`code`、**bold**、*italic*、[link](url)、$math$。
 * 内联公式只用样式展示 LaTeX 源，不再单独开 WebView（性能关键）。
 */
private fun appendInline(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    text: String,
    monoColor: androidx.compose.ui.graphics.Color,
    monoBg: androidx.compose.ui.graphics.Color,
    mathColor: androidx.compose.ui.graphics.Color,
    mathBg: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color,
    mathFontSize: androidx.compose.ui.unit.TextUnit,
    boldColorParam: Color = Color.Unspecified
) {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > 0) {
                builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = monoColor, background = monoBg)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        if (c == '$' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
            val end = findInlineMathEnd(text, i + 1)
            if (end > i + 1) {
                builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = mathColor,
                        background = mathBg,
                        fontSize = mathFontSize
                    )
                ) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        if (c == '*' && i + 1 < text.length && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > 0) {
                val boldCol = boldColorParam
                builder.withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = if (boldCol != Color.Unspecified) boldCol else Color.Unspecified
                    )
                ) { append(text.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        }
        if (c == '*') {
            val end = text.indexOf('*', i + 1)
            if (end > 0) {
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }
        if (c == '[') {
            val close = text.indexOf(']', i + 1)
            if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                val end = text.indexOf(')', close + 2)
                if (end > 0) {
                    builder.withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(text.substring(i + 1, close))
                    }
                    i = end + 1
                    continue
                }
            }
        }
        builder.append(c)
        i++
    }
}

/**
 * 内联公式结束位置：排除「$5 and $10」这类金额误判。
 * 约定：结束 `$` 前一个字符不能是空白，且公式体不能以空白开头/结尾。
 */
private fun findInlineMathEnd(text: String, from: Int): Int {
    var j = from
    while (j < text.length) {
        val c = text[j]
        if (c == '\n') return -1
        if (c == '$') {
            // 结束 $ 前不能是空白
            if (j > from && !text[j - 1].isWhitespace()) return j
            return -1
        }
        j++
    }
    return -1
}

// ---------- Fullscreen dialog ----------

@Composable
fun FullscreenDialog(
    title: String,
    startLandscape: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val activity = LocalContext.current as? Activity
    var landscape by remember { mutableStateOf(startLandscape) }

    LaunchedEffect(landscape) {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "关闭") }
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { landscape = !landscape }) {
                        Icon(Icons.Rounded.ScreenRotation, "横屏/竖屏")
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

// ---------- Shared action row ----------

@Composable
private fun BlockActionRow(
    label: String,
    code: String,
    fileName: String,
    onFullscreen: () -> Unit,
    onLandscape: () -> Unit,
    extra: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(code.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            Toast.makeText(context, if (ok) "已保存" else "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp)
                .widthIn(max = 120.dp)
        )
        Spacer(Modifier.weight(1f))
        extra()
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(code)) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Rounded.ContentCopy, "复制", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        IconButton(
            onClick = { saveLauncher.launch(fileName) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Rounded.FileDownload, "下载", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onFullscreen, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Fullscreen, "全屏", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onLandscape, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.ScreenRotation, "横屏全屏", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------- Code block ----------

private const val CODE_COLLAPSE_LINES = 8

@Composable
private fun CodeBlock(code: String, language: String, dark: Boolean, live: Boolean) {
    val colors = MaterialTheme.colorScheme
    var full by remember { mutableStateOf<Boolean?>(null) }
    val totalLines = remember(code) { code.lineSequence().count() }
    val collapsible = totalLines > CODE_COLLAPSE_LINES
    var expanded by remember(code) { mutableStateOf(!collapsible) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column {
            BlockActionRow(
                label = language.ifBlank { "code" },
                code = code,
                fileName = "code.${langToExt(language)}",
                onFullscreen = { full = false },
                onLandscape = { full = true }
            ) {
                if (collapsible && !live) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            if (expanded) "折叠" else "展开全部",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.35f))
            val shown = when {
                live -> code.lineSequence().take(30).joinToString("\n")
                expanded -> code
                else -> code.lineSequence().take(CODE_COLLAPSE_LINES).joinToString("\n")
            }
            CodeHighlightText(
                code = shown,
                dark = dark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .heightIn(max = if (live) 260.dp else 2400.dp)
            )
            if (!expanded && collapsible) {
                Text(
                    "已折叠 ${totalLines} 行中的后 ${totalLines - CODE_COLLAPSE_LINES} 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            if (live) {
                Text(
                    "流式显示中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }

    full?.let { landscape ->
        FullscreenDialog(
            title = language.ifBlank { "代码" },
            startLandscape = landscape,
            onDismiss = { full = null }
        ) {
            CodeWeb(code, language, dark, Modifier.fillMaxSize())
        }
    }
}

/** 纯 Compose 代码高亮 — 不启动 WebView，滚动零开销。 */
@Composable
private fun CodeHighlightText(code: String, dark: Boolean, modifier: Modifier = Modifier) {
    val annotated = remember(code, dark) { highlightCodeAnnotated(code, dark) }
    SelectionContainer {
        Text(
            text = annotated,
            color = if (dark) Color(0xFFEDE6D8) else Color(0xFF2F2B25),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier
        )
    }
}

/** 轻量关键字/字符串/注释着色（覆盖常见语言即可）。 */
private fun highlightCodeAnnotated(code: String, dark: Boolean): AnnotatedString {
    val comment = if (dark) Color(0xFF8A8172) else Color(0xFF8A8172)
    val keyword = if (dark) Color(0xFF7FA08C) else Color(0xFF5F7D6B)
    val string = if (dark) Color(0xFF7FA07F) else Color(0xFF6B8F6E)
    val number = if (dark) Color(0xFFC4A76A) else Color(0xFFB8924A)
    val title = if (dark) Color(0xFF7A9AAB) else Color(0xFF6A8899)

    val kw = Regex(
        """\b(fun|val|var|class|object|interface|package|import|return|if|else|when|for|while|do|try|catch|finally|throw|in|is|as|null|true|false|this|super|override|private|public|internal|protected|companion|data|sealed|enum|suspend|const|lateinit|by|constructor|init|typealias|where|vararg|inline|operator|external|abstract|final|open|out|reified|expect|actual|
        def|lambda|yield|pass|break|continue|global|nonlocal|assert|del|with|async|await|elif|except|raise|from|export|default|function|typeof|instanceof|new|delete|void|struct|typedef|static|extern|sizeof|union|public|private|protected|template|namespace|using|auto|register|volatile|goto|switch|case|enum|constexpr|noexcept|nullptr|true|false|bool|int|float|double|char|void|string|String|let|const|static|extends|implements|abstract|throws|interface|void|package)\b"""
    )
    return buildAnnotatedString {
        val lines = code.split('\n')
        for ((li, line) in lines.withIndex()) {
            var i = 0
            val n = line.length
            while (i < n) {
                val c = line[i]
                // 行注释
                if ((c == '/' && i + 1 < n && line[i + 1] == '/') ||
                    (c == '#') ||
                    (c == '-' && i + 1 < n && line[i + 1] == '-')
                ) {
                    withStyle(SpanStyle(color = comment, fontStyle = FontStyle.Italic)) {
                        append(line.substring(i))
                    }
                    i = n
                    continue
                }
                // 字符串
                if (c == '"' || c == '\'' || c == '`') {
                    val end = line.indexOf(c, i + 1).let { if (it < 0) n else it + 1 }
                    withStyle(SpanStyle(color = string)) {
                        append(line.substring(i, minOf(end, n)))
                    }
                    i = end
                    continue
                }
                // 数字
                if (c.isDigit()) {
                    var j = i
                    while (j < n && (line[j].isDigit() || line[j] == '.' || line[j] == 'x' || line[j] in 'a'..'f' || line[j] in 'A'..'F' || line[j] == 'L')) j++
                    withStyle(SpanStyle(color = number)) { append(line.substring(i, j)) }
                    i = j
                    continue
                }
                // 标识符 / 关键字
                if (c.isLetter() || c == '_') {
                    var j = i
                    while (j < n && (line[j].isLetterOrDigit() || line[j] == '_')) j++
                    val word = line.substring(i, j)
                    val isKw = kw.matches(word)
                    val isType = word.first().isUpperCase() && word.length > 1
                    when {
                        isKw -> withStyle(SpanStyle(color = keyword, fontWeight = FontWeight.Bold)) { append(word) }
                        isType -> withStyle(SpanStyle(color = title)) { append(word) }
                        else -> append(word)
                    }
                    i = j
                    continue
                }
                append(c)
                i++
            }
            if (li != lines.size - 1) append('\n')
        }
    }
}

// ---------- Mermaid block ----------

@Composable
private fun MermaidBlock(code: String, dark: Boolean, live: Boolean) {
    var full by remember { mutableStateOf<Boolean?>(null) }
    // mermaid.min.js 约 3.3MB，默认不加载；点击「渲染」或进全屏才创建 WebView
    var inlineLoaded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column {
            BlockActionRow(
                label = "mermaid",
                code = code,
                fileName = "diagram.mmd",
                onFullscreen = { full = false },
                onLandscape = { full = true }
            ) {
                if (!inlineLoaded && full == null) {
                    IconButton(
                        onClick = { inlineLoaded = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            "渲染图表",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.35f))
            if (live || !inlineLoaded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .clickable(enabled = !live) { inlineLoaded = true }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (live) "图表生成中…" else "Mermaid 图表 · 点击渲染或全屏查看",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    if (!live) {
                        Text(
                            code.lineSequence().take(3).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            } else {
                MermaidWeb(code, dark, Modifier.fillMaxWidth().height(280.dp), zoomable = false)
            }
        }
    }

    full?.let { landscape ->
        FullscreenDialog("图表", landscape, onDismiss = { full = null }) {
            MermaidWeb(code, dark, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MermaidWeb(code: String, dark: Boolean, modifier: Modifier, zoomable: Boolean = true) {
    val html = remember(code, dark) { MERMAID_HTML }
    LoadableWebView(
        html = html,
        modifier = modifier,
        zoomable = zoomable,
        delayMountMs = if (zoomable) 0L else 200L,
        onReady = { view ->
            val theme = if (dark) "dark" else "default"
            view.evaluateJavascript(
                "renderMermaid(${JSONObject.quote(code)}, ${JSONObject.quote(theme)});",
                null
            )
        }
    )
}

// ---------- Math (KaTeX) block ----------

@Composable
private fun MathBlock(latex: String, dark: Boolean, live: Boolean) {
    var full by remember { mutableStateOf<Boolean?>(null) }
    val colors = MaterialTheme.colorScheme
    val heavy = latex.length > MATH_WEBVIEW_MAX_CHARS
    val useWebView = !live && !heavy

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column {
            BlockActionRow(
                label = "公式",
                code = latex,
                fileName = "formula.tex",
                onFullscreen = { full = false },
                onLandscape = { full = true }
            )
            HorizontalDivider(color = colors.outline.copy(alpha = 0.35f))
            if (useWebView) {
                MathWeb(latex, dark, Modifier.fillMaxWidth(), zoomable = false)
            } else {
                Text(
                    latex,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    maxLines = if (live) 4 else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }

    full?.let { landscape ->
        FullscreenDialog("公式", landscape, onDismiss = { full = null }) {
            MathWeb(latex, dark, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MathWeb(latex: String, dark: Boolean, modifier: Modifier = Modifier, zoomable: Boolean = true) {
    val html = remember(latex, dark) { KATEX_HTML(dark) }
    // JS scrollHeight 是 CSS 像素 ≈ dp，不能用 toDp()（那会再除以 density 导致截断）
    var contentHeight by remember(latex) { mutableStateOf(0) }
    val autoModifier = if (contentHeight > 0) {
        Modifier.height(contentHeight.dp + 8.dp)
    } else {
        Modifier.heightIn(min = 88.dp, max = 280.dp)
    }

    LoadableWebView(
        html = html,
        modifier = modifier.then(autoModifier),
        zoomable = zoomable,
        // 全屏立即加载；列表内停稳后再加载
        delayMountMs = if (zoomable) 0L else 350L,
        onReady = { view ->
            // 渲染 + 量高在同一脚本内顺序执行
            view.evaluateJavascript(
                "renderKatex(${JSONObject.quote(latex)}, true); measureKatex();",
                null
            )
        },
        onContentHeight = { h -> if (h > contentHeight) contentHeight = h }
    )
}

// ---------- Rich text (text + inline math via one KaTeX WebView) ----------

@Composable
private fun RichTextWeb(content: String, dark: Boolean, modifier: Modifier = Modifier, boldColor: Color = Color.Unspecified) {
    val html = remember(content, dark, boldColor) { richHtml(content, dark, boldColor) }
    LoadableWebView(
        html = html,
        modifier = modifier,
        zoomable = false,
        onReady = { it.evaluateJavascript("renderRich();", null) }
    )
}

/** 把带 $math$ 的 markdown-ish 文本转成 HTML，再由 JS 调 KaTeX。 */
private fun boldColorCss(boldColor: Color): String {
    return if (boldColor == Color.Unspecified) "inherit"
    else String.format("#%06X", 0xFFFFFF and boldColor.toArgb())
}
private fun richHtml(text: String, dark: Boolean, boldColor: Color = Color.Unspecified): String {
    val fg = if (dark) "#EDE6D8" else "#2F2B25"
    val muted = if (dark) "#B8AE9C" else "#5A5348"
    val accent = if (dark) "#7FA08C" else "#5F7D6B"
    val quoteBg = if (dark) "#2A3530" else "#E4EEE6"
    val codeBg = if (dark) "#2A3530" else "#E8E2D6"

    val htmlBody = buildString {
        for (raw in text.split("\n")) {
            val line = raw.trimEnd()
            val t = line.trim()
            when {
                t.isEmpty() -> append("<div class='sp'></div>")
                Regex("^#{1,6}\\s+").containsMatchIn(t) -> {
                    val level = t.takeWhile { it == '#' }.length
                    val body = t.dropWhile { it == '#' }.trim()
                    append("<h${level}>").append(inlineToHtml(body)).append("</h${level}>")
                }
                t.startsWith(">") -> {
                    val body = t.removePrefix(">").trim()
                    append("<blockquote>").append(inlineToHtml(body)).append("</blockquote>")
                }
                Regex("^[-*]\\s+").containsMatchIn(t) -> {
                    val body = Regex("^[-*]\\s+").replace(t, "")
                    append("<div class='li'>• ").append(inlineToHtml(body)).append("</div>")
                }
                else -> append("<p>").append(inlineToHtml(line)).append("</p>")
            }
        }
    }

    return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script>
<style>
html,body{margin:0;padding:4px 2px;background:transparent;color:$fg;--bc:${boldColorCss(boldColor)};
  font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;
  font-size:15px;line-height:1.65;overflow-wrap:break-word;}
p{margin:0 0 8px 0;}
h1{font-size:1.35em;margin:10px 0 6px;}
h2{font-size:1.2em;margin:10px 0 6px;}
h3,h4,h5,h6{font-size:1.08em;margin:8px 0 4px;}
blockquote{margin:6px 0;padding:6px 12px;border-left:3px solid $accent;
  background:$quoteBg;color:$muted;font-style:italic;}
.li{margin:2px 0 2px 4px;}
.sp{height:6px;}
code{font-family:ui-monospace,Consolas,monospace;font-size:0.9em;
  background:$codeBg;padding:1px 5px;border-radius:4px;}
a{color:$accent;}
strong{font-weight:600;color:var(--bc,inherit);}
hr.rule{border:none;height:2px;margin:16px 12px;background:linear-gradient(90deg,transparent,rgba(128,128,128,0.45),transparent);}
.math{color:$fg;}
</style>
</head><body id="c">$htmlBody</body>
<script>
function renderRich(){
  document.querySelectorAll('span.math,div.mathb').forEach(function(el){
    try{
      var display = el.classList.contains('mathb');
      katex.render(el.getAttribute('data-tex') || el.textContent, el, {
        displayMode: display, throwOnError: false, output:'html'
      });
    }catch(e){ el.textContent = (el.getAttribute('data-tex')||el.textContent); }
  });
}
</script></body></html>
""".trimIndent()
}

/** 行内：`code`、**bold**、*italic*、[text](url)、$math$。 */
private fun inlineToHtml(text: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > 0) {
                sb.append("<code>").append(escapeHtml(text.substring(i + 1, end))).append("</code>")
                i = end + 1
                continue
            }
        }
        if (c == '$' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
            val end = findInlineMathEnd(text, i + 1)
            if (end > i + 1) {
                val tex = text.substring(i + 1, end)
                sb.append("<span class='math' data-tex='")
                    .append(escapeHtml(tex).replace("'", "&#39;"))
                    .append("'>")
                    .append(escapeHtml(tex))
                    .append("</span>")
                i = end + 1
                continue
            }
        }
        if (c == '*' && i + 1 < text.length && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end > 0) {
                sb.append("<strong>").append(inlineToHtml(text.substring(i + 2, end))).append("</strong>")
                i = end + 2
                continue
            }
        }
        if (c == '*') {
            val end = text.indexOf('*', i + 1)
            if (end > 0) {
                sb.append("<em>").append(inlineToHtml(text.substring(i + 1, end))).append("</em>")
                i = end + 1
                continue
            }
        }
        if (c == '[') {
            val close = text.indexOf(']', i + 1)
            if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                val end = text.indexOf(')', close + 2)
                if (end > 0) {
                    val label = text.substring(i + 1, close)
                    val href = text.substring(close + 2, end)
                    sb.append("<a href=\"").append(escapeHtml(href)).append("\">")
                        .append(escapeHtml(label)).append("</a>")
                    i = end + 1
                    continue
                }
            }
        }
        if (c == '\n') {
            sb.append("<br/>")
        } else {
            sb.append(escapeHtml(c.toString()))
        }
        i++
    }
    return sb.toString()
}

// ---------- Table ----------

@Composable
private fun TableBlock(headers: List<String>, rows: List<List<String>>, dark: Boolean, live: Boolean) {
    val hasMath = MarkdownParser.hasMathInTable(headers, rows)
    if (hasMath && !live) {
        // 含公式的表格：整表一个 WebView，列宽由 HTML table 自然对齐
        RichTableWeb(headers, rows, dark, Modifier.fillMaxWidth().padding(vertical = 4.dp))
        return
    }
    ComposeTable(headers, rows)
}

@Composable
private fun ComposeTable(headers: List<String>, rows: List<List<String>>) {
    val colors = MaterialTheme.colorScheme
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return

    // 列宽统一：按该列最长内容估算，保证各行对齐
    val colWidths = remember(headers, rows, colCount) {
        List(colCount) { ci ->
            val texts = mutableListOf<String>()
            headers.getOrNull(ci)?.let { texts.add(it) }
            rows.forEach { texts.add(it.getOrNull(ci) ?: "") }
            var maxUnits = 1
            for (t in texts) {
                if (t.isEmpty()) continue
                var u = 0
                for (ch in t) u += if (ch.code > 0x7F) 2 else 1
                if (u > maxUnits) maxUnits = u
            }
            val estDp = (maxUnits * 4.2f + 22f)
            estDp.coerceIn(72f, 220f)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            // 表头
            Row(
                Modifier
                    .background(colors.primaryContainer.copy(alpha = 0.45f))
                    .padding(vertical = 8.dp)
            ) {
                for (ci in 0 until colCount) {
                    Text(
                        headers.getOrNull(ci) ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(colWidths[ci].dp)
                            .padding(horizontal = 10.dp)
                    )
                    if (ci < colCount - 1) VerticalCellDivider()
                }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
            rows.forEachIndexed { ri, row ->
                Row(
                    Modifier
                        .background(
                            if (ri % 2 == 1) colors.surfaceVariant.copy(alpha = 0.35f)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .padding(vertical = 8.dp)
                ) {
                    for (ci in 0 until colCount) {
                        Text(
                            row.getOrNull(ci) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(colWidths[ci].dp)
                                .padding(horizontal = 10.dp)
                        )
                        if (ci < colCount - 1) VerticalCellDivider()
                    }
                }
                if (ri < rows.size - 1) {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun RichTableWeb(headers: List<String>, rows: List<List<String>>, dark: Boolean, modifier: Modifier = Modifier) {
    val html = remember(headers, rows, dark) { tableHtml(headers, rows, dark) }
    LoadableWebView(
        html = html,
        modifier = modifier,
        zoomable = false,
        onReady = { it.evaluateJavascript("renderRich();", null) }
    )
}

private fun tableHtml(headers: List<String>, rows: List<List<String>>, dark: Boolean): String {
    val fg = if (dark) "#EDE6D8" else "#2F2B25"
    val headBg = if (dark) "#2A3530" else "#E4EEE6"
    val border = if (dark) "#4A443B" else "#CFC5B2"
    val codeBg = if (dark) "#2A3530" else "#E8E2D6"
    val accent = if (dark) "#7FA08C" else "#5F7D6B"

    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    val headCells = (0 until colCount).joinToString("") {
        "<th>${inlineToHtml(headers.getOrNull(it) ?: "")}</th>"
    }
    val bodyRows = rows.joinToString("") { row ->
        val tds = (0 until colCount).joinToString("") {
            "<td>${inlineToHtml(row.getOrNull(it) ?: "")}</td>"
        }
        "<tr>$tds</tr>"
    }

    return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script>
<style>
html,body{margin:0;padding:6px 2px;background:transparent;color:$fg;
  font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;
  font-size:14px;line-height:1.55;overflow-wrap:break-word;}
table{border-collapse:collapse;width:100%;table-layout:auto;}
th,td{border:1px solid rgba(128,128,128,0.45);padding:8px 10px;text-align:left;vertical-align:top;}
th{background:$headBg;font-weight:600;}
tr:nth-child(even) td{background:rgba(0,0,0,0.03);}
code{font-family:ui-monospace,Consolas,monospace;font-size:0.9em;
  background:$codeBg;padding:1px 5px;border-radius:4px;}
a{color:$accent;}
.math{color:$fg;}
</style>
</head><body id="c">
<table>
<thead><tr>$headCells</tr></thead>
<tbody>$bodyRows</tbody>
</table>
</body>
<script>
function renderRich(){
  document.querySelectorAll('span.math,div.mathb').forEach(function(el){
    try{
      var display = el.classList.contains('mathb');
      katex.render(el.getAttribute('data-tex') || el.textContent, el, {
        displayMode: display, throwOnError: false, output:'html'
      });
    }catch(e){ el.textContent = (el.getAttribute('data-tex')||el.textContent); }
  });
}
</script></body></html>
""".trimIndent()
}

@Composable
private fun VerticalCellDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    )
}

// ---------- Shared WebView with zoom + lazy load ----------

/**
 * 统一 WebView：先显示加载圈，页面就绪后展示；全屏场景开启双指缩放。
 * - 列表滚动中不创建 WebView（关键性能优化）
 * - delayMountMs：停稳后再挂载
 * - onContentHeight：渲染后回传 body 高度(px)
 */
@Composable
private fun LoadableWebView(
    html: String,
    modifier: Modifier = Modifier,
    zoomable: Boolean = true,
    delayMountMs: Long = 180L,
    jsInterface: Pair<String, Any>? = null,
    onReady: ((WebView) -> Unit)? = null,
    onContentHeight: ((Int) -> Unit)? = null
) {
    val scrolling = LocalChatListScrolling.current
    var loaded by remember(html, zoomable) { mutableStateOf(false) }
    var mount by remember(html, zoomable) { mutableStateOf(false) }
    LaunchedEffect(html, zoomable, scrolling) {
        if (scrolling) return@LaunchedEffect // 滚动中不启动；停稳后本 effect 会重启
        if (delayMountMs > 0) delay(delayMountMs)
        if (!scrolling) mount = true
    }

    Box(modifier) {
        if (!mount || !loaded) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (mount) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (loaded) Modifier else Modifier.alpha(0f)),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.setSupportZoom(zoomable)
                        settings.builtInZoomControls = zoomable
                        settings.displayZoomControls = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        jsInterface?.let { addJavascriptInterface(it.second, it.first) }
                        setWebViewClient(object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (view == null) return
                                onReady?.invoke(view)
                                loaded = true
                                if (onContentHeight != null) {
                                    fun measure() {
                                        view.evaluateJavascript(
                                            "Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)"
                                        ) { v ->
                                            val h = v?.trim('"')?.toIntOrNull()
                                            if (h != null && h > 0) onContentHeight(h)
                                        }
                                    }
                                    // 渲染 / 字体 / 布局可能晚到，多次复测取最大，避免截断
                                    view.postDelayed({ measure() }, 80)
                                    view.postDelayed({ measure() }, 250)
                                    view.postDelayed({ measure() }, 600)
                                }
                            }
                        })
                        loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                    }
                },
                update = { web ->
                    if (web.tag != html.hashCode()) {
                        web.tag = html.hashCode()
                        loaded = false
                        web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                    }
                }
            )
        }
    }
}

// ---------- Message-level single WebView (math + code + tables together) ----------

private class MessageJsBridge(
    private val onOpenCode: (String, String) -> Unit,
    private val onOpenMath: (String) -> Unit,
    private val onHeightChanged: (Int) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun openCode(lang: String?, code: String?) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onOpenCode(lang ?: "", code ?: "")
        }
    }

    @android.webkit.JavascriptInterface
    fun openMath(tex: String?) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onOpenMath(tex ?: "")
        }
    }

    @android.webkit.JavascriptInterface
    fun onHeightChanged(h: String?) {
        val v = h?.toIntOrNull() ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onHeightChanged(v)
        }
    }
}

@Composable
private fun MessageRichWeb(blocks: List<Block>, dark: Boolean, modifier: Modifier = Modifier, boldColor: Color = Color.Unspecified) {
    val html = remember(blocks, dark, boldColor) { messageHtml(blocks, dark, boldColor) }
    // JS scrollHeight 是 CSS 像素 ≈ dp（用 .dp，绝不能 toDp()）
    var contentHeight by remember(blocks, dark) { mutableStateOf(0) }
    val heightMod = if (contentHeight > 0) {
        Modifier.height(contentHeight.dp + 6.dp)
    } else {
        Modifier.heightIn(min = 64.dp, max = 600.dp)
    }

    // 全屏请求：代码 / 公式
    var fullCode by remember { mutableStateOf<Pair<String, String>?>(null) } // lang to code
    var fullMath by remember { mutableStateOf<String?>(null) }

    val bridge = remember {
        MessageJsBridge(
            onOpenCode = { lang, code -> fullCode = lang to code },
            onOpenMath = { tex -> fullMath = tex },
            onHeightChanged = { h -> if (h > contentHeight) contentHeight = h }
        )
    }

    LoadableWebView(
        html = html,
        modifier = modifier.then(heightMod),
        zoomable = false,
        delayMountMs = 350L,
        jsInterface = "AndroidBridge" to bridge,
        onReady = { it.evaluateJavascript("renderAll();", null) },
        onContentHeight = { h -> if (h > contentHeight) contentHeight = h }
    )

    fullCode?.let { (lang, code) ->
        FullscreenDialog(
            title = lang.ifBlank { "代码" },
            startLandscape = false,
            onDismiss = { fullCode = null }
        ) {
            CodeWeb(code, lang, dark, Modifier.fillMaxSize(), zoomable = true)
        }
    }
    fullMath?.let { tex ->
        FullscreenDialog(
            title = "公式",
            startLandscape = false,
            onDismiss = { fullMath = null }
        ) {
            MathWeb(tex, dark, Modifier.fillMaxSize(), zoomable = true)
        }
    }
}

/** 把已解析的 blocks 拼成单页 HTML（KaTeX + highlight.js 各只加载一次）。 */
private fun messageHtml(blocks: List<Block>, dark: Boolean, boldColor: Color = Color.Unspecified): String {
    val fg = if (dark) "#EDE6D8" else "#2F2B25"
    val muted = if (dark) "#B8AE9C" else "#5A5348"
    val accent = if (dark) "#7FA08C" else "#5F7D6B"
    val quoteBg = if (dark) "#2A3530" else "#E4EEE6"
    val codeBg = if (dark) "#25221E" else "#F0EBE1"
    val codeHead = if (dark) "#2A3530" else "#E8E2D6"
    val border = if (dark) "#4A443B" else "#CFC5B2"
    val btnBg = if (dark) "#3A3530" else "#E8E2D6"

    val body = buildString {
        for (b in blocks) {
            when (b) {
                is Block.Text -> {
                    for (line in b.content.split("\n")) {
                        val t = line.trim()
                        when {
                            t.isEmpty() -> append("<div class='sp'></div>")
                            Regex("^#{1,6}\\s+").containsMatchIn(t) -> {
                                val level = t.takeWhile { it == '#' }.length.coerceIn(1, 6)
                                val rest = t.dropWhile { it == '#' }.trim()
                                append("<h${level}>").append(inlineToHtml(rest)).append("</h${level}>")
                            }
                            t.startsWith(">") -> {
                                append("<blockquote>").append(inlineToHtml(t.removePrefix(">").trim())).append("</blockquote>")
                            }
                            Regex("^[-*]\\s+").containsMatchIn(t) -> {
                                val rest = Regex("^[-*]\\s+").replace(t, "")
                                append("<div class='li'>• ").append(inlineToHtml(rest)).append("</div>")
                            }
                            else -> append("<p>").append(inlineToHtml(line)).append("</p>")
                        }
                    }
                }
                is Block.Code -> {
                    val cls = if (b.language.isNotBlank()) "language-${b.language.lowercase()}" else ""
                    val langAttr = escapeHtml(b.language)
                    val codeEsc = escapeHtml(b.code)
                    val longCode = b.code.lineSequence().count() > 8
                    val collapsedClass = if (longCode) " collapsed" else ""
                    val toggleBtn = if (longCode) {
                        "<button class='blk-btn' onclick=\"toggleCode(this)\">展开全部</button>"
                    } else ""
                    append(
                        """
                        <div class='codebox$collapsedClass'>
                          <div class='codehead'>
                            <span class='codelang'>${langAttr.ifBlank { "code" }}</span>
                            $toggleBtn
                            <button class='blk-btn' onclick="copyCode(this)">复制</button>
                            <button class='blk-btn' data-lang='${langAttr}' onclick="openCodeFull(this)">全屏</button>
                          </div>
                          <pre><code class='$cls'>$codeEsc</code></pre>
                        </div>
                        """.trimIndent()
                    )
                }
                is Block.Math -> {
                    val texAttr = escapeHtml(b.latex).replace("\"", "&quot;")
                    append(
                        """
                        <div class='mathbox'>
                          <div class='codehead'>
                            <span class='codelang'>公式</span>
                            <button class='blk-btn' onclick="copyMath(this)">复制</button>
                            <button class='blk-btn' onclick="openMathFull(this)">全屏</button>
                          </div>
                          <div class='mathb' data-tex="$texAttr"></div>
                        </div>
                        """.trimIndent()
                    )
                }
                is Block.Table -> {
                    val colCount = maxOf(b.headers.size, b.rows.maxOfOrNull { it.size } ?: 0)
                    val head = (0 until colCount).joinToString("") {
                        "<th>${inlineToHtml(b.headers.getOrNull(it) ?: "")}</th>"
                    }
                    val bodyRows = b.rows.joinToString("") { row ->
                        val tds = (0 until colCount).joinToString("") {
                            "<td>${inlineToHtml(row.getOrNull(it) ?: "")}</td>"
                        }
                        "<tr>$tds</tr>"
                    }
                    append("<div class='tablewrap'><table><thead><tr>$head</tr></thead><tbody>$bodyRows</tbody></table></div>")
                }
                is Block.Rule -> {
                    append("<hr class='rule'/>")
                }
                is Block.Mermaid -> { /* 单 WebView 路径不包含 mermaid */ }
            }
        }
    }

    return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script>
<script src="highlight.min.js"></script>
<style>
html,body{margin:0;padding:2px 2px;background:transparent;color:$fg;--bc:${boldColorCss(boldColor)};
  font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;
  font-size:15px;line-height:1.65;overflow-wrap:break-word;}
p{margin:0 0 8px 0;}
h1{font-size:1.35em;margin:10px 0 6px;}
h2{font-size:1.2em;margin:10px 0 6px;}
h3,h4,h5,h6{font-size:1.08em;margin:8px 0 4px;}
blockquote{margin:6px 0;padding:6px 12px;border-left:3px solid $accent;
  background:$quoteBg;color:$muted;font-style:italic;}
.li{margin:2px 0 2px 4px;}
.sp{height:6px;}
code{font-family:ui-monospace,Consolas,monospace;font-size:0.9em;
  background:$codeBg;padding:1px 5px;border-radius:4px;}
a{color:$accent;}
strong{font-weight:600;color:var(--bc,inherit);}
hr.rule{border:none;height:2px;margin:16px 12px;background:linear-gradient(90deg,transparent,rgba(128,128,128,0.45),transparent);}
.codebox,.mathbox{margin:10px 0;border:1px solid rgba(128,128,128,0.45);border-radius:12px;overflow:hidden;background:$codeBg;}
.codehead{display:flex;align-items:center;gap:6px;padding:6px 10px;background:$codeHead;}
.codelang{font-size:12px;color:$muted;flex:1;}
.blk-btn{border:0;border-radius:8px;padding:4px 10px;font-size:12px;
  background:$btnBg;color:$fg;cursor:pointer;}
.codebox pre{margin:0;padding:12px 14px;overflow-x:auto;}
.codebox pre code{background:transparent;padding:0;font-size:12.5px;line-height:1.55;color:$fg;}
.codebox.collapsed pre{max-height:10.5em;overflow:hidden;}
.mathb{padding:12px 14px;overflow-x:auto;}
.katex{line-height:1.5;}
.katex-display{margin:0.35em 0;}
.tablewrap{margin:10px 0;overflow-x:auto;}
table{border-collapse:collapse;width:100%;}
th,td{border:1px solid rgba(128,128,128,0.45);padding:8px 10px;text-align:left;vertical-align:top;}
th{background:$codeHead;font-weight:600;}
tr:nth-child(even) td{background:rgba(0,0,0,0.03);}
</style>
</head><body id="c">$body</body>
<script>
function renderAll(){
  document.querySelectorAll('span.math').forEach(function(el){
    try{ katex.render(el.getAttribute('data-tex')||el.textContent, el, {throwOnError:false, output:'html'}); }
    catch(e){}
  });
  document.querySelectorAll('div.mathb').forEach(function(el){
    try{ katex.render(el.getAttribute('data-tex')||'', el, {displayMode:true, throwOnError:false, output:'html'}); }
    catch(e){ el.textContent = el.getAttribute('data-tex')||''; }
  });
  try{ if (window.hljs) hljs.highlightAll(); }catch(e){}
}
function copyCode(btn){
  var box = btn.closest('.codebox');
  var code = box ? box.querySelector('pre code') : null;
  copyText(code ? code.innerText : '', btn);
}
function toggleCode(btn){
  var box = btn.closest('.codebox');
  if (!box) return;
  if (box.classList.contains('collapsed')) {
    box.classList.remove('collapsed');
    btn.textContent = '折叠';
  } else {
    box.classList.add('collapsed');
    btn.textContent = '展开全部';
  }
  try{
    if (window.AndroidBridge && window.AndroidBridge.onHeightChanged) {
      setTimeout(function(){
        var h = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
        AndroidBridge.onHeightChanged(String(h));
      }, 50);
    }
  }catch(e){}
}
function copyMath(btn){
  var box = btn.closest('.mathbox');
  var el = box ? box.querySelector('.mathb') : null;
  copyText(el ? (el.getAttribute('data-tex')||el.textContent) : '', btn);
}
function copyText(text, btn){
  try{
    var ta = document.createElement('textarea');
    ta.value = text; ta.style.position='fixed'; ta.style.left='-9999px';
    document.body.appendChild(ta); ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    if (btn){ var old = btn.textContent; btn.textContent='已复制'; setTimeout(function(){ btn.textContent=old; }, 1200); }
  }catch(e){}
}
function openCodeFull(btn){
  var box = btn.closest('.codebox');
  var code = box ? box.querySelector('pre code') : null;
  var lang = btn.getAttribute('data-lang')||'';
  if (window.AndroidBridge && code) AndroidBridge.openCode(lang, code.textContent||'');
}
function openMathFull(btn){
  var box = btn.closest('.mathbox');
  var el = box ? box.querySelector('.mathb') : null;
  if (window.AndroidBridge && el) AndroidBridge.openMath(el.getAttribute('data-tex')||'');
}
</script></body></html>
""".trimIndent()
}

// ---------- Code highlight ----------

@Composable
private fun CodeWeb(code: String, language: String, dark: Boolean, modifier: Modifier, zoomable: Boolean = true) {
    LoadableWebView(
        html = highlightHtml(code, language, dark),
        modifier = modifier,
        zoomable = zoomable,
        delayMountMs = if (zoomable) 0L else 200L,
        onReady = { /* highlightAll 在页面脚本内执行 */ }
    )
}

private fun highlightHtml(code: String, language: String, dark: Boolean): String {
    val cls = if (language.isNotBlank()) "language-${language.lowercase()}" else ""
    return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>${highlightCss(dark)}</style>
<script src="highlight.min.js"></script>
</head><body>
<pre><code class="$cls">${escapeHtml(code)}</code></pre>
<script>hljs.highlightAll();</script>
</body></html>
""".trimIndent()
}

private fun highlightCss(dark: Boolean): String {
    val codeColor = if (dark) "#EDE6D8" else "#2F2B25"
    val comment = "#8A8172"
    val keyword = if (dark) "#7FA08C" else "#5F7D6B"
    val string = if (dark) "#7FA07F" else "#6B8F6E"
    val number = if (dark) "#C4A76A" else "#B8924A"
    val title = if (dark) "#7A9AAB" else "#6A8899"
    val attr = if (dark) "#C47A6B" else "#B36B5D"
    val builtin = if (dark) "#8FB09B" else "#5A7A68"
    return """
html,body{margin:0;padding:14px;background:transparent;}
pre{margin:0;}
code{font-family:ui-monospace,Consolas,monospace;font-size:13px;line-height:1.55;color:$codeColor;}
.hljs-comment,.hljs-quote,.hljs-meta,.hljs-doctag{color:$comment;font-style:italic;}
.hljs-keyword,.hljs-selector-tag,.hljs-type,.hljs-tag,.hljs-name{color:$keyword;font-weight:bold;}
.hljs-string,.hljs-regexp,.hljs-addition{color:$string;}
.hljs-number,.hljs-literal{color:$number;}
.hljs-title,.hljs-title.function_,.hljs-section{color:$title;}
.hljs-attr,.hljs-attribute,.hljs-variable,.hljs-template-variable,.hljs-deletion{color:$attr;}
.hljs-built_in,.hljs-symbol,.hljs-bullet{color:$builtin;}
""".trimIndent()
}

private fun escapeHtml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun langToExt(lang: String): String = when (lang.trim().lowercase()) {
    "kotlin", "kt" -> "kt"
    "java" -> "java"
    "python", "py" -> "py"
    "javascript", "js" -> "js"
    "typescript", "ts" -> "ts"
    "html" -> "html"
    "css" -> "css"
    "scss" -> "scss"
    "json" -> "json"
    "xml" -> "xml"
    "yaml", "yml" -> "yml"
    "markdown", "md" -> "md"
    "sql" -> "sql"
    "c" -> "c"
    "cpp", "c++", "cxx" -> "cpp"
    "go", "golang" -> "go"
    "rust", "rs" -> "rust"
    "shell", "sh", "bash" -> "sh"
    "swift" -> "swift"
    "php" -> "php"
    "ruby", "rb" -> "rb"
    "tex", "latex" -> "tex"
    else -> "txt"
}

// ---------- HTML templates ----------

private val MERMAID_HTML = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html,body{margin:0;padding:12px;background:transparent;overflow:auto;}
#c{display:flex;justify-content:center;}
#c svg{max-width:100%;height:auto;}
</style>
<script src="mermaid.min.js"></script>
</head><body><div id="c"></div>
<script>
function renderMermaid(code, theme){
  mermaid.initialize({startOnLoad:false, theme: theme, securityLevel:'loose', fontFamily:'sans-serif'});
  var el = document.getElementById('c');
  el.innerHTML = '';
  mermaid.render('mmd', code).then(function(res){ el.innerHTML = res.svg; })
  .catch(function(err){ el.innerHTML = '<pre style="color:#dc2626">' + err.message + '</pre>'; });
}
</script></body></html>
""".trimIndent()

private fun KATEX_HTML(dark: Boolean): String {
    val color = if (dark) "#EDE6D8" else "#2F2B25"
    return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="katex.min.css">
<script src="katex.min.js"></script>
<style>
html,body{margin:0;padding:10px 8px;background:transparent;color:$color;overflow:auto;}
#c{display:block;padding:2px 4px;min-height:24px;line-height:1.45;}
.katex{line-height:1.45;}
.katex-display{margin:0.35em 0;padding:0;}
.katex-display>.katex{line-height:1.5;}
</style>
</head><body><div id="c"></div>
<script>
function renderKatex(latex, displayMode){
  try {
    var el = document.getElementById('c');
    el.innerHTML = '';
    katex.render(latex, el, {displayMode: displayMode, throwOnError: false, output:'html'});
  } catch(e) {
    document.getElementById('c').innerHTML = '<pre>' + e.message + '</pre>';
  }
}
function measureKatex(){
  var el = document.getElementById('c');
  if (!el) return 0;
  return Math.max(el.scrollHeight, el.offsetHeight) + 20;
}
</script></body></html>
""".trimIndent()
}
