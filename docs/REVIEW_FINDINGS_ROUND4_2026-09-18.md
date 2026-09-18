# Fourth review round — Priority Backlog Tracker's own core, and deeper passes on the newer applets (2026-09-18)

No commits landed between round 3 (2026-09-17) and this round, so instead of reviewing "what
changed," this round broadens coverage to ground no prior round ever gave a dedicated pass:
**Priority Backlog Tracker's own original applet** (items, ranking, archival approval,
insights) — all three prior rounds explicitly noted it only got incidental coverage — plus
a maintainability/test-coverage pass on Finance Tracker/Material Inventory/Product Catalog
(round 3 covered their security and accessibility, not their code quality), plus a
mobile/onboarding pass on Backlog Tracker's own Top 10/Quick Wins/Needs Attention/Workload/
Archive views (round 2's accessibility audit covered its shared auth/settings screens, not
these feature views). It also re-verifies every item the last two rounds left flagged.

Four background review agents ran in parallel: one re-verified every "flagged, not fixed"
item from rounds 2 and 3 against current code; one did a security + correctness pass on
Backlog Tracker's own domain (items, edit concurrency, archival approval, ranking math,
workload/insights); one did a maintainability + test-coverage pass on Finance Tracker,
Material Inventory, and Product Catalog; one did a mobile + first-time-user pass on Backlog
Tracker's core views. I re-read the actual current source for every finding below myself
before writing it here — a few things the agents flagged turned out to be false leads (a
"no shared cost/balance abstraction" duplication concern, a "case-insensitivity untested"
claim, a "blocker: edit-lock feature missing from the frontend" framing that turned out to
mean the feature was never built on the *backend* either, superseded by simple optimistic
locking) and are noted as such rather than repeated as real findings.

**Findings were reported first, per your instruction, and this document originally shipped
as findings-only.** You then asked to fix everything below — this section records what was
actually done, added the same day, with the rest of the document kept as the original,
verified inventory (severity tags and false-lead notes untouched).

---

## Fixed after triage (2026-09-18, same day)

All nine fixable items were addressed, each with a real test and a live check against a
freshly rebuilt jar (not just a unit test in isolation). Backend: 276/276 tests pass
(fresh run). Frontend: 24/24 vitest, `tsc --noEmit` clean.

1. **Archive-request approval race (§1, HIGH)** — `ArchiveRequestService.approve()` now
   retries on `OptimisticLockingFailureException` with `@Version` added to `ArchiveRequest`,
   mirroring `CostConfigChangeService`/`TransferRequestService`'s own established pattern.
   New concurrency test (`ArchiveRequestServiceTest`): 5 members approving at once all get
   recorded, the request reaches `APPROVED`, the item is archived exactly once.
2. **Double-fire on the final approval (§1, MEDIUM/HIGH)** — `ArchiveService.move()` now
   does an atomic `findAndRemove` on the item first; a losing concurrent caller gets `null`
   back and a 409 instead of creating a second `ArchivedItem`. Covered by the same
   concurrency test above (asserts the item is removed from `items` exactly once).
3. **Member leaving mid-vote (§1, MEDIUM)** — added `ArchiveRequestService.onMemberLeft`
   (new `Status.INVALIDATED`, new `NotificationType.ARCHIVE_REQUEST_INVALIDATED`), mirroring
   `CostConfigChangeService`/`ApprovalService`'s own handling. New API test
   (`aMemberLeavingMidVoteInvalidatesThePendingRequestInsteadOfImplicitlyApprovingIt`) and
   verified the underlying "Bell" notification renders it correctly — **while wiring this
   up, found and fixed a pre-existing bug**: the shared `Bell.tsx` notification popover's
   type union only recognized `COST_CONFIG_INVALIDATED` among the "invalidated" types;
   `ORDER_FINALIZATION_INVALIDATED` and `PROFIT_DISTRIBUTION_INVALIDATED` already existed on
   the backend but silently rendered nothing. Fixed all four (plus the new archive one) with
   one generic `INFO_ONLY_TYPES` list instead of an enumerated condition.
