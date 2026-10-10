package com.elysium369.meet.education.data

import com.elysium369.meet.education.domain.EpistemicTruthState

enum class CurriculumTrack(
    val displayName: String,
    val cycleName: String,
    val gradeNumber: Int,
    val subjectName: String
) {
    // ── I CICLO (1.º, 2.º, 3.º Primaria) ───────────────────────────────────
    MATEMATICA_1("1.º Matemática", "I Ciclo", 1, "MATEMATICA"),
    MATEMATICA_2("2.º Matemática", "I Ciclo", 2, "MATEMATICA"),
    MATEMATICA_3("3.º Matemática", "I Ciclo", 3, "MATEMATICA"),
    CIENCIAS_PRIMARIA("Ciencias Primaria", "I y II Ciclos", 1, "CIENCIAS"),
    ESPANOL_PRIMARIA("Español Primaria", "I y II Ciclos", 1, "ESPANOL"),
    ESTUDIOS_SOCIALES_PRIMARIA("Estudios Sociales Primaria", "I y II Ciclos", 1, "ESTUDIOS_SOCIALES"),
    INGLES_PRIMARIA("Inglés Primaria", "I y II Ciclos", 1, "INGLES"),

    // ── II CICLO (4.º, 5.º, 6.º Primaria) ──────────────────────────────────
    MATEMATICA_4("4.º Matemática", "II Ciclo", 4, "MATEMATICA"),
    MATEMATICA_5("5.º Matemática", "II Ciclo", 5, "MATEMATICA"),
    MATEMATICA_6("6.º Matemática", "II Ciclo", 6, "MATEMATICA"),

    // ── III CICLO (7.º, 8.º, 9.º Secundaria / EGB) ─────────────────────────
    MATEMATICA_7("7.º Matemática (Zapandí)", "III Ciclo", 7, "MATEMATICA"),
    FONTANERIA_7("7.º Fontanería (Artes Ind.)", "III Ciclo", 7, "ARTES_INDUSTRIALES"),
    ESPANOL_7("7.º Español", "III Ciclo", 7, "ESPANOL"),
    CIENCIAS_7("7.º Ciencias", "III Ciclo", 7, "CIENCIAS"),
    ESTUDIOS_SOCIALES_7("7.º Estudios Sociales", "III Ciclo", 7, "ESTUDIOS_SOCIALES"),
    CIVICA_7("7.º Educación Cívica", "III Ciclo", 7, "EDUCACION_CIVICA"),
    INGLES_7("7.º Inglés", "III Ciclo", 7, "INGLES"),

    MATEMATICA_8("8.º Matemática (Ujarrás)", "III Ciclo", 8, "MATEMATICA"),
    DIBUJO_TECNICO_8("8.º Dibujo Técnico (CAD)", "III Ciclo", 8, "ARTES_INDUSTRIALES"),
    ESPANOL_8("8.º Español", "III Ciclo", 8, "ESPANOL"),
    CIENCIAS_8("8.º Ciencias", "III Ciclo", 8, "CIENCIAS"),
    ESTUDIOS_SOCIALES_8("8.º Estudios Sociales", "III Ciclo", 8, "ESTUDIOS_SOCIALES"),
    CIVICA_8("8.º Educación Cívica", "III Ciclo", 8, "EDUCACION_CIVICA"),
    INGLES_8("8.º Inglés", "III Ciclo", 8, "INGLES"),

    MATEMATICA_9("9.º Matemática (Tárcoles)", "III Ciclo", 9, "MATEMATICA"),
    ELECTRICIDAD_9("9.º Electricidad Residencial", "III Ciclo", 9, "ARTES_INDUSTRIALES"),
    ESPANOL_9("9.º Español", "III Ciclo", 9, "ESPANOL"),
    CIENCIAS_9("9.º Ciencias", "III Ciclo", 9, "CIENCIAS"),
    ESTUDIOS_SOCIALES_9("9.º Estudios Sociales", "III Ciclo", 9, "ESTUDIOS_SOCIALES"),
    CIVICA_9("9.º Educación Cívica", "III Ciclo", 9, "EDUCACION_CIVICA"),
    INGLES_9("9.º Inglés", "III Ciclo", 9, "INGLES"),
    CIENCIAS_III_CICLO("Ciencias III Ciclo", "III Ciclo", 7, "CIENCIAS"),

    // ── DIVERSIFICADA & BACHILLERATO POR MADUREZ (10.º - 11.º / BxM) ───────
    MATEMATICA_BXM("Matemática (BxM 10.º-11.º)", "Diversificada", 11, "MATEMATICA"),
    ESPANOL_BXM("Español (BxM 10.º-11.º)", "Diversificada", 11, "ESPANOL"),
    BIOLOGIA_BXM("Biología (BxM 10.º-11.º)", "Diversificada", 10, "BIOLOGIA"),
    QUIMICA_BXM("Química (BxM 10.º-11.º)", "Diversificada", 11, "QUIMICA"),
    FISICA_BXM("Física (BxM 10.º-11.º)", "Diversificada", 11, "FISICA"),
    SOCIALES_BXM("Estudios Sociales (BxM)", "Diversificada", 11, "ESTUDIOS_SOCIALES"),
    CIVICA_BXM("Educación Cívica (BxM)", "Diversificada", 11, "EDUCACION_CIVICA"),
    INGLES_BXM("Inglés (BxM 10.º-11.º)", "Diversificada", 11, "INGLES");

    val isPrimary: Boolean get() = gradeNumber in 1..6
    val isSecondaryBasic: Boolean get() = gradeNumber in 7..9
    val isDiversifiedOrAdult: Boolean get() = gradeNumber >= 10
}

