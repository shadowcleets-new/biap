# BIAP B2B Internal Operations — Design System Brief

## 1. APP PURPOSE & CONTEXT
**Target Users:** Internal operations team for ethnic wear brand (Salwar Kameez, Kurtis)
**Key Tasks:** Manage designs, inventory (sizes/stock/pricing), karigars (tailors), sync with cloud
**Environment:** Android tablet/phone, but optimized for efficient workflows
**Philosophy:** Information-dense, scannable, professional, no AI slop

---

## 2. USER FLOWS

### Flow A: Authentication
**Path:** Splash → Sign-in → Portfolio (on success)

### Flow B: Portfolio & Discovery
**Path:** Portfolio Grid → Search/Filter → Design Detail

### Flow C: Design Management
**Path:** Design Detail → Edit Design → Save → Sync to Cloud

### Flow D: Inventory Management
**Path:** Design Detail → Configure Sizes/Stock/Pricing → Save

### Flow E: Sync & Cloud State
**Path:** Any screen → Sync Button → Loading → Completion → Status feedback

### Flow F: Add New Design
**Path:** + Button → Upload Image → Basic Details → Inventory Config → Save

---

## 3. SCREEN INVENTORY (What we need to design)

| # | Screen | Purpose | Key Data |
|---|--------|---------|----------|
| 1 | Splash | App launch, branding | Logo, loading state |
| 2 | Sign-in | Auth via Google | Form, error handling |
| 3 | Portfolio Grid | Browse all designs | Cards: image, name, category, price range, stock count |
| 4 | Portfolio Search/Filter | Find designs by name/fabric/color/size | Search bar, filter chips, results grid |
| 5 | Design Detail — View | Display design info | Image, name, category, pricing table (per size), stock table, karigars, sync status |
| 6 | Design Detail — Edit | Modify design metadata | Name, category, description (optional) |
| 7 | Inventory Config | Manage sizes & pricing per size | Size selector, stock input, price input, visual hierarchy |
| 8 | Add Design | New design workflow | Image upload, name, category, then inventory setup |
| 9 | Sync State — Loading | During sync | Spinner, "Syncing..." text, cancel option |
| 10 | Sync State — Success | After successful sync | Checkmark, timestamp, "Synced X designs" |
| 11 | Sync State — Error | Sync failed | Error icon, message, retry button |
| 12 | Settings/Profile | Account info, app settings | User name, email, app version, clear cache, sign out |
| 13 | Empty State | No designs exist | Illustration, "Create your first design" CTA |
| 14 | Error State | Generic error | Icon, message, retry button |

---

## 4. DESIGN SYSTEM

### 4.1 Color Palette

**Primary Palette:**
- **Base White:** `#FFFFFF` (backgrounds, cards)
- **Base Black:** `#1A1A1A` (text, dark elements)
- **Neutral Gray:** `#6B7280` (secondary text, dividers, disabled)
- **Light Gray:** `#F3F4F6` (backgrounds, inactive states)
- **Dark Gray:** `#374151` (hover states, emphasis)

**Accent Color (Single, Strategic):**
- **Teal/Forest Green:** `#0D7377` or `#1B4D45` (primary actions, focus, badges)
- Rationale: Professional, ties to textile/heritage, not trendy

**Semantic Colors:**
- **Success/In Stock:** `#059669` (green, inventory available)
- **Alert/Low Stock:** `#DC2626` (red, immediate attention)
- **Action/Primary CTA:** `#0D7377` (teal, draws eye to important actions)
- **Caution/Medium Stock:** `#F59E0B` (amber, requires attention)
- **Neutral/Disabled:** `#D1D5DB` (light gray, inactive)

**No gradients. Solid colors only.**

### 4.2 Typography

**Font Family:** Inter or similar professional sans-serif (Material 3 Expressive uses this)
**Hierarchy:**
- **Display/Hero:** 32px, weight 700, color: Black (splash, page titles)
- **Heading 1 (Page Title):** 24px, weight 600, color: Black
- **Heading 2 (Section Title):** 18px, weight 600, color: Black
- **Body Large (Primary Text):** 16px, weight 400, color: Black
- **Body Regular (Secondary Text):** 14px, weight 400, color: Neutral Gray
- **Label/Caption (Tertiary):** 12px, weight 500, color: Neutral Gray
- **Button Text:** 14px, weight 600, color: White (on dark) or Teal (on light)