4. **Owner Workload unreachable on mobile (§3, HIGH)** — the mobile tabbar's 4th icon
   (`TeamGlyph`) was wired to `/backlog/groups`; changed to `/backlog/team`, matching the
   icon's actual desktop meaning. Group management stays reachable via the header's
   `GroupSwitcher` (already present in every viewport, confirmed by reading its code and
   the CSS — nothing hides it on mobile), so nothing was lost. Verified live at 375px: the
   tabbar link now reads "Team workload" and lands on the workload view.
5. **Completed Items losing Category on mobile (§3, HIGH)** — `.data-table .cell-cat` no
   longer hides on mobile; it renders as a small subtitle line under the title instead
   (matching `.cell-subtitle`'s existing treatment elsewhere). This also fixes the same
   latent loss on the main Items list, which shares the same CSS class. Verified live at
   375px: "Project" now shows under the item title on both Archive and Items cards.
6. **Icon-only priority indicator (§3, MEDIUM)** — `PriorityMark` now carries
   `role="img"` + `aria-label="Priority: {level}"` (it previously relied on `title` alone,
   which never fires on touch); `EffortIcon` already had an `aria-label` and got the same
   `role="img"` hardening. `DueMark` was re-checked and found to already render real visible
   text, not icon-only — no change needed there. Verified live via a DOM query.
7. **Archive's "Read-only (§24)" spec citation (§3, MEDIUM)** — reworded to plain language:
   "Read-only — once an item lands here it can't be edited or brought back to the active
   list." Verified live.
8. **Edit-locking design drift (§3, MEDIUM)** — confirmed (again, directly) that no
   pessimistic edit-lock exists anywhere in the backend either — this was never a
   frontend-only gap. Rather than build the original §19 pessimistic-lock design from
   scratch (a disproportionate amount of new infrastructure for a personal/small-team
   backlog tool, and the existing optimistic-`@Version`-plus-clear-error-message behavior is
   a reasonable trade), `docs/design.md`'s §19 was corrected to describe what's actually
   implemented, with the original spec kept in a collapsed `<details>` block for the
   historical record instead of silently drifting from reality.
9. **`ProfitDistributionService`'s untested rounding rule (§2, MEDIUM)** — added
   `anUnevenThreeWaySplitAbsorbsTheRoundingRemainderOnTheLastRecipient`: a 1:1:1 split on
   ₹100.00 (100/3 = 33.333...) now actually exercises the remainder-absorption branch
   (33.33 + 33.33 + 33.34 = 100.00 exactly), which the existing evenly-divisible 2:1 test
   never did.
10. **`ExpensesPage`/`IncomePage` duplication (§2, LOW)** — extracted the identical
    bill-preview JSX into shared `BillPreview`/`BillFileInput` components
    (`components/BillAttachmentField.tsx`) and the identical "upload after create, don't
    fail the save if the attachment fails" dance into `imagesApi.uploadIfAny()`. Both pages
    are meaningfully shorter; their actual field-level logic (which genuinely differs) was
    left alone rather than forced into one generic form. Verified live: created a real
    expense through the refactored form end-to-end, no console errors.

**Not fixed — reasons below, under "Flagged, not fixed"**: the "zero categories blocks
item creation" finding was checked against the real seeding/fallback code and found to be a
false lead (categories always fall back to a non-empty default list, confirmed live in a
brand-new group) — no action needed, corrected here rather than left as an open item.
`MyInventoryPage.tsx`'s size, the missing bulk+components test, the untested inline
yarn-shortfall comparison, and the setup wizard's focus-on-continue gap were all left as
originally flagged — see that section for why.

---

## Re-verified from rounds 2 and 3 — all still hold, nothing has shifted

- **"Business" vs "Group" terminology** (round 2, user-deferred) — confirmed unchanged, no
  action needed.
- **Unbounded list pagination + the per-creator bulk-variant loop** (round 2) — confirmed
  still present, and now also true of every repository added since (Finance Tracker,
  Material Inventory, Product Catalog) — still low priority at this app's actual scale.
- **`InventoryService.adjustQuantity`'s atomic fix** (round 3) — re-verified still in place
  and still a genuine atomic `findAndModify`/`$inc`.
- **`TransferRequestService.fulfill()`'s soft over-fulfillment cap** (round 3) — re-verified
  the characterization still holds exactly: the business-rule cap can theoretically be
  raced, but the physical inventory invariant cannot, because that piece is separately
  atomic.
- **`ProgressRing`'s `aria-hidden` fix** (round 3) — re-verified still correctly scoped to
  just the decorative SVG, not the percentage text.
- **`SlideToggle`/`ProgressRing` file placement, missing bulk+components test, untested
  inline yarn-shortfall comparison, setup wizard's missing focus-move on "Continue"**
  (all round 3) — all confirmed still open, unchanged.

---

## 1. Security / correctness — Priority Backlog Tracker's own archival-approval flow

**FIXED (was HIGH) — archive-request approval is a plain read-modify-write race, in a codebase that
already has the fix for this exact bug class built and sitting unused.**
`ArchiveRequestService.approve()` (`src/main/java/com/backlogtracker/backlogtracker/archive/service/ArchiveRequestService.java:66-78`)
reads an `ArchiveRequest`, appends the approver's id to `approvedByUserIds`, and calls
`requests.save(req)` — no `@Version` field exists on `ArchiveRequest` at all, and there's no
retry loop. This is the identical shape to `CostConfigChangeRequest`'s approval race (found
and fixed in an earlier session) and `TransferRequest.fulfilledQuantity`'s race (found and
fixed in round 3) — except here, the fix already exists generically in this same codebase:
`commons/approval/service/ApprovalService.java` implements the same unanimous-approval
workflow with `@Version` + a retry loop on `OptimisticLockingFailureException`, and Finance
Tracker's `ProfitDistributionService` already uses it. `ArchiveRequestService` reimplements
the state machine from scratch instead, and drops the concurrency protection in the
process. Concrete failure: in a 4-member group with 2 approvals recorded, two more members
approving within the same window can have one save silently overwrite the other — one
member's vote is lost with no error shown to them, and the request never reaches unanimity
even though everyone in fact clicked approve.

