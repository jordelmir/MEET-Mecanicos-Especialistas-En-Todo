package com.elysium369.meet.fulfillment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private enum class FinancialPeriod(val label: String, val days: Long?) { TODAY("Hoy", 1), WEEK("7 días", 7), MONTH("Mes", 31), YEAR("Año", 365), ALL("Todo", null) }
private enum class FinancialActivity(val label: String, val key: String?) { ALL("Todo", null), RIDES("Viajes", "VIAJES"), TOW("Grúas", "GRUAS"), REPAIR("Mecánica y taller", "TALLER_MECANICA"), PARTS("Repuestos", "REPUESTOS"), SERVICES("Servicios Elysium", "SERVICIOS_ELYSIUM") }

@Composable fun UnifiedActivityScreen(onBack: () -> Unit = {}, viewModel: UnifiedFinancialActivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState(); var period by remember { mutableStateOf(FinancialPeriod.MONTH) }; var activity by remember { mutableStateOf(FinancialActivity.ALL) }
    val filtered = remember(state.entries, period, activity) { state.entries.filter { (activity.key == null || it.activityType == activity.key) && it.isInPeriod(period) } }; val confirmed = filtered.filter { it.isConfirmed }
    val income = confirmed.filter { it.flow == "INGRESO" }.sumByCurrency(); val expense = confirmed.filter { it.flow in setOf("GASTO", "EGRESO") }.sumByCurrency(); val topups = confirmed.filter { it.flow == "RECARGA" }.sumByCurrency(); val walletBalance = state.entries.filter { it.source == "RIDE_WALLET" && it.isConfirmed }.groupBy { it.currency }.mapValues { (_, rows) -> rows.sumOf { if (it.flow == "GASTO") -it.amountMinor else it.amountMinor } }
    Scaffold(containerColor = MeetColors.backgroundDeep, topBar = { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver", tint = MeetColors.textPrimary) }; Column(Modifier.weight(1f)) { Text("Mi actividad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary); Text("Ingresos, gastos y recargas de esta cuenta", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary) }; IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Actualizar", tint = MeetColors.neonGreen) } } }) { padding -> when { state.loading && state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator(color = MeetColors.neonGreen) }; !state.signedIn -> EmptyFinancialState(padding, "Inicia sesión para ver la actividad de esta cuenta."); else -> FinancialContent(padding, period, activity, { period = it }, { activity = it }, income, expense, topups, walletBalance, filtered, state.error) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun FinancialContent(padding: PaddingValues, period: FinancialPeriod, activity: FinancialActivity, onPeriod: (FinancialPeriod) -> Unit, onActivity: (FinancialActivity) -> Unit, income: Map<String, Long>, expense: Map<String, Long>, topups: Map<String, Long>, walletBalance: Map<String, Long>, entries: List<PersonalFinancialActivityEntry>, error: String?) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { FinancialPeriod.entries.forEach { option -> FilterChip(selected = period == option, onClick = { onPeriod(option) }, label = { Text(option.label) }, colors = chips()) } } }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { FinancialActivity.entries.forEach { option -> AssistChip(onClick = { onActivity(option) }, label = { Text(option.label) }, colors = AssistChipDefaults.assistChipColors(containerColor = if (activity == option) MeetColors.neonGreen.copy(alpha = .18f) else MeetColors.cardBackground, labelColor = if (activity == option) MeetColors.neonGreen else MeetColors.textSecondary)) } } }
        item { Text("Resumen confirmado", style = MaterialTheme.typography.titleMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryCard("Ingresos", income.formatTotals(), MeetColors.neonGreen, Modifier.weight(1f)); SummaryCard("Gastos", expense.formatTotals(), Color(0xFFFF8A80), Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryCard("Recargas", topups.formatTotals(), MeetColors.electricBlue, Modifier.weight(1f)); SummaryCard("Saldo billetera", walletBalance.formatTotals(), MeetColors.neonGreen, Modifier.weight(1f)) } }
        if (error != null) item { Text("No se pudo actualizar ahora. Se muestran los últimos movimientos recibidos.", color = Color(0xFFFFC107), style = MaterialTheme.typography.bodySmall) }
        item { Text("Movimientos", style = MaterialTheme.typography.titleMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold); Text("Solo los confirmados suman al resumen. Los pendientes se muestran separados.", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary) }
        if (entries.isEmpty()) item { EmptyFinancialState(PaddingValues(vertical = 28.dp), "No hay movimientos financieros para este período.") }; items(entries, key = { it.entryId }) { FinancialEntryCard(it) }
    }
}

