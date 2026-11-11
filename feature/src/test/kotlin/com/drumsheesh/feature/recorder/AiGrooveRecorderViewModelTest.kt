package com.drumsheesh.feature.recorder

import com.drumsheesh.core.ai.AiGrooveGeneratorPort
import com.drumsheesh.core.ai.AiGrooveRequest
import com.drumsheesh.core.ai.GroovePattern
import com.drumsheesh.core.ai.GrooveSource
import com.drumsheesh.core.ai.ProceduralGrooveGenerator
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.ClockPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiGrooveRecorderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitting blank or short prompt under 3 chars rejects with inline error without calling generator`() {
        var generatorCallCount = 0
        val countingGenerator = AiGrooveGeneratorPort { request ->
            generatorCallCount++
            ProceduralGrooveGenerator.generate(request)
        }

        val viewModel = AiGrooveRecorderViewModel(
            aiGrooveGeneratorPort = countingGenerator
        )

        // Blank prompt
        viewModel.onEvent(AiGrooveRecorderUiEvent.UpdatePromptText("  "))
        viewModel.onEvent(AiGrooveRecorderUiEvent.SubmitAiPrompt)

        assertNotNull(viewModel.uiState.value.promptValidationError)
        assertEquals(0, generatorCallCount)

        // 2-character prompt
        viewModel.onEvent(AiGrooveRecorderUiEvent.UpdatePromptText("hi"))
        viewModel.onEvent(AiGrooveRecorderUiEvent.SubmitAiPrompt)

        assertNotNull(viewModel.uiState.value.promptValidationError)
        assertEquals(0, generatorCallCount)
    }

    @Test
    fun `submitting valid prompt generates GroovePattern, updates preview, and loads into highway`() {
        var loadedHighwayGroove: GroovePattern? = null
        val viewModel = AiGrooveRecorderViewModel(
            aiGrooveGeneratorPort = ProceduralGrooveGenerator,
            onGrooveLoadedToHighway = { loadedHighwayGroove = it }
        )

        viewModel.onEvent(AiGrooveRecorderUiEvent.SetAiModalOpen(true))
        viewModel.onEvent(AiGrooveRecorderUiEvent.UpdatePromptText("165 BPM drum and bass amen break"))
        viewModel.onEvent(AiGrooveRecorderUiEvent.SubmitAiPrompt)

        val stateAfterGenerate = viewModel.uiState.value
        assertNull(stateAfterGenerate.promptValidationError)
        assertFalse(stateAfterGenerate.isGeneratingGroove)
        val preview = stateAfterGenerate.generatedGroovePreview
        assertNotNull(preview)
        assertEquals(165, preview!!.bpm)
        assertTrue(preview.steps.isNotEmpty())
        assertEquals(GrooveSource.SMART_STUDIO_ENGINE.displayBadge, stateAfterGenerate.statusBadgeText)

        // Load preview into Groove Highway
        viewModel.onEvent(AiGrooveRecorderUiEvent.LoadPreviewIntoHighway)
        assertFalse(viewModel.uiState.value.isAiModalOpen)
        assertEquals(preview, viewModel.uiState.value.activeLoadedGroove)
        assertEquals(preview, loadedHighwayGroove)
    }

    @Test
    fun `recording session hits, toggling 1-16 quantization non-destructively, and saving to repository`() {
        var nowMs = 5_000L
        val repository = InMemoryGrooveRepositoryAdapter()
        val viewModel = AiGrooveRecorderViewModel(
            grooveRepositoryPort = repository,
            clockPort = ClockPort { nowMs }
        )

        // Configure 120 BPM (1/16 note = 125ms) and 0% swing
        viewModel.onEvent(
            AiGrooveRecorderUiEvent.SetContextKitAndTempo(
                kitId = DrumKitId.ACOUSTIC_STUDIO,
                bpm = 120,
                swingPercent = 0
            )
        )

        // Start Recording
        viewModel.onEvent(AiGrooveRecorderUiEvent.ToggleRecording)
        assertTrue(viewModel.uiState.value.isRecording)

        // Play 3 hits with slight human timing drift (+14ms, +136ms, +489ms)
        nowMs = 5_014L
        viewModel.onEvent(AiGrooveRecorderUiEvent.RecordStageHit(DrumPadId.KICK_RIGHT, 0.95f))

        nowMs = 5_136L
        viewModel.onEvent(AiGrooveRecorderUiEvent.RecordStageHit(DrumPadId.HI_HAT_CLOSED, 0.75f))

        nowMs = 5_489L
        viewModel.onEvent(AiGrooveRecorderUiEvent.RecordStageHit(DrumPadId.SNARE, 0.90f))

        assertEquals(3, viewModel.uiState.value.recordedHitCountInTake)

        // Stop Recording
        nowMs = 6_000L
        viewModel.onEvent(AiGrooveRecorderUiEvent.ToggleRecording)
        assertFalse(viewModel.uiState.value.isRecording)

        val unquantizedLoop = viewModel.uiState.value.currentRecordedLoop
        assertNotNull(unquantizedLoop)
        assertFalse(unquantizedLoop!!.isQuantized)
        assertEquals(listOf(14L, 136L, 489L), unquantizedLoop.activeHits.map { it.offsetMs })

        // Toggle 1/16 Quantize ON -> snaps to 0ms, 125ms, 500ms
        viewModel.onEvent(AiGrooveRecorderUiEvent.ToggleQuantizeSixteenth)
        val quantizedLoop = viewModel.uiState.value.currentRecordedLoop!!
        assertTrue(quantizedLoop.isQuantized)
        assertEquals(listOf(0L, 125L, 500L), quantizedLoop.activeHits.map { it.offsetMs })
        assertEquals(listOf(14L, 136L, 489L), quantizedLoop.originalHits.map { it.offsetMs })

        // Toggle 1/16 Quantize OFF -> non-destructively restores original timing
        viewModel.onEvent(AiGrooveRecorderUiEvent.ToggleQuantizeSixteenth)
        val restoredLoop = viewModel.uiState.value.currentRecordedLoop!!
        assertFalse(restoredLoop.isQuantized)
        assertEquals(listOf(14L, 136L, 489L), restoredLoop.activeHits.map { it.offsetMs })

        // Save groove and loop to repository (Req 2.3)
        viewModel.onEvent(AiGrooveRecorderUiEvent.SavePreviewGroove)
        viewModel.onEvent(AiGrooveRecorderUiEvent.SaveCurrentLoop)

        assertEquals(1, viewModel.uiState.value.savedGrooves.size)
        assertEquals(1, viewModel.uiState.value.savedLoops.size)
    }
}
