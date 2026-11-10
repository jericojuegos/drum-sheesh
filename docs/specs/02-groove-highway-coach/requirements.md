# 📋 Feature Requirements: Interactive Groove Highway & Rhythm Pocket Coach (`02-groove-highway-coach`)

> **Feature Slug:** `02-groove-highway-coach` · **Phase / Wave:** `Phase 1 (Wave 2)`  
> **Depends On:** `01-drum-kit-stage`  
> **Branch / Owner:** `feat/02-groove-highway-coach` · **Status:** `[ ]` Planned

---

## 1. Introduction & Overview

`02-groove-highway-coach` transforms **Drum Sheesh** from a free-play toy into a smart rhythm trainer. It provides a built-in library of 8+ genre grooves, an adjustable BPM/swing metronome clock, a smooth 60/120fps scrolling **Groove Highway** visual note guide, and the **Rhythm Pocket Coach** in `:core` that evaluates millisecond hit drift (`deltaMs`) to tell the drummer whether they are locked in, rushing, or dragging.

### Assumptions (Flagged for Review)

- **A1 (Timing Judgement Windows):** `PERFECT` is `|deltaMs| <= 20ms`, `GOOD` is `21..45ms`, `EARLY`/`LATE` is `46..90ms`, and `MISS` is `> 90ms` (or unmatched step). **Confirmed.**
- **A2 (Highway Overlay Ergonomics):** The Groove Highway renders as a collapsible split-top lane above the 10-pad stage so the user can watch incoming notes while playing the full drum kit below. **Confirmed.**

### Domain Glossary

- **`GroovePattern`:** Immutable pure-Kotlin step sequence (`bpm`, `swingPercent`, `stepsPerBar`, `bars`, `steps`) shared across presets, AI generation, and Phase 2 Web.
- **`HitJudgement`:** Real-time classification of a user hit against an expected step (`PERFECT`, `GOOD`, `EARLY`, `LATE`, `MISS`) with signed `deltaMs` (`actualTimeMs - expectedTimeMs`).
- **`PocketCoachReport`:** Post-session summary containing `pocketScore` (`0..100`), `meanDriftMs`, `tendency` (`LOCKED_IN`, `RUSHING`, `DRAGGING`), per-pad accuracy, and a 2-sentence actionable drill tip.

---

## 2. Requirements & EARS Acceptance Criteria

### Requirement 1: Built-In Genre Grooves, BPM/Swing Clock & Scrolling Groove Highway
**User Story:** As a learner, I want to pick a genre groove, adjust BPM (`40..240`) and swing (`0..75%`), and watch notes scroll toward a target hit-line, so that I can visually learn new beats in real time.

#### Acceptance Criteria
1. **1.1:** WHEN the user opens the Groove Library, THE `BuiltInGrooveCatalog` in `:core` SHALL provide at least 8 curated genre grooves (*Classic Rock Backbeat*, *92 BPM Neo-Soul Boom Bap*, *140 BPM Trap Hi-Hat Roll*, *Funk Ghost-Note Pocket*, *Pop-Punk Double Time*, *Heavy Metal Double Kick*, *Four-on-the-Floor House*, *Bossa Nova Clave*).
2. **1.2:** WHILE a groove is playing, THE `GrooveTimelineCalculator` in `:core` SHALL compute exact step target timestamps in milliseconds accounting for `bpm` (`40..240`) and `swingPercent` (`0..75%` applied to odd 16th-note offbeats).
3. **1.3:** WHILE the Groove Highway is active, THE `GrooveHighwayRenderer` in `:ui` SHALL render incoming note bars scrolling smoothly toward the Now-Line and flash the immediate `HitJudgement` badge (`PERFECT`, `GOOD`, `EARLY -24ms`, `LATE +31ms`, `MISS`) with zero per-frame allocations.

---

### Requirement 2: Rhythm Pocket Coach Millisecond Drift & Tendency Scoring
**User Story:** As a self-taught drummer, I want an objective breakdown of my timing accuracy and whether I rush or drag specific drums, so that I know exactly how to tighten my groove.

#### Acceptance Criteria
1. **2.1:** WHEN a user plays along to a groove and completes a practice pass (or taps **Finish & Coach**), THE `RhythmPocketCoach` in `:core` SHALL match actual pad hits against expected groove steps within `±120ms` and compute `pocketScore` (`0..100`), signed `meanDriftMs`, and `tendency` (`RUSHING` if `meanDriftMs < -8ms`, `DRAGGING` if `meanDriftMs > +8ms`, otherwise `LOCKED_IN`).
2. **2.2:** WHEN the `PocketCoachReport` is generated, THE `RhythmPocketCoach` SHALL identify the weakest pad (e.g., `SNARE` or `KICK_RIGHT`) and produce a deterministic, plain-English 2-sentence coaching recommendation.

---

## 3. UI & Entrypoint Locations

- **Feature Path:** `feature/src/main/kotlin/com/drumsheesh/feature/highway/`
- **Core Coach Path:** `core/src/main/kotlin/com/drumsheesh/core/coach/RhythmPocketCoach.kt`
- **Design Reference:** [`docs/specs/02-groove-highway-coach/design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)

---

## 4. Non-Goals (Out of Scope for This Feature)

- **NG1:** Do NOT build natural-language AI prompt generation here (handled in `03-ai-groove-recorder`).
- **NG2:** Do NOT use microphone audio pitch/onset detection; all scoring uses deterministic touch-event timestamps.

---

## 5. 📝 Revision History

| Rev | Date | Changes (Added / Changed / Removed) | Reason / User Feedback |
| :--- | :--- | :--- | :--- |
| `v1.0` | `2026-11-10` | Initial specification created. | Initial brainstorm / roadmap scope. |
