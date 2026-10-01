package com.elysium369.meet.ui.elysium.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.elysium369.meet.ui.theme.MeetColors

/** Projects route/domain/global inheritance into legacy renderers and the root Material theme. */
@Composable
fun BindElysiumTheme(route: String) {
    val saved by ElysiumThemeRepository.config.collectAsState()
    val draft by ElysiumThemeRepository.preview.collectAsState()
    val experience = com.elysium369.meet.ui.home.LocalHomeExperience.current
    val effective = remember(saved, draft, route, experience) {
        ElysiumPaletteResolver.resolve(draft ?: saved, ElysiumPaletteResolver.domain(route), route,
            if (experience == com.elysium369.meet.ui.home.HomeExperience.CLASSIC)
                ElysiumPaletteOverride(0xFF00FFD4L,0xFFBB00FFL,0xFF00E5FFL,0xFFFF00AAL)
            else ElysiumPaletteResolver.defaults
        )
    }
    LaunchedEffect(effective) { MeetColors.applyPalette(effective) }
}
