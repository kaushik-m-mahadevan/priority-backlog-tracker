# Third review round — Finance Tracker, Material Inventory, and the Components feature (2026-09-17)

Scope: everything built since round 2 (2026-09-15) that neither prior round ever reviewed —
three entirely new applets (Finance Tracker, Material Inventory, Product Catalog), the
Order Tracker Components feature, and today's Components follow-up polish (standalone
Notes, hours-based progress rings, seeded demo order, SlideToggle UI) — plus a fresh
re-verification of round 2's still-open items against the current code.

Four background review agents ran in parallel for this sweep, each from a different angle:
one re-verified round 2's "flagged, not fixed" list and did a money/security pass over the
Components feature's cost math and authorization; one did a security + money-correctness
audit of Finance Tracker and Material Inventory specifically; one did a maintainability +
test-coverage pass across everything new; one did an accessibility/mobile/onboarding pass
across every new frontend surface. Every finding below was re-verified against the actual
current source by reading the real file and line numbers myself before it went in this
document or got fixed — several agent findings turned out to be false leads (a
bill/invoice-attachment feature that doesn't exist on this branch, a "rule of three"
duplication concern that isn't actually duplicated, a "no test coverage" claim for
yarn-cost tracking that turned out to already be covered by existing tests) and were
dropped rather than repeated here.

Six real correctness/security bugs were found and fixed this round, plus a real mobile
accessibility break and several smaller UX/test gaps. Everything fixed was covered by a
new or updated automated test, the full backend suite (273 tests) and frontend suite
(24 tests) both pass, `tsc --noEmit` is clean, and every user-facing fix was also verified
live in the browser against a freshly rebuilt jar.

---

## Fixed this round

