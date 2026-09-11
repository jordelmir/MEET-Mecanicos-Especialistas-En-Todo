# Empty default callbacks — inventory

This is a syntactic inventory. A match is not proof of production reachability. Critical candidates require caller/authority tracing before closure.

| Location | Triage | Declaration |
|---|---|---|
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/navigation/ForgeNavGraph.kt:533 | REVIEW_REQUIRED | `fun ForgeEntryPoint(onClose: () -> Unit = {}) {` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeAssemblyEditorScreen.kt:58 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeVehicleBuilderScreen.kt:49 | REVIEW_REQUIRED | `onBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeSimulationAndDiagnosticsScreens.kt:74 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeSimulationAndDiagnosticsScreens.kt:192 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeSimulationAndDiagnosticsScreens.kt:291 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeSimulationAndDiagnosticsScreens.kt:420 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeSimulationAndDiagnosticsScreens.kt:475 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeSimulationAndDiagnosticsScreens.kt:543 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgeSimulationAndDiagnosticsScreens.kt:611 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium/vanguard/forge/presentation/screens/ForgePartEditorScreen.kt:77 | REVIEW_REQUIRED | `onBack: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/MainActivity.kt:233 | REVIEW_REQUIRED | `onPasswordRecoveryHandled: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/fulfillment/ui/UnifiedActivityScreen.kt:54 | REVIEW_REQUIRED | `onBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/core/monetization/MonetizationAnalytics.kt:48 | REVIEW_REQUIRED | `propertiesBuilder: JsonObjectBuilder.() -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/terminal/InteractiveTerminalCanvas.kt:58 | REVIEW_REQUIRED | `onFocusRequest: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideSafetyPanels.kt:54 | REVIEW_REQUIRED | `onRequestTopUp: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/AuthScreen.kt:41 | REVIEW_REQUIRED | `onRecoveryComplete: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/AiDiagnosticScreen.kt:62 | REVIEW_REQUIRED | `onNavigateToSettings: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/AiDiagnosticScreen.kt:65 | REVIEW_REQUIRED | `onOpenComponent3d: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/DekraConciergeScreen.kt:44 | CRITICAL_REVIEW_REQUIRED | `onOpenMessages: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/UniversalServicesScreen.kt:48 | CRITICAL_REVIEW_REQUIRED | `onOpenMessages: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/MechanicServiceScreen.kt:64 | REVIEW_REQUIRED | `onNavigateBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/MechanicServiceScreen.kt:66 | CRITICAL_REVIEW_REQUIRED | `onOpenMessages: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/TowTruckServiceScreen.kt:58 | REVIEW_REQUIRED | `onNavigateBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/TowTruckServiceScreen.kt:59 | CRITICAL_REVIEW_REQUIRED | `onOpenMessages: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/PartRequestScreen.kt:312 | REVIEW_REQUIRED | `onNavigateBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/PartRequestScreen.kt:313 | CRITICAL_REVIEW_REQUIRED | `onOpenMessages: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ProviderRegistrationScreen.kt:117 | REVIEW_REQUIRED | `onNavigateBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt:116 | REVIEW_REQUIRED | `onNavigateBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt:117 | REVIEW_REQUIRED | `onOpenDriverRegistration: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt:119 | REVIEW_REQUIRED | `onNavigateToSchedule: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/RideServiceScreen.kt:2041 | REVIEW_REQUIRED | `onRegisterDriver: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/components/EliteUI.kt:464 | REVIEW_REQUIRED | `actions: @Composable RowScope.() -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/tow/TowFulfillmentScreen.kt:36 | REVIEW_REQUIRED | `onBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/marketos/MarketOsHubs.kt:169 | CRITICAL_REVIEW_REQUIRED | `onOpenMessages: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/marketos/MarketOsHubs.kt:211 | CRITICAL_REVIEW_REQUIRED | `onOpenMessages: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ride/PassengerRideRequestScreen.kt:53 | REVIEW_REQUIRED | `onBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ride/PttVoiceWidget.kt:45 | REVIEW_REQUIRED | `onPressStart: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ride/PttVoiceWidget.kt:46 | REVIEW_REQUIRED | `onPressEnd: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ride/PttVoiceWidget.kt:150 | REVIEW_REQUIRED | `onMuteToggle: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/ride/DriverAppScreen.kt:51 | REVIEW_REQUIRED | `onBack: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/humanity/LearningHubScreen.kt:33 | REVIEW_REQUIRED | `onOpenMultimeterSimulation: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/humanity/LearningHubScreen.kt:34 | REVIEW_REQUIRED | `onOpenCapabilityPassport: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/humanity/LearningHubScreen.kt:35 | REVIEW_REQUIRED | `onOpenElysiumLearningOs: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerDashboardTab.kt:766 | REVIEW_REQUIRED | `onCustomize: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerDashboardTab.kt:767 | REVIEW_REQUIRED | `onDiyCreate: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerDashboardTab.kt:946 | REVIEW_REQUIRED | `onTap: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerDashboardTab.kt:947 | REVIEW_REQUIRED | `onLongPress: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerComponents.kt:61 | REVIEW_REQUIRED | `onFreezeFrameClick: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerComponents.kt:63 | REVIEW_REQUIRED | `onAiConsultClick: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerComponents.kt:66 | REVIEW_REQUIRED | `onRepairGuideClick: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/home/classic/HomeClassicScreen.kt:52 | REVIEW_REQUIRED | `onCommitPreview: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/home/classic/HomeClassicScreen.kt:53 | CRITICAL_REVIEW_REQUIRED | `onCancelPreview: () -> Unit = {}` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/home/adaptive/HomeAdaptiveScreen.kt:54 | REVIEW_REQUIRED | `onCommitPreview: () -> Unit = {},` |
| android/app/src/main/kotlin/com/elysium369/meet/ui/screens/home/adaptive/HomeAdaptiveScreen.kt:55 | CRITICAL_REVIEW_REQUIRED | `onCancelPreview: () -> Unit = {}` |
