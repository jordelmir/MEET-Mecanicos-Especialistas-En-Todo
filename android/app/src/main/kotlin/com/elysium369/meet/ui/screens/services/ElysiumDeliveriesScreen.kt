package com.elysium369.meet.ui.screens.services

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/** Entry into the existing server-authoritative service marketplace. */
@Composable
fun ElysiumDeliveriesScreen(
    onBack: () -> Unit,
    onFood: () -> Unit,
    onGroceries: () -> Unit,
    onSmallParcel: () -> Unit,
    onBecomeProvider: () -> Unit,
    onMyOrders: () -> Unit,
) {
    var role by remember { mutableStateOf(DeliveryRole.CUSTOMER) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF04152C), Color(0xFF07101D), Color(0xFF03070E)))
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Inicio", color = MeetColors.cyberCyan) }
            Text("ELYSIUM", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 3.sp)
            Text("COMIDA Y ENTREGAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text("Pide comida, compra en tiendas o envía un objeto pequeño. Cada solicitud se confirma en línea.", color = MeetColors.textSecondary)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeliveryRole.entries.forEach { option ->
                    FilterChip(
                        selected = role == option,
                        onClick = { role = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        if (role == DeliveryRole.CUSTOMER) {
            item { DeliveryAction("🍳", "Comida", "Solicita a sodas y restaurantes registrados", onFood) }
            item { DeliveryAction("🏪", "Tiendas", "Abarrotes y compras de comercios registrados", onGroceries) }
            item { DeliveryAction("📦", "Objeto pequeño", "Mensajería express; para moto, máximo 10 kg y dimensiones que quepan en una mochila", onSmallParcel) }
            item { OutlinedButton(onClick = onMyOrders, modifier = Modifier.fillMaxWidth()) { Text("Ver mis solicitudes en línea") } }
        } else {
            item {
                Text(
                    if (role == DeliveryRole.MERCHANT) "Registra tu oferta como comercio y atiende solicitudes de comida o compras."
                    else "Registra mensajería express y revisa solicitudes disponibles. La moto transporta objetos pequeños, nunca más de un pasajero en Viajes.",
                    color = Color.White,
                )
            }
            item { DeliveryAction("🪪", "Configurar mi oferta", "Perfil y servicios verificados en línea", onBecomeProvider) }
            item { DeliveryAction("📡", "Ver solicitudes", "Propuestas, seguimiento e historial confirmados por el servidor", onMyOrders) }
        }
        item {
            Text(
                "Viajes de personas y entregas son servicios distintos. Una moto de pasajeros admite solo a una persona. Una entrega en moto se limita a comida u objetos pequeños.",
                color = MeetColors.cyberCyan,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

private enum class DeliveryRole(val label: String) {
    CUSTOMER("Cliente"), MERCHANT("Comercio"), COURIER("Repartidor")
}

@Composable
private fun DeliveryAction(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF10243C),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(icon, fontSize = 27.sp)
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(subtitle, color = MeetColors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}
