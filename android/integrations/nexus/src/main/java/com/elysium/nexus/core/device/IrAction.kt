package com.elysium.nexus.core.device

/**
 * High-level semantic actions for universal peripheral interaction.
 *
 * Decouples user intent (e.g. Volume Up) from physical protocol frame formats
 * and brand-specific command code bytes.
 */
enum class IrAction(val displayNameEs: String, val displayNameEn: String) {
    POWER_TOGGLE("Encendido / Apagado", "Power Toggle"),
    POWER_ON("Encender", "Power On"),
    POWER_OFF("Apagar", "Power Off"),
    VOLUME_UP("Subir Volumen", "Volume Up"),
    VOLUME_DOWN("Bajar Volumen", "Volume Down"),
    MUTE("Silenciar", "Mute"),
    CHANNEL_UP("Canal Arriba", "Channel Up"),
    CHANNEL_DOWN("Canal Abajo", "Channel Down"),
    INPUT("Entrada / Source", "Input Source"),
    HOME("Inicio / Smart", "Home"),
    UP("Arriba", "Up"),
    DOWN("Abajo", "Down"),
    LEFT("Izquierda", "Left"),
    RIGHT("Derecha", "Right"),
    OK("Aceptar / OK", "OK / Enter"),
    BACK("Volver / Atrás", "Back"),
    MENU("Menú", "Menu"),
    PLAY("Reproducir", "Play"),
    PAUSE("Pausar", "Pause"),
    STOP("Detener", "Stop"),
    NUM_0("0", "0"),
    NUM_1("1", "1"),
    NUM_2("2", "2"),
    NUM_3("3", "3"),
    NUM_4("4", "4"),
    NUM_5("5", "5"),
    NUM_6("6", "6"),
    NUM_7("7", "7"),
    NUM_8("8", "8"),
    NUM_9("9", "9"),
    NUM_DASH("-", "-"),
    NUM_PLUS("+", "+"),
    INFO("Info", "Info"),
    LAST_CHANNEL("Último Canal", "Last Channel"),
    NETFLIX("Netflix", "Netflix"),
    YOUTUBE("YouTube", "YouTube")
}