### 4.3 Spacing & Grid

**8px base grid**
- Micro: 4px (icon padding, small gaps)
- Compact: 8px (padding inside small components)
- Regular: 16px (standard padding, gaps between elements)
- Comfortable: 24px (section spacing)
- Generous: 32px (major section breaks)

**Responsive Breakpoints:**
- Mobile: 0–600px (single column, full-width cards)
- Tablet: 600px+ (2 column grid)
- Desktop: 900px+ (3+ column grid)

### 4.4 Components & States

**Buttons:**
- **Primary CTA:** Teal background, white text, 16px height, 8px horizontal padding, radius 6px
- **Secondary:** White background, teal border, teal text, 16px height
- **Tertiary:** No background, teal text (text-only button)
- **Disabled:** Gray background, gray text, no interaction
- **States:** Default, Hover (darker teal), Active (pressed), Disabled

**Cards:**
- **Background:** White
- **Border:** 1px light gray or subtle shadow
- **Radius:** 8px
- **Padding:** 16px
- **Hover:** Slight shadow increase (elevation), no color change

**Inputs:**
- **Border:** 1px gray (`#D1D5DB`)
- **Focus:** 2px teal border, no outline
- **Placeholder:** Neutral gray text
- **Height:** 40px
- **Padding:** 12px horizontal, 8px vertical
- **Radius:** 6px

**Tables (Inventory Grid):**
- **Header:** Light gray background (`#F3F4F6`), bold text, left-aligned
- **Rows:** White background, alternating light gray on every other row (for scannability)
- **Borders:** Subtle gray dividers between rows/columns
- **Padding:** 12px per cell
- **Font:** 14px body regular

**Loading State:**
- **Indicator:** Teal circular spinner (Material 3 Expressive LoadingIndicator)
- **Text:** "Syncing..." or "Loading..." below spinner
- **Position:** Centered or in-place

**Badges/Chips:**
- **Style:** Small pill (height 24px, radius 12px), semantic color
- **Example:** Green badge for "In Stock (23)", Red for "Low (2)"
- **Font:** 12px bold, white text

---

## 5. LAYOUT PRINCIPLES (B2B/Internal Ops)

1. **Information First:** No wasted space, but organized clearly
2. **Scannability:** Clear visual hierarchy, use of white space strategically
3. **Density:** Show more in less space (tables, compact cards) for power users
4. **Actions on Top/Right:** Primary CTAs visible without scrolling (top bar or floating button)
5. **Status Always Visible:** Sync status, errors, inventory levels at a glance
6. **Keyboard-Friendly:** Logical tab order, shortcuts for power users
7. **Semantic Color Usage:** Green=good, Red=alert, Blue=action (not decorative)
8. **Consistency:** Same button style everywhere, input behavior predictable

---

## 6. SCREEN-BY-SCREEN LAYOUT SPECS

### Screen 1: Splash
**Layout:** Center-aligned
- Logo (120x120px, center)
- App name "BIAP" below logo (32px, bold)
- Loading indicator below (optional, if splash > 2s)
- No gradient, solid white/light gray background

### Screen 2: Sign-in
**Layout:** Centered form
- Logo (80x80px, top-center)
- "Sign in to BIAP" heading (24px)
- Google Sign-in button (full-width, 48px height, teal)
- Error message (if auth fails, red text)
- Padding: 24px horizontal, 32px top/bottom

### Screen 3: Portfolio Grid
**Layout:** Full-screen with top bar
- **Top Bar:** "Product Portfolio" heading (left), "Cloud Sync" button (right, teal), menu icon (right)
- **Search Bar:** Full-width below heading, placeholder "Search by name, fabric, color, size..."
- **Grid:** 2 columns (mobile), 3 columns (tablet+), cards 160x200px
- **Card:** Image (top), name (14px), category (12px, gray), price range (14px, bold), stock count (small badge)
- **Spacing:** 16px between cards, 16px padding on sides

