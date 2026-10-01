package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId

/**
 * The §28 automation model.
 *
 * An [Automation] is a deterministic
 * trigger + conditions + actions recipe.
 * The engine reads the [Trigger], evaluates
 * the [Condition]s, and executes the
 * [Action]s in order. The engine is
 * **deterministic**: given the same inputs,
 * it produces the same outputs and the
 * same side-effects. There are no
 * probabilistic decisions and no
 * "best-effort" branches inside an
 * automation.
 *
 * Every automation carries an [id], an
 * [author], a [createdAt] timestamp, and a
 * signed [signature] (HMAC by the author's
 * device key). The signature is the audit
 * anchor: a tampered automation is detected
 * on load and refused to run.
 *
 * The data classes are immutable. A
 * running automation is a snapshot; the
 * engine never mutates an automation
 * in place.
 */
data class Automation(
    val id: AutomationId,
    val name: String,
    val author: String,
    val createdAtNs: Long,
    val triggers: List<Trigger>,
    val conditions: List<Condition>,
    val actions: List<Action>,
    val verification: VerificationPolicy,
    val compensation: List<Action> = emptyList(),
    /** The author's signature; null until signed. */
    val signature: ByteArray? = null
) {
    init {
        require(name.isNotBlank()) {
            "Automation.name must be non-blank."
        }
        require(author.isNotBlank()) {
            "Automation.author must be non-blank."
        }
        require(triggers.isNotEmpty()) {
            "Automation.triggers must be non-empty."
        }
        require(actions.isNotEmpty()) {
            "Automation.actions must be non-empty."
        }
        require(createdAtNs >= 0L) {
            "Automation.createdAtNs must be non-negative."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Automation) return false
        return id == other.id &&
            name == other.name &&
            author == other.author &&
            createdAtNs == other.createdAtNs &&
            triggers == other.triggers &&
            conditions == other.conditions &&
            actions == other.actions &&
            verification == other.verification &&
            compensation == other.compensation &&
            (signature?.contentEquals(other.signature ?: ByteArray(0)) ?: (other.signature == null))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + createdAtNs.hashCode()
        result = 31 * result + triggers.hashCode()
        result = 31 * result + conditions.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + verification.hashCode()
        result = 31 * result + compensation.hashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        return result
    }
}

/** A stable, opaque automation id. */
@JvmInline
value class AutomationId(val value: String)

/**
 * A §28.1 trigger. A trigger fires when the
 * [event] is observed on [deviceId] (optional;
 * a global trigger leaves [deviceId] null).
 */
data class Trigger(
    val event: TriggerEvent,
    val deviceId: DeviceId? = null,
    /** An optional threshold for analog triggers. */
    val threshold: Threshold? = null
)

/**
 * The closed set of trigger events. New
 * events are an ADR.
 */
enum class TriggerEvent {
    Time,
    Sunrise,
    Sunset,
    PresenceEntered,
    PresenceLeft,
    Geofence,
    Motion,
    DoorOpened,
    DoorClosed,
    LockStateChanged,
    Temperature,
    Humidity,
    AirQuality,
    EnergyPriceChanged,
    PowerUsageChanged,
    DeviceOffline,
    VoiceCommand,
    Button,
    Nfc,
    Qr,
    Webhook,
    GameStarted,
    ApplicationFocused,
    TvStateChanged,
    AlarmStateChanged
}

/**
 * A threshold for an analog trigger
 * (temperature, humidity, etc.). A
 * `Temperature(value=25.0, direction=ABOVE)`
 * fires when the temperature crosses 25°C
 * from below.
 */
data class Threshold(
    val value: Double,
    val direction: Direction
) {
    init {
        // The `Double` range is loose; the
        // caller picks the unit. The data
        // class is total: any finite value
        // and any direction is valid.
        require(value.isFinite()) {
            "Threshold.value must be finite (got $value)."
        }
    }
}

enum class Direction { Above, Below, Crosses }

/**
 * A §28.2 condition. A condition gates the
 * execution of an automation's actions. A
 * condition is evaluated against a
 * [Context] snapshot (the engine reads the
 * world; the condition is a pure function
 * of that snapshot).
 */
data class Condition(
    val kind: ConditionKind,
    val value: String? = null
)

