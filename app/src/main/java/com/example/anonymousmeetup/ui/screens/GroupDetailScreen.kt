package com.example.anonymousmeetup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.BuildConfig
import com.example.anonymousmeetup.data.model.GroupCompatibilityStatus
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.GroupDetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val group by viewModel.group.collectAsState()
    val compatibility by viewModel.compatibility.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val isMember by viewModel.isMember.collectAsState()

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var joinLoading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
            joinLoading = false
        }
    }
    LaunchedEffect(info) {
        info?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Группа") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        ScreenBackground(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Описание", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = group?.description?.ifBlank { "Описание не задано" } ?: "Описание не задано",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val category = group?.category ?: ""
                            if (category.isNotBlank()) {
                                AssistChip(onClick = { }, label = { Text(category) })
                            }
                            if (group?.isPrivate == true) {
                                AssistChip(onClick = { }, label = { Text("По приглашению") })
                            }
                            compatibility?.takeIf { it.status != GroupCompatibilityStatus.JOINABLE }?.let { report ->
                                AssistChip(onClick = { }, label = { Text(report.userMessage) })
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "poolId: ${group?.poolId?.ifBlank { "—" } ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (isMember) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onOpenChat(groupId) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Открыть чат")
                        }
                        OutlinedButton(
                            onClick = { showLeaveConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Выйти")
                        }
                    }
                } else {
                    val report = compatibility
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (report?.isJoinable == true) {
                            Button(
                                onClick = {
                                    if (!joinLoading) {
                                        joinLoading = true
                                        viewModel.joinGroup(groupId) {
                                            joinLoading = false
                                            scope.launch { snackbarHostState.showSnackbar("Вы вступили в группу") }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !joinLoading
                            ) {
                                Text("Вступить в группу")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false
                            ) {
                                Text(report?.userMessage ?: "Группа недоступна")
                            }
                            if (BuildConfig.DEBUG && report?.status == GroupCompatibilityStatus.LEGACY_REQUIRES_MIGRATION) {
                                OutlinedButton(
                                    onClick = { viewModel.migrateGroup(groupId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Мигрировать legacy группу")
                                }
                            }
                        }
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Приватность", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Участники не хранятся на сервере. Вступление сохраняет локальный group key и подписку на анонимный pool.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Выйти из группы?") },
            text = { Text("Группа останется в каталоге, но исчезнет из ваших локальных подписок.") },
            confirmButton = {
                Button(onClick = {
                    showLeaveConfirm = false
                    viewModel.leaveGroup(groupId) { onNavigateBack() }
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

