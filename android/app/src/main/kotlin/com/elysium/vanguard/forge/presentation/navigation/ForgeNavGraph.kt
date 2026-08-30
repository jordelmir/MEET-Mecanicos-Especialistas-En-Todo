package com.elysium.vanguard.forge.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.elysium.vanguard.forge.data.ForgeArtifactRepository
import com.elysium.vanguard.forge.presentation.screens.ForgeAssemblyEditorScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeDiagnosticReportScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeEngineRuntimeScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeFailureLabScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeHomeScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeManufacturingScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeMaterialsScreen
import com.elysium.vanguard.forge.presentation.screens.ForgePartEditorScreen
import com.elysium.vanguard.forge.presentation.screens.ForgePhysicsSimulationScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeRepairManualScreen
import com.elysium.vanguard.forge.presentation.screens.ForgeVehicleBuilderScreen
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeAssemblyEditorViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeDiagnosticReportViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeEngineRuntimeViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeFailureLabViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeHomeViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeManufacturingViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeMaterialsViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgePartEditorViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgePhysicsSimulationViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeRepairManualViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeVehicleBuilderViewModel

/**
 * Constantes de rutas del sub-grafo de navegación Forge.
 * Todas las rutas tienen prefijo `forge/` para evitar colisiones con rutas MEET.
 *
 * Reglas:
 * - Rutas opcionales (id?) usan nullable + default vacío en NavArguments.
 * - Rutas requeridas (/id) usan StringType sin default.
 * - Ninguna ruta tiene espacios; se sustituyen por `-`.
 */
object ForgeRoutes {
    /** Entrada al módulo Forge desde MEET. */
    const val ENTRY: String = "forge"

    const val HOME: String = "forge/home"
    const val ABOUT: String = "forge/about"

    const val PART_EDITOR_ARG: String = "partId"
    const val PART_EDITOR: String =
        "forge/part-editor?$PART_EDITOR_ARG={$PART_EDITOR_ARG}"

    const val ASSEMBLY_EDITOR_ARG: String = "assemblyId"
    const val ASSEMBLY_EDITOR: String =
        "forge/assembly-editor?$ASSEMBLY_EDITOR_ARG={$ASSEMBLY_EDITOR_ARG}"

    const val VEHICLE_BUILDER_ARG: String = "vehicleId"
    const val VEHICLE_BUILDER: String =
        "forge/vehicle-builder?$VEHICLE_BUILDER_ARG={$VEHICLE_BUILDER_ARG}"

    const val SIMULATION_ARG: String = "assemblyId"
    const val SIMULATION: String =
        "forge/simulation?$SIMULATION_ARG={$SIMULATION_ARG}"

    const val ENGINE_RUNTIME_ARG: String = "vehicleId"
    const val ENGINE_RUNTIME: String =
        "forge/engine-runtime?$ENGINE_RUNTIME_ARG={$ENGINE_RUNTIME_ARG}"

    const val FAILURE_LAB_ARG: String = "assemblyId"
    const val FAILURE_LAB: String =
        "forge/failure-lab?$FAILURE_LAB_ARG={$FAILURE_LAB_ARG}"

    const val DIAGNOSTIC_REPORT_ARG: String = "reportId"
    const val DIAGNOSTIC_REPORT: String =
        "forge/diagnostic-report?$DIAGNOSTIC_REPORT_ARG={$DIAGNOSTIC_REPORT_ARG}"

    const val MANUAL_ARG: String = "manualId"
    const val MANUAL: String =
        "forge/manual?$MANUAL_ARG={$MANUAL_ARG}"

    const val MATERIALS: String = "forge/materials"
    const val MANUFACTURING: String = "forge/manufacturing"
    const val MY_ARTIFACTS: String = "forge/my-artifacts"

    /** Conjunto de todas las rutas para uso en guards y para diagnóstico. */
    val ALL: List<String> = listOf(
        ENTRY, HOME, ABOUT,
        PART_EDITOR, ASSEMBLY_EDITOR, VEHICLE_BUILDER,
        SIMULATION, ENGINE_RUNTIME, FAILURE_LAB,
        DIAGNOSTIC_REPORT, MANUAL,
        MATERIALS, MANUFACTURING, MY_ARTIFACTS
    )
}

/**
 * Argumento opcional reusable para NavGraph.
 * nullable=true evita crash si el argumento no viene en la URL.
 */
