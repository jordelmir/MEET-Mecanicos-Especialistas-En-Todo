package com.elysium369.meet.core.intent

import com.elysium369.meet.core.intent.UniversalIntentRouter.ElysiumIntent
import org.junit.Assert.*
import org.junit.Test

class UniversalIntentRouterTest {

    // ─── LEARN ───

    @Test
    fun `quiero aprender soldadura classifies as LEARN`() {
        val result = UniversalIntentRouter.classify("Quiero aprender soldadura")
        assertEquals(ElysiumIntent.LEARN, result.primaryIntent)
        assertTrue(result.confidence > 0.10)
        assertEquals("WELDING", result.extractedDomain)
    }

    @Test
    fun `teach me calculus classifies as LEARN`() {
        val result = UniversalIntentRouter.classify("teach me calculus")
        assertEquals(ElysiumIntent.LEARN, result.primaryIntent)
    }

    // ─── BUY_SERVICE ───

    @Test
    fun `necesito un plomero classifies as BUY_SERVICE with PLUMBING domain`() {
        val result = UniversalIntentRouter.classify("Necesito un plomero")
        assertEquals(ElysiumIntent.BUY_SERVICE, result.primaryIntent)
        assertEquals("PLUMBING", result.extractedDomain)
    }

    @Test
    fun `se está saliendo agua classifies as BUY_SERVICE`() {
        val result = UniversalIntentRouter.classify("Se está saliendo agua de la pared")
        assertEquals(ElysiumIntent.BUY_SERVICE, result.primaryIntent)
    }

    @Test
    fun `necesito reparar los frenos classifies as BUY_SERVICE AUTOMOTIVE`() {
        val result = UniversalIntentRouter.classify("Necesito reparar los frenos del carro")
        assertEquals(ElysiumIntent.BUY_SERVICE, result.primaryIntent)
        assertEquals("AUTOMOTIVE", result.extractedDomain)
    }

    // ─── PROVIDE_SERVICE ───

    @Test
    fun `soy soldador y quiero clientes classifies as PROVIDE_SERVICE`() {
        val result = UniversalIntentRouter.classify("Soy soldador y quiero clientes")
        assertEquals(ElysiumIntent.PROVIDE_SERVICE, result.primaryIntent)
        assertEquals("WELDING", result.extractedDomain)
    }

    // ─── BUY_PRODUCT ───

    @Test
    fun `necesito comprar un repuesto classifies as BUY_PRODUCT`() {
        val result = UniversalIntentRouter.classify("Necesito comprar un repuesto para el motor")
        assertEquals(ElysiumIntent.BUY_PRODUCT, result.primaryIntent)
    }

    // ─── HIRE ───

    @Test
    fun `necesito contratar cinco soldadores classifies as HIRE`() {
        val result = UniversalIntentRouter.classify("Necesito contratar personal de soldadura")
        assertEquals(ElysiumIntent.HIRE, result.primaryIntent)
    }

    // ─── FIND_WORK ───

    @Test
    fun `necesito ganar dinero classifies as FIND_WORK`() {
        val result = UniversalIntentRouter.classify("Necesito ganar dinero hoy")
        assertEquals(ElysiumIntent.FIND_WORK, result.primaryIntent)
    }

    // ─── ECONOMIC_DISCOVERY ───

    @Test
    fun `que servicios tienen mas demanda classifies as ECONOMIC_DISCOVERY`() {
        val result = UniversalIntentRouter.classify("¿Qué servicios tienen más demanda donde vivo?")
        assertEquals(ElysiumIntent.ECONOMIC_DISCOVERY, result.primaryIntent)
    }

    // ─── LEARNING_TO_EARNING ───

    @Test
    fun `quiero aprender para trabajar classifies as LEARNING_TO_EARNING`() {
        val result = UniversalIntentRouter.classify("Quiero aprender electricidad para trabajar de eso")
        assertEquals(ElysiumIntent.LEARNING_TO_EARNING, result.primaryIntent)
    }

    // ─── DIAGNOSE ───

    @Test
    fun `necesito diagnosticar mi carro classifies as DIAGNOSE`() {
        val result = UniversalIntentRouter.classify("Necesito diagnosticar mi carro con scanner")
        assertEquals(ElysiumIntent.DIAGNOSE, result.primaryIntent)
    }

    // ─── MOVE ───

    @Test
    fun `necesito una grua classifies as MOVE`() {
        val result = UniversalIntentRouter.classify("Necesito una grúa para mi carro")
        assertEquals(ElysiumIntent.MOVE, result.primaryIntent)
    }

    // ─── UNKNOWN ───

    @Test
    fun `empty input returns UNKNOWN with zero confidence`() {
        val result = UniversalIntentRouter.classify("")
        assertEquals(ElysiumIntent.UNKNOWN, result.primaryIntent)
        assertEquals(0.0, result.confidence, 0.001)
    }

    @Test
    fun `gibberish returns UNKNOWN`() {
        val result = UniversalIntentRouter.classify("asdfghjkl qwerty")
        assertEquals(ElysiumIntent.UNKNOWN, result.primaryIntent)
        assertTrue(result.needsClarification)
    }

    // ─── Domain Detection ───

    @Test
    fun `domain detection works independently of intent`() {
        val result = UniversalIntentRouter.classify("algo con plomería")
        assertEquals("PLUMBING", result.extractedDomain)
    }

    // ─── Classification Properties ───

    @Test
    fun `ambiguous input is flagged for clarification`() {
        val result = UniversalIntentRouter.classify("xyz")
        assertTrue(result.needsClarification)
    }

    @Test
    fun `confidence never exceeds 0_98`() {
        val result = UniversalIntentRouter.classify(
            "Quiero aprender a estudiar un curso tutorial con clase y lección"
        )
        assertTrue(result.confidence <= 0.98)
    }
}
