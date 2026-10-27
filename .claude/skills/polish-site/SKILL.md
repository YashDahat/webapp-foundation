---
name: polish-site
description: >-
  Polish a generated client site (foundation + generated business domain) into a
  production-ready, tested website. Use when asked to "polish", "finish",
  "productionize", "make it production ready", "test the site", or "get it ready to
  ship". Drives a fix-until-green loop across build/lint/type gates, foundation-contract
  conformance, a brand-personality design pass driven by the project's architecture brief,
  live-browser + Playwright E2E of the core journeys, UX/visual polish, and production hardening.
  Also use when asked to make the site "beautiful", give it "personality"/"brand"/"identity",
  make customers "feel at home", or make the admin "fun"/"intuitive". Auto-fixes issues and
  re-runs gates until clean; never edits fenced foundation code without syncing the contract cards.
---

# polish-site

Take a project that was generated on top of `webapp-foundation` (the fenced spine + an
LLM-generated business domain layered on top) and drive it to **production-ready**:
everything compiles and lints, the generated code obeys the foundation contracts, the
core user journeys work end-to-end in a real browser, and the obvious production gaps
(secrets, SEO, a11y, empty/error states, performance) are closed.

Operate as an **auto-fix loop**: run a gate → fix what fails → re-run that gate until it
is green, then advance. Report everything you changed at the end.

**`docs/` is the per-project source of truth.** It holds the architecture brief plus the
business/feature/test specs for this project. Consult it throughout — it drives the design
personality (Phase 4), informs which journeys matter (Phase 3), and clarifies intended domain
behavior (Phase 2) — not just any one phase.

## Non-negotiable guardrails (read first)

The project = **fenced foundation code** (immutable spine) + **generated domain code** (yours to polish).

- **Never reimplement or hand-edit fenced foundation surfaces** to "fix" something — auth,
  payments, media/gallery, cart, checkout, the order spine (`OrderService`, `WorkflowEngine`,
  `StageActionDispatcher`, connector SPIs), catalog/inventory, branches/address/feedback, the
  app shell, shared exceptions. If a bug is truly in the foundation, flag it in the report —
  do not patch it.
- **Same-commit contract sync.** If you *do* change any fenced module's public surface (a
  class/DTO/entity field/enum/endpoint/hook/component-prop/exported fn in auth, user, payment,
  media-gallery, cart, checkout, shell, shared-exceptions), you MUST update the matching entry
  in `backend/FOUNDATION_CONTRACT.md` / `frontend/FOUNDATION_CONTRACT.md` **and**
  `foundation.manifest.json` in the same change. See the project `CLAUDE.md`.
- **Fix at the source.** Make generated domain code *conform to* the foundation (import and use
  the fenced services/components), rather than bending the foundation to the domain.
- **No double-toasts.** Failed API requests already `toast.error` via the `apiClient`
  interceptor — never add a manual error toast after a caught request error.

## The loop — phases

Run phases in order. Within a phase, loop until its exit criterion is met before advancing.
Keep a running task list (TaskCreate/TaskUpdate) of findings and fixes.

### Phase 0 — Preflight (boot the stack)
Goal: a running, production-like stack to test against.
- Run `.claude/skills/polish-site/scripts/preflight.sh` — it copies `.env.example`→`.env` if
  missing, `docker compose up -d --build`, and waits for the app on **http://localhost:8080**
  (single origin: backend serves the built frontend; postgres/minio/mailpit alongside).
- Confirm health before proceeding. If boot fails, that's your first fix (usually env or a
  backend compile error → jump to Phase 1).

### Phase 1 — Static gates (compile / type / lint / build)
Goal: zero errors from every gate. Run `.claude/skills/polish-site/scripts/gates.sh`, or:
- Frontend (`cd frontend`): `npm ci` (first run) · `npm run typecheck` · `npm run lint` · `npm run build`
- Backend (`cd backend`): `mvn -q verify`
Fix failures in **generated** code and re-run until all green. Type errors against foundation
types almost always mean the generated code is using a fenced API wrong — fix the usage.

### Phase 2 — Foundation-contract conformance (the differentiator)
Goal: generated domain code obeys every foundation rule. Audit against
`references/foundation-conformance.md` (checklist derived from `CLAUDE.md` + the contract cards).
High-value checks: admin tables use `AdminDataGrid`; every admin entity has a `BulkUploadDialog`
+ `POST /api/v1/admin/<resource>/bulk-upload`; privileged actions call `AuditService.record`;
sellables go through `CatalogItemResolver`; stock only via `InventoryService`; orders/checkout
only via the fenced order spine + connectors; addresses snapshotted onto the order. Fix
violations by rewiring to the fenced API.

