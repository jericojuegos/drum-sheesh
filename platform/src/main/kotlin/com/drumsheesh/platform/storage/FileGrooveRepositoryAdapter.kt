package com.drumsheesh.platform.storage

import android.content.Context
import com.drumsheesh.core.ai.GrooveJsonCodec
import com.drumsheesh.core.ai.GroovePattern
import com.drumsheesh.core.ai.GrooveRepositoryPort
import com.drumsheesh.core.ai.RecordedLoop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * App-scoped JSON file persistence adapter for custom [GroovePattern] and [RecordedLoop] items (`Req 2.3`).
 *
 * Persists files inside `filesDir/grooves/<id>.json` and `filesDir/loops/<id>.json`.
 */
class FileGrooveRepositoryAdapter(
    private val rootDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GrooveRepositoryPort {

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(context.filesDir, ioDispatcher)

    private val groovesDir: File
        get() = File(rootDir, GROOVES_FOLDER).apply { if (!exists()) mkdirs() }

    private val loopsDir: File
        get() = File(rootDir, LOOPS_FOLDER).apply { if (!exists()) mkdirs() }

    override suspend fun saveGroove(pattern: GroovePattern) {
        withContext(ioDispatcher) {
            val safeFileName = "${sanitizeId(pattern.id)}.json"
            val targetFile = File(groovesDir, safeFileName)
            val json = GrooveJsonCodec.encodeGroovePattern(pattern)
            targetFile.writeText(json, Charsets.UTF_8)
        }
    }

    override suspend fun loadSavedGrooves(): List<GroovePattern> =
        withContext(ioDispatcher) {
            val files = groovesDir.listFiles { file -> file.isFile && file.extension == "json" }
                ?.sortedByDescending { it.lastModified() }
                ?: return@withContext emptyList()

            files.mapNotNull { file ->
                runCatching {
                    GrooveJsonCodec.decodeGroovePattern(file.readText(Charsets.UTF_8))
                }.getOrNull()
            }
        }

    override suspend fun saveRecordedLoop(loop: RecordedLoop) {
        withContext(ioDispatcher) {
            val safeFileName = "${sanitizeId(loop.id)}.json"
            val targetFile = File(loopsDir, safeFileName)
            val json = GrooveJsonCodec.encodeRecordedLoop(loop)
            targetFile.writeText(json, Charsets.UTF_8)
        }
    }

    override suspend fun loadRecordedLoops(): List<RecordedLoop> =
        withContext(ioDispatcher) {
            val files = loopsDir.listFiles { file -> file.isFile && file.extension == "json" }
                ?: return@withContext emptyList()

            files.mapNotNull { file ->
                runCatching {
                    GrooveJsonCodec.decodeRecordedLoop(file.readText(Charsets.UTF_8))
                }.getOrNull()
            }.sortedByDescending { it.createdAtMs }
        }

    private fun sanitizeId(rawId: String): String =
        rawId.trim()
            .replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
            .take(80)
            .ifBlank { "studio_item" }

    companion object {
        const val GROOVES_FOLDER = "grooves"
        const val LOOPS_FOLDER = "loops"
    }
}
