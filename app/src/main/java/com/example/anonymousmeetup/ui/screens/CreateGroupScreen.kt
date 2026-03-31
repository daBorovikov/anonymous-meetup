package com.example.anonymousmeetup.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.anonymousmeetup.ui.components.ScreenBackground
import com.example.anonymousmeetup.ui.viewmodels.GroupsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onNavigateBack: () -> Unit,
    onGroupCreated: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("Спорт") }
    var isPrivateGroup by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val categories = listOf("Спорт", "Кино", "Игры", "Учёба", "Другое")
    val error by viewModel.error.collectAsState()
    val createSuccess by viewModel.createSuccess.collectAsState()

    LaunchedEffect(createSuccess) {
        if (createSuccess != null) {
            viewModel.clearCreateSuccess()
            onGroupCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Создать группу") },
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
                    .padding(20.dp)
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Новая группа",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Название группы") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = groupDescription,
                            onValueChange = { groupDescription = it },
                            label = { Text("Описание") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box {
                            OutlinedTextField(
                                value = selectedCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Категория") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { categoryExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать")
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            selectedCategory = category
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Приватность",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isPrivateGroup,
                                onClick = { isPrivateGroup = false },
                                label = { Text("Открытая") }
                            )
                            FilterChip(
                                selected = isPrivateGroup,
                                onClick = { isPrivateGroup = true },
                                label = { Text("По приглашению") }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (groupName.isBlank()) {
                                    localError = "Введите название группы"
                                    return@Button
                                }
                                localError = null
                                viewModel.createGroup(
                                    name = groupName.trim(),
                                    description = groupDescription.trim(),
                                    category = selectedCategory,
                                    isPrivate = isPrivateGroup
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Создать группу")
                        }

                        localError?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        error?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