**FIXED (was MEDIUM/HIGH) — the same missing guard lets the final approval double-fire the archive
move.** `ArchiveRequestService.evaluate()` (line 98-111) calls
`ArchiveService.completeApproved()` → `move()` (`ArchiveService.java:84-98`), which does
`items.findById` + save-then-delete with no version check or "already archived" guard. If
the *last* required approval is submitted twice (a double-click, two open tabs), both
threads can read a request that already satisfies unanimity before either has saved, and
both call `completeApproved` for the same item — producing two `ArchivedItem` documents for
one item before the first `deleteById` makes the second `findById` fail. Same pattern as
round 3's `InventoryEntry`/`settleUp` races, just in the original applet instead of a newer
one.

**FIXED (was MEDIUM) — a member leaving mid-vote is treated as an implicit "yes" instead of cancelling
the vote**, unlike the sibling feature that already handles this correctly.
`ArchiveRequestService` has no `@EventListener` for `MemberLeftGroupEvent` anywhere (grepped
the whole package — zero hits), while `commons/approval/service/ApprovalService.onMemberLeft`
explicitly invalidates a pending approval when a member leaves, "because a member leaving
mid-approval can reasonably change how the remaining members would have voted."
`evaluate()`'s unanimity check (`approvedByUserIds.containsAll(g.getMemberIds())`) means if a
non-approving member leaves, the *existing* approval set can suddenly satisfy the now-smaller
member list — the next `approve()` call by anyone (even a re-triggered evaluation from
someone who already approved) silently archives the item, treating the departure as consent
rather than grounds to cancel the request the way the newer, generic workflow does.