private fun optionalStringArg(name: String) =
    navArgument(name) {
        type = NavType.StringType
        nullable = true
        defaultValue = ""
    }

/**
 * Argumento requerido reusable.
 */
private fun requiredStringArg(name: String) =
    navArgument(name) {
        type = NavType.StringType
        nullable = false
    }

/**
 * Fabrica de ViewModels Forge. Cada VM tiene constructor con defaults, por lo que
 * podemos instanciarlos con argumentos nombrados cuando aplique.
 *
 * Pensada para ser thread-safe (un solo ForgeArtifactRepository por instancia).
 */
class ForgeViewModelFactory(
    private val partId: String? = null,
    private val assemblyId: String? = null,
    private val vehicleId: String? = null,
    @Suppress("unused") private val reportId: String? = null,
    private val manualId: String? = null
) : ViewModelProvider.Factory {

    private val repo: ForgeArtifactRepository = ForgeArtifactRepository()

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ForgeHomeViewModel::class.java) ->
                ForgeHomeViewModel(repository = repo) as T
            modelClass.isAssignableFrom(ForgePartEditorViewModel::class.java) ->
                ForgePartEditorViewModel(
                    partId = partId,
                    repository = repo
                ) as T
            modelClass.isAssignableFrom(ForgeAssemblyEditorViewModel::class.java) ->
                ForgeAssemblyEditorViewModel(
                    assemblyId = assemblyId,
                    repository = repo
                ) as T
            modelClass.isAssignableFrom(ForgeVehicleBuilderViewModel::class.java) ->
                ForgeVehicleBuilderViewModel(
                    vehicleId = vehicleId,
                    repository = repo
                ) as T
            modelClass.isAssignableFrom(ForgePhysicsSimulationViewModel::class.java) ->
                ForgePhysicsSimulationViewModel(
                    assemblyId = requireNotNull(assemblyId) {
                        "ForgePhysicsSimulationViewModel requiere assemblyId"
                    },
                    repository = repo
                ) as T
            modelClass.isAssignableFrom(ForgeEngineRuntimeViewModel::class.java) ->
                ForgeEngineRuntimeViewModel(
                    vehicleId = requireNotNull(vehicleId) {
                        "ForgeEngineRuntimeViewModel requiere vehicleId"
                    },
                    repository = repo
                ) as T
            modelClass.isAssignableFrom(ForgeFailureLabViewModel::class.java) ->
                ForgeFailureLabViewModel(
                    assemblyId = requireNotNull(assemblyId) {
                        "ForgeFailureLabViewModel requiere assemblyId"
                    },
                    repository = repo
                ) as T
            modelClass.isAssignableFrom(ForgeDiagnosticReportViewModel::class.java) ->
                // Por diseño, el DiagnosticReportViewModel no carga por id desde el repo:
                // el reporte se construye en flujo (FailureLab -> DiagnosticEngine -> setReport).
                // Mantenemos reportId en NavArguments para deep-linking / debugging.
                ForgeDiagnosticReportViewModel() as T
            modelClass.isAssignableFrom(ForgeRepairManualViewModel::class.java) ->
                ForgeRepairManualViewModel(
                    manualId = manualId,
                    repository = repo
                ) as T
            modelClass.isAssignableFrom(ForgeMaterialsViewModel::class.java) ->
                ForgeMaterialsViewModel(repository = repo) as T
            modelClass.isAssignableFrom(ForgeManufacturingViewModel::class.java) ->
                ForgeManufacturingViewModel(repository = repo) as T
            else -> error("ForgeViewModelFactory no sabe crear ${modelClass.name}")
        }
    }
}

/**
 * State Machine formal del navegador Forge.
 *
 * Modela los estados discretos por los que la UI puede pasar dentro del módulo.
 * No es un grafo completo de transiciones arbitrarias: define los estados canónicos
 * que un taller pasa para llegar de "sin artefactos" a "falla diagnosticada y
 * manual generado".
 *
 * El NavController es el ejecutor real de las transiciones; este objeto provee
 * los nombres de estado y validadores para evitar movimientos inválidos
 * (p. ej. entrar a Simulation sin un assemblyId resuelto).
 */
object ForgeStateMachine {

