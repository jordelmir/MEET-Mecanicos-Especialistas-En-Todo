package com.elysium.nexus.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonChip

/**
 * The top-of-screen profile selector — **Phase ULT.2**.
 *
 * Renders a horizontally-scrolling row of [NeonChip]s
 * (one per profile). The current profile is the
 * "active" chip — full-strength cyan accent, gradient
 * surface, breathing glow. Tapping a non-current chip
 * calls [onProfileSelected].
 *
 * The chip row is wrapped in a thin-bordered lane
 * (the [ElysiumColors.Outline] hairline) so it reads
 * as a §15 "library" surface, not as a bare row of
 * floating chips.
 */
@Composable
fun ProfileSelector(
    profiles: List<Profile>,
    currentProfileId: Int,
    onProfileSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        profiles.forEach { profile ->
            val isSelected = profile.id == currentProfileId
            NeonChip(
                label = profile.name,
                onClick = { if (!isSelected) onProfileSelected(profile.id) },
                accent = ElysiumColors.NeonCyan,
                active = isSelected,
                icon = {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Person,
                        contentDescription = null
                    )
                }
            )
        }
    }
}