object NationalCurriculumCatalogSeed {

    fun getUnitsForTrack(track: CurriculumTrack): List<CourseUnitData> {
        return when (track) {
            CurriculumTrack.MATEMATICA_1 -> CourseZeroCurriculumSeed.MATEMATICA_1_UNITS
            CurriculumTrack.FONTANERIA_7 -> CourseZeroCurriculumSeed.FONTANERIA_7_UNITS

            // I y II Ciclos (Primaria)
            CurriculumTrack.MATEMATICA_2 -> PrimaryCurriculumSeed.MATEMATICA_2_UNITS
            CurriculumTrack.MATEMATICA_3 -> PrimaryCurriculumSeed.MATEMATICA_3_UNITS
            CurriculumTrack.MATEMATICA_4 -> PrimaryCurriculumSeed.MATEMATICA_4_UNITS
            CurriculumTrack.MATEMATICA_5 -> PrimaryCurriculumSeed.MATEMATICA_5_UNITS
            CurriculumTrack.MATEMATICA_6 -> PrimaryCurriculumSeed.MATEMATICA_6_UNITS
            CurriculumTrack.CIENCIAS_PRIMARIA -> PrimaryCurriculumSeed.CIENCIAS_PRIMARIA_UNITS
            CurriculumTrack.ESPANOL_PRIMARIA -> PrimaryCurriculumSeed.ESPANOL_PRIMARIA_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_PRIMARIA -> PrimaryCurriculumSeed.ESTUDIOS_SOCIALES_PRIMARIA_UNITS
            CurriculumTrack.INGLES_PRIMARIA -> PrimaryCurriculumSeed.INGLES_PRIMARIA_UNITS

            // 7.º Año (Zapandí / III Ciclo)
            CurriculumTrack.MATEMATICA_7 -> SecondaryGrade7CurriculumSeed.MATEMATICA_7_UNITS
            CurriculumTrack.ESPANOL_7 -> SecondaryGrade7CurriculumSeed.ESPANOL_7_UNITS
            CurriculumTrack.CIENCIAS_7 -> SecondaryGrade7CurriculumSeed.CIENCIAS_7_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_7 -> SecondaryGrade7CurriculumSeed.ESTUDIOS_SOCIALES_7_UNITS
            CurriculumTrack.CIVICA_7 -> SecondaryGrade7CurriculumSeed.CIVICA_7_UNITS
            CurriculumTrack.INGLES_7 -> SecondaryGrade7CurriculumSeed.INGLES_7_UNITS
            CurriculumTrack.CIENCIAS_III_CICLO -> SecondaryGrade7CurriculumSeed.CIENCIAS_III_CICLO_UNITS

            // 8.º Año (Ujarrás / III Ciclo)
            CurriculumTrack.MATEMATICA_8 -> SecondaryGrade8CurriculumSeed.MATEMATICA_8_UNITS
            CurriculumTrack.DIBUJO_TECNICO_8 -> SecondaryGrade8CurriculumSeed.DIBUJO_TECNICO_8_UNITS
            CurriculumTrack.ESPANOL_8 -> SecondaryGrade8CurriculumSeed.ESPANOL_8_UNITS
            CurriculumTrack.CIENCIAS_8 -> SecondaryGrade8CurriculumSeed.CIENCIAS_8_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_8 -> SecondaryGrade8CurriculumSeed.ESTUDIOS_SOCIALES_8_UNITS
            CurriculumTrack.CIVICA_8 -> SecondaryGrade8CurriculumSeed.CIVICA_8_UNITS
            CurriculumTrack.INGLES_8 -> SecondaryGrade8CurriculumSeed.INGLES_8_UNITS

            // 9.º Año (Tárcoles / III Ciclo)
            CurriculumTrack.MATEMATICA_9 -> SecondaryGrade9CurriculumSeed.MATEMATICA_9_UNITS
            CurriculumTrack.ELECTRICIDAD_9 -> SecondaryGrade9CurriculumSeed.ELECTRICIDAD_9_UNITS
            CurriculumTrack.ESPANOL_9 -> SecondaryGrade9CurriculumSeed.ESPANOL_9_UNITS
            CurriculumTrack.CIENCIAS_9 -> SecondaryGrade9CurriculumSeed.CIENCIAS_9_UNITS
            CurriculumTrack.ESTUDIOS_SOCIALES_9 -> SecondaryGrade9CurriculumSeed.ESTUDIOS_SOCIALES_9_UNITS
            CurriculumTrack.CIVICA_9 -> SecondaryGrade9CurriculumSeed.CIVICA_9_UNITS
            CurriculumTrack.INGLES_9 -> SecondaryGrade9CurriculumSeed.INGLES_9_UNITS

            // Diversificada y Bachillerato por Madurez (BxM 10.º - 11.º)
            CurriculumTrack.MATEMATICA_BXM -> DiversifiedCurriculumSeed.MATEMATICA_BXM_UNITS
            CurriculumTrack.ESPANOL_BXM -> DiversifiedCurriculumSeed.ESPANOL_BXM_UNITS
            CurriculumTrack.BIOLOGIA_BXM -> DiversifiedCurriculumSeed.BIOLOGIA_BXM_UNITS
            CurriculumTrack.QUIMICA_BXM -> DiversifiedCurriculumSeed.QUIMICA_BXM_UNITS
            CurriculumTrack.FISICA_BXM -> DiversifiedCurriculumSeed.FISICA_BXM_UNITS
            CurriculumTrack.SOCIALES_BXM -> DiversifiedCurriculumSeed.SOCIALES_BXM_UNITS
            CurriculumTrack.CIVICA_BXM -> DiversifiedCurriculumSeed.CIVICA_BXM_UNITS
            CurriculumTrack.INGLES_BXM -> DiversifiedCurriculumSeed.INGLES_BXM_UNITS
        }
    }

