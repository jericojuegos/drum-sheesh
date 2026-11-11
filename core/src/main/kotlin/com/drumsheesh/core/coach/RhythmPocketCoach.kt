package com.drumsheesh.core.coach

import com.drumsheesh.core.model.DrumPadId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Timing accuracy bucket for a single drum pad hit (`Req 1.3, 2.1, A1`).
 */
enum class HitClassification {
    PERFECT,
    GOOD,
    EARLY,
    LATE,
    MISS
}

/**
 * Overall session timing tendency evaluated by [RhythmPocketCoach] (`Req 2.1`).
 */
enum class TimingTendency(val displayLabel: String) {
    LOCKED_IN("LOCKED IN"),
    RUSHING("RUSHING"),
    DRAGGING("DRAGGING")
}

/**
 * Timestamped touch hit captured from the user on the Drum Stage.
 */
data class ActualPadHit(
    val padId: DrumPadId,
    val actualTimeMs: Long,
    val velocity: Float = 0.85f
)

/**
 * Evaluated timing judgement for an expected groove step or live user hit (`Req 1.3, 2.1`).
 *
 * @property deltaMs Signed millisecond drift (`actualTimeMs - expectedTimeMs`), or `null` on a complete miss.
 */
data class HitJudgement(
    val padId: DrumPadId,
    val expectedTimeMs: Long,
    val actualTimeMs: Long?,
    val deltaMs: Long?,
    val classification: HitClassification,
    val stepScore: Int
)

/**
 * Per-pad accuracy and timing drift metrics inside [PocketCoachReport].
 */
data class PadAccuracyBreakdown(
    val padId: DrumPadId,
    val expectedCount: Int,
    val matchedCount: Int,
    val meanAbsDriftMs: Double,
    val meanSignedDriftMs: Double,
    val accuracyPercent: Int
)

/**
 * Post-session analytics report produced by [RhythmPocketCoach] (`Req 2.1, 2.2`).
 */
data class PocketCoachReport(
    val pocketScore: Int,
    val meanDriftMs: Int,
    val tendency: TimingTendency,
    val perfectCount: Int,
    val goodCount: Int,
    val earlyCount: Int,
    val lateCount: Int,
    val missCount: Int,
    val judgements: List<HitJudgement>,
    val perPadBreakdown: Map<DrumPadId, PadAccuracyBreakdown>,
    val weakestPad: DrumPadId?,
    val coachTip: String
) {
    val totalSteps: Int
        get() = judgements.size

    val perfectPercent: Int
        get() = if (totalSteps == 0) 0 else ((perfectCount * 100.0) / totalSteps).roundToInt()

    val goodPercent: Int
        get() = if (totalSteps == 0) 0 else ((goodCount * 100.0) / totalSteps).roundToInt()

    val earlyLatePercent: Int
        get() = if (totalSteps == 0) 0 else (((earlyCount + lateCount) * 100.0) / totalSteps).roundToInt()

    val missPercent: Int
        get() = if (totalSteps == 0) 0 else ((missCount * 100.0) / totalSteps).roundToInt()
}

/**
 * Pure-Kotlin Rhythm Pocket Coach (`Req 1.3, 2.1, 2.2`).
 *
 * Evaluates millisecond timing drift (`deltaMs = actualTimeMs - expectedTimeMs`), classifies
 * hits (`PERFECT` `<=20ms`, `GOOD` `21..45ms`, `EARLY`/`LATE` `46..90ms`, `MISS` `>90ms`),
 * detects `RUSHING` (`< -8ms`), `DRAGGING` (`> +8ms`), or `LOCKED_IN` (`-8..+8ms`) tendency,
 * and synthesizes a deterministic 2-sentence coaching recommendation targeting the weakest pad.
 */
object RhythmPocketCoach {

    const val PERFECT_WINDOW_MS: Long = 20L
    const val GOOD_WINDOW_MS: Long = 45L
    const val EARLY_LATE_WINDOW_MS: Long = 90L
    const val MAX_MATCH_WINDOW_MS: Long = 120L