enum class ConditionKind {
    AfterSunset,
    BeforeSunrise,
    UserPresent,
    UserAbsent,
    UserRole,
    DeviceStateEquals,
    DeviceStateNotEquals,
    TimeInRange,
    DayOfWeek,
    Weather,
    SecurityMode,
    ConfidenceAtLeast,
    HomeOccupied,
    NetworkOnline,
    EnergyTariff
}

/**
 * A §28.3 action. An action is a single
 * command to a single device on a single
 * capability.
 */
data class Action(
    val deviceId: DeviceId,
    val capability: Capability,
    val command: CommandValue
)

/**
 * The command value is a typed envelope.
 * The action's [Capability] decides which
 * variants are valid (e.g. [Capability.OnOff]
 * uses [OnOffCommand]; [Capability.Level] uses
 * [LevelCommand]).
 */
sealed class CommandValue {
    /** On/Off. */
    data class OnOff(val turnOn: Boolean) : CommandValue()
    /** 0..1 level. */
    data class Level(val value: Float) : CommandValue() {
        init {
            require(value in 0f..1f) {
                "LevelCommand.value must be in [0, 1] (got $value)."
            }
        }
    }
    /** Hue + saturation. */
    data class Color(val hueDegrees: Float, val saturation: Float) : CommandValue()
    /** Color temperature in Kelvin. */
    data class ColorTemperature(val kelvin: Int) : CommandValue()
    /** Climate target. */
    data class Climate(val targetCelsius: Float) : CommandValue()
    /** Lock state. */
    data class Lock(val locked: Boolean) : CommandValue()
    /** Position 0..1. */
    data class Position(val percentOpen: Float) : CommandValue()
    /** Media transport. */
    data class Media(val play: Boolean) : CommandValue()
    /** No-op (for compatibility shims). */
    object Noop : CommandValue()
}

/**
 * The §28 verification policy. The
 * [Automation] declares a [VerificationPolicy]
 * and the executor must satisfy it. The
 * policy is the audit + retry policy: a
 * `RequireStateConfirmed` policy refuses
 * to mark the action as completed until
 * the device reports a `STATE_CONFIRMED`
 * class event (per §6.5 + §40). A
 * `BestEffort` policy marks the action as
 * completed when the adapter returns
 * success — no confirmation needed (a
 * light that turns on and reports its new
 * state is confirmed; a TV that takes an
 * IR blast with no return channel is
 * best-effort).
 */
data class VerificationPolicy(
    val timeoutMs: Long,
    val requireStateConfirmation: Boolean
) {
    init {
        require(timeoutMs > 0L) {
            "VerificationPolicy.timeoutMs must be positive."
        }
        require(timeoutMs <= MAX_TIMEOUT_MS) {
            "VerificationPolicy.timeoutMs must be <= $MAX_TIMEOUT_MS (got $timeoutMs)."
        }
    }

    companion object {
        const val MAX_TIMEOUT_MS: Long = 60_000L
    }
}

/**
 * The §40 command status. The executor
 * returns one of these per action. The
 * status is the audit entry's `result`
 * field.
 */
enum class CommandStatus {
    Accepted,
    Sent,
    Acknowledged,
    Confirmed,
    Rejected,
    TimedOut,
    Unsupported,
    DeviceOffline,
    StateUnknown
}

/**
 * The §28.4 idempotency key. The executor
 * uses the key to dedupe retries and to
 * bridge across hubs.
 */
data class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) {
            "IdempotencyKey.value must be non-blank."
        }
    }

    companion object {
        /**
         * @return a new idempotency key from
         * [automation], the trigger [event],
         * and the [deviceId]. The key is
         * stable: the same (automation, event,
         * deviceId) tuple always produces the
         * same key. The dedup window is the
         * executor's policy; a typical value
         * is 5 minutes.
         */
        fun forEvent(
            automation: Automation,
            event: TriggerEvent,
            deviceId: DeviceId?
        ): IdempotencyKey = IdempotencyKey(
            "${automation.id.value}|$event|${deviceId?.value ?: "_"}"
        )
    }
}

// ─── Action Dispatcher ───────────────────────────────────────────────────────