    val ALL_UNITS: List<CourseUnitData> by lazy {
        CurriculumTrack.values().flatMap { getUnitsForTrack(it) }
    }

    // ── Aliases de compatibilidad directa ────────────────────────────────────
    val MATEMATICA_2_UNITS get() = PrimaryCurriculumSeed.MATEMATICA_2_UNITS
    val MATEMATICA_3_UNITS get() = PrimaryCurriculumSeed.MATEMATICA_3_UNITS
    val MATEMATICA_4_UNITS get() = PrimaryCurriculumSeed.MATEMATICA_4_UNITS
    val MATEMATICA_5_UNITS get() = PrimaryCurriculumSeed.MATEMATICA_5_UNITS
    val MATEMATICA_6_UNITS get() = PrimaryCurriculumSeed.MATEMATICA_6_UNITS
    val CIENCIAS_PRIMARIA_UNITS get() = PrimaryCurriculumSeed.CIENCIAS_PRIMARIA_UNITS
    val ESPANOL_PRIMARIA_UNITS get() = PrimaryCurriculumSeed.ESPANOL_PRIMARIA_UNITS
    val ESTUDIOS_SOCIALES_PRIMARIA_UNITS get() = PrimaryCurriculumSeed.ESTUDIOS_SOCIALES_PRIMARIA_UNITS
    val INGLES_PRIMARIA_UNITS get() = PrimaryCurriculumSeed.INGLES_PRIMARIA_UNITS

