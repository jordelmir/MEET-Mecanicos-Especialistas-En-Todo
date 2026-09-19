package com.elysium369.meet.ui.screens.ride

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.ui.theme.MeetColors

/**
 * RideLostAndFoundDialog — Dedicated module allowing passengers and drivers to coordinate
 * the return of forgotten items with explicit, dignity-preserving driver compensation rules:
 * - ₡3,500 CRC within 10 km (from driver location at the requested time).
 * - ₡7,000 CRC for distances exceeding 10 km from driver location.
 */
@Composable
fun RideLostAndFoundDialog(
    ride: RideRequestEntity,
    isDriver: Boolean,
    onDismiss: () -> Unit,
    onSendMessage: ((message: String) -> Unit)? = null,
    onOpenChat: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var itemDescription by remember { mutableStateOf("") }
    var reportSubmitted by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF091422)),
            border = BorderStroke(1.5.dp, if (isDriver) MeetColors.cyberCyan else MeetColors.neonGreen),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            color = if (isDriver) MeetColors.cyberCyan.copy(alpha = 0.16f) else MeetColors.neonGreen.copy(alpha = 0.16f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = if (isDriver) MeetColors.cyberCyan else MeetColors.neonGreen,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isDriver) "CENTRO DE OBJETOS OLVIDADOS" else "RECUPERAR OBJETO OLVIDADO",
                                color = if (isDriver) MeetColors.cyberCyan else MeetColors.neonGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isDriver) "Canal de entrega y atención al chofer" else "Conexión directa con tu chofer",
                                color = MeetColors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                    }
                }

                HorizontalDivider(color = MeetColors.borderSubtle)

                // MANDATORY COMPENSATION POLICY NOTICE
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF241A06),
                    border = BorderStroke(1.5.dp, Color(0xFFFFB74D)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = if (isDriver) "TARIFAS OFICIALES EN TU FAVOR" else "TARIFA DE COMPENSACIÓN AL CHOFER",
                                color = Color(0xFFFFB74D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Text(
                            text = if (isDriver) {
                                "Para dignificar tu trabajo, combustible y disponibilidad, el pasajero debe abonar obligatoriamente las siguientes tarifas al coordinar la devolución del artículo:"
                            } else {
                                "Para dignificar el trabajo, combustible y tiempo del chofer al coordinar y entregar un artículo olvidado, aplican las siguientes tarifas oficiales obligatorias en su favor:"
                            },
                            color = Color(0xFFFFF3E0),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF161004),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("📍 Hasta 10 km (desde ubicación del chofer):", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("₡3,500", color = MeetColors.neonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                                HorizontalDivider(color = Color(0xFFFFB74D).copy(alpha = 0.2f))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("🚗 Más de 10 km (desde ubicación del chofer):", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("₡7,000", color = MeetColors.neonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Text(
                            text = "ℹ️ La distancia se calcula partiendo del punto donde se encuentre el chofer al momento requerido. Pago en efectivo o SINPE directamente al chofer al recibir el objeto.",
                            color = Color(0xFFFFCC80),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }

                // Trip Information Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0E1A2B),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "DETALLES DEL VIAJE ASOCIADO",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isDriver) {
                            Text("👤 Pasajero: ${ride.passengerName}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (ride.passengerPhone.isNotBlank()) {
                                Text("📱 Teléfono: ${ride.passengerPhone}", color = MeetColors.neonGreen, fontSize = 11.sp)
                            }
                        } else {
                            Text("🚗 Chofer: ${ride.assignedDriverName ?: "Chofer asignado"}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (!ride.assignedDriverVehicle.isNullOrBlank()) {
                                Text("🚘 Vehículo: ${ride.assignedDriverVehicle}", color = MeetColors.textSecondary, fontSize = 11.sp)
                            }
                            if (!ride.assignedDriverPhone.isNullOrBlank()) {
                                Text("📱 Teléfono: ${ride.assignedDriverPhone}", color = MeetColors.neonGreen, fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = "📍 Ruta: ${ride.pickupAddress.take(30)}... → ${ride.destAddress.take(30)}...",
                            color = MeetColors.textMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // ROLE-SPECIFIC CONTENT
                if (!isDriver) {
                    // PASSENGER EXPERIENCE
                    if (!reportSubmitted) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "¿Qué artículo u objeto olvidaste en el vehículo?",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            val quickOptions = listOf("📱 Celular", "👛 Billetera", "🔑 Llaves", "🧥 Suéter", "🎒 Mochila", "🕶️ Lentes")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                quickOptions.forEach { opt ->
                                    Surface(
                                        color = Color(0xFF142436),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                                        modifier = Modifier.clickable {
                                            itemDescription = if (itemDescription.isBlank()) opt else "$itemDescription, $opt"
                                        },
                                    ) {
                                        Text(
                                            text = opt,
                                            color = MeetColors.cyberCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = itemDescription,
                                onValueChange = { itemDescription = it.take(250) },
                                placeholder = {
                                    Text(
                                        "Describe el artículo olvidado (color, marca, señas particulares)...",
                                        color = MeetColors.textMuted,
                                        fontSize = 11.sp,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.neonGreen,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }

                        Button(
                            onClick = {
                                val item = itemDescription.trim()
                                if (item.isBlank()) {
                                    Toast.makeText(context, "Por favor describe el artículo olvidado", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val message = "[OBJETO OLVIDADO]: Hola, olvidé en tu vehículo el siguiente artículo: '$item'. Entiendo y acepto la tarifa oficial de compensación al chofer por entrega (₡3,500 <=10km / ₡7,000 >10km)."
                                if (onSendMessage == null || ride.serverVersion <= 0L || ride.assignedDriverId.isNullOrBlank()) {
                                    Toast.makeText(context, "Este viaje aún no permite contactar al chofer", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                onSendMessage(message)
                                reportSubmitted = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.neonGreen,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ENVIAR REPORTE Y CONECTAR CON CHOFER", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    } else {
                        // CONFIRMATION AND DIRECT CONNECTION SCREEN
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0B2418),
                            border = BorderStroke(1.5.dp, MeetColors.neonGreen),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(36.dp))
                                Text("SOLICITUD EN PROCESO", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text(
                                    text = "Se está preparando la solicitud de '$itemDescription' para este viaje. Abre el chat para comprobar si quedó pendiente o se entregó y coordinar con el chofer.",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onOpenChat?.invoke()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.cyberCyan,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ABRIR CHAT DIRECTO CON EL CHOFER", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }

                        if (!ride.assignedDriverPhone.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ride.assignedDriverPhone}"))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, "No se pudo abrir el marcador telefónico", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.neonGreen),
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("LLAMAR AL CHOFER DIRECTAMENTE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    // DRIVER EXPERIENCE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF132235),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Panel de Entrega del Chofer",
                                    color = MeetColors.neonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "Aquí puedes coordinar con el pasajero de este viaje. Cuando el pasajero envíe su reporte o te escriba, los mensajes se sincronizan en el chat del viaje. Recuerda cobrar la tarifa obligatoria (₡3.500 o ₡7.000) por tu tiempo y combustible.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onOpenChat?.invoke()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.cyberCyan,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ABRIR CHAT CON EL PASAJERO", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    if (ride.passengerPhone.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ride.passengerPhone}"))
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(context, "No se pudo abrir el marcador telefónico", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("LLAMAR AL PASAJERO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Text("Cerrar", color = MeetColors.textSecondary)
                }
            }
        }
    }
}

/**
 * DriverLostAndFoundHubDialog — Central de Objetos Olvidados para el Conductor.
 * Permite ver todos los viajes completados, consultar los reportes de pasajeros,
 * abrir chat directo con cada pasajero y llamarlo, con recordatorio explícito
 * de tarifas de entrega garantizadas (₡3,500 <=10km / ₡7,000 >10km).
 */
@Composable
fun DriverLostAndFoundHubDialog(
    completedRides: List<RideRequestEntity>,
    reportsByRide: Map<String, List<RideChatMessageEntity>>,
    onDismiss: () -> Unit,
    onOpenChat: (RideRequestEntity) -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedRideForDetail by remember { mutableStateOf<RideRequestEntity?>(null) }

    if (selectedRideForDetail != null) {
        RideLostAndFoundDialog(
            ride = selectedRideForDetail!!,
            isDriver = true,
            onDismiss = { selectedRideForDetail = null },
            onOpenChat = {
                val r = selectedRideForDetail!!
                selectedRideForDetail = null
                onOpenChat(r)
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF07121E),
            border = BorderStroke(1.5.dp, MeetColors.neonGreen.copy(alpha = 0.6f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                // Header
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = MeetColors.neonGreen.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MeetColors.neonGreen,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "CENTRO DE OBJETOS OLVIDADOS",
                                color = MeetColors.neonGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                "Panel de atención y entrega del conductor",
                                color = MeetColors.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Dignity and Tariff Rule Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0B2418),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tarifas de compensación obligatoria:",
                                color = MeetColors.neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "• ₡3.500 dentro de 10 km (desde tu posición actual).\n• ₡7.000 a más de 10 km de distancia.",
                                color = Color.White,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Rides List
                val filteredRides = remember(completedRides, reportsByRide, searchQuery) {
                    if (searchQuery.isBlank()) completedRides.sortedWith(compareByDescending<RideRequestEntity> { reportsByRide[it.requestId]?.maxOfOrNull { report -> report.createdAt } ?: 0L }.thenByDescending { it.completedAt ?: it.createdAt })
                    else completedRides.filter {
                        it.pickupAddress.contains(searchQuery, ignoreCase = true) ||
                            it.destAddress.contains(searchQuery, ignoreCase = true) ||
                            it.passengerName.contains(searchQuery, ignoreCase = true)
                    }.sortedByDescending { it.completedAt ?: it.createdAt }
                }

                if (filteredRides.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📦", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No hay viajes finalizados registrados aún.",
                                color = MeetColors.textMuted,
                                fontSize = 12.sp,
                            )
                            Text(
                                "Cuando completes viajes con pasajeros, aparecerán aquí para coordinar entregas.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredRides) { ride ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A29)),
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "Pasajero: ${ride.passengerName.ifBlank { "Pasajero" }}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            ride.status,
                                            color = MeetColors.neonGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val reports = reportsByRide[ride.requestId].orEmpty()
                                    Text(
                                        if (reports.isEmpty()) "Sin reporte de objeto registrado" else "${reports.size} reporte(s) de objeto · ${reports.first().textContent.orEmpty().take(90)}",
                                        color = if (reports.isEmpty()) MeetColors.textMuted else MeetColors.neonGreen,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "📍 ${ride.pickupAddress}",
                                        color = MeetColors.textMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "🏁 ${ride.destAddress}",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                onOpenChat(ride)
                                                onDismiss()
                                            },
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MeetColors.cyberCyan,
                                                contentColor = Color.Black,
                                            ),
                                        ) {
                                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Abrir Chat", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }

                                        if (ride.passengerPhone.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    runCatching {
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ride.passengerPhone}"))
                                                        context.startActivity(intent)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.neonGreen),
                                            ) {
                                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Llamar", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { selectedRideForDetail = ride },
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.textSecondary),
                                        ) {
                                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Detalles del viaje")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Text("Volver", color = MeetColors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
