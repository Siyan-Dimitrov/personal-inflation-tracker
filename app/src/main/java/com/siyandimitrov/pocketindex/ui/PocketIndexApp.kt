package com.siyandimitrov.pocketindex.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.siyandimitrov.pocketindex.ui.basket.BasketScreen
import com.siyandimitrov.pocketindex.ui.capture.ReceiptCaptureViewModel
import com.siyandimitrov.pocketindex.ui.overview.OverviewScreen
import com.siyandimitrov.pocketindex.ui.receipts.ReceiptsScreen
import com.siyandimitrov.pocketindex.ui.receipts.ReceiptDetailScreen
import com.siyandimitrov.pocketindex.ui.receipts.ReceiptInboxScreen
import com.siyandimitrov.pocketindex.ui.settings.SettingsScreen
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    TopLevelDestination("overview", "Overview", Icons.Rounded.Home),
    TopLevelDestination("receipts", "Receipts", Icons.AutoMirrored.Rounded.ReceiptLong),
    TopLevelDestination("basket", "Basket", Icons.Rounded.ShoppingCart),
    TopLevelDestination("settings", "Settings", Icons.Rounded.Settings),
)

@Composable
fun PocketIndexApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val activity = context as Activity
    val captureViewModel: ReceiptCaptureViewModel = hiltViewModel()
    val captureState by captureViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val imageUri = scan?.pages?.firstOrNull()?.imageUri
            if (imageUri != null) {
                captureViewModel.processScan(imageUri)
            } else {
                captureViewModel.reportScannerFailure(
                    IllegalStateException("The scan did not contain a JPEG page."),
                )
            }
        }
    }
    val launchScanner = {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build(),
                )
            }
            .addOnFailureListener(captureViewModel::reportScannerFailure)
        Unit
    }

    LaunchedEffect(captureState.completedReceiptId, captureState.message) {
        captureState.completedReceiptId?.let { receiptId ->
            navController.navigate("receipt/$receiptId") {
                launchSingleTop = true
            }
        }
        captureState.message?.let { snackbarHostState.showSnackbar(it) }
        if (captureState.message != null || captureState.completedReceiptId != null) {
            captureViewModel.clearEvent()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                OverviewScreen(
                    isScanning = captureState.isProcessing,
                    onScanReceipt = launchScanner,
                )
            }
            composable("receipts") {
                ReceiptInboxScreen(
                    onReceiptSelected = { receiptId ->
                        navController.navigate("receipt/$receiptId")
                    },
                    onScanReceipt = launchScanner,
                    onAddManualReceipt = {
                        navController.navigate("receipt-entry")
                    },
                )
            }
            composable("receipt-entry") {
                ReceiptsScreen(startWithManualReceipt = true)
            }
            composable(
                route = "receipt/{receiptId}",
                arguments = listOf(
                    navArgument("receiptId") { type = NavType.LongType },
                ),
            ) {
                ReceiptDetailScreen(
                    onBack = navController::popBackStack,
                    onReview = { receiptId ->
                        navController.navigate("receipt-review/$receiptId")
                    },
                )
            }
            composable(
                route = "receipt-review/{receiptId}",
                arguments = listOf(
                    navArgument("receiptId") { type = NavType.LongType },
                ),
            ) {
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
