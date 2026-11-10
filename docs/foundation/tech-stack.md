# 🛠️ Tech Stack & Dependencies — Drum Sheesh

> **Master Brain Stack Pack:** `android-kotlin-compose` (`v1.0`)  
> **Phase 1 Target:** Android Phones & Tablets (`minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`)  
> **Phase 2 Target:** Web Studio Companion (`web/` consuming `:core` pure-Kotlin domain & JSON contracts)  
> **Last Updated:** 2026-11-10

---

## 1. Core Stack Table (Phase 1 — Android Native)

| Layer | Technology / Library | Version | Purpose |
|-------|----------------------|---------|---------|
| **Language** | Kotlin | `2.0.21` | 100% Kotlin across `:core`, `:ui`, `:platform`, `:feature`, `:app` |
| **Build System** | Android Gradle Plugin (AGP) + Kotlin DSL | `8.7.2` | Multi-module build & manifest guard tasks |
| **Core Domain (`:core`)** | Pure Kotlin JVM (`kotlin("jvm")`) + `org.jetbrains.kotlinx:kotlinx-coroutines-core` + `kotlinx-serialization-json` | `1.9.0` / `1.7.3` | Zero-Android-dependency drum kit math, 16-bit PCM WAV synthesizer, Groove sequencer, Pocket Coach, and JSON serialization |
| **UI Framework (`:ui`, `:feature`)** | Jetpack Compose BOM (`androidx.compose:compose-bom`) + Material 3 | `2024.10.01` | Multi-touch Drum Stage Canvas, Groove Highway 60/120fps renderer, Dark Studio hardware theme |
| **State & Lifecycle** | `androidx.lifecycle:lifecycle-viewmodel-compose` + `lifecycle-runtime-compose` | `2.8.7` | UDF `StateFlow<UiState>` ViewModels & lifecycle-aware audio pause/resume |
| **Low-Latency Audio (`:platform`)** | Android `SoundPool` (`USAGE_GAME` / `CONTENT_TYPE_SONIFICATION`) + `:core` `ProceduralDrumSynthesizer` | Android SDK | `<10ms` polyphonic drum triggering (up to 16 simultaneous voices) + instant hi-hat choke |
| **Local Persistence (`:platform`)** | App-scoped JSON repository (`context.filesDir`) via `kotlinx-serialization-json` | `1.7.3` | Zero-overhead persistence for custom AI grooves, recorded loop takes, and user preferences |
| **AI Integration (`:platform`)** | Gemini Structured JSON REST Adapter (`gemini-2.5-flash`) + `:core` `ProceduralGrooveGenerator` fallback | REST / HTTPS | Converts natural-language beat prompts into playable `GroovePattern` JSON objects (works offline via procedural fallback) |
| **Unit Testing** | JUnit 4 / Kotlin Test + `kotlinx-coroutines-test` | `4.13.2` / `1.9.0` | Fast headless JVM unit & property tests for `:core` and `:feature` |

---

## 2. Key Constants & Performance Budgets

| Constant | Value | Defined In | Purpose |
|----------|-------|------------|---------|
| `SAMPLE_RATE_HZ` | `44100` | `:core` `ProceduralDrumSynthesizer` | Standard CD-quality mono 16-bit PCM synthesis rate |
| `MAX_POLYPHONIC_STREAMS` | `16` | `:platform` `SoundPoolDrumAudioAdapter` | Maximum simultaneous drum/cymbal voices before oldest voice stealing |
| `BPM_MIN` / `BPM_MAX` | `40` / `240` | `:core` `GroovePattern` | Allowed tempo range for metronome and Groove Highway |
| `PERFECT_HIT_WINDOW_MS` | `20L` | `:core` `RhythmPocketCoach` | `|deltaMs| <= 20ms` classification for `PERFECT` timing |
| `GOOD_HIT_WINDOW_MS` | `45L` | `:core` `RhythmPocketCoach` | `|deltaMs| <= 45ms` classification for `GOOD` timing |
| `MAX_MATCH_WINDOW_MS` | `120L` | `:core` `RhythmPocketCoach` | Maximum window before an expected step is scored as `MISS` |
| `DEFAULT_AI_MODEL` | `"gemini-2.5-flash"` | `:platform` `GeminiAiGrooveAdapter` | Fast, free-tier-friendly structured JSON beat generation model |

---

## 3. Approved Android Manifest Permission Allowlist

Enforced automatically on every build by `./gradlew checkMergedManifestPermissions`:

| Permission | Allowed? | Justification |
|------------|----------|---------------|
| `android.permission.VIBRATE` | ✅ Allowed | Crisp tactile haptic pulse on drum pad pointer-down |
| `android.permission.INTERNET` | ✅ Allowed | Optional HTTPS calls to Gemini API for AI Text-to-Groove generation |
| `android.permission.RECORD_AUDIO` | ❌ Forbidden | Session recording captures finger-hit event timestamps, never microphone audio |
| `android.permission.CAMERA` | ❌ Forbidden | Not needed |
| `android.permission.ACCESS_FINE_LOCATION` | ❌ Forbidden | Not needed |
| `com.google.android.gms.permission.AD_ID` | ❌ Forbidden | Zero ad networks or tracking SDKs |

---

## 4. External Dependencies & Environment Readiness (Round 7)

| Dependency | Required For | Status | Fallback / Mitigation |
|------------|--------------|--------|-----------------------|
| **JDK 17+ & Android SDK (`compileSdk 35`)** | Building & testing Android modules | ✅ Ready locally | Standard Gradle wrapper |
| **Studio Audio Samples** | All 10 drum pads across 4 kits | ✅ Zero external blocker | Generated deterministically in pure Kotlin by `ProceduralDrumSynthesizer` as 16-bit PCM RIFF WAV buffers |
| **Gemini API Key (`GEMINI_API_KEY`)** | Cloud AI Text-to-Groove | 🟡 Optional at runtime | Users can enter BYOK key in settings OR use the built-in `ProceduralGrooveGenerator` which parses BPM/genre/swing keywords offline |

---

## 5. Prohibited Patterns

1. **Never import `android.*` or `androidx.*` inside `:core`** — `:core` must remain 100% pure Kotlin JVM so unit tests run in milliseconds and Phase 2 Web can reuse `:core`.
2. **Never trigger drum audio on `clickable` / pointer-up** — always trigger on `PointerEventPass.Initial` pointer-down (`changedToDownIgnoreConsumed()`).
3. **Never use `MediaPlayer` for drum pad hits** — `MediaPlayer` has 60–120ms latency and cannot handle rapid polyphonic re-triggering; always use pre-loaded `SoundPool` streams.
4. **Never allocate objects inside Compose `Canvas` draw lambdas** — pre-allocate or `remember` paint/path/color structures outside `onDraw`.
