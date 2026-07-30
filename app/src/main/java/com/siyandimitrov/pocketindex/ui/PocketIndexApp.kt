package com.siyandimitrov.pocketindex.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.siyandimitrov.pocketindex.ui.basket.BasketScreen
import com.siyandimitrov.pocketindex.ui.overview.OverviewScreen
import com.siyandimitrov.pocketindex.ui.receipts.ReceiptsScreen
import com.siyandimitrov.pocketindex.ui.settings.SettingsScreen

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    TopLevelDestination("overview", "Overview", Icons.Rounded.Home),
    TopLevelDestination("receipts", "Receipts", Icons.Rounded.ReceiptLong),
    TopLevelDestination("basket", "Basket", Icons.Rounded.ShoppingCart),
    TopLevelDestination("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun PocketIndexApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "overview",
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        ) {
            composable("overview") {
                OverviewScreen(onScanReceipt = { navController.navigate("receipts") })
            }
            composable("receipts") {
                ReceiptsScreen()
            }
            composable("basket") {
                BasketScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
}
