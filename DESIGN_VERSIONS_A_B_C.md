# BIAP Design System — 3 Versions for Review

## DESIGN VERSION A: "HERITAGE INDIGO"
### Theme: Traditional textile roots + modern professionalism

**Inspiration Sources:**
- Material 3 Expressive (smooth transitions, emphasis typography)
- Figma Admin UI (information density, clear hierarchy)
- Linear App (minimal, focused, fast interactions)

**Primary Accent Color:** `#1E3A5F` (Deep Indigo)
- Rationale: Indigo dyeing is core to South Asian textile heritage; professional depth; warm undertone
- Secondary Tint: `#2D5A8C` (lighter indigo for hover/active states)
- Usage: Primary CTAs, active states, focus rings, badges

**Color Palette:**
```
Base:
  - White: #FFFFFF (cards, backgrounds)
  - Charcoal: #1A1A1A (primary text)
  - Gray: #6B7280 (secondary text)
  - Light Gray: #F3F4F6 (alt backgrounds, headers)

Semantic:
  - Success/In Stock: #10B981 (warm green, heritage tie)
  - Alert/Low: #DC2626 (red, high contrast)
  - Caution/Medium: #F59E0B (amber, natural tone)
  - Neutral/Disabled: #D1D5DB (light gray)

Accent:
  - Primary: #1E3A5F (deep indigo)
  - Hover: #2D5A8C (lighter indigo)
  - Focus: #1E3A5F with 4px border
```

**Typography:**
- Font: Inter (geometric, professional, Material 3 native)
- Display/Splash: 32px, 700, charcoal
- Heading 1: 24px, 600, charcoal
- Body Large: 16px, 400, charcoal
- Body Regular: 14px, 400, gray
- Caption/Label: 12px, 500, gray (uppercase for labels)

**Button Style:**
- Primary: Solid indigo background, white text, 40px height, radius 6px, subtle shadow on hover
- Secondary: White background, indigo 2px border, indigo text
- Tertiary: No background, indigo text, underline on hover

**Card Style:**
- White background, 1px light gray border, radius 8px, 16px padding
- Hover: Shadow elevation (4px shadow), no color change
- Semantic accent: Left border accent (4px) for status (green for in-stock, red for alert)

**Information Density:**
- Portfolio grid: 2-col (mobile), 3-col (tablet), 4-col (desktop)
- Design detail: 2-column layout (image left, info right, tablet+)
- Inventory table: Full-width, compact rows (32px height), alternating gray backgrounds

**Motion (Material 3 Expressive):**
- LoadingIndicator: Indigo, smooth spin (not aggressive)
- Button press: 100ms scale-down (1.02x) + fade-in feedback
- Screen transitions: 300ms spring curve (not bouncy, just smooth)
- Sync animation: Wave progress indicator in indigo

**Visual Character:** Grounded, heritage-conscious, professional, calm (indigo is soothing)

---

## DESIGN VERSION B: "RUST & CRAFT"
### Theme: Artisan/handmade aesthetic + modern operations

**Inspiration Sources:**
- Awwwards B2B winners (craft/artisan focus)
- Behance case studies (inventory management tools)
- Material 3 Expressive (warm palette adaptation)

**Primary Accent Color:** `#A85C4C` (Warm Rust/Terracotta)
- Rationale: Ties to earth tones in textile/pottery; handmade craft aesthetic; warm, inviting but professional
- Secondary Tint: `#C97A63` (lighter rust for hover)
- Usage: Primary CTAs, badges, emphasis

**Color Palette:**
```
Base:
  - White: #FFFFFF (cards, backgrounds)
  - Deep Charcoal: #2C3E50 (primary text, dark navy undertone)
  - Warm Gray: #7A6B63 (secondary text, warm tone)
  - Warm Light: #F5F1ED (alt backgrounds, warm tint)

Semantic:
  - Success/In Stock: #6B8E3C (sage green, natural craft tie)
  - Alert/Low: #C84E31 (rust-red, tied to palette)
  - Caution/Medium: #D4A574 (warm golden, natural)
  - Neutral/Disabled: #C4B5A0 (warm gray)

Accent:
  - Primary: #A85C4C (rust)
  - Hover: #C97A63 (lighter rust)
  - Focus: #A85C4C with 3px border
```

**Typography:**
- Font: Inter (but slightly warmer CSS adjustments for letter-spacing, line-height)
- Display/Splash: 32px, 700, deep charcoal
- Heading 1: 24px, 600, deep charcoal
- Body Large: 16px, 400, deep charcoal
- Body Regular: 14px, 400, warm gray
- Caption/Label: 12px, 500, warm gray

**Button Style:**
- Primary: Solid rust background, white text, 40px height, radius 8px (slightly more rounded for warmth), soft shadow
- Secondary: Warm light background, rust border 2px, rust text
- Tertiary: No background, rust text, warm underline on hover

**Card Style:**
- White background, subtle rust accent top-border (4px), radius 8px, 16px padding
- Hover: Warm shadow (slightly warm-tinted), no color change
- Semantic accent: Left border in sage/rust/golden per status

**Information Density:**
- Portfolio grid: 2-col (mobile), 3-col (tablet), 4-col (desktop)
- Design detail: 2-column (image left, info right, tablet+), slightly more generous padding (18px)
- Inventory table: Full-width, 36px row height (slightly more breathing room), alternating warm light backgrounds

**Motion (Material 3 Expressive):**
- LoadingIndicator: Rust-toned, smooth spin
- Button press: 120ms scale (1.03x) with subtle warmth shift
- Screen transitions: 350ms curve (slightly slower, more deliberate)
- Sync animation: Gentle progress bar in rust, no wave

**Visual Character:** Warm, artisan-forward, human-centered, tied to craft/heritage, inviting but professional

