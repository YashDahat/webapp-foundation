# Design identity & personality (Phase 4)

Turn a generated site from "works but generic" into one that feels like **this specific business**:
customers should feel *at home* (warm, welcoming, beautiful, effortless), admins should find it
*fun and intuitive* to operate. The personality comes from the project's **architecture brief** —
not from your own taste, and not from a generic "modern SaaS" default.

## Step 1 — Read the brief, extract a direction
The project's full details live under **`docs/`** — read that directory as the source of truth
(the architecture brief plus any brand/identity/spec/feature notes alongside it). If `docs/` is
silent on look-and-feel, **ask the operator** before changing anything — do not invent a brand.

From the brief, pull a short, written direction (keep it in your working notes):
- **Audience & context** — who visits, on what device, in what mood.
- **Voice / tone** — e.g. warm & homely, premium & restrained, playful & bold, calm & clinical.
- **Feel adjectives** (3–5) — the words every design decision should serve.
- **Visual cues** — palette hints, imagery style, density, any do/don'ts the brief states.
Everything below must trace back to these. If a choice doesn't serve a feel-adjective, don't make it.

## The levers (what you may edit — and what you may NOT)

| Lever | File | Use it for |
|---|---|---|
| Color tokens | `frontend/src/index.css` (`:root` + `.dark`) | palette: `--primary`, `--accent`, `--background`, `--muted`, `--border`, `--ring`, `--radius` |
| Type / radii / motion | `frontend/tailwind.config.ts` | `fontFamily`, radius scale, `keyframes`/`animation` |
| Font files | `frontend/index.html` or an `@import` in `index.css` | load brand fonts (self-host or Google Fonts) |
| Brand content | `frontend/src/config/siteConfig.ts` | `brandName`, `tagline`, nav labels, `ctaButton` copy, footer, `socialLinks`; optional fixed brand-color classes |
| Page composition | generated public pages in `frontend/src/pages/*` | hero, section rhythm, imagery (via media/gallery), spacing, tasteful motion, microcopy |

**Do NOT edit** `frontend/src/shell/types.ts` (generated scaffold — "do not edit by hand") or any
fenced foundation component (`SiteHeader`/`SiteFooter`/`AdminDataGrid`/shell internals). Personality
reaches the shell through **`siteConfig.ts` props** and the **CSS tokens**, never by forking it.

**Keep the shadcn token NAMES intact.** Change the *values* of `--primary`, `--background`, etc.,
but never rename or remove them — every component reads them. Tune `:root` AND `.dark` together.

## Step 2 — Palette (index.css tokens)
- Derive a brand palette from the brief. Set `--primary`/`--primary-foreground`,
  `--accent`/`--accent-foreground`, `--background`/`--foreground`, `--muted`, `--card`, `--border`,
  `--ring` (focus), and `--destructive`. Values are HSL triples (`H S% L%`), no `hsl()` wrapper.
- Tune the `.dark` block to the same identity, not an afterthought — many users live in dark mode.
- **Contrast gate (hard):** every text/background and interactive pair must clear WCAG AA
  (4.5:1 body, 3:1 large text / UI). Check both themes. A pretty palette that fails contrast is a bug.

## Step 3 — Typography & shape
- Pick fonts that carry the voice (e.g. a characterful display for headings + a clean body face).
  Load them, wire `fontFamily` in `tailwind.config.ts`, apply a clear type scale (don't leave
  everything at default sizes/weights). Ensure a system fallback so text renders if a font fails.
- Set `--radius` to match the feel (soft/rounded = friendly & homely; tight = precise & premium).

## Step 4 — Customer-facing: make it feel "at home"
- **Hero / landing**: a genuine welcome — a warm headline in the brand voice, a real supporting
  image (use the media/gallery, not a gray box), a clear primary CTA. Not a bare H1 + button.
- **Rhythm & air**: generous, consistent whitespace and section spacing; let content breathe.
- **Imagery**: use the gallery/media library for authentic visuals; every image has alt text.
- **Voice everywhere**: the same warm tone across headings, body, buttons, **empty states**,
  **success toasts** (via `withToast(promise, '…')`), and the 404 page. No "lorem ipsum", no
  robotic "Operation successful". Make copy sound like a person from this business.
- **One signature moment**: a single tasteful, brand-true detail (a considered hover, a gentle
  reveal on scroll, a delightful empty state) — restraint over confetti.
- **Motion**: subtle and purposeful; respect `prefers-reduced-motion` (gate motion behind it).

## Step 5 — Admin-facing: make it fun + intuitive
Admins live here daily — optimize for *clarity and speed first*, then delight. Stay **within**
`AdminDataGrid` and the admin shell (never replace them).
- **Orientation**: clear page titles, obvious primary action, logical grouping; an operator should
  know what to do in 3 seconds.
- **Helpful empty states**: "No orders yet — they'll appear here as customers check out", not a
  blank grid. Guide the first action.
- **Friendly microcopy**: human labels, confirmations, and audit-friendly success messages.
- **Efficiency**: sensible defaults, keyboard-friendly flows, good grid density, scannable status
  color-coding (reuse semantic tokens so it stays theme-aware and accessible).
- **Cohesion**: admin shares the brand tokens so it feels part of the same product, while staying
  calmer and denser than the marketing pages. Dark mode must look deliberate here too.

## Step 6 — Accessibility bar (non-negotiable, applies to all of the above)
- WCAG AA contrast on every token pair, light AND dark.
- Visible, on-brand focus ring (`--ring`) on all interactive elements — never `outline: none`
  without a replacement.
- Semantic landmarks (`header`/`nav`/`main`/`footer`), ordered headings, labelled controls.
- `alt` on meaningful images; decorative images `alt=""`.
- Motion and auto-play respect `prefers-reduced-motion`.
- Target sizes comfortable on touch; nothing conveyed by color alone.

## Verify
In the live browser, walk the customer journey and the admin journey at mobile (375px) + desktop,
in light + dark. Confirm the feel-adjectives are legible in the result, re-check contrast, and
confirm no fenced file (shell scaffold / foundation components) was edited. List the levers you
changed in the Phase 7 report.
