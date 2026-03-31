package com.example.anonymousmeetup.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.anonymousmeetup.data.preferences.UserPreferences
import com.example.anonymousmeetup.ui.components.ScreenBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    userPreferences: UserPreferences
) {
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Добро пожаловать",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Как вас можно называть?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
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
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = {
                            nickname = it
                            error = null
                        },
                        label = { Text("Введите ник") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = error != null
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (nickname.isNotBlank()) {
                                scope.launch {
                                    try {
                                        userPreferences.saveNickname(nickname)
                                        navController.navigate("groups") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    } catch (e: Exception) {
                                        error = "Ошибка сохранения данных: ${e.message}"
                                    }
                                }
                            } else {
                                error = "Введите ник"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Продолжить")
                    }
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
} 

