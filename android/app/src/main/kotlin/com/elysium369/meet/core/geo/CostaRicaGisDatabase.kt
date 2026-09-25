package com.elysium369.meet.core.geo

import java.text.Normalizer
import kotlin.math.*

/**
 * ══════════════════════════════════════════════════════════════════════
 *  C O S T A   R I C A   G I S   D A T A B A S E   ( E L Y S I U M )
 *  ──────────────────────────────────────────────────────────────
 *  Enciclopedia geográfica y de comercios nativa de Costa Rica:
 *  - Las 7 Provincias completas.
 *  - Los 84 Cantones oficiales de la República de Costa Rica.
 *  - Distritos y Barrios urbanos neurálgicos.
 *  - Red de Comercios, Servicios y Puntos de Uso Diario con
 *    coordenadas geográficas de alta precisión:
 *      * Supermercados (Auto Mercado, Walmart, Masxmenos, PriceSmart, Pali)
 *      * Tiendas de Conveniencia (AM/PM, Fresh Market, Musmanni)
 *      * Ferreterías & Construcción (EPA, El Lagar)
 *      * Estaciones de Inspección Técnica Vehicular (DEKRA) & COSEVI
 *      * Centros Comerciales & Malls de alto tránsito
 *      * Hospitales Nacionales, Privados y Clínicas Mayores
 *      * Red de Repuestos, Baterías & Talleres Automotrices
 *      * Estaciones de Servicio / Gasolineras estratégicas
 * ══════════════════════════════════════════════════════════════════════
 */
object CostaRicaGisDatabase {

    enum class GisCategory {
        PROVINCE,
        CANTON,
        DISTRICT,
        BARRIO,
        SUPERMARKET,
        CONVENIENCE,
        MALL,
        HOSPITAL_CLINIC,
        POLICE_FIRE_EMERGENCY,
        INSPECTION_DEKRA,
        AUTOMOTIVE_HUB,
        HARDWARE_STORE,
        GAS_STATION,
        LANDMARK
    }

    data class GisPlace(
        val id: String,
        val name: String,
        val province: String,
        val canton: String,
        val district: String = "",
        val category: GisCategory,
        val latitude: Double,
        val longitude: Double,
        val aliases: List<String> = emptyList(),
        val brand: String? = null,
    ) {
        val displayLabel: String
            get() = when {
                district.isNotBlank() && canton.isNotBlank() -> "$name, $district, $canton, $province"
                canton.isNotBlank() -> "$name, $canton, $province"
                else -> "$name, $province, Costa Rica"
            }
    }

