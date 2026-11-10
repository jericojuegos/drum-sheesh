# 📋 Feature Requirements: Multi-Touch Drum Stage & Low-Latency Audio Engine (`01-drum-kit-stage`)

> **Feature Slug:** `01-drum-kit-stage` · **Phase / Wave:** `Phase 1 (Wave 1)`  
> **Depends On:** `00-scaffold`  
> **Branch / Owner:** `feat/01-drum-kit-stage` · **Status:** `[ ]` Planned

---

## 1. Introduction & Overview

`01-drum-kit-stage` delivers the core playable instrument of **Drum Sheesh**: a landscape-optimized 10-pad spatial drum kit for Android phones and tablets backed by a pure-Kotlin procedural 16-bit PCM drum synthesizer (`ProceduralDrumSynthesizer`) and an Android `SoundPool` low-latency adapter (`SoundPoolDrumAudioAdapter`).

### Assumptions (Flagged for Review)

- **A1 (Orientation):** The drum stage runs in full-screen landscape (`sensorLandscape`) so all 10 pads have generous multi-finger hit zones on both phones and tablets. **Confirmed.**
- **A2 (Zero-Asset Audio Synthesis):** All 4 studio drum kits (*Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave Electronic*) are synthesized deterministically as 44.1kHz 16-bit mono RIFF WAV buffers in `:core` and loaded into `SoundPool` on startup. **Confirmed.**

### Domain Glossary

- **`DrumPadId`:** Enum of the 10 playable drum/cymbal pads (`CRASH`, `RIDE`, `HI_HAT_OPEN`, `HI_HAT_CLOSED`, `TOM_HIGH`, `TOM_MID`, `TOM_FLOOR`, `SNARE`, `KICK_LEFT`, `KICK_RIGHT`).
- **Radial Velocity:** Dynamic hit loudness (`0.45f..1.0f`) computed from how close the user's touch coordinate `(x, y)` is to the center of the target `PadHitZone`.
- **Hi-Hat Choke:** Acoustic behavior where triggering `HI_HAT_CLOSED` immediately stops any currently ringing `HI_HAT_OPEN` audio stream.

---

## 2. Requirements & EARS Acceptance Criteria

### Requirement 1: Multi-Touch Pointer-Down Triggering & Radial Velocity
**User Story:** As a mobile/tablet drummer, I want pads to sound immediately when up to 8 fingers touch the screen or roll across pads, so that rapid drum fills and simultaneous kick+hat+snare hits feel instantaneous.

#### Acceptance Criteria
1. **1.1:** WHEN one or more pointers touch down inside any `PadHitZone` on `PointerEventPass.Initial`, THE `DrumStageViewModel` SHALL resolve the hit pad and radial velocity (`0.45f..1.0f`) via `PadHitResolver` and trigger `DrumAudioPort.triggerPad(...)` immediately on pointer-down without waiting for pointer-up.
2. **1.2:** WHILE a pointer remains pressed and slides across the boundary from one `PadHitZone` into a different `PadHitZone`, THE `MultiTouchDrumStageCanvas` SHALL trigger a new pad hit for the newly entered pad (enabling one-finger and two-finger drum rolls).
3. **1.3:** WHEN a pad is triggered, THE `DrumPadSurface` SHALL render an immediate visual hit impulse and expanding Electric Amber (`#FBBF24`) ripple ring at the touch coordinate with zero per-frame object allocations inside `Canvas`.

---

### Requirement 2: 4 Studio Kits, Procedural 16-Bit PCM Synthesizer & Hi-Hat Choke
**User Story:** As a drummer, I want 4 distinct studio drum kits with realistic hi-hat choking and left/right-handed layout flipping, so that the kit adapts to my playing style and genre.

#### Acceptance Criteria
1. **2.1:** WHEN a kit preset (*Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave Electronic*) is requested, THE `ProceduralDrumSynthesizer` in `:core` SHALL generate valid 44.1kHz 16-bit mono RIFF WAV byte arrays (with `RIFF`/`WAVE` headers and non-zero acoustic energy) for all 10 `DrumPadId` values.
2. **2.2:** WHEN `DrumPadId.HI_HAT_CLOSED` is triggered while a `DrumPadId.HI_HAT_OPEN` stream is active, THE `HiHatChokeTracker` and `SoundPoolDrumAudioAdapter` SHALL immediately stop the active open hi-hat stream ID.
3. **2.3:** WHEN the user toggles **Left-Handed Mode (`L/R`)** in `StudioTopBar`, THE `PadHitResolver` and `MultiTouchDrumStageCanvas` SHALL mirror the horizontal X coordinates (`1f - x`) of all asymmetrical pads (hi-hats, snare, toms, ride, crash).

---

## 3. UI & Entrypoint Locations

- **Screen Entrypoint:** `feature/src/main/kotlin/com/drumsheesh/feature/stage/DrumStageScreen.kt`
- **Core Domain Path:** `core/src/main/kotlin/com/drumsheesh/core/{model,audio,engine}/`
- **Platform Audio Adapter:** `platform/src/main/kotlin/com/drumsheesh/platform/audio/SoundPoolDrumAudioAdapter.kt`
- **Design Reference:** [`docs/specs/01-drum-kit-stage/design-reference/stitch-prompt.md`](./design-reference/stitch-prompt.md)

---

## 4. Non-Goals (Out of Scope for This Feature)

- **NG1:** Do NOT build the scrolling Groove Highway or Rhythm Pocket Coach here (handled in `02-groove-highway-coach`).
- **NG2:** Do NOT build AI Text-to-Groove or Session Recording here (handled in `03-ai-groove-recorder`).

---

## 5. 📝 Revision History

| Rev | Date | Changes (Added / Changed / Removed) | Reason / User Feedback |
| :--- | :--- | :--- | :--- |
| `v1.0` | `2026-11-10` | Initial specification created. | Initial brainstorm / roadmap scope. |
