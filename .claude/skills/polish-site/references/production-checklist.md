# Production-readiness checklist (Phases 5–6)

Verify each item in the running stack (http://localhost:8080). Fix generated code; leave fenced
code alone. Check items off as you confirm them.

## UX / visual polish (Phase 5)
- [ ] **Responsive**: key pages usable at mobile (375px), tablet, desktop widths — no overflow,
      no clipped controls.
- [ ] **Loading states**: lists/detail pages show skeletons or spinners while fetching, not a
      blank flash or layout jump.
- [ ] **Empty states**: every list (products, orders, cart, admin tables, gallery) has a
      deliberate empty state with guidance, not a bare empty grid.
- [ ] **Error states**: failed loads show a retriable error UI (not a white screen).
- [ ] **Theme**: light AND dark both legible; contrast is adequate; no hardcoded colors fighting
      the theme tokens.
- [ ] **Consistency**: spacing, typography, button variants consistent with the foundation shadcn
      components; no stray inline styles where a component exists.
- [ ] **Media**: no broken images; gallery/thumbnails load; images have sensible sizing.
- [ ] **Navigation**: all nav links resolve; 404 route renders `NotFoundPage`; back/forward work.
- [ ] **Forms**: inline validation messages; disabled/submitting states; no silent failures.

## Production hardening (Phase 6)
- [ ] **No secrets committed**: no real keys in the repo; `.env` is git-ignored; `.env.example`
      lists every required var with safe placeholders (Razorpay, JWT_SECRET, SMTP, S3…).
- [ ] **Config documented**: any new env var the generated domain needs is in `.env.example`
      (the pipeline's EnvVarScanner appends, but confirm).
- [ ] **SEO / meta**: each public route sets title + description via `react-helmet-async`;
      `index.html` has sane defaults; social/OG tags where relevant.
- [ ] **Accessibility**: images have alt text; interactive elements are reachable by keyboard and
      have accessible names; headings are ordered; forms have labels.
- [ ] **No console noise**: no uncaught errors or React warnings in the browser console during the
      core journeys.
- [ ] **Error boundaries**: a thrown render error doesn't blank the whole app.
- [ ] **Performance**: production `vite build` succeeds; bundle has no accidental huge deps;
      images aren't multi-MB; no obvious N+1 in hot admin lists.
- [ ] **Auth gating**: `/admin/**` is ADMIN-only; protected pages redirect unauthenticated users;
      no admin-only data leaks to public endpoints.
- [ ] **Data integrity**: seeded/demo data is reasonable or clearly flagged; no placeholder
      "lorem ipsum" or "TODO" strings shipped in user-facing copy.
