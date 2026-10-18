package com.mimochat.app.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mimochat.app.MiMoApplication
import com.mimochat.app.data.Conversation
import com.mimochat.app.ui.AppDimens
import com.mimochat.app.ui.AppIconButton
import com.mimochat.app.ui.AppPrimaryButton
import com.mimochat.app.ui.AppSecondaryButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onBackToChat: () -> Unit = {},
    onOpenChatMessage: (Long, Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val app = LocalContext.current.applicationContext as MiMoApplication
    val vm: ConversationListViewModel = viewModel(factory = viewModelFactory {
        initializer { ConversationListViewModel(app.chatRepository, app.settingsRepository) }
    })
    val conversations by vm.conversations.collectAsState(initial = emptyList())
    val firstUser by vm.firstUser.collectAsState(initial = emptyList())
    val firstAssistant by vm.firstAssistant.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    // 对话列表：系统返回 = 回到最近打开的对话，而不是退出 App
    BackHandler { onBackToChat() }

    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        searchResults = if (searchQuery.isNotBlank()) vm.search(searchQuery) else emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchMode) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            placeholder = {
                                Text("搜索所有消息…", style = MaterialTheme.typography.bodyMedium)
                            },
                            shape = RoundedCornerShape(AppDimens.shapePill),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )
                    } else {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val id = vm.newConversation()
                                    onOpenChat(id)
                                }
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp, vertical = 4.dp
                            )
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "新对话",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    if (searchMode) {
                        AppIconButton(
                            onClick = { searchMode = false; searchQuery = "" },
                            icon = Icons.Rounded.Close,
                            contentDescription = "关闭搜索"
                        )
                    } else {
                        AppIconButton(
                            onClick = { searchMode = true },
                            icon = Icons.Rounded.Search,
                            contentDescription = "搜索"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (selectionMode && !searchMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("已选 ${selected.size} 项", modifier = Modifier.weight(1f))
                        if (selected.size == 1) {
                            TextButton(onClick = { showRenameDialog = true }) { Text("重命名") }
                        }
                        TextButton(onClick = {
                            selectionMode = false
                            selected = emptySet()
                        }) { Text("取消") }
                        AppPrimaryButton(
                            onClick = { showDeleteConfirm = true },
                            text = "删除",
                            modifier = Modifier.width(88.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchMode) {
                if (searchResults.isEmpty()) {
                    Text(
                        "输入关键词搜索历史消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(searchResults, key = { it.messageId }) { hit ->
                            SearchResultItem(hit) {
                                onOpenChatMessage(hit.conversationId, hit.messageId)
                            }
                        }
                    }
                }
            } else {
                val firstUserMap = remember(firstUser) {
                    firstUser.associate { it.conversationId to it.content.trim() }
                }
                val firstAiMap = remember(firstAssistant) {
                    firstAssistant.associate { it.conversationId to it.content.trim() }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(conversations, key = { it.id }) { conv ->
                        ConversationItem(
                            conv = conv,
                            previewQuestion = { id -> firstUserMap[id] },
                            previewAnswer = { id -> firstAiMap[id] },
                            selected = conv.id in selected,
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (conv.id in selected) selected - conv.id else selected + conv.id
                                } else {
                                    onOpenChat(conv.id)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selected = setOf(conv.id)
                            }
                        )
                    }
                }

                if (!selectionMode) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        AppSecondaryButton(
                            onClick = onOpenSettings,
                            text = "设置",
                            icon = Icons.Rounded.Settings,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除对话？") },
            text = { Text("将删除选中的 ${selected.size} 个对话及其全部消息，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(selected.toList())
                    selectionMode = false
                    selected = emptySet()
                    showDeleteConfirm = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    placeholder = { Text("新标题") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = selected.firstOrNull()
                    if (id != null && newTitle.isNotBlank()) {
                        vm.rename(id, newTitle)
                    }
                    showRenameDialog = false
                    selectionMode = false
                    selected = emptySet()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SearchResultItem(hit: SearchHit, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                hit.conversationTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                hit.snippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conv: Conversation,
    previewQuestion: (Long) -> String?,
    previewAnswer: (Long) -> String?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(AppDimens.shapeSm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                // 大字=标题（重命名后就是名称，否则等于首问）；小字=补充信息
                val question = previewQuestion(conv.id) ?: ""
                val answer = previewAnswer(conv.id).orEmpty()
                val title = conv.title
                val renamed = question.isNotBlank() && title.isNotBlank() && title != question
                val big = if (renamed) title else (if (question.isNotBlank()) question else title)
                val small = if (renamed) question else answer
                val maxBig = 18
                if (big.length <= maxBig) {
                    Text(
                        big,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (small.isNotBlank()) {
                        Text(
                            small,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    var cut = maxBig
                    for (k in maxBig downTo 2) {
                        val c = big[k]
                        if (c == ' ' || c == '，' || c == '、' || c == '。' ||
                            c == ',' || c == '.' || c == '—' || c == '-'
                        ) { cut = k; break }
                    }
                    Text(
                        big.take(cut),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        big.drop(cut).trimStart().ifBlank { small },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!selectionMode) {
                Text(
                    formatTime(conv.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val f = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return f.format(Date(ts))
}
