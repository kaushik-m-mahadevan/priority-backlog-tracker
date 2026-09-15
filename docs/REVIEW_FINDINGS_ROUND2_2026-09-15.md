# Priority Backlog Tracker / Order Tracker — round 2 review (2026-09-15)

Scope: re-verify every item round 1 (`REVIEW_FINDINGS_2026-09-14.md`) flagged as unfixed,
against the *current* code — not the old writeup — plus a fresh look at everything Order
Tracker gained since then (tools/materials split, assembly & packaging instructions, notes,
estimate-vs-real labeling, contributor timelines, bulk-order envelope editing, and tonight's
collapsible-section layout overhaul).

The headline: nearly every item round 1 flagged was **already fixed** by the time this round
started, in a remediation pass that happened between the two reviews. This round re-verified
each one against the real current code (not just trusted the old doc), found two genuine gaps
still open, fixed both, and adds an honest list of what's still out of scope.

---

## Re-checked from round 1 — confirmed fixed

Every one of these was re-verified by reading the current source, not assumed from the old
report:

- **Security — encryption key startup guard.** `ProdSanityCheck.java` now checks
  `encryption.key()` against the dev default alongside the JWT secret check.
- **Security — IDOR on `GET /api/groups/{id}/categories`.** `GroupCategoryController.get()`
  now passes `actor.id()` through to `categoriesFor(id, userId)`, which calls
  `groupService.requireMember()` before returning anything.
- **Money — `BusinessConfig` record-embedded-in-document crashes** (`isTool`, `splitTracked`)
  and the **`User.handle` sparse-index latent bug** — both fixed with regression tests, per
  round 1's own account (already fixed *during* that round).
- **Money — cost-config approval race.** `CostConfigChangeRequest` now carries `@Version`;
  `CostConfigChangeService` catches `OptimisticLockingFailureException` and retries.
- **Money — orphaned cost-config request when the last approver leaves.** `GroupService.leave()`
  publishes `MemberLeftGroupEvent`; `CostConfigChangeService` listens for it and re-evaluates
  unanimity among the remaining members.
- **Money — bulk stage-progress not reclamped after a quantity shrink.** Both the batch-tracked
  path (`updateBulkDetails`, `sp.setUnitsCompleted(Math.min(...))`) and the per-creator
  split-tracked path (`mergeProgress`) now clamp `unitsCompleted` down to the new ceiling.
- **Maintainability — split-allocation-sum validated only on the frontend.**
  `OrderBusinessRules.validateVariant()` now runs the same check server-side on both
  `create()` and `updateBulkDetails()`, independent of whatever the frontend sent.
- **QA — `OrderService.myWork()` had zero test coverage.** `MyWorkApiTest.java` now exists.
- **Accessibility (screen-reader) — no `<label>` was ever associated with its control.** Every
  form field checked across `NewOrderPage.tsx`, `OrderDetailPage.tsx`, `OrderFormFields.tsx`,
  `AddToGroupModal.tsx` now uses `htmlFor`/`id` pairs.
- **Accessibility (screen-reader) — repeated "Remove" buttons had no distinguishing text.**
  Every remove button in `OrderFormFields.tsx` now carries an `aria-label` naming the item type
  and entry number (or add-on name).
- **Accessibility — split-mismatch hint was color-only and had no visual effect where used.**
  `.form-row .hint` now exists as a base rule (not just `.hint.bad`), and the mismatch `<p>` in
  both `NewOrderPage.tsx` and `OrderDetailPage.tsx` is correctly nested inside a `.form-row`.
- **Mobile — `.form-grid` never collapsed to one column.** It's now mobile-first (`1fr` base,
  `1fr 1fr` only above the breakpoint) — the correct fix, not just a workaround.
- **First-time-user — zero mandatory-item-types rendered a silent blank section.**
  `MandatoryItemsFields`/`ToolsFields` now show an explicit empty state linking to Business
  Settings.
- **Maintainability — `AddToGroupModal` treated a failed existing-link check the same as
  "confirmed unlinked."** The `.catch()` now sets a real error, and the create-new-item form is
  gated on `!error`, so a failed check no longer silently offers to create a duplicate.

Fifteen of round 1's seventeen numbered findings turned out to already be closed. The old
report predates the remediation; treat it as historical from here, not as a live backlog.

## Fixed this round

**Modal had no focus management or Escape-to-close, unlike the pattern already established
elsewhere in this codebase.** `AddToGroupModal` was the one round-1 accessibility item that
was *not* already fixed. Added `role="dialog"`/`aria-modal="true"`, `autoFocus` on the group
select, and an `onKeyDown` handler closing on Escape — mirroring `ManageBusinessPage.tsx`'s
inline rename form, which already had this exact pattern. Verified live: opening the modal
moves focus to the group `<select>`, and Escape closes it.

**`validateSplits` (the frontend half of the split-integrity guard) had zero test coverage.**
Added `OrderFormFields.test.ts` — 8 cases covering the pass path, both sum-mismatch directions,
duplicate-creator detection, unassigned-row handling, blank-label skip, and multi-variant
checking. All pass against the real function, no mocking needed since it's pure logic.

---

## New findings from tonight's feature work

A fresh look at everything added this session (tools split, assembly & packaging, notes,
estimate labeling, contributor timelines, bulk envelope editing, collapsible sections) found
no new correctness or security issues — each of those features is small, additive, and follows
patterns already established elsewhere in the codebase (same DTO-threading shape as
`recipeSteps`, same full-replace semantics as the existing envelope fields). Nothing flagged.

---

## Flagged, not fixed — still open

- **Accessibility (screen-reader) — the round-1 sweep only covered Order Tracker's own
  screens.** `BusinessSettingsPage.tsx`, `ManageBusinessPage.tsx`'s non-rename controls, and
  the shared auth/settings screens weren't re-audited for label association this round either.
- **Mobile — plain `<table>` lists (My Work, Customers, and — per user report 2026-09-15,
  screenshot from the deployed Render site — the Orders list too) still only scroll
  horizontally**, unlike `.data-table`'s existing collapse-to-card treatment elsewhere in the
  app. User's own words: "the order tracker mobile version is clunky. Make it more like the
  priority tracker. More mobile friendly. I don't want too many horizontal scrolling in
  phone." The screenshot shows the Orders table's `TYPE` column (and the `BULK` badge inside
  it) clipped off the right edge of a 412px-wide phone viewport, forcing horizontal scroll to
  see it — Priority Backlog Tracker's own list views don't have this problem. Real, isolated
  fix; out of scope for tonight given everything else already landed. Broader than just the
  three known tables — worth an actual pass over every list/table view in Order Tracker on a
  real phone width, not just the ones already named in this doc.
- **Maintainability — `Packaging.cost()`/`timeHours()` still duplicate `OrderCalculator`'s
  line-item summation** (LOW risk, both still agree today).
- **First-time-user — "Business" vs "Group" terminology still isn't reconciled** between Order
  Tracker's own screens and `AddToGroupModal`'s cross-applet language. This is a product/naming
  decision, not something to guess at unsupervised.
- **QA — frontend still has no rendering/component tests**, only pure-function tests
  (`validateSplits` now included). Adding `@testing-library/react` would need a new dev
  dependency install, which wasn't attempted this session — no verified network/registry access
  path was established for this environment, and installing a new dependency unsupervised
  without being able to confirm it resolves cleanly is the kind of judgment call better left
  for you to greenlight explicitly.
- **Performance/scale and i18n/l10n** — still not reviewed at all, per round 1's own scope note.
