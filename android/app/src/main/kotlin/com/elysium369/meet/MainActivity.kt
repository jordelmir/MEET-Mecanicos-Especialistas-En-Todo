package com.elysium369.meet
import com.elysium369.meet.core.obd.DtcDatabaseHelper

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.screens.*
import com.elysium369.meet.ui.components.AdapterSearchSheet
import com.elysium369.meet.ui.components.ConnectionStatusBar
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Star

val CyberpunkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00FFCC),
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = androidx.compose.ui.graphics.Color(0xFFFF6B35),
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    tertiary = androidx.compose.ui.graphics.Color(0xFFCC00FF),
    background = androidx.compose.ui.graphics.Color.Black,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
    onSurface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF141414),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF00FFCC),
    error = androidx.compose.ui.graphics.Color(0xFFFF003C)
)


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ObdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DtcDatabaseHelper.init(this)
        setContent {
            MaterialTheme(colorScheme = CyberpunkColorScheme) {
                MeetApp(viewModel)
            }
        }
    }
}

@Composable
fun MeetApp(obdViewModel: ObdViewModel) {
    val navController = rememberNavController()
    
    // Verificar si onboarding ya fue completado
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
    var onboardingDone by remember { mutableStateOf(sharedPrefs.getBoolean("onboarding_completed", false)) }
    
    val startDestination = if (onboardingDone) "home" else "onboarding"
    
    Scaffold(
        bottomBar = {
            // Solo mostrar BottomNav si NO estamos en onboarding/auth/connect
            val currentRoute = navController.currentBackStackEntryAsState().value
                ?.destination?.route
            val hideNavRoutes = listOf("onboarding", "auth", "connect", "premium")
            if (currentRoute !in hideNavRoutes && currentRoute != null) {
                MeetBottomNavigation(navController)
            }
        },
        topBar = {
            val currentRoute = navController.currentBackStackEntryAsState().value
                ?.destination?.route
            val hideBarRoutes = listOf("onboarding", "auth", "connect", "premium")
            if (currentRoute !in hideBarRoutes && currentRoute != null) {
                ConnectionStatusBar(viewModel = obdViewModel)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onFinish = { 
                        sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
                        navController.navigate("auth") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("auth") {
                AuthScreen(
                    onAuthSuccess = { navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }},
                    onOfflineMode = { navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }}
                )
            }
            composable("home") {
                HomeScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("scanner") {
                ScannerScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("dtc") {
                DtcScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("terminal") {
                TerminalScreen(viewModel = obdViewModel)
            }
            composable("garage") {
                GarageScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("vehicle_form") {
                VehicleFormScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("trips") {
                TripScreen(
                    trips = emptyList(),
                    isPremium = true,
                    onExportPdf = {}
                )
            }
            composable("ai/{dtcCode}") { backStack ->
                val dtcCode = backStack.arguments?.getString("dtcCode") ?: ""
                AiDiagnosticScreen(
                    dtcCode = dtcCode,
                    onBack = { navController.popBackStack() },
                    viewModel = obdViewModel
                )
            }
            composable("ai") {
                AiDiagnosticScreen(
                    dtcCode = "",
                    onBack = { navController.popBackStack() },
                    viewModel = obdViewModel
                )
            }
            composable("pro_hub") {
                ProHubScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("topology") {
                TopologyScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("active_tests") {
                ActiveTestsScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("service_resets") {
                ServiceResetsScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("reports") {
                ReportScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("settings") {
                SettingsScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("premium") {
                PremiumScreen(
                    onClose = { navController.popBackStack() }
                )
            }
            composable("connect") {
                AdapterSearchSheet(
                    onDismiss = { navController.popBackStack() },
                    onConnect = { name, mac -> 
                        obdViewModel.connect(mac)
                        navController.popBackStack()
                    }
                )
            }
            composable("clone_test") {
                CloneTestScreen(
                    onRunTest = { emptyList() }
                )
            }
            composable("maintenance") {
                MaintenanceScreen(
                    alerts = emptyList(),
                    currentOdometer = 0,
                    onMarkAsDone = {},
                    onBack = { navController.popBackStack() }
                )
            }
            composable("custom_pid") {
                CustomPidEditorScreen(
                    customPids = emptyList(),
                    onAddCustomPid = { _ -> },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun MeetBottomNavigation(navController: NavController) {
    val currentRoute = navController.currentBackStackEntryAsState()
        .value?.destination?.route
    
    NavigationBar(
        containerColor = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
        contentColor = androidx.compose.ui.graphics.Color(0xFF00FFCC)
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, "Home") },
            label = { Text("Inicio") },
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") { launchSingleTop = true; restoreState = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.ui.graphics.Color(0xFF00FFCC),
                selectedTextColor = androidx.compose.ui.graphics.Color(0xFF00FFCC),
                unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                unselectedTextColor = androidx.compose.ui.graphics.Color.Gray,
                indicatorColor = androidx.compose.ui.graphics.Color(0xFF00FFCC).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Build, "Scanner") },
            label = { Text("Scanner") },
            selected = currentRoute == "scanner",
            onClick = { navController.navigate("scanner") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.ui.graphics.Color(0xFF00FFCC),
                selectedTextColor = androidx.compose.ui.graphics.Color(0xFF00FFCC),
                unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                unselectedTextColor = androidx.compose.ui.graphics.Color.Gray,
                indicatorColor = androidx.compose.ui.graphics.Color(0xFF00FFCC).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Warning, "DTCs") },
            label = { Text("DTCs") },
            selected = currentRoute == "dtc",
            onClick = { navController.navigate("dtc") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.ui.graphics.Color(0xFFFF003C),
                selectedTextColor = androidx.compose.ui.graphics.Color(0xFFFF003C),
                unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                unselectedTextColor = androidx.compose.ui.graphics.Color.Gray,
                indicatorColor = androidx.compose.ui.graphics.Color(0xFFFF003C).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, "Garage") },
            label = { Text("Garage") },
            selected = currentRoute == "garage",
            onClick = { navController.navigate("garage") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.ui.graphics.Color(0xFFCC00FF),
                selectedTextColor = androidx.compose.ui.graphics.Color(0xFFCC00FF),
                unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                unselectedTextColor = androidx.compose.ui.graphics.Color.Gray,
                indicatorColor = androidx.compose.ui.graphics.Color(0xFFCC00FF).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Star, "PRO") },
            label = { Text("PRO") },
            selected = currentRoute == "pro_hub",
            onClick = { navController.navigate("pro_hub") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.ui.graphics.Color(0xFFFFD700),
                selectedTextColor = androidx.compose.ui.graphics.Color(0xFFFFD700),
                unselectedIconColor = androidx.compose.ui.graphics.Color.Gray,
                unselectedTextColor = androidx.compose.ui.graphics.Color.Gray,
                indicatorColor = androidx.compose.ui.graphics.Color(0xFFFFD700).copy(alpha = 0.1f)
            )
        )
    }
}
