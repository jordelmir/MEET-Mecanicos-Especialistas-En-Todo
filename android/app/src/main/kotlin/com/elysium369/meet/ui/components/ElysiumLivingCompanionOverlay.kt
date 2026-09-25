package com.elysium369.meet.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.elysium369.meet.automation.AiAction
import com.elysium369.meet.automation.AiAutomationBridge
import com.elysium369.meet.core.agentstore.domain.OfficialAgents
import com.elysium369.meet.core.agentstore.ui.Agent3dAvatarCanvas
import com.elysium369.meet.ride.domain.RideCancellationReason
import com.elysium369.meet.ride.domain.RidePassengerPreferences
import com.elysium369.meet.ride.domain.RidePetType
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.navigation.MeetDestinations
import com.elysium369.meet.ui.navigation.safeNavigate
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pasos de la máquina conversacional de reserva de viajes
 */
enum class RideBookingStep {
    IDLE,
    ASKING_PICKUP,       // "¿Ubicación actual GPS o recogida especial?"
    ASKING_DESTINATION,  // "¿Hacia dónde te diriges? Dime tu destino."
    ASKING_PAYMENT,      // "¿Efectivo o SINPE Móvil?"
    ASKING_PREFERENCES,  // "¿Llevas niños, mascotas (perro/gato), o son 5 personas?"
    CONFIRMING_SUMMARY,  // "¿Confirmas publicar el viaje? Di Sí para confirmar o Cancelar"
}

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   L I V I N G   C O M P A N I O N   O V E R L A Y
 *  ──────────────────────────────────────────────────────────────
 *  Compañero 3D Vivo Flotante omnipresente con Inteligencia de Voz Conversacional:
 *  - Ubicación inicial: CENTRO SUPERIOR de la pantalla al iniciar la app.
 *  - Arrastrable libremente por toda la pantalla.
 *  - Flujo guiado de viaje por voz: Recogida -> Destino -> Pago -> Preferencias -> Publicación.
 *  - Cancelación de viaje por voz en cualquier estado.
 *  - Lado del chofer: Aceptación de viaje por voz (por nombre del pasajero o viaje único),
 *    y avance de estados (llegué, pasajero a bordo, completar).
 *  - Reconocimiento de Voz bidireccional (SpeechRecognizer es-CR + TTS).
 * ══════════════════════════════════════════════════════════════════════
 */