@Composable private fun SummaryCard(label: String, amount: String, color: Color, modifier: Modifier = Modifier) = Card(modifier, colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground), border = BorderStroke(1.dp, MeetColors.borderSubtle)) { Column(Modifier.padding(14.dp)) { Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(5.dp)); Text(amount, color = color, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
@Composable private fun FinancialEntryCard(entry: PersonalFinancialActivityEntry) { val color = when (entry.flow) { "INGRESO" -> MeetColors.neonGreen; "RECARGA" -> MeetColors.electricBlue; else -> Color(0xFFFF8A80) }; Card(colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground), border = BorderStroke(1.dp, MeetColors.borderSubtle), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(activityIcon(entry.activityType), null, tint = color, modifier = Modifier.size(27.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(entry.title.humanize(), color = MeetColors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${entry.activityType.humanize()} · ${entry.occurredAt.asDate()}", color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall); if (!entry.isConfirmed) Text("Pendiente de confirmación", color = Color(0xFFFFC107), style = MaterialTheme.typography.labelSmall) }; Column(horizontalAlignment = Alignment.End) { Text((if (entry.flow == "INGRESO" || entry.flow == "RECARGA") "+" else "−") + entry.amountMinor.money(entry.currency), color = color, fontWeight = FontWeight.Bold); Text(entry.status.humanize(), color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall) } } } }
@Composable private fun EmptyFinancialState(padding: PaddingValues, message: String) = Box(Modifier.fillMaxWidth().padding(padding), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AccountBalanceWallet, null, tint = MeetColors.textSecondary, modifier = Modifier.size(52.dp)); Spacer(Modifier.height(10.dp)); Text(message, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodyMedium) } }
@Composable private fun chips() = FilterChipDefaults.filterChipColors(selectedContainerColor = MeetColors.neonGreen.copy(alpha = .22f), selectedLabelColor = MeetColors.neonGreen)
private fun activityIcon(type: String): ImageVector = when (type) { "VIAJES" -> Icons.Default.DirectionsCar; "GRUAS" -> Icons.Default.LocalShipping; "REPUESTOS" -> Icons.Default.Storefront; else -> Icons.Default.Build }
private fun PersonalFinancialActivityEntry.isInPeriod(period: FinancialPeriod): Boolean = period.days?.let { runCatching { Instant.parse(occurredAt).isAfter(Instant.now().minusSeconds(it * 86_400)) }.getOrDefault(false) } ?: true
private fun List<PersonalFinancialActivityEntry>.sumByCurrency(): Map<String, Long> = groupBy { it.currency }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
private fun Map<String, Long>.formatTotals(): String = if (isEmpty()) "Sin datos" else entries.joinToString("\n") { (currency, amount) -> amount.money(currency) }
private fun Long.money(currencyCode: String): String = runCatching { NumberFormat.getCurrencyInstance(Locale("es", "CR")).apply { currency = Currency.getInstance(currencyCode); maximumFractionDigits = 0 }.format(this) }.getOrElse { "$currencyCode $this" }
private fun String.humanize(): String = lowercase(Locale.getDefault()).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }
private fun String.asDate(): String = runCatching { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es", "CR")).withZone(ZoneId.systemDefault()).format(Instant.parse(this)) }.getOrDefault(this)
