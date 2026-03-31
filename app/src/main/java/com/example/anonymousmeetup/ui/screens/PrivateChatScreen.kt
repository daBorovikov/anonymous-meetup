package com.example.anonymousmeetup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.data.model.SessionStatus
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.PrivateChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    viewModel: PrivateChatViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val listState = rememberLazyListState()

    var text by remember { mutableStateOf("") }
    var showDebug by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        viewModel.bindConversation(conversationId)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val canSend = session?.sessionStatus != SessionStatus.PENDING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.localAlias?.ifBlank { "Приватный чат" } ?: "Приватный чат") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { showDebug = !showDebug }) {
                        Text(if (showDebug) "Скрыть debug" else "Debug")
                    }
                }
            )
        }
    ) { paddingValues ->
        ScreenBackground(modifier = Modifier.padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                StatusBanner(status = session?.sessionStatus)
                Spacer(modifier = Modifier.height(8.dp))

                if (showDebug && session != null) {
                    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("conversationId: ${session?.conversationId}", style = MaterialTheme.typography.labelSmall)
                            Text("poolId: ${session?.poolId}", style = MaterialTheme.typography.labelSmall)
                            Text("status: ${session?.sessionStatus}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        PrivateMessageItem(message)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.sendLocation() }, modifier = Modifier.weight(1f), enabled = canSend) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Геопозиция")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (canSend) "Сообщение" else "Ожидание handshake") },
                        enabled = canSend
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                viewModel.sendText(text)
                                text = ""
                            }
                        },
                        enabled = canSend
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Отправить")
                    }
                }

                if (!info.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Snackbar { Text(info ?: "") }
                }
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Snackbar { Text(error ?: "") }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(status: SessionStatus?) {
    val text = when (status) {
        SessionStatus.PENDING -> "Ожидание подтверждения handshake"
        SessionStatus.ACCEPTED -> "Handshake подтверждён. Канал готов к обмену сообщениями"
        SessionStatus.ACTIVE -> "Сессия активна"
        SessionStatus.FAILED -> "Сессия помечена как failed"
        null -> "Загрузка локальной беседы"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Text(text = text, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PrivateMessageItem(message: com.example.anonymousmeetup.data.model.PrivateMessageUiModel) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val alignment = if (message.isMine) Alignment.End else Alignment.Start
    val color = if (message.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Text(
            text = "${if (message.isMine) "Вы" else "Собеседник"} • ${dateFormat.format(Date(message.timestamp))}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Surface(color = color, shape = MaterialTheme.shapes.medium) {
            Text(text = message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}
