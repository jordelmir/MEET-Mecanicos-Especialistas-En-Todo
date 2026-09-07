package com.elysium369.meet.education.ai

/**
 * Evaluator for C1 English Pragmatics, Advanced Syntax, and Forensic Telemetry.
 *
 * Designed for advanced technical engineering tracks (e.g. BxM Inglés C1,
 * Automotive Diagnostics & Engineering Affidavits).
 *
 * Assesses:
 * 1. Negative Adverbial Inversion (*Under no circumstances should...*, *Seldom does...*)
 * 2. Cleft Sentences for focus and emphasis (*It was the grounding strap that caused...*)
 * 3. Mixed Conditionals in failure analysis (*If the fuse had blown earlier, the ECU would not be damaged now*)
 * 4. Epistemic Modal Hedging vs. dogmatic unproven assertions
 * 5. Connected speech phonological patterns (alveolar plosive elision, yod-coalescence)
 */
object SocraticSpeechPragmaticsEvaluator {

    enum class CefrLevel {
        PRE_A1, A1, A2, B1, B2, C1, C2
    }

    data class PragmaticEvaluationResult(
        val inputUtterance: String,
        val assessedCefrLevel: CefrLevel,
        val masteryScore: Double,
        val hasNegativeInversion: Boolean,
        val hasCleftSentence: Boolean,
        val hasMixedConditional: Boolean,
        val hedgingScore: Double, // -1.0 (dogmatic) to +1.0 (well-hedged forensic)
        val detectedPatterns: List<String>,
        val phonologicalGuidance: List<String>,
        val socraticFeedback: String
    )

    private val NEGATIVE_INVERSION_REGEX = Regex(
        """(?i)\b(under no circumstances|on no account|seldom|rarely|hardly|scarcely|little|in no way|no sooner|never before|at no time)\s+(should|must|did|does|do|can|could|would|had|have|has|is|are|was|were)\s+([a-z]+)""",
        RegexOption.IGNORE_CASE
    )

    private val CLEFT_SENTENCE_IT_REGEX = Regex(
        """(?i)\bit\s+(was|is)\s+([^,]+?)\s+that\s+""",
        RegexOption.IGNORE_CASE
    )

