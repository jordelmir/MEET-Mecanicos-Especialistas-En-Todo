package com.elysium.nexus.fabric.canonical

/**
 * The §4.3 canonical capability enum.
 *
 * Every controllable dimension of a device maps
 * to exactly one [Capability]. Protocol-specific
 * commands (Zigbee cluster, Matter attribute,
 * Z-Wave command class, vendor REST endpoint)
 * translate **into** a [Capability] at the
 * adapter boundary; the rest of the system
 * speaks only [Capability] and the §4.2
 * [DeviceTwin].
 *
 * The enum is **closed**: adding a new
 * capability is a breaking change to the
 * canonical model and requires a new ADR +
 * versioned schema migration. The closed
 * set is the discipline that makes
 * protocol-neutral automation possible
 * (a "turn off" action works on any device
 * that exposes [OnOff], regardless of
 * whether it speaks Matter, Zigbee, Z-Wave,
 * BLE, or vendor REST).
 *
 * Each variant carries a default
 * [ActionRisk] (§31.4) so the policy engine
 * can refuse to execute high-risk commands
 * without an explicit per-call risk class.
 * The risk is a **default**; an automation
 * or a UI can override it (with audit).
 */
enum class Capability(val defaultRisk: ActionRisk) {
    // --- Power & toggles ---------------------------------------------
    OnOff(ActionRisk.Low),
    Toggle(ActionRisk.Low),

    // --- Levels & color ---------------------------------------------
    Level(ActionRisk.Low),
    Color(ActionRisk.Low),
    ColorTemperature(ActionRisk.Low),

    // --- Climate -----------------------------------------------------
    Temperature(ActionRisk.Low),
    TargetTemperature(ActionRisk.Reversible),
    FanSpeed(ActionRisk.Low),
    Swing(ActionRisk.Low),
    Mode(ActionRisk.Low),
    Timer(ActionRisk.Low),

    // --- Position & motion ------------------------------------------
    OpenClose(ActionRisk.PhysicalMotion),
    Position(ActionRisk.PhysicalMotion),
    Direction(ActionRisk.PhysicalMotion),

    // --- Lock & security --------------------------------------------
    LockUnlock(ActionRisk.SecuritySensitive),
    ArmDisarm(ActionRisk.SecuritySensitive),
    Doorbell(ActionRisk.PrivacySensitive),
    Presence(ActionRisk.PrivacySensitive),

    // --- Media -------------------------------------------------------
    StartStop(ActionRisk.Low),
    PauseResume(ActionRisk.Low),
    MediaTransport(ActionRisk.Low),
    Volume(ActionRisk.Low),
    Channel(ActionRisk.Low),
    InputSource(ActionRisk.Low),
    Scene(ActionRisk.Low),

    // --- Energy ------------------------------------------------------
    EnergyRead(ActionRisk.Informational),
    EnergyControl(ActionRisk.HighPower),
    Charging(ActionRisk.HighPower),

    // --- Cameras -----------------------------------------------------
    CameraStream(ActionRisk.PrivacySensitive),
    CameraPtz(ActionRisk.PrivacySensitive),
    CameraTalk(ActionRisk.PrivacySensitive),
    CameraRecord(ActionRisk.PrivacySensitive),
    MotionDetection(ActionRisk.Informational),
    ContactDetection(ActionRisk.Informational),
    SmokeDetection(ActionRisk.LifeSafety),
    CarbonMonoxideDetection(ActionRisk.LifeSafety),
    WaterLeakDetection(ActionRisk.Reversible),
    AirQuality(ActionRisk.Informational),

    // --- Water & irrigation -----------------------------------------
    Irrigation(ActionRisk.Reversible),

    // --- Escape hatch ------------------------------------------------
    Custom(ActionRisk.Reversible);

    companion object {
        /**
         * The set of capabilities that, if exercised
         * on a security-class device (lock, alarm,
         * garage), require step-up authentication
         * per §18.1.
         */
        val SECURITY_SENSITIVE: Set<Capability> = setOf(
            LockUnlock,
            ArmDisarm
        )

        /**
         * The set of capabilities that may affect
         * life safety. The policy engine refuses
         * to silently disable these; an automation
         * that turns them off requires Owner role.
         */
        val LIFE_SAFETY: Set<Capability> = setOf(
            SmokeDetection,
            CarbonMonoxideDetection
        )
    }
}

/**
 * The §31.4 action risk class. The policy engine
 * uses this to gate execution: a high-risk action
 * requires the corresponding [AuthenticationLevel]
 * + explicit confirmation + audit. The class is
 * a **default**; an automation or a UI can override
 * it (with audit) when the device category demands
 * a different risk profile.
 */
enum class ActionRisk {
    Informational,
    Low,
    Reversible,
    PhysicalMotion,
    PrivacySensitive,
    SecuritySensitive,
    HighPower,
    LifeSafety;

    /**
     * @return the minimum [AuthenticationLevel]
     * required to execute an action of this risk
     * class. The mapping is per §6 of
     * `docs/security/THREAT_MODEL.md`:
     *
     * - Informational: `None`
     * - Low: `DeviceSession`
     * - Reversible: `DeviceSession`
     * - PhysicalMotion: `DeviceSession` + summary
     * - PrivacySensitive: `DeviceSession` + summary
     * - SecuritySensitive: `StepUp` + audit
     * - HighPower: `StepUp` + audit
     * - LifeSafety: `StepUp + Owner` + audit + notify
     */
    fun minAuthLevel(): AuthenticationLevel = when (this) {
        Informational -> AuthenticationLevel.None
        Low -> AuthenticationLevel.DeviceSession
        Reversible -> AuthenticationLevel.DeviceSession
        PhysicalMotion -> AuthenticationLevel.DeviceSession
        PrivacySensitive -> AuthenticationLevel.DeviceSession
        SecuritySensitive -> AuthenticationLevel.StepUp
        HighPower -> AuthenticationLevel.StepUp
        LifeSafety -> AuthenticationLevel.StepUpAndOwner
    }
}

/**
 * The §18.1 / §31.3 authentication levels. A
 * command's required level is the
 * [ActionRisk.minAuthLevel] of its risk class;
 * the user must satisfy at least that level
 * to execute. A policy may demand a higher
 * level (per-user, per-device, per-room).
 */
enum class AuthenticationLevel {
    /** No auth required; the device session is enough. */
    None,
    /** The device's session is fresh (within `SESSION_MAX_AGE_MS`). */
    DeviceSession,
    /**
     * Step-up: biometric or PIN within the last
     * `STEP_UP_MAX_AGE_MS`. Used for
     * SecuritySensitive and HighPower.
     */
    StepUp,
    /**
     * Step-up **and** Owner role. Used for
     * LifeSafety.
     */
    StepUpAndOwner;

    companion object {
        /** The default session-max-age: 5 minutes. */
        const val SESSION_MAX_AGE_MS: Long = 5 * 60 * 1000L
        /** The default step-up-max-age: 30 seconds. */
        const val STEP_UP_MAX_AGE_MS: Long = 30 * 1000L
    }
}
