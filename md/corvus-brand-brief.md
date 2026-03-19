# Corvus — Brand & Design Brief

> *Corvus corvax. The crow. Cunning, precise, undeceivable.*

---

## Brand Concept

Corvus is a fact-checking tool that cuts through noise. The brand draws on the crow's reputation in folklore and science — a bird associated with **intelligence, memory, and the ability to distinguish truth from illusion**. In many cultures, corvids are watchers: they observe, remember, and are not easily fooled.

The application should feel like a **precision instrument**, not a consumer toy. It is calm under pressure, exact in its output, and carries a quiet authority. It does not shout verdicts — it presents them with the composure of something that has already done the hard thinking.

---

## Brand Personality

| Trait | Expression |
|---|---|
| **Precise** | Clean type, exact spacing, no decorative excess |
| **Authoritative** | Strong typographic hierarchy, confident verdict presentation |
| **Composed** | Restrained animations, no gratuitous motion |
| **Sharp** | High contrast, angular accents, minimal rounding |
| **Watchful** | Eye-like iconography in the logo mark, subtle surveillance aesthetic |

---

## Naming & Wordmark

**Name:** Corvus  
**Pronunciation:** KOR-vus  
**Tagline options:**
- *See through the noise.*
- *Truth, examined.*
- *What the crow sees.*
- *Facts, not feelings.*

**Wordmark style:** All-caps or small-caps. Slightly wide letter-spacing (tracking +80 to +120). The "V" in Corvus can subtly echo a crow's wingspan or a downward-pointing chevron (verdict arrow).

---

## Logo Mark

**Concept:** A minimal, geometric crow head or eye — not illustrative, not cute. Think:
- A single angular silhouette of a crow in profile, reduced to 5–7 straight lines
- Or: a stylized eye (the "watching" motif) formed by two sharp curves, with a strong pupil — representing scrutiny
- Or: a simple downward chevron (V shape) that reads as both crow beak and "verified checkmark inverted"

**Do not use:**
- Rounded, friendly bird illustrations
- Gradient logo marks
- Checkmark clichés
- Shield shapes (too generic for fact-checking apps)

**Logo mark in UI:** Used in the splash screen and app bar. Monochrome only — never coloured logo on coloured background.

---

## Color System

### Primary Palette — Dark Theme (Default)

Corvus defaults to dark. This is a news/research tool; dark mode reduces fatigue and feels more serious.

| Token | Hex | Usage |
|---|---|---|
| `corvus-void` | `#0A0A0C` | App background — near-black with blue-black undertone |
| `corvus-surface` | `#111116` | Card backgrounds, bottom sheets |
| `corvus-surface-raised` | `#1A1A22` | Elevated surfaces, dialogs |
| `corvus-border` | `#2A2A35` | Dividers, card strokes |
| `corvus-text-primary` | `#F0EFE8` | Primary text — warm white, not pure white |
| `corvus-text-secondary` | `#8A8A99` | Secondary labels, metadata |
| `corvus-text-tertiary` | `#4A4A5A` | Placeholder, disabled |
| `corvus-accent` | `#C8FF00` | Primary accent — electric chartreuse |
| `corvus-accent-dim` | `#8AAA00` | Accent at lower intensity |

### Light Theme

| Token | Hex | Usage |
|---|---|---|
| `corvus-void` | `#F5F4EF` | App background — warm off-white, not pure white |
| `corvus-surface` | `#FFFFFF` | Card backgrounds |
| `corvus-surface-raised` | `#EEEDE8` | Subtle insets |
| `corvus-border` | `#D8D7D0` | Dividers |
| `corvus-text-primary` | `#0F0F14` | Primary text |
| `corvus-text-secondary` | `#5A5A6A` | Secondary text |
| `corvus-accent` | `#3A6600` | Accent in light — deep moss green |

### Verdict Colors (Semantic — Both Themes)

| Verdict | Color | Hex (Dark) | Hex (Light) |
|---|---|---|---|
| TRUE | Moss Green | `#4CAF6E` | `#2D7A4A` |
| FALSE | Crimson | `#E8524A` | `#C0392B` |
| MISLEADING | Amber | `#E8A830` | `#C07800` |
| PARTIALLY_TRUE | Steel Blue | `#5A9FD4` | `#2A6FA0` |
| UNVERIFIABLE | Slate | `#7A7A8A` | `#5A5A6A` |

