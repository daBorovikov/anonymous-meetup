package com.example.anonymousmeetup.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.anonymousmeetup.ui.screens.ChatScreen
import com.example.anonymousmeetup.ui.screens.CreateGroupScreen
import com.example.anonymousmeetup.ui.screens.EncountersScreen
import com.example.anonymousmeetup.ui.screens.FriendsScreen
import com.example.anonymousmeetup.ui.screens.GroupDetailScreen
import com.example.anonymousmeetup.ui.screens.GroupsScreen
import com.example.anonymousmeetup.ui.screens.LoginScreen
import com.example.anonymousmeetup.ui.screens.MapScreen
import com.example.anonymousmeetup.ui.screens.PrivateChatScreen
import com.example.anonymousmeetup.ui.screens.ProfileScreen
import com.example.anonymousmeetup.ui.screens.RegisterScreen
import com.example.anonymousmeetup.ui.screens.SearchGroupsScreen
import com.example.anonymousmeetup.ui.theme.AnonymousMeetupTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnonymousMeetupTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomItems = listOf(
        BottomNavItem("groups", "Группы", Icons.Default.People),
        BottomNavItem("map", "Карта", Icons.Default.Map),
        BottomNavItem("profile", "Профиль", Icons.Default.Person)
    )

    val bottomRoutes = bottomItems.map { it.route }.toSet()
    val showBottomBar = currentDestination?.route in bottomRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "groups",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate("register") },
                    onLoginSuccess = {
                        navController.navigate("groups") { popUpTo("login") { inclusive = true } }
                    }
                )
            }
            composable("register") {
                RegisterScreen(
                    onNavigateToLogin = {
                        navController.navigate("login") { popUpTo("register") { inclusive = true } }
                    },
                    onRegisterSuccess = {
                        navController.navigate("groups") { popUpTo("register") { inclusive = true } }
                    }
                )
            }
            composable("groups") {
                GroupsScreen(
                    onGroupClick = { groupId -> navController.navigate("group/$groupId") },
                    onAddGroupClick = { navController.navigate("create_group") },
                    onSearchGroupsClick = { navController.navigate("groups_search") }
                )
            }
            composable("group/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                GroupDetailScreen(
                    groupId = groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenChat = { id -> navController.navigate("chat/$id") }
                )
            }
            composable("groups_search") {
                SearchGroupsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGroupJoined = {
                        navController.navigate("groups") { popUpTo("groups_search") { inclusive = true } }
                    },
                    onOpenGroup = { groupId -> navController.navigate("group/$groupId") }
                )
            }
            composable("chat/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                ChatScreen(
                    groupId = groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { navController.navigate("map") },
                    onOpenPrivateChat = { conversationId -> navController.navigate("private_chat/$conversationId") }
                )
            }
            composable("private_chat/{conversationId}") { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                PrivateChatScreen(
                    conversationId = conversationId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("friends") {
                FriendsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("encounters") {
                EncountersScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("map") {
                MapScreen(
                    groupId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPrivateChat = { conversationId -> navController.navigate("private_chat/$conversationId") }
                )
            }
            composable("map/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId")
                MapScreen(
                    groupId = groupId,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPrivateChat = { conversationId -> navController.navigate("private_chat/$conversationId") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    onFriendsClick = { navController.navigate("friends") },
                    onEncountersClick = { navController.navigate("encounters") },
                    onGroupsClick = { navController.navigate("groups") },
                    onLogout = {
                        navController.navigate("groups") { popUpTo("groups") { inclusive = true } }
                    },
                    onOpenPrivateChat = { conversationId -> navController.navigate("private_chat/$conversationId") }
                )
            }
            composable("create_group") {
                CreateGroupScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGroupCreated = {
                        navController.navigate("groups") { popUpTo("groups") { inclusive = true } }
                    }
                )
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

