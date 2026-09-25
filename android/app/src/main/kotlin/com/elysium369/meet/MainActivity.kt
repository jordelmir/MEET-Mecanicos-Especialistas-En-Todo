package com.elysium369.meet

import com.elysium369.meet.ui.components.AnimatedNeonIcon
import com.elysium369.meet.ui.components.ElysiumLivingCompanionOverlay

import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.theme.MeetTheme
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.os.Bundle
import android.os.Build
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.zIndex
import com.elysium369.meet.ui.screens.ride.RideLiveCallOverlay
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.elysium369.meet.education.presentation.ElysiumLearningScreen
import com.elysium369.meet.education.presentation.ElysiumLearningViewModel
import com.elysium369.meet.ui.screens.marketos.FuelRewardsHub
import com.elysium369.meet.ui.screens.marketos.LegalVanguardHub
import com.elysium369.meet.ui.screens.marketos.PropertiesHub
import com.elysium369.meet.ui.navigation.backOrHome
import com.elysium369.meet.ui.navigation.navigateTopLevel
import com.elysium369.meet.ui.navigation.safeNavigate
import com.elysium369.meet.core.livelink.LiveLinkServer
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.identity.PrincipalAccessPolicy
import com.elysium369.meet.identity.MainGraphRetentionPolicy
import com.elysium369.meet.ui.navigation.MeetDestinations
import com.elysium369.meet.identity.PrincipalProvisioningStore
import com.elysium369.meet.observability.MeetTelemetry
import com.elysium369.meet.observability.AuthObservability
import com.elysium369.meet.observability.TelemetryContext
import com.elysium369.meet.ui.components.AdapterSearchSheet
import com.elysium369.meet.ui.components.ConnectionStatusBar
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import com.elysium369.meet.ui.components.LocalAnimatedIconClock
import com.elysium369.meet.ui.components.LocalAnimatedIconStyle
import com.elysium369.meet.ui.components.rememberAnimatedIconClock
import com.elysium369.meet.ui.components.rememberAnimatedIconStyle
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
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
import io.github.jan.supabase.gotrue.handleDeeplinks

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

