package com.elysium.vanguard.forge.data

import com.elysium.vanguard.forge.domain.ForgeArtifact
import com.elysium.vanguard.forge.domain.ForgeArtifactType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgeVehicle
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.domain.ManufacturingProcess
import com.elysium.vanguard.forge.domain.ForgeManual
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Repositorio unificado de artefactos Forge. Mantiene caché en memoria, thread-safe.
 * Permite combinar artefactos seed (cargados vía [ForgeSeedLoader]) con artefactos del usuario.
 *
 * Estrategia offline-first: no hay red en V1.
 */
class ForgeArtifactRepository {

    private val mutex = Mutex()

    private val _parts = MutableStateFlow<Map<String, ForgePart>>(emptyMap())
    private val _assemblies = MutableStateFlow<Map<String, ForgeAssembly>>(emptyMap())
    private val _vehicles = MutableStateFlow<Map<String, ForgeVehicle>>(emptyMap())
    private val _materials = MutableStateFlow<Map<String, MaterialSpec>>(emptyMap())
    private val _processes = MutableStateFlow<Map<String, ManufacturingProcess>>(emptyMap())
    private val _manuals = MutableStateFlow<Map<String, ForgeManual>>(emptyMap())

    val parts: StateFlow<Map<String, ForgePart>> = _parts.asStateFlow()
    val assemblies: StateFlow<Map<String, ForgeAssembly>> = _assemblies.asStateFlow()
    val vehicles: StateFlow<Map<String, ForgeVehicle>> = _vehicles.asStateFlow()
    val materials: StateFlow<Map<String, MaterialSpec>> = _materials.asStateFlow()
    val processes: StateFlow<Map<String, ManufacturingProcess>> = _processes.asStateFlow()
    val manuals: StateFlow<Map<String, ForgeManual>> = _manuals.asStateFlow()

    /**
     * Ingesta un lote de documentos, indexando cada uno por tipo.
     * Si un id colisiona con uno existente del usuario, NO sobreescribe (modo seed).
     */
    suspend fun ingestSeeds(docs: List<ForgeArtifactDocument>) =
        mutex.withLock {
            val parts = HashMap(_parts.value)
            val assemblies = HashMap(_assemblies.value)
            val vehicles = HashMap(_vehicles.value)
            val materials = HashMap(_materials.value)
            val processes = HashMap(_processes.value)
            val manuals = HashMap(_manuals.value)

            for (doc in docs) {
                when (doc) {
                    is ForgeArtifactDocument.PartDocument ->
                        if (!parts.containsKey(doc.id)) parts[doc.id] = doc.part
                    is ForgeArtifactDocument.AssemblyDocument ->
                        if (!assemblies.containsKey(doc.id)) assemblies[doc.id] = doc.assembly
                    is ForgeArtifactDocument.VehicleDocument ->
                        if (!vehicles.containsKey(doc.id)) vehicles[doc.id] = doc.vehicle
                    is ForgeArtifactDocument.MaterialDocument ->
                        materials.putIfAbsent(doc.id, doc.material)
                    is ForgeArtifactDocument.ProcessDocument ->
                        processes.putIfAbsent(doc.id, doc.process)
                    is ForgeArtifactDocument.ManualDocument ->
                        manuals.putIfAbsent(doc.id, doc.manual)
                    is ForgeArtifactDocument.ScenarioDocument -> {
                        // Escenarios no se indexan — referenciados por vehicles.
                    }
                }
            }
            _parts.value = parts
            _assemblies.value = assemblies
            _vehicles.value = vehicles
            _materials.value = materials
            _processes.value = processes
            _manuals.value = manuals
        }

    suspend fun savePart(part: ForgePart) = mutex.withLock {
        val map = HashMap(_parts.value)
        map[part.artifact.id] = part
        _parts.value = map
    }

    suspend fun saveAssembly(assembly: ForgeAssembly) = mutex.withLock {
        val map = HashMap(_assemblies.value)
        map[assembly.artifact.id] = assembly
        _assemblies.value = map
    }

    suspend fun saveVehicle(vehicle: ForgeVehicle) = mutex.withLock {
        val map = HashMap(_vehicles.value)
        map[vehicle.artifact.id] = vehicle
        _vehicles.value = map
    }

    suspend fun saveManual(manual: ForgeManual) = mutex.withLock {
        val map = HashMap(_manuals.value)
        map[manual.id] = manual
        _manuals.value = map
    }

    suspend fun deleteArtifact(id: String) = mutex.withLock {
        _parts.value = _parts.value - id
        _assemblies.value = _assemblies.value - id
        _vehicles.value = _vehicles.value - id
        _manuals.value = _manuals.value - id
    }

