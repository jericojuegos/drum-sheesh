# 📄 Product Requirements Document (PRD) — Drum Sheesh

> **Project:** Drum Sheesh (`drum-sheesh`)  
> **Reference App:** *Real Drum: electronic drums set* (`br.com.rodrigokolb.realdrum`)  
> **Primary Platform (Phase 1):** Native Android Phones & Tablets (`android-kotlin-compose`)  
> **Secondary Platform (Phase 2):** Web Studio Companion (`web/` — Desktop QWERTY, WebMIDI & Mobile PWA)  
> **Last Updated:** 2026-11-10

---

## 1. Product Summary & Vision

**Tagline:** *Play, generate, and master any drum groove in seconds.*

**What it does:** **Drum Sheesh** is an ultra-low-latency multi-touch electronic drum kit and interactive rhythm coach built first for Android phones and tablets (with a portable pure-Kotlin core ready for a Web Studio companion in Phase 2). Inspired by the instant playability of *Real Drum*, Drum Sheesh strips away intrusive ad-network bloat and dated skeuomorphism in favor of a sleek dark-studio hardware aesthetic, **AI Text-to-Groove generation**, and a **Rhythm Pocket Coach** that measures millisecond timing drift (rushing vs. dragging) on every beat.

---

## 2. Target User Personas

### Persona 1: The Self-Taught Mobile/Tablet Drummer ("Alex")
- **Profile:** Aspiring drummer or student who wants to practice rhythm, limb independence, and song grooves on an Android tablet or phone without owning a noisy acoustic or expensive electronic drum kit.
- **Pain Points with *Real Drum*:** Interrupted every 2 minutes by full-screen video ads; static preset lessons with zero feedback on *why* a groove feels sloppy (e.g., rushing the snare on beat 4 or inconsistent hi-hat timing).
- **How Drum Sheesh Wins:** Zero ads, instant pointer-down multi-touch response (<10ms audio trigger), visual Groove Highway, and a post-take **Rhythm Pocket Coach** breakdown showing exact millisecond timing accuracy.

### Persona 2: The Bedroom Beatmaker & Finger Drummer ("Maya")
- **Profile:** Producer or songwriter sketching drum patterns on a tablet, phone, or (in Phase 2) browser + MIDI pad.
- **Pain Points with *Real Drum*:** Can't type a musical vibe (e.g., *"94 BPM J Dilla neo-soul boom bap with swung hi-hats"*) to generate a playable beat, and recorded loops are trapped inside a closed format.
- **How Drum Sheesh Wins:** **AI Text-to-Groove** generates structured 16/32-step patterns on demand, and the **Session Recorder** supports overdubbing, quantization, and clean JSON/WAV loop export.

---

## 3. Business & Monetization Model

- **Model:** Transparent Freemium + Pro Unlock (No intrusive banner or interstitial ads ever).
- **Free Tier (Phase 1 Default):**
  - Full 10-pad multi-touch Drum Stage with all 4 studio-synthesized kits (*Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave Electronic*)
  - Left-handed / Right-handed kit flip & radial velocity sensitivity
  - Built-in curated Groove Library (Rock, Boom Bap, Trap, Funk, Pop-Punk, Metal, Bossa Nova, House) + Visual Groove Highway
  - Rhythm Pocket Coach timing accuracy scoring & local Session Recorder
  - BYOK (Bring Your Own Gemini API Key) or offline procedural AI Groove fallback (up to 10 cloud AI groove generations/month on free tier)
- **Pro Tier (Phase 2 — $4.99/mo or $19.99 One-Time Lifetime Pass):**
  - Unlimited Cloud AI Text-to-Groove generations
  - Cross-device synchronization between Android Phone/Tablet and the Phase 2 Web Studio (`web/`)
  - Multi-stem WAV / MIDI file export & custom sample pack import

---

## 4. Core Features by Phase

### Phase 0: Foundation Scaffold (`00-scaffold`)
- 5-module Android Gradle Kotlin DSL workspace (`:app`, `:core`, `:ui`, `:platform`, `:feature`)
- Automated manifest permission guard (`./gradlew checkMergedManifestPermissions`)
- GitHub Actions CI (`.github/workflows/ci.yml`) running `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions`

