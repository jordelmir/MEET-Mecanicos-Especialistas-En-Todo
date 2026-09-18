package com.elysium369.meet.ui.screens.ride

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ui.theme.MeetColors

/**
 * RideLostAndFoundDialog — Dedicated module allowing passengers and drivers to coordinate
 * the return of forgotten items with explicit, dignity-preserving driver compensation rules:
 * - ₡3,500 CRC within 10 km (from driver's location at the requested time).
 * - ₡7,000 CRC for distances exceeding 10 km from driver's location.
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
    var showConfirmation by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1524)),
            border = BorderStroke(1.5.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
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
                    ) {
                        Surface(
                            color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "OBJETOS OLVIDADOS",
                                color = MeetColors.cyberCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                            )
                            Text(
                                text = if (isDriver) "Información y tarifas para el chofer" else "Recuperación de pertenencias",
                                color = MeetColors.textMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                    }
                }

                HorizontalDivider(color = MeetColors.borderSubtle)

                // ═══ MANDATORY COMPENSATION POLICY NOTICE ═══
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF2A1F08),
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
                                text = "TARIFA DE COMPENSACIÓN OBLIGATORIA",
                                color = Color(0xFFFFB74D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp,
                            )
                        }
                        Text(
                            text = "Para dignificar el trabajo, combustible y tiempo del chofer al coordinar y entregar un artículo olvidado, aplican las siguientes tarifas oficiales en su favor:",
                            color = Color(0xFFFFF3E0),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1B1405),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("📍 Hasta 10 km (desde su ubicación):", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("₡3,500", color = MeetColors.neonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                                HorizontalDivider(color = Color(0xFFFFB74D).copy(alpha = 0.2f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("🚗 Más de 10 km (desde su ubicación):", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("₡7,000", color = MeetColors.neonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Text(
                            text = "ℹ️ La distancia se calcula partiendo del punto donde se encuentre el chofer al momento requerido. El pago debe efectuarse en efectivo o SINPE directamente al chofer al recibir el objeto.",
                            color = Color(0xFFFFCC80),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }

                // Trip Information Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F1D30),
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
                            Text("👤 Pasajero: ${ride.passengerName}", color = Color.White, fontSize = 12.sp)
                        } else {
                            Text("🚗 Chofer: ${ride.assignedDriverName ?: "Chofer asignado"}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (!ride.assignedDriverVehicle.isNullOrBlank()) {
                                Text("🚘 Vehículo: ${ride.assignedDriverVehicle}", color = MeetColors.textSecondary, fontSize = 11.sp)
                            }
                        }
                        Text("📍 Ruta: ${ride.pickupAddress.take(30)}... → ${ride.destAddress.take(30)}...", color = MeetColors.textMuted, fontSize = 10.sp)
                    }
                }

                if (!isDriver) {
                    // Item Description Field for Passenger
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "¿Qué artículo u objeto dejaste en el vehículo?",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedTextField(
                            value = itemDescription,
                            onValueChange = { itemDescription = it.take(250) },
                            placeholder = {
                                Text(
                                    "Ej. Teléfono celular azul, billetera café, llaves con cinta negra, suéter...",
                                    color = MeetColors.textMuted,
                                    fontSize = 11.sp,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.cyberCyan,
                                unfocusedBorderColor = MeetColors.borderSubtle,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    // Contact actions
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val item = itemDescription.trim()
                                if (item.isBlank()) {
                                    Toast.makeText(context, "Por favor escribe qué artículo olvidaste", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val message = "[OBJETO OLVIDADO]: Hola, dejé en el vehículo el siguiente artículo: '$item'. Entiendo y acepto la tarifa oficial de compensación al chofer por entrega (₡3,500 <=10km / ₡7,000 >10km)."
                                onSendMessage?.invoke(message)
                                onOpenChat?.invoke()
                                Toast.makeText(context, "Mensaje enviado al chofer", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.neonGreen,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("CONTACTAR AL CHOFER POR CHAT", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }

                        if (!ride.assignedDriverPhone.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ride.assignedDriverPhone}"))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("LLAMAR AL CHOFER", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    // Driver instructions
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF142436),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "🛡️ Respaldo al Chofer",
                                color = MeetColors.neonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Si el pasajero te contacta por chat para recuperar un artículo, coordina un punto de encuentro seguro. La tarifa establecida (₡3,500 o ₡7,000) debe ser cancelada directamente al chofer.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }

                    if (onOpenChat != null) {
                        Button(
                            onClick = {
                                onOpenChat()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.cyberCyan,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ABRIR CHAT DEL VIAJE", fontWeight = FontWeight.Black, fontSize = 11.sp)
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
