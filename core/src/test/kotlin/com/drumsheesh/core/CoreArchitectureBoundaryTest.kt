package com.drumsheesh.core

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreArchitectureBoundaryTest {

    @Test
    fun `core module contains zero android or androidx imports`() {
        val possibleRoots = listOf(
            File("src/main/kotlin"),
            File("core/src/main/kotlin")
        )
        val sourceDir = possibleRoots.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not locate :core src/main/kotlin directory from ${File(".").absolutePath}")

        val kotlinFiles = sourceDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Expected at least 3 Kotlin source files in :core", kotlinFiles.size >= 3)

        val forbiddenImportRegex = Regex("""^\s*import\s+android(x)?\..*""")
        val violations = mutableListOf<String>()

        for (file in kotlinFiles) {
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (forbiddenImportRegex.matches(line)) {
                        violations.add("${file.name}:${index + 1} -> ${line.trim()}")
                    }
                }
            }
        }

        assertTrue(
            "Pure-Kotlin :core module must NEVER import android.* or androidx.*, found: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `base domain enums expose 10 drum pads and 4 studio kits`() {
        assertEquals(10, DrumPadId.entries.size)
        assertEquals(4, DrumKitId.entries.size)
        assertTrue(DrumPadId.HI_HAT_CLOSED.chokesOpenHiHat)
        assertEquals(DrumKitId.TRAP_808, DrumKitId.fromId("808-trap"))
        assertTrue(ClockPort.System.currentTimeMillis() > 0L)
    }
}