### Phase 1: Android Mobile & Tablet MVP (Priority)
1. **`01-drum-kit-stage` (Interactive Multi-Touch Drum Kit & Low-Latency Audio Engine):**
   - Landscape-locked (and tablet-adaptive) 10-pad spatial drum kit (`KICK_LEFT`, `KICK_RIGHT`, `SNARE`, `HI_HAT_CLOSED`, `HI_HAT_OPEN`, `TOM_HIGH`, `TOM_MID`, `TOM_FLOOR`, `CRASH`, `RIDE`).
   - Pure-Kotlin procedural 16-bit 44.1kHz PCM drum synthesizer in `:core` generating studio-grade acoustic & electronic waveforms loaded into Android's `SoundPool` (`USAGE_GAME` / `SONIFICATION`) in `:platform` for <10ms trigger latency.
   - True multi-pointer touch engine (`PointerEventPass.Initial`) supporting up to 8 simultaneous fingers, radial center- vs. rim-hit velocity calculation, slide-to-roll across pads, realistic hi-hat choke (`HI_HAT_CLOSED` silences `HI_HAT_OPEN`), and Left/Right-handed kit flip.
2. **`02-groove-highway-coach` (Interactive Groove Highway, Metronome & Rhythm Pocket Coach):**
   - Built-in library of 8+ genre grooves with adjustable BPM (`40..240 BPM`), swing (`0..75%`), and visual metronome pulse.
   - 60/120fps Compose Canvas **Groove Highway** showing upcoming pad notes scrolling toward a hit-line with `PERFECT` (±20ms), `GOOD` (±45ms), `EARLY`/`LATE` (±90ms), and `MISS` (>90ms) visual feedback.
   - **Rhythm Pocket Coach** in `:core` that computes overall accuracy (`0..100`), mean millisecond drift (e.g., `-11ms Rushing` vs. `+14ms Dragging`), per-limb stability, and actionable practice advice after each session.
3. **`03-ai-groove-recorder` (AI Text-to-Groove Generator & Multi-Take Session Recorder):**
   - **AI Text-to-Groove:** Natural-language prompt input (e.g., *"110 BPM funky breakbeat with ghost snares"*) backed by Gemini structured JSON output (plus a deterministic offline style-grammar fallback when offline or without an API key) that produces a playable `GroovePattern` directly into the Groove Highway.
   - **Session Recorder & Loop Playback:** Record live finger-drumming takes with microsecond-accurate relative timestamps, replay loops on the Drum Stage with visual ghost-pad hits, toggle optional 1/16th-note quantization, and persist saved grooves/sessions locally.

### Phase 2: Web Studio Companion & Pro Ecosystem (`04-web-studio-companion` + Backlog)
- **`04-web-studio-companion`:** Web browser version (`web/`) sharing the `:core` drum/groove/coach logic, playable via Desktop QWERTY keyboard, USB MIDI drum pads (WebMIDI API), and mobile browser touch.
- Custom user WAV/MP3 sample import & pad remapping.
- Cloud account sync between Android and Web.

---

## 5. Core User Journey (Phase 1 End-to-End)

1. **Instant Play (0 seconds to first beat):** User launches **Drum Sheesh** on their Android phone or tablet in landscape mode → the 10-pad *Acoustic Studio* kit is immediately live → tapping any pad with multiple fingers fires instant audio, subtle haptic feedback, and a glowing Electric Amber velocity ring.
2. **Switch Kits & Ergonomics:** User switches from *Acoustic Studio* to *808 Trap* or toggles **Left-Handed Mode** in the top studio bar → pad layout and synthesized voice buffers swap instantaneously.
3. **Generate or Pick a Groove:** User taps **AI Groove** and types *"95 BPM boom bap with swung hi-hats"* (or picks a preset lesson) → the groove loads into the **Groove Highway**.
4. **Practice & Pocket Coach:** User taps **Play & Practice** → plays along for 4–8 bars → **Rhythm Pocket Coach** displays their score (e.g., `91% Pocket Score · -6ms Slight Rush on Snare · 96% Kick Consistency`) with a 2-sentence targeted drill tip.
5. **Record & Replay Loop:** User hits **REC**, lays down a custom 4-bar beat, enables `1/16 Quantize`, and replays/saves the loop locally.
