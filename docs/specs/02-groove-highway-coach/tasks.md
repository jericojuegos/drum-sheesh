# ✅ Feature Tasks: Interactive Groove Highway & Rhythm Pocket Coach (`02-groove-highway-coach`)

> **Phase / Wave:** `Phase 1 (Wave 2)` · **Branch / Owner:** `feat/02-groove-highway-coach` · **Status:** `[x]` Complete  
> **Depends On:** `01-drum-kit-stage` · **Touches:** `core/src/main/kotlin/com/drumsheesh/core/coach/, ui/src/main/kotlin/com/drumsheesh/ui/components/GrooveHighwayRenderer.kt, feature/src/main/kotlin/com/drumsheesh/feature/highway/`  
> **Spec Links:** [`requirements.md`](./requirements.md) · [`design.md`](./design.md) · [`uat.md`](./uat.md) · [`design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)

---

## 🔄 Implementation Tasks

### 1. Built-In Groove Catalog, Swing Timeline Math & `RhythmPocketCoach` Engine (`⚡ BATCH`: `:core` Logic & Unit Tests)
- [x] **1.1** `[BE]` Implement `BuiltInGrooveCatalog` (8 genre grooves) and `GrooveTimelineCalculator` (BPM `40..240`, swing `0..75%` 16th-note offset math) in `core/src/main/kotlin/com/drumsheesh/core/coach/GrooveTimelineCalculator.kt`
  - _Requirements: 1.1, 1.2_
- [x] **1.2** `[BE]` Implement `RhythmPocketCoach` in `core/src/main/kotlin/com/drumsheesh/core/coach/RhythmPocketCoach.kt` computing per-hit `HitJudgement` (`PERFECT`/`GOOD`/`EARLY`/`LATE`/`MISS`), `pocketScore` (`0..100`), signed `meanDriftMs`, `RUSHING`/`DRAGGING`/`LOCKED_IN` tendency, and weakest-pad coaching tip
  - _Requirements: 2.1, 2.2_
- [x] **1.3** `[BE]` Write & run unit/invariant tests in `core/src/test/kotlin/com/drumsheesh/core/coach/RhythmPocketCoachTest.kt` verifying exact millisecond drift scoring, swing timestamp calculation, rushing vs. dragging detection, and score bounds `0..100`
  - _Validates: Requirements 1.1, 1.2, 2.1, 2.2_
- [x] **1.4** `[REVIEW]` **Core Coach Checkpoint:** Run `./gradlew :core:test` — must pass with zero errors

### 2. 60/120fps Groove Highway Canvas & Pocket Coach Report Dialog (`⚡ BATCH`: `:ui` & `:feature`)
- [x] **2.1** `[FE]` Build `GrooveHighwayRenderer` in `ui/src/main/kotlin/com/drumsheesh/ui/components/GrooveHighwayRenderer.kt` rendering scrolling pad lanes, Now-Line hit flash, and real-time `HitJudgement` pill with zero per-frame allocations
  - _Requirements: 1.3_
- [x] **2.2** `[FE]` Implement `GrooveHighwayViewModel`, `PocketCoachReportDialog`, and stage integration in `feature/src/main/kotlin/com/drumsheesh/feature/highway/GrooveHighwayViewModel.kt` + unit tests
  - _Requirements: 1.1, 1.3, 2.1, 2.2_
- [x] **2.3** `[REVIEW]` **Final Feature Checkpoint:** Run `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` — must pass with zero errors

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
| 2026-11-10 | `/spec` | Generated `design.md` and `uat.md` for `02-groove-highway-coach`. |
| 2026-11-10 | `1.1–1.4` | Implemented `BuiltInGrooveCatalog`, `GrooveTimelineCalculator`, `RhythmPocketCoach`, and `RhythmPocketCoachTest` in `core/coach/`. Verified `:core:test` PASS. |
| 2026-11-10 | `2.1–2.3` | Implemented zero-allocation 6-lane `GrooveHighwayRenderer` in `ui/components/GrooveHighwayRenderer.kt` and `GrooveHighwayViewModel`, `GrooveHighwayOverlayPanel`, `PocketCoachReportDialog`, and `GrooveHighwayViewModelTest` in `feature/highway/`. Verified compile, unit tests, and manifest guard PASS. |