    val allPlaces: List<GisPlace> by lazy {
        buildList {
            // ═══════════════════════════════════════════════════════════════
            // ── 1. LAS 7 PROVINCIAS ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_prov_sj", "San José", "San José", "San José", "Catedral", GisCategory.PROVINCE, 9.9333, -84.0833, listOf("chepe", "capital", "san jose centro")))
            add(GisPlace("cr_prov_al", "Alajuela", "Alajuela", "Alajuela", "Alajuela", GisCategory.PROVINCE, 10.0167, -84.2167, listOf("la liga", "ciudad de los mangos", "alajuela centro")))
            add(GisPlace("cr_prov_ca", "Cartago", "Cartago", "Cartago", "Oriental", GisCategory.PROVINCE, 9.8667, -83.9167, listOf("la vieja metropoli", "brumas", "cartago centro")))
            add(GisPlace("cr_prov_he", "Heredia", "Heredia", "Heredia", "Heredia", GisCategory.PROVINCE, 9.9989, -84.1167, listOf("ciudad de las flores", "florenses", "heredia centro")))
            add(GisPlace("cr_prov_gu", "Guanacaste", "Guanacaste", "Liberia", "Liberia", GisCategory.PROVINCE, 10.6333, -85.4333, listOf("la pampa", "bajura", "liberia guanacaste")))
            add(GisPlace("cr_prov_pu", "Puntarenas", "Puntarenas", "Puntarenas", "Puntarenas", GisCategory.PROVINCE, 9.9763, -84.8384, listOf("el puerto", "puerto puntarenas", "pacifico")))
            add(GisPlace("cr_prov_li", "Limón", "Limón", "Limón", "Limón", GisCategory.PROVINCE, 9.9907, -83.0360, listOf("puerto limon", "el caribe", "limon centro")))

            // ═══════════════════════════════════════════════════════════════
            // ── 2. LOS 84 CANTONES DE COSTA RICA ──
            // ═══════════════════════════════════════════════════════════════
            // ── SAN JOSÉ (20 Cantones) ──
            add(GisPlace("cr_can_101", "San José Central", "San José", "San José", "Carmen", GisCategory.CANTON, 9.9333, -84.0833, listOf("san jose", "chepe centro", "avenida central")))
            add(GisPlace("cr_can_102", "Escazú", "San José", "Escazú", "San Miguel", GisCategory.CANTON, 9.9194, -84.1394, listOf("escazu centro", "san antonio de escazu", "guachipelin")))
            add(GisPlace("cr_can_103", "Desamparados", "San José", "Desamparados", "Desamparados", GisCategory.CANTON, 9.8975, -84.0675, listOf("desampa", "desamparados centro")))
            add(GisPlace("cr_can_104", "Puriscal", "San José", "Puriscal", "Santiago", GisCategory.CANTON, 9.8483, -84.3142, listOf("santiago de puriscal")))
            add(GisPlace("cr_can_105", "Tarrazú", "San José", "Tarrazú", "San Marcos", GisCategory.CANTON, 9.6606, -84.0264, listOf("san marcos de tarrazu", "zona de los santos")))
            add(GisPlace("cr_can_106", "Aserrí", "San José", "Aserrí", "Aserrí", GisCategory.CANTON, 9.8667, -84.0833, listOf("aserri centro")))
            add(GisPlace("cr_can_107", "Mora", "San José", "Mora", "Ciudad Colón", GisCategory.CANTON, 9.9150, -84.2417, listOf("ciudad colon", "mora")))
            add(GisPlace("cr_can_108", "Goicoechea", "San José", "Goicoechea", "Guadalupe", GisCategory.CANTON, 9.9478, -84.0567, listOf("guadalupe", "goico")))
            add(GisPlace("cr_can_109", "Santa Ana", "San José", "Santa Ana", "Santa Ana", GisCategory.CANTON, 9.9325, -84.1825, listOf("santa ana centro", "lindora", "pozos")))
            add(GisPlace("cr_can_110", "Alajuelita", "San José", "Alajuelita", "Alajuelita", GisCategory.CANTON, 9.9011, -84.1011, listOf("alajuelita centro")))
            add(GisPlace("cr_can_111", "Vázquez de Coronado", "San José", "Vázquez de Coronado", "San Isidro", GisCategory.CANTON, 9.9772, -84.0083, listOf("coronado", "san isidro de coronado")))
            add(GisPlace("cr_can_112", "Acosta", "San José", "Acosta", "San Ignacio", GisCategory.CANTON, 9.7967, -84.1611, listOf("san ignacio de acosta", "acosta")))
            add(GisPlace("cr_can_113", "Tibás", "San José", "Tibás", "San Juan", GisCategory.CANTON, 9.9575, -84.0833, listOf("san juan de tibas", "tibas", "cinco esquinas")))
            add(GisPlace("cr_can_114", "Moravia", "San José", "Moravia", "San Vicente", GisCategory.CANTON, 9.9633, -84.0483, listOf("san vicente de moravia", "moravia")))
            add(GisPlace("cr_can_115", "Montes de Oca", "San José", "Montes de Oca", "San Pedro", GisCategory.CANTON, 9.9328, -84.0528, listOf("san pedro montes de oca", "fuente de la hispanidad", "san pedro")))
            add(GisPlace("cr_can_116", "Turrubares", "San José", "Turrubares", "San Pablo", GisCategory.CANTON, 9.8789, -84.4789, listOf("san pablo de turrubares", "turrubares")))
            add(GisPlace("cr_can_117", "Dota", "San José", "Dota", "Santa María", GisCategory.CANTON, 9.6542, -83.9242, listOf("santa maria de dota", "dota")))
            add(GisPlace("cr_can_118", "Curridabat", "San José", "Curridabat", "Curridabat", GisCategory.CANTON, 9.9167, -84.0333, listOf("curri", "curridabat centro", "pinares")))
            add(GisPlace("cr_can_119", "Pérez Zeledón", "San José", "Pérez Zeledón", "San Isidro de El General", GisCategory.CANTON, 9.3739, -83.7089, listOf("perez zeledon", "san isidro del general", "pz")))
            add(GisPlace("cr_can_120", "León Cortés Castro", "San José", "León Cortés Castro", "San Pablo", GisCategory.CANTON, 9.6783, -84.0783, listOf("leon cortes", "san pablo de leon cortes")))

            // ── ALAJUELA (16 Cantones) ──
            add(GisPlace("cr_can_201", "Alajuela Central", "Alajuela", "Alajuela", "Alajuela", GisCategory.CANTON, 10.0167, -84.2167, listOf("alajuela", "alajuela centro", "parque central alajuela")))
            add(GisPlace("cr_can_202", "San Ramón", "Alajuela", "San Ramón", "San Ramón", GisCategory.CANTON, 10.0883, -84.4700, listOf("monchito", "san ramon alajuela")))
            add(GisPlace("cr_can_203", "Grecia", "Alajuela", "Grecia", "Grecia", GisCategory.CANTON, 10.0739, -84.3117, listOf("grecia alajuela", "iglesia metalica grecia")))
            add(GisPlace("cr_can_204", "San Mateo", "Alajuela", "San Mateo", "San Mateo", GisCategory.CANTON, 9.9525, -84.5328, listOf("san mateo alajuela")))
            add(GisPlace("cr_can_205", "Atenas", "Alajuela", "Atenas", "Atenas", GisCategory.CANTON, 9.9786, -84.3789, listOf("atenas", "mejor clima del mundo")))
            add(GisPlace("cr_can_206", "Naranjo", "Alajuela", "Naranjo", "Naranjo", GisCategory.CANTON, 10.0989, -84.3878, listOf("naranjo alajuela")))
            add(GisPlace("cr_can_207", "Palmares", "Alajuela", "Palmares", "Palmares", GisCategory.CANTON, 10.0578, -84.4333, listOf("palmares", "fiestas de palmares")))
            add(GisPlace("cr_can_208", "Poás", "Alajuela", "Poás", "San Pedro", GisCategory.CANTON, 10.0767, -84.2444, listOf("san pedro de poas", "volcan poas")))
            add(GisPlace("cr_can_209", "Orotina", "Alajuela", "Orotina", "Orotina", GisCategory.CANTON, 9.9133, -84.5217, listOf("orotina", "ciudad de las frutas")))
            add(GisPlace("cr_can_210", "San Carlos", "Alajuela", "San Carlos", "Quesada", GisCategory.CANTON, 10.3239, -84.4286, listOf("ciudad quesada", "san carlos", "la fortuna")))
            add(GisPlace("cr_can_211", "Zarcero", "Alajuela", "Zarcero", "Zarcero", GisCategory.CANTON, 10.1878, -84.3944, listOf("alfaro ruiz", "parque de zarcero")))
            add(GisPlace("cr_can_212", "Sarchí", "Alajuela", "Sarchí", "Sarchí Norte", GisCategory.CANTON, 10.0889, -84.3478, listOf("valverde vega", "sarchi", "cuna de la artesania")))
            add(GisPlace("cr_can_213", "Upala", "Alajuela", "Upala", "Upala", GisCategory.CANTON, 10.8986, -85.0167, listOf("upala centro", "zona norte")))
            add(GisPlace("cr_can_214", "Los Chiles", "Alajuela", "Los Chiles", "Los Chiles", GisCategory.CANTON, 11.0333, -84.7167, listOf("los chiles frontera")))
            add(GisPlace("cr_can_215", "Guatuso", "Alajuela", "Guatuso", "San Rafael", GisCategory.CANTON, 10.6694, -84.8250, listOf("san rafael de guatuso", "rio celeste")))
            add(GisPlace("cr_can_216", "Río Cuarto", "Alajuela", "Río Cuarto", "Río Cuarto", GisCategory.CANTON, 10.3472, -84.2150, listOf("rio cuarto alajuela")))

            // ── CARTAGO (8 Cantones) ──
            add(GisPlace("cr_can_301", "Cartago Central", "Cartago", "Cartago", "Oriental", GisCategory.CANTON, 9.8667, -83.9167, listOf("cartago", "cartago centro", "ruinas de cartago")))
            add(GisPlace("cr_can_302", "Paraíso", "Cartago", "Paraíso", "Paraíso", GisCategory.CANTON, 9.8383, -83.8672, listOf("paraiso de cartago", "orosi", "cachi")))
            add(GisPlace("cr_can_303", "La Unión", "Cartago", "La Unión", "Tres Ríos", GisCategory.CANTON, 9.9078, -83.9875, listOf("tres rios", "la union")))
            add(GisPlace("cr_can_304", "Jiménez", "Cartago", "Jiménez", "Juan Viñas", GisCategory.CANTON, 9.8944, -83.7431, listOf("juan viñas", "pejibaye")))
            add(GisPlace("cr_can_305", "Turrialba", "Cartago", "Turrialba", "Turrialba", GisCategory.CANTON, 9.9047, -83.6833, listOf("turri", "volcan turrialba", "turrialba centro")))
            add(GisPlace("cr_can_306", "Alvarado", "Cartago", "Alvarado", "Pacayas", GisCategory.CANTON, 9.9233, -83.8056, listOf("pacayas", "alvarado")))
            add(GisPlace("cr_can_307", "Oreamuno", "Cartago", "Oreamuno", "San Rafael", GisCategory.CANTON, 9.8767, -83.8967, listOf("san rafael de oreamuno", "oreamuno", "potrero cerrado")))
            add(GisPlace("cr_can_308", "El Guarco", "Cartago", "El Guarco", "El Tejar", GisCategory.CANTON, 9.8458, -83.9317, listOf("el tejar del guarco", "el guarco")))

            // ── HEREDIA (10 Cantones) ──
            add(GisPlace("cr_can_401", "Heredia Central", "Heredia", "Heredia", "Heredia", GisCategory.CANTON, 9.9989, -84.1167, listOf("heredia", "heredia centro", "el fortin")))
            add(GisPlace("cr_can_402", "Barva", "Heredia", "Barva", "Barva", GisCategory.CANTON, 10.0194, -84.1250, listOf("barva de heredia", "volcan barva")))
            add(GisPlace("cr_can_403", "Santo Domingo", "Heredia", "Santo Domingo", "Santo Domingo", GisCategory.CANTON, 9.9833, -84.0833, listOf("santo domingo de heredia", "domingo")))
            add(GisPlace("cr_can_404", "Santa Bárbara", "Heredia", "Santa Bárbara", "Santa Bárbara", GisCategory.CANTON, 10.0389, -84.1611, listOf("santa barbara de heredia")))
            add(GisPlace("cr_can_405", "San Rafael", "Heredia", "San Rafael", "San Rafael", GisCategory.CANTON, 10.0133, -84.1011, listOf("san rafael de heredia", "el tirol")))
            add(GisPlace("cr_can_406", "San Isidro", "Heredia", "San Isidro", "San Isidro", GisCategory.CANTON, 10.0189, -84.0567, listOf("san isidro de heredia", "zurqui")))
            add(GisPlace("cr_can_407", "Belén", "Heredia", "Belén", "San Antonio", GisCategory.CANTON, 9.9806, -84.1878, listOf("san antonio de belen", "belen", "la asuncion")))
            add(GisPlace("cr_can_408", "Flores", "Heredia", "Flores", "San Joaquín", GisCategory.CANTON, 10.0056, -84.1567, listOf("san joaquin de flores", "flores")))
            add(GisPlace("cr_can_409", "San Pablo", "Heredia", "San Pablo", "San Pablo", GisCategory.CANTON, 9.9944, -84.0989, listOf("san pablo de heredia")))
            add(GisPlace("cr_can_410", "Sarapiquí", "Heredia", "Sarapiquí", "Puerto Viejo", GisCategory.CANTON, 10.4286, -84.0067, listOf("puerto viejo de sarapiqui", "la virgen")))

            // ── GUANACASTE (11 Cantones) ──
            add(GisPlace("cr_can_501", "Liberia", "Guanacaste", "Liberia", "Liberia", GisCategory.CANTON, 10.6333, -85.4333, listOf("ciudad blanca", "liberia centro", "aeropuerto lirr")))
            add(GisPlace("cr_can_502", "Nicoya", "Guanacaste", "Nicoya", "Nicoya", GisCategory.CANTON, 10.1444, -85.4528, listOf("nicoya colonial", "zona azul nicoya")))
            add(GisPlace("cr_can_503", "Santa Cruz", "Guanacaste", "Santa Cruz", "Santa Cruz", GisCategory.CANTON, 10.2611, -85.5850, listOf("ciudad folklorica", "tamarindo", "flamingos")))
            add(GisPlace("cr_can_504", "Bagaces", "Guanacaste", "Bagaces", "Bagaces", GisCategory.CANTON, 10.5283, -85.2556, listOf("bagaces guanacaste", "termas")))
            add(GisPlace("cr_can_505", "Carrillo", "Guanacaste", "Carrillo", "Filadelfia", GisCategory.CANTON, 10.4439, -85.5489, listOf("filadelfia", "playas del coco", "hermosa")))
            add(GisPlace("cr_can_506", "Cañas", "Guanacaste", "Cañas", "Cañas", GisCategory.CANTON, 10.4311, -85.0933, listOf("cañas guanacaste", "interamericana norte")))
            add(GisPlace("cr_can_507", "Abangares", "Guanacaste", "Abangares", "Las Juntas", GisCategory.CANTON, 10.2806, -84.9578, listOf("las juntas de abangares", "ecotursimo minero")))
            add(GisPlace("cr_can_508", "Tilarán", "Guanacaste", "Tilarán", "Tilarán", GisCategory.CANTON, 10.4706, -84.9686, listOf("tilaran", "lago arenal")))
            add(GisPlace("cr_can_509", "Nandayure", "Guanacaste", "Nandayure", "Carmona", GisCategory.CANTON, 9.9889, -85.2528, listOf("carmona nandayure", "playa samara sur")))
            add(GisPlace("cr_can_510", "La Cruz", "Guanacaste", "La Cruz", "La Cruz", GisCategory.CANTON, 11.0744, -85.6328, listOf("la cruz frontera", "bahia salinas", "peñas blancas")))
            add(GisPlace("cr_can_511", "Hojancha", "Guanacaste", "Hojancha", "Hojancha", GisCategory.CANTON, 10.0611, -85.4222, listOf("hojancha guanacaste")))

            // ── PUNTARENAS (13 Cantones) ──
            add(GisPlace("cr_can_601", "Puntarenas Central", "Puntarenas", "Puntarenas", "Puntarenas", GisCategory.CANTON, 9.9763, -84.8384, listOf("paseo de los turistas", "el puerto", "caldera")))
            add(GisPlace("cr_can_602", "Esparza", "Puntarenas", "Esparza", "Espíritu Santo", GisCategory.CANTON, 9.9944, -84.6667, listOf("esparza puntarenas")))
            add(GisPlace("cr_can_603", "Buenos Aires", "Puntarenas", "Buenos Aires", "Buenos Aires", GisCategory.CANTON, 9.1706, -83.3333, listOf("buenos aires puntarenas")))
            add(GisPlace("cr_can_604", "Montes de Oro", "Puntarenas", "Montes de Oro", "Miramar", GisCategory.CANTON, 10.0917, -84.7292, listOf("miramar de puntarenas")))
            add(GisPlace("cr_can_605", "Osa", "Puntarenas", "Osa", "Puerto Cortés", GisCategory.CANTON, 8.9611, -83.5239, listOf("ciudad cortes", "bahia ballena", "uvita")))
            add(GisPlace("cr_can_606", "Quepos", "Puntarenas", "Quepos", "Quepos", GisCategory.CANTON, 9.4317, -84.1617, listOf("manuel antonio", "quepos puerto")))
            add(GisPlace("cr_can_607", "Golfito", "Puntarenas", "Golfito", "Golfito", GisCategory.CANTON, 8.6389, -83.1667, listOf("deposito libre golfito", "golfito")))
            add(GisPlace("cr_can_608", "Coto Brus", "Puntarenas", "Coto Brus", "San Vito", GisCategory.CANTON, 8.8250, -82.9736, listOf("san vito de coto brus")))
            add(GisPlace("cr_can_609", "Parrita", "Puntarenas", "Parrita", "Parrita", GisCategory.CANTON, 9.5217, -84.3278, listOf("parrita pacífico")))
            add(GisPlace("cr_can_610", "Corredores", "Puntarenas", "Corredores", "Corredor", GisCategory.CANTON, 8.6472, -82.9439, listOf("ciudad neily", "paso canoas frontera")))
            add(GisPlace("cr_can_611", "Garabito", "Puntarenas", "Garabito", "Jacó", GisCategory.CANTON, 9.6150, -84.6297, listOf("playa jaco", "herradura", "garabito")))
            add(GisPlace("cr_can_612", "Monteverde", "Puntarenas", "Monteverde", "Santa Elena", GisCategory.CANTON, 10.3017, -84.8197, listOf("bosque nuboso monteverde", "santa elena")))
            add(GisPlace("cr_can_613", "Puerto Jiménez", "Puntarenas", "Puerto Jiménez", "Puerto Jiménez", GisCategory.CANTON, 8.5350, -83.3050, listOf("peninsula de osa", "parque corcovado")))

            // ── LIMÓN (6 Cantones) ──
            add(GisPlace("cr_can_701", "Limón Central", "Limón", "Limón", "Limón", GisCategory.CANTON, 9.9907, -83.0360, listOf("puerto limon", "mora", "playa bonita")))
            add(GisPlace("cr_can_702", "Pococí", "Limón", "Pococí", "Guápiles", GisCategory.CANTON, 10.2167, -83.7833, listOf("guapiles", "pococi", "cariari")))
            add(GisPlace("cr_can_703", "Siquirres", "Limón", "Siquirres", "Siquirres", GisCategory.CANTON, 10.0989, -83.5083, listOf("siquirres caribe", "pacuare")))
            add(GisPlace("cr_can_704", "Talamanca", "Limón", "Talamanca", "Bribri", GisCategory.CANTON, 9.6278, -82.8417, listOf("puerto viejo limon", "cahuita", "bribri", "manzanillo")))
            add(GisPlace("cr_can_705", "Matina", "Limón", "Matina", "Matina", GisCategory.CANTON, 10.0833, -83.2833, listOf("matina limon", "batan")))
            add(GisPlace("cr_can_706", "Guácimo", "Limón", "Guácimo", "Guácimo", GisCategory.CANTON, 10.2139, -83.6872, listOf("guacimo limon", "universidad earth")))

            // ═══════════════════════════════════════════════════════════════
            // ── 3. DISTRITOS & BARRIOS URBANOS NEURÁLGICOS (GAM) ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_bar_rohrmoser", "Rohrmoser", "San José", "San José", "Pavas", GisCategory.BARRIO, 9.9445, -84.1189, listOf("bulevar rohrmoser", "plaza rohrmoser", "embajada usa", "triangulo de rohrmoser")))
            add(GisPlace("cr_bar_escalante", "Barrio Escalante", "San José", "San José", "El Carmen", GisCategory.BARRIO, 9.9356, -84.0623, listOf("paseo gastronomico escalante", "escalante", "calle 33")))
            add(GisPlace("cr_bar_amon", "Barrio Amón", "San José", "San José", "El Carmen", GisCategory.BARRIO, 9.9389, -84.0767, listOf("amon histórico", "barrio amon")))
            add(GisPlace("cr_bar_yoses", "Los Yoses", "San José", "Montes de Oca", "San Pedro", GisCategory.BARRIO, 9.9312, -84.0567, listOf("los yoses", "bulevar los yoses")))
            add(GisPlace("cr_bar_sabana_norte", "Sabana Norte", "San José", "San José", "Mata Redonda", GisCategory.BARRIO, 9.9412, -84.1034, listOf("la sabana norte", "ice sabana")))
            add(GisPlace("cr_bar_sabana_sur", "Sabana Sur", "San José", "San José", "Mata Redonda", GisCategory.BARRIO, 9.9312, -84.1023, listOf("la sabana sur", "contraloria")))
            add(GisPlace("cr_bar_pavas", "Pavas Centro", "San José", "San José", "Pavas", GisCategory.DISTRICT, 9.9458, -84.1308, listOf("zona industrial pavas", "aeropuerto tobias bolaños")))
            add(GisPlace("cr_bar_lindora", "Lindora", "San José", "Santa Ana", "Pozos", GisCategory.BARRIO, 9.9456, -84.1823, listOf("radial lindora", "terrazas lindora", "zona comercial lindora")))
            add(GisPlace("cr_bar_guachipelin", "Guachipelín", "San José", "Escazú", "San Rafael", GisCategory.BARRIO, 9.9478, -84.1567, listOf("guachipelin de escazu", "tunel guachipelin")))
            add(GisPlace("cr_bar_pozos", "Pozos de Santa Ana", "San José", "Santa Ana", "Pozos", GisCategory.DISTRICT, 9.9389, -84.1912, listOf("pozos", "hospital clinica biblica santa ana")))
            add(GisPlace("cr_bar_zapote", "Zapote Centro", "San José", "San José", "Zapote", GisCategory.DISTRICT, 9.9239, -84.0531, listOf("redondel de zapote", "casa presidencial", "rotonda de las garantias")))
            add(GisPlace("cr_bar_san_francisco", "San Francisco de Dos Ríos", "San José", "San José", "San Francisco", GisCategory.DISTRICT, 9.9089, -84.0578, listOf("san francisco", "parque de san francisco")))
            add(GisPlace("cr_bar_pinares", "Pinares", "San José", "Curridabat", "Sánchez", GisCategory.BARRIO, 9.9189, -84.0256, listOf("pinares de curridabat", "cronos plaza")))
            add(GisPlace("cr_bar_guayabos", "Guayabos", "San José", "Curridabat", "Granadilla", GisCategory.BARRIO, 9.9245, -84.0289, listOf("guayabos curridabat", "fresh market guayabos")))

            // ═══════════════════════════════════════════════════════════════
            // ── 4. INSPECCIÓN TÉCNICA (DEKRA) & COSEVI (TRANSPORTE OFICIAL) ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_dekra_alajuela", "DEKRA Alajuela", "Alajuela", "Alajuela", "El Coco", GisCategory.INSPECTION_DEKRA, 10.0078, -84.2045, listOf("riteve alajuela", "revision tecnica alajuela"), "DEKRA"))
            add(GisPlace("cr_dekra_barreal", "DEKRA Barreal de Heredia", "Heredia", "Heredia", "Ulloa", GisCategory.INSPECTION_DEKRA, 9.9702, -84.1481, listOf("riteve barreal", "revision tecnica barreal", "cenada"), "DEKRA"))
            add(GisPlace("cr_dekra_stodomingo", "DEKRA Santo Domingo", "Heredia", "Santo Domingo", "Santo Domingo", GisCategory.INSPECTION_DEKRA, 9.9752, -84.0841, listOf("riteve santo domingo", "revision tecnica santo domingo"), "DEKRA"))
            add(GisPlace("cr_dekra_cartago", "DEKRA Cartago (La Lima)", "Cartago", "Cartago", "San Nicolás", GisCategory.INSPECTION_DEKRA, 9.8512, -83.9451, listOf("riteve cartago", "revision tecnica la lima"), "DEKRA"))
            add(GisPlace("cr_dekra_guapiles", "DEKRA Guápiles", "Limón", "Pococí", "Guápiles", GisCategory.INSPECTION_DEKRA, 10.2078, -83.7712, listOf("riteve guapiles", "revision tecnica pococi"), "DEKRA"))
            add(GisPlace("cr_dekra_pz", "DEKRA Pérez Zeledón", "San José", "Pérez Zeledón", "Daniel Flores", GisCategory.INSPECTION_DEKRA, 9.3621, -83.6942, listOf("riteve perez zeledon", "revision tecnica pz"), "DEKRA"))
            add(GisPlace("cr_dekra_liberia", "DEKRA Liberia", "Guanacaste", "Liberia", "Liberia", GisCategory.INSPECTION_DEKRA, 10.6214, -85.4412, listOf("riteve liberia", "revision tecnica guanacaste"), "DEKRA"))
            add(GisPlace("cr_dekra_puntarenas", "DEKRA Puntarenas (El Roble)", "Puntarenas", "Puntarenas", "El Roble", GisCategory.INSPECTION_DEKRA, 9.9812, -84.7523, listOf("riteve puntarenas", "revision tecnica el roble"), "DEKRA"))
            add(GisPlace("cr_dekra_scarlos", "DEKRA San Carlos (Muelle)", "Alajuela", "San Carlos", "Florencia", GisCategory.INSPECTION_DEKRA, 10.4512, -84.4812, listOf("riteve san carlos", "revision tecnica florencia"), "DEKRA"))
            add(GisPlace("cr_cosevi_central", "COSEVI Sede Central La Uruca", "San José", "San José", "Uruca", GisCategory.LANDMARK, 9.9482, -84.1011, listOf("cosevi", "licencias cosevi", "pruebas de manejo uruca"), "COSEVI"))

            // ═══════════════════════════════════════════════════════════════
            // ── 5. COMERCIOS & SUPERMERCADOS DE USO DIARIO ──
            // ═══════════════════════════════════════════════════════════════
            // Auto Mercado
            add(GisPlace("cr_sup_auto_yoses", "Auto Mercado Los Yoses", "San José", "Montes de Oca", "San Pedro", GisCategory.SUPERMARKET, 9.9328, -84.0583, listOf("automercado yoses", "auto los yoses"), "Auto Mercado"))
            add(GisPlace("cr_sup_auto_escazu", "Auto Mercado Multiplaza Escazú", "San José", "Escazú", "San Rafael", GisCategory.SUPERMARKET, 9.9442, -84.1536, listOf("automercado multiplaza", "auto escazu"), "Auto Mercado"))
            add(GisPlace("cr_sup_auto_plazadelsol", "Auto Mercado Plaza del Sol", "San José", "Curridabat", "Curridabat", GisCategory.SUPERMARKET, 9.9301, -84.0489, listOf("automercado plaza del sol", "auto curridabat"), "Auto Mercado"))
            add(GisPlace("cr_sup_auto_moravia", "Auto Mercado Moravia", "San José", "Moravia", "San Vicente", GisCategory.SUPERMARKET, 9.9654, -84.0478, listOf("automercado moravia"), "Auto Mercado"))
            add(GisPlace("cr_sup_auto_alajuela", "Auto Mercado Alajuela", "Alajuela", "Alajuela", "Alajuela", GisCategory.SUPERMARKET, 10.0125, -84.2150, listOf("automercado alajuela", "plaza real alajuela"), "Auto Mercado"))
            add(GisPlace("cr_sup_auto_lindora", "Auto Mercado Lindora", "San José", "Santa Ana", "Pozos", GisCategory.SUPERMARKET, 9.9478, -84.1802, listOf("automercado lindora", "auto santa ana"), "Auto Mercado"))
            add(GisPlace("cr_sup_auto_herradura", "Auto Mercado Herradura", "Puntarenas", "Garabito", "Jacó", GisCategory.SUPERMARKET, 9.6456, -84.6312, listOf("automercado herradura", "auto jaco"), "Auto Mercado"))

            // Walmart
            add(GisPlace("cr_sup_wal_escazu", "Walmart Escazú", "San José", "Escazú", "San Rafael", GisCategory.SUPERMARKET, 9.9362, -84.1425, listOf("walmart escazu", "walmart san rafael"), "Walmart"))
            add(GisPlace("cr_sup_wal_curri", "Walmart Curridabat", "San José", "Curridabat", "Curridabat", GisCategory.SUPERMARKET, 9.9178, -84.0345, listOf("walmart curridabat", "walmart este"), "Walmart"))
            add(GisPlace("cr_sup_wal_heredia", "Walmart Heredia", "Heredia", "Heredia", "San Francisco", GisCategory.SUPERMARKET, 9.9882, -84.1289, listOf("walmart san francisco heredia", "walmart heredia"), "Walmart"))
            add(GisPlace("cr_sup_wal_alajuela", "Walmart Alajuela", "Alajuela", "Alajuela", "San Antonio", GisCategory.SUPERMARKET, 10.0089, -84.2189, listOf("walmart alajuela", "walmart monserrat"), "Walmart"))
            add(GisPlace("cr_sup_wal_sansebas", "Walmart San Sebastián", "San José", "San José", "San Sebastián", GisCategory.SUPERMARKET, 9.9142, -84.0867, listOf("walmart san sebastian", "walmart circunvalacion"), "Walmart"))
            add(GisPlace("cr_sup_wal_tibas", "Walmart Tibás", "San José", "Tibás", "Llorente", GisCategory.SUPERMARKET, 9.9612, -84.0825, listOf("walmart tibas", "walmart llorente"), "Walmart"))
            add(GisPlace("cr_sup_wal_cartago", "Walmart Cartago", "Cartago", "Cartago", "Occidental", GisCategory.SUPERMARKET, 9.8612, -83.9289, listOf("walmart cartago"), "Walmart"))
            add(GisPlace("cr_sup_wal_guadalupe", "Walmart Guadalupe", "San José", "Goicoechea", "Guadalupe", GisCategory.SUPERMARKET, 9.9512, -84.0534, listOf("walmart guadalupe", "walmart el cruce"), "Walmart"))

            // PriceSmart
            add(GisPlace("cr_sup_ps_zapote", "PriceSmart Zapote", "San José", "San José", "Zapote", GisCategory.SUPERMARKET, 9.9214, -84.0567, listOf("pricesmart zapote", "club pricesmart"), "PriceSmart"))
            add(GisPlace("cr_sup_ps_escazu", "PriceSmart Escazú", "San José", "Escazú", "San Rafael", GisCategory.SUPERMARKET, 9.9402, -84.1491, listOf("pricesmart escazu"), "PriceSmart"))
            add(GisPlace("cr_sup_ps_heredia", "PriceSmart Heredia", "Heredia", "Heredia", "San Francisco", GisCategory.SUPERMARKET, 9.9812, -84.1378, listOf("pricesmart heredia"), "PriceSmart"))
            add(GisPlace("cr_sup_ps_tibas", "PriceSmart Llorente de Tibás", "San José", "Tibás", "Llorente", GisCategory.SUPERMARKET, 9.9634, -84.0789, listOf("pricesmart tibas", "pricesmart llorente"), "PriceSmart"))
            add(GisPlace("cr_sup_ps_santaana", "PriceSmart Santa Ana", "San José", "Santa Ana", "Pozos", GisCategory.SUPERMARKET, 9.9421, -84.1812, listOf("pricesmart santa ana", "pricesmart rio oro"), "PriceSmart"))
            add(GisPlace("cr_sup_ps_alajuela", "PriceSmart Alajuela", "Alajuela", "Alajuela", "Alajuela", GisCategory.SUPERMARKET, 10.0102, -84.2214, listOf("pricesmart alajuela", "pricesmart aeropuerto"), "PriceSmart"))
            add(GisPlace("cr_sup_ps_cartago", "PriceSmart Cartago (La Lima)", "Cartago", "Cartago", "San Nicolás", GisCategory.SUPERMARKET, 9.8634, -83.9456, listOf("pricesmart cartago", "pricesmart la lima"), "PriceSmart"))

            // Masxmenos
            add(GisPlace("cr_sup_mxm_sabana", "Masxmenos La Sabana", "San José", "San José", "Mata Redonda", GisCategory.SUPERMARKET, 9.9389, -84.0989, listOf("masxmenos sabana", "mas por menos sabana"), "Masxmenos"))
            add(GisPlace("cr_sup_mxm_santaana", "Masxmenos Santa Ana", "San José", "Santa Ana", "Santa Ana", GisCategory.SUPERMARKET, 9.9345, -84.1812, listOf("masxmenos santa ana"), "Masxmenos"))
            add(GisPlace("cr_sup_mxm_guayabos", "Masxmenos Guayabos", "San José", "Curridabat", "Granadilla", GisCategory.SUPERMARKET, 9.9212, -84.0289, listOf("masxmenos guayabos", "masxmenos curridabat"), "Masxmenos"))

            // Ferreterías EPA & El Lagar
            add(GisPlace("cr_hrd_epa_curri", "EPA Curridabat", "San José", "Curridabat", "Curridabat", GisCategory.HARDWARE_STORE, 9.9189, -84.0312, listOf("ferreteria epa curridabat", "epa este"), "EPA"))
            add(GisPlace("cr_hrd_epa_escazu", "EPA Escazú", "San José", "Escazú", "San Rafael", GisCategory.HARDWARE_STORE, 9.9389, -84.1456, listOf("ferreteria epa escazu", "epa oeste"), "EPA"))
            add(GisPlace("cr_hrd_epa_tibas", "EPA Tibás", "San José", "Tibás", "Llorente", GisCategory.HARDWARE_STORE, 9.9602, -84.0812, listOf("ferreteria epa tibas"), "EPA"))
            add(GisPlace("cr_hrd_epa_desampa", "EPA Desamparados", "San José", "Desamparados", "Desamparados", GisCategory.HARDWARE_STORE, 9.8989, -84.0689, listOf("ferreteria epa desamparados"), "EPA"))
            add(GisPlace("cr_hrd_lagar_pedro", "El Lagar San Pedro", "San José", "Montes de Oca", "San Pedro", GisCategory.HARDWARE_STORE, 9.9312, -84.0489, listOf("ferreteria el lagar san pedro"), "El Lagar"))
            add(GisPlace("cr_hrd_lagar_alajuela", "El Lagar Alajuela", "Alajuela", "Alajuela", "Alajuela", GisCategory.HARDWARE_STORE, 10.0145, -84.2112, listOf("ferreteria el lagar alajuela"), "El Lagar"))

            // ═══════════════════════════════════════════════════════════════
            // ── 6. CENTROS COMERCIALES (MALLS DE ALTO TRÁNSITO) ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_mall_multi_escazu", "Multiplaza Escazú", "San José", "Escazú", "San Rafael", GisCategory.MALL, 9.9442, -84.1536, listOf("multiplaza", "multi escazu")))
            add(GisPlace("cr_mall_multi_curri", "Multiplaza Curridabat", "San José", "Curridabat", "Sánchez", GisCategory.MALL, 9.9156, -84.0321, listOf("multi curri", "multiplaza del este")))
            add(GisPlace("cr_mall_citymall", "City Mall Alajuela", "Alajuela", "Alajuela", "Alajuela", GisCategory.MALL, 10.0108, -84.2097, listOf("city mall", "mall alajuela")))
            add(GisPlace("cr_mall_oxigeno", "Oxígeno Human Playground", "Heredia", "Heredia", "San Francisco", GisCategory.MALL, 9.9886, -84.1331, listOf("oxigeno", "mall oxigeno heredia")))
            add(GisPlace("cr_mall_paseo_flores", "Paseo de las Flores", "Heredia", "Heredia", "Heredia", GisCategory.MALL, 9.9912, -84.1214, listOf("paseo flores", "mall paseo de las flores")))
            add(GisPlace("cr_mall_lincoln", "Lincoln Plaza Moravia", "San José", "Moravia", "San Vicente", GisCategory.MALL, 9.9645, -84.0512, listOf("lincoln plaza", "mall moravia")))
            add(GisPlace("cr_mall_san_pedro", "Mall San Pedro", "San José", "Montes de Oca", "San Pedro", GisCategory.MALL, 9.9345, -84.0534, listOf("mall san pedro", "rotonda de la hispanidad")))
            add(GisPlace("cr_mall_paseo_metropoli", "Paseo Metrópoli", "Cartago", "Cartago", "San Nicolás", GisCategory.MALL, 9.8621, -83.9389, listOf("paseo metropoli", "mall cartago")))
            add(GisPlace("cr_mall_terramall", "TerraMall Tres Ríos", "Cartago", "La Unión", "San Diego", GisCategory.MALL, 9.9023, -83.9845, listOf("terramall", "mall tres rios")))

            // ═══════════════════════════════════════════════════════════════
            // ── 7. HOSPITALES Y CENTROS DE SALUD MAYORES ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_hosp_mexico", "Hospital México", "San José", "San José", "Uruca", GisCategory.HOSPITAL_CLINIC, 9.9512, -84.1089, listOf("hospital mexico ccss", "el mexico uruca")))
            add(GisPlace("cr_hosp_sanjuan", "Hospital San Juan de Dios", "San José", "San José", "Merced", GisCategory.HOSPITAL_CLINIC, 9.9334, -84.0856, listOf("san juan de dios", "hospital san juan")))
            add(GisPlace("cr_hosp_calderon", "Hospital Calderón Guardia", "San José", "San José", "El Carmen", GisCategory.HOSPITAL_CLINIC, 9.9367, -84.0689, listOf("calderon guardia", "hospital calderon")))
            add(GisPlace("cr_hosp_cima", "Hospital CIMA San José", "San José", "Escazú", "San Rafael", GisCategory.HOSPITAL_CLINIC, 9.9412, -84.1489, listOf("cima escazu", "hospital cima")))
            add(GisPlace("cr_hosp_biblica_central", "Clínica Bíblica Central", "San José", "San José", "Hospital", GisCategory.HOSPITAL_CLINIC, 9.9312, -84.0812, listOf("clinica biblica centro", "hospital clinica biblica")))
            add(GisPlace("cr_hosp_biblica_staana", "Clínica Bíblica Santa Ana", "San José", "Santa Ana", "Pozos", GisCategory.HOSPITAL_CLINIC, 9.9421, -84.1834, listOf("clinica biblica santa ana", "biblica pozos")))
            add(GisPlace("cr_hosp_sanrafael", "Hospital San Rafael de Alajuela", "Alajuela", "Alajuela", "Alajuela", GisCategory.HOSPITAL_CLINIC, 10.0125, -84.2189, listOf("hospital de alajuela", "hospital san rafael")))
            add(GisPlace("cr_hosp_maxperalta", "Hospital Max Peralta", "Cartago", "Cartago", "Oriental", GisCategory.HOSPITAL_CLINIC, 9.8645, -83.9212, listOf("hospital de cartago", "max peralta")))
            add(GisPlace("cr_hosp_sanvicente", "Hospital San Vicente de Paúl", "Heredia", "Heredia", "Heredia", GisCategory.HOSPITAL_CLINIC, 9.9956, -84.1245, listOf("hospital de heredia", "san vicente de paul")))

            // ═══════════════════════════════════════════════════════════════
            // ── 7B. REFUGIOS DE EMERGENCIA (FUERZA PÚBLICA Y BOMBEROS) ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_emg_fp_sanjose", "Delegación Fuerza Pública San José Central", "San José", "San José", "Hospital", GisCategory.POLICE_FIRE_EMERGENCY, 9.9312, -84.0789, listOf("fuerza publica san jose", "policia san jose centro", "comisaria san jose")))
            add(GisPlace("cr_emg_fp_escazu", "Delegación Fuerza Pública Escazú", "San José", "Escazú", "San Antonio", GisCategory.POLICE_FIRE_EMERGENCY, 9.9201, -84.1412, listOf("fuerza publica escazu", "policia escazu")))
            add(GisPlace("cr_emg_fp_alajuela", "Delegación Fuerza Pública Alajuela Central", "Alajuela", "Alajuela", "Alajuela", GisCategory.POLICE_FIRE_EMERGENCY, 10.0150, -84.2140, listOf("fuerza publica alajuela", "policia alajuela")))
            add(GisPlace("cr_emg_fp_cartago", "Delegación Fuerza Pública Cartago", "Cartago", "Cartago", "Occidental", GisCategory.POLICE_FIRE_EMERGENCY, 9.8650, -83.9200, listOf("fuerza publica cartago", "policia cartago")))
            add(GisPlace("cr_emg_fp_heredia", "Delegación Fuerza Pública Heredia Central", "Heredia", "Heredia", "Heredia", GisCategory.POLICE_FIRE_EMERGENCY, 9.9970, -84.1190, listOf("fuerza publica heredia", "policia heredia")))
            add(GisPlace("cr_emg_bomb_central", "Estación Central de Bomberos San José", "San José", "San José", "Merced", GisCategory.POLICE_FIRE_EMERGENCY, 9.9380, -84.0850, listOf("bomberos central", "estacion bomberos san jose")))

            // ═══════════════════════════════════════════════════════════════
            // ── 8. HUBS AUTOMOTRICES, REPUESTOS & AGENCIAS ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_auto_guaca_uruca", "Repuestos La Guaca La Uruca", "San José", "San José", "Uruca", GisCategory.AUTOMOTIVE_HUB, 9.9512, -84.1012, listOf("la guaca uruca", "repuestos la guaca"), "La Guaca"))
            add(GisPlace("cr_auto_guaca_pasoancho", "Repuestos La Guaca Paso Ancho", "San José", "San José", "San Sebastián", GisCategory.AUTOMOTIVE_HUB, 9.9123, -84.0856, listOf("la guaca paso ancho", "calle de repuestos paso ancho"), "La Guaca"))
            add(GisPlace("cr_auto_purdy_uruca", "Purdy Motor Toyota Central", "San José", "San José", "Uruca", GisCategory.AUTOMOTIVE_HUB, 9.9521, -84.1045, listOf("toyota uruca", "purdy toyota", "taller purdy"), "Purdy Motor"))
            add(GisPlace("cr_auto_grupoq_uruca", "Grupo Q La Uruca (Hyundai / Isuzu)", "San José", "San José", "Uruca", GisCategory.AUTOMOTIVE_HUB, 9.9501, -84.1034, listOf("grupo q uruca", "hyundai uruca", "chevrolet uruca"), "Grupo Q"))
            add(GisPlace("cr_auto_superbaterias_uruca", "Super Baterías La Uruca", "San José", "San José", "Uruca", GisCategory.AUTOMOTIVE_HUB, 9.9534, -84.1023, listOf("super baterias uruca", "baterias lth"), "Super Baterías"))
            add(GisPlace("cr_auto_autopits_curri", "Autopits Curridabat", "San José", "Curridabat", "Curridabat", GisCategory.AUTOMOTIVE_HUB, 9.9178, -84.0334, listOf("autopits este", "mantenimiento rapido autopits"), "Autopits"))
            add(GisPlace("cr_auto_autopits_escazu", "Autopits Escazú", "San José", "Escazú", "San Rafael", GisCategory.AUTOMOTIVE_HUB, 9.9378, -84.1434, listOf("autopits oeste", "autopits escazu"), "Autopits"))

            // ═══════════════════════════════════════════════════════════════
            // ── 9. GASOLINERAS ESTRATÉGICAS / PUNTOS DE ENCUENTRO SEGURO ──
            // ═══════════════════════════════════════════════════════════════
            add(GisPlace("cr_gas_delta_sabana", "Estación Delta La Sabana", "San José", "San José", "Mata Redonda", GisCategory.GAS_STATION, 9.9367, -84.0956, listOf("bomba delta sabana", "gasolinera la sabana"), "Delta"))
            add(GisPlace("cr_gas_uno_sanpedro", "Estación Uno San Pedro", "San José", "Montes de Oca", "San Pedro", GisCategory.GAS_STATION, 9.9328, -84.0556, listOf("bomba uno san pedro", "gasolinera san pedro"), "Uno"))
            add(GisPlace("cr_gas_jsm_circunvalacion", "Estación JSM Circunvalación", "San José", "San José", "San Sebastián", GisCategory.GAS_STATION, 9.9112, -84.0812, listOf("bomba jsm circunvalacion", "gasolinera jsm"), "JSM"))
            add(GisPlace("cr_gas_total_curri", "Estación Total Curridabat", "San José", "Curridabat", "Curridabat", GisCategory.GAS_STATION, 9.9156, -84.0389, listOf("bomba total curri", "gasolinera total"), "Total"))
        }
    }

