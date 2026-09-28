package com.elysium.nexus.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * §3/§8 "Mis Controles" screen.
 *
 * §3: Profile selection navigates by profileId ONLY.
 * No DeviceTemplate or InstalledIrProfile object transport.
 */
@Composable
fun InstalledProfilesScreen(
    onBack: () -> Unit,
    onProfileSelected: (profileId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { InstalledIrProfileRepository(context) }
    var profiles by remember { mutableStateOf(repo.getAllProfiles()) }
    var showHelp by remember { mutableStateOf(false) }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(label = "Atrás", onClick = onBack, accent = ElysiumColors.NeonPurple, icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) })
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(ElysiumColors.NeonPurple.copy(alpha = 0.6f)).clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "Ayuda", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            NeonHeroCard(
                title = "Mis Controles",
                subtitle = "${profiles.size} controles configurados",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = { NeonStatusPill(label = "Persistente", color = ElysiumColors.NeonGreen) }
            )

            if (profiles.isEmpty()) {
                NeonCard(modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 16.dp), accent = ElysiumColors.NeonOrange, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No tienes controles guardados", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = ElysiumColors.NeonOrange)
                        Text("Ve a Controles de TV, selecciona tu marca y completa el asistente.", style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = ElysiumColors.OnSurface)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    profiles.forEach { profile ->
                        ProfileCard(
                            profile = profile,
                            onClick = { onProfileSelected(profile.id) },
                            onDelete = {
                                repo.deleteProfile(profile.id)
                                profiles = repo.getAllProfiles()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Mis Controles",
            whatIsThis = "Controles remotos IR configurados y guardados.",
            howToUse = listOf("Toca un control para abrirlo.", "Los controles sobreviven al cierre de la app.", "Usa el bote de basura para eliminar."),
            tip = "Los signalIds se verifican con SHA-256 antes de transmitir.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: com.elysium.nexus.core.device.InstalledIrProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(modifier = modifier.clickable(onClick = onClick), accent = ElysiumColors.NeonCyan, cornerRadius = 14.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(ElysiumColors.NeonCyan.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, tint = ElysiumColors.NeonCyan, modifier = Modifier.size(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(profile.displayName, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = ElysiumColors.OnSurface)
                    Text("${profile.brand} · ${profile.commands.size} botones · ${profile.verificationStatus}", style = TextStyle(fontSize = 12.sp), color = ElysiumColors.OnSurfaceMuted)
                    Text("codeSetId: ${profile.codeSetId.take(12)}...", style = TextStyle(fontSize = 10.sp), color = ElysiumColors.NeonCyan)
                }
            }
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Red.copy(alpha = 0.2f)).clickable { onDelete() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = Color.Red, modifier = Modifier.size(18.dp))
            }
        }
    }
}
