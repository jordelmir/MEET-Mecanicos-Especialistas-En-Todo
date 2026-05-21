package com.elysium369.meet

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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.FleetChatViewModel
import com.elysium369.meet.ui.screens.*
import com.elysium369.meet.ui.screens.chat.*
import com.elysium369.meet.core.livelink.LiveLinkServer
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
    primary = Color(0xFF00FFD4),
    onPrimary = Color.Black,
    secondary = Color(0xFFBB00FF),
    onSecondary = Color.Black,
    tertiary = Color(0xFF00E5FF),
    background = Color(0xFF050B15),
    onBackground = Color(0xFFF0F2F5),
    surface = Color(0xFF0F1B30),
    onSurface = Color(0xFFF0F2F5),
    surfaceVariant = Color(0xFF152640),
    onSurfaceVariant = Color(0xFF00FFD4),
    surfaceContainerHighest = Color(0xFF1A3050),
    surfaceContainerHigh = Color(0xFF152B48),
    surfaceContainer = Color(0xFF112240),
    surfaceContainerLow = Color(0xFF0D1C35),
    surfaceContainerLowest = Color(0xFF08142A),
    inverseSurface = Color(0xFF00FFD4),
    inverseOnSurface = Color(0xFF050B15),
    outline = Color(0xFF1E3355),
    outlineVariant = Color(0xFF152640),
    error = Color(0xFFFF1744),
    errorContainer = Color(0xFF3D0012)
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
    val liveLinkServer = remember { LiveLinkServer() }

    // Stop server when leaving composition
    DisposableEffect(Unit) {
        obdViewModel.attachLiveLinkServer(liveLinkServer)
        onDispose { 
            liveLinkServer.stop()
            obdViewModel.detachLiveLinkServer()
        }
    }
    
    // Verificar si onboarding ya fue completado
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
    var onboardingDone by remember { mutableStateOf(sharedPrefs.getBoolean("onboarding_completed", false)) }
    
    val startDestination = if (onboardingDone) "home" else "onboarding"
    
    val trips by obdViewModel.trips.collectAsState()
    val customPids by obdViewModel.customPids.collectAsState()
    val isPremium by obdViewModel.isPremium.collectAsState()

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
            composable("vehicle_detail/{vehicleId}") { backStack ->
                val vehicleId = backStack.arguments?.getString("vehicleId") ?: return@composable
                val vehicles by obdViewModel.vehicles.collectAsState()
                val vehicle = vehicles.find { it.id == vehicleId }
                
                if (vehicle != null) {
                    VehicleDetailScreen(
                        vehicleId = vehicle.id,
                        vin = vehicle.vin,
                        make = vehicle.make,
                        model = vehicle.model,
                        year = vehicle.year,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
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
                    onExportPdf = { obdViewModel.exportTripToPdf(it) },
                    onGenerateMockTrip = { obdViewModel.generateMockTrip() }
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
            composable("oscilloscope") {
                OscilloscopeScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = obdViewModel
                )
            }
            composable("expert_diagnostic") {
                com.elysium369.meet.ui.screens.ExpertDiagnosticScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("backup_settings") {
                BackupSettingsScreen(navController = navController)
            }
            composable("fleet_chat_list/{businessId}") { backStack ->
                val businessId = backStack.arguments?.getString("businessId") ?: ""
                val chatViewModel: FleetChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                FleetChatListScreen(
                    navController = navController,
                    viewModel = chatViewModel,
                    businessId = businessId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("fleet_chat_detail") { backStack ->
                val parentEntry = remember(backStack) {
                    navController.getBackStackEntry("fleet_chat_list/{businessId}")
                }
                val chatViewModel: FleetChatViewModel = androidx.hilt.navigation.compose.hiltViewModel(parentEntry)
                FleetChatDetailScreen(
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                    navController = navController
                )
            }
            composable("premium") {
                PremiumScreen(
                    onClose = { navController.popBackStack() }
                )
            }
            composable("health_score") {
                HealthScoreScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("component_locator") {
                ComponentLocatorScreen(navController = navController)
            }
            composable("adaptation") {
                AdaptationScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("live_link") {
                LiveLinkScreen(navController = navController, liveLinkServer = liveLinkServer)
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
                MaintenanceScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("custom_pid") {
                CustomPidEditorScreen(
                    customPids = customPids,
                    onAddCustomPid = { obdViewModel.addCustomPid(it) },
                    onSyncPids = { obdViewModel.syncCustomPidsFromCloud() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("pre_purchase") {
                PrePurchaseScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("hud") {
                HudScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("dvir") {
                DvirScreen(
                    navController = navController,
                    viewModel = obdViewModel
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
        containerColor = Color(0xFF070B14),
        contentColor = Color(0xFF00FFD4)
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, "Home") },
            label = { Text("Inicio", fontSize = 10.sp) },
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") { launchSingleTop = true; restoreState = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00FFD4),
                selectedTextColor = Color(0xFF00FFD4),
                unselectedIconColor = Color(0xFF3D4E63),
                unselectedTextColor = Color(0xFF3D4E63),
                indicatorColor = Color(0xFF00FFD4).copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Build, "Scanner") },
            label = { Text("Scanner", fontSize = 10.sp) },
            selected = currentRoute == "scanner",
            onClick = { navController.navigate("scanner") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00E5FF),
                selectedTextColor = Color(0xFF00E5FF),
                unselectedIconColor = Color(0xFF3D4E63),
                unselectedTextColor = Color(0xFF3D4E63),
                indicatorColor = Color(0xFF00E5FF).copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Warning, "DTCs") },
            label = { Text("DTCs", fontSize = 10.sp) },
            selected = currentRoute == "dtc",
            onClick = { navController.navigate("dtc") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFF1744),
                selectedTextColor = Color(0xFFFF1744),
                unselectedIconColor = Color(0xFF3D4E63),
                unselectedTextColor = Color(0xFF3D4E63),
                indicatorColor = Color(0xFFFF1744).copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, "Garage") },
            label = { Text("Garage", fontSize = 10.sp) },
            selected = currentRoute == "garage",
            onClick = { navController.navigate("garage") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFBB00FF),
                selectedTextColor = Color(0xFFBB00FF),
                unselectedIconColor = Color(0xFF3D4E63),
                unselectedTextColor = Color(0xFF3D4E63),
                indicatorColor = Color(0xFFBB00FF).copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Star, "PRO") },
            label = { Text("PRO", fontSize = 10.sp) },
            selected = currentRoute == "pro_hub",
            onClick = { navController.navigate("pro_hub") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFFF00AA),
                selectedTextColor = Color(0xFFFF00AA),
                unselectedIconColor = Color(0xFF3D4E63),
                unselectedTextColor = Color(0xFF3D4E63),
                indicatorColor = Color(0xFFFF00AA).copy(alpha = 0.08f)
            )
        )
    }
}