### Screen 4: Portfolio Search/Filter
**Layout:** Same as Screen 3, but:
- **Filter Bar:** Below search, horizontally scrollable chips: Category, Size, Stock Status, Price Range
- **Results Count:** "12 designs found" (12px gray text)
- **Empty State:** If no results, show icon + "No designs match" + clear filters button

### Screen 5: Design Detail — View
**Layout:** Top-to-bottom scrollable
- **Header:** Image (full-width, 280px height, rounded corners 8px)
- **Title Bar:** Design name (24px), category (12px gray), sync status (small green/red badge)
- **Section 1 — Basic Info:**
  - Name: "Maroon Zig-Zag Patiala"
  - Category: "Salwar Kameez"
  - Karigars: "Rajesh (₹450/hr), Priya (₹500/hr)" (clickable chips)
- **Section 2 — Pricing Table:**
  - Columns: Size, Stock, Price, Last Updated
  - Rows: S (5, ₹850), M (10, ₹950), L (3, ₹1050), XL (0, ₹1150), etc.
  - Semantic coloring: Green for in-stock, red for 0
- **Edit Button:** Floating action button (teal, bottom-right) or top-right button "Edit"
- **Delete Button:** Menu icon → Delete (red, confirmation dialog)

### Screen 6: Design Detail — Edit
**Layout:** Form-based
- Same as Screen 5 view, but fields are editable:
  - Name input (text field)
  - Category dropdown
  - Karigars multi-select
  - Save button (top-right or bottom-right, teal, full-width on mobile)
  - Cancel button (secondary, left of Save)

### Screen 7: Inventory Config
**Layout:** Tab-based or scrollable form
- **Section — Size Selection:**
  - Grid of size chips: S, M, L, XL, XXL, Custom
  - Selection state: highlighted with teal border
- **Section — Stock & Pricing (for selected size):**
  - Stock input: spinner or text field, right-aligned number
  - Price input: text field, ₹ prefix, right-aligned number
  - Last updated: gray text, read-only
- **Visual Feedback:** Inventory table below shows summary: Size | Stock | Price
- **Save Button:** Bottom-right or footer-sticky, teal, full-width

### Screen 8: Add Design
**Layout:** Multi-step workflow (accordion or sequential screens)
- **Step 1 — Image Upload:**
  - Camera icon + "Take photo" or "Choose from gallery"
  - Preview (280x280px, centered)
  - Next button (teal)
- **Step 2 — Basic Details:**
  - Name input, Category dropdown, Description input (optional)
  - Next button
- **Step 3 — Inventory Setup:**
  - Same as Screen 7
  - Save button (teal, full-width)

### Screen 9: Sync State — Loading
**Layout:** Overlay or full-screen
- **Center:** LoadingIndicator (Material 3 Expressive, teal, 48px)
- **Below:** "Syncing..." (16px, bold)
- **Below:** "Last synced: 2 hours ago" (12px gray)
- **Bottom:** Cancel button (secondary)
- **No interaction:** Grayed out background, no action possible

### Screen 10: Sync State — Success
**Layout:** Overlay or toast notification
- **Icon:** Green checkmark (Material 3, 48px)
- **Text:** "Synced successfully" (16px, bold)
- **Subtext:** "Updated 5 designs • 2m ago" (12px gray)
- **Auto-dismiss:** After 3s, fade out
- **OR:** Sticky notification (top of screen, green background, white text)

### Screen 11: Sync State — Error
**Layout:** Overlay or toast notification
- **Icon:** Red alert icon (48px)
- **Text:** "Sync failed" (16px, bold)
- **Subtext:** "Network error. Please retry." (12px gray)
- **Button:** "Retry" (teal, 40px height)
- **Persistent:** Does not auto-dismiss, requires action or dismiss

### Screen 12: Settings/Profile
**Layout:** Vertical list
- **Section 1 — Account:**
  - User name: "Pooja Sharma" (16px)
  - Email: "pooja@brand.com" (12px gray)
- **Section 2 — App:**
  - Version: "1.0" (12px gray)
  - Storage: "Cache: 45MB" with "Clear cache" button (secondary)