    private val CLEFT_SENTENCE_WHAT_REGEX = Regex(
        """(?i)\bwhat\s+([^,]+?)\s+(was|is|revealed|demonstrated|showed)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val MIXED_CONDITIONAL_PAST_PRESENT_REGEX = Regex(
        """(?i)\bif\s+.*?\s+had\s+([a-z]+ed|[a-z]+en|been).*?,\s+.*?\s+(would|could|might)\s+(not\s+)?(be|exhibit|suffer|remain)\b""",
        RegexOption.IGNORE_CASE
    )

    private val MIXED_CONDITIONAL_PRESENT_PAST_REGEX = Regex(
        """(?i)\bif\s+.*?\s+(were|had|understood).*?,\s+.*?\s+(would|could|might)\s+(not\s+)?have\s+([a-z]+ed|[a-z]+en|been)\b""",
        RegexOption.IGNORE_CASE
    )

    private val HEDGING_MARKERS = listOf(
        "the empirical evidence suggests",
        "the sensor records indicate",
        "it is plausible that",
        "the data demonstrates that",
        "might have contributed",
        "appears to correlate with",
        "can tentatively be attributed to",
        "preliminary telemetry suggests",
        "under these test conditions"
    )

    private val DOGMATIC_MARKERS = listOf(
        "definitely 100%",
        "obviously broken",
        "without question the fault of",
        "certainly dead",
        "undoubtedly caused by",
        "always fails because",
        "guaranteed to be"
    )

    /**
     * Evaluates a spoken transcript or written engineering diagnosis.
     */
    fun evaluate(utterance: String): PragmaticEvaluationResult {
        val trimmed = utterance.trim()
        val detected = mutableListOf<String>()

        // 1. Inversion Check
        val hasInversion = NEGATIVE_INVERSION_REGEX.containsMatchIn(trimmed)
        if (hasInversion) {
            val match = NEGATIVE_INVERSION_REGEX.find(trimmed)?.value
            detected.add("Negative Adverbial Inversion: '$match'")
        }

        // 2. Cleft Sentences Check
        val hasItCleft = CLEFT_SENTENCE_IT_REGEX.containsMatchIn(trimmed)
        val hasWhatCleft = CLEFT_SENTENCE_WHAT_REGEX.containsMatchIn(trimmed)
        val hasCleft = hasItCleft || hasWhatCleft
        if (hasItCleft) {
            detected.add("It-Cleft Focus Construction: '${CLEFT_SENTENCE_IT_REGEX.find(trimmed)?.value?.trim()}'")
        }
        if (hasWhatCleft) {
            detected.add("Wh-Cleft Construction: '${CLEFT_SENTENCE_WHAT_REGEX.find(trimmed)?.value?.trim()}'")
        }

        // 3. Mixed Conditionals Check
        val hasMixed1 = MIXED_CONDITIONAL_PAST_PRESENT_REGEX.containsMatchIn(trimmed)
        val hasMixed2 = MIXED_CONDITIONAL_PRESENT_PAST_REGEX.containsMatchIn(trimmed)
        val hasMixed = hasMixed1 || hasMixed2
        if (hasMixed) {
            detected.add("Mixed Conditional Causal Structure")
        }

        // 4. Modal Hedging Calculation
        var hedgeCount = 0
        var dogmaticCount = 0
        val lower = trimmed.lowercase()

        HEDGING_MARKERS.forEach { marker ->
            if (lower.contains(marker)) {
                hedgeCount++
                detected.add("Forensic Modal Hedging: '$marker'")
            }
        }
        DOGMATIC_MARKERS.forEach { marker ->
            if (lower.contains(marker)) {
                dogmaticCount++
                detected.add("Dogmatic Unverified Assertion: '$marker'")
            }
        }

        val hedgingScore = when {
            hedgeCount > 0 && dogmaticCount == 0 -> (0.5 + (hedgeCount * 0.25)).coerceAtMost(1.0)
            dogmaticCount > 0 && hedgeCount == 0 -> (-0.5 - (dogmaticCount * 0.25)).coerceAtLeast(-1.0)
            hedgeCount > dogmaticCount -> 0.3
            else -> 0.0
        }

        // 5. Phonological guidance based on consonant clusters in text
        val phonologyGuidance = auditPhonologicalOpportunities(trimmed)

        // 6. Score & CEFR Level Determination
        var points = 0.0
        if (hasInversion) points += 30.0
        if (hasCleft) points += 25.0
        if (hasMixed) points += 25.0
        if (hedgingScore > 0.0) points += 20.0 * hedgingScore

        val cefr = when {
            points >= 75.0 -> CefrLevel.C1
            points >= 50.0 -> CefrLevel.B2
            points >= 25.0 -> CefrLevel.B1
            trimmed.split(" ").size >= 5 -> CefrLevel.A2
            else -> CefrLevel.A1
        }

        // 7. Socratic Feedback
        val feedback = buildString {
            if (cefr == CefrLevel.C1) {
                append("Outstanding forensic articulation! You demonstrated mastery of sophisticated C1 structures. ")
                if (hasInversion) append("Your negative adverbial inversion added commanding rhetorical emphasis. ")
                if (hedgingScore > 0.0) append("Your epistemic hedging complies with ISO/IEC forensic affidavit standards.")
            } else {
                append("Good technical explanation. To elevate your discourse to C1 engineering standard: ")
                if (!hasInversion) append("Consider employing a negative adverbial inversion (e.g., 'Under no circumstances should the wiring be probed...'). ")
                if (!hasCleft) append("Use a cleft structure to focus the root cause (e.g., 'What the scope revealed was an intermittent ground.'). ")
                if (dogmaticCount > 0) append("Refrain from categorical claims without physical bench-testing; prefer probabilistic hedging (e.g., 'The telemetry strongly indicates...').")
            }
        }

        return PragmaticEvaluationResult(
            inputUtterance = trimmed,
            assessedCefrLevel = cefr,
            masteryScore = points.coerceIn(0.0, 100.0),
            hasNegativeInversion = hasInversion,
            hasCleftSentence = hasCleft,
            hasMixedConditional = hasMixed,
            hedgingScore = hedgingScore,
            detectedPatterns = detected,
            phonologicalGuidance = phonologyGuidance,
            socraticFeedback = feedback
        )
    }

    private fun auditPhonologicalOpportunities(text: String): List<String> {
        val tips = mutableListOf<String>()
        val lower = text.lowercase()

        // Plosive elision
        if (lower.contains("last night") || lower.contains("next step") || lower.contains("first test") || lower.contains("boost pressure")) {
            tips.add("Elision of /t/: In clusters like 'first test' or 'next step', the final /t/ is naturally elided before the following consonant in fluent connected speech.")
        }
        if (lower.contains("ground wire") || lower.contains("hold back") || lower.contains("and the")) {
            tips.add("Elision of /d/: In 'ground wire' or 'and the', elide the alveolar /d/ into the resonant consonant.")
        }

        // Yod-coalescence
        if (lower.contains("did you") || lower.contains("would you") || lower.contains("could you")) {
            tips.add("Assimilation / Yod-coalescence: In rapid speech, /d/ + /j/ coalesces into affricate /dʒ/ (e.g., 'did you' → /dɪdʒuː/).")
        }
        if (lower.contains("don't you") || lower.contains("can't you")) {
            tips.add("Assimilation: /t/ + /j/ coalesces into voiceless affricate /tʃ/ ('don't you' → /doʊntʃuː/).")
        }

        if (tips.isEmpty()) {
            tips.add("Intonation & Stress: Place contrastive pitch accent on the diagnostic operand (e.g., 'the PRIMARY sensor, not the secondary').")
        }

        return tips
    }
}
