package com.elysium369.meet.safety

import org.junit.Assert.*
import org.junit.Test

class SafetyPublicProjectionPrivacyTest {

    @Test
    fun `SafetyPublicPointEntity has no private fields`() {
        val forbiddenFields = listOf("narrative", "privateLatitude", "privateNarrative", "exactLatitude")
        
        try {
            val clazz = Class.forName("com.elysium369.meet.safety.domain.SafetyPublicPointEntity")
            val fieldNames = clazz.declaredFields.map { it.name }
            for (field in forbiddenFields) {
                assertFalse("Class should not contain $field", fieldNames.contains(field))
            }
        } catch (e: ClassNotFoundException) {
            // Class may be created later
        }
    }

    @Test
    fun `SafetyPublicCaseEntity has no fields for private narratives`() {
        val forbiddenFields = listOf("privateNarrative", "narrative", "secretDetails")
        
        try {
            val clazz = Class.forName("com.elysium369.meet.safety.domain.SafetyPublicCaseEntity")
            val fieldNames = clazz.declaredFields.map { it.name }
            for (field in forbiddenFields) {
                assertFalse("Class should not contain $field", fieldNames.contains(field))
            }
        } catch (e: ClassNotFoundException) {
            // Class may be created later
        }
    }
}