    /**
     * Estados del recorrido canónico Forge.
     */
    enum class ForgeScreenState(val route: String, val requires: Set<Requirement>) {
        HOME(ForgeRoutes.HOME, emptySet()),
        ABOUT(ForgeRoutes.ABOUT, emptySet()),
        MY_ARTIFACTS(ForgeRoutes.MY_ARTIFACTS, emptySet()),
        MATERIALS(ForgeRoutes.MATERIALS, emptySet()),
        MANUFACTURING(ForgeRoutes.MANUFACTURING, emptySet()),
        PART_EDITOR(ForgeRoutes.PART_EDITOR, setOf(Requirement.OPTIONAL_PART_ID)),
        ASSEMBLY_EDITOR(ForgeRoutes.ASSEMBLY_EDITOR, setOf(Requirement.OPTIONAL_ASSEMBLY_ID)),
        VEHICLE_BUILDER(ForgeRoutes.VEHICLE_BUILDER, setOf(Requirement.OPTIONAL_VEHICLE_ID)),
        SIMULATION(ForgeRoutes.SIMULATION, setOf(Requirement.REQUIRED_ASSEMBLY_ID)),
        ENGINE_RUNTIME(ForgeRoutes.ENGINE_RUNTIME, setOf(Requirement.REQUIRED_VEHICLE_ID)),
        FAILURE_LAB(ForgeRoutes.FAILURE_LAB, setOf(Requirement.REQUIRED_ASSEMBLY_ID)),
        DIAGNOSTIC_REPORT(ForgeRoutes.DIAGNOSTIC_REPORT, setOf(Requirement.REQUIRED_REPORT_ID)),
        MANUAL(ForgeRoutes.MANUAL, setOf(Requirement.REQUIRED_MANUAL_ID));

        companion object {
            fun fromRoute(route: String?): ForgeScreenState? =
                values().firstOrNull { it.route == route }
        }
    }

    /**
     * Requisitos lógicos para entrar a una pantalla Forge.
     */
    enum class Requirement { OPTIONAL_PART_ID, OPTIONAL_ASSEMBLY_ID, OPTIONAL_VEHICLE_ID, REQUIRED_ASSEMBLY_ID, REQUIRED_VEHICLE_ID, REQUIRED_REPORT_ID, REQUIRED_MANUAL_ID }

    /**
     * Inputs de navegación pre-validada.
     */
    data class NavigationRequest(
        val target: ForgeScreenState,
        val partId: String? = null,
        val assemblyId: String? = null,
        val vehicleId: String? = null,
        val reportId: String? = null,
        val manualId: String? = null
    ) {
        /**
         * Resultado de validación: si es nulo, la transición es válida.
         */
        fun validate(): String? {
            val reqs = target.requires
            if (Requirement.OPTIONAL_ASSEMBLY_ID in reqs && assemblyId != null && assemblyId.isBlank()) {
                return "assemblyId presente pero vacío"
            }
            if (Requirement.OPTIONAL_VEHICLE_ID in reqs && vehicleId != null && vehicleId.isBlank()) {
                return "vehicleId presente pero vacío"
            }
            if (Requirement.OPTIONAL_PART_ID in reqs && partId != null && partId.isBlank()) {
                return "partId presente pero vacío"
            }
            if (Requirement.REQUIRED_ASSEMBLY_ID in reqs && assemblyId.isNullOrBlank()) {
                return "Ruta ${target.route} requiere assemblyId no vacío"
            }
            if (Requirement.REQUIRED_VEHICLE_ID in reqs && vehicleId.isNullOrBlank()) {
                return "Ruta ${target.route} requiere vehicleId no vacío"
            }
            if (Requirement.REQUIRED_REPORT_ID in reqs && reportId.isNullOrBlank()) {
                return "Ruta ${target.route} requiere reportId no vacío"
            }
            if (Requirement.REQUIRED_MANUAL_ID in reqs && manualId.isNullOrBlank()) {
                return "Ruta ${target.route} requiere manualId no vacío"
            }
            return null
        }
    }

    /**
     * Construye la ruta final con query params opcionales solo cuando tienen valor.
     */
    fun buildRoute(req: NavigationRequest): String {
        val base = req.target.route
        val params = mutableListOf<String>()
        if (req.partId?.isNotBlank() == true) params += "${ForgeRoutes.PART_EDITOR_ARG}=${req.partId}"
        if (req.assemblyId?.isNotBlank() == true) params += "${ForgeRoutes.ASSEMBLY_EDITOR_ARG}=${req.assemblyId}"
        if (req.vehicleId?.isNotBlank() == true) params += "${ForgeRoutes.VEHICLE_BUILDER_ARG}=${req.vehicleId}"
        if (req.reportId?.isNotBlank() == true) params += "${ForgeRoutes.DIAGNOSTIC_REPORT_ARG}=${req.reportId}"
        if (req.manualId?.isNotBlank() == true) params += "${ForgeRoutes.MANUAL_ARG}=${req.manualId}"
        return if (params.isEmpty()) base else "$base&${params.joinToString("&")}"
    }

