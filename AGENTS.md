# 🧠 Drum Sheesh — Agent Brain (`AGENTS.md`)

**Project:** Drum Sheesh — Ultra-low-latency multi-touch electronic drum kit, interactive groove highway, and AI Rhythm Pocket Coach
**Context:** Personal (`contexts/personal.md`)
**Stack:** Native Android Priority (Kotlin 2.x · Jetpack Compose · Material 3 · Coroutines / Flow · Low-Latency `SoundPool`/`AudioTrack` · Gradle Kotlin DSL) + Phase 2 Web Companion (`web/`) powered by Pure-Kotlin Portable `:core`
**Target Platforms:** Android Phones & Tablets (Landscape-first stage · Min SDK `26` · Target SDK `35`) + Phase 2 Web Browser Companion
**Architecture Pattern:** Layered MVVM / UDF with Pure-Kotlin Core ("Portable Core, Platform Shell")
**Master Brain:** `android-kotlin-compose`
**Last synced:** `android-kotlin-compose-v1.0` - 2026-11-10

---

## 🚦 Project State

**Stage:** `active`
**Stage last updated:** `2026-11-10`

| Stage | Meaning | Next Action / Skill |
|---|---|---|
| `initialized` | Brain & specs ready, Phase 0 not built yet | Run **`/build`** on `docs/specs/00-scaffold/tasks.md` |
| `scaffolded` | Base Android project, modules & CI verified | Run **`/spec`** or **`/build`** on Wave 1 features in `docs/planning/roadmap.md` |
| `active` | Feature wave development in progress | **`/spec`** → **`/build`** → **`/verify`** → **`/ship`** |

---

## ⚡ Deterministic Verification Commands

Run these commands after every task before marking `[x]`:
- **Kotlin Compile & Typecheck (Every task):** `./gradlew compileDebugKotlin`
- **Unit Tests (Core & Feature logic):** `./gradlew testDebugUnitTest`
- **Static Analysis & Lint:** `./gradlew lintDebug`
- **Permission & Manifest Allowlist Guard:** `./gradlew checkMergedManifestPermissions`
- **Full Verification (`/verify`):** `./gradlew check testDebugUnitTest checkMergedManifestPermissions`

---

## 🗺️ Context Map (`docs/INDEX.md`) & Skills (`.agents/skills/`)

> **Progressive Disclosure Rule:** Do not read all docs at once. Read [`docs/INDEX.md`](docs/INDEX.md) or use this quick table:

| Question / Action | File or Skill |
|---|---|
| What should we build next? | **`/next`** ([`.agents/skills/next/SKILL.md`](.agents/skills/next/SKILL.md)) |
| What is the phased roadmap & feature dependency wave matrix? | [`docs/planning/roadmap.md`](docs/planning/roadmap.md) |
| Create or expand a feature spec (`requirements`, `design`, `tasks`, `uat`) | **`/spec`** ([`.agents/skills/spec/SKILL.md`](.agents/skills/spec/SKILL.md)) |
| Build Phase 0 scaffold or execute tasks/waves for a feature | **`/build`** ([`.agents/skills/build/SKILL.md`](.agents/skills/build/SKILL.md)) |
| Verify build, EARS acceptance criteria, and stack Hard Failures | **`/verify`** ([`.agents/skills/verify/SKILL.md`](.agents/skills/verify/SKILL.md)) |
| Ship completed feature, update `roadmap.md`, and generate PR summary | **`/ship`** ([`.agents/skills/ship/SKILL.md`](.agents/skills/ship/SKILL.md)) |
| What are we building overall & why? | [`docs/foundation/prd.md`](docs/foundation/prd.md) |
| Where do files live & what is the module layout? | [`docs/foundation/architecture.md`](docs/foundation/architecture.md) |
| What libraries, minSdk, constants & permissions exist? | [`docs/foundation/tech-stack.md`](docs/foundation/tech-stack.md) |
| What are the Compose tokens, theme & visual tone? | [`docs/foundation/design-system.md`](docs/foundation/design-system.md) |
| Why was X decided this way? | [`docs/foundation/decisions.md`](docs/foundation/decisions.md) |
| How do we execute tasks & waves? | [`.agents/rules/execution-protocol.md`](.agents/rules/execution-protocol.md) |
| What are the Android layer, Compose & privacy rules? | [`.agents/rules/stack-rules.md`](.agents/rules/stack-rules.md) |

