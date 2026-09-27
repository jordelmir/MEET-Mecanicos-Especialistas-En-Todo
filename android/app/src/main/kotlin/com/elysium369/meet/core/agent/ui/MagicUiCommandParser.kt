package com.elysium369.meet.core.agent.ui

/**
 * Comandos mágicos de interfaz de usuario de máxima prioridad.
 * Se ejecutan en un fast-path determinista antes de cualquier inferencia LLM.
 */
sealed interface MagicUiCommand {
    /**
     * "SELECCIONA <nombre del botón o control visible>"
     * Busca únicamente controles actualmente visibles en la pantalla activa.
     */
    data class Select(val targetText: String) : MagicUiCommand

    /**
     * "SECCIÓN <nombre de sección o botón>"
     * Busca primero controles visibles, y si no existen, consulta NavigationCatalog.
     */
    data class Section(val targetText: String) : MagicUiCommand
}

object MagicUiCommandParser {

    // Partial speech is not dictation when it starts an interface command.
    private val commandPrefix = Regex(
        """^(?:por favor[,\s]+)?(?:selecciona|seleccione|seleccionar|toca|toque|presiona|presione|click|haz clic|entra|ingresa|ir|vamos|abre|secci[oó]n|no\s+(?:selecciones?|toques?|presiones?))(?:\s|$)""",
        RegexOption.IGNORE_CASE
    )
    fun isCommandLike(input: String): Boolean = commandPrefix.containsMatchIn(input.trim())

    private val selectRegex = Regex(
        """^(?:selecciona|seleccione|seleccionar|toca|toque|presiona|presione|click en|haz clic en)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val sectionRegex = Regex(
        """^(?:(?:entra|ingresa|ir|vamos|abre)\s+(?:a(?:\s+la)?|en(?:\s+la)?|la)?\s*secci[oó]n|secci[oó]n)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    // Regex para evitar falsos positivos con negaciones ("no selecciones", "no toques")
    private val negationRegex = Regex(
        """^no\s+(?:selecciones?|selecciona|toques?|toca|presiones?|presiona|entres?|abras?|ingreses?|vayas|navegues?)\b""",
        RegexOption.IGNORE_CASE
    )
    fun isNegatedCommand(input: String): Boolean = negationRegex.containsMatchIn(input.trim())

    fun parse(input: String): MagicUiCommand? {
        val clean = input.trim().replace(Regex("""^por favor[,\s]+""", RegexOption.IGNORE_CASE), "")
        if (clean.isBlank()) return null
        if (negationRegex.containsMatchIn(clean)) return null

        selectRegex.matchEntire(clean)?.let { match ->
            val target = match.groupValues[1].trim().replace(
                Regex("""^(?:(?:el|la)\s+)?(?:bot[oó]n|opci[oó]n)\s+""", RegexOption.IGNORE_CASE), "")
            if (target.isNotBlank()) {
                return MagicUiCommand.Select(target)
            }
        }

        sectionRegex.matchEntire(clean)?.let { match ->
            val target = match.groupValues[1].trim()
            if (target.isNotBlank()) {
                return MagicUiCommand.Section(target)
            }
        }

        return null
    }
}