### Phase 3 — Functional E2E (live browser + persisted Playwright specs)
Goal: the core journeys actually work, and stay working.
- First **drive a live browser** through the journeys in `references/test-flows.md` (auth →
  catalog → cart → checkout → admin CRUD + bulk upload). `uiverify.mjs` in `frontend/` is a
  working reference for the Playwright-driver + `data-testid` conventions (`login-identifier`,
  `login-password`, `login-submit`, …).
- Then **author reusable specs** in `frontend/e2e/<feature>.spec.ts` (Playwright is already
  configured: `testDir: ./e2e`, `baseURL` from `$BASE_URL`, default `:8080`). Run with
  `cd frontend && BASE_URL=http://localhost:8080 npx playwright test`.
- Prefer `data-testid` / ARIA roles over brittle text selectors. Fix whatever the walkthrough
  breaks; re-run until specs pass.

### Phase 4 — Design identity & personality (the creative pass)
Goal: the site stops looking like a template and feels like *this* business — warm and
welcoming for customers ("at home"), fun and effortless for admins — with a personality drawn
from the project's **architecture brief**. Full how-to + lever map in `references/design-identity.md`.
- **Read the brief first.** The project's full details live under **`docs/`** — read it (the
  architecture brief + any brand/identity/spec notes there) as the source of truth. Extract:
  audience, tone/voice, feel-adjectives, visual direction, do/don'ts. If `docs/` says nothing
  about look-and-feel, **ask the operator** — never invent a brand.
- **Express it through the real levers** (never the fenced shell scaffold `shell/types.ts`):
  design tokens in `src/index.css` (`:root` + `.dark`), `tailwind.config.ts` (fonts/radii/
  animation), `src/config/siteConfig.ts` (brand name, tagline, nav + CTA copy, socials), and the
  generated public pages (hero, imagery via the media/gallery, spacing, tasteful motion, voice).
- **Customer feel = "at home"**: a welcoming hero, generous whitespace, a consistent warm voice
  across pages + empty states + success toasts, and one signature moment. **Admin feel = fun +
  intuitive**: clear hierarchy, helpful empty states, friendly microcopy, sensible defaults,
  keyboard-friendly — expressed *within* `AdminDataGrid`/the shell, never by replacing them.
- **Accessibility is part of the creative bar, not a trade-off**: WCAG AA contrast on every
  token pair (light AND dark), visible focus, `prefers-reduced-motion` respected, semantic
  landmarks + alt text. Keep the shadcn token NAMES intact — components depend on them.
Verify visually in the live browser at mobile + desktop widths and in both themes.

### Phase 5 — UX states & responsiveness
Goal: it looks finished at every state and size. Work `references/production-checklist.md` → UX
section: responsive layout, loading skeletons, **empty states**, error states, consistent
spacing/theme (light + dark), broken images/links, keyboard focus. Verify in the live browser.

### Phase 6 — Production hardening
Goal: no obvious production gap. `references/production-checklist.md` → Production section:
no secrets/placeholders committed, real env documented, SEO/meta per route (`react-helmet-async`
is available), a11y basics, no console errors, reasonable bundle size, sensible error boundaries.

### Phase 7 — Report
Summarize: gates status, conformance fixes, design-identity work (brief → levers changed), E2E
specs added + pass/fail, UX/production changes, and anything you **deliberately left** (e.g. a
suspected foundation bug, or a decision needing the owner). Be honest about what still fails.

## Stop criteria
Done when: all static gates green · conformance checklist clean (or deviations justified in the
report) · the design expresses the brief's personality for both customer and admin, at WCAG AA
contrast in light and dark · all authored E2E specs pass against the running stack · UX +
production checklists addressed. If a gate cannot go green without editing fenced code, **stop and
report** rather than breaching the guardrails.

## References (load as needed)
- `references/foundation-conformance.md` — auditable rule checklist (Phase 2)
- `references/test-flows.md` — canonical journeys to exercise + spec-authoring notes (Phase 3)
- `references/design-identity.md` — brief → design levers, "at home" + admin-delight + a11y (Phase 4)
- `references/production-checklist.md` — UX + production gates (Phases 5–6)
