package com.elysium369.meet.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale

@Composable
internal fun rememberRideJavaLocale(): java.util.Locale {
    val languageTag = Locale.current.toLanguageTag()
    return remember(languageTag) {
        java.util.Locale.forLanguageTag(languageTag)
    }
}