    val MATEMATICA_7_UNITS get() = SecondaryGrade7CurriculumSeed.MATEMATICA_7_UNITS
    val ESPANOL_7_UNITS get() = SecondaryGrade7CurriculumSeed.ESPANOL_7_UNITS
    val CIENCIAS_7_UNITS get() = SecondaryGrade7CurriculumSeed.CIENCIAS_7_UNITS
    val ESTUDIOS_SOCIALES_7_UNITS get() = SecondaryGrade7CurriculumSeed.ESTUDIOS_SOCIALES_7_UNITS
    val CIVICA_7_UNITS get() = SecondaryGrade7CurriculumSeed.CIVICA_7_UNITS
    val INGLES_7_UNITS get() = SecondaryGrade7CurriculumSeed.INGLES_7_UNITS
    val CIENCIAS_III_CICLO_UNITS get() = SecondaryGrade7CurriculumSeed.CIENCIAS_III_CICLO_UNITS

    val MATEMATICA_8_UNITS get() = SecondaryGrade8CurriculumSeed.MATEMATICA_8_UNITS
    val DIBUJO_TECNICO_8_UNITS get() = SecondaryGrade8CurriculumSeed.DIBUJO_TECNICO_8_UNITS
    val ESPANOL_8_UNITS get() = SecondaryGrade8CurriculumSeed.ESPANOL_8_UNITS
    val CIENCIAS_8_UNITS get() = SecondaryGrade8CurriculumSeed.CIENCIAS_8_UNITS
    val ESTUDIOS_SOCIALES_8_UNITS get() = SecondaryGrade8CurriculumSeed.ESTUDIOS_SOCIALES_8_UNITS
    val CIVICA_8_UNITS get() = SecondaryGrade8CurriculumSeed.CIVICA_8_UNITS
    val INGLES_8_UNITS get() = SecondaryGrade8CurriculumSeed.INGLES_8_UNITS

    val MATEMATICA_9_UNITS get() = SecondaryGrade9CurriculumSeed.MATEMATICA_9_UNITS
    val ELECTRICIDAD_9_UNITS get() = SecondaryGrade9CurriculumSeed.ELECTRICIDAD_9_UNITS
    val ESPANOL_9_UNITS get() = SecondaryGrade9CurriculumSeed.ESPANOL_9_UNITS
    val CIENCIAS_9_UNITS get() = SecondaryGrade9CurriculumSeed.CIENCIAS_9_UNITS
    val ESTUDIOS_SOCIALES_9_UNITS get() = SecondaryGrade9CurriculumSeed.ESTUDIOS_SOCIALES_9_UNITS
    val CIVICA_9_UNITS get() = SecondaryGrade9CurriculumSeed.CIVICA_9_UNITS
    val INGLES_9_UNITS get() = SecondaryGrade9CurriculumSeed.INGLES_9_UNITS

    val MATEMATICA_BXM_UNITS get() = DiversifiedCurriculumSeed.MATEMATICA_BXM_UNITS
    val ESPANOL_BXM_UNITS get() = DiversifiedCurriculumSeed.ESPANOL_BXM_UNITS
    val BIOLOGIA_BXM_UNITS get() = DiversifiedCurriculumSeed.BIOLOGIA_BXM_UNITS
    val QUIMICA_BXM_UNITS get() = DiversifiedCurriculumSeed.QUIMICA_BXM_UNITS
    val FISICA_BXM_UNITS get() = DiversifiedCurriculumSeed.FISICA_BXM_UNITS
    val SOCIALES_BXM_UNITS get() = DiversifiedCurriculumSeed.SOCIALES_BXM_UNITS
    val CIVICA_BXM_UNITS get() = DiversifiedCurriculumSeed.CIVICA_BXM_UNITS
    val INGLES_BXM_UNITS get() = DiversifiedCurriculumSeed.INGLES_BXM_UNITS
}