    fun getPart(id: String): ForgePart? = _parts.value[id]
    fun getAssembly(id: String): ForgeAssembly? = _assemblies.value[id]
    fun getVehicle(id: String): ForgeVehicle? = _vehicles.value[id]
    fun getMaterial(id: String): MaterialSpec? = _materials.value[id]
    fun getProcess(id: String): ManufacturingProcess? = _processes.value[id]
    fun getManual(id: String): ForgeManual? = _manuals.value[id]

    fun search(query: String, type: ForgeArtifactType? = null): List<ForgeArtifact> {
        val q = sanitize(query)
        if (q.isBlank()) return emptyList()
        val sources: List<ForgeArtifact> = when (type) {
            ForgeArtifactType.PART -> _parts.value.values.map { it.artifact }
            ForgeArtifactType.ASSEMBLY -> _assemblies.value.values.map { it.artifact }
            ForgeArtifactType.VEHICLE -> _vehicles.value.values.map { it.artifact }
            ForgeArtifactType.MATERIAL -> _materials.value.values.map {
                ForgeArtifact(
                    id = it.id,
                    name = it.displayName,
                    description = it.category,
                    artifactType = ForgeArtifactType.MATERIAL,
                    safetyClassification = com.elysium.vanguard.forge.domain.SafetyClassification.EDUCATIONAL
                )
            }
            ForgeArtifactType.MANUFACTURING_PROCESS -> _processes.value.values.map {
                ForgeArtifact(
                    id = it.id,
                    name = it.displayName,
                    description = it.category,
                    artifactType = ForgeArtifactType.MANUFACTURING_PROCESS,
                    safetyClassification = com.elysium.vanguard.forge.domain.SafetyClassification.EDUCATIONAL
                )
            }
            ForgeArtifactType.MANUAL -> _manuals.value.values.map { it.artifact }
            ForgeArtifactType.SIMULATION_SCENARIO -> emptyList()
            null -> _parts.value.values.map { it.artifact } +
                    _assemblies.value.values.map { it.artifact } +
                    _vehicles.value.values.map { it.artifact } +
                    _manuals.value.values.map { it.artifact }
        }
        return sources.filter {
            q in it.name.lowercase() ||
            q in it.id.lowercase() ||
            it.tags.any { tag -> q in tag.lowercase() }
        }
    }

    private fun sanitize(input: String): String {
        if (input.length > 64) return input.substring(0, 64).lowercase()
        return input.lowercase().filter { c -> c.isLetterOrDigit() || c == ' ' || c == '-' || c == '_' }
    }

    fun exportToText(artifactId: String): String? {
        val part = getPart(artifactId)
        if (part != null) {
            val doc = ForgeArtifactDocument.PartDocument(
                id = part.artifact.id, part = part
            )
            return Json.encodeToString(ForgeArtifactDocument.serializer(), doc)
        }
        val assembly = getAssembly(artifactId)
        if (assembly != null) {
            val doc = ForgeArtifactDocument.AssemblyDocument(
                id = assembly.artifact.id, assembly = assembly
            )
            return Json.encodeToString(ForgeArtifactDocument.serializer(), doc)
        }
        return null
    }

    /**
     * Importa un texto JSON. Esta función NO es suspend — el caller debe usar coroutines si necesita IO pesado.
     */
    fun importFromText(text: String): Result<ForgeArtifactType> {
        return try {
            val doc = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString(ForgeArtifactDocument.serializer(), text)
            // Mutaciones sincrónicas — usar las versiones internas (no suspend).
            when (doc) {
                is ForgeArtifactDocument.PartDocument -> {
                    val map = HashMap(_parts.value)
                    map[doc.part.artifact.id] = doc.part
                    _parts.value = map
                    Result.success(ForgeArtifactType.PART)
                }
                is ForgeArtifactDocument.AssemblyDocument -> {
                    val map = HashMap(_assemblies.value)
                    map[doc.assembly.artifact.id] = doc.assembly
                    _assemblies.value = map
                    Result.success(ForgeArtifactType.ASSEMBLY)
                }
                is ForgeArtifactDocument.VehicleDocument -> {
                    val map = HashMap(_vehicles.value)
                    map[doc.vehicle.artifact.id] = doc.vehicle
                    _vehicles.value = map
                    Result.success(ForgeArtifactType.VEHICLE)
                }
                is ForgeArtifactDocument.ManualDocument -> {
                    val map = HashMap(_manuals.value)
                    map[doc.manual.id] = doc.manual
                    _manuals.value = map
                    Result.success(ForgeArtifactType.MANUAL)
                }
                else -> Result.failure(IllegalArgumentException("Unsupported artifact type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}