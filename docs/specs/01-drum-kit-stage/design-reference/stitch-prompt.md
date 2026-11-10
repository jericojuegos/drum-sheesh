# 🎨 UI / Google Stitch Prompt: Multi-Touch Drum Stage (`01-drum-kit-stage`)

> **How to Use:** Copy the prompt block below into [Google Stitch](https://stitch.withgoogle.com/) (or Figma AI) if generating visual mockups before running `/build`.

---

## Screen 1 Prompt — `Landscape 10-Pad Multi-Touch Drum Stage` (Copy-Paste into Stitch)

```text
Design a mobile/tablet landscape screen for "Drum Sheesh" — an ultra-low-latency multi-touch electronic drum kit and AI rhythm coach.

SCREEN PURPOSE:
The primary full-bleed 10-pad landscape drum stage where users play drums with multiple fingers, switch between 4 studio kits, flip Left/Right-handed ergonomics, and see real-time velocity ripples.

VISUAL DIRECTION & DESIGN TOKENS (from docs/foundation/design-system.md):
- Visual Tone: Dark & Premium Studio Hardware (inspired by Teenage Engineering, Ableton Note, and Real Drum's spatial layout)
- Mode: Dark Mode Default
- Background Stage: #0F0F1A | HUD Bar & Pad Resting Surface: #1A1A2E | Elevated Overlay: #232338 | Border: #2D2D44
- Text Primary: #E2E8F0 | Text Secondary: #94A3B8
- Primary Hit / Beat Accent: #FBBF24 (Electric Amber) | AI Accent: #818CF8 (Indigo)
- Pad Category Rings: Cymbals #FBBF24, Snare #FB7185, Toms #38BDF8, Dual Kicks #A78BFA
- Typography: Heading/UI font Inter, Monospace readout JetBrains Mono

LAYOUT & KEY COMPONENTS (Mapped to EARS Requirements):
1. Top Studio Transport HUD Bar (52dp height): Brand badge "DRUM SHEESH", Kit Selector dropdown ("Acoustic Studio"), Left/Right-Handed toggle pill ("R-HAND"), Monospace BPM counter ("120 BPM"), and action pills for "Groove Highway", "AI Groove", and "REC".
2. Full-Bleed 10-Pad Spatial Drum Stage (Req 1.1, 1.3):
   - Top Row: 16" Crash Cymbal (left), 10" High Tom (center-left), 12" Mid Tom (center-right), 20" Ride Cymbal (right)
   - Middle Row: Open Hi-Hat & Closed Hi-Hat (left), 14" Studio Snare with prominent target rings (center-left), 16" Floor Tom (right)
   - Bottom Row: Left Kick Pedal & Right Kick Pedal (wide rounded rectangular pedal pads at bottom center)
3. Active Velocity Ripple State (Req 1.3): Show Snare and Closed Hi-Hat mid-hit with a glowing #FBBF24 concentric velocity ring and "+94% VEL" micro-indicator.

CONSTRAINTS:
- Do NOT include banner ads, social feeds, or complex multi-track DAW mixer sliders.
- Keep maximum screen area dedicated to large, finger-friendly drum pad hit targets.
```
