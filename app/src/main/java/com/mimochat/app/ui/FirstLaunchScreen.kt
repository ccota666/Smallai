package com.mimochat.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 首次启动权限说明页。
 * 用户点击「同意并继续」后触发权限申请。
 */
@Composable
fun FirstLaunchScreen(
    onAgree: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("欢迎使用小爱（Small ai）", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // 权限说明占位（后续替换为正式文字）
        PermissionItem("通知权限", "用于屏幕问答结果通知与任务完成提醒")
        PermissionItem("悬浮窗权限", "用于屏幕问答悬浮球显示")
        PermissionItem("截屏权限", "用于屏幕问答截取屏幕内容")
        PermissionItem("存储权限", "用于保存聊天附件与智能体产物")

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("同意并继续", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "点击同意即表示您了解并同意上述权限申请",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionItem(title: String, desc: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