@Composable
fun ElysiumLivingCompanionOverlay(
    navController: NavController,
    obdViewModel: ObdViewModel,
    activeRoute: String?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()

    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    // Roster of unlocked companions
    val availableCompanions = remember { OfficialAgents.ALL }
    var equippedCompanion by remember { mutableStateOf(availableCompanions.first()) } // Default: Draco Dragon

    // POSICIÓN INICIAL: Centro en la parte superior al abrir el APK
    var offsetX by remember { mutableFloatStateOf((screenWidthPx / 2f) - with(density) { 40.dp.toPx() }) }
    var offsetY by remember { mutableFloatStateOf(with(density) { 56.dp.toPx() }) }

    var isMenuOpen by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
    var speechBubbleText by remember { mutableStateOf<String?>("¡Draco listo! Tócame para hablar.") }
    var customCommandText by remember { mutableStateOf("") }

    // Estado conversacional de reserva de viajes
    var bookingStep by remember { mutableStateOf(RideBookingStep.IDLE) }
    var pickupLocation by remember { mutableStateOf("Ubicación actual (GPS)") }
    var destinationLocation by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var petType by remember { mutableStateOf(RidePetType.NONE) }
    var kidsCount by remember { mutableIntStateOf(0) }
    var isFivePassengers by remember { mutableStateOf(false) }

    // Datos reactivos del sistema de viajes
    val openRides by obdViewModel.openRideRequests.collectAsState(initial = emptyList())
    val activeRide by obdViewModel.activeRideRequest.collectAsState(initial = null)
    val isDriver by obdViewModel.rideDriverMode.collectAsState(initial = false)

    // Text to speech instance
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "CR")
            }
        }
        onDispose {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {}
        }
    }

    // Declaración previa de SpeechRecognizer
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun startVoiceInput() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        if (speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (_: Exception) {
                isListening = false
            }
        }
    }

    fun stopVoiceInput() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isListening = false
    }

    // Permission launcher for microphone
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceInput()
        } else {
            speechBubbleText = "Permiso de micrófono no otorgado."
        }
    }

    // Motor de ejecución de intenciones por voz
    fun executeCompanionCommand(rawText: String) {
        val query = rawText.lowercase().trim()
        val persona = equippedCompanion.avatarVisualType

        // ── 1. CANCELACIÓN DE VIAJE (PASAJERO Y CHOFER) ──
        if (query.contains("cancelar viaje") || query.contains("cancelar el viaje") ||
            query.contains("anular viaje") || query.contains("ya no quiero el viaje") ||
            query.contains("cancelar solicitud")
        ) {
            if (bookingStep != RideBookingStep.IDLE) {
                bookingStep = RideBookingStep.IDLE
                val resp = "Solicitud de viaje cancelada."
                speechBubbleText = "❌ $resp"
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "cancel_booking")
                return
            }
            val ride = activeRide
            if (ride != null) {
                val reason = if (isDriver) RideCancellationReason.PASSENGER_NO_SHOW else RideCancellationReason.CHANGE_OF_PLANS
                obdViewModel.cancelRide(
                    requestId = ride.requestId,
                    reason = reason,
                    detail = "Cancelado por orden de voz del usuario",
                    actorRole = if (isDriver) "DRIVER" else "PASSENGER"
                )
                val resp = "Tu viaje ha sido cancelado con éxito."
                speechBubbleText = "❌ $resp"
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "cancel_active")
                return
            } else {
                obdViewModel.clearAllStuckRides()
                val resp = "No tienes ningún viaje activo. He liberado cualquier solicitud pendiente."
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "no_active_ride")
                return
            }
        }

        // ── 2. LADO DEL CHOFER: ACEPTAR VIAJE POR VOZ (CON NOMBRE O ÚNICO) Y AVANCE ──
        if (isDriver || query.contains("modo chofer") || query.contains("soy chofer")) {
            if (query.contains("aceptar")) {
                // Caso A: Aceptar viaje con nombre específico
                val nameQuery = when {
                    query.contains("aceptar viaje de ") -> query.substringAfter("aceptar viaje de ").trim()
                    query.contains("aceptar el viaje de ") -> query.substringAfter("aceptar el viaje de ").trim()
                    query.contains("aceptar a ") -> query.substringAfter("aceptar a ").trim()
                    else -> null
                }

                if (nameQuery != null && nameQuery.isNotBlank()) {
                    val targetRide = openRides.find {
                        it.passengerName.lowercase().contains(nameQuery.lowercase())
                    }
                    if (targetRide != null) {
                        obdViewModel.makeRideOffer(
                            requestId = targetRide.requestId,
                            counterPrice = targetRide.priceOffer,
                            currency = targetRide.currency,
                            estArrivalMin = 8,
                            message = "Aceptado por comando de voz"
                        )
                        val resp = "¡Aceptando el viaje de ${targetRide.passengerName}! Oferta de ₡${targetRide.priceOffer.toInt()} enviada."
                        speechBubbleText = "✓ Viaje de ${targetRide.passengerName} aceptado."
                        tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_accept_name")
                        return
                    } else {
                        val resp = "No encontré solicitudes de $nameQuery. Solicitudes activas: ${openRides.joinToString { it.passengerName }}."
                        speechBubbleText = resp
                        tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_not_found")
                        return
                    }
                }

                // Caso B: Aceptar viaje si hay 1 solo
                if (openRides.size == 1) {
                    val singleRide = openRides.first()
                    obdViewModel.makeRideOffer(
                        requestId = singleRide.requestId,
                        counterPrice = singleRide.priceOffer,
                        currency = singleRide.currency,
                        estArrivalMin = 8,
                        message = "Aceptado por comando de voz"
                    )
                    val resp = "¡Viaje de ${singleRide.passengerName} aceptado! En camino a recoger al pasajero."
                    speechBubbleText = "✓ Viaje de ${singleRide.passengerName} aceptado."
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_accept_single")
                    return
                } else if (openRides.size > 1) {
                    val names = openRides.take(3).joinToString(", ") { it.passengerName }
                    val resp = "Hay ${openRides.size} viajes disponibles: $names. Di: 'Aceptar viaje de' y el nombre del pasajero."
                    speechBubbleText = "Hay ${openRides.size} viajes. Di el nombre."
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_multi_rides")
                    return
                } else {
                    val resp = "No hay solicitudes de viaje abiertas en este momento."
                    speechBubbleText = resp
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_no_rides")
                    return
                }
            }

            // Comandos de avance de estado para chofer
            if (query.contains("llegué") || query.contains("ya llegue") || query.contains("estoy en el punto")) {
                val ride = activeRide
                if (ride != null) {
                    AiAutomationBridge.dispatchAction(AiAction.AdvanceRideStatus(ride.requestId, "ARRIVED"))
                    val resp = "Notificando al pasajero que has llegado al punto de recogida."
                    speechBubbleText = "📍 Chofer en el punto de recogida."
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_arrived")
                    return
                }
            }
            if (query.contains("pasajero a bordo") || query.contains("iniciar viaje") || query.contains("comenzar viaje")) {
                val ride = activeRide
                if (ride != null) {
                    AiAutomationBridge.dispatchAction(AiAction.AdvanceRideStatus(ride.requestId, "IN_PROGRESS"))
                    val resp = "Abordaje verificado. Viaje iniciado hacia el destino."
                    speechBubbleText = "🚗 Viaje en curso."
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_in_progress")
                    return
                }
            }
            if (query.contains("completar viaje") || query.contains("terminar viaje") || query.contains("finalizar viaje")) {
                val ride = activeRide
                if (ride != null) {
                    AiAutomationBridge.dispatchAction(AiAction.AdvanceRideStatus(ride.requestId, "COMPLETED"))
                    val resp = "¡Viaje completado exitosamente! Aplicando comisión oficial del 5%."
                    speechBubbleText = "✅ Viaje completado."
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "driver_completed")
                    return
                }
            }
        }

        // ── 3. MÁQUINA CONVERSACIONAL GUIADA: PEDIR VIAJE (PASAJERO) ──
        when (bookingStep) {
            RideBookingStep.ASKING_PICKUP -> {
                pickupLocation = if (query.contains("actual") || query.contains("aquí") || query.contains("gps") || query.contains("donde estoy")) {
                    "Ubicación actual (GPS)"
                } else {
                    rawText.trim().replaceFirstChar { it.uppercase() }
                }
                bookingStep = RideBookingStep.ASKING_DESTINATION
                val resp = "Recogida fijada en: $pickupLocation. ¿Hacia dónde te diriges? Dime tu destino."
                speechBubbleText = "🏁 ¿Cuál es tu destino?"
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "ask_dest")
                coroutineScope.launch {
                    delay(3800)
                    startVoiceInput()
                }
                return
            }

            RideBookingStep.ASKING_DESTINATION -> {
                destinationLocation = rawText.removePrefix("a ").removePrefix("hacia ").removePrefix("al ").trim().replaceFirstChar { it.uppercase() }
                bookingStep = RideBookingStep.ASKING_PAYMENT
                val resp = "Destino a $destinationLocation. ¿Cómo deseas pagar? ¿En efectivo o por SINPE Móvil?"
                speechBubbleText = "💵 ¿Efectivo o SINPE Móvil?"
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "ask_payment")
                coroutineScope.launch {
                    delay(3800)
                    startVoiceInput()
                }
                return
            }

            RideBookingStep.ASKING_PAYMENT -> {
                paymentMethod = if (query.contains("sinpe") || query.contains("transferencia") || query.contains("móvil")) {
                    "SINPE"
                } else {
                    "CASH"
                }
                bookingStep = RideBookingStep.ASKING_PREFERENCES
                val payLabel = if (paymentMethod == "SINPE") "SINPE Móvil" else "Efectivo"
                val resp = "Pago por $payLabel. Por último: ¿Llevas niños, mascotas como perro o gato, o son 5 pasajeros?"
                speechBubbleText = "🐾/👶 ¿Llevas niños, mascotas o 5 personas?"
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "ask_prefs")
                coroutineScope.launch {
                    delay(4200)
                    startVoiceInput()
                }
                return
            }

            RideBookingStep.ASKING_PREFERENCES -> {
                petType = when {
                    query.contains("perro") -> RidePetType.DOG
                    query.contains("gato") -> RidePetType.CAT
                    else -> RidePetType.NONE
                }
                kidsCount = when {
                    query.contains("2") || query.contains("dos niños") -> 2
                    query.contains("3") || query.contains("tres niños") -> 3
                    query.contains("4") || query.contains("cuatro niños") -> 4
                    query.contains("niño") || query.contains("bebé") || query.contains("hijo") || query.contains("hija") || query.contains("un niño") -> 1
                    else -> 0
                }
                isFivePassengers = query.contains("5") || query.contains("cinco") || query.contains("más de cuatro")

                bookingStep = RideBookingStep.CONFIRMING_SUMMARY

                val payLabel = if (paymentMethod == "SINPE") "SINPE Móvil" else "Efectivo"
                val petStr = if (petType != RidePetType.NONE) ", con mascota (${petType.displayName})" else ""
                val kidStr = if (kidsCount > 0) ", $kidsCount niño(s)" else ""
                val fiveStr = if (isFivePassengers) ", 5 pasajeros" else ""

                val summaryMsg = "Resumen de viaje: De $pickupLocation hacia $destinationLocation, pago en $payLabel$petStr$kidStr$fiveStr. ¿Confirmas publicar este viaje? Di Sí para confirmar o Cancelar."
                speechBubbleText = "📋 ¿Confirmas viaje a $destinationLocation? (Di Sí o Cancelar)"
                tts?.speak(summaryMsg, TextToSpeech.QUEUE_FLUSH, null, "confirm_ride_summary")
                coroutineScope.launch {
                    delay(5200)
                    startVoiceInput()
                }
                return
            }

            RideBookingStep.CONFIRMING_SUMMARY -> {
                val isAffirmative = query.contains("si") || query.contains("sí") || query.contains("confirmo") ||
                        query.contains("confirmar") || query.contains("dale") || query.contains("publicar") ||
                        query.contains("adelante") || query.contains("claro") || query.contains("por favor") || query.contains("ok")
                val isNegative = query.contains("no") || query.contains("cancelar") || query.contains("espera") ||
                        query.contains("detener") || query.contains("alto") || query.contains("abortar")

                if (isAffirmative) {
                    bookingStep = RideBookingStep.IDLE
                    val summaryMsg = "¡Entendido y confirmado por ti! Publicando tu viaje hacia $destinationLocation en la red oficial MEET."
                    speechBubbleText = "✓ Publicando viaje a $destinationLocation"
                    tts?.speak(summaryMsg, TextToSpeech.QUEUE_FLUSH, null, "publish_ride")

                    // Asegurar modo pasajero y navegación
                    obdViewModel.setRideDriverMode(false)
                    navController.safeNavigate(MeetDestinations.RIDE_HOME)

                    // Crear solicitud oficial de viaje
                    AiAutomationBridge.dispatchAction(
                        AiAction.CreateRide(
                            pickupAddress = pickupLocation,
                            pickupLat = 9.9333,
                            pickupLng = -84.0833,
                            destAddress = destinationLocation,
                            destLat = 9.8644,
                            destLng = -83.9194,
                            priceOffer = 4500.0,
                            currency = "CRC"
                        )
                    )
                    return
                } else if (isNegative) {
                    bookingStep = RideBookingStep.IDLE
                    val resp = "Entendido, no publicamos el viaje. La solicitud ha sido cancelada. Estoy aquí cuando me necesites."
                    speechBubbleText = "❌ Viaje no publicado (cancelado por usuario)"
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "cancel_ride_summary")
                    return
                } else {
                    val resp = "¿Confirmas publicar el viaje hacia $destinationLocation? Di Sí para publicar o Cancelar."
                    speechBubbleText = "¿Confirmas publicar? Di Sí o Cancelar."
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "retry_confirm_ride")
                    coroutineScope.launch {
                        delay(4000)
                        startVoiceInput()
                    }
                    return
                }
            }

            RideBookingStep.IDLE -> {
                // Detectar inicio de pedir viaje
                val isRideIntent = query.contains("viaje") || query.contains("viajes") || query.contains("ride") ||
                        query.contains("carrera") || query.contains("transporte") || query.contains("uber") ||
                        query.contains("llevarme") || query.contains("pedir")

                if (isRideIntent && !isDriver) {
                    // Si el usuario proporcionó destino en la misma frase (ej: "pedirme un viaje a Cartago")
                    if (query.contains(" a ") || query.contains(" para ")) {
                        val candDest = if (query.contains(" a ")) query.substringAfter(" a ").trim() else query.substringAfter(" para ").trim()
                        destinationLocation = candDest.replaceFirstChar { it.uppercase() }
                    }

                    bookingStep = RideBookingStep.ASKING_PICKUP
                    obdViewModel.setRideDriverMode(false)
                    navController.safeNavigate(MeetDestinations.RIDE_HOME)

                    val resp = "¡Con gusto! Vamos a coordinar tu viaje paso a paso. ¿Usamos tu ubicación GPS actual o deseas que te recoja en otro punto?"
                    speechBubbleText = "📍 ¿Ubicación GPS actual o especial?"
                    tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "start_guided_ride")
                    coroutineScope.launch {
                        delay(4500)
                        startVoiceInput()
                    }
                    return
                }
            }
        }

        // ── 4. OTROS SERVICIOS Y COMANDOS DEL ECOSISTEMA ──
        val isTow = query.contains("grua") || query.contains("grúa") || query.contains("remolque")
        val isMechanic = query.contains("mecanico") || query.contains("mecánico") || query.contains("taller")
        val isParts = query.contains("repuesto") || query.contains("tienda") || query.contains("part")
        val isPulperia = query.contains("pulperia") || query.contains("pulpería") || query.contains("abarrote") || query.contains("minisuper") || query.contains("minisúper") || query.contains("mandado")
        val isSoda = query.contains("soda") || query.contains("restaurante") || query.contains("comida") || query.contains("casado") || query.contains("almuerzo") || query.contains("desayuno")
        val isServiciosActivos = query.contains("servicios activos") || query.contains("viajes activos") || query.contains("pedidos activos") || query.contains("mis servicios")
        val isServiciosFinalizados = query.contains("servicios finalizados") || query.contains("historial") || query.contains("pedidos finalizados") || query.contains("completados")
        val isScanner = query.contains("scanner") || query.contains("escan") || query.contains("falla") || query.contains("dtc")
        val isDragon = query.contains("dragon") || query.contains("dragón") || query.contains("draco")
        val isVolt = query.contains("volt") || query.contains("sparky") || query.contains("pokemon") || query.contains("eléctrico")
        val isSaiyan = query.contains("goku") || query.contains("saiyajin") || query.contains("sayayin")

        when {
            isPulperia -> {
                val resp = "Abriendo Pulpería Express. Pide tus abarrotes con chofer repartidor de MEET a tu puerta. 🏪"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "pulperia")
                navController.safeNavigate("services_active?tab=active")
            }
            isSoda -> {
                val resp = "Abriendo Sodas y Restaurantes criollos. Casados y comida casera con entrega express. 🍳"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "soda")
                navController.safeNavigate("services_active?tab=active")
            }
            isServiciosActivos -> {
                val resp = "Abriendo el panel de Servicios y Pedidos Activos. ⚡"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "activos")
                navController.safeNavigate("services_active?tab=active")
            }
            isServiciosFinalizados -> {
                val resp = "Abriendo el historial de Servicios y Viajes Finalizados. 📜"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "finalizados")
                navController.safeNavigate("services_active?tab=completed")
            }
            isTow -> {
                val resp = "Localizando servicio de grúa de emergencia. 🚨"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "tow")
                navController.safeNavigate(MeetDestinations.TOW_TRUCK)
            }
            isMechanic -> {
                val resp = "Abriendo la red de talleres y especialistas certificados. 🔧"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "mechanic")
                navController.safeNavigate(MeetDestinations.MECHANIC_SERVICES)
            }
            isParts -> {
                val resp = "Abriendo el marketplace técnico de repuestos con compatibilidad VIN. 📦"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "parts")
                navController.safeNavigate(MeetDestinations.PARTS_STORE)
            }
            isScanner -> {
                val resp = "Iniciando escaneo de la computadora del vehículo. ⏱️"
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "scanner")
                navController.safeNavigate(MeetDestinations.SCANNER)
            }
            isDragon -> {
                availableCompanions.find { it.avatarVisualType == "DRAGON" }?.let { equippedCompanion = it }
                val resp = "¡Draco Ignis despierta! Fuego en los cilindros."
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "switch_dragon")
            }
            isVolt -> {
                availableCompanions.find { it.avatarVisualType == "POKEMON_VOLT" }?.let { equippedCompanion = it }
                val resp = "¡Volt Sparky listo! Batería al 100%."
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "switch_volt")
            }
            isSaiyan -> {
                availableCompanions.find { it.avatarVisualType == "SAIYAN_SSJ4" }?.let { equippedCompanion = it }
                val resp = "¡Goku SSJ4 al mando! Ki protector al máximo."
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "switch_saiyan")
            }
            query.contains("ocultar") || query.contains("cerrar") -> {
                isVisible = false
                tts?.speak("Compañero minimizado.", TextToSpeech.QUEUE_FLUSH, null, "hide")
            }
            else -> {
                val resp = "Te escuché: “$rawText”. Prueba decir: 'Pedirme un viaje', 'Aceptar viaje', 'Cancelar viaje' o 'Grúa'."
                speechBubbleText = resp
                tts?.speak(resp, TextToSpeech.QUEUE_FLUSH, null, "general")
            }
        }
    }

    // Inicialización del SpeechRecognizer
    DisposableEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        speechBubbleText = when (bookingStep) {
                            RideBookingStep.ASKING_PICKUP -> "🎙️ Dime: 'Actual' o tu dirección"
                            RideBookingStep.ASKING_DESTINATION -> "🎙️ Dime tu destino..."
                            RideBookingStep.ASKING_PAYMENT -> "🎙️ Dime: 'Efectivo' o 'SINPE'"
                            RideBookingStep.ASKING_PREFERENCES -> "🎙️ ¿Niños, mascota o 5 personas?"
                            else -> "🎙️ Te escucho... Di tu orden"
                        }
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                        speechBubbleText = "Procesando orden..."
                    }
                    override fun onError(error: Int) {
                        isListening = false
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            executeCompanionCommand(text)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            speechBubbleText = "🗣️ \"$it...\""
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            speechRecognizer = recognizer
        }
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    // Colectar eventos de voz externos por ADB o Automation Bridge
    LaunchedEffect(Unit) {
        AiAutomationBridge.actionEvents.collect { action ->
            if (action is AiAction.VoiceCommand) {
                executeCompanionCommand(action.command)
            }
        }
    }

    // Bocadillos vivos contextuales según la pantalla activa
    LaunchedEffect(activeRoute, equippedCompanion) {
        delay(1200)
        if (bookingStep == RideBookingStep.IDLE) {
            speechBubbleText = when (activeRoute) {
                "home" -> when (equippedCompanion.avatarVisualType) {
                    "DRAGON" -> "¡El motor ruge! 🔥 Pídeme un viaje."
                    "POKEMON_VOLT" -> "¡Pika-volt! ⚡ Listo para viajar."
                    "SAIYAN_SSJ4" -> "¡Ki protector activo! 💥 Tócame para hablar."
                    else -> "A tu lado en el camino. Tócame para hablar."
                }
                "ride_service", "rides" -> if (isDriver) {
                    if (openRides.isNotEmpty()) "¡Hay ${openRides.size} viajes! Di: Aceptar viaje" else "Modo Chofer activo. Esperando viajes."
                } else {
                    "Pídeme un viaje guiado paso a paso."
                }
                "scanner", "obd" -> "Monitoreando sensores en vivo ⏱️"
                "dtcs" -> "Escaneando fallas de motor..."
                "elysium_services" -> "¿Ocupas grúa o cerrajero? Tócame 🚨"
                "agent_store" -> "¡Todos los agentes están desbloqueados! ✨"
                else -> "A tu lado en el camino. Tócame para hablar."
            }
            delay(7000)
            if (!isListening && bookingStep == RideBookingStep.IDLE) {
                speechBubbleText = null
            }
        }
    }

    if (!isVisible) {
        // Minimized pill in top right corner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Surface(
                modifier = Modifier.clickable { isVisible = true },
                shape = CircleShape,
                color = Color(0xCC071424),
                border = BorderStroke(1.dp, MeetColors.cyberCyan)
            ) {
                Text(
                    text = "🤖 Abrir Agente 3D",
                    color = MeetColors.cyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(10f, screenWidthPx - 210f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(40f, screenHeightPx - 240f)
                    }
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Speech Bubble
                AnimatedVisibility(
                    visible = speechBubbleText != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    speechBubbleText?.let { text ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xF2071626),
                            border = BorderStroke(
                                1.dp,
                                if (isListening) MeetColors.neonGreen else Color(equippedCompanion.themeColorHex)
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = text,
                                color = if (isListening) MeetColors.neonGreen else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 3D Living Character Container with Mic Indicator
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing green aura while listening
                    if (isListening) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.30f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MeetColors.neonGreen.copy(alpha = 0.35f))
                        )
                    }

                    // Main Avatar Surface (Tap toggles voice listening)
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(12.dp, CircleShape)
                            .clickable {
                                if (isListening) {
                                    stopVoiceInput()
                                } else {
                                    startVoiceInput()
                                }
                            },
                        shape = CircleShape,
                        color = Color(0xEA06111D),
                        border = BorderStroke(
                            2.5.dp,
                            if (isListening) MeetColors.neonGreen else Color(equippedCompanion.themeColorHex)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Agent3dAvatarCanvas(
                                avatarVisualType = equippedCompanion.avatarVisualType,
                                themeColor = if (isListening) MeetColors.neonGreen else Color(equippedCompanion.themeColorHex),
                                modifier = Modifier.size(68.dp),
                                isInteractive = false,
                                isPulsing = isListening
                            )
                        }
                    }

                    // Floating Mic Action Badge (Bottom-Right)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clickable {
                                if (isListening) stopVoiceInput() else startVoiceInput()
                            },
                        shape = CircleShape,
                        color = if (isListening) MeetColors.neonGreen else Color(0xFF0C1F33),
                        border = BorderStroke(1.5.dp, if (isListening) Color.White else Color(equippedCompanion.themeColorHex)),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Hablar al Agente",
                                tint = if (isListening) Color.Black else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Floating Menu / Cog Badge (Top-Right)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .clickable { isMenuOpen = true },
                        shape = CircleShape,
                        color = Color(0xDD071424),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Opciones",
                                tint = MeetColors.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialog: Menú Táctico del Compañero Vivo ──
    if (isMenuOpen) {
        Dialog(onDismissRequest = { isMenuOpen = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(0.96f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF071424)),
                border = BorderStroke(1.5.dp, Color(equippedCompanion.themeColorHex))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with 3D Canvas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0x2200E5FF))
                                .border(1.5.dp, Color(equippedCompanion.themeColorHex), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Agent3dAvatarCanvas(
                                avatarVisualType = equippedCompanion.avatarVisualType,
                                themeColor = Color(equippedCompanion.themeColorHex),
                                modifier = Modifier.size(54.dp),
                                isInteractive = false
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = equippedCompanion.displayName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MeetColors.neonGreen.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isDriver) "MODO CHOFER" else "PASAJERO",
                                        color = MeetColors.neonGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = equippedCompanion.subtitle,
                                color = Color(equippedCompanion.themeColorHex),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = { isMenuOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Voice Activation Big Button
                    Button(
                        onClick = {
                            if (isListening) {
                                stopVoiceInput()
                            } else {
                                startVoiceInput()
                            }
                            isMenuOpen = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) MeetColors.neonGreen else Color(0xFF1E3A5F)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isListening) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isListening) "DETENER ESCUCHA ⏹️" else "🎙️ HABLAR AHORA AL AGENTE",
                            color = if (isListening) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Indicador interactivo del paso conversacional
                    if (bookingStep != RideBookingStep.IDLE) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x3300E5FF),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "FLUJO GUIADO DE VIAJE ACTIVO:",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = when (bookingStep) {
                                        RideBookingStep.ASKING_PICKUP -> "1. Esperando Origen: ¿Ubicación GPS actual o especial?"
                                        RideBookingStep.ASKING_DESTINATION -> "2. Esperando Destino: Recogida en $pickupLocation"
                                        RideBookingStep.ASKING_PAYMENT -> "3. Esperando Método: Destino $destinationLocation"
                                        RideBookingStep.ASKING_PREFERENCES -> "4. Esperando Preferencias: Pago $paymentMethod"
                                        else -> ""
                                    },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Acciones Rápidas según rol (Chofer vs Pasajero)
                    Text(
                        text = if (isDriver) "ÓRDENES RÁPIDAS DE CHOFER:" else "ÓRDENES RÁPIDAS DE VIAJE:",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    if (isDriver) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isMenuOpen = false
                                        executeCompanionCommand("aceptar viaje")
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x3300E676),
                                border = BorderStroke(1.dp, MeetColors.neonGreen)
                            ) {
                                Text(
                                    text = "✓ Aceptar Viaje",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isMenuOpen = false
                                        executeCompanionCommand("ya llegué")
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x3300E5FF),
                                border = BorderStroke(1.dp, MeetColors.cyberCyan)
                            ) {
                                Text(
                                    text = "📍 Ya Llegué",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isMenuOpen = false
                                        executeCompanionCommand("completar viaje")
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x33FFB300),
                                border = BorderStroke(1.dp, Color(0xFFFFB300))
                            ) {
                                Text(
                                    text = "🏁 Completar",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isMenuOpen = false
                                        executeCompanionCommand("pedirme un viaje")
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x3300E5FF),
                                border = BorderStroke(1.dp, MeetColors.cyberCyan)
                            ) {
                                Text(
                                    text = "🚗 Pedir Viaje",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isMenuOpen = false
                                        executeCompanionCommand("cancelar viaje")
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x33FF5252),
                                border = BorderStroke(1.dp, Color(0xFFFF5252))
                            ) {
                                Text(
                                    text = "❌ Cancelar Viaje",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isMenuOpen = false
                                        executeCompanionCommand("pedir grua")
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x33FFB300),
                                border = BorderStroke(1.dp, Color(0xFFFFB300))
                            ) {
                                Text(
                                    text = "🚨 Grúa",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Text Input Bar for manual commands
                    OutlinedTextField(
                        value = customCommandText,
                        onValueChange = { customCommandText = it },
                        placeholder = { Text("Escribe una orden por voz...", color = MeetColors.textSecondary, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(equippedCompanion.themeColorHex),
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (customCommandText.isNotBlank()) {
                                        val cmd = customCommandText
                                        customCommandText = ""
                                        isMenuOpen = false
                                        executeCompanionCommand(cmd)
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = Color(equippedCompanion.themeColorHex))
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "CAMBIAR DE PERSONAJE 3D",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // Character selector carousel
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableCompanions) { comp ->
                            val isSelected = comp.id == equippedCompanion.id
                            Surface(
                                modifier = Modifier.clickable {
                                    equippedCompanion = comp
                                    val ttsMsg = "Cambiando a ${comp.displayName}. ¡Sistemas en línea!"
                                    tts?.speak(ttsMsg, TextToSpeech.QUEUE_FLUSH, null, "comp_switch")
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(comp.themeColorHex).copy(alpha = 0.25f) else Color(0xFF0C1D30),
                                border = BorderStroke(1.5.dp, if (isSelected) Color(comp.themeColorHex) else MeetColors.borderSubtle)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier.size(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Agent3dAvatarCanvas(
                                            avatarVisualType = comp.avatarVisualType,
                                            themeColor = Color(comp.themeColorHex),
                                            modifier = Modifier.size(38.dp),
                                            isInteractive = false
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = comp.displayName.split(" ").first(),
                                        color = if (isSelected) Color.White else MeetColors.textSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isMenuOpen = false
                                navController.safeNavigate(MeetDestinations.AGENT_STORE)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan)
                        ) {
                            Text("Agent Store", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                isVisible = false
                                isMenuOpen = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ocultar", color = MeetColors.textSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
