package com.elysium.nexus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.R
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.engine.TransportBinding
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.core.profile.ProfileImportResult
import com.elysium.nexus.core.settings.AppSettings
import com.elysium.nexus.core.transport.ControllerTransport
import com.elysium.nexus.core.transport.LocalEchoTransport
import com.elysium.nexus.ui.editor.AlignmentAction
import com.elysium.nexus.ui.editor.ControlKind
import com.elysium.nexus.ui.editor.EditorActions
import com.elysium.nexus.ui.editor.EditorCanvas
import com.elysium.nexus.ui.editor.EditorToolbar
import com.elysium.nexus.ui.editor.ProfileImportDialog
import com.elysium.nexus.ui.editor.ProfileRenameDialog
import com.elysium.nexus.ui.editor.ProfileSelector
import com.elysium.nexus.ui.editor.TouchSurfaceViewHost
import com.elysium.nexus.ui.editor.TransportSelector
import com.elysium.nexus.ui.settings.SettingsDialog
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonEmptyState
import com.elysium.nexus.ui.theme.NeonFab
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * The first Compose UI screen of the project —
 * **Phase ULT.2 visual overhaul**.
 *
 * The screen has evolved across phases:
 *
 *  - Phase 1.0: a `Text`-only projection of the
 *    engine state + a `Button` that calls
 *    [CanonicalInputEngine.neutralize].
 *  - Phase 1.1: the `EditorCanvas` is layered
 *    on top of the engine projection.
 *  - Phase 1.2: the [EditorToolbar] is the top
 *    strip; the user can "Add button" / "Add
 *    stick" / "Add trigger" and "Save" /
 *    "Reset".
 *  - Phase 1.3: the [TouchSurfaceViewHost] is
 *    *inside* the Compose tree (Bug #18 fix).
 *  - Phase 1.5+1.18+1.24: profile management
 *    (new/delete/duplicate/rename), settings,
 *    import, share, transport selector.
 *  - **Phase ULT.2**: the visual overhaul. The
 *    screen now opens with a [NeonHeroCard]
 *    (profile name + status pills), the
 *    [ProfileSelector] and [TransportSelector]
 *    are wrapped in [NeonCard]s with section
 *    headers, the [EditorToolbar] uses
 *    [NeonChip]s, and a pulsing [NeonFab]
 *    sits in the bottom-right for the
 *    "Add control" action. An empty state
 *    shows when the profile has 0 controls.
 *
 * The status pills in the hero card are:
 *  - The current transport + state (green
 *    when connected, magenta when error,
 *    cyan otherwise).
 *  - The control count.
 *  - The current sequence number (mono
 *    font).
 */
@Composable
fun MainScreen(
    engine: CanonicalInputEngine,
    profile: Profile,
    allProfiles: List<Profile>,
    transports: List<ControllerTransport>,
    currentTransport: ControllerTransport,
    onTransportSelected: (ControllerTransport) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onProfileUpdated: (Profile) -> Unit,
    onNewProfile: () -> Unit = { },
    onDeleteProfile: () -> Unit = { },
    onDuplicateProfile: () -> Unit = { },
    onRenameProfile: (String) -> Unit = { },
    onShareProfile: () -> Unit = { },
    onImportProfile: (String) -> ProfileImportResult = { ProfileImportResult.Failure("Not wired") },
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit = { },
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by engine.state.collectAsState()
    MainScreenContent(
        engine = engine,
        state = state,
        profile = profile,
        allProfiles = allProfiles,
        transports = transports,
        currentTransport = currentTransport,
        onTransportSelected = onTransportSelected,
        onProfileSelected = onProfileSelected,
        onControlMoved = { controlId, newVisualBounds ->
            onProfileUpdated(
                EditorActions.moveControl(
                    profile = profile,
                    controlId = controlId,
                    newVisualBounds = newVisualBounds,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlScaled = { controlId, newW, newH ->
            onProfileUpdated(
                EditorActions.resizeControl(
                    profile = profile,
                    controlId = controlId,
                    newWidth = newW,
                    newHeight = newH,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlRotated = { controlId, newRotation ->
            onProfileUpdated(
                EditorActions.rotateControl(
                    profile = profile,
                    controlId = controlId,
                    newRotation = newRotation,
                    now = System.currentTimeMillis()
                )
            )
        },
        onOpacityChange = { controlId, newOpacity ->
            onProfileUpdated(
                EditorActions.setOpacity(
                    profile = profile,
                    controlId = controlId,
                    newOpacity = newOpacity,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlAdded = { kind ->
            onProfileUpdated(
                EditorActions.addControl(
                    profile = profile,
                    kind = kind,
                    now = System.currentTimeMillis()
                )
            )
        },
        onControlDeleted = { controlId ->
            onProfileUpdated(
                EditorActions.removeControl(
                    profile = profile,
                    controlId = controlId,
                    now = System.currentTimeMillis()
                )
            )
        },
        onProfileUpdated = onProfileUpdated,
        onReset = { onProfileUpdated(profile) },
        onNewProfile = onNewProfile,
        onDeleteProfile = onDeleteProfile,
        onDuplicateProfile = onDuplicateProfile,
        onRenameProfile = onRenameProfile,
        onShareProfile = onShareProfile,
        onImportProfile = onImportProfile,
        settings = settings,
        onSettingsChange = onSettingsChange,
        onNeutralize = onNeutralize,
        modifier = modifier
    )
}

/**
 * The stateless projection. Splitting this out
 * makes the composable previewable from Android
 * Studio without instantiating an engine.
 */
@Composable
private fun MainScreenContent(
    engine: CanonicalInputEngine,
    state: UniversalControllerState,
    profile: Profile,
    allProfiles: List<Profile>,
    transports: List<ControllerTransport>,
    currentTransport: ControllerTransport,
    onTransportSelected: (ControllerTransport) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onControlMoved: (controlId: Int, newVisualBounds: NormalizedRect) -> Unit,
    onControlScaled: (controlId: Int, newWidth: Float, newHeight: Float) -> Unit,
    onControlRotated: (controlId: Int, newRotation: Float) -> Unit,
    onOpacityChange: (controlId: Int, newOpacity: Float) -> Unit,
    onControlAdded: (ControlKind) -> Unit,
    onControlDeleted: (controlId: Int) -> Unit,
    onProfileUpdated: (Profile) -> Unit = { },
    onReset: () -> Unit,
    onNewProfile: () -> Unit = { },
    onDeleteProfile: () -> Unit = { },
    onDuplicateProfile: () -> Unit = { },
    onRenameProfile: (String) -> Unit = { },
    onShareProfile: () -> Unit = { },
    onImportProfile: (String) -> ProfileImportResult = { ProfileImportResult.Failure("Not wired") },
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit = { },
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val selectedOpacity: Float? = selectedId?.let { id ->
        profile.controls.firstOrNull { it.id == id }?.opacity
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElysiumColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === HERO CARD (compact) ==============================
            // The big "you are here" card. Profile
            // name + status pills. Compact so the
            // editor canvas gets most of the screen.
            NeonHeroCard(
                title = profile.name,
                subtitle = "${profile.controls.size} CTRLS · P${profile.id} · SEQ ${state.sequence}",
                accent = ElysiumColors.NeonPurple,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                statusChips = {
                    NeonStatusPill(
                        label = currentTransport.capabilities.label.uppercase(),
                        color = ElysiumColors.NeonCyan
                    )
                    NeonStatusPill(
                        label = "TOUCH ${state.touches.size()}",
                        color = ElysiumColors.NeonGreen
                    )
                }
            )

            // === PROFILES + TRANSPORT (compact, single row) =======
            // Both selectors share a single
            // compact strip. The "PROFILES" label
            // sits left of the chip row; the
            // "TRANSPORT" label is inline. No
            // wrapping NeonCard — just the section
            // header + chip row directly.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                NeonSectionHeader(
                    text = "Profiles · Transport",
                    accent = ElysiumColors.NeonCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                ProfileSelector(
                    profiles = allProfiles,
                    currentProfileId = profile.id,
                    onProfileSelected = {
                        selectedId = null
                        onProfileSelected(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TransportSelector(
                    transports = transports,
                    currentTransport = currentTransport,
                    onTransportSelected = onTransportSelected,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // === EDITOR TOOLBAR ====================================
            EditorToolbar(
                onAdd = onControlAdded,
                onSave = { },
                onReset = {
                    selectedId = null
                    onReset()
                },
                onNewProfile = onNewProfile,
                onDeleteProfile = onDeleteProfile,
                onDuplicateProfile = onDuplicateProfile,
                onRenameProfile = { showRename = true },
                onShare = onShareProfile,
                onImport = { showImport = true },
                onSettings = { showSettings = true },
                onAlign = { action ->
                    val now = System.currentTimeMillis()
                    val sid = selectedId
                    val updated = when (action) {
                        AlignmentAction.AlignLeft ->
                            if (sid != null) EditorActions.alignLeft(profile, sid, now)
                            else profile
                        AlignmentAction.AlignRight ->
                            if (sid != null) EditorActions.alignRight(profile, sid, now)
                            else profile
                        AlignmentAction.AlignTop ->
                            if (sid != null) EditorActions.alignTop(profile, sid, now)
                            else profile
                        AlignmentAction.AlignBottom ->
                            if (sid != null) EditorActions.alignBottom(profile, sid, now)
                            else profile
                        AlignmentAction.DistributeHorizontally ->
                            EditorActions.distributeHorizontally(profile, now)
                        AlignmentAction.DistributeVertically ->
                            EditorActions.distributeVertically(profile, now)
                    }
                    onProfileUpdated(updated)
                },
                onOpacityChange = { newOpacity ->
                    selectedId?.let { id -> onOpacityChange(id, newOpacity) }
                },
                selectedOpacity = selectedOpacity
            )

            // === EDITOR CANVAS + TOUCH SURFACE =====================
            // The big play area. The touch
            // surface is behind the canvas; the
            // canvas renders the user's profile
            // controls. When the profile has 0
            // controls, the empty state shows
            // through the canvas.
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                TouchSurfaceViewHost(
                    onTouchPointChange = { id, point, t0Ns ->
                        engine.submitTouchPoint(id, point, t0Ns)
                    }
                )
                if (profile.controls.isEmpty()) {
                    // Empty state when the
                    // profile has 0 controls.
                    NeonEmptyState(
                        title = "No controls yet",
                        body = "Tap + to add your first button, stick, or trigger.",
                        cta = "Add control",
                        onCta = { onControlAdded(ControlKind.Button) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    )
                } else {
                    android.util.Log.e("ElysiumMainScreen", "EditorCanvas branch: controls=${profile.controls.size}")
                    EditorCanvas(
                        profile = profile,
                        onMoved = onControlMoved,
                        onScaled = onControlScaled,
                        onRotated = onControlRotated,
                        onTapped = { id -> selectedId = id },
                        onLongPressed = { id ->
                            selectedId = null
                            onControlDeleted(id)
                        },
                        selectedId = selectedId
                    )
                }
            }
        }

        // === FAB (Add control) ====================================
        // A pulsing neon FAB in the bottom-right.
        // The FAB is the primary "add a control"
        // action. Tapping it adds a button; the
        // user can also use the toolbar's
        // "Add stick" / "Add trigger" chips.
        NeonFab(
            icon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.editor_add_button),
                    modifier = Modifier.size(32.dp),
                    tint = ElysiumColors.Background
                )
            },
            onClick = { onControlAdded(ControlKind.Button) },
            accent = ElysiumColors.NeonCyan,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )

        // === §38 NEUTRALIZE PILL ==================================
        // A small magenta pill in the bottom-left.
        // The §38 disconnect test relies on this
        // being a visible, tappable target. The
        // pill is the "force everything back to
        // neutral" escape hatch.
        NeonChip(
            label = "§38",
            onClick = onNeutralize,
            accent = ElysiumColors.NeonMagenta,
            destructive = true,
            icon = { Icon(Icons.Filled.PauseCircleFilled, contentDescription = null) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        )

        // === DIALOGS ===============================================
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onSettingsChange = onSettingsChange,
                onDismiss = { showSettings = false }
            )
        }
        if (showImport) {
            ProfileImportDialog(
                onImport = onImportProfile,
                onDismiss = { showImport = false }
            )
        }
        if (showRename) {
            ProfileRenameDialog(
                currentName = profile.name,
                onRename = onRenameProfile,
                onDismiss = { showRename = false }
            )
        }
    }
}

/**
 * Android Studio preview. A no-op that shows
 * the layout with a synthetic neutral state
 * and the default profile.
 */
@Preview
@Composable
private fun MainScreenPreview() {
    val state = UniversalControllerState.neutral()
    val profile = Profile.defaultProfile(now = 0L)
    val previewScope = rememberCoroutineScope()
    MainScreenContent(
        engine = CanonicalInputEngine(
            leftStickConfig = com.elysium.nexus.core.filter.StickConfig(),
            rightStickConfig = com.elysium.nexus.core.filter.StickConfig(),
            scope = previewScope
        ),
        state = state,
        profile = profile,
        allProfiles = listOf(profile),
        transports = listOf(LocalEchoTransport()),
        currentTransport = LocalEchoTransport(),
        onProfileSelected = { },
        onControlMoved = { _, _ -> },
        onControlScaled = { _, _, _ -> },
        onControlRotated = { _, _ -> },
        onOpacityChange = { _, _ -> },
        onControlAdded = { },
        onControlDeleted = { },
        onReset = { },
        onNewProfile = { },
        onDeleteProfile = { },
        onDuplicateProfile = { },
        onRenameProfile = { _ -> },
        onShareProfile = { },
        onImportProfile = { ProfileImportResult.Failure("Preview") },
        settings = AppSettings(),
        onSettingsChange = { },
        onTransportSelected = { },
        onNeutralize = { }
    )
}
