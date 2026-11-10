# 📋 Project Backlog (Deferred & Rejected) — Drum Sheesh

> **Location:** `docs/planning/backlog.md`  
> **Last Updated:** 2026-11-10

---

## 1. Deferred Features (`[-]` — Phase 2 & Beyond)

| ID | Feature | Target Phase | Why Deferred from Phase 1 |
|----|---------|--------------|---------------------------|
| `04-web-studio-companion` | **Web Browser Studio (`web/`)** — Desktop QWERTY keyboard, WebMIDI drum controller support, and mobile browser PWA reusing `:core` groove/synth contracts | Phase 2 | Mobile/Tablet native multi-touch (`android-kotlin-compose`) is Priority #1; `:core` is built 100% pure Kotlin in Phase 1 so Web reuses it cleanly in Phase 2 |
| `05-custom-sample-import` | **Custom User WAV/MP3 Sample Import & Pad Remapping** | Phase 2 | Phase 1 ships with 4 deterministic studio-synthesized kits (`ProceduralDrumSynthesizer`) for zero asset friction |
| `06-usb-midi-android` | **Android USB/Bluetooth MIDI Hardware Drum Trigger Support (`android.media.midi`)** | Phase 2 | On-screen multi-touch drumming is the primary MVP flow |
| `07-stem-midi-export` | **Standard MIDI (`.mid`) & Multi-Stem WAV File Share Sheet Export** | Phase 2 | Phase 1 focuses on in-app session loop recording, quantization, and JSON groove persistence |
| `08-cloud-sync` | **Cross-Device Cloud Groove Library Sync (Android ↔ Web)** | Phase 2 | Local app-scoped JSON persistence is sufficient until the Phase 2 Web Companion launches |

---

## 2. Explicitly Rejected Features (Permanently Out of Scope — Anti-Hallucination List)

| Rejected Feature | Reason |
|------------------|--------|
| **Intrusive Ad-Network SDKs (AdMob, Unity Ads, Banner/Interstitial Ads)** | *Real Drum*'s biggest user pain point is mid-session ad interruptions; Drum Sheesh is a clean, zero-ad instrument |
| **In-App Social Feed, Public Profiles, Comments & Follower Graphs** | Adds moderation and backend bloat unrelated to practicing rhythm and beatmaking |
| **50-Pack Microtransaction Asset Store** | Focus on high-craft studio synthesis and customizable parameters rather than paywalled asset clutter |
| **Full Multi-Track DAW Arrangement Timeline (Piano Roll, Synth Tracks, Vocal Mixing)** | Drum Sheesh is a dedicated drum instrument and rhythm coach, not a general-purpose DAW |
| **Microphone Audio Recording (`RECORD_AUDIO` permission)** | All recording captures finger-hit event timestamps and pad velocities; never request microphone permissions |
