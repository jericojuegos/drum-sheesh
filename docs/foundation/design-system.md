# 🎨 Design System — Drum Sheesh

> **Source:** Extracted from *Real Drum* dark stage layout + Upgraded with Teenage Engineering / Ableton Note Dark Studio Hardware Aesthetic & `scaffolds/foundation/brand-defaults.md`  
> **Last updated:** 2026-11-10  
>
> This file is the single source of truth for all visual decisions across Android Jetpack Compose (`:ui`, `:feature`) and the Phase 2 Web Studio.

---

## Visual Identity

- **Tone:** Dark & premium (Tactile dark hardware studio aesthetic, glassmorphic top transport bar, velocity-reactive glowing pad rings, zero visual clutter)
- **Reference UI:** *Real Drum* (spatial 10-pad acoustic/electronic stage ergonomy) + *Ableton Note* / *Linear* (high-contrast dark surface hierarchy & crisp typography)
- **Default mode:** `dark` (Stage Dark `#0F0F1A` default for low eye-strain during drumming sessions)
- **Supports both modes:** `yes` (Dark mode default; high-contrast Light studio mode supported)
- **Primary Orientation (Mobile/Tablet):** Landscape (`sensorLandscape`) full-bleed stage with top compact HUD bar and slide-over sheets/modals for Groove Highway, AI Prompt, and Pocket Coach results.

---

## Color Palette

| Token | Light Mode | Dark Mode (Default) | Usage |
|-------|-----------|---------------------|-------|
| `--color-primary` | `#F59E0B` | `#FBBF24` | Active drum hit rings, primary buttons, beat 1 downbeat pulse, active kit badge |
| `--color-primary-hover` | `#D97706` | `#F59E0B` | Button pressed/hover state, rim-hit glow |
| `--color-primary-subtle` | `#FEF3C7` | `#2E210B` | Active pad badge backgrounds, selected groove pill |
| `--color-accent-ai` | `#6366F1` | `#818CF8` | AI Text-to-Groove & Rhythm Pocket Coach accents, AI badges |
| `--color-bg-page` | `#F8FAFC` | `#0F0F1A` | Drum Stage floor / root background |
| `--color-bg-surface` | `#FFFFFF` | `#1A1A2E` | Top HUD bar, drum pad resting shell, cards |
| `--color-bg-elevated` | `#F1F5F9` | `#232338` | Groove Highway overlay, AI Prompt dialog, Pocket Coach modal |
| `--color-text-primary` | `#0F172A` | `#E2E8F0` | Primary HUD labels, BPM readout, pad titles |
| `--color-text-secondary` | `#64748B` | `#94A3B8` | Pad sub-labels (e.g., `CLOSED HH`), millisecond drift labels |
| `--color-text-disabled` | `#CBD5E1` | `#475569` | Inactive transport controls |
| `--color-border` | `#E2E8F0` | `#2D2D44` | Pad resting outer rings, HUD dividers |
| `--color-border-focus` | `#F59E0B` | `#FBBF24` | Active focus / downbeat border |
| `--color-success` | `#22C55E` | `#A6E3A1` | `PERFECT` (±20ms) timing hit indicator & high Pocket Score (`>=85%`) |
| `--color-error` | `#EF4444` | `#F38BA8` | `MISS` (>90ms) indicator, active `REC` pulsing dot |
| `--color-warning` | `#F59E0B` | `#F9E2AF` | `EARLY` / `LATE` drift indicator (`46..90ms`) |
| `--color-info` | `#3B82F6` | `#89B4FA` | `GOOD` (`21..45ms`) timing indicator & cymbal pad tint |

### Pad Category Color Coding (Dark Stage)
- **Cymbals (`CRASH`, `RIDE`, `HI_HAT_OPEN`, `HI_HAT_CLOSED`):** Brushed Warm Brass ring `#FBBF24` with subtle radial metallic sheen `#2A2418`
- **Snare (`SNARE`):** Crisp Studio Coral/Amber ring `#FB7185` with high-visibility center target zone
- **Toms (`TOM_HIGH`, `TOM_MID`, `TOM_FLOOR`):** Electric Indigo/Cyan ring `#38BDF8`
- **Kicks (`KICK_LEFT`, `KICK_RIGHT`):** Deep Sub-Bass Violet/Emerald ring `#A78BFA`

---

## Typography

| Element | Font Family | Size | Weight | Line-height |
|---------|------------|------|--------|-------------|
| h1 (Pocket Score / Big BPM) | Inter / Sans-Serif | `32sp` (`2rem`) | `700` | `1.2` |
| h2 (Modal Titles) | Inter / Sans-Serif | `22sp` (`1.375rem`) | `600` | `1.3` |
| h3 (Section Headers) | Inter / Sans-Serif | `18sp` (`1.125rem`) | `600` | `1.4` |
| h4 (Pad Primary Label) | Inter / Sans-Serif | `14sp` (`0.875rem`) | `700` | `1.3` |
| body | Inter / Sans-Serif | `14sp` (`0.875rem`) | `400` | `1.5` |
| small (Pad Sub-Label / Drift) | JetBrains Mono / Monospace | `11sp` (`0.6875rem`) | `500` | `1.4` |
| button | Inter / Sans-Serif | `13sp` (`0.8125rem`) | `600` | `1.0` |

