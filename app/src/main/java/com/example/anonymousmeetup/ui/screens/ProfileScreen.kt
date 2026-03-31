package com.example.anonymousmeetup.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.BuildConfig
import com.example.anonymousmeetup.data.model.SessionStatus
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onFriendsClick: () -> Unit,
    onEncountersClick: () -> Unit,
    onGroupsClick: () -> Unit,
    onLogout: () -> Unit,
    onOpenPrivateChat: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val nickname by viewModel.nickname.collectAsState(initial = "")
    val publicKey by viewModel.publicKey.collectAsState()
    val isTrackingEnabled by viewModel.isLocationTrackingEnabled.collectAsState(initial = false)
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val secureBackupEnabled by viewModel.secureBackupEnabled.collectAsState(initial = false)
    val backupPayload by viewModel.backupPayload.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val debugTraces by viewModel.debugTraces.collectAsState(initial = emptyList())
    val isDebugBusy by viewModel.isDebugBusy.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEditDialog by remember { mutableStateOf(false) }
    var editedNickname by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPassword by remember { mutableStateOf("") }
    var importData by remember { mutableStateOf("") }
    var showStartPrivateDialog by remember { mutableStateOf(false) }
    var peerAlias by remember { mutableStateOf("") }
    var peerPublicKey by remember { mutableStateOf("") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Scaffold(topBar = { TopAppBar(title = { Text("Профиль") }) }, snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        ScreenBackground(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = nickname?.ifBlank { "Аноним" } ?: "Аноним", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { editedNickname = nickname ?: ""; showEditDialog = true }) { Text("Изменить") }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Ваш локальный профиль для анонимной переписки", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Контактный ключ", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = publicKey ?: "—", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Стабильный публичный ключ для handshake. На сервер не отправляются alias и локальные имена.", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val key = publicKey ?: return@Button
                                clipboardManager.setText(AnnotatedString(key))
                                scope.launch { snackbarHostState.showSnackbar("Контактный ключ скопирован") }
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Скопировать")
                            }
                            Button(onClick = { showStartPrivateDialog = true }, modifier = Modifier.weight(1f)) {
                                Text("Начать чат")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedActionButton(text = "Экспорт", icon = Icons.Default.UploadFile, modifier = Modifier.weight(1f)) { showExportDialog = true }
                            OutlinedActionButton(text = "Импорт", icon = Icons.Default.VpnKey, modifier = Modifier.weight(1f)) { showImportDialog = true }
                        }
                    }
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Анонимные беседы", style = MaterialTheme.typography.titleSmall)
                        }
                        if (conversations.isEmpty()) {
                            Text(
                                "Здесь появятся исходящие и входящие приглашения в приватные чаты. Новые invite можно принять или отклонить после открытия беседы.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        } else {
                            conversations.take(8).forEach { conversation ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (conversation.isPendingIncoming) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.large,
                                    onClick = { onOpenPrivateChat(conversation.conversationId) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(conversation.title, style = MaterialTheme.typography.titleSmall)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(conversation.subtitle, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (conversation.isPendingIncoming) {
                                                Text("Новый invite", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                            Text(statusLabel(conversation.status), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwitchRow(Icons.Default.Notifications, "Уведомления", "Локальные уведомления приложения", notificationsEnabled) { enabled ->
                            viewModel.setNotificationsEnabled(enabled)
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasNotifPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                if (!hasNotifPermission) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        SwitchRow(Icons.Default.LocationOn, "Геолокация", "Используется только при добровольной отправке LOCATION", isTrackingEnabled) { enabled ->
                            if (enabled) {
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                else viewModel.setLocationTrackingEnabled(true)
                            } else {
                                viewModel.setLocationTrackingEnabled(false)
                            }
                        }
                        SwitchRow(Icons.Default.Security, "Secure backup", "Экспорт/импорт identity и дневных ключей", secureBackupEnabled) {
                            viewModel.setSecureBackupEnabled(it)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Разрешение геолокации")
                                Text(if (hasLocationPermission) "Разрешено" else "Не разрешено", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { openAppSettings(context) }) { Text("Настройки") }
                        }
                        ProfileActionRow(icon = Icons.Default.People, text = "Контакты", onClick = onFriendsClick)
                        ProfileActionRow(icon = Icons.Default.Place, text = "Встреченные", onClick = onEncountersClick)
                        ProfileActionRow(icon = Icons.Default.LocationOn, text = "Мои группы", onClick = onGroupsClick)
                    }
                }

                if (BuildConfig.DEBUG) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Debug tools", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Сброс локального anonymous state, очистка legacy кэшей, пересинхронизация групп и проверочный сценарий для live/dev окружения.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.resetLocalAnonymousState() }, enabled = !isDebugBusy, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reset state")
                                }
                                OutlinedButton(onClick = { viewModel.clearLegacyCaches() }, enabled = !isDebugBusy, modifier = Modifier.weight(1f)) {
                                    Text("Clear legacy")
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { viewModel.resyncGroupsFromServer() }, enabled = !isDebugBusy, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resync groups")
                                }
                                Button(onClick = { viewModel.runGroupVerificationScenario() }, enabled = !isDebugBusy, modifier = Modifier.weight(1f)) {
                                    Text(if (isDebugBusy) "Выполняется" else "Run scenario")
                                }
                            }
                            if (debugTraces.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Последние trace", style = MaterialTheme.typography.titleSmall)
                                    TextButton(onClick = { viewModel.clearTraceLog() }) { Text("Очистить") }
                                }
                                debugTraces.take(5).forEach { entry ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            Text(
                                                text = "${entry.level} • ${entry.tag} • ${formatDebugTime(entry.timestamp)}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(entry.message, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(onClick = { viewModel.logout(); onLogout() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("Выйти")
                }

                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    LaunchedEffect(backupPayload) {
        val payload = backupPayload ?: return@LaunchedEffect
        clipboardManager.setText(AnnotatedString(payload))
        snackbarHostState.showSnackbar("Backup скопирован в буфер")
        viewModel.consumeBackupPayload()
    }
    LaunchedEffect(info) {
        info?.let { snackbarHostState.showSnackbar(it); viewModel.clearInfo() }
    }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Изменить ник") },
            text = { OutlinedTextField(value = editedNickname, onValueChange = { editedNickname = it }, label = { Text("Новый ник") }) },
            confirmButton = { Button(onClick = { if (editedNickname.isNotBlank()) { viewModel.updateNickname(editedNickname.trim()); showEditDialog = false } }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Отмена") } }
        )
    }
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Экспорт ключей") },
            text = { OutlinedTextField(value = exportPassword, onValueChange = { exportPassword = it }, label = { Text("Пароль для backup") }) },
            confirmButton = { Button(onClick = { if (exportPassword.isNotBlank()) { viewModel.exportBackup(exportPassword); exportPassword = ""; showExportDialog = false } }) { Text("Экспорт") } },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Отмена") } }
        )
    }
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Импорт ключей") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = importData, onValueChange = { importData = it }, label = { Text("Backup JSON") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = importPassword, onValueChange = { importPassword = it }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { if (importData.isNotBlank() && importPassword.isNotBlank()) { viewModel.importBackup(importData, importPassword); importData = ""; importPassword = ""; showImportDialog = false } }) { Text("Импорт") } },
            dismissButton = { TextButton(onClick = { showImportDialog = false }) { Text("Отмена") } }
        )
    }
    if (showStartPrivateDialog) {
        AlertDialog(
            onDismissRequest = { showStartPrivateDialog = false },
            title = { Text("Новый приватный чат") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = peerAlias, onValueChange = { peerAlias = it }, label = { Text("Локальное имя собеседника") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = peerPublicKey, onValueChange = { peerPublicKey = it }, label = { Text("Публичный контактный ключ") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (peerPublicKey.isNotBlank()) {
                        viewModel.startPrivateChat(peerPublicKey, peerAlias.ifBlank { null }) { conversationId ->
                            if (conversationId != null) {
                                showStartPrivateDialog = false
                                peerAlias = ""
                                peerPublicKey = ""
                                onOpenPrivateChat(conversationId)
                            }
                        }
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showStartPrivateDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.labelSmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OutlinedActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text)
    }
}

@Composable
private fun ProfileActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(intent)
}

private fun formatDebugTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun statusLabel(status: SessionStatus): String {
    return when (status) {
        SessionStatus.PENDING -> "pending"
        SessionStatus.ACCEPTED -> "accepted"
        SessionStatus.ACTIVE -> "active"
        SessionStatus.REJECTED -> "rejected"
        SessionStatus.FAILED -> "failed"
    }
}
