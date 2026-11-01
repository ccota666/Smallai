package com.mimochat.app.ui.chat

import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mimochat.app.MiMoApplication
import com.mimochat.app.data.Attachment
import com.mimochat.app.data.ChatMessage
import com.mimochat.app.data.ChatRepository
import com.mimochat.app.ui.AppDimens
import com.mimochat.app.ui.AppFilledIconButton
import com.mimochat.app.ui.AppIconButton
import com.mimochat.app.ui.AppPillBackground
import com.mimochat.app.ui.AppSlider
import com.mimochat.app.ui.AppStopIconButton
import com.mimochat.app.ui.ChatListWebView
import com.mimochat.app.ui.FilePicker
import com.mimochat.app.ui.ImageEditorDialog
import com.mimochat.app.ui.FullscreenDialog
import com.mimochat.app.ui.LocalChatListScrolling
import com.mimochat.app.ui.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 滚到「最后一行文字」而不是最后一条消息的开头：
 * 1) 先滚到末条 item 顶部；2) 再把该 item 内超出视口的剩余高度滚完。
 */
private suspend fun LazyListState.scrollChatToBottom(animate: Boolean = false) {
    if (layoutInfo.totalItemsCount == 0) return
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (animate) animateScrollToItem(lastIndex) else scrollToItem(lastIndex)
    // 多轮补滚，覆盖末条比视口高的情况（长回答/长代码）
    repeat(12) {
        if (!canScrollForward) return
        val info = layoutInfo
        val last = info.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
        val overshoot = last.offset + last.size - info.viewportEndOffset
        if (overshoot <= 0) {
            // 末条已见底，但前面还可能差 contentPadding
            if (!canScrollForward) return
            scrollBy(64f)
        } else {
            scrollBy(overshoot.toFloat() + 8f)
        }
    }
}

