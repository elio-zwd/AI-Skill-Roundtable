package com.elio.jianyu.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionContractTest {
    @Test
    fun animatedSharedComponentsRespectReducedMotionPreference() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val shell = File(
            root,
            "app/src/main/java/com/elio/jianyu/ui/components/JianyuPageShell.kt",
        ).readText()
        val legacy = File(
            root,
            "app/src/main/java/com/elio/jianyu/ui/components/LegacyUiComponents.kt",
        ).readText()

        assertTrue(shell.contains("val reducedMotion = LocalReducedMotion.current"))
        assertTrue(legacy.contains("val reducedMotion = LocalReducedMotion.current"))
        assertTrue(legacy.contains("targetValue = if (reducedMotion) 1.0f"))
    }
}
