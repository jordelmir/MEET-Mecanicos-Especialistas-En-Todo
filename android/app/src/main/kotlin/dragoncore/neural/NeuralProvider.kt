package dragoncore.neural

sealed class NeuralProvider {
    data class Gemini(
        val apiKey: String,
        val modelName: String = "gemini-1.5-flash" // Flash for speed, Pro for reasoning
    ) : NeuralProvider()

    data class OpenAI(
        val apiKey: String,
        val modelName: String = "gpt-4o-mini"
    ) : NeuralProvider()

    data class Anthropic(
        val apiKey: String,
        val modelName: String = "claude-3-5-sonnet-20240620"
    ) : NeuralProvider()

    // object Local : NeuralProvider() // Purged in Operation Clear Sky
}
