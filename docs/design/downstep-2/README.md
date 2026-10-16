# Downstep 2.0 — Player redesign handoff

Prototype of the redesigned Downstep player (practice / transcription: speed, pitch, loops).
Built from the current app's theme (`Color.kt`, `Type.kt`), fonts and app icon in `EvolveHQ/downstep`.

```
handoff/
  README.md                 this document
  prototype/                clickable prototype — open "Downstep Player v3.dc.html" in a browser
  screenshots/              every section and behaviour, 1280px layout, dark + light
  assets/
    (fonts)                 Inter 400/500/600, Space Grotesk 500/700 (OFL): not copied here, they live once in
                            shared-ui/src/commonMain/composeResources/font/ (the prototype loads them from there)
    icons/                  downstep-app-icon.svg (replaces the Java coffee-cup in the title bar)
    palette.json            all colours, type, radii and control sizes used
```

The prototype simulates audio: the playhead advances in real time at the chosen speed, loops wrap, passes count,
progression ramps the speed. Nothing is actually heard. Isolate, Metronome, Count‑in, Save preset, Export are visual states only.

---

## 1. Design principles

1. **The waveform is the instrument.** It spans the full window width with no card around it; everything else is
   secondary and sits above or below it. A second, zoomed waveform shows the active loop at sub‑beat precision.
2. **Loops are first‑class objects.** A track owns many named loops. They are visible everywhere at once — as colour
   regions on the waveform, as chips above it, and as the subject of the tools below it.
3. **Library is a drawer, not a sidebar.** It appears on demand and never takes width from the waveform.
4. **Actions are labelled.** Every top action is an icon *plus* a word (Metronome, Count‑in, Analyze) or a
   standard, well‑known icon with a tooltip (theme, focus, save, export). No ambiguous icon rows.
5. **Two accents only.** Amber = speed / loop start (A) / primary action. Teal = pitch / loop end (B) / isolate.
   Everything else is a neutral surface scale.
6. **Pills for state, cards for settings.** Toggles and chips are 999px pills; settings groups are 20px‑radius cards.

### Colour (see `assets/palette.json`)

| Role | Dark | Light |
|---|---|---|
| Background / waveform ground | `#0F1114` / `#0B0D10` | `#FAF6F0` / `#F4EEE5` |
| Surface 1‑4 (cards → hover → borders → strongest) | `#1A1E24 #20252C #2C323B #3B424C` | `#EEE7DC #E6DED1 #DDD4C6 #D3CABD` |
| Text / secondary text | `#ECE8E2` / `#A4AAB4` | `#1C1B19` / `#5E5A54` |
| Amber (speed, A, play, loop‑on) | `#F2A541` on `#2A1800` | `#A86400` on white |
| Teal (pitch, B, isolate) | `#62B6B0` on `#00201E` | `#2D7C77` on white |
| Loop colours (cycled by loop id) | amber, teal, coral `#E07A5F`, olive `#B8C46A` | `#A86400 #2D7C77 #B0533A #3F7A5A` |

Light mode is the existing Downstep light palette; dark is the existing dark palette. The theme toggle (sun/moon,
header) persists in `localStorage`.

### Type

- **Space Grotesk 700** — track title (26px), large read‑outs (speed 34px, pitch 34px, focus read‑outs 18px), drawer title.
- **Inter 400/500/600** — all UI text. 13px controls, 12px meta, 11px uppercase section labels with 0.6px tracking.
- Tabular numerals everywhere (`font-feature-settings: 'tnum'`) so timecodes and percentages do not jitter.

### Spacing & sizes

24px page gutter. Header controls 40px tall; chips 34px; waveform 200px (260px in focus); zoom strip 96px;
play 68px; transport buttons 44px; loop‑tool buttons 30px. Minimum hit target in the transport is 44px.

---

## 2. Anatomy, top to bottom

![player dark](screenshots/01-player-dark.png)

### 2.1 Title bar
OS‑style strip with the real Downstep icon (`assets/icons/downstep-app-icon.svg`) and version.

### 2.2 Header
- **Library** pill (left) with track count → opens the drawer.
- **Track title** + meta line: source · duration · detected BPM · loop count.
- **Analyze** pill → section detection (see §4.8). Label becomes *Analyzing…* then *Re‑analyze*.
- **Metronome / Count‑in** segmented pill; each is an independent amber‑filled toggle.
- Icon buttons: **Focus** (⤢), **Theme** (☀/☾), **Save preset** (bookmark), **Export** (↑).

