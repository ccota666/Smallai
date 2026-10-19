package com.mimochat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mimochat.app.data.AppSettings
import com.mimochat.app.ui.screenqa.ScreenQABridge
import com.mimochat.app.ui.chat.ChatScreen
import com.mimochat.app.ui.conversations.ConversationListScreen
import com.mimochat.app.ui.settings.SettingsScreen
import com.mimochat.app.ui.theme.MiMoChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra("screen_qa_path")?.let {
            ScreenQABridge.pendingImagePath = it
        }
        enableEdgeToEdge()
        setContent {
            val app = application as MiMoApplication
            val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())
            MiMoChatTheme(settings) {
                val nav = rememberNavController()
                // 记住最近打开的对话：从列表返回时回到这里
                var lastChatId by remember { mutableLongStateOf(-1L) }

                NavHost(navController = nav, startDestination = "boot") {
                    // 启动即进「新对话」，不先看列表
                    composable("boot") {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            val id = app.chatRepository.createConversation(settings.model)
                            lastChatId = id
                            nav.navigate("chat/$id") {
                                popUpTo("boot") { inclusive = true }
                            }
                        }
                    }
                    composable("conversations") {
                        ConversationListScreen(
                            onOpenChat = { id ->
                                lastChatId = id
                                nav.navigate("chat/$id")
                            },
                            onOpenChatMessage = { convId, msgId ->
                                lastChatId = convId
                                nav.navigate("chat/$convId?msg=$msgId")
                            },
                            onOpenSettings = { nav.navigate("settings") },
                            onBackToChat = {
                                // 返回最近打开的一次对话；若没有则新建
                                val id = lastChatId
                                if (id > 0) {
                                    nav.navigate("chat/$id") {
                                        popUpTo("conversations") { inclusive = true }
                                    }
                                } else {
                                    nav.navigate("boot") {
                                        popUpTo("conversations") { inclusive = true }
                                    }
                                }
                            }
                        )
                    }
                    composable(
                        "chat/{conversationId}?msg={messageId}",
                        arguments = listOf(
                            navArgument("conversationId") { type = NavType.LongType },
                            navArgument("messageId") { type = NavType.LongType; defaultValue = -1L }
                        )
                    ) { entry ->
                        val convId = entry.arguments?.getLong("conversationId") ?: -1L
                        val msgId = entry.arguments?.getLong("messageId") ?: -1L
                        lastChatId = convId
                        // 系统返回 = 退出 App；左上角菜单 = 进对话列表
                        ChatScreen(
                            conversationId = convId,
                            targetMessageId = msgId,
                            onBack = { nav.popBackStack() },
                            onOpenList = {
                                nav.navigate("conversations") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
