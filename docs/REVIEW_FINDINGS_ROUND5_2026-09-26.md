# Fifth review round — UI/UX, functionality, and bugs across the whole app (2026-09-26)

Scope: everything built since round 4 (2026-09-18) — roughly 90 commits, covering the yarn
transfer request redesign (mb-33), Order Tracker ↔ Material Inventory sync (ad-2), Finance
Tracker's double-entry ledger rework (ad-1), the notification orchestration layer (ad-4),
cross-applet search (ad-5), a large batch of mobile/UI polish (ui-1..13), and a very large
dedup/refactor + test-coverage effort (qd/bdup/fdup/tg/tc/to/tf series) — plus the demo
seeder change (mb-39) from immediately before this round started.

Four background review agents ran in parallel: one re-verified every item rounds 2–4 left
"flagged, not fixed" against current code; one did a security + correctness pass on the new
cross-applet features; one did a UI/UX + mobile/accessibility pass on the ui-1..13 batch and
the Kanban board; one did a maintainability + test-coverage pass on the dedup/refactor
commits. I re-read the real current source for every finding below myself, and live-verified
the highest-severity ones in the browser (or with a real dispatched `TouchEvent`/backend
test) before treating them as confirmed — a few things agents flagged turned out to need a
different test setup than first written (a security-lens regression test that needed the
member added to both groups before the link, then removed after) but no finding itself was a
false lead this round; all ten held real substance.

Six real bugs got fixed and verified live this round, each on its own commit and pushed:
cross-applet search crashing on non-overlapping group membership, my own mb-39 seeder never
actually linking Product Catalog, a Kanban swipe gesture that could misfire while the board
was mid-scroll, mobile tabbar tap targets stuck below the app's own 44×44 rule, closeLine/
cancel leaking a raw 500 on a version conflict instead of a friendly 409, and a missed
`formatMoney(0)` spot. Backend: 382/382 tests (up from 380, two new regression tests).
Frontend: 250/248 → 250/250 (two new `useSwipe` tests), `tsc --noEmit` clean.

---

## Fixed this round

