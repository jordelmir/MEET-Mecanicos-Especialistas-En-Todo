package com.elysium.nexus.core.device

/**
 * The category of device the user wants to control.
 *
 * The categories are the **top level** of the §15
 * hierarchy. The user picks a category (TV, PlayStation,
 * Xbox, …) and then a brand (Samsung, LG, Sony, …) and
 * then a specific model. The hierarchy is the §15
 * "Control Universal" tree.
 *
 * Each category has:
 *
 *  - A **human label** in English + Spanish (the
 *    `labelEn` / `labelEs` fields).
 *  - An **emoji / icon hint** for the category card
 *    (the `iconHint` field — the actual icon is in
 *    [com.elysium.nexus.core.device.DeviceCategoryIcons]).
 *  - A **list of supported brands** (the
 *    [DeviceTemplate.catalog] filter).
 *  - A **default button layout** (the
 *    [com.elysium.nexus.core.device.DeviceButton]
 *    set). The TV category has Power / Vol / Ch /
 *    Numpad; the PlayStation category has Cross /
 *    Circle / Square / Triangle / L1 / R1 / etc.
 */
enum class DeviceCategory(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val iconHint: String,
    val blurbEn: String,
    val blurbEs: String,
    /**
     * The primary transport this category uses.
     * TVs use infrared (IR); consoles use
     * Bluetooth HID; computers use Wi-Fi / USB.
     */
    val primaryTransport: PrimaryTransport
) {
    TV(
        id = "tv",
        labelEn = "TV",
        labelEs = "Televisor",
        iconHint = "tv",
        blurbEn = "Control any TV with the infrared (IR) blaster on your phone.",
        blurbEs = "Controla cualquier televisor con el infrarrojo (IR) de tu teléfono.",
        primaryTransport = PrimaryTransport.INFRARED
    ),
    ANDROID_TV(
        id = "android_tv",
        labelEn = "Android TV",
        labelEs = "Android TV",
        iconHint = "android_tv",
        blurbEn = "Control an Android TV / Fire TV / Chromecast over Wi-Fi.",
        blurbEs = "Controla un Android TV / Fire TV / Chromecast por Wi-Fi.",
        primaryTransport = PrimaryTransport.WIFI
    ),
    PLAYSTATION(
        id = "playstation",
        labelEn = "PlayStation",
        labelEs = "PlayStation",
        iconHint = "playstation",
        blurbEn = "PS4 / PS5 controllers over Bluetooth.",
        blurbEs = "Controles de PS4 / PS5 por Bluetooth.",
        primaryTransport = PrimaryTransport.BLUETOOTH
    ),
    XBOX(
        id = "xbox",
        labelEn = "Xbox",
        labelEs = "Xbox",
        iconHint = "xbox",
        blurbEn = "Xbox One / Series controllers over Bluetooth.",
        blurbEs = "Controles de Xbox One / Series por Bluetooth.",
        primaryTransport = PrimaryTransport.BLUETOOTH
    ),
    NINTENDO(
        id = "nintendo",
        labelEn = "Nintendo",
        labelEs = "Nintendo",
        iconHint = "nintendo",
        blurbEn = "Switch / Switch 2 controllers over Bluetooth.",
        blurbEs = "Controles de Switch / Switch 2 por Bluetooth.",
        primaryTransport = PrimaryTransport.BLUETOOTH
    ),
    COMPUTER(
        id = "computer",
        labelEn = "Computer",
        labelEs = "Computadora",
        iconHint = "computer",
        blurbEn = "Mac / Windows / Linux over Wi-Fi or USB.",
        blurbEs = "Mac / Windows / Linux por Wi-Fi o USB.",
        primaryTransport = PrimaryTransport.WIFI
    ),
    STREAMING(
        id = "streaming",
        labelEn = "Streaming",
        labelEs = "Streaming",
        iconHint = "streaming",
        blurbEn = "Roku, Apple TV, Fire TV Stick — over Wi-Fi.",
        blurbEs = "Roku, Apple TV, Fire TV Stick — por Wi-Fi.",
        primaryTransport = PrimaryTransport.WIFI
    ),
    SOUNDBAR(
        id = "soundbar",
        labelEn = "Soundbar",
        labelEs = "Barra de sonido",
        iconHint = "soundbar",
        blurbEn = "Soundbars and AV receivers over IR or Wi-Fi.",
        blurbEs = "Barras de sonido y receptores AV por IR o Wi-Fi.",
        primaryTransport = PrimaryTransport.INFRARED
    ),
    PROJECTOR(
        id = "projector",
        labelEn = "Projector",
        labelEs = "Proyector",
        iconHint = "projector",
        blurbEn = "Projectors over IR.",
        blurbEs = "Proyectores por IR.",
        primaryTransport = PrimaryTransport.INFRARED
    ),
    AIR_CONDITIONER(
        id = "air_conditioner",
        labelEn = "Air Conditioner",
        labelEs = "Aire Acondicionado",
        iconHint = "ac",
        blurbEn = "AC / HVAC with stateful IR commands (temperature + mode + fan).",
        blurbEs = "AC / HVAC con comandos IR estado (temperatura + modo + ventilador).",
        primaryTransport = PrimaryTransport.INFRARED
    );

    companion object {
        /**
         * The category list used in the **Hub** screen,
         * in display order. The first entry (TV) is the
         * most common use case and gets a hero card;
         * the rest are tile cards.
         */
        val hubOrder: List<DeviceCategory> = listOf(
            TV,
            PLAYSTATION,
            XBOX,
            NINTENDO,
            ANDROID_TV,
            STREAMING,
            COMPUTER,
            SOUNDBAR,
            PROJECTOR
        )

        /**
         * The console sub-categories. The Hub shows
         * "PlayStation" as a single card; tapping it
         * drills into the sub-category list (PS3,
         * PS4, PS5, etc.) so the user can pick the
         * exact console.
         */
        val playstationSubcategories: List<ConsoleSubcategory> = listOf(
            ConsoleSubcategory("ps5", "PlayStation 5", "PS5", 2020),
            ConsoleSubcategory("ps4", "PlayStation 4", "PS4", 2013),
            ConsoleSubcategory("ps3", "PlayStation 3", "PS3", 2006),
            ConsoleSubcategory("psvita", "PlayStation Vita", "PS Vita", 2011)
        )

        val xboxSubcategories: List<ConsoleSubcategory> = listOf(
            ConsoleSubcategory("xbox-series", "Xbox Series X/S", "Series", 2020),
            ConsoleSubcategory("xbox-one", "Xbox One", "One", 2013),
            ConsoleSubcategory("xbox-360", "Xbox 360", "360", 2005)
        )

        val nintendoSubcategories: List<ConsoleSubcategory> = listOf(
            ConsoleSubcategory("switch-2", "Nintendo Switch 2", "Switch 2", 2025),
            ConsoleSubcategory("switch", "Nintendo Switch", "Switch", 2017),
            ConsoleSubcategory("switch-lite", "Switch Lite", "Lite", 2019),
            ConsoleSubcategory("wii-u", "Wii U", "Wii U", 2012),
            ConsoleSubcategory("3ds", "Nintendo 3DS", "3DS", 2011)
        )
    }
}

/**
 * A console sub-category — a specific model
 * within a [DeviceCategory] (PS4 vs PS5, Xbox
 * One vs Xbox Series, etc.).
 */
data class ConsoleSubcategory(
    val id: String,
    val labelEn: String,
    val labelEs: String,
    val year: Int
)

/**
 * The primary transport a category uses. The
 * transport determines which connection flow the
 * user sees.
 */
enum class PrimaryTransport {
    INFRARED,
    BLUETOOTH,
    WIFI,
    USB
}
