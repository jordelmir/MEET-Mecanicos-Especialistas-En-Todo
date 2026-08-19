package com.elysium369.meet.core.services.guard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductionHardcodedActorGuardTest {

    private val forbiddenTokens = listOf(
        "mechanic_101",
        "driver_101",
        "store_101",
        "+50688888888",
        "+50677777777",
        "local_shop_id",
        "Grúas Express Pro",
        "Mecánica Elite Pro",
    )

    @Test
    fun verifyNoHardcodedDemoActorsInProductionScreens() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val mainSrcDir = File(projectDir, "src/main/kotlin")
        val searchDir = if (mainSrcDir.exists()) mainSrcDir else File(projectDir, "app/src/main/kotlin")

        if (!searchDir.exists()) {
            // Running from root
            val rootSrc = File("android/app/src/main/kotlin")
            if (rootSrc.exists()) {
                assertDirClean(rootSrc)
                return
            }
        } else {
            assertDirClean(searchDir)
        }
    }

    private fun assertDirClean(dir: File) {
        val violations = mutableListOf<String>()
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            for (token in forbiddenTokens) {
                if (content.contains(token)) {
                    violations.add("File ${file.name} contains forbidden production demo token: $token")
                }
            }
        }
        assertTrue(
            "Found hardcoded demo tokens in production codebase:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