**r5-1 (HIGH) — Cross-applet search crashed the whole request when a linked group's membership didn't overlap the caller's.**
`CrossAppletSearchService.search` (`src/main/java/com/backlogtracker/commons/search/CrossAppletSearchService.java:46-52`)
called `other.search(linkedGroupId, userId, query)` for every linked applet with no
try/catch. Every `Searchable` provider's own `search()` starts with
`groupService.requireMember(linkedGroupId, userId)`, which throws a 403 the instant the
caller isn't literally a member of the linked group. Since `GroupLinkService`'s own
invite-all is explicitly best-effort ("a member an invite can't reach... is skipped rather
than failing the whole link"), non-identical membership across linked groups is the normal
case, not an edge case — any founder missing from just one linked applet lost *all* search
results, including from their own group, the moment that link existed. Fixed by catching the
per-provider `ResponseStatusException` and simply omitting that applet's results, matching
how the rest of the app already treats a member gap as best-effort rather than fatal. New
regression test (`CrossAppletSearchServiceTest.aLinkedGroupTheCallerIsntAMemberOfIsSilentlyS
kippedNotACrash`) reproduces the drifted-membership scenario directly — the existing test
suite only ever linked groups where the same user created both sides, so this exact,
completely ordinary topology was never exercised. Verified via `mvn test` (9/9 in the class,
382/382 full suite).

**r5-2 (MEDIUM, own bug) — mb-39's demo seeder claimed to link "every pairing" but never linked Product Catalog to anything.**
`DemoDataSeeder.java` created and seeded a `pcGroupId` (Product Catalog) alongside the other
three applet groups, but the 6 `groupLinkService.link(...)` calls right after it only covered
the 4-way clique of {Backlog Tracker, Order Tracker, Finance Tracker, Material Inventory} —
Product Catalog was never included, leaving one of the five demo groups functionally
disconnected in the one seed path meant to showcase the whole cross-linked system. Added the
4 missing pairings for a real 5-group mesh (10 links total). Verified live: Product Catalog's
Manage page now shows "Founders" already linked as its Business.

**r5-3 (HIGH) — Kanban card swipe gesture could misfire while the board was being scrolled.**
The board must scroll horizontally on a phone to reach columns off-screen (3 columns ≈ 780px
wide inside a 375px viewport, confirmed live via `scrollWidth`/`clientWidth`), but
`useSwipe.ts` attached raw touch handlers directly to each card with no `touch-action`
restriction and no awareness of the board's own scroll. A drag over a card to scroll sideways
also accumulated as swipe distance, so scrolling to see the next column could silently also
fire "cancel this order" (swipe-left) or promote it (swipe-right) once the finger lifted.
Fixed by having `useSwipe` find the nearest horizontally-scrollable ancestor at touch-start
and abort the gesture (no callback, no visual offset) the instant that ancestor's
`scrollLeft` actually changes — a real scroll can never masquerade as a swipe afterward.
Verified live by dispatching real `TouchEvent`s in the browser: an -80px drag that also moves
`board.scrollLeft` no longer opens the cancel modal; an identical -80px drag with no board
scroll still does (regression check). 2 new unit tests cover both cases
(`useSwipe.test.ts`), plus all 6 pre-existing tests still pass unchanged.

**r5-4 (HIGH) — the mobile bottom tabbar's tap targets were ~34×34, well under the app's own 44×44 rule.**
`ui-9` established a 44×44 minimum tappable area for icon-only controls but only grew
`.iconbtn`/`.time-track-button-inline`/`.pw-toggle` — the bottom tabbar (Backlog Tracker's
and Order Tracker's *primary* mobile navigation, 4-5 icons) was left at 6px padding around a
22px glyph (34×34, confirmed via `getBoundingClientRect` in the live browser). Fixed by
applying the same `min-width`/`min-height: 44px` pattern already used elsewhere. Verified
live at 375px width: tabbar links now measure exactly 44×44.

**r5-5 (LOW/MEDIUM) — `TransferRequestService.closeLine`/`cancel` leaked a raw 500 instead of the friendly 409 the rest of the service already gives on a version conflict.**
`send`/`confirmReceived` correctly retry-with-re-read on `OptimisticLockingFailureException`;
`closeLine`/`cancel` did a single unretried `repository.save`, so a real conflict (racing
against a concurrent send/confirm/close on the same request) surfaced as an unhandled
`OptimisticLockingFailureException` rather than a mapped `ResponseStatusException(409)`.
Neither method touches inventory before saving, so the fix is a plain re-read-and-retry loop
with no compensating credit/debit needed (unlike `send`/`confirmReceived`, which do have to
undo an inventory debit on a lost race). New concurrency test races 20 concurrent `closeLine`
calls on the same line and asserts every loser gets a real 409, never the raw locking
exception — this specifically distinguishes "caught business rejection" from "any
RuntimeException", since a naive catch-all assertion would have silently passed even before
the fix.

**r5-6 (NIT) — a hardcoded `"₹0.00"` fallback for packaging cost, missed by the earlier `formatMoney()` dedup pass.**
`MaterialsSection.tsx:81,254` used `order.packaging?.cost != null ? formatMoney(...) :
"₹0.00"` instead of `formatMoney(order.packaging?.cost ?? 0)` — identical output today, but a
spot that would silently diverge if `formatMoney`'s own zero-formatting ever changed.
Verified live: an order with no packaging cost still shows "₹0.00".

---

## Re-checked from rounds 2–4 — status as of today

- **"Business" vs "Group" terminology** (round 2, user-deferred) — still present exactly as
  described (Order Tracker says "Business" everywhere, every other applet says "Group"). No
  commit since round 4 touches this; still a product-naming decision, not a bug.
- **Unbounded list pagination + the per-creator bulk-variant loop** (round 2/4) — still true,
  now confirmed across all four applets' repositories (`OrderRepository`,
  `CustomerRepository`, `LedgerEntryRepository`, `YarnTypeRepository`,
  `TransferRequestRepository`, `ColorwayRepository`, `InventoryEntryRepository` all still
  return plain `List<T>`). At this app's real scale (dozens–hundreds of records per group)
  this remains a low-priority note, not a live problem.
- **`SlideToggle`/`ProgressRing` file placement** — **fixed**, same day as round 4
  (commit `df7eb4b`, before this round started): both now live in the shared
  `components/` directory.
- **Missing test for bulk variant + components / removing a component with logged time** —
  **fixed**, same commit `df7eb4b` — both gaps now have real tests.
- **Setup wizard focus-on-Continue** — **fixed**, same commit `df7eb4b`.
- **`ExpensesPage`/`IncomePage` duplication** — **resolved**, but not the way round 4
  thought: `ad-1` (commit `3ff0c71`) deleted both pages entirely and replaced them with one
  unified `LedgerPage.tsx` as part of the double-entry ledger rework — the duplication is now
  structurally impossible since there's only one page.
- **The yarn-shortfall comparison** — still open, relocated. After the `OrderDetailPage.tsx`
  split it now lives in `SummaryCard.tsx:150,155,162,167`, still inline JSX with no dedicated
  test file for that component.
- **`MyInventoryPage.tsx` size** — grew, not shrunk: 702 lines (was 625 at round 4), touched
  by two features since (`ui-2`'s reservation-row move, `ad-2`'s usage/reservation wiring)
  that both added to the same file rather than decomposing it. Still a signal, not a
  diagnosed problem — flagged again below.
- **`TransferRequestService`'s over-fulfillment cap characterization** (round 3) — **obsolete,
  not just re-verified**: the whole service was rebuilt in mb-33 around real send/receive/
  close semantics with a proper per-line `@Version` retry loop that re-reads current state
  on every attempt (confirmed directly, not just from the commit message) — the old
  "theoretically raceable" framing no longer describes this code at all.
  `InventoryService.adjustQuantity`'s atomic fix and `ProgressRing`'s `aria-hidden` scoping
  are both still correctly in place.
- **Backlog Tracker's own maintainability/QA lens** (round 4 said fully unreviewed) —
  partially addressed since: `qd-1` (commit `bf5e9cb`) fixed the exact duplication round 4
  named (`ArchiveRequestService` reimplementing `ApprovalService`'s own state machine) as
  part of a broader consolidation. A dedicated, standalone pass on Backlog Tracker's own
  code quality still hasn't happened as its own exercise.

---

## New findings this round

### Security / correctness

**MEDIUM — `LedgerEntry.sourceRef`'s idempotency guarantee is a convention, not a database constraint.**
`LedgerEntry.java:50-51` marks `sourceRef` `@Indexed` only, not `unique = true`, and
`LedgerEntryService.syncFromOrderPayment` (`:158-173`) does a plain
`find → if absent → insert`, not an atomic upsert — contrast `GroupLink`, which enforces its
own equivalent guarantee with a real `@CompoundIndex(unique = true)` plus a caught
`DuplicateKeyException`. Two concurrent deliveries with the same `sourceRef` (a double-clicked
"Backfill payments to Finance", which itself has no locking either) can both pass the
existence check before either inserts, producing two ledger rows for one real payment. Not
fixed this round — the right fix (a real unique+sparse compound index, since `sourceRef` is
null for every manually-entered row) touches the ledger's core write path and deserves its
own focused pass with a migration check against any existing data, not a quick patch bundled
into an already-large review round.

**MEDIUM — `Order` has no `@Version`; two concurrent writes to the same order (usage-log entries, payments, time-log entries) can silently drop one via last-write-wins.**
Confirmed no `@Version` field exists on `Order.java`, and `addUsageLogEntry`/`addPayment`/
`addTimeLogEntry` in `OrderService.java` all read-modify-save without one. This is distinct
from (and doesn't corrupt) the actual inventory ledger — `InventoryService.adjustQuantity`'s
atomic `$inc` already protects real stock quantities — but a lost usage-log entry whose
`synced=true` deduction already happened silently erases the order's own audit trail of it.
Not fixed this round: adding `@Version` to `Order` is a wide-blast-radius change (every other
read-modify-save call site on this heavily-used document would start throwing 409s it
doesn't currently expect, and none of the surrounding frontend code has a conflict-retry UX
for it yet) — this needs a deliberate, dedicated pass, not a bolt-on inside this round.

### UI/UX / accessibility

**MEDIUM — Popover menus (Bell, Connections, NavMenu, the new `MoreTab`) never manage focus.**
`usePopoverPosition.ts` only computes on-screen coordinates; nothing moves focus into the
menu on open or back to the trigger button on close (`useDismissableMenu.ts:16-19` closes on
Escape but never returns focus). This predates `ui-8`'s edge-aware positioning work, but that
commit touched exactly this code path without addressing it. A keyboard/screen-reader user
has to tab in manually and loses their place on close. Flagged, not fixed — real accessibility
gap shared across four menu components, wants its own careful pass (focus trap + restore)
rather than a rushed change to shared popover code this late in an already-large round.

**LOW/MEDIUM (unverified) — no body scroll lock when a full-screen sheet modal is open.** No
`useLockBodyScroll`-style mechanism exists anywhere in the frontend; `.modal-backdrop` is a
fixed full-viewport overlay, which usually blocks background scroll visually but isn't always
reliable via touch on older iOS Safari. Flagged for a live mobile-Safari check rather than
asserted as broken from code alone — the emulated browser used for this round's live
verification can't reproduce that specific engine quirk.

**LOW (nit) — `runAction`/error-handling copy-pasted three times across the `OrderDetailPage` split** (`OrderDetailPage.tsx:68-77`, `AssignmentsSection.tsx:28-37`, `LogisticsSection.tsx:29`), each with its own independent error state. Functionally correct today; a future change to error-handling behavior would need three separate edits. Not fixed — purely a maintainability nit, no user-facing symptom.

**Verified fine, no issue (checked directly, not just re-asserted):** the Kanban board's
"strictly adjacent-only transitions" never offers an illegal move in the first place (no
drag-and-drop exists at all — only a filtered `<select>`), so there's no silent-reject case to
worry about. The guided cancel flow explicitly states what does and doesn't change ("Logged
time and any payments already recorded stay exactly as they are"). `Switch` uses a real
`<input type="checkbox" role="switch">`, not a styled div. The full-screen sheet modal's
z-index (60) correctly layers above both the sticky action bar (15) and top nav (20) — no
bleed-through. `MyInventoryPage`'s expand toggle and quantity controls all carry real
`aria-label`s. `Section`'s disclosure widget uses a real `<button aria-expanded>` (missing
only `aria-controls`, a nit not worth a separate entry).

### Maintainability / test coverage

**MEDIUM — the yarn transfer request redesign's frontend (`RequestsPage.tsx`, rewritten by 371 lines in mb-33) has zero test coverage.** The backend side is well covered (13 tests in
`TransferRequestServiceTest`, including a real concurrency test for the version-retry loop),
but this multi-step, multi-status stateful form — exactly the shape of component the tf-2
sweep targeted elsewhere — has no test file of its own, and landed *after* that systematic
coverage pass. Flagged for a follow-up test pass rather than guessed at.

**MEDIUM — `ad-2`'s two new collaborator classes (`OrderTrackerReservationProvider`, `MaterialInventoryUsageConsumer`) have no dedicated unit tests, only indirect coverage via one integration-style test file** (`UsageLogAndReservationApiTest`, 5 scenarios). Edge cases
outside those 5 flows (partial usage across multiple creators, reservation floor at zero) have
no direct test. Flagged for a follow-up test pass.

**Verified fine, no issue (checked directly, several dedup extractions this round's
maintainability lens specifically re-derived from the diff rather than trusted from the
commit message):** `OrderCalculator.pricingPipeline`, `formatMoney`'s negative-number
handling, `CustomerService.applyFields` (confirmed it wasn't left stale when `ad-6` later
added multi-address support), and `SafeRemovalRule` are all genuinely behavior-preserving
extractions. The "surfacing 2 real gaps" commit (`4c9ce9b`) fixed both gaps in the same
commit, not just documented them. The "2 silently broken e2e specs" (`c6f77e7`) were 3 real
UI-copy drifts, correctly fixed, and CI (`mvnw verify` → real packaged jar → Playwright)
would catch a real regression of this kind again — the only residual note is that label-text
e2e assertions inherently need upkeep on copy changes, which isn't a new risk. `capitalize()`
and `useDismissableMenu` are fully adopted everywhere they should be; the two call sites that
deliberately declined `ScopedLookup.requireInGroup` still correctly scope by group through
other means.

---

## Flagged, not fixed — summary with reasons

- **`LedgerEntry.sourceRef` not database-uniqued** — real fix touches the ledger's core write
  path; deserves a dedicated pass with its own migration check, not a bolt-on here.
- **`Order` has no `@Version`** — correct fix has a wide blast radius across every other
  read-modify-save call site on `Order`; needs a deliberate pass (including frontend
  conflict-retry UX), not a same-round addition.
- **Popover focus management** (Bell/Connections/NavMenu/MoreTab) — real, but shared across
  four components; wants a careful, dedicated focus-trap-and-restore pass.
- **Body scroll lock on modals** — flagged as unverified rather than fixed; needs a real
  mobile Safari check this round's tooling can't perform.
- **`runAction` triplication in the `OrderDetailPage` split** — maintainability nit only, no
  user-facing symptom.
- **`RequestsPage.tsx` and `ad-2`'s two collaborator classes have thin/no dedicated tests** —
  both are "write the tests" follow-up work, not bugs; sized for their own pass.
- **"Business" vs "Group" terminology, unbounded pagination, `MyInventoryPage.tsx` size, the
  yarn-shortfall comparison in `SummaryCard.tsx`** — all re-confirmed exactly as previously
  characterized; none have gotten worse in a way that changes their priority, all still
  low/deferred for the reasons already on record from earlier rounds.

## What this round did not get to

- A live, real-device check of body-scroll-lock behavior on mobile Safari specifically (the
  one finding this round could only characterize as "unverified," not confirmed or refuted).
- Writing the actual follow-up tests for `RequestsPage.tsx` or `OrderTrackerReservationProvider`/`MaterialInventoryUsageConsumer` — flagged as real gaps, not closed.
- A dedicated, standalone maintainability/QA pass on Backlog Tracker's own frontend code
  (its backend got a real fix this round via the qd-1 consolidation already landed before
  this round started, but the frontend side of that applet still hasn't had its own pass).
