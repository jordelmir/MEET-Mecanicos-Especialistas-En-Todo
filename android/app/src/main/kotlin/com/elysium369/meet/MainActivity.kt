package com.elysium369.meet
import com.elysium369.meet.core.obd.DtcDatabaseHelper

import android.os.Bundle
import android.os.Build
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    primary = Color(0xFF39FF14),
    onPrimary = Color.Black,
    secondary = Color(0xFFFF6B35),
    onSecondary = Color.Black,
    tertiary = Color(0xFFCC00FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0A0E1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFF39FF14),
    error = Color(0xFFFF003C)
)


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ObdViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            // Handle denied permissions (e.g., show a dialog)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DtcDatabaseHelper.init(this)
        
        checkPermissions()

        setContent {
            MaterialTheme(colorScheme = CyberpunkColorScheme) {
                MeetApp(viewModel)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
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
    
    val trips by obdViewModel.trips.collectAsState()
    val alerts by obdViewModel.maintenanceAlerts.collectAsState()
    val customPids by obdViewModel.customPids.collectAsState()
    val isPremium by obdViewModel.isPremium.collectAsState()
    val selectedVehicle by obdViewModel.selectedVehicle.collectAsState()

    Scaffold(
        containerColor = Color(0xFF060612),
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
            modifier = Modifier.fillMaxSize().padding(paddingValues)
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
                    trips = trips,
                    isPremium = isPremium,
                    onExportPdf = { obdViewModel.exportTripToPdf(it) }
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
            composable("support_chat") {
                val vehicle by obdViewModel.selectedVehicle.collectAsState()
                val vehicleLabel = vehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo Genérico"
                SupportChatScreen(
                    onBack = { navController.popBackStack() },
                    vehicleInfo = vehicleLabel
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
                    onRunTest = { obdViewModel.runAdapterCloneTest() }
                )
            }
            composable("maintenance") {
                val currentOdo by obdViewModel.currentOdometer.collectAsState()
                MaintenanceScreen(
                    alerts = alerts,
                    currentOdometer = currentOdo.toLong(),
                    onMarkAsDone = { obdViewModel.markMaintenanceDone(it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("custom_pid") {
                CustomPidEditorScreen(
                    customPids = customPids,
                    onAddCustomPid = { obdViewModel.addCustomPid(it) },
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
        containerColor = Color(0xFF0A0E1A),
        contentColor = Color(0xFF39FF14)
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, "Home") },
            label = { Text("Inicio") },
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") { launchSingleTop = true; restoreState = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF39FF14),
                selectedTextColor = Color(0xFF39FF14),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFF39FF14).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Build, "Scanner") },
            label = { Text("Scanner") },
            selected = currentRoute == "scanner",
            onClick = { navController.navigate("scanner") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF39FF14),
                selectedTextColor = Color(0xFF39FF14),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFF39FF14).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Warning, "DTCs") },
            label = { Text("DTCs") },
            selected = currentRoute == "dtc",
            onClick = { navController.navigate("dtc") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFF003C),
                selectedTextColor = Color(0xFFFF003C),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFFFF003C).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, "Garage") },
            label = { Text("Garage") },
            selected = currentRoute == "garage",
            onClick = { navController.navigate("garage") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFCC00FF),
                selectedTextColor = Color(0xFFCC00FF),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFFCC00FF).copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Star, "PRO") },
            label = { Text("PRO") },
            selected = currentRoute == "pro_hub",
            onClick = { navController.navigate("pro_hub") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFFD700),
                selectedTextColor = Color(0xFFFFD700),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0xFFFFD700).copy(alpha = 0.1f)
            )
        )
    }
}
