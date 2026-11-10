# ✅ Feature Tasks: AI Text-to-Groove Generator & Session Loop Recorder (`03-ai-groove-recorder`)

> **Phase / Wave:** `Phase 1 (Wave 2)` · **Branch / Owner:** `feat/03-ai-groove-recorder` · **Status:** `[ ]` Planned  
> **Depends On:** `01-drum-kit-stage` · **Touches:** `core/src/main/kotlin/com/drumsheesh/core/ai/, platform/src/main/kotlin/com/drumsheesh/platform/{ai,storage}/, feature/src/main/kotlin/com/drumsheesh/feature/recorder/`  
> **Spec Links:** [`requirements.md`](./requirements.md) · [`design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)

---

## 🔄 Implementation Tasks

### 1. Pure-Kotlin AI Groove Codec, Offline Procedural Generator & Loop Quantizer (`⚡ BATCH`: `:core` Logic & Unit Tests)
- [ ] **1.1** `[BE]` Implement `GrooveJsonCodec`, `AiGroovePromptBuilder`, and `ProceduralGrooveGenerator` (extracting BPM, swing, and genre patterns from natural-language prompts) in `core/src/main/kotlin/com/drumsheesh/core/ai/ProceduralGrooveGenerator.kt`
  - _Requirements: 1.1, 1.2_
- [ ] **1.2** `[BE]` Implement `SessionRecorderEngine` and non-destructive `LoopQuantizer` (`1/16` grid snapping with swing support) in `core/src/main/kotlin/com/drumsheesh/core/engine/LoopQuantizer.kt`
  - _Requirements: 2.1, 2.2_
- [ ] **1.3** `[BE]` Write & run unit/invariant tests in `core/src/test/kotlin/com/drumsheesh/core/ai/AiGrooveAndQuantizerTest.kt` verifying JSON round-trip serialization, out-of-range BPM/step clamping, prompt-to-genre parsing, and non-destructive quantization invariants
  - _Validates: Requirements 1.1, 1.2, 2.1, 2.2_
- [ ] **1.4** `[REVIEW]` **Core AI & Recorder Checkpoint:** Run `./gradlew :core:test` — must pass with zero errors

### 2. Platform Adapters (`GeminiAiGrooveAdapter`, `FileGrooveRepositoryAdapter`) & Recorder UI (`⚡ BATCH`: `:platform` & `:feature`)
- [ ] **2.1** `[BE]` Implement `GeminiAiGrooveAdapter` (HTTPS Gemini JSON endpoint with automatic `ProceduralGrooveGenerator` fallback) and `FileGrooveRepositoryAdapter` (app-scoped JSON persistence) in `platform/src/main/kotlin/com/drumsheesh/platform/ai/GeminiAiGrooveAdapter.kt`
  - _Requirements: 1.2, 2.3_
- [ ] **2.2** `[FE]` Build `AiGrooveRecorderViewModel`, `AiGroovePromptModal`, and `SessionLoopDrawer` in `feature/src/main/kotlin/com/drumsheesh/feature/recorder/AiGrooveRecorderViewModel.kt` + ViewModel unit tests
  - _Requirements: 1.1, 1.3, 2.1, 2.2, 2.3_
- [ ] **2.3** `[REVIEW]` **Final Feature Checkpoint:** Run `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` — must pass with zero errors

---

## 🔀 Task Dependency Graph (Intra-Feature Waves)

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4"] },
    { "id": 2, "tasks": ["2.1", "2.2"] },
    { "id": 3, "tasks": ["2.3"] }
  ]
}
```

---

## 🧪 Feature Verification & UAT Gate
- [ ] **Level 1 (Automated Build & Unit Tests):** `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` passes with zero errors
- [ ] **Level 2 & 3 (UAT & Visual Match):** `/verify` completed in `uat.md`

---

## 🛑 Blocked / Waiting
*(None)*

---

## 📝 Feature Activity Log

| Date | Task | Action & Files Touched |
| :--- | :--- | :--- |
| 2026-11-10 | Init | Feature spec bundle created during `/brainstorm`. |
