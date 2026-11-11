# 🧪 Feature UAT & Verification: AI Text-to-Groove Generator & Session Loop Recorder (`03-ai-groove-recorder`)

> **Feature Slug:** `03-ai-groove-recorder` · **Spec Links:** [`requirements.md`](./requirements.md) · [`design.md`](./design.md) · [`tasks.md`](./tasks.md) · [`design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)  
> **Overall Status:** `[x]` Verified · **Verified Date:** `2026-11-10`

---

## Level 1 — Automated Terminal, Unit & Hard-Failure Checks (AI Executes)

- [x] **Build & Type/Syntax Check:** `./gradlew compileDebugKotlin` passes with zero errors across `:core`, `:ui`, `:platform`, `:feature`, and `:app`.
- [x] **Stack Hard-Failure Audit:** All items in `.agents/rules/stack-rules.md` (zero `android.*` imports in `:core` verified by `CoreArchitectureBoundaryTest`, zero allocations in `Canvas` draw loops, zero `RECORD_AUDIO` permission) and `AGENTS.md` (`checkMergedManifestPermissions`) pass.
- [x] **Unit / Invariant Tests (Tier 1 — Mandatory):** `./gradlew testDebugUnitTest` passes for `AiGrooveAndQuantizerTest` (`:core` — 6/6 PASS) and `AiGrooveRecorderViewModelTest` (`:feature` — 3/3 PASS).

---

## Level 2 — Functional & EARS Requirement Verification (Deterministic Evidence)

> **Rule (Zero Unbacked `[x]`):** Never mark a row `[x]` without populating the **`Evidence / Proof`** column with a concrete citation (automated test `file:line` or CLI verification output).

| Check ID | Requirement Ref | Scenario (WHEN / IF) | Expected Outcome (THE System SHALL) | Evidence / Proof (`test:line`, CLI output, or `./evidence/*.png`) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **UAT-1.1** | `Req 1.1` | User submits a non-empty prompt (`3..200` chars) in the AI Groove modal | `AiGrooveGeneratorPort` returns a validated `GroovePattern` with clamped `bpm` (`40..240`), `swingPercent` (`0..75`), and valid `DrumPadId` steps within `[0..stepsPerBar * bars - 1]` | `AiGrooveAndQuantizerTest.kt:L35-L96` & `AiGrooveRecorderViewModelTest.kt:L61-L86` PASS | `[x]` |
| **UAT-1.2** | `Req 1.2` | Gemini API key is configured vs. offline/unconfigured | `GeminiAiGrooveAdapter` validates Gemini JSON via `GrooveJsonCodec` when online, and seamlessly falls back to `ProceduralGrooveGenerator` when offline or unconfigured | `GeminiAiGrooveAdapter.kt:L40-L68` & `AiGrooveAndQuantizerTest.kt:L14-L33` PASS | `[x]` |
| **UAT-1.3** | `Req 1.3` | User submits a blank or `< 3` character prompt | `AiGrooveRecorderViewModel` rejects the submission and displays an inline validation message without calling the generator | `AiGrooveAndQuantizerTest.kt:L99-L106` & `AiGrooveRecorderViewModelTest.kt:L35-L58` (`generatorCallCount == 0` verified) PASS | `[x]` |
| **UAT-2.1** | `Req 2.1` | Recording mode (`REC`) is active on the Drum Stage | `SessionRecorderEngine` captures every triggered `DrumPadId` with its relative `offsetMs` from recording start and `velocity` | `AiGrooveAndQuantizerTest.kt:L109-L132` & `AiGrooveRecorderViewModelTest.kt:L89-L125` PASS | `[x]` |
| **UAT-2.2** | `Req 2.2` | User toggles **Quantize 1/16** on a recorded take | `LoopQuantizer` snaps each hit's `offsetMs` to the nearest 16th-note grid interval for the current BPM and swing while preserving the original unquantized take for non-destructive undo | `AiGrooveAndQuantizerTest.kt:L135-L162` & `AiGrooveRecorderViewModelTest.kt:L128-L139` PASS | `[x]` |
| **UAT-2.3** | `Req 2.3` | User saves a generated groove or recorded loop | `FileGrooveRepositoryAdapter` persists the JSON file in app-scoped storage (`filesDir/grooves/` and `filesDir/loops/`) and reloads saved items across app restarts | `FileGrooveRepositoryAdapter.kt:L31-L74`, `AiGrooveAndQuantizerTest.kt:L146-L152` & `AiGrooveRecorderViewModelTest.kt:L142-L146` PASS | `[x]` |

---

## Level 3 — Visual Fidelity (`design-reference/`), Evidence Pack & Human UX Sign-Off

- [x] **Visual Reference Match:** Built UI in `AiGrooveRecorderViewModel.kt` (`AiGroovePromptModal` & `SessionLoopDrawer`) matches `docs/specs/03-ai-groove-recorder/design-reference/stitch-prompt.md` and `docs/foundation/design-system.md` tokens (`StudioColors.AiCoachIndigo` `#818CF8`, `StudioColors.ElectricAmber` `#FBBF24`, `StudioColors.MissOrRecRed` `#F38BA8`, `StudioColors.SurfaceElevatedDark` `#232338`).
- [x] **Double-Submit & Empty State:** `Generate Groove` button disables while `isGeneratingGroove` is true; `Play Loop`, `1/16 Quantize`, and `Save Loop` controls disable cleanly when no loop is recorded or while actively recording.
- [x] **Sign-Off:** Approved to mark `[x]` in `docs/planning/roadmap.md` via `/ship`.

---

## 🧭 Manual Testing Walkthrough (Step-by-Step Human QA)

### 1. Prerequisites & Local Setup
- **Build & Install on Device/Emulator:** `./gradlew installDebug`
- **Entry Screen:** Open the **AI Groove** modal (`AiGroovePromptModal`) or **Session Loop Recorder** drawer (`SessionLoopDrawer`) from the top studio HUD bar.

### 2. Happy-Path Walkthrough (`Req 1.1–2.3`)
1. **AI Text-to-Groove Generation (`Req 1.1, 1.2`):** Open the AI Text-to-Groove Modal, tap a Quick Vibe Chip (`"90s Boom Bap"`, `"140 BPM Trap Roll"`, `"165 BPM Amen Break"`, or `"Double-Bass Metal"`) or type `"94 BPM J Dilla neo-soul boom bap with swung hi-hats and ghost snares"`, and tap **✦ Generate Groove**.
   - **Expected UI Result:** The 16-step mini grid preview renders the generated `KICK`, `SNARE`, `CL HH`, and `OP HH` steps with clamped BPM/swing badges and source pill (`Smart Studio Engine · Ready` or `Cloud AI · Gemini`). Tapping **Load into Groove Highway** loads the pattern into the active groove session.
2. **Session Recording & 1/16 Quantize (`Req 2.1, 2.2`):** Tap **REC** in the Session Loop Recorder transport, play several drum pads on the stage, then tap **REC** again to stop. Toggle **1/16 Quantize** on and off.
   - **Expected UI Result:** Recorded hits display with count and take title; toggling **1/16 Quantize** ON snaps hit timestamps to the 16th-note grid (`Snapped to 1/16 Grid (Non-Destructive)`), and toggling it OFF restores the exact original unquantized timing (`Original Unquantized Timing`).
3. **Save & Reload Persistence (`Req 2.3`):** Tap **Save Groove** in the AI modal or **Save Loop** in the Session Loop Drawer.
   - **Expected Result:** Saved grooves and loops increment the saved counter and persist as JSON files in `filesDir/grooves/` and `filesDir/loops/` across app restarts.

### 3. Edge Cases & Error Validation (`Req 1.3`)
1. **Short Prompt Rejection (`Req 1.3`):** Type `"hi"` (`< 3` chars) or leave the prompt blank and tap **✦ Generate Groove** → **Expected:** Inline validation error (`"Please enter at least 3 characters describing your drum groove."`) appears in `#F38BA8` red and the generator is not invoked.
2. **Offline / No API Key Resilience (`Req 1.2`):** Generate a groove with Airplane Mode enabled or without a Gemini API key → **Expected:** `GeminiAiGrooveAdapter` falls back immediately to `ProceduralGrooveGenerator` (`Smart Studio Engine · Ready`) with zero error dialogs.