**Verified clean, not a finding:** Item CRUD is correctly group-scoped everywhere (every
read/write funnels through `requireMemberItem`/`groupService.requireMember` first — no IDOR
path found). There is no separate `EditLockService`/pessimistic-lock domain in this applet
at all; concurrent item edits are instead handled purely through Mongo's optimistic
`@Version` locking on `Item` (a legitimate, simpler alternative design — not a bug, just
worth noting since it means the original design spec's §19 pessimistic edit-locking feature
was never built, superseded by this approach instead). `ScoringService`'s ranking math is
guarded against division-by-zero and correctly clamps overdue items to max urgency; tie-break
ordering matches the documented spec exactly. `WorkloadService`/`InsightsController` scope
every query by group membership — no cross-group or personal-item leak found. Item id
assignment correctly reuses the shared atomic `CounterService`, not a home-rolled scheme.

---

## 2. Maintainability / QA — Finance Tracker, Material Inventory, Product Catalog

**FIXED (was MEDIUM) — `ProfitDistributionService`'s documented remainder-rounding rule is never
actually exercised by a test.** `computeAmounts()`
(`financetracker/profitsplit/service/ProfitDistributionService.java:207-220`) deliberately
gives the *last* proportional recipient `remaining.subtract(allocated)` instead of its own
division, specifically to absorb a non-terminating remainder (e.g. splitting ₹100 three
ways). But the only proportional test case in `ProfitDistributionServiceTest.java` is a 2:1
split on ₹300 — evenly divisible, so the remainder-absorption branch never actually has a
remainder to absorb. A future "simplification" of that last-recipient branch to also divide
evenly would pass every existing test while silently reintroducing a rounding shortfall.

**FIXED (was LOW) — `ExpensesPage.tsx` and `IncomePage.tsx` are structurally duplicated.** ~245 of
~275 lines differ in content, but the shape is identical: same load/reset/edit lifecycle,
the same bill-attachment upload-after-create sequence with the same error message, the same
CRUD-table rendering. Only the split-selection sub-form and field names actually differ. No
shared hook/component exists to absorb this the way this codebase has extracted shared
logic elsewhere (e.g. `validateSplits`). Not urgent on its own — a third page in this exact
shape would make it a clear case to extract.

**LOW — `MyInventoryPage.tsx` (625 lines) has grown large** relative to comparable pages
elsewhere (270-330 lines for `ExpensesPage`/`IncomePage`/`ColorwaysPage`), likely because the
yarn table, needle table, and per-member quantity editing all live in one component. Flagging
the size disparity as a signal worth a closer look, not a fully diagnosed problem — a
follow-up pass would need to actually attempt the decomposition to know if it's worth doing.

**Verified clean, not findings (some agent leads turned out false):** `LedgerEntryService`
already has tests for a 3-way split and a settlement that exactly zeroes a balance.
`YarnTypeService`'s duplicate-detection is already tested case-insensitively. Colorway
promotion of a null-pattern idea-box item is already covered and is a non-issue (`promote()`
never touches `pattern`). No copy-pasted balance/cost/promotion math exists across these
three applets worth deduplicating — their shapes (aggregate-over-collection vs.
append-on-change vs. flag-flip) are different enough that a shared abstraction would be
forced, not natural (consistent with round 3's own dismissal of a similar "rule of three"
lead for `Order.Component`). "Design decision" comment density in these three applets is
actually *higher* than Order Tracker's, not thinner. No TODO/FIXME or orphaned exports found.

---

## 3. Mobile / first-time-user — Priority Backlog Tracker's own core views

**FIXED (was HIGH) — Owner Workload is unreachable on mobile, and the icon that should lead there
instead silently goes somewhere else.** Desktop reaches `/backlog/team` via `LeftDock`'s
"Team workload" entry (labeled with `TeamGlyph`), but `LeftDock` is force-hidden below
980px (`index.css:1819-1830`). The mobile tabbar's 5-icon bar includes that *same*
`TeamGlyph` icon (`Layout.tsx:77-78`) — but wired to `NavLink to="/backlog/groups"`, not
`/backlog/team`. A mobile-first user has no way to reach Owner Workload at all; a user who's
also used the desktop app and recognizes the icon gets actively misdirected into group
management instead of the page they're expecting. This isn't a missing feature so much as a
navigation bug — the route exists and works fine once reached (verified: `TeamBody`'s layout
holds up at 375px, the "Unassigned" bucket renders clearly, empty state exists).

