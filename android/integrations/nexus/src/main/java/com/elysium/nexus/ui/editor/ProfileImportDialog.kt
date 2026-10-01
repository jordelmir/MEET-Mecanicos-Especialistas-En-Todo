package com.elysium.nexus.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elysium.nexus.R
import com.elysium.nexus.core.profile.ProfileImportResult

/**
 * The §15 profile import dialog.
 *
 * The dialog is a modal [AlertDialog] with a
 * single multiline [OutlinedTextField] for the
 * JSON payload. The user pastes a profile's
 * JSON (e.g. from a share receipt, a file
 * exported via the §15 share intent, an
 * e-mail attachment) and taps "Import".
 *
 * The "Import" button is disabled while the
 * field is empty. The "Cancel" button dismisses
 * the dialog. The "Paste from clipboard"
 * shortcut uses the platform clipboard; the
 * field is updated with the clipboard's
 * contents (if non-empty).
 *
 * The result of the import is reported by
 * [onImport] as a [ProfileImportResult]. The
 * caller (the activity) persists a
 * [ProfileImportResult.Success]; the dialog
 * stays open on [ProfileImportResult.Failure]
 * and shows the reason inline. The user can
 * edit the JSON and retry.
 *
 * ## Why a modal and not a separate screen
 *
 * The import dialog is small (one text field,
 * two buttons). A modal is the right shape; a
 * separate screen would require the activity to
 * host two screens and to handle back-stack.
 */
@Composable
fun ProfileImportDialog(
    onImport: (String) -> ProfileImportResult,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var errorReason: String? by remember { mutableStateOf(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_import_profile)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.import_profile_paste_hint))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        // Clear the inline error
                        // when the user edits;
                        // the next import
                        // attempt will produce
                        // a fresh error.
                        errorReason = null
                    },
                    placeholder = { Text(stringResource(R.string.import_profile_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    isError = errorReason != null,
                    supportingText = errorReason?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = onImport(text)
                    if (result is ProfileImportResult.Failure) {
                        errorReason = result.reason
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.import_profile_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        }
    )
}
