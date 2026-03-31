package com.example.anonymousmeetup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.BuildConfig
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.data.model.GroupCompatibilityReport
import com.example.anonymousmeetup.data.model.GroupCompatibilityStatus
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.GroupsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchGroupsScreen(
    onNavigateBack: () -> Unit,
    onGroupJoined: () -> Unit,
    onOpenGroup: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val diagnostics by viewModel.searchDiagnostics.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val joinSuccess by viewModel.joinSuccess.collectAsState()
    val joinedGroups by viewModel.groups.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(joinSuccess) {
        if (joinSuccess != null) {
            viewModel.clearJoinSuccess()
            onGroupJoined()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск групп") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        ScreenBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchGroups(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Введите название группы") }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isSearchLoading) {
                        items(3) {
                            SearchSkeletonItem()
                        }
                    }
                    if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Text(
                                text = "Группы не найдены",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(searchResults) { group ->
                        val isJoined = joinedGroups.any { it.id == group.id }
                        val compatibility = diagnostics[group.id] ?: viewModel.compatibilityFor(group)
                        SearchGroupItem(
                            group = group,
                            compatibility = compatibility,
                            isJoined = isJoined,
                            onJoin = { viewModel.joinGroup(group) },
                            onOpen = { onOpenGroup(group.id) },
                            onMigrate = { viewModel.migrateGroup(group) }
                        )
                    }

                    error?.let { message ->
                        item {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    info?.let { message ->
                        item {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSkeletonItem() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SkeletonLine(widthFraction = 0.55f, height = 18.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonLine(widthFraction = 0.8f, height = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
            SkeletonLine(widthFraction = 0.3f, height = 12.dp)
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {}
}

@Composable
private fun SearchGroupItem(
    group: Group,
    compatibility: GroupCompatibilityReport,
    isJoined: Boolean,
    onJoin: () -> Unit,
    onOpen: () -> Unit,
    onMigrate: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = group.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = group.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.category.isNotBlank()) {
                    AssistChip(
                        onClick = { },
                        label = { Text(group.category) }
                    )
                }
                if (group.isPrivate) {
                    AssistChip(
                        onClick = { },
                        label = { Text("По приглашению") }
                    )
                }
                if (compatibility.status != GroupCompatibilityStatus.JOINABLE) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                when (compatibility.status) {
                                    GroupCompatibilityStatus.LEGACY_REQUIRES_MIGRATION -> "Legacy"
                                    GroupCompatibilityStatus.MISSING_JOIN_TOKEN -> "Без ключа"
                                    GroupCompatibilityStatus.BROKEN_JOIN_TOKEN -> "Токен повреждён"
                                    GroupCompatibilityStatus.MALFORMED -> "Битая"
                                    GroupCompatibilityStatus.JOINABLE -> "OK"
                                }
                            )
                        }
                    )
                }
            }
            if (compatibility.status != GroupCompatibilityStatus.JOINABLE) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = compatibility.userMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isJoined) {
                    Button(onClick = onOpen) {
                        Text("Открыть")
                    }
                } else if (compatibility.isJoinable) {
                    OutlinedButton(onClick = onJoin) {
                        Text("Вступить")
                    }
                } else {
                    OutlinedButton(onClick = { }, enabled = false) {
                        Text("Недоступно")
                    }
                    if (BuildConfig.DEBUG && compatibility.status == GroupCompatibilityStatus.LEGACY_REQUIRES_MIGRATION) {
                        OutlinedButton(onClick = onMigrate) {
                            Text("Миграция")
                        }
                    }
                }
            }
        }
    }
}

