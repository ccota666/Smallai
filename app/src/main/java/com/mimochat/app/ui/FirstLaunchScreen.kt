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
        Spacer(Modifier.height(32.dp))
        Text("欢迎使用 Small ai", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))

        InfoSection(title = "隐私") {
            InfoBullet("本应用不收集、不上传你的任何个人信息，所有数据保存在本地。网络仅用于调用模型 API，不进行任何其他联网活动。")
            InfoBullet("未向模型添加任何隐藏提示词，所有提示词均可在 [设置-高级] 查看与修改。")
        }

        InfoSection(title = "免责声明") {
            InfoBullet("AI 生成内容可能有误，请注意核实与甄别，仅供参考。")
            InfoBullet("本应用为个人学习项目，开发过程使用了 AI 辅助，仅作为交流分享。作者水平有限，不保证无 bug、无功能异常，请自行妥善备份聊天记录等重要数据。")
            InfoBullet("请遵守大模型官网使用条例与文档，以及所调用第三方 API 服务的隐私政策。模型调用产生的费用，具体价格以各平台官网定价为准。")
            InfoBullet("请勿从其他途径下载应用。")
        }

        InfoSection(title = "所需权限（可按需开启）") {
            InfoBullet("**网络**：用于调用大模型 API")
            InfoBullet("**相机**：用于拍照提问")
            InfoBullet("**麦克风**：用于语音输入")
            InfoBullet("**存储**：用于保存聊天附件与智能体产物")
            InfoBullet("**录屏**：用于屏幕问答截屏")
            InfoBullet("**通知权限**：用于屏幕问答结果返回")
            InfoBullet("**悬浮窗权限**：用于屏幕问答悬浮球显示")
            InfoBullet("**RUN_COMMAND (Termux)**：用于智能体执行命令")
        }

        Spacer(Modifier.height(8.dp))
        Text("作者：ccota@bilibili", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("github.com/ccota666/Smallai", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("同意并继续", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun InfoBullet(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("• ", style = MaterialTheme.typography.bodySmall)
        // Simple bold parsing for **text**
        val parts = text.split("**")
        Text(
            buildString {
                for ((i, p) in parts.withIndex()) {
                    if (i % 2 == 1) append("")
                    append(p)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