**Verdict color usage:** Background tint on verdict card only. Never used as full-screen backgrounds. The verdict badge uses a subtle fill + strong label, not a solid block of colour.

### Accent Rationale

`#C8FF00` (electric chartreuse) is the defining visual signature:
- Unexpected against near-black — creates immediate recognition
- Associated with alertness, signal, visibility (radar screens, night-vision)
- Distinct from every generic fact-checking / news app in the market
- Works as a progress indicator, CTA button, and active state marker
- In light mode, replaced with deep moss green to maintain sophistication

---

## Typography

### Typeface Stack

| Role | Font | Style | Notes |
|---|---|---|---|
| **Display / Wordmark** | [DM Serif Display](https://fonts.google.com/specimen/DM+Serif+Display) | Regular | Headlines, verdict label |
| **UI / Body** | [IBM Plex Mono](https://fonts.google.com/specimen/IBM+Plex+Mono) | Regular / Medium | All UI labels, body text |
| **Secondary Body** | [IBM Plex Sans](https://fonts.google.com/specimen/IBM+Plex+Sans) | Regular / SemiBold | Longer explanation text |

**Rationale:**
- **DM Serif Display** — editorial authority, a newspaper-of-record feel. Used sparingly for maximum impact (verdict word, app name, major headings only).
- **IBM Plex Mono** — the monospaced font reinforces the "analytical instrument" feel. Data, sources, confidence scores, metadata all in mono. Feels like output from a system that has processed information.
- **Plex Mono + Serif** is an unusual pairing that creates genuine character — editorial gravity meets technical precision.

### Type Scale (sp)

| Token | Size | Font | Weight | Usage |
|---|---|---|---|---|
| `type-verdict` | 36sp | DM Serif Display | Regular | Verdict word (TRUE / FALSE etc.) |
| `type-headline` | 22sp | DM Serif Display | Regular | Screen titles |
| `type-title` | 18sp | IBM Plex Mono | Medium | Card titles, section headers |
| `type-body` | 15sp | IBM Plex Sans | Regular | Explanation text |
| `type-label` | 13sp | IBM Plex Mono | Regular | Source names, metadata |
| `type-caption` | 11sp | IBM Plex Mono | Regular | Timestamps, confidence value |

### Tracking & Leading
- Verdict text: letter-spacing +2sp, no tight leading
- Monospaced labels: letter-spacing +0.5sp
- Body text: default leading × 1.5

---

## Iconography

**Style:** Line icons, 1.5dp stroke weight, squared-off caps (not rounded). No filled icons except for the active/selected state (filled swap).

**Icon set:** Use [Phosphor Icons](https://phosphoricons.com/) — they have an Android library and match the sharp, precise aesthetic.

**Key custom icons:**
- Corvus logo mark (in-house)
- Verdict indicators: custom arrow-down (FALSE), arrow-up (TRUE), wave (MISLEADING)

---

## Shape & Radius System

Corvus uses **minimal rounding**. This is not a bubbly consumer app.

| Token | Value | Usage |
|---|---|---|
| `shape-sharp` | 2dp | Verdict badge, accent chips |
| `shape-card` | 6dp | Content cards, source cards |
| `shape-sheet` | 12dp | Bottom sheets (top corners only) |
| `shape-input` | 4dp | Text fields |

No 24dp+ pill shapes anywhere in the app. The squareness reinforces precision.

---

## Motion & Animation

**Principle:** Motion communicates meaning, not delight. Every animation in Corvus has a reason.

| Moment | Animation | Duration | Easing |
|---|---|---|---|
| Verdict reveal | Fade + scale from 0.92 | 380ms | FastOutSlowIn |
| Verdict badge color fill | Horizontal wipe left→right | 500ms | Linear |
| Confidence bar fill | Width expand from 0 | 600ms | FastOutSlowIn |
| Source cards | Staggered fade-in (80ms apart) | 280ms each | EaseOut |
| Pipeline step change | Text crossfade | 200ms | EaseInOut |
| Loading state | Pulsing accent dot (not spinner) | Loop 1200ms | EaseInOut |
| Error state | Subtle shake (horizontal, 3 cycles) | 300ms | EaseOut |

**Loading indicator:** Do not use a standard `CircularProgressIndicator`. Use a pulsing `corvus-accent` dot or a horizontal scanning line — more in keeping with the "analytical processing" feel.

**No confetti, no celebration animations.** Corvus is a truth tool, not a game.

---

## Screen-by-Screen Design Notes

### Input Screen
- Large `OutlinedTextField` with `IBM Plex Mono` input text
- Char counter in `corvus-text-tertiary`, mono
- CTA button: full-width, `corvus-accent` background, `corvus-void` text, 4dp radius, "ANALYSE" label (not "Check" — more precise)
- Below field: subtle label "Paste a claim, tweet, or statement"
- Empty state: crow icon mark centered, tagline below

### Pipeline Progress Card
- Replaces input area during processing
- Current step label in `IBM Plex Mono` medium
- Accent pulsing dot to the left of label
- Step list below: completed steps in `corvus-accent-dim`, current in `corvus-accent`, pending in `corvus-text-tertiary`

### Result Screen
- **Verdict card** at top: `DM Serif Display` 36sp verdict word, colored left border (3dp, verdict color), confidence value in mono below
- Explanation in `IBM Plex Sans` 15sp, comfortable line height
- Key facts as dashed-border list items (not bullet points — dashes feel more analytical)
- Sources section header: "EVIDENCE" in `IBM Plex Mono` small-caps
- Each source: publisher name in accent, title in primary, URL in tertiary — tap opens browser
- Sticky bottom bar: "ANALYSE ANOTHER" + "SHARE" buttons

### History Screen
- List grouped by date: `TODAY`, `YESTERDAY`, `THIS WEEK` in mono small-caps as section headers
- Each item: verdict color left stripe (2dp), claim truncated to 2 lines, verdict badge right-aligned, timestamp below
- Empty state: "No checks yet. Corvus is watching." + icon

---

## App Icon

**Concept:** The icon should work at 48×48dp. A minimal geometric crow silhouette or stylised eye mark on `corvus-void` background with `corvus-accent` as the singular accent element.

**Do not:**
- Use gradients in the icon
- Use more than 2 colors
- Make it look friendly or approachable

**Target feel:** Something between a surveillance camera logo and a vintage naturalist illustration mark — but abstract, geometric, contemporary.

**Adaptive icon:**
- Foreground: logo mark in `corvus-text-primary`
- Background: `corvus-void` (#0A0A0C)

---

## What Corvus Is Not

To maintain brand discipline, reject the following in any design decision:

| Do Not | Because |
|---|---|
| Purple gradient backgrounds | Generic AI aesthetic |
| Rounded pill buttons | Too friendly, mismatches precision |
| Checkmark as primary symbol | Overused in fact-checking |
| Shield iconography | Overused in security/trust apps |
| Sans-serif only typography | Lacks editorial authority |
| Confetti / celebration on TRUE verdict | Fact-checking is not a game |
| Blue as primary accent | Indistinguishable from every news app |
| Excessive card shadows | Reduces sharpness of the layout |

---

## Summary Token Reference

```xml
<!-- colors.xml (dark theme) -->
<color name="corvus_void">#0A0A0C</color>
<color name="corvus_surface">#111116</color>
<color name="corvus_surface_raised">#1A1A22</color>
<color name="corvus_border">#2A2A35</color>
<color name="corvus_text_primary">#F0EFE8</color>
<color name="corvus_text_secondary">#8A8A99</color>
<color name="corvus_text_tertiary">#4A4A5A</color>
<color name="corvus_accent">#C8FF00</color>
<color name="corvus_accent_dim">#8AAA00</color>

<!-- Verdict semantic colors -->
<color name="corvus_verdict_true">#4CAF6E</color>
<color name="corvus_verdict_false">#E8524A</color>
<color name="corvus_verdict_misleading">#E8A830</color>
<color name="corvus_verdict_partial">#5A9FD4</color>
<color name="corvus_verdict_unverifiable">#7A7A8A</color>
```