@Composable
private fun PlatformOwnerRouteGuard(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val access by viewModel.platformOwnerAccess.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshPlatformOwnerAccess() }
    when (access) {
        com.elysium369.meet.ride.domain.PlatformOwnerAccess.GRANTED -> content()
        com.elysium369.meet.ride.domain.PlatformOwnerAccess.UNKNOWN -> Box(
            Modifier.fillMaxSize().background(MeetColors.backgroundDeep),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = MeetColors.cyberCyan) }
        else -> Scaffold(containerColor = MeetColors.backgroundDeep) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("ACCESO RESTRINGIDO", color = Color.White, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esta zona requiere la autorización exclusiva de la cuenta propietaria confirmada.",
                    color = MeetColors.textSecondary,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack) { Text("REGRESAR") }
            }
        }
    }
}


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ObdViewModel by viewModels()
    private var passwordRecoveryRequested by mutableStateOf(false)
    private var passwordRecoverySessionImported by mutableStateOf(false)
    private var pendingDeepLinkRoute by mutableStateOf<String?>(null)

    companion object {
        /** Volatile flag so ObdViewModel can check BT permission status before starting the FGS. */
        @Volatile
        var bluetoothPermissionsGranted: Boolean = false
            private set
    }

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
            ).all { permission ->
                permissions[permission] == true ||
                    checkSelfPermission(permission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        } else {
            true
        }
        bluetoothPermissionsGranted = btGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S

        if (!btGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.util.Log.w("MainActivity",
                "Bluetooth permissions DENIED — OBD scanning will be limited. " +
                "User must grant Bluetooth in Settings for full functionality.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        passwordRecoveryRequested = handleAuthenticationIntent(intent)
        pendingDeepLinkRoute = resolveDeepLinkRoute(intent)

        // Pre-check if permissions are already granted (e.g. from previous session)
        bluetoothPermissionsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        checkPermissions()
        MeetColors.initialize(this)

        setContent {
            MeetTheme {
                MeetApp(
                    obdViewModel = viewModel,
                    passwordRecoveryRequested = passwordRecoveryRequested,
                    passwordRecoverySessionReady = passwordRecoverySessionImported,
                    onPasswordRecoveryHandled = {
                        passwordRecoveryRequested = false
                        passwordRecoverySessionImported = false
                    },
                    initialDeepLinkRoute = pendingDeepLinkRoute,
                    onDeepLinkHandled = {
                        pendingDeepLinkRoute = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        passwordRecoveryRequested = handleAuthenticationIntent(intent)
        pendingDeepLinkRoute = resolveDeepLinkRoute(intent)
    }

    private fun resolveDeepLinkRoute(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val path = uri.path.orEmpty()
        val host = uri.host.orEmpty()
        val scheme = uri.scheme.orEmpty()

        if (scheme == "meet") {
            return when (host) {
                "verify" -> MeetDestinations.CAMPAIGNS
                "ride" -> MeetDestinations.RIDE_HOME
                "diagnostic" -> MeetDestinations.DTCS
                "invite" -> MeetDestinations.HOME
                else -> null
            }
        }
        if (scheme == "https" && (host == "elysium-vanguard.app" || host == "meet.elysium-vanguard.app")) {
            return when {
                path.startsWith("/verify") -> MeetDestinations.CAMPAIGNS
                path.startsWith("/ride") -> MeetDestinations.RIDE_HOME
                path.startsWith("/diagnostic") -> MeetDestinations.DTCS
                path.startsWith("/invite") -> MeetDestinations.HOME
                else -> null
            }
        }
        return null
    }

    private fun handleAuthenticationIntent(intent: Intent?): Boolean {
        val data = intent?.data
        val isRecoveryDestination = data?.scheme == AuthRedirectPolicy.SCHEME &&
            data.host == AuthRedirectPolicy.HOST &&
            data.path == AuthRedirectPolicy.RECOVERY_PATH
        val accepted = AuthRedirectPolicy.isPasswordRecoveryLink(intent?.dataString)
        AuthObservability.recoveryLink(received = isRecoveryDestination, accepted = accepted)
        if (accepted && intent != null) {
            passwordRecoverySessionImported = false
            SupabaseModule.client.handleDeeplinks(intent) {
                passwordRecoverySessionImported = true
            }
        } else {
            passwordRecoverySessionImported = false
        }
        return accepted
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

        // NOTE: RECORD_AUDIO is NOT requested at startup.
        // It is requested on-demand when the user activates Voice Copilot
        // or attaches audio evidence in Safety reports.

        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun MeetApp(
    obdViewModel: ObdViewModel,
    passwordRecoveryRequested: Boolean = false,
    passwordRecoverySessionReady: Boolean = false,
    onPasswordRecoveryHandled: () -> Unit = {},
    initialDeepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
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
        currentUser?.id?.let {
            PrincipalProvisioningStore.recordAuthenticated(context, it)
            obdViewModel.syncPendingTrustApplications()
        }
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
    // Keep the controller and runtime attachment above transient auth states.
    // Supabase briefly emits LoadingFromStorage when the app resumes; creating
    // these below that gate used to dispose the graph, reset screen state and
    // stop the shared runtime even though the user had not signed out.
    val navController = rememberNavController()
    val liveLinkServer = remember { LiveLinkServer.shared() }
    var mainGraphEstablished by rememberSaveable { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        navBackStackEntry?.destination?.route?.let { currentRoute ->
            com.elysium369.meet.automation.AiAutomationBridge.currentRoute = currentRoute
        }
    }
    LaunchedEffect(navController) {
        com.elysium369.meet.automation.AiAutomationBridge.navEvents.collect { route ->
            android.util.Log.i("AiAutomation", "Navigating to route from AI bus: $route")
            navController.safeNavigate(route)
        }
    }
    LaunchedEffect(initialDeepLinkRoute) {
        initialDeepLinkRoute?.let { targetRoute ->
            navController.safeNavigate(targetRoute)
            onDeepLinkHandled()
        }
    }

    val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
    val onboardingCompleted = sharedPrefs.getBoolean("onboarding_completed", false)
    val hasProfile = !sharedPrefs.getString("user_profile", null).isNullOrBlank()

    LaunchedEffect(accessDecision) {
        mainGraphEstablished = MainGraphRetentionPolicy.nextEstablished(
            decision = accessDecision,
            graphEstablished = mainGraphEstablished,
        )
    }

    if (MainGraphRetentionPolicy.shouldRender(accessDecision, mainGraphEstablished)) {
        DisposableEffect(obdViewModel, liveLinkServer) {
            obdViewModel.attachLiveLinkServer(liveLinkServer)
            onDispose {
                liveLinkServer.stop()
                obdViewModel.detachLiveLinkServer()
            }
        }
    }

    if (passwordRecoveryRequested) {
        AuthScreen(
            initialMode = AuthFlowMode.PASSWORD_UPDATE,
            recoverySessionReady = passwordRecoverySessionReady &&
                authStatus is SessionStatus.Authenticated && currentUser != null,
            onRecoveryComplete = onPasswordRecoveryHandled,
            onAuthSuccess = {
                SupabaseModule.client.auth.currentUserOrNull()?.id?.let {
                    PrincipalProvisioningStore.recordAuthenticated(context, it)
                }
                obdViewModel.syncSelectedUsageProfile()
                obdViewModel.syncPendingTrustApplications()
                val completed = sharedPrefs.getBoolean("onboarding_completed", false)
                val hasProfile = !sharedPrefs.getString("user_profile", null).isNullOrBlank()
                if (!completed || !hasProfile) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
        )
        return
    }

    if (!MainGraphRetentionPolicy.shouldRender(accessDecision, mainGraphEstablished) &&
        accessDecision == PrincipalAccessPolicy.Decision.RESOLVING
    ) {
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
                obdViewModel.syncPendingTrustApplications()
                val completed = sharedPrefs.getBoolean("onboarding_completed", false)
                val hasProfile = !sharedPrefs.getString("user_profile", null).isNullOrBlank()
                if (!completed || !hasProfile) {
                    navController.navigate("onboarding") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
        )
        return
    }

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

    val startDestination = when {
        !onboardingCompleted -> "onboarding"
        !hasProfile -> "onboarding"
        else -> "home"
    }
    
    val trips by obdViewModel.trips.collectAsState()
    val customPids by obdViewModel.customPids.collectAsState()
    val isPremium by obdViewModel.isPremium.collectAsState()
    val animatedIconStyle by rememberAnimatedIconStyle(context)
    val animatedIconClock = rememberAnimatedIconClock(animatedIconStyle)
    val activeRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    CompositionLocalProvider(
        LocalAnimatedIconStyle provides animatedIconStyle,
        LocalAnimatedIconClock provides animatedIconClock
    ) {
        Scaffold(
        containerColor = Color(0xFF060612),
        bottomBar = {
            // Solo mostrar BottomNav si NO estamos en onboarding/auth/connect/safety/*
            val hideNavRoutes = listOf("onboarding", "auth", "connect", "premium", "ride_service", "ride_active_tracking", "ride_schedule", "ride_driver_registration")
            val isSafetyRoute = activeRoute?.startsWith("safety") == true
            if (activeRoute !in hideNavRoutes && !isSafetyRoute && activeRoute != null) {
                MeetBottomNavigation(navController)
            }
        },
        topBar = {
            val hideBarRoutes = listOf("onboarding", "auth", "connect", "premium", "ride_service", "ride_active_tracking", "ride_schedule", "ride_driver_registration")
            val isSafetyBar = activeRoute?.startsWith("safety") == true
            if (activeRoute !in hideBarRoutes && !isSafetyBar && activeRoute != null) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    ConnectionStatusBar(viewModel = obdViewModel, showQos = true)
                }
            }
        }
    ) { paddingValues ->
        // paddingValues accounts for the top status bar and bottom navigation bar.
        // Apply it once to the Box so all children stay within the safe area.
        // Do NOT re-apply on NavHost — that double-squeezes content and breaks layout.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val hideBgRoutes = listOf("onboarding", "auth", "connect", "premium", "ride_service", "ride_active_tracking", "ride_schedule", "ride_driver_registration")
            if (activeRoute !in hideBgRoutes && activeRoute != null) {
                HolographicBackgroundShared()
            }
            val towRepository = obdViewModel.towCommandRepository
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it / 6 }
                },
                exitTransition = {
                    fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 6 }
                },
                popEnterTransition = {
                    fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it / 6 }
                },
                popExitTransition = {
                    fadeOut(tween(250, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 6 }
                },
            ) {
            composable("onboarding") {
                OnboardingScreen(
                    onFinish = { 
                        sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("auth") {
                AuthScreen(
                    onAuthSuccess = {
                        SupabaseModule.client.auth.currentUserOrNull()?.id?.let {
                            PrincipalProvisioningStore.recordAuthenticated(context, it)
                        }
                        obdViewModel.syncSelectedUsageProfile()
                        val completed = sharedPrefs.getBoolean("onboarding_completed", false)
                        val hasProfile = !sharedPrefs.getString("user_profile", null).isNullOrBlank()
                        if (!completed || !hasProfile) {
                            navController.navigate("onboarding") {
                                popUpTo(0) { inclusive = true }
                            }
                        } else {
                            navController.navigate("home") {
                                popUpTo("auth") { inclusive = true }
                            }
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
                    onBack = { navController.backOrHome() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=legal") },
                )
            }
            composable("elysium_properties") {
                PropertiesHub(
                    onBack = { navController.backOrHome() },
                    onOpenLegal = { navController.navigate("legal_vanguard") },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=property") },
                )
            }
            composable("fuel_rewards") {
                FuelRewardsHub(
                    onBack = { navController.backOrHome() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=fuel") },
                )
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
                    onBack = { navController.backOrHome() },
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
                        onNavigateBack = { navController.backOrHome() }
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
                    onBack = { navController.backOrHome() },
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
                                appendLine("FUENTE CANÓNICA Elysium · ${canonicalPart.atlasDisplayName}")
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
                    onBack = { navController.backOrHome() },
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
                    onBack = { navController.backOrHome() },
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
                    onClose = { navController.backOrHome() }
                )
            }
            composable("vehicle_history/{vehicleId}") { backStack ->
                val vehicleId = backStack.arguments?.getString("vehicleId").orEmpty()
                val vehicles by obdViewModel.vehicles.collectAsState()
                val vehicle = vehicles.find { it.id == vehicleId }
                
                VehicleHistoryScreen(
                    vehicleId = vehicleId,
                    vehicleLabel = vehicle?.let { "${it.year} ${it.make} ${it.model}" } ?: vehicleId,
                    onClose = { navController.backOrHome() },
                    onOpenReport = { reportId ->
                        navController.navigate("inspection_session/$vehicleId")
                    }
                )
            }
            composable("oscilloscope") {
                OscilloscopeScreen(
                    onNavigateBack = { navController.backOrHome() },
                    viewModel = obdViewModel
                )
            }
            composable("expert_diagnostic") {
                com.elysium369.meet.ui.screens.ExpertDiagnosticScreen(
                    viewModel = obdViewModel,
                    navController = navController,
                    onNavigateBack = { navController.backOrHome() }
                )
            }
            composable("vehicle_manuals") {
                com.elysium369.meet.ui.screens.VehicleManualsScreen(
                    viewModel = obdViewModel,
                    navController = navController
                )
            }
            composable("elysium_manuals") {
                com.elysium369.meet.ui.screens.MeetManualsScreen(navController)
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
                    onBack = { navController.backOrHome() },
                    registry = registry,
                    keyStore = keyStore
                )
            }
            composable("backup_settings") {
                BackupSettingsScreen(navController = navController, viewModel = obdViewModel)
            }
            composable("fleet_chat_list/{businessId}") { backStack ->
                val businessId = backStack.arguments?.getString("businessId") ?: ""
                val chatViewModel: FleetChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                FleetChatListScreen(
                    navController = navController,
                    viewModel = chatViewModel,
                    businessId = businessId,
                    onBack = { navController.backOrHome() }
                )
            }
            composable("fleet_chat_detail") { backStack ->
                val parentEntry = remember(backStack) {
                    navController.getBackStackEntry("fleet_chat_list/{businessId}")
                }
                val chatViewModel: FleetChatViewModel = androidx.hilt.navigation.compose.hiltViewModel(parentEntry)
                FleetChatDetailScreen(
                    viewModel = chatViewModel,
                    onBack = { navController.backOrHome() },
                    navController = navController
                )
            }
            composable("premium") {
                PremiumScreen(
                    viewModel = obdViewModel,
                    onClose = { navController.backOrHome() }
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
                    onDismiss = { navController.backOrHome() },
                    onConnect = { name, mac -> 
                        obdViewModel.connect(mac)
                        navController.backOrHome()
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
                    onBack = { navController.backOrHome() }
                )
            }
            composable("custom_pid") {
                CustomPidEditorScreen(
                    customPids = customPids,
                    onAddCustomPid = { obdViewModel.addCustomPid(it) },
                    onSyncPids = { obdViewModel.syncCustomPidsFromCloud() },
                    onBack = { navController.backOrHome() }
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
                MeetAiScreen(
                    facade = evairViewModel.facade,
                    gateway = evairViewModel.gateway,
                    stateEngine = evairViewModel.stateEngine,
                    onBack = { navController.backOrHome() },
                    onNavigateToTerminal = { navController.navigate("terminal") },
                    onNavigateToLiveTelemetry = { navController.navigate("scanner") }
                )
            }
            composable("evair") {
                val evairViewModel: com.elysium369.meet.ui.ElysiumAiViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                MeetAiScreen(
                    facade = evairViewModel.facade,
                    gateway = evairViewModel.gateway,
                    stateEngine = evairViewModel.stateEngine,
                    onBack = { navController.backOrHome() },
                    onNavigateToTerminal = { navController.navigate("terminal") },
                    onNavigateToLiveTelemetry = { navController.navigate("scanner") }
                )
            }
            composable("agent_store") {
                val agentStoreViewModel: com.elysium369.meet.core.agentstore.presentation.AgentStoreViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                com.elysium369.meet.core.agentstore.ui.AgentStoreScreen(
                    navController = navController,
                    viewModel = agentStoreViewModel
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
                    onBack = { navController.backOrHome() },
                    onOpenGarage = { navController.navigate("garage") },
                    onOpenTow = { navController.navigate("tow_truck_service") },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=inspection") },
                )
            }
            composable("theory_exam_preparation") {
                val theoryViewModel: TheoryExamViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                TheoryExamPreparationScreen(
                    viewModel = theoryViewModel,
                    onBack = { navController.backOrHome() },
                )
            }
            composable("learning_hub") {
                LearningHubScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.backOrHome() },
                    onOpenDrivingTheory = { navController.navigate("theory_exam_preparation") },
                    onOpenMissionDetail = { missionId -> navController.navigate("mission_detail/$missionId") },
                    onOpenMultimeterSimulation = { navController.navigate("multimeter_simulation") },
                    onOpenCapabilityPassport = { navController.navigate("capability_passport") },
                    onOpenElysiumLearningOs = { navController.navigate("elysium_learning_os") },
                )
            }
            composable("elysium_learning_os") {
                val elysiumLearningViewModel: ElysiumLearningViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                ElysiumLearningScreen(
                    viewModel = elysiumLearningViewModel,
                    onBack = { navController.backOrHome() },
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
                    onBack = { navController.backOrHome() },
                    onOpenSimulation = { navController.navigate("multimeter_simulation") }
                )
            }
            composable("multimeter_simulation") {
                MultimeterSimulationScreen(
                    onBack = { navController.backOrHome() }
                )
            }
            composable("capability_passport") {
                CapabilityPassportScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.backOrHome() }
                )
            }
            composable("universal_services") {
                UniversalServicesScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.backOrHome() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=universal") },
                    onNavigateToActiveServices = { navController.safeNavigate(MeetDestinations.SERVICES_ACTIVE) },
                    onNavigateToHistory = { navController.safeNavigate(MeetDestinations.SERVICES_COMPLETED) },
                    onNavigateToProviderConfig = { navController.safeNavigate(MeetDestinations.PROVIDER_SERVICES_CONFIG) },
                )
            }
            composable("elysium_services") {
                com.elysium369.meet.ui.screens.services.ElysiumServicesMarketplaceScreen(
                    navController = navController,
                    viewModel = obdViewModel
                )
            }
            composable(MeetDestinations.SERVICES_ACTIVE) {
                com.elysium369.meet.ui.screens.services.ActiveAndCompletedServicesHubScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    initialTab = 0
                )
            }
            composable(MeetDestinations.SERVICES_COMPLETED) {
                com.elysium369.meet.ui.screens.services.ActiveAndCompletedServicesHubScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    initialTab = 1
                )
            }
            composable(
                route = "services_active?tab={tab}",
                arguments = listOf(
                    androidx.navigation.navArgument("tab") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = "active"
                    }
                )
            ) { backStackEntry ->
                val tabParam = backStackEntry.arguments?.getString("tab") ?: "active"
                val initialTab = if (tabParam == "completed") 1 else 0
                com.elysium369.meet.ui.screens.services.ActiveAndCompletedServicesHubScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    initialTab = initialTab
                )
            }
            composable(MeetDestinations.PROVIDER_SERVICES_CONFIG) {
                com.elysium369.meet.ui.screens.provider.ProviderServiceCatalogConfigScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("universal_activity/{serviceId}") { backStackEntry ->
                val serviceId = backStackEntry.arguments?.getString("serviceId").orEmpty()
                val service = com.elysium369.meet.core.services.UniversalServiceCatalog.getById(serviceId)
                    ?: com.elysium369.meet.core.services.UniversalServiceCatalog.definitions.first { it.id == "hardware_materials" }
                com.elysium369.meet.ui.screens.universal.UniversalActivityWorkflowScreen(
                    service = service,
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.backOrHome() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=universal") },
                )
            }
            composable("provider_registration") {
                ProviderRegistrationScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.backOrHome() }
                )
            }
            composable("platform_trust_center") {
                PlatformOwnerRouteGuard(obdViewModel, { navController.backOrHome() }) {
                    PlatformTrustCenterScreen(
                        viewModel = obdViewModel,
                        onBack = { navController.backOrHome() },
                        onNavigateToCommandCenter = { navController.navigate("meet_command_center") },
                    )
                }
            }
            composable("meet_command_center") {
                PlatformOwnerRouteGuard(obdViewModel, { navController.backOrHome() }) {
                    com.elysium369.meet.ui.screens.intelligence.MeetExecutiveCommandCenterScreen(
                        onNavigateBack = { navController.backOrHome() },
                        onNavigateToTrustCenter = { _ -> navController.navigate("platform_trust_center") },
                    )
                }
            }
            composable("driver_command_center") {
                com.elysium369.meet.ui.screens.intelligence.DriverCommandCenterScreen(
                    driverId = obdViewModel.currentUserId ?: "demo-driver",
                    onNavigateBack = { navController.backOrHome() },
                    onNavigateToTrustCenter = { _ -> navController.navigate("platform_trust_center") },
                )
            }
            composable("fleet_command_center") {
                com.elysium369.meet.ui.screens.intelligence.FleetCommandCenterScreen(
                    fleetId = "fleet-vanguard-01",
                    onNavigateBack = { navController.backOrHome() },
                    onNavigateToTrustCenter = { _ -> navController.navigate("platform_trust_center") },
                )
            }
            composable("trust_center_hub") {
                PlatformOwnerRouteGuard(obdViewModel, { navController.backOrHome() }) {
                    com.elysium369.meet.ui.screens.trust.TrustCenterHubScreen(
                        principalId = obdViewModel.currentUserId.orEmpty(),
                        organizationId = null,
                        onNavigateBack = { navController.backOrHome() },
                        onNavigateToCommandCenter = { navController.navigate("meet_command_center") },
                    )
                }
            }
            composable("passenger_activity") {
                com.elysium369.meet.fulfillment.ui.UnifiedActivityScreen(onBack = { navController.backOrHome() })
            }
            composable("mechanic_business") {
                com.elysium369.meet.ui.screens.intelligence.MechanicBusinessScreen(
                    mechanicId = obdViewModel.currentUserId ?: "demo-mechanic",
                    onNavigateBack = { navController.backOrHome() },
                    onNavigateToTrustCenter = { navController.navigate("trust_center_hub") },
                )
            }
            composable("workshop_command_center") {
                com.elysium369.meet.ui.screens.intelligence.WorkshopCommandCenterScreen(
                    workshopOrgId = "workshop-vanguard-01",
                    onNavigateBack = { navController.backOrHome() },
                    onNavigateToTrustCenter = { _ -> navController.navigate("trust_center_hub") },
                )
            }
            composable("tow_command_center") {
                com.elysium369.meet.ui.screens.intelligence.TowCommandCenterScreen(
                    operatorOrOrgId = obdViewModel.currentUserId ?: "demo-tow-operator",
                    isOrganization = false,
                    onNavigateBack = { navController.backOrHome() },
                    onNavigateToTrustCenter = { navController.navigate("trust_center_hub") },
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
                    onNavigateBack = { navController.backOrHome() },
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
                    onNavigateBack = { navController.backOrHome() },
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
                    onNavigateBack = { navController.backOrHome() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=parts") },
                )
            }
            composable(MeetDestinations.RIDE_HOME) {
                com.elysium369.meet.ui.screens.RideServiceScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.backOrHome() },
                    onOpenDriverRegistration = {
                        navController.navigate(MeetDestinations.RIDE_DRIVER_REGISTRATION)
                    },
                    onOpenMessages = { referenceId ->
                        navController.navigate(
                            referenceId?.let {
                                "messages?serviceVertical=ride&serviceReferenceId=$it&serviceTitle=Viaje%20Elysium"
                            } ?: "messages?serviceVertical=ride"
                        )
                    },
                    onNavigateToSchedule = {
                        navController.navigate(MeetDestinations.RIDE_SCHEDULE)
                    },
                    onNavigateToRideCenter = {
                        navController.navigate(MeetDestinations.RIDE_CENTER)
                    },
                )
            }
            composable(MeetDestinations.RIDE_CENTER) {
                com.elysium369.meet.ui.screens.ride.RideCenterScreen(
                    viewModel = obdViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("ai") {
                AiDiagnosticScreen(
                    dtcCode = "",
                    onBack = { navController.backOrHome() },
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
                    onNavigateBack = { navController.backOrHome() },
                    onOpenMessages = { navController.navigate("messages?serviceVertical=tow") },
                )
            }
            composable("trust_center") {
                PlatformOwnerRouteGuard(obdViewModel, { navController.backOrHome() }) {
                    PlatformTrustCenterScreen(viewModel = obdViewModel, onBack = { navController.backOrHome() })
                }
            }
            composable(MeetDestinations.RIDE_DRIVER_REGISTRATION) {
                com.elysium369.meet.ui.screens.ProviderRegistrationScreen(
                    viewModel = obdViewModel,
                    onNavigateBack = { navController.backOrHome() },
                    openDriverOnStart = true,
                )
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
            composable(MeetDestinations.RIDE_PASSENGER_EXPERIMENT) {
                com.elysium369.meet.ui.screens.ride.PassengerRideRequestScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    onBack = { navController.backOrHome() },
                    onStartActiveRide = {
                        navController.navigate(MeetDestinations.RIDE_ACTIVE_TRACKING)
                    }
                )
            }
            composable(MeetDestinations.RIDE_DRIVER_EXPERIMENT) {
                com.elysium369.meet.ui.screens.ride.DriverAppScreen(
                    navController = navController,
                    viewModel = obdViewModel,
                    onBack = { navController.backOrHome() }
                )
            }

            composable(MeetDestinations.RIDE_ACTIVE_TRACKING) {
                val context = LocalContext.current
                val currentGps by obdViewModel.currentGpsLocation.collectAsState()
                LaunchedEffect(Unit) {
                    obdViewModel.detectCurrentLocation(context)
                }
                val activeRideReq by obdViewModel.activeRideRequest.collectAsState()
                val isDriverMode by obdViewModel.rideDriverMode.collectAsState()
                var rideNotice by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    obdViewModel.rideVerificationNotice.collect { rideNotice = it }
                }
                LaunchedEffect(activeRideReq?.status, activeRideReq?.serverState) {
                    val req = activeRideReq
                    if (req != null && (req.status == "COMPLETED" || req.serverState == "COMPLETED")) {
                        obdViewModel.promptRideRating(req)
                        navController.navigate(com.elysium369.meet.ui.navigation.MeetDestinations.RIDE_HOME) {
                            popUpTo(MeetDestinations.RIDE_ACTIVE_TRACKING) { inclusive = true }
                        }
                    }
                }
                val activeRide = activeRideReq?.let { req ->
                    val now = System.currentTimeMillis()
                    val parsedState = runCatching {
                        com.elysium369.meet.ride.domain.RideState.valueOf(
                            when (req.serverState) {
                                "DRIVER_ARRIVED" -> "ARRIVED"
                                else -> req.serverState ?: "UNKNOWN"
                            }
                        )
                    }.getOrNull() ?: com.elysium369.meet.ride.domain.RideState.UNKNOWN

                    val etaMin = when {
                        parsedState == com.elysium369.meet.ride.domain.RideState.ARRIVED -> 0
                        parsedState in listOf(
                            com.elysium369.meet.ride.domain.RideState.PASSENGER_ONBOARD,
                            com.elysium369.meet.ride.domain.RideState.IN_PROGRESS
                        ) -> req.estimatedDurationMin.takeIf { it > 0 } ?: 10
                        else -> req.estimatedDurationMin.takeIf { it > 0 } ?: 5
                    }

                    val matchedDriver = req.assignedDriverId?.let { driverId ->
                        com.elysium369.meet.ui.screens.ride.MatchedDriver(
                            driverId = driverId,
                            name = req.assignedDriverName ?: "Conductor Asignado",
                            rating = req.driverRating,
                            totalTrips = null,
                            vehicle = req.assignedDriverVehicle,
                            plate = null,
                            phone = req.assignedDriverPhone,
                            etaMinutes = etaMin,
                            distanceMeters = (req.estimatedDistanceKm * 1000.0).toInt()
                        )
                    }

                    val isDriverRole = isDriverMode || (req.assignedDriverId != null && req.assignedDriverId == obdViewModel.currentUserId)

                    val driverLoc = when {
                        parsedState == com.elysium369.meet.ride.domain.RideState.ARRIVED -> {
                            com.elysium369.meet.ui.screens.ride.RideLocationPoint(
                                latitude = req.pickupLatitude,
                                longitude = req.pickupLongitude,
                                accuracy = req.pickupAccuracy.takeIf { it in 1f..100f } ?: 5f,
                                timestamp = now,
                                receivedAt = now,
                                sequenceId = 1L,
                                source = "ARRIVED_CONFIRMED"
                            )
                        }
                        currentGps != null && isDriverRole -> {
                            com.elysium369.meet.ui.screens.ride.RideLocationPoint(
                                latitude = currentGps!!.latitude,
                                longitude = currentGps!!.longitude,
                                accuracy = currentGps!!.accuracy.coerceIn(1f, 100f),
                                timestamp = now,
                                receivedAt = now,
                                sequenceId = 1L,
                                source = "DRIVER_DEVICE_GPS"
                            )
                        }
                        currentGps != null -> {
                            com.elysium369.meet.ui.screens.ride.RideLocationPoint(
                                latitude = currentGps!!.latitude,
                                longitude = currentGps!!.longitude,
                                accuracy = currentGps!!.accuracy.coerceIn(1f, 100f),
                                timestamp = now,
                                receivedAt = now,
                                sequenceId = 1L,
                                source = "GPS_TRACKING"
                            )
                        }
                        req.pickupLatitude != 0.0 -> {
                            com.elysium369.meet.ui.screens.ride.RideLocationPoint(
                                latitude = req.pickupLatitude,
                                longitude = req.pickupLongitude,
                                accuracy = 10f,
                                timestamp = now,
                                receivedAt = now,
                                sequenceId = 1L,
                                source = "PICKUP_ORIGIN"
                            )
                        }
                        else -> null
                    }

                    val passengerLoc = currentGps?.let { gps ->
                        com.elysium369.meet.ui.screens.ride.RideLocationPoint(
                            latitude = gps.latitude,
                            longitude = gps.longitude,
                            accuracy = gps.accuracy.coerceIn(1f, 100f),
                            timestamp = now,
                            receivedAt = now,
                            sequenceId = 1L,
                            source = "PASSENGER_DEVICE_GPS"
                        )
                    } ?: if (req.pickupLatitude != 0.0) {
                        com.elysium369.meet.ui.screens.ride.RideLocationPoint(
                            latitude = req.pickupLatitude,
                            longitude = req.pickupLongitude,
                            accuracy = req.pickupAccuracy.coerceIn(1f, 100f),
                            timestamp = now,
                            receivedAt = now,
                            sequenceId = 1L,
                            source = "PICKUP_COORDINATES"
                        )
                    } else null

                    com.elysium369.meet.ui.screens.ride.ActiveRideViewState(
                        rideId = req.requestId,
                        driver = matchedDriver,
                        pickup = com.elysium369.meet.ui.screens.ride.RidePlaceInput(
                            placeId = "p_${req.requestId}",
                            displayName = req.pickupAddress,
                            address = req.pickupAddress,
                            latitude = req.pickupLatitude,
                            longitude = req.pickupLongitude,
                            placeType = com.elysium369.meet.ui.screens.ride.PlaceType.CURRENT
                        ),
                        dropoff = com.elysium369.meet.ui.screens.ride.RidePlaceInput(
                            placeId = "d_${req.requestId}",
                            displayName = req.destAddress,
                            address = req.destAddress,
                            latitude = req.destLatitude,
                            longitude = req.destLongitude,
                            placeType = com.elysium369.meet.ui.screens.ride.PlaceType.SEARCH
                        ),
                        fareQuote = run {
                            val breakdown = try {
                                org.json.JSONObject(req.fareBreakdownJson)
                            } catch (_: Exception) {
                                org.json.JSONObject()
                            }
                            val mode = breakdown.optString("mode", "OPEN_BID")
                            val distanceFare = breakdown.optLong("distanceFareMinor", 0L)
                            val timeFare = breakdown.optLong("timeFareMinor", 0L)
                            val serverTotal = when {
                                breakdown.has("estimatedTotalMinor") -> breakdown.optLong("estimatedTotalMinor", req.estimatedFareMinor)
                                breakdown.has("acceptedFareMinor") -> breakdown.optLong("acceptedFareMinor", req.estimatedFareMinor)
                                else -> req.estimatedFareMinor
                            }
                            com.elysium369.meet.ui.screens.ride.FareQuote(
                                baseFare = serverTotal - distanceFare - timeFare,
                                distanceFare = distanceFare,
                                timeFare = timeFare,
                                totalFare = serverTotal,
                                currency = req.currency,
                                estimatedDistanceKm = req.estimatedDistanceKm,
                                estimatedDurationMin = req.estimatedDurationMin,
                                fareMode = try {
                                    com.elysium369.meet.ride.domain.RideFareMode.valueOf(mode)
                                } catch (_: Exception) {
                                    com.elysium369.meet.ride.domain.RideFareMode.METERED_TIME_DISTANCE
                                }
                            )
                        },
                        state = parsedState,
                        driverLocation = driverLoc,
                        passengerLocation = passengerLoc,
                        boardingPin = req.boardingPin
                    )
                }

                if (activeRide != null) {
                    com.elysium369.meet.ui.screens.ride.ActiveRideTrackingScreen(
                        ride = activeRide,
                        notice = rideNotice,
                        onCancelRide = {
                            val isDriver = isDriverMode
                            val role = if (isDriver) "DRIVER" else "PASSENGER"
                            val reason = if (isDriver) {
                                com.elysium369.meet.ride.domain.RideCancellationReason.PASSENGER_NO_SHOW
                            } else {
                                com.elysium369.meet.ride.domain.RideCancellationReason.CHANGE_OF_PLANS
                            }
                            obdViewModel.cancelRide(
                                requestId = activeRide.rideId,
                                reason = reason,
                                detail = "Cancelado desde seguimiento",
                                actorRole = role
                            )
                        },
                        onGeneratePin = {
                            obdViewModel.issueRideBoardingPin(activeRide.rideId)
                        },
                        onCallDriver = {
                            obdViewModel.startRideCall(activeRide.rideId, "PASSENGER")
                        },
                        onMessageDriver = {
                            navController.navigate(
                                "messages?serviceVertical=ride&serviceReferenceId=${activeRide.rideId}&serviceTitle=Viaje%20Elysium"
                            )
                        },
                        onPay = null,
                        onRate = {
                            activeRideReq?.let { obdViewModel.promptRideRating(it) }
                            navController.navigate(com.elysium369.meet.ui.navigation.MeetDestinations.RIDE_HOME) {
                                popUpTo(MeetDestinations.RIDE_ACTIVE_TRACKING) { inclusive = true }
                            }
                        },
                        onBack = { navController.backOrHome() }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(com.elysium369.meet.ui.theme.MeetColors.backgroundDeep)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No hay ningún viaje activo",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = com.elysium369.meet.ui.theme.MeetColors.textPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Solicita un viaje desde la sección de Movilidad para iniciar el seguimiento.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.elysium369.meet.ui.theme.MeetColors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { navController.navigate(MeetDestinations.RIDE_PASSENGER_REQUEST) },
                                colors = ButtonDefaults.buttonColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen, contentColor = Color.Black)
                            ) {
                                Text("SOLICITAR VIAJE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            composable(MeetDestinations.RIDE_SCHEDULE) {
                val scheduleViewModel: com.elysium369.meet.ui.screens.ride.RideScheduleViewModel = hiltViewModel()
                val currentUserId = obdViewModel.currentUserId ?: ""
                com.elysium369.meet.ui.screens.ride.RideScheduleScreen(
                    navController = navController,
                    userId = currentUserId,
                    viewModel = scheduleViewModel,
                )
            }

            composable("tow_active_tracking") {
                com.elysium369.meet.ui.screens.tow.TowFulfillmentScreen(
                    viewModel = obdViewModel,
                    towRepository = towRepository,
                    onBack = { navController.backOrHome() }
                )
            }

            composable("unified_activity") {
                com.elysium369.meet.fulfillment.ui.UnifiedActivityScreen(onBack = { navController.backOrHome() })
            }

            composable("fleet") {
                val chatViewModel: FleetChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                FleetChatListScreen(navController = navController, viewModel = chatViewModel, businessId = "fleet_default", onBack = { navController.backOrHome() })
            }
            composable("support") {
                val vehicle by obdViewModel.selectedVehicle.collectAsState()
                val vehicleLabel = vehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo Genérico"
                SupportChatScreen(onBack = { navController.backOrHome() }, vehicleInfo = vehicleLabel)
            }
            composable("battery_health") {
                HealthScoreScreen(navController = navController, viewModel = obdViewModel)
            }

            // SAFETY FOUNDATION V1
            composable(MeetDestinations.SAFETY_HOME) {
                com.elysium369.meet.safety.ui.hub.SafetyHubScreen(
                    onBack = { navController.backOrHome() },
                    onNavigateToMap = { navController.navigate(MeetDestinations.SAFETY_MAP) },
                    onNavigateToReport = { navController.navigate(MeetDestinations.SAFETY_REPORT) },
                    onNavigateToMyReports = { navController.navigate(MeetDestinations.SAFETY_MY_REPORTS) },
                    onNavigateToCases = { navController.navigate(MeetDestinations.SAFETY_CASES) },
                    onNavigateToTimelines = { navController.navigate(MeetDestinations.SAFETY_CASES) },
                    onNavigateToAccountability = { navController.navigate(MeetDestinations.SAFETY_ACCOUNTABILITY) },
                    onNavigateToObservatory = { navController.navigate(MeetDestinations.SAFETY_OBSERVATORY) },
                )
            }
            composable(MeetDestinations.SAFETY_MAP) {
                com.elysium369.meet.safety.ui.map.SafetyMapScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToReport = { navController.navigate(MeetDestinations.SAFETY_REPORT) },
                    onSearchLocation = { navController.navigate("safety/report/location/search") },
                    onSelectLocationOnMap = { navController.navigate("safety/report/location/map") },
                )
            }
            composable(MeetDestinations.SAFETY_REPORT) {
                com.elysium369.meet.safety.ui.report.SafetyReportScreen(
                    onBack = { navController.popBackStack() },
                    onReportSubmitted = {
                        navController.popBackStack()
                        navController.navigate(MeetDestinations.SAFETY_MY_REPORTS)
                    },
                )
            }
            composable(
                route = MeetDestinations.SAFETY_REPORT_LOCATION,
                arguments = listOf(navArgument("mode") { type = NavType.StringType }),
            ) { entry ->
                com.elysium369.meet.safety.ui.report.SafetyReportScreen(
                    onBack = { navController.popBackStack() },
                    onReportSubmitted = {
                        navController.popBackStack()
                        navController.navigate(MeetDestinations.SAFETY_MY_REPORTS)
                    },
                    locationEntryMode = entry.arguments?.getString("mode"),
                )
            }
            composable(MeetDestinations.SAFETY_MY_REPORTS) {
                val viewModel: com.elysium369.meet.safety.ui.report.SafetyMyReportsViewModel = hiltViewModel()
                com.elysium369.meet.safety.ui.report.SafetyMyReportsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(MeetDestinations.SAFETY_CASES) {
                com.elysium369.meet.safety.ui.cases.SafetyCasesScreen(
                    onBack = { navController.popBackStack() },
                    onCaseClick = { caseId -> navController.navigate("safety/case/" + android.net.Uri.encode(caseId)) },
                )
            }
            composable(
                route = MeetDestinations.SAFETY_CASE_DETAIL,
                arguments = listOf(androidx.navigation.navArgument("caseId") { type = androidx.navigation.NavType.StringType }),
            ) {
                com.elysium369.meet.safety.ui.cases.SafetyCaseDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(MeetDestinations.SAFETY_ACCOUNTABILITY) {
                com.elysium369.meet.safety.ui.accountability.SafetyAccountabilityScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(MeetDestinations.SAFETY_OBSERVATORY) {
                com.elysium369.meet.safety.ui.observatory.SafetyObservatoryScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }

        val liveCallState by obdViewModel.liveCallState.collectAsState()
        RideLiveCallOverlay(
            state = liveCallState,
            onToggleMute = { obdViewModel.toggleRideCallMute() },
            onHangUp = { obdViewModel.endRideCall() },
            onAnswer = {
                val incoming = liveCallState as? com.elysium369.meet.communications.LiveCallState.Incoming
                if (incoming != null) {
                    val role = if (incoming.callerRole.equals("DRIVER", ignoreCase = true)) "PASSENGER" else "DRIVER"
                    obdViewModel.startRideCall(incoming.rideId, role)
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(99f),
        )

        // Elysium Living 3D Companion Overlay (Draco Dragon, Pokemon Volt, Goku SSJ4, Evair, etc.)
        // Lives across all screens, draggable, speech bubble, TTS audible interaction, companion switcher
        ElysiumLivingCompanionOverlay(
            navController = navController,
            obdViewModel = obdViewModel,
            activeRoute = activeRoute,
        )
        }
        BackHandler(enabled = activeRoute != null && activeRoute != MeetDestinations.HOME) {
            navController.backOrHome()
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
            onClick = { navController.navigateTopLevel("home") },
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
            onClick = { navController.navigateTopLevel("scanner") },
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
            onClick = { navController.navigateTopLevel("dtc") },
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
            onClick = { navController.navigateTopLevel("garage") },
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
            onClick = { navController.navigateTopLevel("pro_hub") },
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
