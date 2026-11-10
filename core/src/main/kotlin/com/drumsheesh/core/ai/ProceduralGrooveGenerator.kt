package com.drumsheesh.core.ai

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Origin badge for a generated or recorded groove pattern (`Req 1.1, 1.2`).
 */
enum class GrooveSource(val displayBadge: String) {
    CLOUD_AI("Cloud AI · Gemini"),
    SMART_STUDIO_ENGINE("Smart Studio Engine · Ready"),
    RECORDED_LOOP("Recorded Studio Loop");

    companion object {
        fun fromString(value: String?): GrooveSource =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
                ?: SMART_STUDIO_ENGINE
    }
}

/**
 * Single step trigger within a [GroovePattern] (`Req 1.1`).
 */
data class GrooveStep(
    val stepIndex: Int,
    val padId: DrumPadId,
    val velocity: Float
)

/**
 * Validated, playable multi-bar step sequencer pattern shared across `:core`, `:platform`, `:feature`, and Phase 2 Web (`Req 1.1`).
 */
data class GroovePattern(
    val id: String,
    val title: String,
    val bpm: Int,
    val swingPercent: Int,
    val stepsPerBar: Int = DEFAULT_STEPS_PER_BAR,
    val bars: Int = DEFAULT_BARS,
    val kitId: DrumKitId = DrumKitId.ACOUSTIC_STUDIO,
    val steps: List<GrooveStep>,
    val source: GrooveSource = GrooveSource.SMART_STUDIO_ENGINE
) {
    val totalSteps: Int
        get() = stepsPerBar * bars

    companion object {
        const val MIN_BPM: Int = 40
        const val MAX_BPM: Int = 240
        const val MIN_SWING_PERCENT: Int = 0
        const val MAX_SWING_PERCENT: Int = 75
        const val DEFAULT_STEPS_PER_BAR: Int = 16
        const val DEFAULT_BARS: Int = 2
        const val MIN_VELOCITY: Float = 0.10f
        const val MAX_VELOCITY: Float = 1.00f
    }
}

/**
 * User request payload for AI Text-to-Groove generation (`Req 1.1, 1.3`).
 */
data class AiGrooveRequest(
    val prompt: String,
    val targetKit: DrumKitId? = null
) {
    companion object {
        const val MIN_PROMPT_LENGTH: Int = 3
        const val MAX_PROMPT_LENGTH: Int = 200

        fun validatePrompt(prompt: String): String? {
            val trimmed = prompt.trim()
            return when {
                trimmed.length < MIN_PROMPT_LENGTH ->
                    "Please enter at least $MIN_PROMPT_LENGTH characters describing your drum groove."
                trimmed.length > MAX_PROMPT_LENGTH ->
                    "Prompt must be $MAX_PROMPT_LENGTH characters or fewer."
                else -> null
            }
        }
    }
}

/**
 * Domain port for generating a playable [GroovePattern] from a natural-language prompt (`Req 1.1, 1.2`).
 */
fun interface AiGrooveGeneratorPort {
    suspend fun generateGroove(request: AiGrooveRequest): GroovePattern
}

/**
 * Domain port for persisting and reloading user-saved [GroovePattern] and [RecordedLoop] items (`Req 2.3`).
 */
interface GrooveRepositoryPort {
    suspend fun saveGroove(pattern: GroovePattern)
    suspend fun loadSavedGrooves(): List<GroovePattern>
    suspend fun saveRecordedLoop(loop: RecordedLoop)
    suspend fun loadRecordedLoops(): List<RecordedLoop>
}

/**
 * Builds structured system and user prompts for Gemini `gemini-2.5-flash` JSON generation (`Req 1.1, 1.2`).
 */
object AiGroovePromptBuilder {

    val QuickVibePresets: List<String> = listOf(
        "90s Boom Bap",
        "140 BPM Trap Roll",
        "165 BPM Amen Break",
        "Double-Bass Metal"
    )

    fun buildSystemInstruction(): String =
        """
        You are the Drum Sheesh Studio Groove Architect.
        Respond ONLY with a single valid JSON object matching this exact schema (no markdown commentary):
        {
          "id": "groove-custom-slug",
          "title": "Human-readable groove title",
          "bpm": 94,
          "swingPercent": 18,
          "stepsPerBar": 16,
          "bars": 2,
          "kitId": "acoustic-studio | 808-trap | vintage-breakbeat | synthwave-electronic",
          "steps": [
            { "stepIndex": 0, "padId": "KICK_RIGHT", "velocity": 0.95 }
          ]
        }
        Valid padId values: CRASH, RIDE, HI_HAT_OPEN, HI_HAT_CLOSED, TOM_HIGH, TOM_MID, TOM_FLOOR, SNARE, KICK_LEFT, KICK_RIGHT.
        Constraints: bpm in 40..240, swingPercent in 0..75, stepsPerBar = 16, bars = 2, stepIndex in 0..31, velocity in 0.1..1.0.
        """.trimIndent()

