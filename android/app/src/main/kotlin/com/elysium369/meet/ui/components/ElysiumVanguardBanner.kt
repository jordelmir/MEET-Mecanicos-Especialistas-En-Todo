package com.elysium369.meet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.elysium369.meet.R

/** Full original automotive brand artwork; never represents a registered vehicle. */
@Composable
fun ElysiumVanguardBanner(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.elysium_vanguard_banner),
        contentDescription = "Elysium Vanguard AI OS · OBD2 · AI · Mobility",
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}