**FIXED (was HIGH) — Completed Items loses the Category column on mobile with no substitute and no
drill-in path to recover it.** `.data-table .cell-cat, .cell-lbl { display: none; }` inside
the 620px breakpoint (`index.css:891-894`) drops Category and the Effort/Due text labels
outright; `ArchivePage.tsx` renders both (lines 88, 91) but nothing shows them any other way
on a phone, and archive rows aren't clickable to open a detail view either — the information
is just gone. Same class of loss round 3 found in Material Inventory's per-member matrix
tables, here in a different shape (dropped columns rather than a broken card-collapse).

**ADDRESSED (was MEDIUM, docs corrected rather than a new feature built) — the original design spec's edit-locking feature (§19, pessimistic lock + 423)
appears to have never been built, on either side, and nothing in the app currently signals
a concurrent edit is in progress.** Grepped the whole backend for `EditLock`/`423`/`Locked`
and found nothing (confirmed above); the frontend likewise has no code path that requests,
displays, or disables inputs for a "someone else has this open" state —
`ItemFormModal.tsx` only reacts to a `409` version conflict *after* a failed save (lines
179-186), so a second editor can type a full edit before ever learning someone else already
changed it. This isn't necessarily a bug to fix — optimistic `@Version` locking is a
legitimate, simpler design than the original spec's pessimistic lock — but if this was a
deliberate substitution, the design doc should probably say so; if it was just never
finished, that's worth a decision either way rather than sitting silently different from
what §19 describes.

**FIXED (was MEDIUM) — icon-only priority/effort/due indicators on the Top 10 list depend on hover,
which doesn't exist on a touchscreen.** `PriorityMark`/`EffortIcon`/`DueMark` convey meaning
only through a `title` tooltip, never a visible label or `aria-label`. A first-time mobile
user sees three unlabeled glyphs per row with no way to learn what they mean short of
opening the edit modal for that item.

**RESOLVED AS A FALSE LEAD — "a brand-new group with zero configured categories/priorities
silently blocks item creation."** Checked `GroupCategoryService.effectiveCategories()`
directly: it falls back to `GroupCategories.DEFAULTS` (a real, non-empty list) whenever a
group has never edited its categories, rather than an empty list. Priorities live on the
single global `AppConfig` document, always seeded at startup. Confirmed live by creating a
brand-new group and opening the item form: both dropdowns had real options immediately, no
prior configuration needed. No fix required — this agent-flagged concern didn't hold up.

**FIXED — Archive's "unidirectional, can't be undone" rule was only signaled via a spec
citation.** See "Fixed after triage" above — reworded to plain language and verified live.

**Verified clean, not findings:** Needs Attention already has the exact "nothing to worry
about" empty state round 1 found missing in a different applet ("Nothing slipping. Nice and
calm."). Quick Wins holds up fine at mobile width with its own explicit empty state. The
item create/edit form's overall layout (not the dropdown content above) is correctly
single-column and scrollable below 480px. Archive and Quick Wins both have real, explicit
zero-states.

---

## What this round did not get to

- The maintainability/QA lens wasn't applied to Backlog Tracker's own code (this round's
  Backlog Tracker pass was security/correctness-only) — its own frontend/backend code
  quality is still unreviewed on that axis.
- No agent attempted to actually decompose `MyInventoryPage.tsx` to confirm whether a
  refactor is warranted — flagged by size alone.
- Whether new groups are seeded with default categories (which would resolve or confirm the
  blank-dropdown finding above) wasn't checked against the actual setup/seeding code — flagged
  as an open question, not verified either way.