**Font loading:**
- Android Compose: System `FontFamily.SansSerif` (Roboto/Inter metrics) for UI + `FontFamily.Monospace` for BPM, step counters, and millisecond timing drift (`-8ms`) so numbers never jitter during 60fps updates.

---

## Spacing Scale

```text
4dp  (0.25rem) → micro: icon-to-label gap, pad ring stroke width
8dp  (0.5rem)  → small: HUD pill spacing, compact pad grid gap
12dp (0.75rem) → compact: control bar vertical padding
16dp (1rem)    → default: modal padding, card spacing
20dp (1.25rem) → comfortable: section gaps
24dp (1.5rem)  → spacious: tablet stage outer margin
32dp (2rem)    → large: modal header separation
```

---

## Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `--radius-sm` | `6dp` | Step sequencer grid cells, compact badges |
| `--radius-md` | `12dp` | HUD transport buttons, kit selector cards |
| `--radius-lg` | `18dp` | Kick pedal pads, Groove Highway panel, dialogs |
| `--radius-xl` | `24dp` | Tablet floating HUD dock |
| `--radius-full` | `9999dp` | Circular acoustic drum/cymbal pads, BPM pill |

---

## Shadows

| Token | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `2dp elevation` | HUD buttons |
| `--shadow-md` | `6dp elevation` | Drum pad shells |
| `--shadow-lg` | `16dp elevation` | Groove Highway & Pocket Coach modals |

---

## Component Patterns

### 1. Multi-Touch Drum Pads (`DrumPadSurface`)
- **Resting State:** Dark radial gradient fill (`#1A1A2E` → `#121222`), `2dp` category-colored border ring at `55%` alpha, crisp uppercase label + monospace shortcut badge.
- **Active Hit State (`<150ms` decay):** Instant scale impulse (`1.0f` → `0.95f` → `1.0f`), border alpha jumps to `100%` `#FBBF24`, radial velocity ripple expands from exact touch coordinate `(x, y)` with opacity proportional to `velocity` (`0.45f..1.0f`).
- **Hi-Hat Choke Indicator:** When `HI_HAT_CLOSED` chokes `HI_HAT_OPEN`, the Open Hi-Hat pad ring flashes a quick choke dampener ring.

### 2. Top Studio Transport HUD Bar (`StudioTopBar`)
- Height: `52dp` pinned at top of landscape screen (`#1A1A2E` at `92%` alpha).
- Left: App Brand Pill (`DRUM SHEESH`), Active Kit Selector Dropdown (*Acoustic Studio*, *808 Trap*, *Vintage Breakbeat*, *Synthwave*), Left/Right-Handed Flip Toggle (`L/R`).
- Center: Monospace BPM Counter (`120 BPM` with `-`/`+` & Tap Tempo), Metronome Pendulum Pill, Active Groove Title.
- Right: **Groove Highway** toggle button, **AI Groove** button (`#818CF8` accent), **REC / Loop** transport controls, and **Pocket Coach** report button.

### 3. Groove Highway Overlay (`GrooveHighwayRenderer`)
- Collapsible top/side lane showing 10 pad tracks (or compact 6-core-lane view on phones) with glowing note pills scrolling toward the Now-Line.
- Real-time hit judgement badge (`PERFECT`, `GOOD`, `EARLY -28ms`, `LATE +34ms`, `MISS`) rendered with zero per-frame allocations.

---

## Motion

| Property | Value | Usage |
|----------|-------|-------|
| Pad Hit Impulse | `0ms` attack, `140ms` exponential decay | Instant visual confirmation on pointer-down |
| Sheet / Modal transition | `200ms FastOutSlowInEasing` | Opening AI Groove or Pocket Coach sheet |
| Highway Frame Rate | `withInfiniteAnimationFrameMillis` (60/120Hz) | Smooth scrolling note bars synced to audio clock |
| Reduced Motion | Respect system animator scale | Disable expanding ring ripples; keep high-contrast border flash |

---

## Layout

| Property | Value |
|----------|-------|
| Phone Landscape (`< 840dp` width) | Full-bleed 10-pad stage (`82%` height) + compact `52dp` top HUD bar; Groove Highway opens as translucent top split-lane (`28%` height) |
| Tablet Landscape (`>= 840dp` width) | Spacious 10-pad stage + persistent split Groove Highway & live Pocket Coach telemetry sidebar |

---

## Icons

- **Library:** Material Icons Extended / Custom Compose Vector paths for Cymbal, Snare, Kick, Metronome, Waveform, and AI Sparkles
- **Default size:** `18dp` HUD controls, `24dp` transport actions
