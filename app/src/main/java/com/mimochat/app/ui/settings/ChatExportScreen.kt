package com.mimochat.app.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mimochat.app.MiMoApplication
import com.mimochat.app.ui.AppIconButton
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatExportScreen(onBack: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as MiMoApplication
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天记录") },
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
            SettingsSectionCard {
                Text("导出聊天记录", style = MaterialTheme.typography.titleMedium)
                Text(
                    "导出为 JSON 格式，兼容 ChatGPT 等平台导入。包含所有对话的标题、消息内容和时间戳。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            status = "正在导出…"
                            try {
                                val json = exportChatHistory(app)
                                val dir = File(context.getExternalFilesDir(null), "exports")
                                dir.mkdirs()
                                val file = File(dir, "chat_export_${System.currentTimeMillis()}.json")
                                file.writeText(json.toString(2))
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "导出聊天记录"))
                                status = "导出成功！"
                            } catch (e: Exception) {
                                status = "导出失败：${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导出为 JSON")
                }
            }

            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = if (status.contains("失败")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private suspend fun exportChatHistory(app: MiMoApplication): JSONObject {
    val repo = app.chatRepository
    val convs = repo.getAllConversations()
    val arr = JSONArray()

    for (conv in convs) {
        val obj = JSONObject()
        obj.put("title", conv.title)
        obj.put("create_time", conv.createdAt / 1000.0)
        obj.put("update_time", conv.updatedAt / 1000.0)
        obj.put("model", conv.model)

        val msgs = repo.getMessages(conv.id)
        val msgArr = JSONArray()
        for (m in msgs) {
            if (m.content.startsWith("[TASK]")) continue
            val msg = JSONObject()
            msg.put("role", m.role)
            msg.put("content", m.content)
            msg.put("create_time", m.createdAt / 1000.0)
            msgArr.put(msg)
        }
        obj.put("messages", msgArr)
        arr.put(obj)
    }

    val root = JSONObject()
    root.put("format", "chatgpt-export-v1")
    root.put("app", "Small ai")
    root.put("export_time", System.currentTimeMillis() / 1000.0)
    root.put("conversations", arr)
    return root
}