    fun buildUserPrompt(request: AiGrooveRequest): String {
        val cleaned = request.prompt.trim().replace("\"", "\\\"")
        val kitDirective = request.targetKit?.let { "Target drum kit preset: ${it.id}." } ?: ""
        return "Generate a 2-bar (32-step, 16 steps/bar) playable drum groove for: \"$cleaned\". $kitDirective".trim()
    }
}

/**
 * Pure-Kotlin JSON serializer & validator for [GroovePattern] and [RecordedLoop] (`Req 1.1, 1.2, 2.3`).
 *
 * Uses zero Android or external libraries so `:core` remains 100% portable across JVM and Phase 2 Web.
 */
object GrooveJsonCodec {

    fun encodeGroovePattern(pattern: GroovePattern): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"id\": \"").append(escapeJson(pattern.id)).append("\",\n")
        sb.append("  \"title\": \"").append(escapeJson(pattern.title)).append("\",\n")
        sb.append("  \"bpm\": ").append(pattern.bpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)).append(",\n")
        sb.append("  \"swingPercent\": ")
            .append(pattern.swingPercent.coerceIn(GroovePattern.MIN_SWING_PERCENT, GroovePattern.MAX_SWING_PERCENT))
            .append(",\n")
        sb.append("  \"stepsPerBar\": ").append(pattern.stepsPerBar).append(",\n")
        sb.append("  \"bars\": ").append(pattern.bars).append(",\n")
        sb.append("  \"kitId\": \"").append(escapeJson(pattern.kitId.id)).append("\",\n")
        sb.append("  \"source\": \"").append(pattern.source.name).append("\",\n")
        sb.append("  \"steps\": [\n")
        pattern.steps.forEachIndexed { index, step ->
            val formattedVel = String.format(Locale.US, "%.2f", step.velocity.coerceIn(GroovePattern.MIN_VELOCITY, GroovePattern.MAX_VELOCITY))
            sb.append("    { \"stepIndex\": ").append(step.stepIndex)
                .append(", \"padId\": \"").append(step.padId.name)
                .append("\", \"velocity\": ").append(formattedVel).append(" }")
            if (index < pattern.steps.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun decodeGroovePattern(
        rawJson: String,
        defaultSource: GrooveSource = GrooveSource.SMART_STUDIO_ENGINE
    ): GroovePattern {
        val cleaned = stripMarkdownCodeFences(rawJson)
        require(cleaned.contains("{") && cleaned.contains("}")) {
            "Invalid GroovePattern JSON: missing root object braces"
        }

        val id = extractStringField(cleaned, "id")?.takeIf { it.isNotBlank() } ?: "groove-ai-generated"
        val title = extractStringField(cleaned, "title")?.takeIf { it.isNotBlank() } ?: "AI Studio Groove"
        val bpm = (extractIntField(cleaned, "bpm") ?: 110)
            .coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)
        val swingPercent = (extractIntField(cleaned, "swingPercent") ?: 0)
            .coerceIn(GroovePattern.MIN_SWING_PERCENT, GroovePattern.MAX_SWING_PERCENT)
        val stepsPerBar = (extractIntField(cleaned, "stepsPerBar") ?: GroovePattern.DEFAULT_STEPS_PER_BAR)
            .coerceIn(8, 32)
        val bars = (extractIntField(cleaned, "bars") ?: GroovePattern.DEFAULT_BARS)
            .coerceIn(1, 8)
        val totalSteps = stepsPerBar * bars
        val kitIdRaw = extractStringField(cleaned, "kitId") ?: DrumKitId.ACOUSTIC_STUDIO.id
        val kitId = DrumKitId.fromId(kitIdRaw)
        val sourceRaw = extractStringField(cleaned, "source")
        val source = if (sourceRaw != null) GrooveSource.fromString(sourceRaw) else defaultSource

        val stepsBlock = extractArrayBlock(cleaned, "steps")
            ?: throw IllegalArgumentException("Invalid GroovePattern JSON: missing 'steps' array")

        val stepObjectRegex = Regex("""\{[^{}]*\}""")
        val parsedSteps = stepObjectRegex.findAll(stepsBlock).mapNotNull { match ->
            val obj = match.value
            val rawStepIndex = extractIntField(obj, "stepIndex") ?: return@mapNotNull null
            val padRaw = extractStringField(obj, "padId") ?: return@mapNotNull null
            val padId = resolvePadId(padRaw) ?: return@mapNotNull null
            val velocity = (extractFloatField(obj, "velocity") ?: 0.85f)
                .coerceIn(GroovePattern.MIN_VELOCITY, GroovePattern.MAX_VELOCITY)
            val clampedStepIndex = rawStepIndex.coerceIn(0, totalSteps - 1)
            GrooveStep(
                stepIndex = clampedStepIndex,
                padId = padId,
                velocity = velocity
            )
        }.distinctBy { it.stepIndex to it.padId }
            .sortedWith(compareBy<GrooveStep> { it.stepIndex }.thenBy { it.padId.ordinal })
            .toList()

        require(parsedSteps.isNotEmpty()) {
            "Invalid GroovePattern JSON: 'steps' array contained zero valid DrumPadId triggers"
        }

        return GroovePattern(
            id = id,
            title = title,
            bpm = bpm,
            swingPercent = swingPercent,
            stepsPerBar = stepsPerBar,
            bars = bars,
            kitId = kitId,
            steps = parsedSteps,
            source = source
        )
    }

    fun encodeRecordedLoop(loop: RecordedLoop): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"id\": \"").append(escapeJson(loop.id)).append("\",\n")
        sb.append("  \"title\": \"").append(escapeJson(loop.title)).append("\",\n")
        sb.append("  \"bpm\": ").append(loop.bpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)).append(",\n")
        sb.append("  \"swingPercent\": ")
            .append(loop.swingPercent.coerceIn(GroovePattern.MIN_SWING_PERCENT, GroovePattern.MAX_SWING_PERCENT))
            .append(",\n")
        sb.append("  \"bars\": ").append(loop.bars.coerceIn(1, 8)).append(",\n")
        sb.append("  \"kitId\": \"").append(escapeJson(loop.kitId.id)).append("\",\n")
        sb.append("  \"isQuantized\": ").append(loop.isQuantized).append(",\n")
        sb.append("  \"createdAtMs\": ").append(loop.createdAtMs).append(",\n")
        sb.append("  \"originalHits\": [\n")
        appendHitsJson(sb, loop.originalHits)
        sb.append("  ],\n")
        sb.append("  \"activeHits\": [\n")
        appendHitsJson(sb, loop.activeHits)
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun decodeRecordedLoop(rawJson: String): RecordedLoop {
        val cleaned = stripMarkdownCodeFences(rawJson)
        require(cleaned.contains("{") && cleaned.contains("}")) {
            "Invalid RecordedLoop JSON: missing root object braces"
        }

        val id = extractStringField(cleaned, "id")?.takeIf { it.isNotBlank() } ?: "loop-recorded"
        val title = extractStringField(cleaned, "title")?.takeIf { it.isNotBlank() } ?: "Studio Take"
        val bpm = (extractIntField(cleaned, "bpm") ?: 110)
            .coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)
        val swingPercent = (extractIntField(cleaned, "swingPercent") ?: 0)
            .coerceIn(GroovePattern.MIN_SWING_PERCENT, GroovePattern.MAX_SWING_PERCENT)
        val bars = (extractIntField(cleaned, "bars") ?: 2).coerceIn(1, 8)
        val kitId = DrumKitId.fromId(extractStringField(cleaned, "kitId") ?: DrumKitId.ACOUSTIC_STUDIO.id)
        val isQuantized = extractBooleanField(cleaned, "isQuantized") ?: false
        val createdAtMs = extractLongField(cleaned, "createdAtMs") ?: 0L

        val originalHits = parseHitArray(extractArrayBlock(cleaned, "originalHits") ?: "")
        val activeHitsRaw = extractArrayBlock(cleaned, "activeHits")
        val activeHits = if (activeHitsRaw != null) parseHitArray(activeHitsRaw) else originalHits

        return RecordedLoop(
            id = id,
            title = title,
            bpm = bpm,
            swingPercent = swingPercent,
            bars = bars,
            kitId = kitId,
            originalHits = originalHits,
            activeHits = activeHits,
            isQuantized = isQuantized,
            createdAtMs = createdAtMs
        )
    }

    private fun appendHitsJson(sb: StringBuilder, hits: List<RecordedHitEvent>) {
        hits.forEachIndexed { index, hit ->
            val vel = String.format(Locale.US, "%.2f", hit.velocity.coerceIn(GroovePattern.MIN_VELOCITY, GroovePattern.MAX_VELOCITY))
            sb.append("    { \"padId\": \"").append(hit.padId.name)
                .append("\", \"offsetMs\": ").append(hit.offsetMs.coerceAtLeast(0L))
                .append(", \"velocity\": ").append(vel).append(" }")
            if (index < hits.lastIndex) sb.append(",")
            sb.append("\n")
        }
    }

    private fun parseHitArray(arrayBlock: String): List<RecordedHitEvent> {
        val objRegex = Regex("""\{[^{}]*\}""")
        return objRegex.findAll(arrayBlock).mapNotNull { match ->
            val obj = match.value
            val padRaw = extractStringField(obj, "padId") ?: return@mapNotNull null
            val padId = resolvePadId(padRaw) ?: return@mapNotNull null
            val offsetMs = (extractLongField(obj, "offsetMs") ?: 0L).coerceAtLeast(0L)
            val velocity = (extractFloatField(obj, "velocity") ?: 0.85f)
                .coerceIn(GroovePattern.MIN_VELOCITY, GroovePattern.MAX_VELOCITY)
            RecordedHitEvent(
                padId = padId,
                offsetMs = offsetMs,
                velocity = velocity
            )
        }.toList()
    }

    internal fun resolvePadId(raw: String): DrumPadId? {
        val normalized = raw.trim().uppercase(Locale.US).replace('-', '_').replace(' ', '_')
        DrumPadId.entries.firstOrNull { it.name == normalized || it.shortLabel.uppercase(Locale.US).replace(' ', '_') == normalized }
            ?.let { return it }

        return when (normalized) {
            "KICK", "BASS_DRUM", "BD" -> DrumPadId.KICK_RIGHT
            "DOUBLE_KICK", "KICK_2" -> DrumPadId.KICK_LEFT
            "CLOSED_HI_HAT", "HIHAT_CLOSED", "HH_CLOSED", "CHH" -> DrumPadId.HI_HAT_CLOSED
            "OPEN_HI_HAT", "HIHAT_OPEN", "HH_OPEN", "OHH" -> DrumPadId.HI_HAT_OPEN
            "SNARE_DRUM", "SD" -> DrumPadId.SNARE
            "HIGH_TOM", "TOM1" -> DrumPadId.TOM_HIGH
            "MID_TOM", "TOM2" -> DrumPadId.TOM_MID
            "FLOOR", "TOM3", "LOW_TOM" -> DrumPadId.TOM_FLOOR
            else -> null
        }
    }

    private fun stripMarkdownCodeFences(raw: String): String {
        val trimmed = raw.trim()
        val fenceRegex = Regex("""^```(?:json)?\s*([\s\S]*?)\s*```$""", RegexOption.IGNORE_CASE)
        val match = fenceRegex.find(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    private fun extractStringField(json: String, field: String): String? {
        val regex = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = regex.find(json)?.groupValues?.get(1) ?: return null
        return raw.replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun extractIntField(json: String, field: String): Int? {
        val regex = Regex(""""$field"\s*:\s*(-?\d+(?:\.\d+)?)""")
        val raw = regex.find(json)?.groupValues?.get(1) ?: return null
        return raw.toDoubleOrNull()?.roundToInt()
    }

    private fun extractLongField(json: String, field: String): Long? {
        val regex = Regex(""""$field"\s*:\s*(-?\d+)""")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractFloatField(json: String, field: String): Float? {
        val regex = Regex(""""$field"\s*:\s*(-?\d+(?:\.\d+)?)""")
        return regex.find(json)?.groupValues?.get(1)?.toFloatOrNull()
    }

    private fun extractBooleanField(json: String, field: String): Boolean? {
        val regex = Regex(""""$field"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
        return regex.find(json)?.groupValues?.get(1)?.lowercase(Locale.US)?.toBooleanStrictOrNull()
    }

    private fun extractArrayBlock(json: String, field: String): String? {
        val keyRegex = Regex(""""$field"\s*:\s*\[""")
        val match = keyRegex.find(json) ?: return null
        val startBracketIndex = match.range.last
        var depth = 0
        for (i in startBracketIndex until json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return json.substring(startBracketIndex + 1, i)
                    }
                }
            }
        }
        return null
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}

/**
 * Pure-Kotlin rule/grammar beat generator in `:core` (`Req 1.1, 1.2`).
 *
 * Extracts BPM (`40..240`), swing (`0..75%`), kit preset, and genre archetypes
 * (*boom bap, trap, rock, metal, funk, house, dnb, jazz, punk, lofi*) from any natural-language prompt.
 */
object ProceduralGrooveGenerator : AiGrooveGeneratorPort {

    enum class GenreArchetype(
        val slug: String,
        val displayTitle: String,
        val defaultBpm: Int,
        val defaultSwingPercent: Int,
        val defaultKit: DrumKitId
    ) {
        BOOM_BAP("boom-bap", "Neo-Soul Boom Bap", 92, 24, DrumKitId.VINTAGE_BREAKBEAT),
        TRAP("trap", "808 Trap Hi-Hat Roll", 140, 8, DrumKitId.TRAP_808),
        DNB("dnb", "Jungle Amen Breakbeat", 165, 0, DrumKitId.VINTAGE_BREAKBEAT),
        METAL("metal", "Double-Bass Thrash Metal", 155, 0, DrumKitId.ACOUSTIC_STUDIO),
        FUNK("funk", "Syncopated Ghost-Note Funk", 104, 16, DrumKitId.VINTAGE_BREAKBEAT),
        HOUSE("house", "Four-on-the-Floor Studio House", 124, 10, DrumKitId.SYNTHWAVE_ELECTRONIC),
        JAZZ("jazz", "Swung Jazz Ride Spang-a-Lang", 116, 38, DrumKitId.ACOUSTIC_STUDIO),
        PUNK("punk", "Fast Skate Punk D-Beat", 176, 0, DrumKitId.ACOUSTIC_STUDIO),
        LOFI("lofi", "Late-Night Dusty Lo-Fi Pocket", 82, 28, DrumKitId.VINTAGE_BREAKBEAT),
        ROCK("rock", "Arena Studio Rock Backbeat", 114, 0, DrumKitId.ACOUSTIC_STUDIO)
    }

    override suspend fun generateGroove(request: AiGrooveRequest): GroovePattern =
        generate(request)

    fun generate(request: AiGrooveRequest): GroovePattern {
        val rawPrompt = request.prompt.trim().ifBlank { "110 BPM Studio Rock Groove" }
        val lower = rawPrompt.lowercase(Locale.US)

        val archetype = detectArchetype(lower)
        val extractedBpm = extractBpm(lower) ?: archetype.defaultBpm
        val clampedBpm = extractedBpm.coerceIn(GroovePattern.MIN_BPM, GroovePattern.MAX_BPM)

        val extractedSwing = extractSwingPercent(lower) ?: archetype.defaultSwingPercent
        val clampedSwing = extractedSwing.coerceIn(
            GroovePattern.MIN_SWING_PERCENT,
            GroovePattern.MAX_SWING_PERCENT
        )

        val kit = request.targetKit ?: detectKitOverride(lower) ?: archetype.defaultKit
        val stepsPerBar = GroovePattern.DEFAULT_STEPS_PER_BAR
        val bars = GroovePattern.DEFAULT_BARS
        val totalSteps = stepsPerBar * bars

        val baseSteps = buildArchetypeSteps(
            archetype = archetype,
            lowerPrompt = lower,
            totalSteps = totalSteps
        )

        val title = buildGrooveTitle(clampedBpm, archetype, rawPrompt)
        val slugHash = (rawPrompt.lowercase(Locale.US).hashCode() and 0x7FFFFFFF) % 10000

        return GroovePattern(
            id = "groove-${archetype.slug}-$clampedBpm-$slugHash",
            title = title,
            bpm = clampedBpm,
            swingPercent = clampedSwing,
            stepsPerBar = stepsPerBar,
            bars = bars,
            kitId = kit,
            steps = baseSteps,
            source = GrooveSource.SMART_STUDIO_ENGINE
        )
    }

    internal fun detectArchetype(lowerPrompt: String): GenreArchetype = when {
        lowerPrompt.contains("dilla") ||
            lowerPrompt.contains("boom bap") ||
            lowerPrompt.contains("boombap") ||
            lowerPrompt.contains("neo-soul") ||
            lowerPrompt.contains("neo soul") ||
            lowerPrompt.contains("hip hop") ||
            lowerPrompt.contains("hip-hop") -> GenreArchetype.BOOM_BAP
        lowerPrompt.contains("trap") ||
            lowerPrompt.contains("808") ||
            lowerPrompt.contains("drill") -> GenreArchetype.TRAP
        lowerPrompt.contains("amen") ||
            lowerPrompt.contains("dnb") ||
            lowerPrompt.contains("drum and bass") ||
            lowerPrompt.contains("drum & bass") ||
            lowerPrompt.contains("jungle") ||
            lowerPrompt.contains("breakbeat") -> GenreArchetype.DNB
        lowerPrompt.contains("metal") ||
            lowerPrompt.contains("double-bass") ||
            lowerPrompt.contains("double bass") ||
            lowerPrompt.contains("blast") ||
            lowerPrompt.contains("thrash") -> GenreArchetype.METAL
        lowerPrompt.contains("funk") ||
            lowerPrompt.contains("disco") ||
            lowerPrompt.contains("linear") -> GenreArchetype.FUNK
        lowerPrompt.contains("house") ||
            lowerPrompt.contains("techno") ||
            lowerPrompt.contains("synthwave") ||
            lowerPrompt.contains("edm") ||
            lowerPrompt.contains("four on the floor") -> GenreArchetype.HOUSE
        lowerPrompt.contains("jazz") ||
            lowerPrompt.contains("bebop") ||
            lowerPrompt.contains("bossa") -> GenreArchetype.JAZZ
        lowerPrompt.contains("punk") ||
            lowerPrompt.contains("hardcore") ||
            lowerPrompt.contains("d-beat") -> GenreArchetype.PUNK
        lowerPrompt.contains("lofi") ||
            lowerPrompt.contains("lo-fi") ||
            lowerPrompt.contains("chill") -> GenreArchetype.LOFI
        else -> GenreArchetype.ROCK
    }

    internal fun extractBpm(lowerPrompt: String): Int? {
        // Explicit "94 bpm" or "bpm 94"
        val explicitBpmRegex = Regex("""(?:(\d{2,3})\s*bpm|bpm\s*:?\s*(\d{2,3}))""")
        explicitBpmRegex.find(lowerPrompt)?.let { match ->
            val value = (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()
            if (value != null) return value
        }

        // Fallback: standalone 2-to-3 digit number that isn't "808" or "90s" / "80s" / "70s" / "1/16"
        val numberRegex = Regex("""\b(\d{2,3})\b""")
        for (match in numberRegex.findAll(lowerPrompt)) {
            val token = match.groupValues[1]
            val endIdx = match.range.last + 1
            val followedByDecadeS = endIdx < lowerPrompt.length && lowerPrompt[endIdx] == 's'
            val precededBySlash = match.range.first > 0 && lowerPrompt[match.range.first - 1] == '/'
            if (token != "808" && !followedByDecadeS && !precededBySlash) {
                val candidate = token.toIntOrNull()
                if (candidate != null && candidate in 30..300) {
                    return candidate
                }
            }
        }
        return null
    }

    internal fun extractSwingPercent(lowerPrompt: String): Int? {
        val explicitSwingRegex = Regex("""(\d{1,2})\s*%\s*swing|swing\s*(\d{1,2})\s*%?""")
        explicitSwingRegex.find(lowerPrompt)?.let { match ->
            val value = (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()
            if (value != null) return value
        }
        return when {
            lowerPrompt.contains("heavy swing") || lowerPrompt.contains("drunk") || lowerPrompt.contains("dilla") -> 28
            lowerPrompt.contains("swung") || lowerPrompt.contains("swing") || lowerPrompt.contains("shuffle") -> 22
            lowerPrompt.contains("straight") -> 0
            else -> null
        }
    }

    private fun detectKitOverride(lowerPrompt: String): DrumKitId? = when {
        lowerPrompt.contains("808") || lowerPrompt.contains("trap") -> DrumKitId.TRAP_808
        lowerPrompt.contains("vintage") || lowerPrompt.contains("break") || lowerPrompt.contains("boom bap") -> DrumKitId.VINTAGE_BREAKBEAT
        lowerPrompt.contains("synthwave") || lowerPrompt.contains("electronic") || lowerPrompt.contains("house") -> DrumKitId.SYNTHWAVE_ELECTRONIC
        lowerPrompt.contains("acoustic") -> DrumKitId.ACOUSTIC_STUDIO
        else -> null
    }

    private fun buildArchetypeSteps(
        archetype: GenreArchetype,
        lowerPrompt: String,
        totalSteps: Int
    ): List<GrooveStep> {
        val steps = mutableListOf<GrooveStep>()
        val wantsGhostSnares = lowerPrompt.contains("ghost") ||
            archetype == GenreArchetype.BOOM_BAP ||
            archetype == GenreArchetype.FUNK ||
            archetype == GenreArchetype.DNB

        fun addStep(step: Int, padId: DrumPadId, velocity: Float) {
            if (step in 0 until totalSteps) {
                steps.add(
                    GrooveStep(
                        stepIndex = step,
                        padId = padId,
                        velocity = velocity.coerceIn(GroovePattern.MIN_VELOCITY, GroovePattern.MAX_VELOCITY)
                    )
                )
            }
        }

        // Crash on bar 1 downbeat
        addStep(0, DrumPadId.CRASH, 0.90f)

        for (bar in 0 until (totalSteps / 16)) {
            val base = bar * 16
            when (archetype) {
                GenreArchetype.BOOM_BAP, GenreArchetype.LOFI -> {
                    // Kick on 0, 7, 10 (and bar 2 variation on 14)
                    addStep(base + 0, DrumPadId.KICK_RIGHT, 0.95f)
                    addStep(base + 7, DrumPadId.KICK_RIGHT, 0.78f)
                    addStep(base + 10, DrumPadId.KICK_RIGHT, 0.88f)
                    if (bar % 2 == 1) addStep(base + 14, DrumPadId.KICK_LEFT, 0.72f)

                    // Snare backbeat on 4 and 12
                    addStep(base + 4, DrumPadId.SNARE, 0.92f)
                    addStep(base + 12, DrumPadId.SNARE, 0.94f)

                    // Ghost snares on 9 and 15
                    if (wantsGhostSnares) {
                        addStep(base + 9, DrumPadId.SNARE, 0.48f)
                        addStep(base + 15, DrumPadId.SNARE, 0.52f)
                    }

                    // 8th-note swung hi-hats + open hat lift on step 14
                    for (s in 0 until 16 step 2) {
                        if (s == 14) {
                            addStep(base + s, DrumPadId.HI_HAT_OPEN, 0.82f)
                        } else {
                            addStep(base + s, DrumPadId.HI_HAT_CLOSED, if (s % 4 == 0) 0.82f else 0.65f)
                        }
                    }
                    addStep(base + 15, DrumPadId.HI_HAT_CLOSED, 0.70f) // Chokes open hat on 15
                }

                GenreArchetype.TRAP -> {
                    // 808 Sub kicks
                    addStep(base + 0, DrumPadId.KICK_RIGHT, 1.00f)
                    addStep(base + 6, DrumPadId.KICK_RIGHT, 0.86f)
                    addStep(base + 11, DrumPadId.KICK_RIGHT, 0.90f)
                    if (bar % 2 == 1) addStep(base + 14, DrumPadId.KICK_LEFT, 0.84f)

                    // Half-time / energetic trap snare on 4 & 12
                    addStep(base + 4, DrumPadId.SNARE, 0.94f)
                    addStep(base + 12, DrumPadId.SNARE, 0.96f)

                    // 16th-note trap hi-hat roll across the bar
                    for (s in 0 until 16) {
                        val vel = when {
                            s % 4 == 0 -> 0.88f
                            s in 6..7 || s in 13..15 -> 0.76f
                            else -> 0.58f
                        }
                        addStep(base + s, DrumPadId.HI_HAT_CLOSED, vel)
                    }
                    addStep(base + 8, DrumPadId.HI_HAT_OPEN, 0.85f)
                    addStep(base + 9, DrumPadId.HI_HAT_CLOSED, 0.80f)
                }

                GenreArchetype.DNB -> {
                    // Classic Amen break skeleton: Kick on 0, 2, 10; Snare on 4, 7, 9, 12, 15
                    addStep(base + 0, DrumPadId.KICK_RIGHT, 0.96f)
                    addStep(base + 2, DrumPadId.KICK_RIGHT, 0.88f)
                    addStep(base + 10, DrumPadId.KICK_RIGHT, 0.92f)

                    addStep(base + 4, DrumPadId.SNARE, 0.95f)
                    addStep(base + 7, DrumPadId.SNARE, 0.55f)
                    addStep(base + 9, DrumPadId.SNARE, 0.58f)
                    addStep(base + 12, DrumPadId.SNARE, 0.96f)
                    addStep(base + 15, DrumPadId.SNARE, 0.62f)

                    for (s in 0 until 16 step 2) {
                        addStep(base + s, DrumPadId.RIDE, 0.78f)
                    }
                }

                GenreArchetype.METAL -> {
                    // Relentless 16th-note alternating double-bass pedal
                    for (s in 0 until 16) {
                        val pad = if (s % 2 == 0) DrumPadId.KICK_RIGHT else DrumPadId.KICK_LEFT
                        addStep(base + s, pad, if (s % 4 == 0) 0.95f else 0.82f)
                    }
                    addStep(base + 4, DrumPadId.SNARE, 0.98f)
                    addStep(base + 12, DrumPadId.SNARE, 0.98f)
                    for (s in 0 until 16 step 2) {
                        addStep(base + s, DrumPadId.RIDE, 0.84f)
                    }
                    if (bar % 2 == 1) {
                        addStep(base + 14, DrumPadId.TOM_HIGH, 0.90f)
                        addStep(base + 15, DrumPadId.TOM_FLOOR, 0.94f)
                    }
                }

                GenreArchetype.FUNK -> {
                    addStep(base + 0, DrumPadId.KICK_RIGHT, 0.95f)
                    addStep(base + 3, DrumPadId.KICK_RIGHT, 0.80f)
                    addStep(base + 6, DrumPadId.KICK_RIGHT, 0.84f)
                    addStep(base + 10, DrumPadId.KICK_RIGHT, 0.90f)

                    addStep(base + 4, DrumPadId.SNARE, 0.94f)
                    addStep(base + 7, DrumPadId.SNARE, 0.50f)
                    addStep(base + 9, DrumPadId.SNARE, 0.52f)
                    addStep(base + 12, DrumPadId.SNARE, 0.95f)
                    addStep(base + 15, DrumPadId.SNARE, 0.55f)

                    for (s in 0 until 16) {
                        if (s == 14) {
                            addStep(base + s, DrumPadId.HI_HAT_OPEN, 0.82f)
                        } else {
                            addStep(base + s, DrumPadId.HI_HAT_CLOSED, if (s % 2 == 0) 0.78f else 0.54f)
                        }
                    }
                }

                GenreArchetype.HOUSE -> {
                    // Four-on-the-floor kick (0, 4, 8, 12) + offbeat open hats (2, 6, 10, 14)
                    for (s in 0 until 16 step 4) {
                        addStep(base + s, DrumPadId.KICK_RIGHT, 0.96f)
                        addStep(base + s, DrumPadId.HI_HAT_CLOSED, 0.72f)
                    }
                    addStep(base + 4, DrumPadId.SNARE, 0.90f)
                    addStep(base + 12, DrumPadId.SNARE, 0.92f)
                    for (s in 2 until 16 step 4) {
                        addStep(base + s, DrumPadId.HI_HAT_OPEN, 0.85f)
                    }
                }

                GenreArchetype.JAZZ -> {
                    // Spang-a-lang ride pattern + hi-hat foot chick on 2 & 4 (steps 4 & 12)
                    val rideSteps = listOf(0, 3, 4, 8, 11, 12, 15)
                    for (s in rideSteps) {
                        addStep(base + s, DrumPadId.RIDE, if (s % 4 == 0) 0.85f else 0.68f)
                    }
                    addStep(base + 4, DrumPadId.HI_HAT_CLOSED, 0.82f)
                    addStep(base + 12, DrumPadId.HI_HAT_CLOSED, 0.82f)
                    addStep(base + 0, DrumPadId.KICK_RIGHT, 0.60f)
                    addStep(base + 10, DrumPadId.SNARE, 0.58f)
                }

                GenreArchetype.PUNK, GenreArchetype.ROCK -> {
                    addStep(base + 0, DrumPadId.KICK_RIGHT, 0.94f)
                    addStep(base + 6, DrumPadId.KICK_RIGHT, 0.82f)
                    addStep(base + 8, DrumPadId.KICK_RIGHT, 0.92f)
                    addStep(base + 10, DrumPadId.KICK_RIGHT, 0.85f)

                    addStep(base + 4, DrumPadId.SNARE, 0.95f)
                    addStep(base + 12, DrumPadId.SNARE, 0.95f)

                    for (s in 0 until 16 step 2) {
                        addStep(base + s, DrumPadId.HI_HAT_CLOSED, 0.80f)
                    }
                    if (bar % 2 == 1) {
                        addStep(base + 13, DrumPadId.TOM_HIGH, 0.85f)
                        addStep(base + 14, DrumPadId.TOM_MID, 0.88f)
                        addStep(base + 15, DrumPadId.TOM_FLOOR, 0.92f)
                    }
                }
            }
        }

        return steps
            .distinctBy { it.stepIndex to it.padId }
            .sortedWith(compareBy<GrooveStep> { it.stepIndex }.thenBy { it.padId.ordinal })
    }

    private fun buildGrooveTitle(bpm: Int, archetype: GenreArchetype, rawPrompt: String): String {
        val compactPrompt = rawPrompt.replace(Regex("""\s+"""), " ").trim()
        return if (compactPrompt.length <= 42) {
            if (compactPrompt.lowercase(Locale.US).contains("bpm")) {
                compactPrompt
            } else {
                "$bpm BPM $compactPrompt"
            }
        } else {
            "$bpm BPM ${archetype.displayTitle}"
        }
    }
}