    const val RUSHING_THRESHOLD_MS: Int = -8
    const val DRAGGING_THRESHOLD_MS: Int = 8

    private const val SCORE_PERFECT: Int = 100
    private const val SCORE_GOOD: Int = 80
    private const val SCORE_EARLY_OR_LATE: Int = 50
    private const val SCORE_MISS: Int = 0

    /**
     * Classifies a signed [deltaMs] (`actualTimeMs - expectedTimeMs`) into [HitClassification].
     */
    fun classifyDeltaMs(deltaMs: Long): HitClassification {
        val absDelta = abs(deltaMs)
        return when {
            absDelta <= PERFECT_WINDOW_MS -> HitClassification.PERFECT
            absDelta <= GOOD_WINDOW_MS -> HitClassification.GOOD
            absDelta <= EARLY_LATE_WINDOW_MS -> if (deltaMs < 0L) HitClassification.EARLY else HitClassification.LATE
            else -> HitClassification.MISS
        }
    }

    /**
     * Maps a [HitClassification] to its 0..100 step score weight.
     */
    fun scoreForClassification(classification: HitClassification): Int = when (classification) {
        HitClassification.PERFECT -> SCORE_PERFECT
        HitClassification.GOOD -> SCORE_GOOD
        HitClassification.EARLY, HitClassification.LATE -> SCORE_EARLY_OR_LATE
        HitClassification.MISS -> SCORE_MISS
    }

    /**
     * Classifies signed [meanDriftMs] into [TimingTendency] (`Req 2.1`).
     */
    fun classifyTendency(meanDriftMs: Int): TimingTendency = when {
        meanDriftMs < RUSHING_THRESHOLD_MS -> TimingTendency.RUSHING
        meanDriftMs > DRAGGING_THRESHOLD_MS -> TimingTendency.DRAGGING
        else -> TimingTendency.LOCKED_IN
    }

    /**
     * Evaluates a single live user pad hit against the active [timeline] for instant highway badge feedback (`Req 1.3`).
     */
    fun judgeLiveHit(
        padId: DrumPadId,
        actualTimeMs: Long,
        timeline: List<ScheduledGrooveStep>
    ): HitJudgement {
        val nearestStep = timeline
            .asSequence()
            .filter { it.padId == padId }
            .minByOrNull { abs(actualTimeMs - it.expectedTimeMs) }

        if (nearestStep == null) {
            return HitJudgement(
                padId = padId,
                expectedTimeMs = actualTimeMs,
                actualTimeMs = actualTimeMs,
                deltaMs = null,
                classification = HitClassification.MISS,
                stepScore = SCORE_MISS
            )
        }

        val deltaMs = actualTimeMs - nearestStep.expectedTimeMs
        if (abs(deltaMs) > MAX_MATCH_WINDOW_MS) {
            return HitJudgement(
                padId = padId,
                expectedTimeMs = nearestStep.expectedTimeMs,
                actualTimeMs = actualTimeMs,
                deltaMs = deltaMs,
                classification = HitClassification.MISS,
                stepScore = SCORE_MISS
            )
        }

        val classification = classifyDeltaMs(deltaMs)
        return HitJudgement(
            padId = padId,
            expectedTimeMs = nearestStep.expectedTimeMs,
            actualTimeMs = actualTimeMs,
            deltaMs = deltaMs,
            classification = classification,
            stepScore = scoreForClassification(classification)
        )
    }

    /**
     * Formats a [HitJudgement] into a compact HUD pill label (`"PERFECT -4ms"`, `"LATE +31ms"`, `"MISS"`).
     */
    fun formatJudgementBadge(judgement: HitJudgement): String {
        val delta = judgement.deltaMs
        if (judgement.classification == HitClassification.MISS || delta == null) {
            return "MISS"
        }
        val signedMs = if (delta >= 0L) "+${delta}ms" else "${delta}ms"
        return "${judgement.classification.name} $signedMs"
    }

