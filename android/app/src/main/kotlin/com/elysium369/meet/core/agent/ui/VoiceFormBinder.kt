package com.elysium369.meet.core.agent.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface VoiceBindingResult {
    data class Applied(val fieldId: AgentUiControlId, val text: String, val isFinal: Boolean) : VoiceBindingResult
    data class MultiSlotApplied(val slots: Map<AgentUiControlId, String>) : VoiceBindingResult
    data class DeniedSecretField(val fieldId: AgentUiControlId) : VoiceBindingResult
    data class StaleHumanEditRace(val fieldId: AgentUiControlId) : VoiceBindingResult
    object NotApplicable : VoiceBindingResult
    object Ambiguous : VoiceBindingResult
}

/**
 * ══════════════════════════════════════════════════════════════════════
 *  V O I C E   F O R M   B I N D E R
 *  ──────────────────────────────────────────────────────────────
 *  Maneja la escritura progresiva de texto mientras el usuario habla,
 *  resolución semántica de campos por rol (recogida, destino, búsqueda),
 *  extracción multislot ("de X a Y"), y protección contra condiciones
 *  de carrera con la edición manual del humano.
 * ══════════════════════════════════════════════════════════════════════
 */
class VoiceFormBinder(
    private val registry: AgentUiRegistry = AgentUiRegistry.default,
) {

    private var activeSession: VoiceTextSession? = null
    private var interruptedFieldId: AgentUiControlId? = null
    fun resetSession() { activeSession = null; interruptedFieldId = null }

    // Regex para extracción multislot de origen y destino en viajes
    private val multiSlotRegexes = listOf(
        Regex("""(?:rec[oó]geme\s+en|origen|desde|recogida\s*(?:en)?)\s+(.+?)\s+(?:y\s+ll[eé]vame\s+a|destino|hacia|para|a)\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^de\s+(.+?)\s+a\s+(.+)$""", RegexOption.IGNORE_CASE),
    )

    suspend fun bindPartial(
        partialTranscript: String,
        candidateFields: List<AgentUiControlSnapshot> = registry.activeTextFields(),
    ): VoiceBindingResult {
        val text = partialTranscript.trim()
        interruptedFieldId?.let { return VoiceBindingResult.StaleHumanEditRace(it) }
        if (text.isBlank() || candidateFields.isEmpty()) return VoiceBindingResult.NotApplicable

        // Si ya hay una sesión activa en curso
        val session = activeSession
        if (session != null) {
            val targetControl = registry.current(session.fieldId) ?: run {
                activeSession = null
                return VoiceBindingResult.NotApplicable
            }

            // HUMAN EDIT RACE: Si el usuario editó manualmente, la generación cambió
            if (targetControl.snapshot.generation != session.fieldGeneration || !targetControl.snapshot.isActionable ||
                targetControl.readText?.invoke() != session.computeValueForPartial(session.lastPartialText)) {
                activeSession = null
                interruptedFieldId = session.fieldId
                return VoiceBindingResult.StaleHumanEditRace(session.fieldId)
            }

            if ((targetControl.snapshot.sensitivity == AgentUiSensitivity.SECRET || targetControl.snapshot.role == AgentTextFieldRole.SECRET)) {
                activeSession = null
                return VoiceBindingResult.DeniedSecretField(session.fieldId)
            }

            if (targetControl.writeText == null) return VoiceBindingResult.NotApplicable
            val cleanText = cleanSpokenPrefix(text, targetControl.snapshot)
            val projectedValue = session.computeValueForPartial(cleanText)
            withContext(Dispatchers.Main.immediate) {
                targetControl.writeText?.invoke(projectedValue)
            }
            activeSession = session.copy(lastPartialText = cleanText)
            return VoiceBindingResult.Applied(session.fieldId, projectedValue, isFinal = false)
        }

        // Si no hay sesión, resolver campo de destino
        val targetField = resolveTargetField(text, candidateFields) ?: return VoiceBindingResult.NotApplicable
        if ((targetField.sensitivity == AgentUiSensitivity.SECRET || targetField.role == AgentTextFieldRole.SECRET)) {
            return VoiceBindingResult.DeniedSecretField(targetField.id)
        }

        val control = registry.current(targetField.id) ?: return VoiceBindingResult.NotApplicable
        if (!control.snapshot.isActionable || control.writeText == null || control.readText == null) return VoiceBindingResult.NotApplicable
        val currentValue = control.readText?.invoke() ?: ""
        val cleanText = cleanSpokenPrefix(text, targetField)
        val newSession = VoiceTextSession(
            fieldId = targetField.id,
            baseValue = currentValue,
            lastPartialText = cleanText,
            fieldGeneration = targetField.generation,
        )
        activeSession = newSession

        val projectedValue = newSession.computeValueForPartial(cleanText)
        withContext(Dispatchers.Main.immediate) {
            control.writeText?.invoke(projectedValue)
        }
        return VoiceBindingResult.Applied(targetField.id, projectedValue, isFinal = false)
    }

    suspend fun bindFinal(
        finalTranscript: String,
        candidateFields: List<AgentUiControlSnapshot> = registry.activeTextFields(),
    ): VoiceBindingResult {
        val text = finalTranscript.trim()
        interruptedFieldId?.let {
            resetSession()
            return VoiceBindingResult.StaleHumanEditRace(it)
        }
        if (text.isBlank() || candidateFields.isEmpty()) {
            activeSession = null
            return VoiceBindingResult.NotApplicable
        }

        // 1. Verificar si es una frase Multi-Slot (Recogida + Destino simultáneos)
        val multiSlotResult = tryExtractAndApplyMultiSlot(text, candidateFields)
        if (multiSlotResult != null) {
            activeSession = null
            return multiSlotResult
        }

        // 2. Si había sesión activa, completar
        val session = activeSession
        if (session != null) {
            val control = registry.current(session.fieldId)
            activeSession = null

            if (control != null) {
                if (control.snapshot.generation != session.fieldGeneration || !control.snapshot.isActionable ||
                    control.readText?.invoke() != session.computeValueForPartial(session.lastPartialText)) {
                    return VoiceBindingResult.StaleHumanEditRace(session.fieldId)
                }
                if ((control.snapshot.sensitivity == AgentUiSensitivity.SECRET || control.snapshot.role == AgentTextFieldRole.SECRET)) {
                    return VoiceBindingResult.DeniedSecretField(session.fieldId)
                }

                if (control.writeText == null) return VoiceBindingResult.NotApplicable
                val finalValue = session.computeValueForPartial(cleanSpokenPrefix(text, control.snapshot))
                withContext(Dispatchers.Main.immediate) {
                    control.writeText?.invoke(finalValue)
                    control.commitText?.invoke()
                }
                return VoiceBindingResult.Applied(session.fieldId, finalValue, isFinal = true)
            }
        }

        // 3. Resolución fresca sobre campos visibles
        val targetField = resolveTargetField(text, candidateFields) ?: return VoiceBindingResult.NotApplicable
        if ((targetField.sensitivity == AgentUiSensitivity.SECRET || targetField.role == AgentTextFieldRole.SECRET)) {
            return VoiceBindingResult.DeniedSecretField(targetField.id)
        }

        val control = registry.current(targetField.id) ?: return VoiceBindingResult.NotApplicable
        if (!control.snapshot.isActionable || control.writeText == null || control.readText == null) return VoiceBindingResult.NotApplicable
        val currentValue = control.readText?.invoke() ?: ""
        val finalCleanText = cleanSpokenPrefix(text, targetField)
        val finalValue = if (currentValue.isBlank()) finalCleanText else "$currentValue $finalCleanText".trim()

        withContext(Dispatchers.Main.immediate) {
            control.writeText?.invoke(finalValue)
            control.commitText?.invoke()
        }
        return VoiceBindingResult.Applied(targetField.id, finalValue, isFinal = true)
    }

    private suspend fun tryExtractAndApplyMultiSlot(
        transcript: String,
        fields: List<AgentUiControlSnapshot>,
    ): VoiceBindingResult? {
        val pickups = fields.filter { it.role == AgentTextFieldRole.PICKUP_ADDRESS }
        val destinations = fields.filter { it.role == AgentTextFieldRole.DESTINATION_ADDRESS }
        val pickupField = pickups.singleOrNull()
        val destField = destinations.singleOrNull()

        if (pickupField == null || destField == null) return null

        for (regex in multiSlotRegexes) {
            val match = regex.find(transcript)
            if (match != null && match.groupValues.size >= 3) {
                val rawPickup = match.groupValues[1].trim()
                val rawDest = match.groupValues[2].trim()

                if (rawPickup.isNotBlank() && rawDest.isNotBlank()) {
                    val pickupCtrl = registry.current(pickupField.id)
                    val destCtrl = registry.current(destField.id)
                    if (pickupCtrl == null || destCtrl == null || pickupCtrl.writeText == null || destCtrl.writeText == null ||
                        !pickupCtrl.snapshot.isActionable || !destCtrl.snapshot.isActionable ||
                        pickupCtrl.snapshot.generation != pickupField.generation || destCtrl.snapshot.generation != destField.generation) {
                        return VoiceBindingResult.StaleHumanEditRace(pickupField.id)
                    }
                    if ((pickupCtrl.snapshot.sensitivity == AgentUiSensitivity.SECRET || pickupCtrl.snapshot.role == AgentTextFieldRole.SECRET)) return VoiceBindingResult.DeniedSecretField(pickupField.id)
                    if ((destCtrl.snapshot.sensitivity == AgentUiSensitivity.SECRET || destCtrl.snapshot.role == AgentTextFieldRole.SECRET)) return VoiceBindingResult.DeniedSecretField(destField.id)

                    withContext(Dispatchers.Main.immediate) {
                        pickupCtrl?.writeText?.invoke(rawPickup)
                        destCtrl?.writeText?.invoke(rawDest)
                        pickupCtrl?.commitText?.invoke()
                        destCtrl?.commitText?.invoke()
                    }

                    return VoiceBindingResult.MultiSlotApplied(
                        mapOf(
                            pickupField.id to rawPickup,
                            destField.id to rawDest
                        )
                    )
                }
            }
        }
        return null
    }

    private fun resolveTargetField(
        text: String,
        fields: List<AgentUiControlSnapshot>,
    ): AgentUiControlSnapshot? {
        val norm = UiTextNormalizer.normalize(text)

        // A duplicate label does not authorize writing to an arbitrary first field.
        val named = fields.filter { field ->
            val label = UiTextNormalizer.normalize(field.label)
            norm == label || norm.startsWith("en $label ") || norm.startsWith("para $label ") ||
                norm.startsWith("$label ") || norm.startsWith("escribe en $label ")
        }
        if (named.isNotEmpty()) return named.singleOrNull()

        // Por rol semántico
        if (norm.contains("recogida") || norm.contains("origen") || norm.contains("recogeme")) {
            val pField = fields.singleOrNull { it.role == AgentTextFieldRole.PICKUP_ADDRESS }
            if (pField != null) return pField
        }
        if (norm.contains("destino") || norm.contains("voy a") || norm.contains("llevame") || norm.contains("hacia")) {
            val dField = fields.singleOrNull { it.role == AgentTextFieldRole.DESTINATION_ADDRESS }
            if (dField != null) return dField
        }
        if (norm.contains("buscar") || norm.contains("busca")) {
            val sField = fields.singleOrNull { it.role == AgentTextFieldRole.SEARCH || it.kind == AgentUiControlKind.SEARCH_FIELD }
            if (sField != null) return sField
        }
        if (norm.startsWith("mensaje ") || norm.startsWith("escribe un mensaje ") || norm.startsWith("escribe en mensaje ")) {
            fields.singleOrNull { it.role == AgentTextFieldRole.MESSAGE }?.let { return it }
        }

        // Conversational speech is dictation only when a human selected a field.
        return fields.filter { it.focused }.singleOrNull()
    }

    private fun cleanSpokenPrefix(text: String, field: AgentUiControlSnapshot): String {
        var clean = text
        val label = field.label
        val prefixes = listOf(
            "escribe en $label", "escribe un mensaje", "en $label", "para $label", "$label:", "$label ",
            "recogida en", "recogida", "destino a", "destino", "buscar", "busca"
        )
        for (p in prefixes) {
            if (clean.startsWith(p, ignoreCase = true)) {
                clean = clean.substring(p.length).trim()
                break
            }
        }
        return clean.trim().removePrefix(":").trim()
    }
}
