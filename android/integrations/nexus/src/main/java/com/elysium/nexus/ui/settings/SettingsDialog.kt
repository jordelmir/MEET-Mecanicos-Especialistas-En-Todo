package com.elysium.nexus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium.nexus.R
import com.elysium.nexus.core.settings.AppSettings

/**
 * The §15 settings dialog.
 *
 * The dialog is a modal [AlertDialog] hosted over
 * the editor. The dialog has three sections:
 *
 *  1. **Sticks** — two sliders for left / right
 *     stick sensitivity. The slider's range is
 *     `[AppSettings.MIN_SENSITIVITY, AppSettings.MAX_SENSITIVITY]`
 *     and the step is the slider's default
 *     (continuous; the value is a `Float`).
 *  2. **Axis inversion** — four switches (left X,
 *     left Y, right X, right Y). The switch's
 *     default state is "off" (canonical axes).
 *  3. **Haptics** — one switch. The default is
 *     "on".
 *
 * The dialog is *live*: every change calls
 * [onSettingsChange] with a new [AppSettings]
 * document. The caller persists the document
 * (the [com.elysium.nexus.core.settings.AppSettingsStore]
 * handles persistence).
 *
 * The dialog also has a "Reset" button that
 * returns to [AppSettings]'s defaults. The
 * "Close" button dismisses the dialog.
 *
 * ## Why a modal and not a separate screen
 *
 * A modal dialog preserves the editor's state
 * (the profile, the selection, the touch
 * surface) and is one tap to close. A separate
 * screen would require the activity to host
 * two screens and to handle back-stack. The
 * settings are a small bag; the modal is the
 * right shape.
 *
 * ## Why every change is a "save"
 *
 * The store is the source of truth. A "save"
 * button would force the user to remember which
 * changes they have committed; a "live" model
 * is the standard pattern for settings dialogs
 * and matches every OS settings UI the user has
 * seen.
 */
@Composable
fun SettingsDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    // The dialog is "live": every change updates
    // the parent's settings, and the parent's
    // settings are reflected on the next
    // recomposition. We do not need local
    // mutable state for the dialog itself; the
    // parent owns the document.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sticks section
                SectionHeader(stringResource(R.string.settings_section_sticks))
                LabeledSlider(
                    label = stringResource(R.string.settings_left_stick_sensitivity),
                    value = settings.leftStickSensitivity,
                    onValueChange = { v ->
                        onSettingsChange(
                            settings.copy(leftStickSensitivity = v)
                        )
                    }
                )
                LabeledSlider(
                    label = stringResource(R.string.settings_right_stick_sensitivity),
                    value = settings.rightStickSensitivity,
                    onValueChange = { v ->
                        onSettingsChange(
                            settings.copy(rightStickSensitivity = v)
                        )
                    }
                )
                // Axis inversion section
                SectionHeader(stringResource(R.string.settings_section_inversion))
                LabeledSwitch(
                    label = stringResource(R.string.settings_invert_left_x),
                    checked = settings.invertLeftX,
                    onCheckedChange = { v ->
                        onSettingsChange(settings.copy(invertLeftX = v))
                    }
                )
                LabeledSwitch(
                    label = stringResource(R.string.settings_invert_left_y),
                    checked = settings.invertLeftY,
                    onCheckedChange = { v ->
                        onSettingsChange(settings.copy(invertLeftY = v))
                    }
                )
                LabeledSwitch(
                    label = stringResource(R.string.settings_invert_right_x),
                    checked = settings.invertRightX,
                    onCheckedChange = { v ->
                        onSettingsChange(settings.copy(invertRightX = v))
                    }
                )
                LabeledSwitch(
                    label = stringResource(R.string.settings_invert_right_y),
                    checked = settings.invertRightY,
                    onCheckedChange = { v ->
                        onSettingsChange(settings.copy(invertRightY = v))
                    }
                )
                // Haptics section
                SectionHeader(stringResource(R.string.settings_section_haptics))
                LabeledSwitch(
                    label = stringResource(R.string.settings_haptics_enabled),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { v ->
                        onSettingsChange(settings.copy(hapticsEnabled = v))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onSettingsChange(AppSettings())
            }) {
                Text(stringResource(R.string.settings_reset))
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = AppSettings.MIN_SENSITIVITY..AppSettings.MAX_SENSITIVITY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
