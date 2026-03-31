package com.example.anonymousmeetup.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.data.model.Group
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.GroupsViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onGroupClick: (String) -> Unit,
    onAddGroupClick: () -> Unit,
    onSearchGroupsClick: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsState()
    val error by viewModel.error.collectAsState()
    val isTrackingEnabled by viewModel.isLocationTrackingEnabled.collectAsState(initial = false)
    val selectedFilter by viewModel.filter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val nearbyGroupHashes by viewModel.nearbyGroupHashes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showFabSheet by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startTracking(context, viewModel)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }
    
    val enableLocation = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startTracking(context, viewModel)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadGroups()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Группы") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showFabSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Создать группу")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        ScreenBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable(onClick = onSearchGroupsClick),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Поиск групп",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "Мои",
                        onClick = { viewModel.setFilter("Мои") },
                        label = { Text("Мои") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Рядом",
                        onClick = { viewModel.setFilter("Рядом") },
                        label = { Text("Рядом") }
                    )
                    FilterChip(
                        selected = selectedFilter == "Популярные",
                        onClick = { viewModel.setFilter("Популярные") },
                        label = { Text("Популярные") }
                    )
                }

                LocationTrackingRow(
                    isEnabled = isTrackingEnabled,
                    onToggle = { enabled ->
                        if (enabled) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPermission) {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                startTracking(context, viewModel)
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasNotifPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasNotifPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        } else {
                            stopTracking(context, viewModel)
                        }
                    }
                )

                val visibleGroups = when (selectedFilter) {
                    "Популярные" -> groups.sortedByDescending { it.createdAt }
                    "Рядом" -> groups.filter { nearbyGroupHashes.contains(it.groupHash) }
                    else -> groups
                }

                if (!isTrackingEnabled) {
                    LocationDisabledBanner(onEnableClick = { enableLocation() })
                }

                if (isLoading && visibleGroups.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        items(4) {
                            GroupSkeletonItem()
                        }
                    }
                } else if (visibleGroups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedFilter == "Рядом") {
                                    "Поблизости нет активных встреч"
                                } else {
                                    "Вы пока не состоите ни в одной группе"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (selectedFilter != "Рядом") {
                                Button(onClick = onSearchGroupsClick) {
                                    Text("Найти группы")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        items(visibleGroups) { group ->
                            GroupItem(
                                group = group,
                                onClick = { onGroupClick(group.id) }
                            )
                        }
                    }
                }

                error?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (showFabSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFabSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SheetAction(
                            title = "Создать группу",
                            subtitle = "Опиши идею и пригласи участников",
                            onClick = {
                                showFabSheet = false
                                onAddGroupClick()
                            }
                        )
                        SheetAction(
                            title = "Найти группы рядом",
                            subtitle = "Покажем доступные группы",
                            onClick = {
                                showFabSheet = false
                                onSearchGroupsClick()
                            }
                        )
                        SheetAction(
                            title = "Мои приглашения",
                            subtitle = "Скоро будет доступно",
                            onClick = {
                                showFabSheet = false
                                scope.launch {
                                    snackbarHostState.showSnackbar("Раздел в разработке")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationDisabledBanner(onEnableClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Геолокация выключена", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Включите для уведомлений о людях рядом",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            TextButton(onClick = onEnableClick) {
                Text("Включить")
            }
        }
    }
}

@Composable
private fun GroupSkeletonItem() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SkeletonLine(widthFraction = 0.6f, height = 18.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonLine(widthFraction = 0.9f, height = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonLine(widthFraction = 0.4f, height = 12.dp)
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
private fun SheetAction(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LocationTrackingRow(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Отслеживание рядом", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Уведомления при встрече участников",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Switch(checked = isEnabled, onCheckedChange = onToggle)
        }
    }
}

private fun startTracking(context: android.content.Context, viewModel: GroupsViewModel) {
    // Global proximity tracker removed in anonymous architecture.
    viewModel.setLocationTrackingEnabled(true)
}

private fun stopTracking(context: android.content.Context, viewModel: GroupsViewModel) {
    // Global proximity tracker removed in anonymous architecture.
    viewModel.setLocationTrackingEnabled(false)
}

@Composable
private fun GroupItem(
    group: Group,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium
                )
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
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "pool: ${group.poolId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onClick) {
                Text("Открыть")
            }
        }
    }
}