---

## ⚠️ Critical Constraints — Never Violate

- **Pure-Kotlin Core Rule (Enables Mobile + Web Reusability):** The `:core` module must NEVER import Android SDK classes (`android.*`, `androidx.*`, Compose, `SoundPool`, `AudioTrack`, `Context`). All drum pad hit geometry, procedural 16-bit PCM drum synthesis, groove step sequencer math, millisecond timing drift scoring (`RhythmPocketCoach`), and AI prompt/JSON schemas live in `:core` so they run identically in JVM unit tests, Android, and the Phase 2 Web version.
- **Multi-Touch Down-Event Immediate Triggering:** Drum pads MUST trigger audio on pointer-down (`PointerEventPass.Initial` / `changedToDownIgnoreConsumed()`) with full multi-pointer tracking (up to 8 simultaneous fingers) and slide-to-roll detection—never wait for `onClick` or pointer-up, which adds >80ms latency.
- **Hi-Hat Choke & Polyphonic Voice Pool:** Triggering `HI_HAT_CLOSED` or `HI_HAT_PEDAL` MUST immediately choke (stop/fade within 15ms) any active `HI_HAT_OPEN` voice stream.
- **Zero Per-Frame Allocations in Canvas:** Never instantiate `Path`, `Paint`, `Brush`, or collection objects inside Compose `Canvas` `onDraw` blocks on the 60/120Hz Drum Stage or Groove Highway.
- **Manifest Permission Allowlist:** Only `android.permission.VIBRATE` and `android.permission.INTERNET` (solely for optional AI Text-to-Groove HTTPS calls) are permitted in `AndroidManifest.xml`. `RECORD_AUDIO`, `CAMERA`, `ACCESS_FINE_LOCATION`, and ad-tracking permissions are strictly forbidden and blocked by `./gradlew checkMergedManifestPermissions`.
- **Lifecycle Safety:** `SoundPool`, metronome coroutines, and audio synthesis tracks in `:platform` must bind to lifecycle and pause/release cleanly on `ON_STOP` so no ghost beats play when the app is backgrounded.

---

## 🛡️ Code Review & Security Audit Gate (All AI Models)

When asked to review code, audit security, or evaluate another AI model's findings (even without invoking `/verify`):
1. **Contract First:** Read `docs/foundation/decisions.md`, `.agents/rules/stack-rules.md`, and the active feature's `docs/specs/<NN-feature-slug>/{requirements,design}.md` before flagging issues. Never contradict settled decisions or Non-Goals.
2. **Evidence-Only Severity Tiers (No Ping-Pong):** Only report **`[P0 — Blocker]`** (provable bug, `:core` Android SDK leak, unapproved manifest permission, lifecycle leak, broken EARS requirement, or `stack-rules.md` Hard Failure) and **`[P1 — Defect]`** (unhandled `design.md` Error Matrix case or race condition) with exact `file:line` and a concrete trigger path. Suppress **`[P2 — Style/Nitpick]`** opinions.
3. **Arbiter Mode for External Reviews:** When given findings from another developer's AI review, classify each item against `design.md` + `stack-rules.md` into **`ACCEPT (P0/P1)`** vs. **`REJECT (P2 / Settled Tradeoff / Guarded Upstream)`** before modifying any code.

---

## 🚫 Anti-Hallucination List (Out of Scope for Current Phase)

Do NOT build in Phase 1 (Android Mobile/Tablet MVP):
- **No Ad-Network SDKs or Interstitial/Banner Ads** (zero AdMob, Unity Ads, or tracking SDKs)
- **No Social Network Features** (no user profiles, public leaderboards, comment feeds, or following systems)
- **No 50-Pack Paid Asset Store / Marketplace** (ship 4 studio-grade built-in kits synthesized deterministically via `:core` PCM engine: *Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave Electronic*)
- **No Multi-Track DAW Timeline Editor** (focus strictly on interactive drum pads, groove highway, loop recording, and AI coaching)
- **No Microphone Audio Recording (`RECORD_AUDIO` permission)** (all recording captures finger-hit event timestamps, pad IDs, and velocities—never ambient microphone audio)
- **Web Companion App (`web/`) is Phase 2 (`04-web-studio-companion`)** — in Phase 1, keep all domain/audio/groove logic 100% pure Kotlin inside `:core` so Phase 2 Web can consume the exact same models and contracts without rewrite.
