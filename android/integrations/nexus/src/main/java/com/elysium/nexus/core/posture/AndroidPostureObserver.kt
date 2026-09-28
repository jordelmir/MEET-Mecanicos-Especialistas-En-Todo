package com.elysium.nexus.core.posture

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * The Android implementation of [PostureObserver].
 *
 * `MASTER_ORDER.md` §16 calls for the project to
 * support foldable postures (open, half-folded,
 * tabletop) and the cover screen. The implementation
 * uses Jetpack WindowManager's [WindowInfoTracker]
 * to observe `FoldingFeature` events and maps them
 * to the closed set of [Posture] values.
 *
 * ## Why a `CoroutineScope` parameter
 *
 * The observer is a long-lived object — it lives
 * for the activity's lifetime. The [scope] is the
 * coroutine scope that hosts the `WindowInfoTracker`
 * collection. The activity passes its own scope
 * (`activityScope`); the observer does not own
 * the scope's lifetime, the activity does. When
 * the activity is destroyed, the scope is cancelled
 * and the observer's collection is cancelled with
 * it.
 *
 * ## Why no `repeatOnLifecycle`
 *
 * The `androidx.lifecycle:lifecycle-runtime-ktx`
 * dependency would give us `repeatOnLifecycle` —
 * but at the cost of pulling in the full Lifecycle
 * library. The current implementation simply
 * collects the flow unconditionally; the activity
 * passes a scope it controls. The flow is cold;
 * when the activity is paused, the OS may pause
 * the `WindowInfoTracker` updates, but the
 * collection itself is still alive. The cost is
 * negligible.
 */
class AndroidPostureObserver(
    private val activity: Activity,
    private val scope: CoroutineScope
) : PostureObserver {

    private val currentRef = AtomicReference<Posture?>(null)
    private var collectionJob: Job? = null

    override fun postures(): Flow<Posture> = callbackFlow {
        val tracker = WindowInfoTracker.getOrCreate(activity)
        val job: Job = scope.launch {
            tracker.windowLayoutInfo(activity).collect { info ->
                val posture = info.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                    ?.let { mapPosture(it) }
                    ?: Posture.UNKNOWN
                currentRef.set(posture)
                trySend(posture)
            }
        }
        collectionJob = job
        awaitClose {
            job.cancel()
            currentRef.set(null)
            collectionJob = null
        }
    }.distinctUntilChanged()

    override fun current(): Posture? = currentRef.get()

    override fun close() {
        collectionJob?.cancel()
        currentRef.set(null)
    }

    private fun mapPosture(feature: FoldingFeature): Posture = when {
        feature.state == FoldingFeature.State.FLAT -> Posture.FLAT
        feature.state == FoldingFeature.State.HALF_OPENED -> Posture.HALF_OPENED
        feature.orientation == FoldingFeature.Orientation.HORIZONTAL &&
            feature.isSeparating -> Posture.HALF_OPENED
        else -> Posture.UNKNOWN
    }
}
