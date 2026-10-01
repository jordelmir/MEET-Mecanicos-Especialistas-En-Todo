package com.elysium.nexus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.posture.Posture
import com.elysium.nexus.core.profile.NormalizedRect
import com.elysium.nexus.core.profile.Profile
import com.elysium.nexus.core.profile.ProfileImportResult
import com.elysium.nexus.core.settings.AppSettings
import com.elysium.nexus.ui.editor.ControlKind
import com.elysium.nexus.ui.editor.EditorActions
import com.elysium.nexus.ui.editor.EditorCanvas
import com.elysium.nexus.ui.editor.EditorToolbar
import com.elysium.nexus.ui.editor.ProfileSelector
import com.elysium.nexus.ui.editor.TouchSurfaceViewHost

/**
 * The §16 foldable-aware main screen.
 *
 * `MASTER_ORDER.md` §16 says the project's
 * editor shall adapt to the foldable posture:
 *
 *  - **Open** (hinge fully open): single-pane
 *    layout; the editor and the diagnostic
 *    overlay share the screen.
 *  - **Half-opened** (hinge at a non-flat angle):
 *    tabletop layout; the top half holds the
 *    dashboard / profile selector and the
 *    bottom half holds the editor + the touch
 *    surface.
 *  - **Flat** (hinge fully open, lying flat):
 *    the editor uses the full surface (same as
 *    Open, but with no system bar occlusion).
 *  - **Closed** (hinge fully closed): the cover
 *    screen is the primary surface; the editor
 *    is in "compact" mode (one row of chips).
 *  - **Unknown** (non-foldable device): the
 *    same as Open.
 *
 * The function is a Compose composable that
 * takes the current [Posture] and renders the
 * appropriate layout. The [Posture] is
 * collected from the [PostureObserver] by the
 * activity; the function itself is stateless
 * (a true projection of the posture).
 *
 * ## Why a separate composable
 *
 * The original [MainScreen] is a single-pane
 * layout that does not adapt to the posture.
 * Adding posture awareness to the same
 * composable would have tangled the layout
 * logic with the input flow. The posture-
 * aware version lives in its own file; the
 * activity wires the [PostureObserver] to the
 * `posture` state and passes the value to
 * [PostureAwareMainScreen].
 */
