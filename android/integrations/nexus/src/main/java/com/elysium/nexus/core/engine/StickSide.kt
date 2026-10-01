package com.elysium.nexus.core.engine

/**
 * Which side of a paired input is being addressed.
 *
 * The two sticks and the two triggers are paired (left + right),
 * so a single enum covers all four. Using a single enum (instead
 * of separate `LeftStick` / `RightStick` / `LeftTrigger` /
 * `RightTrigger` types) keeps the engine API small: every
 * "latest-wins" input is a `(side, value)` pair, and the side
 * discriminates the destination field.
 */
enum class StickSide {
    Left,
    Right
}
