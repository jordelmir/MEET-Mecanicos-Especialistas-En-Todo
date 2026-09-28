package com.elysium.nexus.fabric.infrared.database

/**
 * Probabilistic brand-first ordering for universal probes (§35).
 *
 * Costa Rica retail reality: the TVs on the Gollo/Monge floor and in most
 * homes are Samsung, LG, Hisense, TCL, Panasonic, Sony, Philips — followed
 * by Konka, Telstar, AIWA, RCA, JVC. Probing in this order turns a
 * "find it in the store" session from minutes into seconds, because the
 * brands most likely on the floor are tried first with a matched code set.
 *
 * Pure JVM: no Android dependencies, so tests can assert the exact SQL.
 */
object BrandRanking {

    val RANK = listOf(
        "Samsung", "LG", "Hisense", "TCL", "Panasonic", "Sony", "Philips",
        "Sharp", "Toshiba", "Konka", "Telstar", "AIWA", "RCA", "JVC",
        "Xiaomi / Mi", "Sankey", "Kintech", "Challenger", "Kalley",
        "Daewoo", "Hyundai", "Noblex", "Akai", "Sanyo", "Funai",
        "Magnavox", "Sylvania", "Westinghouse", "CCE", "Philco"
    )

    /**
     * SQL ORDER BY that puts the [RANK] brands first, then the rest
     * alphabetically. Deterministic and stable for paging.
     */
    val ORDER_BY_SQL: String by lazy {
        val cases = RANK.withIndex().joinToString(" ") { (i, brand) ->
            "WHEN '$brand' THEN ${i + 1}"
        }
        "ORDER BY CASE b.display_name $cases ELSE 99 END, b.display_name, cs.id"
    }
}