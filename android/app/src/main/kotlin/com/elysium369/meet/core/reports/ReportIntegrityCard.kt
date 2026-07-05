package com.elysium369.meet.core.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.di.ReportsEntryPoint
import com.elysium369.meet.ui.theme.MeetColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose-friendly accessor for [ReportHashingService] when the host
 * screen does not already receive it as a parameter.
 *
 * Uses `EntryPointAccessors` against the Hilt `SingletonComponent`. The
 * service is `@Singleton` so re-creating the lookup on every recompose
 * is cheap, but we still wrap in `remember(applicationContext)` so the
 * card body holds a stable reference.
 */
@Composable
fun rememberReportHashingService(): ReportHashingService {
    val ctx = LocalContext.current
    return remember(ctx.applicationContext) {
        EntryPointAccessors
            .fromApplication(ctx.applicationContext, ReportsEntryPoint::class.java)
            .reportHashingService()
    }
}

/**
 * Report Integrity Card — Compose surface that, given a
 * [ReportHashingService], demonstrates the cross-runtime parity
 * (TS ≡ Kotlin) end-to-end on the device.
 *
 * The card is intentionally a leaf composable so any screen
 * (ReportScreen, a future DiagnosticScreen, a Settings debug section)
 * can drop it in via the Hilt graph.
 *
 * Behavior:
 *   - On first composition it spins on a worker dispatcher to keep the
 *     UI thread snappy even though SHA-256 + canonical string build is
 *     microseconds. The placeholder state prevents a one-frame flicker.
 *   - It re-uses the Service singleton; repeated recomposition does NOT
 *     re-hash. State is local to the card.
 */
@Composable
fun ReportIntegrityCard(
    service: ReportHashingService,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ReportHashingService.ParityResult?>(null) }
    var chainOk by remember { mutableStateOf<HashEngine.ChainResult?>(null) }
    var chainBroken by remember { mutableStateOf<HashEngine.ChainResult?>(null) }

    androidx.compose.runtime.LaunchedEffect(service) {
        scope.launch {
            val r = withContext(Dispatchers.Default) { service.p0230ParityDemo() }
            val okChain = withContext(Dispatchers.Default) { service.demoReportChainOk() }
            val brokenChain = withContext(Dispatchers.Default) { service.demoReportChainBroken() }
            result = r
            chainOk = okChain
            chainBroken = brokenChain
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MeetColors.cardBackground)
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Cross-runtime parity",
                    tint = MeetColors.electricBlue,
                    modifier = Modifier.height(20.dp)
                )
                Text(
                    text = "INTEGRIDAD DE REPORTES — TS ≡ Kotlin",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = "Esta pantalla reproduce en el APK el hash SHA-256 del snapshot P0230 calculado por la web (TypeScript). Si el badge muestra MATCH, las dos runtimes son byte-exact.",
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )

            val cur = result
            if (cur == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    CircularProgressIndicator(
                        color = MeetColors.electricBlue,
                        modifier = Modifier.height(18.dp),
                    )
                    Text(
                        text = "Calculando hash en Dispatchers.Default…",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (cur.match) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = if (cur.match) "match" else "mismatch",
                        tint = if (cur.match) MeetColors.neonGreen else Color(0xFFFF3333),
                        modifier = Modifier.height(22.dp),
                    )
                    Text(
                        text = if (cur.match) "MATCH · byte-exact con TypeScript" else "MISMATCH · drift detectado",
                        color = if (cur.match) MeetColors.neonGreen else Color(0xFFFF3333),
                        fontWeight = FontWeight.Bold,
                    )
                }
                HashRow(label = "Esperado (TS)", value = cur.expectedHash)
                HashRow(label = "Calculado (APK)", value = cur.computedHash)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Canonical (ordenado por Kotlin toSortedMap):",
                    color = MeetColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF04070E))
                        .padding(8.dp)
                        .heightIn(max = 96.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = cur.canonical,
                        color = MeetColors.neonGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
            }

            val ok = chainOk
            val br = chainBroken
            if (ok != null && br != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cadena de reportes (verificación).",
                    color = MeetColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                ChainRow(label = "Cadena íntegra", result = ok)
                ChainRow(label = "Cadena con tamper", result = br)
            }
        }
    }
}

@Composable
private fun HashRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MeetColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF04070E))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = value,
                color = MeetColors.electricBlue,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ChainRow(label: String, result: HashEngine.ChainResult) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (result.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (result.ok) "ok" else "broken",
            tint = if (result.ok) MeetColors.neonGreen else Color(0xFFFF3333),
            modifier = Modifier.height(16.dp),
        )
        Text(
            text = "$label → ${if (result.ok) "OK" else "BROKEN at ${result.brokenAt}"}",
            color = if (result.ok) MeetColors.neonGreen else Color(0xFFFF3333),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