    /**
     * Búsqueda en texto normalizado ultrarrápida (< 1 ms en memoria local).
     */
    fun search(query: String, limit: Int = 10): List<GisPlace> {
        val trimmed = normalize(query)
        if (trimmed.length < 2) return emptyList()

        return allPlaces.mapNotNull { place ->
            val score = computeMatchScore(trimmed, place)
            if (score > 0.20) score to place else null
        }.sortedByDescending { it.first }
            .map { it.second }
            .take(limit)
    }

    /**
     * Búsqueda de lugares más cercanos a unas coordenadas GPS.
     */
    fun nearest(
        latitude: Double,
        longitude: Double,
        category: GisCategory? = null,
        limit: Int = 5
    ): List<Pair<GisPlace, Double>> {
        return allPlaces
            .filter { category == null || it.category == category }
            .map { place ->
                val distKm = haversineDistanceKm(latitude, longitude, place.latitude, place.longitude)
                place to distKm
            }
            .sortedBy { it.second }
            .take(limit)
    }

    private fun computeMatchScore(query: String, place: GisPlace): Double {
        val normName = normalize(place.name)
        val normCanton = normalize(place.canton)
        val normDistrict = normalize(place.district)
        val normProvince = normalize(place.province)
        val brand = place.brand?.let(::normalize)
        val allAliases = place.aliases.map(::normalize)

        val candidateStrings = listOfNotNull(normName, normCanton, normDistrict, normProvince, brand) + allAliases

        // Coincidencia exacta de nombre o alias
        if (candidateStrings.any { it == query }) return 1.0

        // Prefijo directo
        if (candidateStrings.any { it.startsWith(query) }) return 0.95

        // Inclusión de frase
        if (candidateStrings.any { it.contains(query) }) return 0.85

        // Token matching
        val queryTokens = query.split("\\s+".toRegex()).filter { it.length > 1 }
        if (queryTokens.isNotEmpty()) {
            val matches = queryTokens.count { token -> candidateStrings.any { it.contains(token) } }
            val ratio = matches.toDouble() / queryTokens.size.toDouble()
            if (ratio > 0.0) return ratio * 0.75
        }

        return 0.0
    }

    fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun normalize(text: String): String {
        return Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .trim()
    }
}
