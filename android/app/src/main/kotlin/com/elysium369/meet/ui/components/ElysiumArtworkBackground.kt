package com.elysium369.meet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.elysium369.meet.R

/** Full-bleed original artwork behind independently scrollable screen controls. */
@Composable
fun ElysiumArtworkBackground(automotive: Boolean = false) {
    val exp = com.elysium369.meet.ui.home.LocalHomeExperience.current
    if (exp == com.elysium369.meet.ui.home.HomeExperience.CLASSIC) {
        Box(Modifier.fillMaxSize().background(com.elysium369.meet.ui.theme.MeetColors.backgroundDeep))
        return
    }
    if (exp == com.elysium369.meet.ui.home.HomeExperience.MAIKEL_BLANCO) {
        Box(Modifier.fillMaxSize().background(Color.White))
        return
    }
    Image(
        painter = painterResource(if (automotive) R.drawable.elysium_vanguard_banner else R.drawable.ev_logo_original),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color(0xFF050B15).copy(alpha = 0.18f),
        Color(0xFF050B15).copy(alpha = 0.48f),
        Color(0xFF050B15).copy(alpha = 0.80f),
    ))))
}
