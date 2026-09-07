package com.elysium369.meet.education.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocraticSpeechPragmaticsEvaluatorTest {

    @Test
    fun `detects negative adverbial inversion and awards C1 points`() {
        val utterance = "Under no circumstances should the technician probe the CAN-bus wire with a test light."
        val result = SocraticSpeechPragmaticsEvaluator.evaluate(utterance)

        assertTrue("Inversion detected", result.hasNegativeInversion)
        assertTrue(result.detectedPatterns.any { it.contains("Negative Adverbial Inversion") })
        assertTrue(result.masteryScore >= 30.0)
    }

    @Test
    fun `detects cleft sentences for technical emphasis`() {
        val utteranceIt = "It was the corroded ground terminal that caused the voltage drop across the circuit."
        val resultIt = SocraticSpeechPragmaticsEvaluator.evaluate(utteranceIt)
        assertTrue("It-cleft detected", resultIt.hasCleftSentence)

        val utteranceWhat = "What the oscilloscope revealed was an excessive noise ripple from the alternator diode."
        val resultWhat = SocraticSpeechPragmaticsEvaluator.evaluate(utteranceWhat)
        assertTrue("Wh-cleft detected", resultWhat.hasCleftSentence)
    }

    @Test
    fun `detects mixed conditionals in root cause failure analysis`() {
        val utterance = "If the relay had failed open, the fuel pump would not be operating right now."
        val result = SocraticSpeechPragmaticsEvaluator.evaluate(utterance)

        assertTrue("Mixed conditional detected", result.hasMixedConditional)
    }

    @Test
    fun `hedging vs dogmatic scoring`() {
        val hedged = "The sensor records indicate that the downstream catalytic efficiency has degraded under these test conditions."
        val resultHedged = SocraticSpeechPragmaticsEvaluator.evaluate(hedged)
        assertTrue(resultHedged.hedgingScore > 0.0)
        assertFalse(resultHedged.detectedPatterns.any { it.contains("Dogmatic") })

        val dogmatic = "The oxygen sensor is definitely 100% obviously broken and certainly dead."
        val resultDogmatic = SocraticSpeechPragmaticsEvaluator.evaluate(dogmatic)
        assertTrue(resultDogmatic.hedgingScore < 0.0)
        assertTrue(resultDogmatic.detectedPatterns.any { it.contains("Dogmatic") })
    }

    @Test
    fun `full C1 technical utterance achieves C1 CEFR rating`() {
        val utterance = "Under no circumstances should we replace the catalytic converter prematurely. " +
            "It was the air-fuel ratio sensor that triggered P0171, and the empirical evidence suggests " +
            "that if the intake gasket had been inspected earlier, the vehicle would not suffer from lean surge now."

        val result = SocraticSpeechPragmaticsEvaluator.evaluate(utterance)

        assertTrue("Has negative inversion", result.hasNegativeInversion)
        assertTrue("Has cleft sentence", result.hasCleftSentence)
        assertTrue("Has mixed conditional", result.hasMixedConditional)
        assertTrue("Has positive hedging", result.hedgingScore > 0.0)
        assertEquals(SocraticSpeechPragmaticsEvaluator.CefrLevel.C1, result.assessedCefrLevel)
        assertTrue(result.masteryScore >= 75.0)
        assertTrue(result.socraticFeedback.contains("Outstanding forensic articulation"))
    }

    @Test
    fun `phonological guidance highlights elision and assimilation`() {
        val text = "We conducted the first test and verified the ground wire, did you confirm the next step?"
        val result = SocraticSpeechPragmaticsEvaluator.evaluate(text)

        assertTrue(result.phonologicalGuidance.any { it.contains("Elision of /t/") })
        assertTrue(result.phonologicalGuidance.any { it.contains("Elision of /d/") })
        assertTrue(result.phonologicalGuidance.any { it.contains("Assimilation") })
    }
}
