package com.elysium369.meet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.elysium369.meet.R

/** Original EV artwork displayed in full, without tinting or cropping. */
@Composable
fun ElysiumBrandLogo(modifier: Modifier = Modifier, hero: Boolean = false) {
    Image(
        painter = painterResource(if (hero) R.drawable.ev_logo_original else R.drawable.ev_logo_mark),
        contentDescription = "Elysium Vanguard",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
