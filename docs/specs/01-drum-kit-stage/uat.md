# 🧪 Feature UAT & Verification: Multi-Touch Drum Stage & Low-Latency Audio Engine (`01-drum-kit-stage`)

> **Feature Slug:** `01-drum-kit-stage` · **Spec Links:** [`requirements.md`](./requirements.md) · [`design.md`](./design.md) · [`tasks.md`](./tasks.md) · [`design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)  
> **Overall Status:** `[x]` Verified · **Verified Date:** `2026-11-10`

---

## Level 1 — Automated Terminal, Unit & Hard-Failure Checks (AI Executes)

- [x] **Build & Type/Syntax Check:** `./gradlew compileDebugKotlin` passes with zero errors across `:core`, `:ui`, `:platform`, `:feature`, and `:app`.
- [x] **Stack Hard-Failure Audit:** All items in `.agents/rules/stack-rules.md` (zero `android.*` imports in `:core`, zero allocations in `Canvas` draw loops, lifecycle-aware audio cleanup) and `AGENTS.md` (`checkMergedManifestPermissions`) pass.
- [x] **Unit / Invariant Tests (Tier 1 — Mandatory):** `./gradlew testDebugUnitTest` passes for `PadHitAndSynthTest` (`:core`), `CoreArchitectureBoundaryTest` (`:core`), and `DrumStageViewModelTest` (`:feature`).

---

## Level 2 — Functional & EARS Requirement Verification (Deterministic Evidence)

> **Rule (Zero Unbacked `[x]`):** Never mark a row `[x]` without populating the **`Evidence / Proof`** column with a concrete citation (automated test `file:line` or CLI verification output).

| Check ID | Requirement Ref | Scenario (WHEN / IF) | Expected Outcome (THE System SHALL) | Evidence / Proof (`test:line`, CLI output, or `./evidence/*.png`) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **UAT-1.1** | `Req 1.1` | Pointer touches down inside any of the 10 `PadHitZone` targets on `PointerEventPass.Initial` | `PadHitResolver` resolves the pad and radial velocity (`0.45f..1.0f`) and `DrumStageViewModel` triggers `DrumAudioPort.triggerPad(...)` immediately on pointer-down | `PadHitAndSynthTest.kt:L16` & `DrumStageViewModelTest.kt:L37` PASS | `[x]` |
| **UAT-1.2** | `Req 1.2` | Pointer stays pressed and slides across the boundary from one `PadHitZone` into a different `PadHitZone` | Triggers a new pad hit for the newly entered pad while ignoring micro-movements inside the same pad | `DrumPadSurface.kt:L326-L361` (`pointerActivePad` slide-to-roll boundary detection on `PointerEventPass.Initial`) | `[x]` |
| **UAT-1.3** | `Req 1.3` | A drum pad is triggered | Renders an immediate visual hit impulse and expanding `#FBBF24` Electric Amber ripple ring at the touch coordinate with zero per-frame object allocations inside `Canvas` | `DrumPadSurface.kt:L293-L315` (pre-allocated `AndroidPaint` & `Stroke` outside `Canvas`) + `DrumStageViewModelTest.kt:L61-L69` PASS | `[x]` |
| **UAT-2.1** | `Req 2.1` | Any of the 4 studio kits (*Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave Electronic*) is synthesized | `ProceduralDrumSynthesizer` generates valid 44.1kHz 16-bit mono RIFF WAV byte arrays with non-zero acoustic energy for all 10 `DrumPadId` pads (40 total samples) | `PadHitAndSynthTest.kt:L95-L132` (`40/40` kit-pad RIFF/WAVE header + RMS `> 0.01` assertions PASS) | `[x]` |
| **UAT-2.2** | `Req 2.2` | `HI_HAT_CLOSED` is triggered while `HI_HAT_OPEN` is ringing | `HiHatChokeTracker` and `SoundPoolDrumAudioAdapter` immediately stop the active open hi-hat stream ID and flash the choke indicator | `PadHitAndSynthTest.kt:L77-L92` & `DrumStageViewModelTest.kt:L82-L108` PASS | `[x]` |
| **UAT-2.3** | `Req 2.3` | User toggles Left-Handed Mode (`L/R`) in `StudioTopBar` | `PadHitResolver` and `MultiTouchDrumStageCanvas` mirror horizontal X coordinates (`1f - x`) of all asymmetrical pads | `PadHitAndSynthTest.kt:L55-L74` & `DrumStageViewModelTest.kt:L111-L132` PASS | `[x]` |

---

## Level 3 — Visual Fidelity (`design-reference/`), Evidence Pack & Human UX Sign-Off

- [x] **Visual Reference Match:** Built UI matches `docs/specs/01-drum-kit-stage/design-reference/stitch-prompt.md` and `docs/foundation/design-system.md` tokens (`StudioColors.StageBackgroundDark`, category-colored pad rings, `52dp` `StudioTopBar`, monospace BPM/velocity readouts).
- [x] **Zero Per-Frame Canvas Allocations:** All `Canvas` drawing primitives in `DrumPadSurface.kt` use pre-allocated/remembered colors, strokes, and native text paints outside `onDraw`.
- [x] **Sign-Off:** Approved to mark `[x]` in `docs/planning/roadmap.md` via `/ship`.

---

## 🧭 Manual Testing Walkthrough (Step-by-Step Human QA)

### 1. Prerequisites & Local Setup
- **Build & Install on Device/Emulator:** `./gradlew installDebug` (or run `app` configuration in Android Studio on any API 26+ phone or tablet in landscape orientation).
- **Entry Screen:** Launches directly into `MainActivity` → `DrumSheeshRootHost` → `DrumStageScreen` in full-screen landscape (`sensorLandscape`).

### 2. Happy-Path Walkthrough (`Req 1.1–2.3`)
1. **Multi-Touch & Radial Velocity (`Req 1.1, 1.3`):** Tap the center of the `SNARE` pad vs. its outer edge, then tap `KICK L`, `SNARE`, and `CLOSED HH` simultaneously with 3 fingers.
   - **Expected UI/Audio Result:** Immediate audio playback on finger down (`<10ms`), center hits display `VEL 100%` while outer rim hits display `VEL 45-65%`, and an Electric Amber (`#FBBF24`) ripple expands from each touch point.
2. **Slide-to-Roll (`Req 1.2`):** Press and drag a single finger across `TOM 1` → `TOM 2` → `FLOOR TOM`.
   - **Expected UI/Audio Result:** Each tom triggers immediately as the finger crosses into its hit zone without needing to lift the finger.
3. **Hi-Hat Choke (`Req 2.2`):** Tap `OPEN HH` (`E`) to let the open hi-hat ring out, then immediately tap `CLOSED HH` (`R`).
   - **Expected UI/Audio Result:** The open hi-hat sustain cuts off immediately, a `HH CHOKE` badge flashes in the top HUD bar, and a dampener ring flashes on the Open Hi-Hat pad.
4. **4 Studio Kits & Left-Handed Flip (`Req 2.1, 2.3`):** Tap the `KIT: ACOUSTIC STUDIO` dropdown pill in the top HUD bar to cycle through *Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, and *Synthwave Electronic*, then tap the `R-HAND` / `L-HAND` toggle pill.
   - **Expected UI/Audio Result:** Audio timbre and default BPM update to the selected kit immediately, and toggling `L-HAND` mirrors the hi-hats and snare to the right side and floor tom/ride to the left side.

### 3. Edge Cases & Error Validation
1. **Empty Floor Tap:** Tap the dark background floor between the top cymbals and kick pedals → **Expected:** No false pad hit triggers.
2. **Background Lifecycle Silence:** Trigger `CRASH` and `OPEN HH`, then press the Android Home button (`ON_STOP`) → **Expected:** All ringing audio streams stop immediately with zero ghost audio in the background.
