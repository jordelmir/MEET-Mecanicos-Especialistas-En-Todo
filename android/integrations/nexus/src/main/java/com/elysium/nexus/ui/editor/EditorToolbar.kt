package com.elysium.nexus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.R
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonChip

/**
 * The kind of alignment / distribution action
 * the toolbar's row 2 chips dispatch.
 */
enum class AlignmentAction {
    AlignLeft,
    AlignRight,
    AlignTop,
    AlignBottom,
    DistributeHorizontally,
    DistributeVertically
}

/**
 * The kind of control the toolbar's "Add" chip
 * creates.
 */
enum class ControlKind { Button, Stick, Trigger }

/**
 * The top toolbar of the editor — **Phase ULT.2**.
 *
 * Three rows of [NeonChip]s (the 3D pill
 * primitive). The toolbar's color story:
 *
 *  - **Row 1** is the action row. The three
 *    "Add" chips are cyan-tinted. The "Save"
 *    chip is purple-tinted (the "this changes
 *    the persistent state" color). The "Reset"
 *    chip is orange-tinted. The profile
 *    management chips (New, Delete, Duplicate,
 *    Rename) are cyan. The "Share" / "Import"
 *    chips are green-tinted. The "Settings"
 *    chip is purple-tinted.
 *  - **Row 2** is the alignment row. All chips
 *    are cyan.
 *  - **Row 3** is the opacity slider. The
 *    slider's track is cyan.
 *
 * Each row is horizontally scrollable; the
 * toolbar is wider than a 360dp screen on a
 * phone.
 */
@Composable
fun EditorToolbar(
    onAdd: (ControlKind) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onNewProfile: () -> Unit = { },
    onDeleteProfile: () -> Unit = { },
    onDuplicateProfile: () -> Unit = { },
    onRenameProfile: () -> Unit = { },
    onShare: () -> Unit = { },
    onImport: () -> Unit = { },
    onSettings: () -> Unit = { },
    onAlign: (AlignmentAction) -> Unit = { },
    onOpacityChange: (Float) -> Unit = { },
    selectedOpacity: Float? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scrollState2 = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth().background(ElysiumColors.Background)) {
        // Row 1: the primary action chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            NeonChip(
                label = stringResource(R.string.editor_add_button),
                onClick = { onAdd(ControlKind.Button) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.RadioButtonChecked, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_add_stick),
                onClick = { onAdd(ControlKind.Stick) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.SportsEsports, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_add_trigger),
                onClick = { onAdd(ControlKind.Trigger) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.VideogameAsset, contentDescription = null) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            NeonChip(
                label = stringResource(R.string.editor_save),
                onClick = onSave,
                accent = ElysiumColors.NeonPurple,
                active = true,
                icon = { Icon(Icons.Filled.Save, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_reset),
                onClick = onReset,
                accent = ElysiumColors.NeonOrange,
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            NeonChip(
                label = stringResource(R.string.editor_new_profile),
                onClick = onNewProfile,
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_duplicate_profile),
                onClick = onDuplicateProfile,
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_rename_profile),
                onClick = onRenameProfile,
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_delete_profile),
                onClick = onDeleteProfile,
                destructive = true,
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            NeonChip(
                label = stringResource(R.string.editor_share_profile),
                onClick = onShare,
                accent = ElysiumColors.NeonGreen,
                icon = { Icon(Icons.Filled.Share, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_import_profile),
                onClick = onImport,
                accent = ElysiumColors.NeonGreen,
                icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_settings),
                onClick = onSettings,
                accent = ElysiumColors.NeonPurple,
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) }
            )
        }
        // Row 2: alignment / distribution chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState2)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NeonChip(
                label = stringResource(R.string.editor_align_left),
                onClick = { onAlign(AlignmentAction.AlignLeft) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.AlignHorizontalLeft, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_align_right),
                onClick = { onAlign(AlignmentAction.AlignRight) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.AlignHorizontalRight, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_align_top),
                onClick = { onAlign(AlignmentAction.AlignTop) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.CenterFocusStrong, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_align_bottom),
                onClick = { onAlign(AlignmentAction.AlignBottom) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.SwapVert, contentDescription = null) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            NeonChip(
                label = stringResource(R.string.editor_distribute_horizontally),
                onClick = { onAlign(AlignmentAction.DistributeHorizontally) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) }
            )
            NeonChip(
                label = stringResource(R.string.editor_distribute_vertically),
                onClick = { onAlign(AlignmentAction.DistributeVertically) },
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.SwapVert, contentDescription = null) }
            )
        }
        // Row 3: opacity slider.
        if (selectedOpacity != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.editor_opacity).uppercase(),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = ElysiumColors.NeonCyan,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Slider(
                    value = selectedOpacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElysiumColors.NeonCyan,
                        activeTrackColor = ElysiumColors.NeonCyan,
                        inactiveTrackColor = ElysiumColors.SurfaceHigh
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