@Composable
fun ChatScreen(
    conversationId: Long,
    targetMessageId: Long = -1L,
    onBack: () -> Unit,
    onOpenList: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as MiMoApplication
    val vm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer { ChatViewModel(app.chatRepository, app.settingsRepository) }
    })
    LaunchedEffect(conversationId) { vm.init(conversationId) }

    val conversation by vm.conversation.collectAsState()
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val reasoning by vm.reasoning.collectAsState()
    val isThinking by vm.isThinking.collectAsState()
    val apiStatus by vm.apiStatus.collectAsState()
    val thinkingSeconds by vm.thinkingSeconds.collectAsState()
    val generating by vm.isGenerating.collectAsState()
    val settings by vm.settings.collectAsState()
    val error by vm.error.collectAsState()

    val dark = when (settings.themeMode) {
        0 -> false
        1 -> true
        else -> isSystemInDarkTheme()
    }

    var inputText by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    // 屏幕问答手动模式：悬浮球截的图自动挂到输入框
    LaunchedEffect(conversationId) {
        com.mimochat.app.ui.screenqa.ScreenQABridge.pendingImagePath?.let { path ->
            com.mimochat.app.ui.screenqa.ScreenQABridge.pendingImagePath = null
            val f = File(path)
            if (f.exists()) {
                attachments = attachments + Attachment(
                    type = "image", path = path, name = f.name
                )
            }
        }
    }
    var editingImagePath by remember { mutableStateOf<String?>(null) }

    editingImagePath?.let { path ->
        ImageEditorDialog(
            sourcePath = path,
            onDismiss = { editingImagePath = null },
            onSave = { newPath, _ ->
                val existed = attachments.any { it.path == path }
                attachments = if (existed) {
                    attachments.map {
                        if (it.path == path) it.copy(path = newPath, name = File(newPath).name)
                        else it
                    }
                } else {
                    // 拍照新图：加入附件待发
                    attachments + Attachment("image", path = newPath, name = File(newPath).name)
                }
                editingImagePath = null
            }
        )
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputAreaHeight by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val voiceRecorder = remember { com.mimochat.app.ui.VoiceRecorder(context) }
    var agentMode by remember { mutableStateOf(false) }

    val reqTermuxPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            agentMode = true
            Toast.makeText(context, "智能体模式已开启，发送即执行任务", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要 Termux 的 RUN_COMMAND 权限才能下发任务", Toast.LENGTH_LONG).show()
        }
    }
    var agentRunning by remember { mutableStateOf(false) }
    var agentTaskId by remember { mutableLongStateOf(0L) }
    var voiceRecording by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }
    val listState = rememberLazyListState()

    // 拍照 → 图片编辑 → 带图提问
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) pendingCameraPath?.let { editingImagePath = it }
        pendingCameraPath = null
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val mime = context.contentResolver.getType(uri) ?: ""
                if (mime.startsWith("audio/")) {
                    // 音频文件 → 识别成文字进输入框
                    Toast.makeText(context, "正在识别音频…", Toast.LENGTH_SHORT).show()
                    try {
                        val f = java.io.File(context.cacheDir, "asr_${System.currentTimeMillis()}.m4a")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { output -> input.copyTo(output) }
                        }
                        val txt = com.mimochat.app.data.MiMoAsr().transcribe(settings, f)
                        if (txt.isNotBlank()) inputText = (inputText + " " + txt).trim()
                        else Toast.makeText(context, "未识别到内容", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "识别失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    scope.launch {
                        // 智能体模式：任意文件类型都接受；普通模式：只接受图片/文本
                        val att = if (agentMode) {
                            FilePicker.toFileAttachment(context, uri)
                        } else {
                            FilePicker.toAttachment(context, uri)
                        }
                        if (att != null) attachments = attachments + att
                        else Toast.makeText(context, "暂不支持该文件类型", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 是否在底部：用 canScrollForward 更可靠
    val isAtBottom by remember {
        derivedStateOf { !listState.canScrollForward }
    }
    // 用户上滑后又有新内容 → 回底按钮闪烁提示
    var hasNewBelow by remember { mutableStateOf(false) }
    var lastStreamLen by remember { mutableStateOf(0) }
    // 跟随最新输出：点「回到底部」开启，用户手动上滑则暂停（由 WebView JS 状态驱动）
    var followBottom by remember { mutableStateOf(true) }
    var webViewAtBottom by remember { mutableStateOf(true) }
    var jumpMessageId by remember { mutableStateOf(0L) }
    var artifactNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var artifactRefreshTick by remember { mutableStateOf(0) }
    // 加载产物列表（智能体生成的文件，排除 uploads/ 和隐藏文件）
    // 打开列表菜单时刷新（消息一直在更新，messages.size 不变）
    LaunchedEffect(conversationId, artifactRefreshTick) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File("/storage/emulated/0/Download/MiMoChat", "conv_$conversationId")
            val names = dir.listFiles()?.filter {
                it.isFile && !it.name.startsWith(".") && !it.name.startsWith("mimo_") &&
                    it.name != "bridge_log.txt" && it.extension.lowercase() !in setOf("sh", "log")
            }?.map { it.name }?.sorted() ?: emptyList()
            artifactNames = names
        }
    }
    var chatFullscreen by remember { mutableStateOf(false) }
    // 全屏时按安卓返回：先退出全屏，而不是离开聊天
    androidx.activity.compose.BackHandler(enabled = chatFullscreen) {
        chatFullscreen = false
    }
    // 触发 WebView 滚到底（时间戳变化即滚动）
    var webViewScrollToBottom by remember { mutableStateOf(0L) }

    var pendingScrollTarget by remember { mutableStateOf(targetMessageId) }
    // jumpMessageId 保持直到下一次跳转，WebView update 里消费


    // 生成时只在「用户已贴底」才跟随；否则停在原地并提示
    LaunchedEffect(messages.size, streaming) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (streaming.length != lastStreamLen) {
            lastStreamLen = streaming.length
            if (!isAtBottom) hasNewBelow = true
        }
        if (followBottom || isAtBottom) {
            hasNewBelow = false
            listState.scrollChatToBottom()
        }
    }
    // 用户手动滚动离开底部 → 暂停跟随
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling && listState.canScrollForward) {
                    followBottom = false
                }
            }
    }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            hasNewBelow = false
            followBottom = true
        }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
          if (!chatFullscreen) {
            androidx.compose.foundation.layout.Box {
                // 渐变遮罩：顶部从主题色渐入，内容滚到栏下自然淡出
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                )
                            )
                        )
                        .statusBarsPadding()
                        .height(96.dp)
                )
            ChatTopBar(
                models = settings.models.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                currentModel = conversation?.model ?: settings.model,
                webSearch = settings.webSearch,
                deepThinking = settings.deepThinking,
                temperature = settings.temperature,
                questions = messages.filter { it.role == "user" && it.content.isNotBlank() },
                artifacts = artifactNames,
                onRefreshArtifacts = { artifactRefreshTick++ },
                onOpenArtifact = { name ->
                    scope.launch {
                        val dir = java.io.File("/storage/emulated/0/Download/MiMoChat", "conv_$conversationId")
                        val file = java.io.File(dir, name)
                        if (file.exists()) {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, context.packageName + ".fileprovider", file
                                )
                                val mime = context.contentResolver.getType(uri)
                                    ?: android.webkit.MimeTypeMap.getSingleton()
                                        .getMimeTypeFromExtension(file.extension.lowercase())
                                    ?: "*/*"
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "无法打开：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                agentMode = agentMode,
                onOpenList = onOpenList,
                onSelectModel = { vm.setModel(it) },
                onSetWebSearch = { vm.setWebSearch(it) },
                onSetDeepThinking = { vm.setDeepThinking(it) },
                onSetTemperature = { vm.setTemperature(it) },
                onJumpToQuestion = { msgId ->
                    hasNewBelow = false
                    jumpMessageId = msgId
                }
            )
            }
          }
        }
    ) { padding ->
        // WebView 全出血：顶到状态栏、底到导航栏；输入栏浮在上面
        Box(Modifier.fillMaxSize().imePadding()) {
            val listScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
            CompositionLocalProvider(LocalChatListScrolling provides listScrolling) {
                Box(Modifier.fillMaxSize()) {
                if (messages.isEmpty() && !generating) {
                    EmptyState(conversation?.model ?: settings.model, Modifier.fillMaxSize())
                } else {
                    val boldCol = if (settings.boldColor != 0)
                        androidx.compose.ui.graphics.Color(settings.boldColor)
                    else androidx.compose.ui.graphics.Color.Unspecified
                    val msgsJson = remember(messages, streaming, reasoning, generating) {
                        buildMessagesJson(messages, streaming, reasoning, generating)
                    }
                    val streamJson = remember(generating, streaming, reasoning, isThinking, thinkingSeconds) {
                        buildStreamingJson(generating, streaming, reasoning, isThinking, thinkingSeconds, apiStatus)
                    }
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val topBarH = with(density) { 52.dp.toPx() }
                    val inputH = with(density) { 76.dp.toPx() }
                    ChatListWebView(
                        messagesJson = msgsJson,
                        streamingJson = streamJson,
                        dark = dark,
                        boldColor = boldCol,
                        fontSizeSp = settings.fontSize,
                        topInsetPx = topBarH.toInt(),
                        bottomInsetPx = inputH.toInt(),
                        scrollTrigger = webViewScrollToBottom,
                        jumpMessageId = jumpMessageId,
                        fullscreen = chatFullscreen,
                        modifier = Modifier.fillMaxSize(),
                        onFullscreen = { chatFullscreen = it },
                        onScrollAtBottom = { atBottom ->
                            webViewAtBottom = atBottom
                            if (atBottom) {
                                hasNewBelow = false
                                followBottom = true
                            } else {
                                followBottom = false
                            }
                        },
                        onAction = { action, id ->
                            when (action) {
                                "copy" -> {
                                    val m = messages.firstOrNull { it.id == id }
                                    m?.let {
                                        clipboardManager.setText(AnnotatedString(it.content))
                                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "regen" -> vm.regenerate(id)
                                "delete" -> vm.deletePair(id)
                            }
                        }
                    )
                }

                // 回到底部按钮：浮在输入栏上方
                if (!webViewAtBottom && messages.isNotEmpty() && !chatFullscreen) {
                    JumpToBottomButton(
                        flash = hasNewBelow || generating,
                        onClick = {
                            hasNewBelow = false
                            followBottom = true
                            webViewScrollToBottom = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = inputAreaHeight.dp + 12.dp)
                    )
                }
                } // Box inner
            } // CompositionLocalProvider

            // 底部输入区：全屏时隐藏
            if (!chatFullscreen)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { inputAreaHeight = (it.height / density.density).toInt() }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                                )
                            )
                        )
                ) {
            InputBar(
                text = inputText,
                currentModel = conversation?.model ?: settings.model,
                onTextChange = { inputText = it },
                attachments = attachments,
                onRemoveAttachment = { attachments = attachments - it },
                onEditImage = { path -> editingImagePath = path },
                onPickFile = { fileLauncher.launch(arrayOf("*/*")) },
                voiceRecording = voiceRecording,
                agentMode = agentMode,
                agentRunning = agentRunning,
                onToggleAgent = {
                    if (!agentMode) {
                        val curModel = conversation?.model ?: settings.model
                        val isExpensive = com.mimochat.app.data.ModelsParser.isExpensive(
                            settings.models, curModel
                        )
                        if (isExpensive) {
                            Toast.makeText(context, "⭐ ${curModel} 为贵模型，不建议用于智能体任务，请先切换便宜模型", Toast.LENGTH_LONG).show()
                        } else if (!com.mimochat.app.ui.termux.TermuxBridge.isTermuxInstalled(context)) {
                            Toast.makeText(context, "未检测到 Termux，请先安装并在 Termux 设置里允许外部应用", Toast.LENGTH_LONG).show()
                        } else {
                        val perm = "com.termux.permission.RUN_COMMAND"
                        val granted = context.checkSelfPermission(perm) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            Toast.makeText(context, "需要授权 Termux 命令权限", Toast.LENGTH_SHORT).show()
                            reqTermuxPerm.launch(perm)
                        } else {
                            agentMode = true
                            Toast.makeText(context, "智能体模式已开启，发送即执行任务", Toast.LENGTH_SHORT).show()
                        }
                        }
                    } else {
                        agentMode = false
                    }
                },
                modelsCsv = settings.models,
                onVoiceInput = {
                    if (voiceRecording) {
                        // 停止录音并识别
                        val f = voiceRecorder.stop()
                        voiceRecording = false
                        if (f != null && f.length() > 0) {
                            Toast.makeText(context, "识别中…", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                try {
                                    val asr = com.mimochat.app.data.MiMoAsr()
                                    val txt = asr.transcribe(settings, f)
                                    if (txt.isNotBlank()) {
                                        inputText = (inputText + " " + txt).trim()
                                    } else {
                                        Toast.makeText(context, "没听清，请重试", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "识别失败：${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        // 开始录音
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                Toast.makeText(context, "请先允许录音权限", Toast.LENGTH_SHORT).show()
                                return@InputBar
                            }
                        }
                        try {
                            voiceRecorder.start()
                            voiceRecording = true
                            Toast.makeText(context, "正在录音，再点一次结束", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法录音：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onTakePhoto = {
                    try {
                        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
                        val f = java.io.File(dir, "cap_${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", f
                        )
                        pendingCameraPath = f.absolutePath
                        cameraLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开相机：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onSend = {
                    val taskText = inputText.trim()
                    if (agentMode && taskText.isNotEmpty()) {
                        val atts = attachments
                        inputText = ""
                        attachments = emptyList()
                        agentRunning = true
                        val taskId = System.currentTimeMillis()
                        agentTaskId = taskId
                        val conv = vm.conversationId
                        scope.launch {
                            // 附件放 uploads/ 子目录（与智能体产出区分），同名覆盖
                            val workDir = java.io.File("/storage/emulated/0/Download/MiMoChat", "conv_$conv")
                            val uploadDir = java.io.File(workDir, "uploads")
                            uploadDir.mkdirs()
                            val fileNames = atts.mapNotNull { att ->
                                att.path?.let { p ->
                                    val src = java.io.File(p)
                                    if (src.exists()) {
                                        val dst = java.io.File(uploadDir, src.name)
                                        runCatching { src.copyTo(dst, overwrite = true) }
                                        src.name
                                    } else null
                                }
                            }
                            // 用户提问入库，显示气泡
                            vm.insertLocalMessage(vm.conversationId, "user", taskText)
                            val aiId = vm.insertLocalTask("⏳ 任务已下发，正在执行…")
                            val curModel = conversation?.model ?: settings.model
                            com.mimochat.app.ui.termux.AgentTaskManager.start(
                                context, conv, taskId, taskText, curModel, fileNames
                            ) { text, attJson ->
                                vm.updateLocalTask(aiId, text, attJson)
                            }
                            // 等任务结束（切页面也不影响，Manager 是全局的）
                            com.mimochat.app.ui.termux.AgentTaskManager.states.collect { map ->
                                val st = map[taskId]
                                if (st != null && !st.running) {
                                    agentRunning = false
                                }
                            }
                        }
                    } else {
                    vm.send(inputText, attachments)
                    inputText = ""
                    attachments = emptyList()
                    followBottom = true
                    webViewScrollToBottom = System.currentTimeMillis()
                    }
                },
                generating = generating,
                onStop = {
                    if (agentRunning) {
                        com.mimochat.app.ui.termux.MimoRunner.cancel(context, agentTaskId)
                        agentRunning = false
                    }
                    vm.stop()
                }
            )
                } // Column
            } // overlay Box
        } // root Box
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    models: List<String>,
    currentModel: String,
    webSearch: Boolean,
    deepThinking: Boolean,
    temperature: Float,
    questions: List<ChatMessage>,
    artifacts: List<String> = emptyList(),
    onOpenArtifact: (String) -> Unit = {},
    onRefreshArtifacts: () -> Unit = {},
    agentMode: Boolean = false,
    onOpenList: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSetWebSearch: (Boolean) -> Unit,
    onSetDeepThinking: (Boolean) -> Unit,
    onSetTemperature: (Float) -> Unit,
    onJumpToQuestion: (Long) -> Unit
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    var settingsMenuOpen by remember { mutableStateOf(false) }
    var questionsMenuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE) }
    var showAgentHint by remember { mutableStateOf(prefs.getBoolean("show_agent_hint", true)) }
    TopAppBar(
        title = {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        navigationIcon = {
            // 三横线 = 进入对话/功能列表
            AppIconButton(onClick = onOpenList, icon = Icons.Rounded.Menu, contentDescription = "对话列表")
        },
        actions = {
            // 模型选择：胶囊 chip
            Box {
                AppPillBackground {
                    TextButton(
                        onClick = { modelMenuOpen = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp, vertical = 0.dp
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppDimens.shapePill))
                            .height(32.dp)
                    ) {
                        val modelExpensive = com.mimochat.app.data.ModelsParser.isExpensive(
                            models.joinToString(","), currentModel
                        )
                        Text(
                            currentModel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (modelExpensive) Color(0xFFE74C3C)
                                    else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                    val items = com.mimochat.app.data.ModelsParser.parse(models.joinToString(","))
                    items.forEach { item ->
                        val id = item.id
                        val exp = item.expensive
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (id == currentModel) "● " else "   ") + id,
                                    color = if (exp) Color(0xFFE74C3C)
                                            else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (id == currentModel)
                                        androidx.compose.ui.text.font.FontWeight.Bold
                                    else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            onClick = { onSelectModel(id); modelMenuOpen = false }
                        )
                    }
                }
            }

            // 问题/产物双分页
            Box {
                AppIconButton(
                    onClick = { questionsMenuOpen = true; onRefreshArtifacts() },
                    icon = Icons.AutoMirrored.Rounded.ListAlt,
                    contentDescription = "问题与产物",
                    tint = if (questions.isNotEmpty() || artifacts.isNotEmpty())
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenu(
                    expanded = questionsMenuOpen,
                    onDismissRequest = { questionsMenuOpen = false },
                    modifier = Modifier.widthIn(min = 260.dp, max = 320.dp)
                ) {
                    var selectedTab by remember { mutableIntStateOf(0) }
                    // Tab 标签栏
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("问题 (${questions.size})", "产物 (${artifacts.size})").forEachIndexed { i, label ->
                            FilterChip(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    if (selectedTab == 0) {
                        // 问题列表
                        if (questions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无提问", style = MaterialTheme.typography.bodySmall) },
                                onClick = { questionsMenuOpen = false }
                            )
                        } else {
                            questions.forEachIndexed { index, q ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${index + 1}. ${q.content.trim().replace('\n', ' ')}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        questionsMenuOpen = false
                                        onJumpToQuestion(q.id)
                                    }
                                )
                            }
                        }
                    } else {
                        // 产物列表
                        if (artifacts.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无产物", style = MaterialTheme.typography.bodySmall) },
                                onClick = { questionsMenuOpen = false }
                            )
                        } else {
                            artifacts.forEach { name ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.InsertDriveFile, null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                name,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    onClick = {
                                        questionsMenuOpen = false
                                        onOpenArtifact(name)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 设置按钮：智能体模式下显示蓝点+文字
            Box {
                AppIconButton(
                    onClick = { settingsMenuOpen = true },
                    icon = Icons.Rounded.Settings,
                    contentDescription = "聊天设置"
                )
            }
            DropdownMenu(expanded = settingsMenuOpen, onDismissRequest = { settingsMenuOpen = false }) {
                Column(Modifier.widthIn(min = 260.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (showAgentHint) {
                        Text(
                            "⚠️ 此处设置对智能体模式不生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        TextButton(
                            onClick = { showAgentHint = false
                            prefs.edit().putBoolean("show_agent_hint", false).apply() },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text("不再显示",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("联网搜索", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = webSearch, onCheckedChange = onSetWebSearch)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("深度思考", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = deepThinking, onCheckedChange = onSetDeepThinking)
                    }
                    HorizontalDivider()
                    var temp by remember(temperature) { mutableFloatStateOf(temperature) }
                    Text(
                        if (deepThinking) "温度（深度思考中固定 1.0）"
                        else "温度 ${"%.2f".format(temp)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    AppSlider(
                        value = temp,
                        onValueChange = { temp = it },
                        onValueChangeFinished = { if (!deepThinking) onSetTemperature(temp) },
                        valueRange = 0f..1.5f,
                        enabled = !deepThinking
                    )
                    // 最大输出固定 131072（官方默认上限），不再提供设置
                }
            }
        }
    )
}

@Composable
private fun JumpToBottomButton(
    flash: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 生成中有新内容时主色脉冲闪烁
    val transition = rememberInfiniteTransition(label = "jump")
    val flashAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash"
    )

    val container = if (flash) Color(0xFF1A1A1A) else Color.White
    val content = if (flash) Color.White else Color(0xFF333333)
    val alpha = if (flash) flashAlpha else 1f

    Surface(
        shape = RoundedCornerShape(AppDimens.shapePill),
        color = container.copy(alpha = alpha * (if (flash) 1f else 0.92f)),
        contentColor = content,
        shadowElevation = 6.dp,
        modifier = modifier.size(44.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "回到底部",
                tint = content,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(model: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "我们来创造什么 › _ ~ ;",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

    }
}

@Composable
private fun MessageItem(
    msg: ChatMessage,
    displayContent: String,
    boldColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    displayReasoning: String,
    dark: Boolean,
    isStreaming: Boolean,
    isThinking: Boolean,
    liveThinkingSeconds: Float,
    onRegenerate: () -> Unit,
    onDeletePair: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (msg.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = colors.primaryContainer,
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    val atts = ChatRepository.parseAttachments(msg.attachmentsJson)
                    if (atts.isNotEmpty()) AttachmentPreview(atts)
                    if (msg.content.isNotBlank()) {
                        SelectionContainer {
                            Text(msg.content, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            // 思考时长：生成中用实时值，结束后用入库值；只在 ReasoningBlock 一处展示
            val thinkSec = if (isStreaming) liveThinkingSeconds else msg.thinkingMs / 1000f
            val showThinking = isThinking || displayReasoning.isNotBlank() ||
                (!isStreaming && thinkSec > 0f && displayReasoning.isBlank())

            if (showThinking) {
                ReasoningBlock(
                    reasoning = displayReasoning,
                    live = isThinking,
                    seconds = thinkSec
                )
            }

            if (displayContent.isNotBlank()) {
                MarkdownText(displayContent, dark, live = isStreaming, boldColor = boldColor)
                val citations = ChatRepository.parseCitations(msg.citationsJson)
                if (citations.isNotEmpty() && !isStreaming) {
                    CitationsBlock(citations)
                }
            }

            if (!msg.pending && !isStreaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconButton(
                        onClick = { clipboard.setText(AnnotatedString(displayContent)) },
                        icon = Icons.Rounded.ContentCopy,
                        contentDescription = "复制"
                    )
                    AppIconButton(
                        onClick = onRegenerate,
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "重新生成"
                    )
                    AppIconButton(
                        onClick = { showDeleteConfirm = true },
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除此问答"
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除此问答？") },
            text = { Text("将删除这条 AI 回答及其对应的提问，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeletePair() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 思考过程折叠块 — 唯一的计时展示入口。
 * live=true 时标题为「思考中 · X.Xs」，结束后为「思考过程 · X.Xs」。
 */
@Composable
private fun ReasoningBlock(reasoning: String, live: Boolean, seconds: Float) {
    val colors = MaterialTheme.colorScheme
    // 思考中展开、结束后自动折叠（可点开回看）
    var expanded by remember { mutableStateOf(live) }
    LaunchedEffect(live) {
        if (!live) expanded = false
    }

    Surface(
        shape = RoundedCornerShape(AppDimens.shapeSm),
        color = colors.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                if (live && reasoning.isBlank()) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (live) "思考中" else "思考过程",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                if (seconds > 0f) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${"%.1f".format(seconds)}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            if (expanded) {
                HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                Text(
                    reasoning.ifBlank { if (live) "正在思考…" else "（无思考内容）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** 联网搜索引用来源列表 */
@Composable
private fun CitationsBlock(citations: List<com.mimochat.app.data.Citation>) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            "参考来源",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        citations.forEachIndexed { i, c ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        "${i + 1}. ${c.title.ifBlank { c.url }}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.primary
                    )
                    if (c.siteName.isNotBlank() || c.publishTime.isNotBlank()) {
                        Text(
                            listOf(c.siteName, c.publishTime).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Text(
                        c.url,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(atts: List<Attachment>, onEditImage: ((String) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (atts.size > 1) {
            Text(
                "共 ${atts.size} 个附件",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            for (a in atts) {
                AttachmentCard(a, onEditImage)
            }
        }
    }
}

/** 附件卡片：类型图标/缩略图 + 类型名，下方文件名截断显示 */
@Composable
private fun AttachmentCard(a: Attachment, onEditImage: ((String) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val isImage = a.type == "image"
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .width(96.dp)
            .then(
                if (isImage && a.path != null && onEditImage != null) {
                    Modifier.clickable { onEditImage(a.path) }
                } else Modifier
            )
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isImage && a.path != null) {
                var bmp by remember(a.path) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(a.path) {
                    bmp = withContext(Dispatchers.IO) { FilePicker.loadImage(a.path, maxDim = 256) }
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val b = bmp
                    if (b != null) {
                        Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Rounded.Image, null, tint = colors.onSurfaceVariant)
                    }
                }
                Text("图片", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            } else {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (a.type == "text") Icons.Rounded.Description else Icons.AutoMirrored.Rounded.InsertDriveFile,
                        null,
                        tint = colors.primary
                    )
                }
                Text(
                    if (a.type == "text") "文本" else "文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                a.name?.let { FilePicker.shortName(it) } ?: "附件",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ImageThumb(path: String) {
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        bmp = withContext(Dispatchers.IO) { FilePicker.loadImage(path) }
    }
    bmp?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(AppDimens.shapeMd))
                .clickable { full = true }
        )
        if (full) {
            FullscreenDialog(
                title = "图片",
                startLandscape = false,
                onDismiss = { full = false }
            ) {
                ZoomableImage(image, Modifier.fillMaxSize())
            }
        }
    }
}

/** 双指缩放图片，缩放比 1x–5x。 */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale <= 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    )
}

@Composable
private fun InputBar(
    text: String,
    currentModel: String,
    onTextChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    onEditImage: (String) -> Unit,
    onPickFile: () -> Unit,
    onTakePhoto: () -> Unit = {},
    onVoiceInput: () -> Unit = {},
    voiceRecording: Boolean = false,
    agentMode: Boolean = false,
    agentRunning: Boolean = false,
    onToggleAgent: () -> Unit = {},
    modelsCsv: String = "",
    onSend: () -> Unit,
    generating: Boolean,
    onStop: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier
        .navigationBarsPadding()
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
            if (attachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    for (a in attachments) {
                        AssistChip(
                            onClick = {
                                if (a.type == "image" && a.path != null) onEditImage(a.path)
                            },
                            label = {
                                Text(
                                    a.name?.let { FilePicker.shortName(it, 14) } ?: "附件",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 88.dp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (a.type) {
                                        "image" -> Icons.Rounded.Image
                                        "text" -> Icons.Rounded.Description
                                        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                                    },
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveAttachment(a) }
                                )
                            },
                            shape = RoundedCornerShape(AppDimens.shapeSm)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // 胶囊输入栏：聚焦时附件/拍照/语音/发送 降到下一行
            val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            var inputFocused by remember { mutableStateOf(false) }
            val focusModifier = Modifier.onFocusChanged { inputFocused = it.isFocused }
            Surface(
                shape = RoundedCornerShape(if (inputFocused) 18.dp else AppDimens.shapePill),
                color = colors.surface,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!inputFocused) {
                    AppIconButton(onClick = onPickFile, icon = Icons.Rounded.Add, contentDescription = "添加文件")
                    AppIconButton(onClick = onTakePhoto, icon = Icons.Rounded.CameraAlt, contentDescription = "拍照")
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        color = colors.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .then(focusModifier),
                    decorationBox = { innerTextField ->
                        Box(Modifier.padding(vertical = 8.dp)) {
                            if (text.isEmpty()) {
                                val exp = com.mimochat.app.data.ModelsParser.isExpensive(
                                    modelsCsv, currentModel
                                )
                                Text(
                                    currentModel,   // 不带省略号
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (exp) Color(0xFFE74C3C)
                                            else colors.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (!inputFocused) {
                    // 语音在发送按钮左边，紧挨着
                    AppIconButton(
                        onClick = onVoiceInput,
                        icon = if (voiceRecording) Icons.Rounded.Close else Icons.Rounded.Mic,
                        contentDescription = "语音输入",
                        tint = if (voiceRecording) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.primary
                    )
                    if (generating || agentRunning) {
                        AppStopIconButton(onClick = onStop, modifier = Modifier.padding(2.dp))
                    } else {
                        AppFilledIconButton(
                            onClick = onSend,
                            icon = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "发送",
                            enabled = text.isNotBlank() || attachments.isNotEmpty(),
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }
            // 第二行：聚焦时显示功能按钮
            if (inputFocused) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIconButton(onClick = onPickFile, icon = Icons.Rounded.Add, contentDescription = "添加文件")
                    AppIconButton(onClick = onTakePhoto, icon = Icons.Rounded.CameraAlt, contentDescription = "拍照")
                    // 胶囊按钮：智能体 / 退出
                    // 紧凑胶囊：与图标按钮同高；仅文字+描边变色
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                1.dp,
                                if (agentMode) Color(0xFF2F6FED)
                                else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(18.dp)
                            )
                            .then(
                                if (agentMode) Modifier.clickable { onToggleAgent() }
                                else Modifier.clickable { onToggleAgent() }
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            "智能体",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (agentMode) Color(0xFF2F6FED)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    AppIconButton(
                        onClick = onVoiceInput,
                        icon = if (voiceRecording) Icons.Rounded.Close else Icons.Rounded.Mic,
                        contentDescription = "语音输入",
                        tint = if (voiceRecording) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.primary
                    )
                    if (generating || agentRunning) {
                        AppStopIconButton(onClick = onStop, modifier = Modifier.padding(2.dp))
                    } else {
                        AppFilledIconButton(
                            onClick = onSend,
                            icon = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "发送",
                            enabled = text.isNotBlank() || attachments.isNotEmpty(),
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }
            } // Column
            } // Surface
        }
    }


/** 把消息列表序列化给 ChatListWebView */
private fun buildMessagesJson(
    messages: List<ChatMessage>,
    streaming: String,
    reasoning: String,
    generating: Boolean
): String {
    val arr = JSONArray()
    for ((idx, m) in messages.withIndex()) {
        val isLast = idx == messages.lastIndex
        val isStreamingMsg = isLast && generating && m.pending
        val rawContent = if (isStreamingMsg) streaming else m.content
        val isTask = rawContent.startsWith("[TASK]\n")
        val o = JSONObject()
            .put("id", m.id)
            .put("role", m.role)
            .put("task", isTask)
            .put("content", if (isTask) rawContent.removePrefix("[TASK]\n") else rawContent)
            .put("reasoning", if (isStreamingMsg) reasoning else m.reasoning)
            .put("thinkingMs", if (isStreamingMsg) 0 else m.thinkingMs)
            .put("pending", m.pending && isStreamingMsg)  // 非正在生成的一律不显示「正在思考」
            .put("failed", m.failed)
            .put("thinking", false)
        val atts = JSONArray()
        for (a in ChatRepository.parseAttachments(m.attachmentsJson)) {
            atts.put(
                JSONObject()
                    .put("type", a.type)
                    .put("path", a.path ?: "")
                    .put("name", a.name ?: "")
            )
        }
        o.put("attachments", atts)
        val cites = JSONArray()
        for (c in ChatRepository.parseCitations(m.citationsJson)) {
            cites.put(
                JSONObject()
                    .put("url", c.url)
                    .put("title", c.title)
                    .put("siteName", c.siteName)
                    .put("publishTime", c.publishTime)
            )
        }
        o.put("citations", cites)
        arr.put(o)
    }
    return arr.toString()
}

private fun buildStreamingJson(
    generating: Boolean,
    streaming: String,
    reasoning: String,
    isThinking: Boolean,
    seconds: Float,
    status: String = ""
): String {
    return JSONObject()
        .put("active", generating)
        .put("content", streaming)
        .put("reasoning", reasoning)
        .put("thinking", isThinking)
        .put("seconds", seconds.toDouble())
        .put("status", status)
        .toString()
}
