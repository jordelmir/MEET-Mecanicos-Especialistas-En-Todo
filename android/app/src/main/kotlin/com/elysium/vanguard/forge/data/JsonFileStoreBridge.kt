package com.elysium.vanguard.forge.data

import com.elysium.vanguard.forge.domain.ForgePart
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Bridge: provee (de)serializers tipados para [JsonFileStore].
 *
 * Existe como objeto separado para evitar ciclos de import entre
 * [JsonFileStore] (genérico, sin conocer [ForgePart]) y el data class del dominio.
 *
 * Internamente usa `kotlinx.serialization` con la convención de Map<String, T>.
 * Los parts individualmente son `@Serializable` desde
 * `com.elysium.vanguard.forge.domain.ForgePart` (vía el archivo de artefacto).
 *
 * Visibilidad `internal`: solo ForgeArtifactRepository.load/saveUserPartsFromDisk
 * lo consume.
 */
internal object JsonFileStoreBridge {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val partsMapSerializer =
        MapSerializer(String.serializer(), ForgePart.serializer())

    /**
     * Serializa un Map<String, ForgePart> a string JSON.
     * El formato es un objeto JSON plano: `{ "<id>": { ...PartFields... } }`.
     */
    fun encodePartsMap(map: Map<String, ForgePart>): String =
        json.encodeToString(partsMapSerializer, map)

    /**
     * Deserializa un string JSON (producido por [encodePartsMap]) a su mapa original.
     * Si el string está vacío o malformado, retorna mapa vacío.
     */
    fun decodePartsMap(text: String): Map<String, ForgePart> =
        if (text.isBlank()) emptyMap()
        else json.decodeFromString(partsMapSerializer, text)
}