---

## DESIGN VERSION C: "MINIMAL SLATE"
### Theme: Contemporary minimalism + Swiss design influence

**Inspiration Sources:**
- Figma (contemporary minimal design tool)
- Stripe Dashboard (clean, functional, elegant)
- Material 3 Expressive (default modern aesthetic)
- Dribble minimal category (refined B2B tools)

**Primary Accent Color:** `#2563EB` (Vibrant True Blue)
- Rationale: Modern, tech-forward, ties to digital/reliability; high contrast for accessibility; clean
- Secondary Tint: `#3B82F6` (lighter blue for hover)
- Usage: Primary CTAs, focus states, active badges

**Color Palette:**
```
Base:
  - White: #FFFFFF (cards, backgrounds)
  - Slate Black: #0F172A (primary text, ultra-dark)
  - Slate Gray: #64748B (secondary text, neutral)
  - Light Slate: #F1F5F9 (alt backgrounds, cool tone)

Semantic:
  - Success/In Stock: #22C55E (bright green, high contrast)
  - Alert/Low: #EF4444 (bright red, high contrast)
  - Caution/Medium: #F59E0B (amber, warm contrast)
  - Neutral/Disabled: #CBD5E1 (slate gray)

Accent:
  - Primary: #2563EB (true blue)
  - Hover: #3B82F6 (lighter blue)
  - Focus: #2563EB with 2px border
```

**Typography:**
- Font: Inter (tight, minimal, contemporary)
- Letter-spacing: -0.5px (tight, modern look)
- Display/Splash: 32px, 700, slate black
- Heading 1: 24px, 600, slate black
- Body Large: 16px, 400, slate black
- Body Regular: 14px, 400, slate gray
- Caption/Label: 12px, 500, slate gray (no uppercase)

**Button Style:**
- Primary: Solid blue background, white text, 36px height, radius 4px (sharp, minimal), no shadow
- Secondary: White background, blue 1px border, blue text
- Tertiary: No background, blue text, no underline (text fades on hover)

**Card Style:**
- White background, 1px slate gray border, radius 4px (sharp corners, minimal), 16px padding
- Hover: 1px darker gray border, no shadow (elevation via border only)
- Semantic accent: Subtle left border (2px) in semantic color

**Information Density:**
- Portfolio grid: 2-col (mobile), 3-col (tablet), 4-col (desktop), compact 12px gaps
- Design detail: 2-column (tight layout), minimal padding (14px)
- Inventory table: Full-width, 28px row height (dense, scannable), minimal borders, no alternating backgrounds

**Motion (Material 3 Expressive):**
- LoadingIndicator: Blue, fast spin (180ms per rotation, feels responsive)
- Button press: 80ms scale (1.01x), snap back quickly
- Screen transitions: 250ms linear curve (modern, snappy, not smooth/organic)
- Sync animation: Linear progress bar in blue, no wave

**Visual Character:** Contemporary, minimal, tech-forward, high contrast, fast, Swiss grid-inspired, no warmth (purely functional)

---

## COMPARISON TABLE

| Aspect | Version A (Heritage Indigo) | Version B (Rust & Craft) | Version C (Minimal Slate) |
|--------|--------------------------|------------------------|------------------------|
| **Vibe** | Grounded, heritage, calm | Warm, artisan, human | Modern, minimal, fast |
| **Primary Color** | `#1E3A5F` Indigo | `#A85C4C` Rust | `#2563EB` Blue |
| **Warmth** | Cool (heritage tie) | Warm (craft tie) | Neutral (tech) |
| **Typography** | Spacious, readable | Warm spacing | Tight, minimal |
| **Buttons** | Rounded 6px | Rounded 8px | Sharp 4px |
| **Shadows** | Subtle elevation | Warm soft shadow | Border-based (no shadow) |
| **Info Density** | Moderate (comfort) | Moderate+ (breathing) | High (scannable) |
| **Motion Speed** | Calm (300ms) | Deliberate (350ms) | Fast (250ms) |
| **Best For** | Heritage brand, team morale | Artisan/craft narrative | Fast-paced operations |
| **Accessibility** | Good (indigo contrast) | Good (rust contrast) | Excellent (blue/white) |

---

## DESIGN RECOMMENDATIONS BY PERSONA

**Persona 1: Design Owner/Manager**
- Wants: Quick overview, beautiful presentation, heritage connection
- Recommendation: **Version B (Rust & Craft)** — warm, artisan-focused, ties brand story to UI

**Persona 2: Inventory Operator (Power User)**
- Wants: Information density, fast interactions, minimal friction
- Recommendation: **Version C (Minimal Slate)** — high contrast, snappy, dense data scanning

**Persona 3: Mixed (Design + Inventory)**
- Wants: Balance of beauty + speed + heritage
- Recommendation: **Version A (Heritage Indigo)** — professional, grounded, bridges aesthetics + functionality

---

## NEXT STEP: VISUAL MOCKUPS IN STITCH

I will now build all 3 versions in Stitch by Google with:
- ✅ All 14 screens per version
- ✅ Portfolio grid, design detail, inventory table, sync states, settings
- ✅ Exact color palettes, typography, spacing, buttons, cards
- ✅ Material 3 Expressive components (LoadingIndicator, transitions)
- ✅ Real data examples (Maroon Zig-Zag, Patti Black Kalamka, etc.)

You'll see:
1. **Side-by-side color palettes** (how colors appear in context)
2. **Portfolio screens** (grid layout, card hierarchy)
3. **Design detail screens** (image + info + inventory table)
4. **Sync state screens** (loading, success, error)
5. **Component variations** (buttons, inputs, badges, tables)

Then you can choose: A, B, C, or blend them.
