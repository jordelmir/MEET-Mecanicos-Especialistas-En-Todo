package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import com.elysium369.meet.ai.DiagnosticAiContextBuilder
import com.elysium369.meet.ai.ProprietaryGroundedContextBuilder
import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** DI boundary for the 3D diagnostic feature; Compose no longer constructs repositories. */
@HiltViewModel
class ComponentLocatorViewModel @Inject constructor(
    val visualRepository: VisualDiagnosticRepositoryImpl,
    val diagnosticAiContextBuilder: DiagnosticAiContextBuilder,
    val proprietaryGroundedContextBuilder: ProprietaryGroundedContextBuilder,
) : ViewModel()
