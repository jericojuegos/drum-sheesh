# 🏛️ Architecture Decision Records (ADRs) — Drum Sheesh

> **Location:** `docs/foundation/decisions.md`  
> **Last Updated:** 2026-11-10

---

## ADR-001: Native Android (`android-kotlin-compose`) Priority with Pure-Kotlin Portable `:core` for Phase 2 Web

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:** *Real Drum* is a fast-paced multi-finger percussion instrument where pointer-down latency (`<10ms`) and 4-to-8-finger simultaneous touch tracking are paramount. However, we also want a Web version (`04-web-studio-companion`) in Phase 2 for desktop keyboard/MIDI players.
- **Decision:** Build Phase 1 on **`android-kotlin-compose`** using the **5-Module "Portable Core, Platform Shell"** architecture (`:core`, `:ui`, `:platform`, `:feature`, `:app`). Enforce zero Android imports in `:core` so all pad geometry, 16-bit PCM drum synthesis, groove sequencing, timing drift scoring (`RhythmPocketCoach`), and JSON groove contracts are 100% portable to the Phase 2 Web companion.
- **Consequences:** Mobile/tablet users get unbeatable native multi-touch ergonomics and `SoundPool` audio responsiveness in Phase 1, while Phase 2 Web reuses the exact same domain rules and JSON groove schemas.

---

## ADR-002: Pure-Kotlin Procedural 16-Bit PCM Drum Synthesizer (`ProceduralDrumSynthesizer`) + `SoundPool`

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:** Binary audio assets (`.wav`/`.ogg`) often bloat repositories, cause licensing headaches, or fail to load in headless unit tests. Conversely, naive synthesizers sound like 8-bit buzzers if not properly shaped.
- **Decision:** Implement a studio-inspired multi-layer `ProceduralDrumSynthesizer` in `:core` that generates standard 44.1kHz 16-bit mono RIFF WAV byte arrays for all 10 pads across 4 distinct kits (*Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave Electronic*) using pitch-swept sine/triangle body oscillators, bandpass-filtered snare wire bursts, and metallic FM inharmonic cymbal matrices. `:platform` writes these WAV buffers to `cacheDir` on startup and loads them into an Android `SoundPool` (`USAGE_GAME`).
- **Consequences:** Zero external asset blockers, 100% deterministic audio tests in `:core`, `<10ms` trigger latency on Android, and instant compatibility with custom user `.wav` files in Phase 2.

---

## ADR-003: Multi-Pointer Down Triggering, Radial Velocity & Hi-Hat Choke State Machine

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:** Standard Compose `Modifier.clickable` waits for pointer-up (adding 70–120ms delay) and only tracks a single pointer per composable, making two-handed drum rolls impossible. Additionally, on a real drum kit, stepping on or hitting a closed hi-hat immediately chokes a ringing open hi-hat.
- **Decision:**
  1. Use a single unified stage `pointerInput` gesture loop inspecting `PointerEventPass.Initial` that triggers hits immediately when any pointer `changedToDownIgnoreConsumed()` or crosses into a new `PadHitZone` during a slide/roll.
  2. Compute hit velocity (`0.45f..1.0f`) from radial proximity to the pad center (`PadHitResolver`).
  3. Track active `HI_HAT_OPEN` stream IDs in `HiHatChokeTracker` so any `HI_HAT_CLOSED` hit immediately stops the open hi-hat stream.
- **Consequences:** Authentic multi-finger drumming feel, expressive dynamics without pressure-sensitive hardware, and realistic hi-hat articulation.

---

## ADR-004: Hybrid AI Text-to-Groove (`Gemini 2.5 Flash` + Offline Procedural Fallback) & Rhythm Pocket Coach

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:** *Real Drum* only provides static preset beats and zero timing feedback. We want users to generate any groove from a text prompt AND work 100% offline when no API key or internet connection is present.
- **Decision:**
  1. Define a `AiGrooveGeneratorPort` in `:core` that accepts a natural-language prompt and returns a validated `GroovePattern`.
  2. In `:platform`, if `GEMINI_API_KEY` is configured and network is available, call Gemini (`gemini-2.5-flash` with `responseMimeType = "application/json"`); otherwise, transparently fall back to `:core`'s `ProceduralGrooveGenerator`, which parses BPM, genre keywords (*boom bap, trap, rock, metal, funk, house, jazz, punk, dnb*), swing, and density into an authentic playable pattern.
  3. Pair every groove with `RhythmPocketCoach` in `:core` to evaluate user hit timestamps (`deltaMs`), classifying rushing/dragging tendencies and per-pad accuracy.
- **Consequences:** AI Text-to-Groove works reliably both online with Gemini and 100% offline during local development, CI, and airplane-mode practice.

---

## ADR-005: Strict Manifest Permission Allowlist Guard (`checkMergedManifestPermissions`)

- **Date:** 2026-11-10
- **Status:** Accepted
- **Context:** Mobile drum apps on Google Play are notorious for invasive permissions (`RECORD_AUDIO`, `READ_PHONE_STATE`, `AD_ID`, location tracking).
- **Decision:** Register a custom Gradle verification task `checkMergedManifestPermissions` in root `build.gradle.kts` that parses the merged debug manifest and fails the build if any permission outside `["android.permission.VIBRATE", "android.permission.INTERNET"]` is present.
- **Consequences:** Provable privacy guarantee and automatic enforcement of the `android-kotlin-compose` stack rules.
