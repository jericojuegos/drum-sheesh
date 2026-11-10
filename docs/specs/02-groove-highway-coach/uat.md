# 🧪 Feature UAT & Verification: Interactive Groove Highway & Rhythm Pocket Coach (`02-groove-highway-coach`)

> **Feature Slug:** `02-groove-highway-coach` · **Spec Links:** [`requirements.md`](./requirements.md) · [`design.md`](./design.md) · [`tasks.md`](./tasks.md) · [`design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)  
> **Overall Status:** `[x]` Verified · **Verified Date:** `2026-11-10`

---

## Level 1 — Automated Terminal, Unit & Hard-Failure Checks (AI Executes)

- [x] **Build & Type/Syntax Check:** `./gradlew compileDebugKotlin` passes with zero errors across `:core`, `:ui`, `:platform`, `:feature`, and `:app`.
- [x] **Stack Hard-Failure Audit:** All items in `.agents/rules/stack-rules.md` (zero `android.*` imports in `:core`, zero allocations in `Canvas` draw loops, UDF ViewModel state flow) and `AGENTS.md` (`checkMergedManifestPermissions`) pass.
- [x] **Unit / Invariant Tests (Tier 1 — Mandatory):** `./gradlew testDebugUnitTest` passes for `RhythmPocketCoachTest` (`:core`), `CoreArchitectureBoundaryTest` (`:core`), and `GrooveHighwayViewModelTest` (`:feature`).

---

## Level 2 — Functional & EARS Requirement Verification (Deterministic Evidence)

> **Rule (Zero Unbacked `[x]`):** Never mark a row `[x]` without populating the **`Evidence / Proof`** column with a concrete citation (automated test `file:line` or CLI verification output).

| Check ID | Requirement Ref | Scenario (WHEN / IF) | Expected Outcome (THE System SHALL) | Evidence / Proof (`test:line`, CLI output, or `./evidence/*.png`) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **UAT-1.1** | `Req 1.1` | User opens the Groove Library | `BuiltInGrooveCatalog` provides 8 curated genre grooves (*Classic Rock Backbeat*, *92 BPM Neo-Soul Boom Bap*, *140 BPM Trap Hi-Hat Roll*, *Funk Ghost-Note Pocket*, *Pop-Punk Double Time*, *Heavy Metal Double Kick*, *Four-on-the-Floor House*, *Bossa Nova Clave*) | `RhythmPocketCoachTest.kt:L11-L40` & `GrooveHighwayViewModelTest.kt:L16-L27` PASS | `[x]` |
| **UAT-1.2** | `Req 1.2` | Groove is playing with custom BPM (`40..240`) and Swing (`0..75%`) | `GrooveTimelineCalculator` computes exact millisecond target timestamps applying swing offset to odd 16th-note offbeats | `RhythmPocketCoachTest.kt:L43-L79` & `GrooveHighwayViewModelTest.kt:L29-L36` PASS | `[x]` |
| **UAT-1.3** | `Req 1.3` | Groove Highway is active and user hits pads | `GrooveHighwayRenderer` renders scrolling 6-lane notes toward the `#FBBF24` Now-Line and flashes live `HitJudgement` pill (`PERFECT`, `GOOD`, `EARLY`, `LATE`, `MISS`) with zero per-frame allocations | `GrooveHighwayRenderer.kt:L78-L216` (pre-allocated `AndroidPaint`, `Stroke`, `CornerRadius` outside `Canvas`) + `RhythmPocketCoachTest.kt:L161-L174` PASS | `[x]` |
| **UAT-2.1** | `Req 2.1` | User completes a practice pass or taps **Finish & Coach** | `RhythmPocketCoach` matches hits within `±120ms` and computes `pocketScore` (`0..100`), signed `meanDriftMs`, and `tendency` (`RUSHING` `< -8ms`, `DRAGGING` `> +8ms`, `LOCKED_IN` `-8..+8ms`) | `RhythmPocketCoachTest.kt:L82-L158` & `GrooveHighwayViewModelTest.kt:L39-L80` PASS | `[x]` |
| **UAT-2.2** | `Req 2.2` | `PocketCoachReport` is generated | Identifies the weakest pad and produces a deterministic 2-sentence plain-English coaching recommendation | `RhythmPocketCoachTest.kt:L115-L120` & `GrooveHighwayViewModelTest.kt:L72-L75` PASS | `[x]` |

---

## Level 3 — Visual Fidelity (`design-reference/`), Evidence Pack & Human UX Sign-Off

- [x] **Visual Reference Match:** Built UI matches `docs/specs/02-groove-highway-coach/design-reference/stitch-prompt.md` and `docs/foundation/design-system.md` tokens (`#0F0F1A` stage, `#232338` elevated modal, `#FBBF24` Now-Line, `#818CF8` AI Coach border, semantic timing pill colors).
- [x] **Zero Per-Frame Canvas Allocations:** All `Canvas` drawing primitives in `GrooveHighwayRenderer.kt` use pre-allocated/remembered colors, strokes, and native text paints outside `onDraw`.
- [x] **Sign-Off:** Approved to mark `[x]` in `docs/planning/roadmap.md` via `/ship`.

---

## 🧭 Manual Testing Walkthrough (Step-by-Step Human QA)

### 1. Prerequisites & Local Setup
- **Build & Install on Device/Emulator:** `./gradlew installDebug` (run on any API 26+ Android phone or tablet in landscape orientation).
- **Entry Component:** Open the Groove Highway panel (`GrooveHighwayOverlayPanel` / `GrooveHighwayRenderer`) above the 10-pad stage.

### 2. Happy-Path Walkthrough (`Req 1.1–2.2`)
1. **Select a Built-In Genre Groove (`Req 1.1`):** Tap the `GROOVE: CLASSIC ROCK BACKBEAT ▾` selector pill and pick *92 BPM Neo-Soul Boom Bap* (or any of the 8 built-in grooves).
   - **Expected Result:** Groove title, default BPM (`92 BPM`), and Swing (`SWING 18%`) load into the highway transport bar.
2. **Adjust BPM & Swing (`Req 1.2`):** Adjust BPM (`−`/`+` between `40..240 BPM`) and Swing (`0..75%`), then tap **PLAY GROOVE**.
   - **Expected Result:** Incoming note pills scroll smoothly across the 6 highway lanes (`KICK`, `SNARE`, `CLOSED HH`, `OPEN HH`, `TOMS`, `CYMBALS`) toward the bright `#FBBF24` vertical Now-Line.
3. **Live Hit Judgement Pill (`Req 1.3`):** Tap pads along with the scrolling notes crossing the Now-Line.
   - **Expected Result:** The live timing pill flashes `PERFECT -4ms` (`#A6E3A1`), `GOOD +28ms` (`#89B4FA`), `EARLY -58ms` / `LATE +64ms` (`#F9E2AF`), or `MISS` (`#F38BA8`).
4. **Rhythm Pocket Coach Modal (`Req 2.1, 2.2`):** Tap **FINISH & COACH** to open `PocketCoachReportDialog`.
   - **Expected Result:** Displays Pocket Score (`0..100`), Tendency badge (`LOCKED IN`, `RUSHING`, or `DRAGGING`), signed mean drift (`ms`), Perfect/Good/Early-Late/Miss breakdown bar, the 2-sentence Coach Tip card (`#818CF8` border) naming the weakest pad, and **Retry Groove** / **Slow Tempo -10 BPM** buttons.

### 3. Edge Cases & Error Validation
1. **Finish With Zero Hits:** Start a groove and immediately tap **FINISH & COACH** without hitting any pads → **Expected:** Score displays `0 / 100` with `0ms` drift and starter coaching guidance (no crash or NaN).
2. **Slow Tempo Retry At Minimum BPM (`40 BPM`):** Set BPM to `45 BPM`, open the Pocket Coach modal, and tap **Slow Tempo -10 BPM** → **Expected:** BPM clamps cleanly to `40 BPM` and restarts the groove.
