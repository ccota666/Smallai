package com.mimochat.app.ui.screenqa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mimochat.app.data.QAPlan
import com.mimochat.app.data.ScreenQARepository
import com.mimochat.app.ui.AppIconButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 屏幕问答设置：悬浮球开关、自动模式、方案增删改、图片压缩率。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenQASettingsScreen(onBack: () -> Unit, onShowTutorial: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ScreenQARepository(context) }
    val config by repo.config.collectAsState(
        initial = com.mimochat.app.data.ScreenQAConfig()
    )

    var editing by remember { mutableStateOf<QAPlan?>(null) }
    var deleting by remember { mutableStateOf<QAPlan?>(null) }
    var showNew by remember { mutableStateOf(false) }

    // 截屏权限
    val mpm = remember {
        context.getSystemService(MediaProjectionManager::class.java)
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限结果不阻塞，无权限时用 Toast 兜底 */ }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            FloatingQAService.projectionData = res.data
            FloatingQAService.start(context)
            scope.launch { repo.setEnabled(true) }
            Toast.makeText(context, "截屏服务已启动，点悬浮球即可提问", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "未授权截屏", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureOverlayThenProjection() {
        if (!Settings.canDrawOverlays(context)) {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
            context.startActivity(i)
            Toast.makeText(context, "请允许「显示在其他应用上层」后再次开启", Toast.LENGTH_LONG).show()
            return
        }
        // 通知权限（自动模式结果靠通知展示）
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕问答") },
                navigationIcon = {
                    AppIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 总开关
            Card(modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("悬浮球", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (config.enabled) "运行中 · 点球提问，长按球退出"
                            else "未启动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (config.enabled) {
                            Button(onClick = {
                                FloatingQAService.stop(context)
                                scope.launch { repo.setEnabled(false) }
                            }) { Text("停止") }
                        } else {
                            Button(onClick = { ensureOverlayThenProjection() }) { Text("启动悬浮球") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onShowTutorial() }, modifier = Modifier.fillMaxWidth()) {
                        Text("屏幕问答使用教程")
                    }
                }
            }

            if (config.autoMode && config.plans.isNotEmpty()) {
                Text("当前方案", style = MaterialTheme.typography.labelMedium)
                config.plans.forEach { plan ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = config.activePlanId == plan.id,
                            onClick = { scope.launch { repo.setActivePlan(plan.id) } }
                        )
                        Text(plan.name, modifier = Modifier.weight(1f))
                        Text("压缩 ${plan.quality}%")
                    }
                }
            }

            // 方案列表
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("提问方案", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { showNew = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }

            if (config.plans.isEmpty()) {
                
            }

            config.plans.forEach { plan ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(plan.name, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { editing = plan }) {
                                Icon(Icons.Rounded.Edit, "编辑")
                            }
                            IconButton(onClick = { deleting = plan }) {
                                Icon(Icons.Rounded.Delete, "删除",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            plan.prompt.take(80) + if (plan.prompt.length > 80) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "图片压缩率 ${plan.quality}%（越高越清晰、文件越大，同图片编辑里的压缩效果）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // 新建/编辑方案
    if (showNew || editing != null) {
        PlanEditDialog(
            initial = editing,
            onDismiss = { showNew = false; editing = null },
            onSave = { plan ->
                scope.launch {
                    repo.upsertPlan(plan)
                    if (repo.config.first().activePlanId == -1L) repo.setActivePlan(plan.id)
                }
                showNew = false
                editing = null
            }
        )
    }

    deleting?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除方案？") },
            text = { Text("「${plan.name}」删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deletePlan(plan.id) }
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PlanEditDialog(
    initial: QAPlan?,
    onDismiss: () -> Unit,
    onSave: (QAPlan) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var prompt by remember { mutableStateOf(initial?.prompt ?: "") }
    var quality by remember { mutableFloatStateOf((initial?.quality ?: 85).toFloat()) }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    val modelOptions = listOf(
        "" to "默认（跟随 App 设置）",
        "mimo-v2.6-flash" to "mimo-v2.6-flash（快）",
        "mimo-v2.6-pro" to "mimo-v2.6-pro（准）",
        "mimo-v2.6-pro-ultraspeed" to "mimo-v2.6-pro-ultraspeed（极速）"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建方案" else "编辑方案") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("方案名，如「转账核对」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("提示词（自动模式发给模型）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("使用模型", style = MaterialTheme.typography.labelMedium)
                modelOptions.forEach { (id, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = model == id,
                            onClick = { model = id }
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("图片压缩率 ${quality.roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 10f..100f
                )
                Text(
                    "越低文件越小、发送越快，但文字可能糊。100% 最清晰。与图片编辑里的「压缩率」是同一效果。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && prompt.isNotBlank(),
                onClick = {
                    onSave(
                        QAPlan(
                            id = initial?.id ?: System.currentTimeMillis(),
                            name = name.trim(),
                            prompt = prompt.trim(),
                            quality = quality.roundToInt().coerceIn(10, 100),
                            model = model
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
