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

## Fixed 2026-09-15 (user-approved implementation pass)

- **Mobile — Orders/My Work/Customers tables collapsed to cards under 620px**, mirroring
  Priority Backlog Tracker's own `.data-table` pattern (`.ot-table` in `index.css`). Verified
  live at 375px (zero horizontal overflow, confirmed via computed styles) and at desktop width
  (headers still align with columns — Item now comes before Order # in both, since the item
  name is what's actually recognizable at a glance).
- **Accessibility (screen-reader) — extended the label audit beyond Order Tracker.**
  `BusinessSettingsPage.tsx` was already clean. Found and fixed real gaps in
  `ManageBusinessPage.tsx` (rename input, invite input), and — more significantly — across the
  shared screens round 1 never covered at all: `LoginPage.tsx` (both the sign-in and
  forgot-password forms), `RegisterPage.tsx` (all 5 fields), `SettingsPage.tsx` (profile,
  password, timezone, ranking-weights, and category/priority-add fields), `GroupsPage.tsx`
  (group name, rename, invite), and Priority Backlog Tracker's own item form
  (`ItemFormModal.tsx` — title/category/priority/effort/due-date/assignee/notes) including its
  `MarkdownField.tsx` textarea and formatting-toolbar buttons (which had `title` but no
  `aria-label`, and their inner text — "B", "I", "H", "•" — would otherwise have been their only
  accessible name). Verified live via `document.querySelector('label[for=...]')` /
  `getElementById` pairing checks on Login, Register, and Settings.
- **Testing infrastructure** — added `@testing-library/react`, `jest-dom`, and `user-event` as
  dev dependencies plus a jsdom test environment, and proved it out with real coverage of
  `MandatoryItemsFields` (multi-entry grouping, isTool filtering, add/remove, edit-triggers-
  onChange). `npm ci` (what `frontend-maven-plugin` actually runs) confirmed working with the
  updated lockfile via a full `mvn package`.
- **Maintainability — `Packaging.cost()`/`timeHours()` deduplicated against
  `OrderCalculator.lineItemsCost()`.** Both independently summed `unitCost * quantity` (and
  `unitTimeHours * quantity`) over a line-item list. Extracted to
  `LineItem.sumCost()`/`sumTimeHours()` static helpers; both call sites now delegate.
- **Performance/scale — reviewed via a dedicated background audit** (N+1 patterns, missing
  pagination, indexing, repeated fetches). One real, worth-fixing issue found and fixed:
  `OrderService.view()` called `businessConfigService.get()` per order, so loading the Orders
  list or My Work issued one extra Group + BusinessConfig fetch per bulk order instead of once
  per request. Fixed by threading an already-fetched `BusinessConfig` through a new overload
  used by `all()`/`myWork()`; single-order call sites unchanged. Everything else the audit found
  is genuinely low-priority at this app's actual scale (dozens to low-hundreds of orders per
  business): a small per-creator loop in bulk-variant building (bounded, not proportional to
  total order volume), and a handful of unbounded `List<T>` collection endpoints
  (`OrderRepository.findByGroupId`, `CustomerRepository.findByGroupId`, etc.) that have no
  `Pageable` — fine today, worth adding once order/customer counts head into the thousands
  (unlike Items, which already has archival + pagination). Indexing matches actual query
  patterns; no unindexed hot-path query found.
- **i18n/l10n — reviewed via a dedicated background audit.** One real, worth-fixing bug found
  and fixed: three Order Tracker pages (`OrderDetailPage.tsx`, `OrdersPage.tsx`,
  `MyWorkPage.tsx`) called raw `toLocaleDateString()` instead of the shared
  `formatDate`/`formatDateTime` (`src/lib/format.ts`) that the rest of the app already uses to
  honor the user's chosen display timezone (`src/lib/tz.ts`) — so changing that preference
  silently had no effect on Order Tracker's own dates. Fixed at all 5 call sites; verified live
  (dates now render as "16 Sept 2026" instead of the browser's raw locale format). Currency
  formatting (`₹{amount.toFixed(2)}`, ~12 sites in `OrderDetailPage.tsx`) was reviewed and left
  as-is: no thousands separator, but internally consistent everywhere it's used, and this is a
  single-currency (INR), single-business app — a formatter migration would be cosmetic churn,
  not a fix for an actual inconsistency. No multi-language need exists or is anticipated, so no
  i18n framework was considered.

## Flagged, not fixed — still open

- **First-time-user — "Business" vs "Group" terminology still isn't reconciled** between Order
  Tracker's own screens and `AddToGroupModal`'s cross-applet language. **User decision
  (2026-09-15): leave the code as-is** — noted here for the record, not slated for
  implementation.
- **Mobile — unbounded list pagination** (see performance note above) and a small per-creator
  loop in bulk-variant building are low-priority today; revisit if order/customer volume grows
  substantially.

This closes out every item from the user's 2026-09-15 approved punch list except the
Business/Group terminology question, which the user explicitly chose to defer.
