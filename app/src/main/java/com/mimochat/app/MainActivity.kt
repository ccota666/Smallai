package com.mimochat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        intent?.getStringExtra("screen_qa_path")?.let {
            ScreenQABridge.pendingImagePath = it
        }
        setContent {
            val app = application as MiMoApplication
            val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())
            MiMoChatTheme(settings) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "conversations") {
                    composable("conversations") {
                        ConversationListScreen(
                            onOpenChat = { id -> nav.navigate("chat/$id") },
                            onOpenChatMessage = { convId, msgId -> nav.navigate("chat/$convId?msg=$msgId") },
                            onOpenSettings = { nav.navigate("settings") }
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
                        ChatScreen(conversationId = convId, targetMessageId = msgId, onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