### 2.3 Loop chips strip
One chip per loop, in time order: colour dot · name · length · **pass badge**. The active chip is inverted
(light fill on dark, dark fill on light) with an amber pass badge. Suggested loops from Analyze appear as
dashed amber chips with ✓ / ✕. The strip ends with a dashed **+ New loop `L`** chip. Scrolls horizontally when long.

### 2.4 Overview waveform (full width)
- Bars: played = light grey, unplayed = dark grey; inside the active loop they take the loop colour (played solid, unplayed 50%).
- Every loop draws a tinted region, a 3px top rule and 1px edge lines in its colour; the active one is brighter.
- Thin bar‑lines (every 4 beats) when *Snap to beat* is on.
- **A / B pills** at the bottom with timecodes to 0.1s; a faint bracket shows the zoom window.
- **Playhead**: 2px line with a glow and a floating timecode pill at the top.
- Dashed amber outlines + labels for Analyze suggestions.

### 2.5 Zoom strip
Attached under the overview, inset to the gutter. Shows the active loop plus ~35% padding each side (minimum 4 beats).
Beat lines every beat (bar lines stronger). Header text: `ZOOM · <loop> · <window from–to>` and
`<n> beats · <n> bars` for the loop. A/B lines are draggable here with the same snapping rules.

### 2.6 Transport row
- **Left:** loop toggle pill (amber when looping, shows the active loop name or *Loop off*) · active‑loop tool
  group (◀ nudge · ½ · ×2 · nudge ▶ · ✕ delete) · **Snap to beat** toggle.
- **Centre:** ⏮ to start · ↺ −5s · **Play/Pause** · ↻ +5s · ⏭ next loop.
- **Right:** **Pass** counter pill (`Pass 4 · +3% in 2` while progression is on) · keyboard hints (`A B set · [ ] loops · F focus`).
  In focus mode the hints are replaced by large speed and pitch read‑outs.

### 2.7 Speed · Pitch · Isolate cards
- **Speed** 25–200%, slider, −5/+5, reset. Amber read‑out.
- **Pitch** −12…+12 semitones, slider, −1/+1, reset. Teal read‑out.
- **Isolate** Low / Mid / High solo toggles (teal when on). Label reads *Full mix* or e.g. *Low + Mid only*.

### 2.8 Progression · Notes cards (per active loop)
- **Progression**: on/off switch; one sentence of pill‑steppers — *From 70% to 100%, raise +3% every 2 passes*;
  status line (*Off — speed stays where you set it* / *Now 76% · next +3% in 1 pass* / *At target — 100%*);
  a thin from→to progress track.
- **Notes**: free text saved with the loop (chords, fingering, what to listen for).

### 2.9 Library drawer
360px panel sliding over the left edge with a scrim. Title, search, close; Tracks / Playlists segmented pill;
list with artwork tile, title, meta (source · loop count); the current track is highlighted amber. Full‑width amber **Import** button at the bottom.

![library](screenshots/05-library-tracks.png)

---

## 3. Interaction model

### Pointer
| Target | Gesture | Result |
|---|---|---|
| Overview or zoom waveform | click | seek playhead |
| A or B line (either waveform) | drag | move loop edge; snaps to beat when *Snap* is on; A can't cross B (min 0.5% apart) |
| Loop chip | click | make active, jump playhead to its start, loop on |
| Suggested chip ✓ / ✕ | click | accept (becomes a real loop, active) / dismiss |
| ½ / ×2 | click | halve / double the loop keeping A fixed |
| ◀ ▶ nudge | click | shift the whole loop one beat (or 1s when snap is off) |
| ✕ in tool group | click | delete active loop; the first remaining loop becomes active |
| Progression steppers | click | ±5% (from/to), ±1% (step), ±1 (every) |

### Keyboard (ignored while typing in Notes)
| Key | Action |
|---|---|
| `Space` | play / pause |
| `L` | new loop at the playhead (8 beats long, snapped) |
| `Shift+L` | loop on / off |
| `A` / `B` | set active loop's start / end at the playhead (snapped) |
| `[` / `]` | previous / next loop (jumps to its start) |
| `←` / `→` | seek −5 s / +5 s |
| `F` | focus mode on / off |
| `Esc` | close drawer, exit focus |

