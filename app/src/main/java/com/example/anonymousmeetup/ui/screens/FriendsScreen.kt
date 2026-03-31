package com.example.anonymousmeetup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.data.model.LocalContact
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.FriendsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Контакты") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
        )
    }) { paddingValues ->
        ScreenBackground {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (friends.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Пока нет локальных контактов")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        items(friends) { friend -> ContactItem(friend) }
                    }
                }
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun ContactItem(contact: LocalContact) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = contact.alias, style = MaterialTheme.typography.titleMedium)
            Text(text = "Conversation: ${contact.sessionId.take(8)}...", style = MaterialTheme.typography.bodySmall)
            Text(text = "Добавлен: ${dateFormat.format(Date(contact.addedAt))}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "pool: ${contact.currentChatHash}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