**BLOCKER — cross-user yarn transfers could drive inventory negative or conjure yarn from
nothing under concurrency.** `InventoryService.adjustQuantity()` was a read-then-write
(`quantityOf()` then a separate `save()`), and `TransferRequestService.fulfill()` calls it
twice per fulfillment. Two concurrent fulfillments touching the same person's same yarn
row — two different pending requests fulfilled at once, or a retried request — could both
read the same starting quantity and both write, driving the row negative or losing a
decrement. Fixed by rewriting `adjustQuantity` as a single atomic MongoDB
`findAndModify`/`$inc` (the same pattern `CounterService` already established elsewhere in
this codebase), with the "not enough on hand" check folded into the query filter itself so
an insufficient-balance withdrawal simply doesn't match, atomically. Verified with a new
concurrency test (`MaterialInventoryServiceTest.adjustQuantityUnderConcurrentWithdrawalsNeverGoesNegativeOrLosesAnUpdate`,
mirroring `CounterServiceTest`'s own concurrency test): 50 concurrent 1-skein withdrawals
against a 50-skein balance land on exactly zero, every time.

**MEDIUM — a lost-update race on `TransferRequest.fulfilledQuantity`.** Two concurrent
partial fulfillments of the same request could both read the same `fulfilledQuantity`, and
one save could silently overwrite the other's increment — undercounting how much had
actually changed hands, independent of the inventory bug above. Fixed by adding `@Version`
to `TransferRequest` and wrapping the request-side bookkeeping in a retry loop on
`OptimisticLockingFailureException` (the exact pattern `CostConfigChangeService.approve()`
already uses for the same problem shape) — the physical inventory move itself stays outside
the retry loop so a retry can never double-move yarn. Verified live with a new test
(20 concurrent 0.25-skein fulfillments against a request, `fulfilledQuantity` lands on
exactly their sum, never short).

**MEDIUM — the same read-then-write race on Finance Tracker's `settleUp`.** Balances are a
derived aggregate over the whole ledger (no single document to version), so two concurrent
settlements by the same payee — a double-click, or one against the business and one
against a person at the same instant — could both read the same pre-settlement balance and
both pass the "not more than owed" check, over-settling a debt. Fixed with an in-process
lock keyed by `(groupId, userId)` around the read-validate-save sequence — this app runs as
a single instance and no distributed lock/transaction infrastructure exists anywhere else
in this codebase either, so this closes the actual race without inventing new
infrastructure for what's otherwise a single-document insert. Verified with a new
concurrency test: 20 concurrent attempts to settle a ₹50 debt ₹5 at a time resolve to
exactly 10 successes, never more.

**HIGH — an IDOR on `GET /api/groups/{id}/links/{otherAppletKey}`.** Unlike `link()` and
`unlink()` on the same controller (which correctly call `groupService.requireMember()`),
the read endpoint passed the path `id` straight through with no membership check — any
authenticated user could probe an arbitrary group id to learn what other-applet group it's
linked to. This is the same IDOR class round 1 already fixed once for
`GroupCategoryController`; the newer cross-applet linking endpoint didn't get the same
treatment. Fixed by requiring membership in `GroupLinkService.linkedGroupId()` before the
lookup; added a regression test (`readingALinkRequiresMembershipInTheGroupAsked`) alongside
updating the three existing call sites for the new signature.

**HIGH — a work-stage progress ring's own percentage was hidden from screen readers by its
own wrapper.** `ProgressRing` (`OrderDetailPage.tsx`) put `aria-hidden="true"` on the
*outer* span, which hid both the SVG and the real DOM text showing the percentage — so a
sighted user sees "56%" but a screen-reader user gets nothing at all for that stage, on
every hours-based work-stage row in every order. Fixed by moving `aria-hidden` onto just
the decorative `<svg>`, leaving the percentage text in the accessible tree. Verified live:
every rendered ring's percentage text is now real, unhidden DOM text (checked via a DOM
walk for any `aria-hidden` ancestor on the seeded order's 5 rendered rings).

**HIGH — Material Inventory's yarn/needle "who has what" tables broke on mobile.** Both
tables used `.ot-table`, whose mobile card-collapse rule (`@media (max-width: 620px)`)
hides `<thead>` entirely — correct for a fixed-schema row (order #, status, ...), but these
two tables have one column *per group member*, generated dynamically. Collapsing them
turned every row into a stack of bare numbers with no name attached to any of them, on the
exact information the table exists to show. Fixed by removing `.ot-table` from just these
two tables (the class only has rules inside that one mobile media query, confirmed by
reading `index.css`, so this is a clean opt-out with zero effect on desktop styling) —
`.table-wrap`'s existing `overflow-x: auto` lets them scroll horizontally instead, keeping
the header row intact. Verified live at 375px width: "YARN"/"YOU" column headers stay
visible above each quantity after the fix (screenshotted before and after).

**MEDIUM — a cluster of repeated buttons across four surfaces with no distinguishing
accessible name**, all following the same pattern already fixed for Order Tracker in round
2 but not yet applied to what's been built since: `ExpensesPage.tsx`/`IncomePage.tsx`'s
per-row "Edit", `ProfitSplitPage.tsx`'s per-proposal "Approve"/"Reject" and per-recipient
"Remove", `ColorwaysPage.tsx`'s "Promote to catalog"/"Edit"/"Remove", and
`OrderFormFields.tsx`'s new `ComponentsFields` "Remove component" (inconsistent within its
own file — the add-on and mandatory-item "Remove" buttons two functions up already do this
correctly). Fixed by adding `aria-label`s naming the specific entry at every site.

**MEDIUM — `SlideToggle` exposed no selection state to assistive technology.** Two plain
`<button>`s with only a CSS class marking which was active — keyboard-operable (confirmed:
real buttons, Tab/Enter/Space work), but a screen-reader user tabbing between
"Individual"/"Bulk" heard two identically-weighted buttons with no indication which was
selected. Fixed with `role="radiogroup"`/`role="radio"`/`aria-checked`. Verified live via a
DOM query on the New Order page: both toggles now report `role=radiogroup` on the
container and correct `aria-checked` true/false on each option.

**LOW — the stale-inventory indicator's explanation was hover/title-only**, unreachable by
touch or most screen readers even though the "stale" label text itself was visible. Added a
matching `aria-label`. Also added accessible names to the per-cell quantity-edit buttons
(previously announced only as a bare number) and to the yarn/needle "Remove" buttons.

**LOW — a contradictory doc comment on `Order.mandatoryItems`.** It claimed mandatory
items are "only meaningful when components is empty ... uses components exclusively," but
`OrderCalculator` actually sums both unconditionally (verified by reading the real
multiplication chain — no double-counting exists in the code, only the comment was wrong).
Corrected to describe the real additive semantics.

**LOW — clearing a yarn's cost back to null had no test.** The two edge cases an agent
flagged as "untested" (first-ever cost set, unrelated-edit-doesn't-append-history) turned
out to already be covered by `MaterialInventoryServiceTest`'s existing cost-history tests —
that finding was a false lead, dropped rather than duplicated here. The one gap that
actually existed (explicitly clearing a previously-set cost) got a new test instead.

---

## Re-checked from round 2 — confirmed still correct to leave as-is

- **"Business" vs "Group" terminology.** Confirmed unchanged in the current code (Order
  Tracker still says "Business" throughout; `AddToGroupModal` still says "Group") — the
  user explicitly deferred this on 2026-09-15, and nothing about this round's work touches
  it either way.
- **Unbounded list pagination + the per-creator loop in bulk-variant building.** Both
  confirmed still present (`OrderRepository`/`CustomerRepository` still have no
  `Pageable` overloads; the loop is bounded by distinct creators per order, not by total
  volume). Nothing built this round changes the app's actual scale — still low priority.

---

## New findings — verified clean, no fix needed

- **Components feature money math.** Traced the full scaling chain for a concrete case
  (variant quantity 10, component quantity 5): materials/time are scaled by component
  quantity only, folded into the *per-unit* gross before the variant-level multiply, with
  overhead/profit applied exactly once at the order/variant level — no double-counting.
  Authorization on every component-touching path (time logging, template lookup) correctly
  requires group membership first.
- **Finance Tracker's split-sum validation, profit distribution, and cross-applet
  linking's membership checks** (aside from the one IDOR fixed above) are all correctly
  guarded server-side, independent of frontend input.
- **Material Inventory's `YarnType`/`NeedleType`** are plain Lombok classes with nullable
  fields, not records — they don't reproduce the `BusinessConfig`/`User.handle`
  primitive-field-on-an-existing-document crash pattern round 1 found.
- **The Order Tracker ↔ Material Inventory shortfall bridge** (`OrderDetailPage.tsx`) has
  correct boundary-condition math (equal have/needed is not flagged short) — genuinely
  correct, just untested and inline (see below).
- **The forced first-time setup wizard** gates every Order Tracker route at the layout
  level, so no route is reachable around it.
- **Brown theme** doesn't regress round 2's color-only-hint fix — the mismatch hint still
  carries real text, not just color, in every theme.

## Flagged, not fixed

- **`TransferRequestService.fulfill()`'s "no more than requested" cap is a soft business
  rule, not a physically-enforced one.** The initial read of `remaining` can go stale under
  heavy concurrent fulfillment, in principle letting a request's `fulfilledQuantity`
  slightly exceed `requestedQuantity`. This is bounded and non-destructive — the yarn that
  moves genuinely existed and genuinely moved, thanks to the atomic inventory fix above; the
  actual physical-stock invariant (never negative, never conjured) can't be broken by this.
  Closing it fully would need a real multi-document transaction, and no `@Transactional`
  usage exists anywhere else in this codebase either — flagged rather than solved with new
  infrastructure this round wasn't scoped for.
- **`SlideToggle` and `ProgressRing` live in files named for something else**
  (`OrderFormFields.tsx`, `OrderDetailPage.tsx`) rather than this codebase's existing shared
  `components/` directory (`EffortIcon.tsx`, `Badge.tsx`, etc.). Purely a code-organization
  nit with no functional impact — noted for a future pass, not fixed now to avoid a
  same-round import-path refactor on top of the correctness fixes above.
- **No test exists for a bulk variant with components, or for removing a component that
  still has logged time against it.** The single-component/individual-order path is well
  tested; these two combinations aren't. Flagged for a follow-up test pass rather than
  guessed at without a concrete failure to reproduce first.
- **The yarn-shortfall comparison in `OrderDetailPage.tsx` is correct but untested and
  inline**, unlike `validateSplits` (round 2 extracted that into a pure, tested function).
  Same shape of gap, not yet closed — a refactor-plus-test task, deferred.
- **Setup wizard doesn't move focus to the next step's heading on "Continue."** Low
  severity — the wizard isn't a modal, so there's no focus trap, and nothing gets stuck;
  just a rough edge for screen-reader users advancing through it.