    /**
     * Helper de navegación segura: sólo ejecuta navigate si validate() pasa.
     * Si la validación falla, retorna el mensaje de error para diagnóstico.
     */
    fun navigateIfValid(
        navController: NavController,
        request: NavigationRequest,
        popUpToRoute: String? = null,
        popUpInclusive: Boolean = false
    ): String? {
        val err = request.validate()
        if (err != null) return err
        val route = buildRoute(request)
        return try {
            if (popUpToRoute != null) {
                navController.navigate(route) {
                    if (popUpInclusive) {
                        popUpTo(popUpToRoute) { inclusive = true }
                    } else {
                        popUpTo(popUpToRoute)
                    }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(route) { launchSingleTop = true }
            }
            null
        } catch (t: Throwable) {
            "Falló navigate: ${t.message}"
        }
    }
}

/**
 * Sub-grafo de navegación Forge. Se monta dentro del NavHost raíz de MEET
 * detrás de un `composable("forge")` o como destino directo.
 *
 * Decisiones:
 * - Usamos `viewModel(factory = ...)` por ruta, pasando los ids opcionales.
 * - `launchSingleTop = true` para evitar duplicación al navegar dos veces al mismo destino.
 * - Pop-back-to-home al cerrar todas las pantallas Forge cierra el módulo.
 */
@Composable
fun ForgeNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = ForgeRoutes.HOME,
        modifier = androidx.compose.ui.Modifier
    ) {
        composable(ForgeRoutes.HOME) {
            val vm: ForgeHomeViewModel = viewModel(
                factory = ForgeViewModelFactory()
            )
            ForgeHomeScreen(
                viewModel = vm,
                onNavigate = { destination ->
                    // Mapeo simple: el destino es la ruta. Si la ruta no es Forge,
                    // asumimos que el destino es un id de artefacto MEET o un placeholder.
                    if (ForgeRoutes.ALL.contains(destination) || destination.startsWith("forge/")) {
                        navController.navigate(destination) { launchSingleTop = true }
                    }
                }
            )
        }
        composable(ForgeRoutes.ABOUT) {
            // El About es opcional pero está reservado. Si no hay contenido propio,
            // reusamos la ForgeHomeScreen como placeholder informativo.
            val vm: ForgeHomeViewModel = viewModel(factory = ForgeViewModelFactory())
            ForgeHomeScreen(
                viewModel = vm,
                onNavigate = { navController.navigate(it) { launchSingleTop = true } }
            )
        }

        composable(
            route = ForgeRoutes.PART_EDITOR,
            arguments = listOf(optionalStringArg(ForgeRoutes.PART_EDITOR_ARG))
        ) { backStack ->
            val partId = backStack.arguments?.getString(ForgeRoutes.PART_EDITOR_ARG)?.takeIf { it.isNotBlank() }
            val vm: ForgePartEditorViewModel = viewModel(
                factory = ForgeViewModelFactory(partId = partId)
            )
            ForgePartEditorScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ForgeRoutes.ASSEMBLY_EDITOR,
            arguments = listOf(optionalStringArg(ForgeRoutes.ASSEMBLY_EDITOR_ARG))
        ) { backStack ->
            val assemblyId = backStack.arguments?.getString(ForgeRoutes.ASSEMBLY_EDITOR_ARG)?.takeIf { it.isNotBlank() }
            val vm: ForgeAssemblyEditorViewModel = viewModel(
                factory = ForgeViewModelFactory(assemblyId = assemblyId)
            )
            ForgeAssemblyEditorScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ForgeRoutes.VEHICLE_BUILDER,
            arguments = listOf(optionalStringArg(ForgeRoutes.VEHICLE_BUILDER_ARG))
        ) { backStack ->
            val vehicleId = backStack.arguments?.getString(ForgeRoutes.VEHICLE_BUILDER_ARG)?.takeIf { it.isNotBlank() }
            val vm: ForgeVehicleBuilderViewModel = viewModel(
                factory = ForgeViewModelFactory(vehicleId = vehicleId)
            )
            ForgeVehicleBuilderScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onSimulate = { vid ->
                    ForgeStateMachine.navigateIfValid(
                        navController,
                        ForgeStateMachine.NavigationRequest(
                            target = ForgeStateMachine.ForgeScreenState.ENGINE_RUNTIME,
                            vehicleId = vid
                        )
                    )
                }
            )
        }

        composable(
            route = ForgeRoutes.SIMULATION,
            arguments = listOf(requiredStringArg(ForgeRoutes.SIMULATION_ARG))
        ) { backStack ->
            val assemblyId = backStack.arguments?.getString(ForgeRoutes.SIMULATION_ARG) ?: ""
            val vm: ForgePhysicsSimulationViewModel = viewModel(
                factory = ForgeViewModelFactory(assemblyId = assemblyId)
            )
            ForgePhysicsSimulationScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ForgeRoutes.ENGINE_RUNTIME,
            arguments = listOf(requiredStringArg(ForgeRoutes.ENGINE_RUNTIME_ARG))
        ) { backStack ->
            val vehicleId = backStack.arguments?.getString(ForgeRoutes.ENGINE_RUNTIME_ARG) ?: ""
            val vm: ForgeEngineRuntimeViewModel = viewModel(
                factory = ForgeViewModelFactory(vehicleId = vehicleId)
            )
            ForgeEngineRuntimeScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ForgeRoutes.FAILURE_LAB,
            arguments = listOf(requiredStringArg(ForgeRoutes.FAILURE_LAB_ARG))
        ) { backStack ->
            val assemblyId = backStack.arguments?.getString(ForgeRoutes.FAILURE_LAB_ARG) ?: ""
            val vm: ForgeFailureLabViewModel = viewModel(
                factory = ForgeViewModelFactory(assemblyId = assemblyId)
            )
            ForgeFailureLabScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ForgeRoutes.DIAGNOSTIC_REPORT,
            arguments = listOf(requiredStringArg(ForgeRoutes.DIAGNOSTIC_REPORT_ARG))
        ) { backStack ->
            val reportId = backStack.arguments?.getString(ForgeRoutes.DIAGNOSTIC_REPORT_ARG) ?: ""
            val vm: ForgeDiagnosticReportViewModel = viewModel(
                factory = ForgeViewModelFactory(reportId = reportId)
            )
            ForgeDiagnosticReportScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ForgeRoutes.MANUAL,
            arguments = listOf(requiredStringArg(ForgeRoutes.MANUAL_ARG))
        ) { backStack ->
            val manualId = backStack.arguments?.getString(ForgeRoutes.MANUAL_ARG) ?: ""
            val vm: ForgeRepairManualViewModel = viewModel(
                factory = ForgeViewModelFactory(manualId = manualId)
            )
            ForgeRepairManualScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ForgeRoutes.MATERIALS) {
            val vm: ForgeMaterialsViewModel = viewModel(
                factory = ForgeViewModelFactory()
            )
            ForgeMaterialsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ForgeRoutes.MANUFACTURING) {
            val vm: ForgeManufacturingViewModel = viewModel(
                factory = ForgeViewModelFactory()
            )
            ForgeManufacturingScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ForgeRoutes.MY_ARTIFACTS) {
            // MyArtifacts reusa la HomeScreen con foco en los artefactos creados por el usuario.
            val vm: ForgeHomeViewModel = viewModel(
                factory = ForgeViewModelFactory()
            )
            ForgeHomeScreen(
                viewModel = vm,
                onNavigate = { navController.navigate(it) { launchSingleTop = true } }
            )
        }
    }
}

/**
 * Punto de entrada del módulo Forge desde MEET MainActivity.
 * Crea un NavController anidado y monta el sub-grafo.
 *
 * Uso en MainActivity:
 *   composable("forge") {
 *       ForgeEntryPoint(onClose = { navController.popBackStack() })
 *   }
 */
@Composable
fun ForgeEntryPoint(onClose: () -> Unit = {}) {
    val nestedController = androidx.navigation.compose.rememberNavController()
    ForgeNavGraph(navController = nestedController)
    val currentRoute = nestedController.currentBackStackEntryAsState().value?.destination?.route
    BackHandler {
        if (currentRoute == ForgeRoutes.HOME || !nestedController.popBackStack()) {
            onClose()
        }
    }
}