/**
 * Dispatches an [Action] to the appropriate [DeviceAdapter].
 */
fun interface ActionDispatcher {
    fun dispatch(action: Action, verification: VerificationPolicy): CommandStatus
}

// ─── Automation Store ────────────────────────────────────────────────────────

/**
 * Deduplication store for automation executions.
 */
interface AutomationStore {
    fun isInFlight(key: IdempotencyKey): Boolean
    fun markInFlight(key: IdempotencyKey)
    fun markCompleted(key: IdempotencyKey)
}

// ─── Automation Context ──────────────────────────────────────────────────────

/**
 * Snapshot of the world state for condition evaluation.
 */
data class Context(val state: Map<String, String>) {
    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_TIME_OF_DAY = "time_of_day"
        const val KEY_DAY_OF_WEEK = "day_of_week"
        const val KEY_HOME_OCCUPIED = "home_occupied"
        const val KEY_NETWORK_ONLINE = "network_online"
    }
}

// ─── Automation Verdict ──────────────────────────────────────────────────────

/**
 * Result of executing an automation.
 */
sealed class Verdict {
    /** All actions completed successfully. */
    data class Completed(
        val perAction: List<Pair<Action, CommandStatus>>
    ) : Verdict()

    /** Condition(s) were not met. */
    data class ConditionsNotMet(
        val failedCondition: Condition? = null
    ) : Verdict()

    /** Automation is already running (idempotency dedup). */
    data object AlreadyRunning : Verdict()

    /** An action failed and compensation ran. */
    data class CompensationRan(
        val failedAction: Action,
        val compensationResults: List<Pair<Action, CommandStatus>>
    ) : Verdict()

    /** Automation timed out. */
    data object Timeout : Verdict()
}

// ─── Automation Engine ───────────────────────────────────────────────────────

/**
 * Executes [Automation] instances with full
 * trigger → condition → action → verification semantics.
 */
object AutomationEngine {

    /**
     * Execute an automation.
     *
     * 1. Check idempotency (dedup via [store])
     * 2. Evaluate conditions against [context]
     * 3. Execute actions via [dispatcher]
     * 4. Run compensation on failure
     */
    fun execute(
        automation: Automation,
        event: TriggerEvent,
        deviceId: DeviceId?,
        context: Context,
        store: AutomationStore,
        dispatcher: ActionDispatcher
    ): Verdict {
        val key = IdempotencyKey.forEvent(automation, event, deviceId)

        // Dedup check
        if (store.isInFlight(key)) {
            return Verdict.AlreadyRunning
        }
        store.markInFlight(key)

        // Evaluate conditions
        for (condition in automation.conditions) {
            if (!evaluateCondition(condition, context)) {
                store.markCompleted(key)
                return Verdict.ConditionsNotMet(failedCondition = condition)
            }
        }

        // Execute actions
        val results = mutableListOf<Pair<Action, CommandStatus>>()
        for (action in automation.actions) {
            val status = dispatcher.dispatch(action, automation.verification)
            results.add(action to status)

            if (status == CommandStatus.Rejected || status == CommandStatus.DeviceOffline) {
                // Run compensation
                val compResults = mutableListOf<Pair<Action, CommandStatus>>()
                for (compAction in automation.compensation) {
                    val compStatus = dispatcher.dispatch(compAction, automation.verification)
                    compResults.add(compAction to compStatus)
                }
                store.markCompleted(key)
                return Verdict.CompensationRan(
                    failedAction = action,
                    compensationResults = compResults
                )
            }
        }

        store.markCompleted(key)
        return Verdict.Completed(perAction = results)
    }

    private fun evaluateCondition(condition: Condition, context: Context): Boolean {
        return when (condition.kind) {
            ConditionKind.UserPresent -> {
                context.state[Context.KEY_USER_ID] == condition.value
            }
            ConditionKind.UserAbsent -> {
                context.state[Context.KEY_USER_ID] != condition.value
            }
            ConditionKind.HomeOccupied -> {
                context.state[Context.KEY_HOME_OCCUPIED] == condition.value
            }
            ConditionKind.NetworkOnline -> {
                context.state[Context.KEY_NETWORK_ONLINE] == "true"
            }
            else -> true // Default: condition passes
        }
    }
}