### State feedback
- Active loop: inverted chip, brighter region, visible A/B pills, tool group enabled, zoom strip follows it.
- Playing: play icon ↔ pause icon; playhead and timecode pill move; passes tick up at every wrap.
- Toggles (Metronome, Count‑in, Loop, Snap, Isolate, Progression) fill with their accent when on.
- Suggestions are always dashed until accepted — the app never commits loops on the user's behalf.

---

## 4. Functionalities

### 4.1 Multiple named loops
Each loop = `{ name, start, end, passes, notes }`, stored with the track, sorted by start time, coloured by id.
Any number per track. One is *active*: it is the one that wraps playback, gets edited, zoomed and counted.

### 4.2 Looping
When *Loop* is on and the playhead reaches B, it jumps to A and the loop's **pass counter** increments.
*Loop off* keeps the loop defined but plays through.

### 4.3 Speed and pitch
Independent; speed 25–200% in 5% steps, pitch ±12 semitones. The transport simulation advances in proportion to speed.

### 4.4 Snap to beat
BPM is shown as detected (92 in the demo). When on: A/B set/drag, new loops and nudges quantise to the beat grid;
bar lines appear in both waveforms. Off: free positioning, 1s nudges.

### 4.5 Zoom strip
Derived view, no separate zoom control: window = active loop ± max(35% of its length, 4 beats). Changing the loop
re‑frames it automatically.

### 4.6 Pass counter and Progression
A pass is counted every time the active loop wraps during playback (no judgement of quality — see note below).
Progression, when on, sets speed to *From* and raises it by *Step* every *Every* passes until *Target*; the
transport pill shows *+3% in N*, and the card's status/progress bar reflect where you are.
> Recommendation for the real app: reset the pass counter if the user intervenes mid‑pass (seek, A/B change, speed/pitch change, pause) so "passes" means uninterrupted passes.

### 4.7 Isolate
Band solo (Low / Mid / High) — any combination; none selected = full mix. Visual only in the prototype.

### 4.8 Analyze (suggested loops)
Simulates ~1.6s of analysis then proposes sections (Verse 1, Chorus, Bridge) as dashed chips and dashed outlines
on the waveform. Accept turns one into a normal loop and makes it active; dismiss removes it. *Re‑analyze* repeats.
Intended backend: section/beat detection model; an LLM only for naming. Never auto‑commit.

### 4.9 Focus mode
Hides header, chips strip, Speed/Pitch/Isolate and Progression/Notes. Keeps: compact title + *Exit focus*,
taller waveform (260px), zoom strip, transport with loop tools, big speed/pitch read‑outs and pass pill.

![focus](screenshots/03-focus-mode.png)

### 4.10 Notes
Per‑loop free text, persisted with the loop, shown for the active loop.

### 4.11 Library
Tracks and playlists in a drawer; selecting a track loads it (loops are per track), closes the drawer. Import at the bottom.

### 4.12 Theme
Dark (default) and light, toggled from the header, persisted.

---

## 5. Screenshot index

| File | What it shows |
|---|---|
| `01-player-dark.png` | Default state, dark, Solo loop active |
| `02-player-light.png` | Same in light theme |
| `03-focus-mode.png` | Focus mode |
| `04-player-after-focus.png` | Back to full layout |
| `05-library-tracks.png` | Library drawer, Tracks tab |
| `06-library-playlists.png` | Library drawer, Playlists tab |
| `07-analyze-running.png` | Analyze in progress (header label *Analyzing…*) |
| `08-analyze-suggestions.png` | Three dashed suggestions on chips and waveform |
| `09-suggestion-accepted.png` | Chorus accepted → real loop; Verse dismissed |
| `10-progression-isolate-metronome.png` | Progression on (speed snapped to 70%), Low isolated, Metronome + Count‑in on |
| `11-playing-passes-ramp.png` | Playing; pause icon, pass pill with ramp hint, moved playhead |
| `12-loop-off-snap-off.png` | Loop off, Snap off (no grid lines) |
| `13-new-loop-added.png` | `L` pressed → "Loop 5" (8 beats) created at playhead and active |
| `14-loop-halved.png` | ½ applied → 4 beats, zoom re‑framed |

---

## 6. Not in scope / open
- Spotify / Apple Music import (DRM prevents time‑stretch; dropped by decision).
- Real audio engine, EQ, metronome click, count‑in bars, export formats, preset contents.
- Mic‑based "clean pass" scoring.
- Responsive behaviour below ~1000px (cards would need to stack).