    /**
     * Evaluates an entire groove pass or session (`Req 2.1, 2.2`).
     *
     * Matches each expected step in [expectedSteps] to the closest unclaimed [ActualPadHit] for the
     * same [DrumPadId] within `±120ms` ([MAX_MATCH_WINDOW_MS]).
     */
    fun evaluate(
        expectedSteps: List<ScheduledGrooveStep>,
        actualHits: List<ActualPadHit>
    ): PocketCoachReport {
        if (expectedSteps.isEmpty()) {
            return PocketCoachReport(
                pocketScore = 0,
                meanDriftMs = 0,
                tendency = TimingTendency.LOCKED_IN,
                perfectCount = 0,
                goodCount = 0,
                earlyCount = 0,
                lateCount = 0,
                missCount = 0,
                judgements = emptyList(),
                perPadBreakdown = emptyMap(),
                weakestPad = null,
                coachTip = "Select a groove pattern and play along with the scrolling notes on the Groove Highway. Focus on locking your Kick and Snare hits with the steady pulse of the metronome."
            )
        }

        val sortedExpected = expectedSteps.sortedBy { it.expectedTimeMs }
        val sortedActual = actualHits.sortedBy { it.actualTimeMs }
        val claimedHitIndices = BooleanArray(sortedActual.size)

        val judgements = ArrayList<HitJudgement>(sortedExpected.size)
        val matchedDeltas = ArrayList<Long>(sortedExpected.size)

        for (step in sortedExpected) {
            var bestHitIdx = -1
            var bestAbsDelta = Long.MAX_VALUE

            for (i in sortedActual.indices) {
                if (claimedHitIndices[i]) continue
                val candidate = sortedActual[i]
                if (candidate.padId != step.padId) continue

                val absDelta = abs(candidate.actualTimeMs - step.expectedTimeMs)
                if (absDelta <= MAX_MATCH_WINDOW_MS && absDelta < bestAbsDelta) {
                    bestAbsDelta = absDelta
                    bestHitIdx = i
                }
            }

            if (bestHitIdx != -1) {
                claimedHitIndices[bestHitIdx] = true
                val matchedHit = sortedActual[bestHitIdx]
                val deltaMs = matchedHit.actualTimeMs - step.expectedTimeMs
                val classification = classifyDeltaMs(deltaMs)
                val stepScore = scoreForClassification(classification)
                matchedDeltas.add(deltaMs)
                judgements.add(
                    HitJudgement(
                        padId = step.padId,
                        expectedTimeMs = step.expectedTimeMs,
                        actualTimeMs = matchedHit.actualTimeMs,
                        deltaMs = deltaMs,
                        classification = classification,
                        stepScore = stepScore
                    )
                )
            } else {
                judgements.add(
                    HitJudgement(
                        padId = step.padId,
                        expectedTimeMs = step.expectedTimeMs,
                        actualTimeMs = null,
                        deltaMs = null,
                        classification = HitClassification.MISS,
                        stepScore = SCORE_MISS
                    )
                )
            }
        }

        val rawPocketScore = judgements.map { it.stepScore }.average().roundToInt().coerceIn(0, 100)
        val meanDriftMs = if (matchedDeltas.isEmpty()) {
            0
        } else {
            matchedDeltas.average().roundToInt()
        }
        val tendency = classifyTendency(meanDriftMs)

        val perfectCount = judgements.count { it.classification == HitClassification.PERFECT }
        val goodCount = judgements.count { it.classification == HitClassification.GOOD }
        val earlyCount = judgements.count { it.classification == HitClassification.EARLY }
        val lateCount = judgements.count { it.classification == HitClassification.LATE }
        val missCount = judgements.count { it.classification == HitClassification.MISS }

        val perPadBreakdown = judgements
            .groupBy { it.padId }
            .mapValues { (padId, padJudgements) ->
                val expectedCount = padJudgements.size
                val matched = padJudgements.filter { it.deltaMs != null }
                val matchedCount = matched.size
                val meanAbsDrift = if (matched.isEmpty()) {
                    MAX_MATCH_WINDOW_MS.toDouble()
                } else {
                    matched.map { abs(it.deltaMs!!).toDouble() }.average()
                }
                val meanSignedDrift = if (matched.isEmpty()) {
                    0.0
                } else {
                    matched.map { it.deltaMs!!.toDouble() }.average()
                }
                val accuracyPct = padJudgements.map { it.stepScore }.average().roundToInt().coerceIn(0, 100)

                PadAccuracyBreakdown(
                    padId = padId,
                    expectedCount = expectedCount,
                    matchedCount = matchedCount,
                    meanAbsDriftMs = meanAbsDrift,
                    meanSignedDriftMs = meanSignedDrift,
                    accuracyPercent = accuracyPct
                )
            }

        // Identify weakest pad: lowest accuracyPercent, tie-broken by highest meanAbsDriftMs
        val weakestBreakdown = perPadBreakdown.values.minWithOrNull(
            compareBy<PadAccuracyBreakdown> { it.accuracyPercent }
                .thenByDescending { it.meanAbsDriftMs }
                .thenBy { it.padId.ordinal }
        )
        val weakestPad = weakestBreakdown?.padId

        val coachTip = buildTwoSentenceCoachTip(
            pocketScore = rawPocketScore,
            meanDriftMs = meanDriftMs,
            tendency = tendency,
            weakestBreakdown = weakestBreakdown,
            matchedHitCount = matchedDeltas.size
        )

        return PocketCoachReport(
            pocketScore = rawPocketScore,
            meanDriftMs = meanDriftMs,
            tendency = tendency,
            perfectCount = perfectCount,
            goodCount = goodCount,
            earlyCount = earlyCount,
            lateCount = lateCount,
            missCount = missCount,
            judgements = judgements,
            perPadBreakdown = perPadBreakdown,
            weakestPad = weakestPad,
            coachTip = coachTip
        )
    }

