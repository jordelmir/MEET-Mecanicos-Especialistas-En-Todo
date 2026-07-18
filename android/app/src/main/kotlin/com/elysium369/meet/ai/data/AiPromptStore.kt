package com.elysium369.meet.ai.data

import com.elysium369.meet.ai.domain.AiFeature
import com.elysium369.meet.ai.prompts.SystemPrompts
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPromptStore @Inject constructor() {
    fun getSystemPrompt(feature: AiFeature): String {
        return when (feature) {
            AiFeature.DIAGNOSTIC_DTC -> SystemPrompts.AUTOMOTIVE_CLINICAL
            AiFeature.LIVE_PID_ANALYSIS -> SystemPrompts.LIVE_DATA
            AiFeature.AI_COPILOT -> SystemPrompts.AUTOMOTIVE_CLINICAL
            AiFeature.REPAIR_GUIDE -> SystemPrompts.MECHANICAL_PROCEDURE
            AiFeature.VISUAL_3D_PART_CONTEXT -> SystemPrompts.VISUAL_3D
            AiFeature.REPORT_PRE_SCAN -> SystemPrompts.REPORTS
            AiFeature.REPORT_POST_SCAN -> SystemPrompts.REPORTS
            AiFeature.DVIR -> SystemPrompts.REPORTS
            AiFeature.PREDICTIVE_HEALTH -> SystemPrompts.AUTOMOTIVE_CLINICAL
            AiFeature.OSCILLOSCOPE_ANALYSIS -> SystemPrompts.OSCILLOSCOPE
            AiFeature.TERMINAL_EXPLAINER -> SystemPrompts.TERMINAL
            AiFeature.LIVE_LINK_REMOTE_ASSIST -> SystemPrompts.AUTOMOTIVE_CLINICAL
            AiFeature.MECHANIC_MARKETPLACE -> SystemPrompts.MECHANICS
            AiFeature.GAUGE_MARKET -> SystemPrompts.MARKETPLACE
            AiFeature.MANUAL_CENTER -> SystemPrompts.AUTOMOTIVE_CLINICAL
        }
    }
}