- **Section 3 — Actions:**
  - Sign out button (red text, secondary style)

### Screen 13: Empty State
**Layout:** Center-aligned
- **Icon:** Folder/design icon (96px, gray)
- **Heading:** "No designs yet" (24px)
- **Text:** "Create your first design to get started" (14px gray)
- **Button:** "+ Add Design" (teal, primary, 48px height)

### Screen 14: Error State
**Layout:** Center-aligned
- **Icon:** Alert/broken icon (96px, red)
- **Heading:** "Something went wrong" (24px)
- **Text:** "Unable to load designs. Please try again." (14px gray)
- **Button:** "Retry" (teal, 48px height)

---

## 7. MATERIAL 3 EXPRESSIVE USAGE

- **Theme:** MaterialExpressiveTheme (app-wide)
- **Motion Scheme:** MotionScheme.expressive() (smooth transitions, not overdone)
- **Components:**
  - **LoadingIndicator:** On Screen 9 (sync loading)
  - **LinearWavyProgressIndicator:** On Screen 5/6 when editing (optional subtle feedback)
  - **Emphasized Typography:** Page titles use titleLargeEmphasized
  - **Transitions:** Screen enter/exit use smooth spring animations (not jarring)
  - **Button Press:** Subtle scale + ripple on primary CTA
- **No overuse:** Motion enhances, doesn't distract (internal ops users need speed)

---

## 8. DESIGN TOKENS SUMMARY

| Token | Value | Usage |
|-------|-------|-------|
| `color_primary` | `#0D7377` | CTAs, focus states, badges |
| `color_success` | `#059669` | In-stock, positive feedback |
| `color_alert` | `#DC2626` | Low stock, errors |
| `color_caution` | `#F59E0B` | Medium attention |
| `color_bg_default` | `#FFFFFF` | Cards, content areas |
| `color_bg_alt` | `#F3F4F6` | Alternate rows, headers |
| `color_text_primary` | `#1A1A1A` | Body text |
| `color_text_secondary` | `#6B7280` | Secondary/gray text |
| `spacing_xs` | 4px | Micro gaps |
| `spacing_sm` | 8px | Compact padding |
| `spacing_md` | 16px | Standard spacing |
| `spacing_lg` | 24px | Generous spacing |
| `spacing_xl` | 32px | Section breaks |
| `radius_sm` | 6px | Inputs, small components |
| `radius_md` | 8px | Cards, buttons |
| `radius_full` | 12px | Pills, badges |
| `typography_display` | 32px, 700, black | Splash, hero |
| `typography_heading1` | 24px, 600, black | Page titles |
| `typography_body` | 16px, 400, black | Primary text |
| `typography_body_small` | 14px, 400, gray | Secondary text |
| `typography_caption` | 12px, 500, gray | Tertiary/labels |

---

## 9. ANTI-PATTERNS (WHAT TO AVOID)

❌ Purple gradients  
❌ Decorative animations (buttons bouncing, confetti)  
❌ Cutesy illustrations (this is B2B, not D2C)  
❌ Too much whitespace (internal ops users expect density)  
❌ Inconsistent button styles (same action = same button everywhere)  
❌ Hidden affordances (icons that don't clearly signal action)  
❌ Non-semantic color use (red for non-alert, green for non-success)  
❌ Slow interactions (every click should feel responsive, < 200ms feedback)  
❌ Missing error states (every action must have error handling)  
❌ AI Slop (generic, lifeless, uninspired design)

---

## 10. NEXT STEPS

1. **Approval:** Review this brief, confirm all 14 screens, colors, typography
2. **Stitch Design:** Build each screen in Stitch by Google per this spec
3. **Implementation:** Code follows design exactly (Compose, Material 3 Expressive)
4. **Testing:** On-device validation (tablet/phone, sync workflows, error states)

---

**Design Lead:** Contemporary Heritage Brand (Ethnic Wear B2B)  
**Platform:** Android (Jetpack Compose)  
**Design System:** Material 3 Expressive (professional, motion-forward)  
**Philosophy:** Information density + clarity + speed + no AI slop
