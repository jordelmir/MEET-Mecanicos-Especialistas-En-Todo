package dragoncore.chemistry

import java.util.regex.Pattern

/**
 * Elysium Vanguard — ChemParser
 *
 * Capaz de descomponer fórmulas químicas en sus componentes atómicos
 * para balanceo estequiométrico simbólico.
 */
class ChemParser {

    // Regex para elementos (Ej: Fe, O, Na, Mg)
    private val elementPattern = Pattern.compile("([A-Z][a-z]?)(\\d*)")
    // Regex para grupos entre paréntesis (Ej: (OH)2)
    private val groupPattern = Pattern.compile("\\((.*)\\)(\\d+)")

    /**
     * Parsea una molécula y devuelve un mapa de Elemento -> Cantidad.
     */
    fun parseFormula(formula: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()

        // Primero resolvemos grupos (recursividad simple)
        val matcherGroup = groupPattern.matcher(formula)
        var refinedFormula = formula
        while (matcherGroup.find()) {
            val content = matcherGroup.group(1)
            val multiplier = matcherGroup.group(2).toInt()
            val subCounts = parseFormula(content ?: "")
            subCounts.forEach { (el, count) ->
                counts[el] = (counts[el] ?: 0) + (count * multiplier)
            }
            refinedFormula = refinedFormula.replace(matcherGroup.group(0), "")
        }

        // Luego procesamos elementos directos
        val matcherElement = elementPattern.matcher(refinedFormula)
        while (matcherElement.find()) {
            val symbol = matcherElement.group(1)
            val countStr = matcherElement.group(2)
            val count = if (countStr.isNullOrEmpty()) 1 else countStr.toInt()
            if (symbol != null) {
                counts[symbol] = (counts[symbol] ?: 0) + count
            }
        }

        return counts
    }
}
