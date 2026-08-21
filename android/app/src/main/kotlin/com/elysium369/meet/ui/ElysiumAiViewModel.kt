package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import com.elysium369.meet.core.evair.agent.AutomotiveAgentGateway
import com.elysium369.meet.core.evair.bridge.VehicleToolFacade
import com.elysium369.meet.core.evair.prediction.LongitudinalHealthPredictor
import com.elysium369.meet.core.evair.state.VehicleStateEngine
import com.elysium369.meet.core.evair.vision.ComponentVisionEngine
import com.elysium369.meet.core.evair.voice.VoiceMechanicOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ElysiumAiViewModel @Inject constructor(
    val facade: VehicleToolFacade,
    val gateway: AutomotiveAgentGateway,
    val stateEngine: VehicleStateEngine,
    val voiceOrchestrator: VoiceMechanicOrchestrator,
    val visionEngine: ComponentVisionEngine,
    val healthPredictor: LongitudinalHealthPredictor,
) : ViewModel()
