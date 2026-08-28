package com.elysium369.meet

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.theme.MeetTheme
import android.os.Bundle
import android.os.Build
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.FleetChatViewModel
import com.elysium369.meet.ui.RepairNetworkViewModel
import com.elysium369.meet.ui.TheoryExamViewModel
import com.elysium369.meet.ui.components.gauges.GaugeStyleManager
import com.elysium369.meet.ui.screens.*
import com.elysium369.meet.ui.screens.chat.*
import com.elysium369.meet.ui.screens.humanity.CapabilityPassportScreen
import com.elysium369.meet.ui.screens.humanity.LearningHubScreen
import com.elysium369.meet.ui.screens.humanity.MissionDetailScreen
import com.elysium369.meet.ui.screens.humanity.MultimeterSimulationScreen
import com.elysium369.meet.ui.screens.marketos.FuelRewardsHub
import com.elysium369.meet.ui.screens.marketos.LegalVanguardHub
import com.elysium369.meet.ui.screens.marketos.PropertiesHub
import com.elysium369.meet.core.livelink.LiveLinkServer
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.identity.PrincipalAccessPolicy
import com.elysium369.meet.identity.PrincipalProvisioningStore
import com.elysium369.meet.observability.MeetTelemetry
import com.elysium369.meet.observability.TelemetryContext
import com.elysium369.meet.ui.components.AdapterSearchSheet
import com.elysium369.meet.ui.components.ConnectionStatusBar
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import com.elysium369.meet.ui.components.LocalAnimatedIconClock
import com.elysium369.meet.ui.components.LocalAnimatedIconStyle
import com.elysium369.meet.ui.components.rememberAnimatedIconClock
import com.elysium369.meet.ui.components.rememberAnimatedIconStyle
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Star
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth

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

    companion object {
        /** Volatile flag so ObdViewModel can check BT permission status before starting the FGS. */
        @Volatile
        var bluetoothPermissionsGranted: Boolean = false
            private set
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = permissions.entries
            .filter { it.key.contains("BLUETOOTH") }
            .any { it.value }
        bluetoothPermissionsGranted = btGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S

        if (!btGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.util.Log.w("MainActivity",
                "Bluetooth permissions DENIED — OBD scanning will be limited. " +
                "User must grant Bluetooth in Settings for full functionality.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pre-check if permissions are already granted (e.g. from previous session)
        bluetoothPermissionsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        checkPermissions()
        MeetColors.initialize(this)

        setContent {
            MeetTheme {
                MeetApp(viewModel)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Microphone permission for Voice Copilot
        permissions.add(android.Manifest.permission.RECORD_AUDIO)

        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun MeetApp(obdViewModel: ObdViewModel) {
    val context = LocalContext.current
    val authStatus by SupabaseModule.client.auth.sessionStatus.collectAsState()
    val currentUser = SupabaseModule.client.auth.currentUserOrNull()
    LaunchedEffect(Unit) {
        MeetTelemetry.configure(
            TelemetryContext(
                appVersion = BuildConfig.VERSION_NAME,
                buildSha = BuildConfig.MEET_BUILD_SHA,
                environment = if (BuildConfig.DEBUG) "debug" else "release",
            ),
        )
        MeetTelemetry.event("app.startup")
    }
    LaunchedEffect(currentUser?.id) {
        currentUser?.id?.let { PrincipalProvisioningStore.recordAuthenticated(context, it) }
    }
    val provisionedPrincipalId = PrincipalProvisioningStore.principalId(context)
    val accessDecision = PrincipalAccessPolicy.decide(
        session = when (authStatus) {
            is SessionStatus.Authenticated -> PrincipalAccessPolicy.SessionEvidence.AUTHENTICATED
            is SessionStatus.NotAuthenticated -> PrincipalAccessPolicy.SessionEvidence.NOT_AUTHENTICATED
            SessionStatus.LoadingFromStorage -> PrincipalAccessPolicy.SessionEvidence.LOADING
            SessionStatus.NetworkError -> PrincipalAccessPolicy.SessionEvidence.NETWORK_UNAVAILABLE
        },
        provisionedPrincipalId = provisionedPrincipalId,
    )

    if (accessDecision == PrincipalAccessPolicy.Decision.RESOLVING) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator(color = MeetColors.neonGreen)
        }
        return
    }

    if (accessDecision == PrincipalAccessPolicy.Decision.REQUIRE_AUTHENTICATION) {
        AuthScreen(
            onAuthSuccess = {
                SupabaseModule.client.auth.currentUserOrNull()?.id?.let {
                    PrincipalProvisioningStore.recordAuthenticated(context, it)
                }
                obdViewModel.syncSelectedUsageProfile()
            },
        )
        return
    }

    val navController = rememberNavController()
    val liveLinkServer = remember { LiveLinkServer.shared() }

    // Navigation collector for voice commands
    LaunchedEffect(Unit) {
        obdViewModel.navigationEvent.collect { route ->
            android.util.Log.d("MainActivity", "Voice navigation: navigating to $route")
            try {
                navController.navigate(route) {
                    popUpTo("home") { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to navigate to $route", e)
            }
        }
    }

    // Stop server when leaving composition
    DisposableEffect(Unit) {
        obdViewModel.attachLiveLinkServer(liveLinkServer)
        onDispose { 
            liveLinkServer.stop()
            obdViewModel.detachLiveLinkServer()
        }
    }
    
    val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
    val startDestination = "home"
    
    val trips by obdViewModel.trips.collectAsState()
    val customPids by obdViewModel.customPids.collectAsState()
    val isPremium by obdViewModel.isPremium.collectAsState()
    val animatedIconStyle by rememberAnimatedIconStyle(context)
    val animatedIconClock = rememberAnimatedIconClock(animatedIconStyle)

    CompositionLocalProvider(
        LocalAnimatedIconStyle provides animatedIconStyle,
        LocalAnimatedIconClock provides animatedIconClock
    ) {
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
                Box(modifier = Modifier.statusBarsPadding()) {
                    ConnectionStatusBar(viewModel = obdViewModel, showQos = true)
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val currentRoute = navController.currentBackStackEntryAsState().value
                ?.destination?.route
            val hideBgRoutes = listOf("onboarding", "auth", "connect", "premium")
            if (currentRoute !in hideBgRoutes && currentRoute != null) {
                HolographicBackgroundShared()
            }
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
                    onAuthSuccess = {
                        obdViewModel.syncSelectedUsageProfile()
                        navController.navigate("home") {
                            popUpTo("auth") { inclusive = true }
                        }
                    },
                )
            }
            composable("home") {
                HomeScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("legal_vanguard") {
                LegalVanguardHub(
                    onBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=legal") },
                )
            }
            composable("elysium_properties") {
                PropertiesHub(
                    onBack = { navController.popBackStack() },
                    onOpenLegal = { navController.navigate("legal_vanguard") },
                )
            }
            composable("fuel_rewards") {
                FuelRewardsHub(onBack = { navController.popBackStack() })
            }
            composable(
                route = "messages?serviceVertical={serviceVertical}&serviceReferenceId={serviceReferenceId}&serviceTitle={serviceTitle}",
                arguments = listOf(
                    navArgument("serviceVertical") { defaultValue = "" },
                    navArgument("serviceReferenceId") { defaultValue = "" },
                    navArgument("serviceTitle") { defaultValue = "" },
                ),
            ) { backStack ->
                MessagesScreen(
                    onBack = { navController.popBackStack() },
                    serviceVertical = backStack.arguments?.getString("serviceVertical")?.takeIf(String::isNotBlank),
                    serviceReferenceId = backStack.arguments?.getString("serviceReferenceId")?.takeIf(String::isNotBlank),
                    serviceTitle = backStack.arguments?.getString("serviceTitle")
                        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        ?.takeIf(String::isNotBlank),
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
            composable(
                route = "repair/{dtcCode}?findingId={findingId}",
                arguments = listOf(navArgument("findingId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) { backStack ->
                val dtcCode = backStack.arguments?.getString("dtcCode") ?: ""
                DtcRepairGuideScreen(
                    navController = navController,
                    dtcCode = dtcCode,
                    findingId = backStack.arguments?.getString("findingId"),
                    viewModel = obdViewModel
                )
            }
            composable("repair_verification/{findingId}") { backStack ->
                RepairVerificationWorkflowScreen(
                    navController = navController,
                    findingId = backStack.arguments?.getString("findingId").orEmpty(),
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
                    onExportPdf = { obdViewModel.exportTripToPdf(it) }
                )
            }
            composable("ai/{dtcCode}") { backStack ->
                val dtcCode = backStack.arguments?.getString("dtcCode") ?: ""
                AiDiagnosticScreen(
                    dtcCode = dtcCode,
                    onBack = { navController.popBackStack() },
                    viewModel = obdViewModel,
                    onNavigateToSettings = { navController.navigate("ai_settings") },
                    onRequestMechanic = { info ->
                        navController.navigate("mechanic_service?vehicleInfo=${java.net.URLEncoder.encode(info, "UTF-8")}")
                    },
                    onRequestPart = { info ->
                        navController.navigate("part_request?vehicleInfo=${java.net.URLEncoder.encode(info, "UTF-8")}")
                    },
                    onOpenComponent3d = { navController.navigate("component_locator") }
                )
            }
            composable(
                route = "ai?atlasPartId={atlasPartId}",
                arguments = listOf(
                    navArgument("atlasPartId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStack ->
                val screenContext = LocalContext.current
                val atlasPartId = backStack.arguments?.getString("atlasPartId")
                val atlasContext = remember(atlasPartId, screenContext) {
                    atlasPartId?.let { canonicalId ->
                        runCatching {
                            val canonicalPart = requireNotNull(
                                com.elysium369.meet.core.catalog
                                    .CanonicalVehiclePartRepository(screenContext)
                                    .find(canonicalId),
                            )
                            val element = canonicalPart.element
                            val section = canonicalPart.section
                            buildString {
                                appendLine("FUENTE CANÓNICA MEET · ${canonicalPart.atlasDisplayName}")
                                appendLine("Atlas: ${canonicalPart.atlasId}")
                                appendLine("ID: ${element.canonicalId}")
                                appendLine("Elemento: ${element.nameOriginal}")
                                appendLine("Sistema: ${section.title}")
                                appendLine("Conocimiento: ${section.knowledge}")
                                appendLine("Autoridad visual: ${element.visual.authority}")
                                appendLine("Vehículo de referencia: ${canonicalPart.vehicleLabel}")
                                appendLine(canonicalPart.geometryWarning)
                                append(
                                    "No afirmar compatibilidad exacta sin VIN/OEM/foto/" +
                                        "conector/medidas y confirmación física.",
                                )
                            }
                        }.getOrNull()
                    }
                }
                AiDiagnosticScreen(
                    dtcCode = "",
                    initialGroundedContext = atlasContext,
                    onBack = { navController.popBackStack() },
                    viewModel = obdViewModel,
                    onNavigateToSettings = { navController.navigate("ai_settings") },
                    onRequestMechanic = { info ->
                        navController.navigate("mechanic_service?vehicleInfo=${java.net.URLEncoder.encode(info, "UTF-8")}")
                    },
                    onRequestPart = { info ->
                        navController.navigate("part_request?vehicleInfo=${java.net.URLEncoder.encode(info, "UTF-8")}")
                    },
                    onOpenComponent3d = { navController.navigate("component_locator") }
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
            composable("advanced_diagnostics") {
                AdvancedDiagnosticsScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("service_resets") {
                ServiceResetsScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("reports") {
                ReportScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("inspection_session/{vehicleId}") { backStack ->
                val vehicleId = backStack.arguments?.getString("vehicleId").orEmpty()
                val vehicles by obdViewModel.vehicles.collectAsState()
                val vehicle = vehicles.find { it.id == vehicleId }
                val obdState by obdViewModel.connectionState.collectAsState()
                
                InspectionSessionScreen(
                    vehicleId = vehicleId,
                    vehicleLabel = vehicle?.let { "${it.year} ${it.make} ${it.model}" } ?: vehicleId,
                    vehicleVin = vehicle?.vin,
                    vehicleOdometerKm = null,
                    obdConnected = obdState == com.elysium369.meet.core.obd.ObdState.CONNECTED,
                    onClose = { navController.popBackStack() }
                )
            }
            composable("vehicle_history/{vehicleId}") { backStack ->
                val vehicleId = backStack.arguments?.getString("vehicleId").orEmpty()
                val vehicles by obdViewModel.vehicles.collectAsState()
                val vehicle = vehicles.find { it.id == vehicleId }
                
                VehicleHistoryScreen(
                    vehicleId = vehicleId,
                    vehicleLabel = vehicle?.let { "${it.year} ${it.make} ${it.model}" } ?: vehicleId,
                    onClose = { navController.popBackStack() },
                    onOpenReport = { reportId ->
                        navController.navigate("inspection_session/$vehicleId")
                    }
                )
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
                    navController = navController,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("vehicle_manuals") {
                com.elysium369.meet.ui.screens.VehicleManualsScreen(
                    viewModel = obdViewModel,
                    navController = navController
                )
            }
            composable("elysium_manuals") {
                com.elysium369.meet.ui.screens.ElysiumManualsScreen(navController)
            }
            composable("settings") {
                SettingsScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("ai_settings") {
                val context = LocalContext.current
                val keyStore = remember { com.elysium369.meet.ai.data.AiSecureKeyStoreImpl(context) }
                val registry = remember { com.elysium369.meet.ai.data.AiProviderRegistry(keyStore) }
                com.elysium369.meet.ai.ui.AiSettingsScreen(
                    onBack = { navController.popBackStack() },
                    registry = registry,
                    keyStore = keyStore
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
                    viewModel = obdViewModel,
                    onClose = { navController.popBackStack() }
                )
            }
            composable("health_score") {
                HealthScoreScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable(
                route = "component_locator?partId={partId}&findingId={findingId}",
                arguments = listOf(navArgument("partId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }, navArgument("findingId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStack ->
                ComponentLocatorScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    initialPartId = backStack.arguments?.getString("partId"),
                    initialFindingId = backStack.arguments?.getString("findingId"),
                )
            }
            composable(
                route = "parts_repairs?partId={partId}",
                arguments = listOf(navArgument("partId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStack ->
                PartsRepairsCatalogScreen(
                    navController = navController,
                    initialPartId = backStack.arguments?.getString("partId")
                )
            }
            composable("adaptation") {
                AdaptationScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("live_link") {
                LiveLinkScreen(
                    navController = navController,
                    liveLinkServer = liveLinkServer,
                    viewModel = obdViewModel
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
            composable("findings") {
                FindingsScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("holo_local_read") {
                com.elysium369.meet.ui.screens.HoloLocalReadScreen(
                    viewModel = obdViewModel,
                    navController = navController
                )
            }
            composable("dashcam") {
                DashcamScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("meet_perito") {
                MeetPeritoScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("meet_dna") {
                MeetDnaScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("elysium_ai") {
                val evairViewModel: com.elysium369.meet.ui.ElysiumAiViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                ElysiumAiScreen(
                    facade = evairViewModel.facade,
                    gateway = evairViewModel.gateway,
                    stateEngine = evairViewModel.stateEngine,
                    onBack = { navController.popBackStack() },
                    onNavigateToTerminal = { navController.navigate("terminal") },
                    onNavigateToLiveTelemetry = { navController.navigate("scanner") }
                )
            }
            composable("evair") {
                val evairViewModel: com.elysium369.meet.ui.ElysiumAiViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                ElysiumAiScreen(
                    facade = evairViewModel.facade,
                    gateway = evairViewModel.gateway,
                    stateEngine = evairViewModel.stateEngine,
                    onBack = { navController.popBackStack() },
                    onNavigateToTerminal = { navController.navigate("terminal") },
                    onNavigateToLiveTelemetry = { navController.navigate("scanner") }
                )
            }
            composable("vehicle_access") {
                com.elysium369.meet.ui.screens.vehicleaccess.VehicleAccessDashboardScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("repair_network") {
                val repairViewModel: RepairNetworkViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                RepairNetworkScreen(
                    navController = navController,
                    viewModel = repairViewModel,
                    obdViewModel = obdViewModel
                )
            }
            composable("dekra_concierge") {
                DekraConciergeScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenGarage = { navController.navigate("garage") },
                    onOpenTow = { navController.navigate("tow_truck_service") },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=inspection") },
                )
            }
            composable("theory_exam_preparation") {
                val theoryViewModel: TheoryExamViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                TheoryExamPreparationScreen(
                    viewModel = theoryViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("learning_hub") {
                LearningHubScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenDrivingTheory = { navController.navigate("theory_exam_preparation") },
                    onOpenMissionDetail = { missionId -> navController.navigate("mission_detail/$missionId") },
                    onOpenMultimeterSimulation = { navController.navigate("multimeter_simulation") },
                    onOpenCapabilityPassport = { navController.navigate("capability_passport") },
                )
            }
            composable(
                route = "mission_detail/{missionId}",
                arguments = listOf(navArgument("missionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val missionId = backStackEntry.arguments?.getString("missionId") ?: ""
                MissionDetailScreen(
                    missionId = missionId,
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSimulation = { navController.navigate("multimeter_simulation") }
                )
            }
            composable("multimeter_simulation") {
                MultimeterSimulationScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("capability_passport") {
                CapabilityPassportScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("universal_services") {
                UniversalServicesScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=universal") },
                )
            }
            composable("provider_registration") {
                ProviderRegistrationScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("platform_trust_center") {
                PlatformTrustCenterScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("repair_case_detail/{caseId}") { backStack ->
                val caseId = backStack.arguments?.getString("caseId") ?: ""
                val repairViewModel: RepairNetworkViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                LaunchedEffect(caseId) {
                    repairViewModel.selectCase(caseId)
                }
                RepairCaseDetailScreen(navController = navController, caseId = caseId, viewModel = repairViewModel)
            }
            composable("contribute_case") {
                val repairViewModel: RepairNetworkViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                ContributeCaseScreen(navController = navController, viewModel = repairViewModel)
            }
            composable("community_cases") {
                val repairViewModel: RepairNetworkViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                com.elysium369.meet.ui.screens.CommunityCasesScreen(navController = navController, viewModel = repairViewModel)
            }
            composable("marketplace") {
                MarketplaceScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable(
                route = "gauge_marketplace?publishGaugeId={publishGaugeId}",
                arguments = listOf(
                    navArgument("publishGaugeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val screenContext = LocalContext.current
                val styleManager = remember { GaugeStyleManager(screenContext) }
                GaugeMarketplaceScreen(
                    navController = navController,
                    gaugeStyleManager = styleManager,
                    initialPublishGaugeId = backStackEntry.arguments?.getString("publishGaugeId")
                )
            }
            composable("workshop_dashboard") {
                WorkshopDashboardScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable("technical_forum") {
                TechnicalForumScreen(navController = navController)
            }
            composable("pro_chemical_guide") {
                ProChemicalGuideScreen(navController = navController)
            }
            composable(
                route = "tow_truck_service?vehicleInfo={vehicleInfo}",
                arguments = listOf(
                    androidx.navigation.navArgument("vehicleInfo") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                        nullable = true
                    }
                )
            ) { backStack ->
                val vehicleInfo = backStack.arguments?.getString("vehicleInfo")?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                }?.takeIf { it.isNotBlank() }
                com.elysium369.meet.ui.screens.TowTruckServiceScreen(
                    viewModel = obdViewModel,
                    prefilledVehicleInfo = vehicleInfo,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=tow") },
                )
            }
            composable(
                route = "mechanic_service?vehicleInfo={vehicleInfo}",
                arguments = listOf(
                    androidx.navigation.navArgument("vehicleInfo") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                        nullable = true
                    }
                )
            ) { backStack ->
                val vehicleInfo = backStack.arguments?.getString("vehicleInfo")?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                }?.takeIf { it.isNotBlank() }
                com.elysium369.meet.ui.screens.MechanicServiceScreen(
                    viewModel = obdViewModel,
                    prefilledVehicleInfo = vehicleInfo,
                    onNavigateBack = { navController.popBackStack() },
                    onPostScanRequested = { vehicleId ->
                        navController.navigate("inspection_session/$vehicleId")
                    },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=repair") },
                )
            }
            composable(
                route = "part_request?vehicleInfo={vehicleInfo}&atlasPartId={atlasPartId}",
                arguments = listOf(
                    androidx.navigation.navArgument("vehicleInfo") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                    androidx.navigation.navArgument("atlasPartId") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                        nullable = true
                    }
                )
            ) { backStack ->
                val vehicleInfo = backStack.arguments?.getString("vehicleInfo")?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                }?.takeIf { it.isNotBlank() }
                com.elysium369.meet.ui.screens.PartRequestScreen(
                    viewModel = obdViewModel,
                    prefilledVehicleInfo = vehicleInfo,
                    prefilledAtlasPartId = backStack.arguments
                        ?.getString("atlasPartId")
                        ?.takeIf { it.isNotBlank() },
                    onNavigateBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=parts") },
                )
            }
            composable("ride_service") {
                com.elysium369.meet.ui.screens.RideServiceScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenDriverRegistration = {
                        navController.navigate("provider_registration")
                    },
                    onOpenMessages = { referenceId ->
                        navController.navigate(
                            referenceId?.let {
                                "messages?serviceVertical=ride&serviceReferenceId=$it&serviceTitle=Viaje%20Elysium"
                            } ?: "messages?serviceVertical=ride"
                        )
                    },
                )
            }
            composable("ai") {
                AiDiagnosticScreen(
                    dtcCode = "",
                    onBack = { navController.popBackStack() },
                    viewModel = obdViewModel,
                    onNavigateToSettings = { navController.navigate("ai_settings") },
                    onRequestMechanic = { info ->
                        navController.navigate("mechanic_service?vehicleInfo=${java.net.URLEncoder.encode(info, "UTF-8")}")
                    },
                    onRequestPart = { info ->
                        navController.navigate("part_request?vehicleInfo=${java.net.URLEncoder.encode(info, "UTF-8")}")
                    },
                    onOpenComponent3d = { navController.navigate("component_locator") }
                )
            }
            composable("dtcs") {
                DtcScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("vanguard_perito") {
                MeetPeritoScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("vanguard_dna") {
                MeetDnaScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("engine_3d") {
                ComponentLocatorScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("parts_store") {
                MarketplaceScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("mechanic_services") {
                val repairViewModel: RepairNetworkViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                RepairNetworkScreen(navController = navController, viewModel = repairViewModel, obdViewModel = obdViewModel)
            }
            composable("tow_truck") {
                TowTruckServiceScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=tow") },
                )
            }
            composable("trust_center") {
                PlatformTrustCenterScreen(viewModel = obdViewModel, onBack = { navController.popBackStack() })
            }
            composable("trip_log") {
                TripScreen(trips = trips, isPremium = isPremium, onExportPdf = { obdViewModel.exportTripToPdf(it) })
            }
            composable("live_stream") {
                LiveLinkScreen(navController = navController, liveLinkServer = liveLinkServer, viewModel = obdViewModel)
            }
            composable("protocol_learning") {
                AdaptationScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("adapter_diagnostics") {
                CloneTestScreen(onRunTest = { obdViewModel.runAdapterCloneTest() })
            }
            composable("ride_home") {
                com.elysium369.meet.ui.screens.RideServiceScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenDriverRegistration = {
                        navController.navigate("provider_registration")
                    },
                    onOpenMessages = { referenceId ->
                        navController.navigate(
                            referenceId?.let {
                                "messages?serviceVertical=ride&serviceReferenceId=$it&serviceTitle=Viaje%20Elysium"
                            } ?: "messages?serviceVertical=ride"
                        )
                    },
                )
            }
            composable("fleet") {
                val chatViewModel: FleetChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                FleetChatListScreen(navController = navController, viewModel = chatViewModel, businessId = "fleet_default", onBack = { navController.popBackStack() })
            }
            composable("support") {
                val vehicle by obdViewModel.selectedVehicle.collectAsState()
                val vehicleLabel = vehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo Genérico"
                SupportChatScreen(onBack = { navController.popBackStack() }, vehicleInfo = vehicleLabel)
            }
            composable("battery_health") {
                HealthScoreScreen(navController = navController, viewModel = obdViewModel)
            }
        }
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
        contentColor = MeetColors.neonGreen
    ) {
        NavigationBarItem(
            icon = { AnimatedNeonIcon(Icons.Default.Home, "Home") },
            label = { Text("Inicio", fontSize = 10.sp) },
            selected = currentRoute == "home",
            onClick = { navController.navigate("home") { launchSingleTop = true; restoreState = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeetColors.neonGreen,
                selectedTextColor = MeetColors.neonGreen,
                unselectedIconColor = MeetColors.textMuted,
                unselectedTextColor = MeetColors.textMuted,
                indicatorColor = MeetColors.neonGreen.copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { AnimatedNeonIcon(Icons.Default.Build, "Scanner") },
            label = { Text("Scanner", fontSize = 10.sp) },
            selected = currentRoute == "scanner",
            onClick = { navController.navigate("scanner") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeetColors.cyberCyan,
                selectedTextColor = MeetColors.cyberCyan,
                unselectedIconColor = MeetColors.textMuted,
                unselectedTextColor = MeetColors.textMuted,
                indicatorColor = MeetColors.cyberCyan.copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { AnimatedNeonIcon(Icons.Default.Warning, "DTCs") },
            label = { Text("DTCs", fontSize = 10.sp) },
            selected = currentRoute == "dtc",
            onClick = { navController.navigate("dtc") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeetColors.error,
                selectedTextColor = MeetColors.error,
                unselectedIconColor = MeetColors.textMuted,
                unselectedTextColor = MeetColors.textMuted,
                indicatorColor = MeetColors.error.copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { AnimatedNeonIcon(Icons.Default.List, "Garage") },
            label = { Text("Garage", fontSize = 10.sp) },
            selected = currentRoute == "garage",
            onClick = { navController.navigate("garage") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeetColors.electricBlue,
                selectedTextColor = MeetColors.electricBlue,
                unselectedIconColor = MeetColors.textMuted,
                unselectedTextColor = MeetColors.textMuted,
                indicatorColor = MeetColors.electricBlue.copy(alpha = 0.08f)
            )
        )
        NavigationBarItem(
            icon = { AnimatedNeonIcon(Icons.Default.Star, "PRO") },
            label = { Text("PRO", fontSize = 10.sp) },
            selected = currentRoute == "pro_hub",
            onClick = { navController.navigate("pro_hub") { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeetColors.hotMagenta,
                selectedTextColor = MeetColors.hotMagenta,
                unselectedIconColor = MeetColors.textMuted,
                unselectedTextColor = MeetColors.textMuted,
                indicatorColor = MeetColors.hotMagenta.copy(alpha = 0.08f)
            )
        )
    }
}
