package com.mimochat.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.initializer
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mimochat.app.MiMoApplication
import com.mimochat.app.data.AppSettings
import com.mimochat.app.ui.AppIconButton
import com.mimochat.app.ui.AppSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var settingsPage by remember { mutableStateOf(-1) }
    var showScreenQA by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = settingsPage != -1 || showScreenQA) {
        if (showScreenQA) showScreenQA = false
        else settingsPage = -1
    }
    if (showScreenQA) {
        com.mimochat.app.ui.screenqa.ScreenQASettingsScreen(onBack = { showScreenQA = false })
        return
    }
        val app = LocalContext.current.applicationContext as MiMoApplication
    val scope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app.settingsRepository) }
    })
    val settings by vm.settings.collectAsState(initial = AppSettings())

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var models by remember { mutableStateOf(settings.models) }
    var systemPrompt by remember { mutableStateOf(settings.systemPrompt) }
    var compressPrompt by remember(settings.compressPrompt) { mutableStateOf(settings.compressPrompt) }

    LaunchedEffect(settings.apiKey) { apiKey = settings.apiKey }
    LaunchedEffect(settings.baseUrl) { baseUrl = settings.baseUrl }
    LaunchedEffect(settings.models) { models = settings.models }
    LaunchedEffect(settings.systemPrompt) { systemPrompt = settings.systemPrompt }
    LaunchedEffect(settings.compressPrompt) { compressPrompt = settings.compressPrompt }

    val modelList = models.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (settingsPage) {
                        0 -> "API 连接与模型"
                        2 -> "外观"
                        3 -> "高级"
                        4 -> "使用教程"
                        else -> "设置"
                    })
                },
                navigationIcon = {
                    AppIconButton(
                        onClick = { if (settingsPage == -1) onBack() else settingsPage = -1 },
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (settingsPage == -1) {
                SettingsNavCard("API 连接与模型", "接口、Key、模型") { settingsPage = 0 }
                SettingsNavCard("屏幕问答", "悬浮球截屏") { showScreenQA = true }
                SettingsNavCard("外观", "主题、字体、加粗") { settingsPage = 2 }
                SettingsNavCard("高级", "上下文、压缩、提示词") { settingsPage = 3 }
                SettingsNavCard("使用教程", "API 获取、部署与智能体") { settingsPage = 4 }
            } else {


            if (settingsPage == 0) {
                SettingsSectionCard {
                    Text("Base URL", style = MaterialTheme.typography.labelLarge)
                    val urlPresets = listOf(
                        "https://api.xiaomimimo.com/v1" to "按量付费 API",
                        "https://token-plan-cn.xiaomimimo.com/v1" to "Token Plan（cn）"
                    )
                    urlPresets.forEach { (u, name) ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = baseUrl.trimEnd('/') == u,
                                    onClick = { baseUrl = u; vm.setBaseUrl(u) }
                                )
                                Text("$name  ·  $u", style = MaterialTheme.typography.bodySmall,
                                    color = if (name.contains("Token Plan")) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface)
                            }
                            if (name.contains("Token Plan")) {
                                Text("最好不要使用，详情见官网使用条例",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE74C3C),
                                    modifier = Modifier.padding(start = 32.dp))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; vm.setApiKey(it) },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; vm.setBaseUrl(it) },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { settingsPage = 4 }, modifier = Modifier.fillMaxWidth()) {
                        Text("获取 API Key 教程")
                    }
                    Text(
                        "此处设置仅对聊天模式生效，智能体模式请参考",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { settingsPage = 4 }, modifier = Modifier.fillMaxWidth()) {
                        Text("MiMoCode 部署教程")
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingsSectionCard {
                    OutlinedTextField(
                        value = models,
                        onValueChange = { models = it; vm.setModels(it) },
                        label = { Text("模型列表（逗号分隔）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ModelDropdown(
                        models = modelList,
                        current = settings.model,
                        onSelect = { vm.setModel(it) }
                    )
                }
            }

            if (settingsPage == 2) {
                SettingsSectionCard {
                    ThemeSelector(settings.themeMode) { vm.setThemeMode(it) }
                    FontSizeSlider(settings.fontSize) { vm.setFontSize(it) }
                    BoldColorPicker(settings.boldColor) { vm.setBoldColor(it) }
                }
            }

            if (settingsPage == 3) {
                SettingsSectionCard {
                    ContextLimitSlider(settings.contextLimit) { vm.setContextLimit(it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("超出历史条数时压缩上文", modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.compressOverflow,
                            onCheckedChange = { scope.launch { vm.setCompressOverflow(it) } }
                        )
                    }
                    OutlinedTextField(
                        value = compressPrompt,
                        onValueChange = {
                            compressPrompt = it
                            vm.setCompressPrompt(it)
                        },
                        label = { Text("压缩提示词") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
if (settingsPage == 3) {
                SettingsSectionCard {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it; vm.setSystemPrompt(it) },
                        label = { Text("系统提示词（可选）") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingsSectionCard {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it; vm.setSystemPrompt(it) },
                        label = { Text("系统提示词（可选）") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (settingsPage == 4) {
                SettingsSectionCard {
                    Text("教程列表", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth()) {
                        Text("API 获取")
                    }
                    OutlinedButton(onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth()) {
                        Text("MiMoCode 部署与智能体模式使用")
                    }
                    OutlinedButton(onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth()) {
                        Text("屏幕问答使用")
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
private fun SettingsNavCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleMedium)
        }
    }
}
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ModelDropdown(models: List<String>, current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val cleanCurrent = current.removePrefix("**").removeSuffix("**").trim()
    val isExpensive = com.mimochat.app.data.ModelsParser.isExpensive(models.joinToString(","), cleanCurrent)
    Box {
        OutlinedTextField(
            value = cleanCurrent,
            onValueChange = {},
            readOnly = true,
            label = { Text("默认模型") },
            trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (isExpensive) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = if (isExpensive) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = if (isExpensive) Color(0xFFE74C3C) else MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = if (isExpensive) Color(0xFFE74C3C) else MaterialTheme.colorScheme.outline
            )
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { m ->
                val exp = com.mimochat.app.data.ModelsParser.isExpensive(models.joinToString(","), m)
                val displayName = if (exp) m.removePrefix("**").removeSuffix("**") else m
                DropdownMenuItem(
                    text = {
                        Text(
                            displayName + if (exp) " ¥" else "",
                            color = if (exp) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onSelect(m.removePrefix("**").removeSuffix("**")); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector(current: Int, onSelect: (Int) -> Unit) {
    val options = listOf(0 to "浅色", 1 to "深色", 2 to "跟随系统")
    Column {
        options.forEach { (v, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = current == v, onClick = { onSelect(v) })
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 加粗/关键词颜色：预设色板 + 跟随主题 */
@Composable
private fun BoldColorPicker(current: Int, onChange: (Int) -> Unit) {
    // ARGB 高位 0x7F7F7F 作为彩虹标记
    val RAINBOW = (0x7F7F7F)   // 彩虹：单字渐变
    val COLORFUL = (0x5F5F5F)  // 五颜六色：按分段换色
    val presets = listOf(
        0 to "主题色",
        COLORFUL to "五彩",
        android.graphics.Color.parseColor("#E67E22") to "橙",
        android.graphics.Color.parseColor("#C0392B") to "红",
        android.graphics.Color.parseColor("#2980B9") to "蓝",
        android.graphics.Color.parseColor("#27AE60") to "绿",
        android.graphics.Color.parseColor("#8E44AD") to "紫",
        android.graphics.Color.parseColor("#D35400") to "赤",
        android.graphics.Color.parseColor("#16A085") to "青"
    )
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("加粗文字颜色", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            for ((color, label) in presets) {
                val selected = current == color
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .let { mod ->
                                if (color == RAINBOW) {
                                    mod.background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFF6B6B), Color(0xFFFEC89A),
                                                Color(0xFF95D5B2), Color(0xFF4D96FF), Color(0xFFB197FC)
                                            )
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                } else if (color == COLORFUL) {
                                    mod.background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF2F6FED), Color(0xFFC678DD), Color(0xFFE67E22),
                                                Color(0xFF27AE60), Color(0xFFE74C3C), Color(0xFF16A085)
                                            )
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                } else {
                                    mod.background(
                                        if (color == 0) MaterialTheme.colorScheme.surfaceVariant
                                        else Color(color),
                                        RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                            .then(
                                if (selected) Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            )
                            .clickable { onChange(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (color == 0) {
                            Text("Aa", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FontSizeSlider(current: Float, onChange: (Float) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current) }
    Column {
        Text("字体大小：${value.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
        AppSlider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value) },
            valueRange = 12f..26f,
            steps = 13
        )
    }
}

@Composable
private fun ContextLimitSlider(current: Int, onChange: (Int) -> Unit) {
    var value by remember(current) { mutableIntStateOf(current) }
    Column {
        Text("携带历史消息条数：$value", style = MaterialTheme.typography.bodyMedium)
        AppSlider(
            value = value.toFloat(),
            onValueChange = { value = it.toInt() },
            onValueChangeFinished = { onChange(value) },
            valueRange = 4f..200f,
            steps = 195
        )
        Text(
            "越大越能记住上下文，但消耗更多 token。MiMo 支持 1M 上下文窗口。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(onClick = onBack, icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back")
        Text(title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp))
    }
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
