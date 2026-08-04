---
name: Aetheris Clinical
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f4'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#45464d'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f0f1f1'
  outline: '#76777d'
  outline-variant: '#c6c6cd'
  surface-tint: '#565e74'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#131b2e'
  on-primary-container: '#7c839b'
  inverse-primary: '#bec6e0'
  secondary: '#505f76'
  on-secondary: '#ffffff'
  secondary-container: '#d0e1fb'
  on-secondary-container: '#54647a'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#191c1e'
  on-tertiary-container: '#818486'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2fd'
  primary-fixed-dim: '#bec6e0'
  on-primary-fixed: '#131b2e'
  on-primary-fixed-variant: '#3f465c'
  secondary-fixed: '#d3e4fe'
  secondary-fixed-dim: '#b7c8e1'
  on-secondary-fixed: '#0b1c30'
  on-secondary-fixed-variant: '#38485d'
  tertiary-fixed: '#e0e3e5'
  tertiary-fixed-dim: '#c4c7c9'
  on-tertiary-fixed: '#191c1e'
  on-tertiary-fixed-variant: '#444749'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 48px
    fontWeight: '800'
    lineHeight: '1.1'
    letterSpacing: -0.04em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
    letterSpacing: 0.01em
  body-md:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
    letterSpacing: 0.01em
  label-caps:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '700'
    lineHeight: '1'
    letterSpacing: 0.1em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  container-padding: 40px
  section-gap: 64px
  card-gap: 24px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 32px
---

## Brand & Style

This design system embodies a "future-clinical" aesthetic—a precise, high-end environment that balances the sterility of advanced technology with the warmth of premium hospitality. It is designed for high-stakes environments where clarity and data density must coexist with a sense of calm and order.

The visual narrative is driven by **Neo-Glassmorphism** and **Extreme Minimalism**. It utilizes translucent layers to manage depth without heavy shadows, relying on light refraction and 1px edge highlights to define structure. The emotional response is one of absolute confidence, serenity, and cutting-edge intelligence.

**Key Stylistic Pillars:**
- **Purity:** Stark white surfaces paired with subtle light-grey washes.
- **Transparency:** Multi-layered blurred backdrops that suggest depth and continuity.
- **Precision:** Perfect geometric alignment and generous, rhythmic whitespace.

## Colors

The palette is intentionally restrained to maintain a "clinical but warm" atmosphere. 

- **Primary & Neutral:** We use a high-contrast relationship between a deep Slate/Black (`#0F172A`) for typography and absolute White (`#FFFFFF`) for base surfaces.
- **Translucency:** Surfaces utilize a 60% opacity white overlay with a `24px` backdrop blur. This creates the "glass" effect that defines the system.
- **Highlights:** 1px solid borders using `glass_stroke` are applied to the top and left edges of cards to simulate light catching on glass.
- **Functional Accents:** A single clinical blue (`#38BDF8`) is reserved for critical status indicators or primary action highlights, used sparingly to maintain the monochrome prestige.

## Typography

Typography is used as a structural element. By utilizing **Manrope**, we achieve a modern, geometric feel that remains highly legible.

- **Headlines:** Set with aggressive negative letter spacing to feel "locked" and authoritative.
- **Body Text:** Increased line height (1.6x) and slight tracking (0.01em) to ensure "airiness" and reduce cognitive load during long reading sessions.
- **Metadata:** Small caps with generous tracking (0.1em) are used for labels and secondary data points to create a technical, "instrumentation" look.

## Layout & Spacing

This design system employs a **Fixed Grid** philosophy with extreme internal margins. 

- **The Macro Layout:** Content is centered in a 1440px container with 40px outer safe zones.
- **The Rhythm:** We use a base-8 spacing scale, but emphasize the larger increments (32px, 64px) to create the "extreme whitespace" requested. 
- **The Breath:** Every major section must be separated by at least 64px. Elements within cards should never be closer than 24px to the card edge, ensuring the content feels like it is "floating" within its container.

## Elevation & Depth

Depth is conveyed through **Z-axis Layering** rather than traditional drop shadows.

- **Level 0 (Base):** A subtle gradient or a high-quality environmental background image.
- **Level 1 (Main Canvas):** A large, 32px rounded container with a deep backdrop blur (40px) and a semi-transparent white fill (80% opacity).
- **Level 2 (Interactive Elements):** Cards and buttons sit on Level 1. They use 1px "inner-glow" borders (`rgba(255,255,255,0.5)`) and ultra-soft, low-opacity shadows (`rgba(0,0,0,0.02)`) to lift slightly from the canvas.
- **Interactions:** Upon hover, elements should increase their backdrop blur intensity rather than increasing shadow spread.

## Shapes

The shape language is characterized by **Hyper-Smooth Geometry**. 

- **Large Containers:** Cards and primary dashboard modules use a 32px radius. This "squircle-adjacent" roundness softens the clinical palette and feels friendlier to the touch.
- **Small Elements:** Buttons and tags use a 14px radius, providing enough curvature to feel consistent with the cards without appearing fully "pill-shaped," maintaining a professional edge.
- **Consistency:** All nested elements must follow a "concentric radius" rule where inner radii are smaller than outer radii to maintain visual harmony.

## Components

### Buttons
- **Primary:** Solid `#0F172A` with white text. No shadow, 14px radius.
- **Glass:** 20% white overlay, 20px blur, 1px white stroke. 
- **Interaction:** Smooth 200ms transition. On hover, glass buttons increase opacity to 30%.

### Cards
- **Structure:** 32px padding, 32px radius.
- **Header:** Labels in `label-caps` style, secondary data in `body-md`.
- **Edge Work:** 1px stroke on top/left to define the light source.

### Input Fields
- **Style:** Subtle light-grey fill (`#F1F5F9`) with no border. 
- **Focus:** 1px stroke of `accent_clinical` and a 4px soft outer glow in the same color.

### Chips/Status
- **Clinical Tags:** Small, all-caps text with a 4px dot indicator. 
- **Backgrounds:** Very pale versions of status colors (e.g., 10% opacity green for "Active").

### Data Visualization
- **Line Charts:** Ultra-thin 1.5pt lines with soft gradient fills below the path.
- **Gauges:** Minimalist, thin-stroke rings with large center-aligned `display-lg` numbers.