@Composable
fun PostureAwareMainScreen(
    engine: CanonicalInputEngine,
    profile: Profile,
    allProfiles: List<Profile>,
    transports: List<com.elysium.nexus.core.transport.ControllerTransport>,
    currentTransport: com.elysium.nexus.core.transport.ControllerTransport,
    onTransportSelected: (com.elysium.nexus.core.transport.ControllerTransport) -> Unit,
    posture: Posture,
    onProfileSelected: (Int) -> Unit,
    onProfileUpdated: (Profile) -> Unit,
    onNewProfile: () -> Unit,
    onDeleteProfile: () -> Unit,
    onDuplicateProfile: () -> Unit,
    onRenameProfile: (String) -> Unit,
    onShareProfile: () -> Unit,
    onImportProfile: (String) -> ProfileImportResult,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (posture) {
        Posture.OPEN, Posture.UNKNOWN, Posture.FLAT ->
            MainScreen(
                engine = engine,
                profile = profile,
                allProfiles = allProfiles,
                transports = transports,
                currentTransport = currentTransport,
                onTransportSelected = onTransportSelected,
                onProfileSelected = onProfileSelected,
                onProfileUpdated = onProfileUpdated,
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
        Posture.HALF_OPENED -> TabletopMainScreen(
            engine = engine,
            profile = profile,
            allProfiles = allProfiles,
            onProfileSelected = onProfileSelected,
            onProfileUpdated = onProfileUpdated,
            onNewProfile = onNewProfile,
            onDeleteProfile = onDeleteProfile,
            onDuplicateProfile = onDuplicateProfile,
            onRenameProfile = onRenameProfile,
            onShareProfile = onShareProfile,
            onNeutralize = onNeutralize,
            modifier = modifier
        )
        Posture.CLOSED -> CompactMainScreen(
            engine = engine,
            profile = profile,
            allProfiles = allProfiles,
            onProfileSelected = onProfileSelected,
            onProfileUpdated = onProfileUpdated,
            modifier = modifier
        )
    }
}

/**
 * The tabletop layout (Phase 1.8).
 *
 * The top half holds the dashboard / profile
 * selector. The bottom half holds the editor +
 * the touch surface. The split is at the
 * hinge; without knowing the hinge position
 * (the device is foldable but the hinge
 * bounds are not in the [Posture] enum), the
 * split is a fixed 50/50.
 *
 * The full per-hinge layout (with the hinge
 * bounds from `FoldingFeature.bounds`) lands
 * in Phase 1.10+ with the per-posture layout
 * extensions.
 */
@Composable
private fun TabletopMainScreen(
    engine: CanonicalInputEngine,
    profile: Profile,
    allProfiles: List<Profile>,
    onProfileSelected: (Int) -> Unit,
    onProfileUpdated: (Profile) -> Unit,
    onNewProfile: () -> Unit,
    onDeleteProfile: () -> Unit,
    onDuplicateProfile: () -> Unit,
    onRenameProfile: (String) -> Unit,
    onShareProfile: () -> Unit,
    onNeutralize: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val selectedOpacity: Float? = selectedId?.let { id ->
        profile.controls.firstOrNull { it.id == id }?.opacity
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F12)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top half: the dashboard (profile
            // selector + diagnostic).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F0F12))
            ) {
                ProfileSelector(
                    profiles = allProfiles,
                    currentProfileId = profile.id,
                    onProfileSelected = {
                        selectedId = null
                        onProfileSelected(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Dashboard (tabletop top half)",
                        color = Color(0xFFF2F2F4),
                        fontSize = 12.sp
                    )
                }
            }
            // Bottom half: the editor + touch surface.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F0F12))
            ) {
                TouchSurfaceViewHost(
                    onTouchPointChange = { id, point, t0Ns ->
                        engine.submitTouchPoint(id, point, t0Ns)
                    }
                )
                EditorCanvas(
                    profile = profile,
                    onMoved = { id, bounds ->
                        onProfileUpdated(
                            EditorActions.moveControl(profile, id, bounds, System.currentTimeMillis())
                        )
                    },
                    onScaled = { id, w, h ->
                        onProfileUpdated(
                            EditorActions.resizeControl(profile, id, w, h, System.currentTimeMillis())
                        )
                    },
                    onRotated = { id, r ->
                        onProfileUpdated(
                            EditorActions.rotateControl(profile, id, r, System.currentTimeMillis())
                        )
                    },
                    onTapped = { id -> selectedId = id },
                    onLongPressed = { id ->
                        selectedId = null
                        onProfileUpdated(
                            EditorActions.removeControl(profile, id, System.currentTimeMillis())
                        )
                    },
                    selectedId = selectedId
                )
                EditorToolbar(
                    onAdd = { kind ->
                        onProfileUpdated(
                            EditorActions.addControl(profile, kind, System.currentTimeMillis())
                        )
                    },
                    onSave = { },
                    onReset = { selectedId = null; onProfileUpdated(profile) },
                    onNewProfile = onNewProfile,
                    onDeleteProfile = onDeleteProfile,
                    onDuplicateProfile = onDuplicateProfile,
                    onRenameProfile = { /* rename dialog is hosted by MainScreen, not Tabletop */ },
                    onShare = onShareProfile,
                    onImport = { /* import dialog is hosted by MainScreen, not Tabletop */ },
                    onSettings = { /* settings dialog is hosted by MainScreen, not Tabletop */ },
                    onOpacityChange = { newOpacity ->
                        selectedId?.let { id -> onProfileUpdated(
                            EditorActions.setOpacity(profile, id, newOpacity, System.currentTimeMillis())
                        ) }
                    },
                    selectedOpacity = selectedOpacity
                )
            }
        }
    }
}

/**
 * The compact layout (Phase 1.8, foldable cover
 * screen).
 *
 * The cover screen is small (e.g. 200x400 px on
 * the Galaxy Z Flip). The compact layout drops
 * the editor canvas and shows only the profile
 * selector + the diagnostic; the user picks a
 * profile to load and then opens the device
 * to edit.
 */
@Composable
private fun CompactMainScreen(
    engine: CanonicalInputEngine,
    profile: Profile,
    allProfiles: List<Profile>,
    onProfileSelected: (Int) -> Unit,
    onProfileUpdated: (Profile) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F12)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Elysium Nexus (cover)",
                color = Color(0xFFF2F2F4),
                fontSize = 14.sp
            )
            Text(
                text = "Tap a profile to load:",
                color = Color(0xFFAAAAAA),
                fontSize = 10.sp
            )
            ProfileSelector(
                profiles = allProfiles,
                currentProfileId = profile.id,
                onProfileSelected = onProfileSelected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
