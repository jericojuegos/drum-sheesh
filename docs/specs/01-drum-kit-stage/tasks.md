# ✅ Feature Tasks: Multi-Touch Drum Stage & Low-Latency Audio Engine (`01-drum-kit-stage`)

> **Phase / Wave:** `Phase 1 (Wave 1)` · **Branch / Owner:** `feat/01-drum-kit-stage` · **Status:** `[x]` Complete  
> **Depends On:** `00-scaffold` · **Touches:** `core/src/main/kotlin/com/drumsheesh/core/{model,audio,engine}/, platform/src/main/kotlin/com/drumsheesh/platform/audio/, ui/src/main/kotlin/com/drumsheesh/ui/components/DrumPadSurface.kt, feature/src/main/kotlin/com/drumsheesh/feature/stage/`  
> **Spec Links:** [`requirements.md`](./requirements.md) · [`design.md`](./design.md) · [`uat.md`](./uat.md) · [`design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)

---

## 🔄 Implementation Tasks

### 1. Pure-Kotlin Kit Geometry, Hi-Hat Choke & 16-Bit PCM Synthesizer (`⚡ BATCH`: `:core` Models, Synth & Unit Tests)
- [x] **1.1** `[BE]` Implement 10-pad spatial hit geometry (`PadHitZone`, `PadHitResolver` with radial velocity `0.45f..1.0f` and Left/Right-handed horizontal mirroring) and `HiHatChokeTracker` in `core/src/main/kotlin/com/drumsheesh/core/engine/PadHitResolver.kt`
  - _Requirements: 1.1, 1.2, 2.2, 2.3_
- [x] **1.2** `[BE]` Implement `ProceduralDrumSynthesizer` in `core/src/main/kotlin/com/drumsheesh/core/audio/ProceduralDrumSynthesizer.kt` generating 44.1kHz 16-bit mono RIFF WAV byte arrays for all 10 pads across all 4 studio kits (*Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave Electronic*)
  - _Requirements: 2.1_
- [x] **1.3** `[BE]` Write & run unit/invariant tests in `core/src/test/kotlin/com/drumsheesh/core/engine/PadHitAndSynthTest.kt` verifying RIFF WAV header validity, non-zero RMS amplitude for all 40 kit-pad combinations, radial velocity bounds, left-handed mirroring, and hi-hat choke stream tracking
  - _Validates: Requirements 1.1, 2.1, 2.2, 2.3_
- [x] **1.4** `[REVIEW]` **Core Domain Checkpoint:** Run `./gradlew :core:test` — must pass with zero errors and zero `android.*` imports

### 2. Android `SoundPool` Low-Latency Adapter & Multi-Touch Drum Stage UI (`⚡ BATCH`: `:platform`, `:ui`, `:feature`)
- [x] **2.1** `[BE]` Implement `SoundPoolDrumAudioAdapter` and `AndroidHapticFeedbackAdapter` in `platform/src/main/kotlin/com/drumsheesh/platform/audio/SoundPoolDrumAudioAdapter.kt` with lifecycle-aware `onStop` silence and open-hi-hat stream choking
  - _Requirements: 1.1, 2.1, 2.2_
- [x] **2.2** `[FE]` Build `MultiTouchDrumStageCanvas` and `StudioTopBar` in `ui/src/main/kotlin/com/drumsheesh/ui/components/DrumPadSurface.kt` using `awaitEachGesture` + `PointerEventPass.Initial` multi-pointer tracking, slide-to-roll detection, and pre-allocated Canvas brushes/paints
  - _Requirements: 1.1, 1.2, 1.3, 2.3_
- [x] **2.3** `[FE]` Implement `DrumStageViewModel` (UDF `StateFlow<DrumStageUiState>`) and `DrumStageScreen` in `feature/src/main/kotlin/com/drumsheesh/feature/stage/DrumStageScreen.kt` + unit tests in `feature/src/test/kotlin/com/drumsheesh/feature/stage/DrumStageViewModelTest.kt`
  - _Requirements: 1.1, 2.2, 2.3_
- [x] **2.4** `[REVIEW]` **Final Feature Checkpoint:** Run `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` — must pass with zero errors

---

## 🔀 Task Dependency Graph (Intra-Feature Waves)

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4"] },
    { "id": 2, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4"] }
  ]
}
```

---

## 🧪 Feature Verification & UAT Gate
- [x] **Level 1 (Automated Build & Unit Tests):** `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` passes with zero errors
- [x] **Level 2 & 3 (UAT & Visual Match):** `/verify` completed in `uat.md`

---

## 🛑 Blocked / Waiting
*(None)*

---

## 📝 Feature Activity Log

| Date | Task | Action & Files Touched |
| :--- | :--- | :--- |
| 2026-11-10 | Init | Feature spec bundle created during `/brainstorm`. |
| 2026-11-10 | `/spec` | Generated `design.md` and `uat.md` for `01-drum-kit-stage`. |
| 2026-11-10 | `1.1–1.4` | Implemented `PadHitResolver`, `HiHatChokeTracker`, `ProceduralDrumSynthesizer`, and `PadHitAndSynthTest`; passed `./gradlew :core:test`. |
| 2026-11-10 | `2.1–2.4` | Implemented `SoundPoolDrumAudioAdapter`, `MultiTouchDrumStageCanvas`, `StudioTopBar`, `DrumStageViewModel`, `DrumStageScreen`, and `DrumStageViewModelTest`; passed `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions`. |