    private fun buildTwoSentenceCoachTip(
        pocketScore: Int,
        meanDriftMs: Int,
        tendency: TimingTendency,
        weakestBreakdown: PadAccuracyBreakdown?,
        matchedHitCount: Int
    ): String {
        val padName = weakestBreakdown?.padId?.shortLabel ?: "SNARE"

        if (matchedHitCount == 0 || weakestBreakdown == null) {
            return "No matched hits were recorded on $padName during this pass, resulting in a 0/100 pocket score. Try tapping Slow Tempo -10 BPM and focusing on landing $padName right as each note crosses the amber Now-Line."
        }

        val padSignedMs = weakestBreakdown.meanSignedDriftMs.roundToInt()
        val padAbsMs = abs(padSignedMs)

        return when (tendency) {
            TimingTendency.RUSHING -> {
                "Your overall groove is rushing by ${abs(meanDriftMs)}ms on average, with $padName arriving ${padAbsMs}ms ahead of the grid (${weakestBreakdown.accuracyPercent}% accuracy). Try exhaling on the downbeat and letting your $padName strokes sit slightly behind the metronome click."
            }
            TimingTendency.DRAGGING -> {
                "Your overall groove is dragging by +${abs(meanDriftMs)}ms on average, with $padName landing ${padAbsMs}ms behind the beat (${weakestBreakdown.accuracyPercent}% accuracy). Keep your stick height compact above $padName and subdivisions crisp so your rebound catches the front of the click."
            }
            TimingTendency.LOCKED_IN -> {
                if (pocketScore >= 88) {
                    "Your pocket is locked in at $pocketScore/100 with a tight ${formatSignedMs(meanDriftMs)} mean drift, and even your lowest-scoring pad ($padName) hit ${weakestBreakdown.accuracyPercent}% accuracy. To push your precision even further, practice keeping $padName within ±10ms while raising the tempo by 5 to 10 BPM."
                } else {
                    "Your average drift is balanced at ${formatSignedMs(meanDriftMs)}, but $padName showed the widest spread at ${weakestBreakdown.accuracyPercent}% accuracy (${weakestBreakdown.meanAbsDriftMs.roundToInt()}ms average offset). Focus on subdividing 16th notes evenly before striking $padName so every backbeat lands squarely inside the ±20ms Perfect window."
                }
            }
        }
    }

    private fun formatSignedMs(ms: Int): String = if (ms >= 0) "+${ms}ms" else "${ms}ms"
}
