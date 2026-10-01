package com.elysium.nexus

/**
 * Placeholder class for Phase 0.1.
 *
 * Per ADR-0001, this module's purpose at 0.1 is to prove the
 * Gradle / AGP / Kotlin toolchain is wired end to end. There is no
 * application logic yet — that arrives in Phase 0.2 (canonical input
 * model) and Phase 0.3 (stick deadzones / curves).
 *
 * The constant [BUILD] is asserted in [com.elysium.nexus.PlaceholderTest]
 * so a regression in the build pipeline (wrong Kotlin compiler, wrong
 * AGP version, missing JDK toolchain) fails the test rather than
 * silently producing an empty APK.
 */
object Placeholder {
    /**
     * The build label this APK was produced from. We pin the value in
     * 0.1 so a future iteration that changes the format has a clear
     * "before/after" to compare against.
     */
    const val BUILD: String = "0.1.0-foundation"
}
