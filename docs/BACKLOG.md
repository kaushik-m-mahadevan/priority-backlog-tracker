# Backlog — pending product decisions and design work

Living document. Tracks everything decided-but-not-built, proposed-but-not-decided, and
explicitly-deferred, so none of it gets lost between sessions. Update this file whenever a
new item is decided, started, or finished — don't let decisions live only in chat history.

Source context: `docs/REVIEW_FINDINGS_ROUND4_2026-09-18.md` (technical panel review), the
"Everything But The Hook" automation audit (five-round, multi-persona UX/automation review,
2026-09-18 — published as a Claude artifact, not checked into the repo; ask the project owner
for the link if it's needed again), and a 2026-09-19 seven-agent code-quality/SOLID audit and a same-day five-agent test-coverage
audit, both covering every backend package and every frontend area (findings folded into the
"Code quality" and "Testing" sections below).

---

## Testing — coverage backlog (2026-09-19 audit)

Five agents read every backend package's production code against its actual test file, and
the whole frontend against its actual (very sparse — 4 unit/component test files + 2
Playwright e2e specs) test suite. Organized by priority. As with the code-quality section,
nothing here needs a product decision — proceed on engineering judgment, and prefer the
**"keep as template"** patterns below when writing new tests rather than inventing new
conventions.

### Highest-value gaps — real business rules with zero test coverage anywhere

- **`ConfigService.addPriority`/`removePriority` (Backlog Tracker) — zero tests, any layer.**
  All three safe-removal branches (block/reassign/delete-outright) are completely untested,
  even though the *identical* logic on the category side (`GroupCategoryService`) is well
  tested. This is the single largest gap in the whole audit.
- **`CustomerService.search` (Order Tracker) — the entire blind-index duplicate-detection
  feature has zero tests.** Not exercised by any endpoint test, at any layer.
- **`OrderBusinessRules.requireSplitAllocationSumsToQuantity` — a named, user-facing
  validation rule with zero test coverage**, on both the create and bulk-update paths.
- **`TransferRequestService`'s `unreserve()` compensating-rollback path — the one behavior
  its own class-level javadoc specifically calls out — has zero coverage.** No test forces
  the inventory-move step to fail after a successful reservation.
- **`BlindIndexService` (commons/crypto) — no test file exists at all.** Hash normalization,
  null/blank handling, and domain-separation are entirely unverified.
- **JWT token expiry has never been tested, and currently *can't* be tested deterministically
  — `JwtService` calls `Instant.now()` directly** instead of the injected `Clock` bean every
  other time-sensitive service in this codebase correctly uses (`ApprovalService`,
  `ImageService`, `ArchiveService`, `ScoringService`, `AgingService`, `ConfigService` all take
  `Clock`). Fix the injection first, then add the expiry test it unblocks.
- **`BusinessConfig.bufferDaysFor` — zero direct tests; 3 of the 4 `DeliveryTier` values
  (`SAME_STATE`/`OTHER_STATE`/`INTERNATIONAL`) are never exercised by any test in the
  codebase, and the "configured value is 0, fall back to the built-in default" branch is
  never hit either.**
- **`hourlyWageConfirmed`'s effect on a real order's price is never verified** — the
  confirm/unconfirm *flag* is well tested (`CostConfigChangeApiTest`), but no test actually
  re-prices an order before/after confirmation to prove `effectiveHourlyWage()` changes what
  a customer is quoted.
- **Day-boundary threshold tests are missing everywhere aging/staleness math exists** —
  `AgingService.needsAttention`'s `staleThresholdDays`/`buriedThresholdDays` exact boundaries
  (14/15, 30/31) are never tested (existing tests use values comfortably far from the
  boundary on both sides); same gap pattern in `AgingService.health()`'s stage thresholds
  (3/6/10).
- **`OrderService.updateStatus` has zero test coverage** — worth adding as a documented
  baseline of today's "any status → any status, no state machine" behavior before the Kanban
  workflow rework changes it, plus the untested `actualDeliveryDate` auto-stamp-once behavior.
- **Payment-status exact boundaries are never tested with real numbers** — `net == finalCost`
  exactly, and `finalCost ± 1`, are all untested; existing tests use round numbers with wide
  margins from any boundary.
- **Non-member-caller authorization is a codebase-wide blind spot in Material Inventory,
  Finance Tracker, and Product Catalog** — every service method is gated by
  `groupService.requireMember(...)`, and *none* of the five test files covering those three
  applets test what happens when the caller isn't a member (as opposed to being a member
  acting on the wrong resource, which mostly *is* tested).
- **Group-isolation (non-member access) is entirely untested in Backlog Tracker's own test
  suite** — no test anywhere has a second, non-member user attempt to hit any Backlog Tracker
  read/write endpoint for a group they don't belong to.

### Concurrency — claimed in comments, never actually raced

- **Two of three explicit "this guards against a concurrent race" claims in `commons` have
  no concurrent test at all** (only sequential/single-threaded tests exist):
  `ApprovalService.approve`'s optimistic-lock retry loop, and `GroupLinkService.link`'s
  duplicate-key race handling. (The third, `CounterService`, **is** well-tested with real
  threads — see "keep as template" below.)
- **`CostConfigChangeService.approve`'s retry loop (Order Tracker) has the same gap** — no
  concurrent test proves two members approving at once doesn't lose an update.
- **The four concurrency tests that *do* use real thread pools** (`TransferRequestServiceTest`
  ×2, `MaterialInventoryServiceTest`, `LedgerEntryServiceTest`) **all share a structural
  weakness**: none use a `CountDownLatch`/`CyclicBarrier` to force genuinely simultaneous
  submission — they rely on `ExecutorService.invokeAll` alone, which only *probably* overlaps.
  Strengthen with a latch-gated "release all threads at once" pattern rather than rewriting
  from scratch.
- **`ArchiveRequestServiceTest`'s concurrent-approval test checks the live-item side of the
  archive race but never asserts no duplicate `ArchivedItem` was created** — the exact
  failure mode `ArchiveService.move`'s own javadoc says the atomic `findAndRemove` prevents.

### Frontend — the real gap is breadth, not quality

The 4 existing test files (`format.test.ts`, `tz.test.ts`, `OrderFormFields.test.ts`,
`OrderFormFields.component.test.tsx`) are genuinely well-written — real assertions, no
snapshot tests, no "renders without crashing" filler, good boundary-case habits where they
exist at all. The problem is almost everything else in the frontend has **zero** test
coverage:

- **Every custom hook** — `useSetupGate` (decides which of 3 very different UIs a business
  sees — 0% covered), `useLinkedYarnTypes`/`useLinkedNeedleTypes`/`useMyYarnInventory`/
  `useMyNeedleInventory` (same cancel-on-unmount/error-fallback shape 4 times, one test
  pattern would cover all four), `useBusiness`/`useAuth`/`useGroups` context hooks,
  `useKeepAlive`, `useItemsChanged`.
- **Components with real branching logic** — `OrderDueDate` (the overdue boolean, cheap and
  high-value), `TimeTracking`'s slider math (`roundToStep`/`clampHours`/
  `valueFromClientY`), `ProgressRing`'s clamping, `Connections`' full link/unlink state
  machine, `Bell`'s actionable-type branching, `ItemFormModal`'s validation table and
  dirty-check diff.
- **`api/client.ts`'s `request()`** — the one module every network call in the app funnels
  through — has zero tests despite being trivially testable today (mock `fetch`) with no
  extraction needed.
- **Business logic still tangled inline in `OrderDetailPage.tsx`'s render body** (the
  hours-percentage calc, the per-creator ETA math) is currently untestable without a full
  component-render harness — extracting it (already recommended in the code-quality audit for
  unrelated reasons) would unlock testing the highest-risk, most duplicated calculation logic
  in the frontend as a side effect. Do the extraction once, get both benefits.
- **`OrderFormFields.tsx`'s own `AddOnsFields`/`ComponentsFields`/`duplicateVariant`/
  `blankComponent`** sit right next to the well-tested `validateSplits`/
  `MandatoryItemsFields` in the same file, completely untested.
- Two Playwright e2e specs exist (`core-flow.spec.ts`, `order-tracker-flow.spec.ts`) — thin
  but real smoke coverage for Backlog Tracker's core loop and Order Tracker's individual-order
  path. No e2e coverage at all for Finance Tracker, Material Inventory, Product Catalog, or
  Order Tracker's bulk-order path.

### Redundant / overlapping tests to consolidate

- `OrderCalculatorTest.individualEstimateFollowsTheRound5CostFormula` and
  `.laborCostAndDeliveryFormulaMatchTheWorkedExample` are near-duplicates post-rework — merge
  or narrow the first to only the "no overhead cost line" assertion it's uniquely there for.
- `TransferRequestServiceTest`'s two concurrency tests share near-identical setup boilerplate
  — worth parameterizing into one test with two cases rather than true duplication.
- `YarnTypeService`/`NeedleTypeService` test files re-derive nearly line-for-line identical
  "duplicate on create"/"any member can edit" scenarios — a shared parameterized base would
  cut ~40 lines per file with no coverage loss.
- `GroupLinkServiceTest.enforcesOneFinanceGroupPerBusinessAndOneBusinessPerFinanceGroup` and
  `RegisterApiTest.rejectsABadHandleOrShortPassword` each bundle several unrelated assertions
  into one test method — split so a failure in one doesn't silently hide a regression in the
  others.

### Weak/brittle assertions to strengthen

- `OrderApiTest.bulkSplitAndBatchStageProgressRollUpIntoCompletionPercentage` asserts only
  `completionPercentage &gt; 0` — should assert the exact expected percentage, given the
  underlying math is already precisely unit-tested elsewhere.
- `UserHandleLegacyDataTest.resavingTwoLegacyUsersWithNoHandleCollide` and a few
  `GroupLinkServiceTest` tests assert only "an exception was thrown" / "is a
  `ResponseStatusException`" with no message or status-code check — a wrong-but-still-an-error
  regression would pass silently.
- `TransferRequestServiceTest.rejectsFulfillingMoreThanTheTargetHasOnHand` has no message
  assertion, unlike every sibling test in the same file — could silently swap with the
  adjacent "more than requested" 400 path without failing.
- Several archive-request-lifecycle tests never re-fetch the item's own fields
  (title/priority/category) to confirm they stay untouched while a request is pending, despite
  that being an explicit design invariant.

### Test organization

- `OrderApiTest` (Order Tracker) is trending into a dumping ground — individual CRUD, bulk
  CRUD, stage-assignment, payments, shipment-plan, and change-log assertions all in one file,
  while time-log and finalization were correctly split into their own files. Consider a
  dedicated pricing-focused split given how much round-5 logic lives here now.
- No `BusinessConfigTest` pure-unit-test file exists — `effectiveHourlyWage()`/`bufferDaysFor()`
  are only proven indirectly through full order-creation integration tests.
- `ConfigApiTest` mixes config-weight tests and group-category tests in one file where every
  other pairing in the codebase (e.g. `ArchiveRequestApiTest` vs. `ArchiveApiTest`) is
  correctly split by production class.

### Keep as the template for new tests

- **`CounterServiceTest.concurrentCallsProduceNoDuplicates`** — real `ExecutorService`, 50
  tasks across a 16-thread pool, asserts the exact `1..n` sequence with no duplicates. The
  strongest concurrency test in the codebase; new concurrency tests should match this shape
  (and add the latch-gating improvement noted above).
- **`ScoringServiceTest`'s `Clock.fixed(NOW, ZoneOffset.UTC)` pattern** — the correct way to
  test date-boundary logic deterministically; `InsightsApiTest` and others relying on real
  `Instant.now()` arithmetic should be converted to this pattern, not just given wider
  thresholds to paper over the flakiness risk.
- **`OrderFormFields.test.ts`'s `validateSplits` coverage** — genuinely thorough (exact-sum,
  under/over-sum with message-substring checks, duplicate creators, blank rows ignored,
  multi-variant correctness) — the bar every new frontend logic test should clear.

---

## Code quality — internal engineering backlog (2026-09-19 audit)

Everything below is **purely internal** (no product-visible behavior change intended) unless
explicitly marked "user-visible" or "bug" — those should be fixed with the same rigor as any
other bug (test, verify, commit separately). Nothing here needs a product decision; proceed
on engineering judgment.

### Real bugs found during the audit — fix these first

- **Data loss**: `EditOrderForm`'s save (`OrderDetailPage.tsx`) always sends
  `itemizedPackaging: []`, permanently erasing any itemized packaging lines on every edit of
  an individual order, regardless of what was there before.
- **Crash risk**: `EditBulkDetailsForm`'s "+ Add variant" button calls
  `duplicateVariant(prev[prev.length - 1])` with no fallback — throws if `variants` is ever
  empty. `NewOrderPage.tsx`'s equivalent button already guards this
  (`?? blankVariant()`); apply the same fix here.
- **Wrong data recorded**: every payment recorded from the order page is hardcoded to
  `mode: "UPI"` regardless of how it was actually paid — no UI exists to pick another mode
  even though `PaymentView.mode` supports any string.
- **Silent failures**: the payment-record button, the status-change dropdown, and the
  stage-progress inputs on `OrderDetailPage.tsx` call the API with no try/catch at all —
  every other mutation on the same page (edit forms, finalization) surfaces `err.message` via
  an `error` state; these six call sites just fail invisibly.
- **Audit-trail bug**: `ConfigService.addPriority` hardcodes `changedBy: null` in the config
  history — every other config change (including `removePriority`) correctly records the real
  actor. The "added priority" row in Settings' audit history permanently shows no one made it.
- **Misleading public API**: `OrderController.myWork`'s query parameter is named `status` but
  actually means a completion filter (`"pending"`/`"done"`/`"all"`) — nothing to do with
  `OrderStatus`. Rename to `completionFilter` to match the service method it forwards to
  (breaking change for any existing caller — coordinate with the frontend call site).
- **Latent correctness risk**: `WorkloadService.HOT_PRIORITIES` hardcodes `"Critical"`/`"High"`
  as literal strings instead of deriving from the business's actual configurable priority
  list — renaming a priority silently zeroes out workload's "hot" count with no error.
  Similarly, `CompletionStatsService` hardcodes `"RESOLVED"`/`"ARCHIVED"` instead of using the
  existing `TerminalStatus` enum.
- **Worse error messages than necessary**: `imagesApi.ts`'s `list`/`remove` always throw a
  generic `"Failed (status)"` instead of surfacing the backend's real error message, unlike
  every other API call in the app (which consistently parse `data.message`).

### Architecture violations — larger internal refactors

- **`commons.notification.NotificationService` directly imports and depends on
  `backlogtracker.archive.ArchiveRequest`** — a domain class from one specific applet — which
  contradicts a rule this codebase states explicitly elsewhere ("commons never imports an
  applet package"; "an applet depends on commons, never the other way around"). Three public
  methods take `ArchiveRequest` as a parameter. Fix: generalize `NotificationService`'s API
  (primitive fields or a small internal DTO) and move the archive-specific wiring into
  `backlogtracker.archive`, mirroring how `CostConfigChangeService`/`OrderFinalizationService`
  already call `NotificationService.info(...)` generically.
- **`DemoDataSeeder` lives inside the Backlog Tracker applet's own package
  (`backlogtracker.backlogtracker.demo`) but imports and seeds Order Tracker, Finance Tracker,
  and Product Catalog data too** — the same "an applet never imports another applet" rule
  violated from the opposite direction. Move to a platform-level package (e.g.
  `com.backlogtracker.demo`), no logic change needed.
- **`ArchiveRequestService` reimplements the same unanimous-approval state machine that
  `ApprovalService` already generalizes** (its own javadoc says it was extracted from
  `CostConfigChangeService` specifically so future features wouldn't have to reimplement
  this) — ties directly into the already-queued "consolidate the three approval
  implementations" item; this audit found the exact duplicated pieces (retry-loop structure,
  unanimity check, member-leave invalidation) to work from.

### Duplication → missing abstractions (medium/large, purely internal)

**Backend:**
- `InventoryService` vs `NeedleInventoryService` — ~40 of `InventoryService`'s ~130 lines
  (list/mine/the upsert-or-delete-on-zero shape) are structurally duplicated verbatim.
- `YarnTypeService` vs `NeedleTypeService` vs `ColorwayService` — the identical
  "CRUD with natural-key-uniqueness-check" template implemented three separate times
  (list/requireById/duplicate-check-on-create/duplicate-check-on-update/requireText all
  near-identical across all three).
- `ConfigService.removePriority` vs `GroupCategoryService.removeCategory` — the same
  "block if 2+ uses, require reassignment if exactly 1, remove if 0" rule duplicated with
  even the two duplicate implementations diverging stylistically from each other.
- Three byte-identical `sha256(String)` helpers (`JwtService`, `AesGcmCipher`,
  `BlindIndexService`) — trivial, safe to extract, zero behavior change.
- `requireQuarterStep` duplicated verbatim between `InventoryService` and
  `TransferRequestService` (same epsilon, same check, same error message text).
- `OrderCalculator.estimateIndividual` vs `.priceVariant` — both compute the identical
  materials→addOns→packaging→labor→gross→profit→final pipeline; this is the single
  highest-risk duplication in the app given the formula was just reworked this session —
  a future pricing tweak applied to one and not the other would be very easy to miss.
- `OrderService` (~880 lines) — time-log handling (~90 lines) and change-log
  logging (~30 lines) are self-contained enough to extract into `TimeLogService`/
  `OrderChangeLogService` collaborators, called from `OrderService` as a facade.
- A "find-by-id-or-404, optionally check status or 409" helper shape repeats 6+ times across
  `ApprovalService`, `CostConfigChangeService`, `ArchiveRequestService`, `NotificationService`,
  `PasswordRequestService`, `UserService`.
- An "enum-from-request-string with a friendly 400 message" helper repeats 3 times
  (`ItemController` ×2, `ArchiveController`).
- A pagination page/size clamp (`Math.max(0,page)` / `Math.min(Math.max(1,size),200)`)
  repeats verbatim in `ItemQueryService` and `ArchiveController`.
- `ItemStatus.LIVE`-equivalent list (`List.of(BACKLOG, IN_PROGRESS)`) duplicated verbatim in
  `ItemService` and `ItemQueryService` — should be one constant, ideally on the enum itself.

**Frontend:**
- **`OrderDetailPage.tsx` is 1743 lines holding 5 components** (a generic `Section` wrapper,
  both edit forms, the detail page itself with 7 un-memoized derived-calculation blocks, plus
  `FinalizationCard`/`ShippingCard`) — split into a `useOrderDetail`/`useOrderDerived` hook
  pair plus one presentational component per existing `<Section>` block. Do this alongside
  (not instead of) the already-queued 3-form-merge — they touch the same file.
- **`MyInventoryPage.tsx`'s yarn table and needle table are near-total copy-paste** (~250
  lines) — a generic `PerMemberQuantityTable` component would collapse both, and should be
  built **before** the inventory-reservation feature adds a third near-identical table.
- **Four "linked Material Inventory" hooks** (`useLinkedYarnTypes`/`useLinkedNeedleTypes`/
  `useMyYarnInventory`/`useMyNeedleInventory`) are ~95% identical — one generic
  `useLinkedMaterialResource` hook replaces all four.
- **Finance Tracker / Material Inventory / Product Catalog's Context+Layout+Root triads are
  ~950 lines of near-total copy-paste**, differing only in identifier names/strings — a
  `createAppletGroupContext(appletKey, storageKey)` factory (+ matching Layout factory) could
  produce all three from one implementation while preserving the intentional per-applet
  isolation. Largest line-count win in the whole audit; also the highest-risk (touches
  group-switching in 3 apps) — do with real test coverage, not a rushed pass.
- Four duplicated "dismissable dropdown" implementations (`NavMenu`, `Connections`,
  `GroupSwitcher`, and `Bell`) — a shared `useDismissableMenu()` hook removes ~60 duplicated
  lines. **Note (2026-09-19 UI/UX audit):** `GroupSwitcher` is being removed entirely (replaced
  by the new applet group/business chooser screen) and `Connections` moves from a header
  popover to the group's own home page — reduce this consolidation to whichever of
  `NavMenu`/`Bell`/`Connections` are still genuine dropdowns once that redesign lands, rather
  than building for four when it'll really be two or three.
- No shared `formatMoney()` — 7 files, 27 occurrences of hand-rolled `₹${n.toFixed(2)}`,
  including three subtly different variants (one handles negative amounts, others don't).
  Build this **before** the ledger Debit/Credit rewrite lands, so the new ledger UI doesn't
  clone the same hardcoded-symbol problem into new code.
- `ColorwaysPage.tsx`'s new-colorway and edit-colorway forms duplicate the same 5 fields —
  one shared `ColorwayForm` component.
- No shared `Pager` component — the exact same prev/next pagination block is duplicated
  verbatim between `ItemsPage.tsx` and `ArchivePage.tsx`.
- No shared loading/error/empty-state convention — `"Loading…"`/`className="error"` are
  reimplemented ad hoc in nearly every page across every applet; Backlog Tracker's
  `EmptyLeaf` component isn't reused by the other three applets at all.

### Small / trivial cleanups

- Hardcoded stage keys `"crocheting"`/`"assembly"` appear as free-standing string literals in
  both `BusinessConfig.java` (backend) and `OrderDetailPage.tsx`/`STAGE_ICONS` (frontend)
  instead of referencing one shared constant/set — silently stops matching if a stage is ever
  renamed (no edit UI exists today, but the field is technically editable).
- Location/creator/order-type code digit-widths (3, 3, 2) are re-hardcoded independently in
  `CreatorService`, `LocationCodeService`, and `OrderNumberService.pad(...)` with no shared
  constant tying them together — a width change in one place would silently truncate order
  numbers rather than fail loudly.
- `BusinessConfigService.seed()` hand-rolls its own random-two-digit-code generator instead of
  reusing `RandomCodeAssigner` (can't reuse directly since both codes must differ from each
  other, not from other documents — a small overload would unify them).
- `finalCost` derivation (`order.orderType === INDIVIDUAL ? costEstimate... : bulkDetails...`)
  is duplicated verbatim twice in `OrderService.java`.
- `CustomerService.create`/`.update` copy the same 8 fields via two different mechanisms
  (builder vs. setters) instead of one shared `applyFields` helper.
- Collection name string literals (`"items"`, `"archivedItems"`) are hardcoded across 4 files
  with no shared constant referencing the real `@Document` names.
- `CounterService.nextSharedItemId()` (a Backlog-Tracker-specific ID format, `"ITM-%03d"`)
  lives in the generic `commons.counter` package — belongs in `backlogtracker.item`.
- Dead code confirmed via full-repo grep, safe to delete: `InventoryService.quantityOf`,
  `RankingService.DEFAULT_LIMIT`, `GroupRepository.findByMemberIdsContaining`/
  `countByMemberIdsContaining`, five unused Backlog Tracker repository methods
  (`ItemRepository.findByItemId`, `ArchivedItemRepository.findByGroupId`/`findByItemId`,
  `ArchiveRequestRepository.findByItemIdAndStatus`), `lib/format.ts`'s `score()` export, and
  the stale `@Version` field/comment on `TransferRequest` (already tracked above).
- `UserSummary` vs. `UserView` (backend) and `User` vs. `UserSummary` (frontend) are
  near-duplicate "safe user projection" types with a subtle, likely-accidental divergence
  (`handle` required in one, optional in the other) — worth unifying or documenting why two
  exist.
- Terminal-status literal arrays (`["RESOLVED","REJECTED","ARCHIVED"]`) are re-declared 3
  times in the frontend instead of deriving from the one place the union type is actually
  defined.
- `capitalize`/title-case logic duplicated 4 times in the frontend instead of one
  `lib/format.ts` helper.

### Confirmed correct — do not touch

- Every atomic Mongo `findAndModify` concurrency pattern (`CounterService`,
  `InventoryService.adjustQuantity`/`withdraw`/`deposit`, `TransferRequestService.
  reserveFulfillment`, `ArchiveService.move`) — these are deliberate, correctly-implemented
  race-condition fixes. Any refactor touching the same files must preserve these exact
  query/update shapes, never "simplify" them into read-then-write.
- `Order.java`'s many nested static domain classes — consistent with this codebase's own
  established convention for aggregate roots (confirmed by comparison with
  `OrderChangeLog`/`BusinessConfig`), not a violation.
- `OrderView.java`'s manual DTO mapping (~220 lines of explicit `of()` factories) — the right
  call at this scale; introducing a reflection-based mapper here would trade explicitness
  (especially around where `EncryptedString` gets decrypted) for a small line-count win.
- Finance Tracker / Material Inventory / Product Catalog's separate per-applet Context/Layout
  — an intentional platform decision (applets stay decoupled), not an oversight; the factory-
  function idea above removes the *duplication* while preserving the *separation*, it doesn't
  undo the decision.

---

## Shipped

### Estimate & delivery-date breakdown redesign
**Status:** built, tested, live-verified, and pushed (commit `01c6b89`) 2026-09-19.

Full itemized cost breakdown (materials / add-ons / packaging / labor / profit margin) and a
full itemized delivery-date breakdown (allocated hours ÷ hours/day + delivery buffer + time
overhead), replacing today's bare-total estimate. Decided shape:

- **Cost formula:** `gross = materials + addOns + packaging + laborCost`, where
  `laborCost = totalHours × hourlyWage` (all four hour categories — research, crafting,
  assembly, packaging — are paid labor). Profit margin % applies on top of gross. **No
  overhead cost line** — overhead is a time concept only, not a cost concept, in this design.
- **Hourly wage:** new business-wide `BusinessConfig` setting, defaults to ₹100/h, goes
  through the same group-approval flow as overhead%/margin% changes today
  (`CostConfigChangeService`). Track a `hourlyWageConfirmed` flag (`false` until the group
  explicitly proposes/changes it) and show a visible warning on every breakdown that's still
  using the unconfirmed default.
- **Delivery-date formula:** `workDays = ceil(totalHours / hoursPerDay)`, then
  `+ deliveryBuffer` (flat days, keyed off the order's existing origin/destination location
  codes — same-city / cross-state / international tiers), then the combined total is
  multiplied by the time-overhead percentage and rounded up:
  `quotableDelivery = ceil((workDays + deliveryBuffer) × (1 + overheadPct))`.
- **Delivery buffer tiers:** for now, a plain editable text/number field per tier in Business
  Settings (not a real geo/logistics lookup yet — that needs research into actual courier
  tiers, e.g. Delhivery zones, before it's worth building properly). Sensible starting
  defaults: same city 1 day, same state 2 days, other state 3 days, international 5 days —
  all overridable.
- **Scope explicitly excludes invoice generation.** What's being built now is the *quotable*
  estimate shown to you and (eventually) the customer before/at order acceptance — a separate
  invoice-generation feature is deferred to later and designed separately.
- **Still worth double-checking during implementation:** the worked example used to agree
  this shape used ~23% for a "20%" margin line (₹250 on ₹1,085 gross); implementation should
  use the precise percentage (₹217), not the illustrative rounding from the discussion.

---

## Approved designs, not yet started

### Order → Finance Tracker ledger auto-entry
**Status:** design finalized 2026-09-19, not yet built.

**The ledger becomes a real double-entry table, scoped tightly to actual cash movements
only — never conceptual/accrued entries.** Every row: date, description, amount, a **Debit**
party (who paid) and a **Credit** party (who received). Every row is *always* exactly one
Debit and one Credit — never more, never a multi-party row. A party is either a business
member, the "Business Account" (only selectable once a new Business Settings toggle —
*"Business account configured"*, in Business Settings — is on), the real customer's name
(pulled from the order, not a generic "Customer" label), or a free-text external label for
anyone else (e.g. a supplier) — with a suggestions dropdown of previously-typed external
names, sorted by how often each has been used. External parties are shown for clarity but
never enter the internal owed-to-whom balance, which is always computed live (sum of credits
minus debits per member), never stored as its own fact. This replaces the separate
Income/Expense pages with one unified table.

- **Shared/split expenses are not a ledger construct.** If a cost is shared between people,
  it becomes however many *real* transactions actually happen — each person pays the supplier
  directly, or one person pays and the others transfer their share back later, or the
  business account pays directly. An "equal split" calculator can stay as a UI convenience
  when logging an expense (e.g. "split ₹850 three ways" shows ₹283.33 each), but it always
  resolves to separate real transactions being entered, never a single combined row.
- **Order → ledger sync:** recording a `PaymentEntry` on an order auto-creates one ledger row
  (Debit the customer, Credit whoever the order's payment form says received it — a new
  "Received by" dropdown on the payment form itself, defaulting to the current user).
  Cross-applet delivery via a generic domain event (Order Tracker and Finance Tracker can't
  import each other) — first real consumer of `GroupLink`.
- **Removing a payment** (new feature — doesn't exist today; needed since this is now the
  undo path for an accidental entry) requires a confirmation and removes the matching ledger
  row.
- **A refund is its own new ledger row** (Debit whoever's refunding, Credit the customer),
  never an edit to the original payment's row — keeps history honest.
- **Linking** an Order Tracker group to a Finance Tracker group backfills every historical
  order's payments as ledger rows (idempotent via a `sourceRef` back to the `PaymentEntry`,
  safe to re-run). **Unlinking prompts** to keep or remove the already-created rows — no
  silent default either way.
- **Data migration:** the app is still in trial mode, so existing ledger/order data itself can
  be discarded or reshaped freely during this rework if that's simpler than a careful
  best-effort conversion. The one thing that must **not** change: the existing user accounts
  (one admin + three additional users) and which businesses/groups each of them belongs to.

**Pricing vs. profit-split are deliberately different numbers:**
- The customer-facing price still includes labor as a real cost line (materials + labor +
  packaging + add-ons + margin) — unchanged from the estimate/delivery redesign.
- **Leftover profit for splitting** = actual payment received − actual materials cost (from
  real Material Inventory usage × real per-skein cost, once that feature exists — see below)
  − packaging/add-ons. **Labor is deliberately excluded from this subtraction** — it's already
  priced into what the customer paid, and how long any one person took to finish their pieces
  is their own outcome (a fast finisher benefits, a slow one bears it), not a factor in the
  split. The owner takes no separate salary — only a share of this leftover at settlement.
- Split is purely by **pieces completed**, not hours logged (e.g. 4 of 10 pieces = 40%,
  regardless of time spent) — matches the existing profit-split default formula.
- The order page gets a read-only, **personal-only** "what I'll make from this order"
  preview (your pieces ÷ total pieces × leftover) — not the whole business's picture; for
  that, the ledger itself is the source of truth. A full account-balances/statement view is
  deferred (not designed yet).

### Order → Material Inventory decrement, with reservation
**Status:** design finalized 2026-09-19, not yet built.

**Yarn-only.** Needles/hooks are a durable tool, not a consumable — they never get used up by
a project, so none of this (reservation, usage logging, low-stock, transfer suggestions)
applies to them at all. Needles keep exactly the simple on-hand count that exists today.

- **Usage logging, not a single field:** log actual yarn usage against a material line any
  number of times (ideally once per ball/skein finished) — a running list on the order, same
  shape as the existing time-log entries. Independent of order status entirely (no tie to
  "Shipped" or any other stage).
- **Sync setting (auto/manual) is a per-user setting** (on the Creator profile, not
  business-wide) — each person chooses whether their own usage logging auto-applies to their
  inventory. Auto-sync on = every logged usage entry immediately adjusts the real inventory
  count, no prompt. Auto-sync off = entries accumulate; a **global "Sync to inventory" button**
  (one button per user, sweeping up pending usage across *all* their orders at once, not
  per-order) shows a summary of what it's about to deduct before applying it.
- **Reservation:** an order's planned/needed quantity reserves that much yarn the moment the
  order exists — Material Inventory shows e.g. "5 total — 2 reserved (Order #123) —
  3 available," not just a raw count. Logging usage eats the reservation first, then the
  crafter's own unreserved stash, then — if still short — checks teammates' stock (see
  below). Inventory is always per-person (there's no separate "business inventory" concept —
  every skein belongs to whichever member logged receiving it); for a **bulk order split
  across multiple creators**, each creator's own reservation is their proportional share —
  e.g. a variant needing 1 skein/unit with 8 units split 5-to-Alex/3-to-Priya reserves 5
  skeins from Alex's stash and 3 from Priya's, matching how everything else in this design is
  apportioned by pieces assigned.
- **Cancelling an order releases its reservation immediately**, no confirmation needed.
- **Going over the reservation is never blocked or flagged live** — planned-vs-actual is just
  visible afterward on the order itself, as a retrospective fact, not an active warning.
- **Low-stock warning, two separate tiers, same threshold (≤1 ball):** if a person's *own*
  stash of a yarn+colour drops to ≤1 ball, they get a flag suggesting "ask a teammate." If the
  *business-wide total* across every member's stash of that yarn+colour also drops to ≤1 ball,
  a separate flag suggests "place a reorder" — nobody has meaningful stock left at that point.
  Both are flags on the yarn itself, not tied to any specific order. Per-yarn custom
  thresholds is a later idea, not this round.
- **Smart transfer suggestion:** when a crafter runs short, the app checks teammates in the
  same location first (reusing the location data already used for order numbering), then
  further afield — but always shows every available option, just visually highlighting the
  most relevant (nearest) one rather than hiding the rest.
- Requires a real `GroupLink` consumer (see the ledger design above) to know which inventory
  group/user to touch — this is the second feature that needs that plumbing built.

### Order status workflow — a Kanban board, not a dropdown
**Status:** design finalized 2026-09-19, not yet built.

Replace the current free-choice "any status → any status" dropdown with a JIRA-style board of
four columns, each grouping the existing statuses:

```
PENDING              IN PROGRESS       COMPLETED              CLOSED
Inquiry                                Ready to Ship          Delivered
Confirmed            In Progress       Shipped                (Cancelled — see below)
```

- **Board and List views coexist** — a view toggle, defaulting to the board. The board only
  shows Pending/In Progress/Completed orders; the moment an order reaches a terminal state
  (Delivered *or* Cancelled) it drops off entirely and moves to the existing
  historic/completed-orders screen, which is a separate, complementary view.
- **The transition rule is column-based, not status-based, and strictly adjacent-only:**
  moving between two statuses in the *same* column (Inquiry ↔ Confirmed, Ready to Ship ↔
  Shipped) is always free. Moving to the immediately **next or previous column** is the only
  other kind of move possible — forward one column is free, backward one column requires a
  justification. **Multi-column jumps aren't possible at all, even with a reason** — a card
  can never skip a column in either direction. The justification prompt ("⚠ Moving this from
  Completed back to In Progress — what happened?") offers a few canned reasons (Item damaged,
  Rework needed, Customer changed request) plus a free-text "Other," saved into the order's
  existing change-log history.
- **Cancel is a separate, always-available action per order** (not a board column — e.g. a
  dedicated button/icon on the order), and is its own guided flow, not a simple status flip:
  1. Required reason (canned categories + free text).
  2. Any **already-logged** material usage stays permanently booked against real inventory —
     cancelling the order doesn't undo physically-consumed yarn/needles. Only the **unused**
     portion of the reservation is released back to available stock. (No new mechanism here —
     this is just the usage-log/sync pipeline from the inventory design above, working
     normally; cancellation only touches the reservation, never already-synced usage.)
  3. Logged time stays exactly as logged — a historical fact, never erased.
  4. Refund — reuses the existing payment-recording flow (type = Refund) for whatever amount,
     from ₹0 up to what was collected; a real refund becomes a real ledger row automatically,
     same as any other refund.
  5. An **informational-only** estimated-loss summary shown on the cancelled order (materials
     + unrecovered labor time) — deliberately *not* a ledger entry, since no cash actually
     moved for a pure loss (matches the ledger's real-payments-only rule). The one gap:
     add-ons have no inventory tracking at all today, so any add-on material consumed on a
     cancelled order can only ever show up in this informational summary, never as a real
     stock write-off — extending inventory tracking to add-ons would be a separate, bigger
     feature.

### Notification orchestration
**Status:** design finalized 2026-09-19, not yet started.

Notifications fire the moment something is *created* and needs someone's attention, not just
when it's cancelled — closes a real gap where cost-config/finalization/profit-split proposals,
password requests, and new signups today notify nobody until something falls apart.

- A cost-config, finalization, or profit-split proposal notifies every *other* current group
  member. A password request notifies admins. A new signup notifies admins (today: nobody).
- All of these join the "actionable" set that counts on the bell badge, alongside group
  invites and archive requests.
- Clicking one of these navigates to the relevant page (Business Settings, the order's
  Finalization card, Admin console) rather than trying to approve/reject inline in the bell —
  that inline UI already exists on two other notification types; a third copy isn't worth it.
- **Refactor alongside this:** a new, separate `NotificationOrchestrator` service sits above
  the existing `NotificationService` (which stays a dumb create/list/accept/decline store).
  The five proposal-services each call one clean method (`notifyGroupExcept(...)`,
  `notifyAdmins(...)`) instead of each hand-rolling its own ad hoc notification call — this
  centralization is what should have caught "new signups notify nobody" long before now.

### Single cross-applet search
**Status:** design finalized 2026-09-19, not yet started.

Scoped to **the current business and whatever's linked to it** (not literally every group
across every app) — one always-visible search box in the header, searching customers/orders
/yarn types/colorways across Order Tracker plus whatever Finance Tracker/Material Inventory
/Product Catalog groups are linked via 🔗. Results grouped by app with an icon, each linking
straight to the record. Plain substring matching — order volumes don't need anything fancier.
This is the second real consumer of `GroupLink`, alongside the ledger sync.

### Multiple saved addresses per customer
**Status:** design finalized 2026-09-19, not yet started.

Replaces the single `Customer.shippingAddress` string with a list of saved addresses per
customer, each labeled ("Home," "Office," "Mom's place — gift"), one marked default —
familiar shape from consumer delivery apps. An order's shipping card picks from the
customer's saved list (this *is* the "override" the current code comment promises but never
built) or adds a new one inline, same pattern as adding a brand-new customer inline today.

### Password-request duplicate handling, unified
**Status:** design finalized 2026-09-19, not yet started.

Both the in-app "change password" and the login-screen "forgot password" paths behave the
same now: submitting a new request while one's already pending asks *"You already have a
pending request — replace it with this one?"* — confirm cancels the old one and creates the
new one, decline leaves the existing request untouched. Replaces today's split behavior
(silent no-op on one path, a hard 409 on the other) with one consistent, visible choice.

---

## Queued for design discussion — approved to pursue, not yet designed

Confirmed as pure internal refactors — no product-visible behavior change expected from
either. Implementer's judgment call on the exact internal shape.

- **Consolidate the three separately hand-written "everyone must approve" implementations**
  (archive requests, cost-config changes, the generic `ApprovalService`) into one shared
  mechanism.
- **Merge the three near-duplicate order forms** (new / edit / edit-bulk) into one shared
  form.

---

## Small, ready to build — no further design needed

- Delete the stale `@Version` field + comment on `TransferRequest` (describes a locking
  mechanism superseded by the atomic rewrite).
- Add a confirmation dialog before deleting a logged time entry.
- Re-check a group invite's per-app membership cap at accept time, not only at invite time —
  if it's since become invalid, show the *invitee* a clear error ("this business is at its
  member limit — ask the group owner to raise it or contact an admin"), not the inviter.
- Consolidate the duplicated new-customer fields (declared once in order-creation, again on
  the standalone Customers page) into one shared component.
- Add a "Pending invites" list to the Manage Business/Group page, showing exactly who was
  invited (handle or email) — view-only for now, no cancel/revoke action. The toast itself
  stays as-is for now; revisit once real email delivery exists.

---

## Explicitly deferred, with a reason

- **Outside-the-app notifications** (email/SMS/push/any external channel). App is still in
  dev stage; revisit once there are real users depending on it.
- **Invoice generation.** Separate feature, designed separately, after the quotable-estimate
  work already shipped.
- **Delivery-buffer tiers as a real logistics/geo lookup** (e.g. actual courier zone data).
  Keep it a plain editable field for now; revisit once there's real shipping data to model
  against.
- **Recurring-expense templates in Finance Tracker.** This business has no recurring costs —
  everything is per-order. Revisit if that ever changes.
- **A real P&L / monthly summary view.**
- **Invite-a-stranger-by-email** (today you can only invite someone already registered).
- **Real Instagram/WhatsApp DM intake.**

---

## Rejected — decided against, don't revisit without a reason to

- **A start/stop crafting timer.** The app is meant to stay a logging tool, not something
  crafting itself depends on being open/online for.
- **Needle/hook transfer requests.** Intentional asymmetry with yarn — only yarn is meant to
  be shared between teammates.
- **Self-service path for the first user to become an admin.** Descoped — there's only ever
  one admin (the owner), who creates any others manually; not a real gap for this business.

---

## Confirmed intentional — not a bug, no change needed

- "Group" vs. "business" naming differing by applet — expected, per-applet terminology.
- The Connections (🔗) popover's "data will show up here too" promise — becomes true for the
  ledger and inventory-sync pairs once those ship; still aspirational for other applet pairs
  until they get real consumers too.
- The 30-day "stale" badge on a yarn entry — superseded by the real low-stock warning in the
  inventory-decrement design above; no longer needed as its own fix.

---

## Already well-designed — do not touch
- Automatic order numbering, existing cost/due-date auto-recompute plumbing.
- Component templates as a reuse mechanic.
- Photo-attached receipts/payment screenshots on ledger entries (the attach-a-file mechanism
  itself) — **note:** the 2026-09-19 UI/UX audit below adds direct in-app *camera capture* as
  a new way to produce that photo, additive to this existing attach flow, not a replacement.
- Approvals auto-resolving instantly for a solo business.
- Zero-quantity inventory rows disappearing automatically.
- The atomic Mongo `findAndModify` concurrency patterns (transfer fulfillment, inventory
  adjust) — do not simplify these away in the name of a new feature.

---

## Governing UI/UX design principles (2026-09-19)

Established during the mobile/web UI/UX audit below; apply these to all new UI, not just the
items that prompted them.

- **Header — exactly 4 fixed elements, nothing else:** home icon (→ console), applet name
  (→ that applet's own group/business chooser — see below), notification bell, avatar/profile
  menu. No business/group switcher and no Connections button in the header — the switcher
  moves to the new applet group/business chooser screen (below); Connections moves to the
  group's own home page, since it links one specific already-selected group and needs that
  active context (the chooser screen, listing multiple groups pre-selection, has no single
  "current" group for it to act on).
- **Bottom nav — max 5 icons, icon-only, one per function.** If an applet has more than 5
  destinations, the first 4 keep their own icon and a 5th "More" slot holds the rest. No text
  labels on primary nav.
- **Minimal-to-no horizontal scrolling**, especially on mobile — a table/list that can't fit
  needs a real narrower layout, not `overflow-x: auto`.
- **Icons over verbose text wherever a common action repeats** — e.g. "+ New X" buttons become
  a bare "+", labeled "Remove" buttons become a small corner "×", etc. Applies app-wide, not
  just to whichever screen first prompted it.
- **No redundancy** — don't show the same control, label, or piece of chrome twice on one
  screen.
- **Minimum 44×44px tappable area on every icon-only control**, achieved via padding/hit-area
  around the glyph, not by inflating the glyph itself.
- **Every boolean setting is a switch, never a checkbox.** Any accompanying description stays
  to one line max, on both phone and desktop — further explanation goes behind a "?" icon
  button, not inline expanded text.
- **Swipe gestures on list rows with one destructive + one promoting action** (first applied to
  Orders, extend elsewhere the same shape genuinely fits — Customers, Product Catalog,
  Material Inventory rows): swipe left launches the destructive flow (e.g. order cancellation —
  opens its full question flow, never a silent delete); swipe right promotes to the next
  adjacent state with a "Moved to [state]. Undo?" toast, not a blocking confirmation.
- **Applet group/business chooser screen** (new, one per applet): tapping the applet name in
  the header always lands here — a simple list of that applet's groups/businesses (tap to
  enter) plus a "+" to create a new one. Always shown, even when there's only one group —
  never auto-skipped, since the user may still want to add another. The group's own home page,
  once entered, no longer carries any switcher/create-new UI — that's now this chooser's job
  entirely.

---

## UI/UX audit — mobile & web (2026-09-19)

Full-app UI/UX review across mobile and web, mobile weighted more heavily since that's the
primary usage pattern. Discussed and finalized item-by-item before writing here, per the
project owner's request. All items below are approved designs, not yet built, unless noted.

### 1. Header overflow (measured, reproduced live)
Confirmed live at 375px viewport: header content overflows the viewport by 29px because it
carries more than the 4 fixed elements (business switcher + Connections button, on top of
home/applet-name/bell/avatar). **Fix:** the business switcher moves to the new applet
group/business chooser screen; the Connections button moves to the group's own home page
(it links one specific selected group, so it needs that active context — the chooser screen,
which lists multiple groups pre-selection, has no single "current" group for it to act on).
No new page logic needed beyond the chooser itself and relocating the existing Connections UI.

### 2. Material Inventory "Who has what" table — compact identity, no collapse needed
Confirmed live: the table adds one full column per group member with no cap, and already
risks horizontal scroll at today's 4-person businesses — directly against the no-scroll
principle. **Fix — a compact "nickname" identity instead of collapsing columns:**
- **Brand** — abbreviated text, max 5 characters.
- **Shade** — icon only (a colour swatch matching the yarn's recorded colour from a palette, or
  a blended swatch for a variegated/mixed colour, or the closest palette variant) — no colour
  name spelled out in the row. Tapping the swatch reveals the exact recorded shade text.
- **Size** — icon only, one glyph per standard weight class (lace / fingering / sport / DK /
  worsted / bulky / super bulky), same tap-to-reveal-detail pattern as Shade.
- With the identity column this small, the table always shows the full per-member breakdown
  directly — no accordion/expand-collapse needed.
- Same compact-identity + icon pattern applies to the Hooks & Needles table too, for
  consistency between the two.
- **Reservation breakdown (total/reserved/available, from the inventory-decrement design
  above) does not live inside this compact row at all** — it gets its own separate
  row/section per yarn type (below or beside the main quantity table), rather than being
  packed into the per-member quantity cell. Keeps the main table's row height uniform and
  compact regardless of whether reservation is active on a given yarn.

### 3. Order Tracker bottom nav — icon-only, 4 destinations
Confirmed via the actual code (`ordertracker/pages/`) that Customers is genuinely part of
Order Tracker (scoped `groupId`-per-business, encrypted per Order Tracker's own §4/§5 design —
not a generic CRM entity), while Product Catalog is a real separate applet. Final bottom nav
for Order Tracker: **Orders** (list by default, long-press → Kanban view, tap an order →
detail) — **New Order** ("+", center position) — **Customers** — **More** (Business Settings,
Manage Business, the historic/completed-orders archive). Only 4 destinations needed; no forced
5th icon.

### 4. NewOrderPage — collapsible sections
Reuses the existing `Section` component (already used by `OrderDetailPage`, defaults collapsed
on mobile) instead of exposing every field group at once. All sections collapsed by default on
mobile **except Summary, which starts open.**

### 5. ItemFormModal — full-screen on mobile
Below the existing mobile breakpoint (768px, matching `Section`'s own check), the modal goes
edge-to-edge as a full-screen sheet instead of a centered card with margins.

### 6. Bulk-variant nesting — flatten to a divided list
Bulk order variants currently render as cards nested inside a card. **Fix:** one outer
container, each variant as a row separated by a divider — no nested card chrome.

### 7. SettingsPage — sectioned and collapsible
Same `Section` collapse pattern as item 4, rather than a separate tab-strip jump-nav. Paired
with the new toggle/description principles above (switches not checkboxes; one-line
descriptions with a "?" button for more) — applied to every toggle/setting in the app, not
just this page.

### 8. Dropdown/popover viewport-overflow risk
Some dropdowns render partially off-screen near a viewport edge. **Fix:** edge-aware
positioning (flip/clamp within the viewport) added once to the shared dropdown/popover
component(s), not patched per call site.

### 9. Touch target sizing + swipe gestures
Systemic 44×44px minimum tappable area (see principles above), plus the new swipe-gesture
convention (left = destructive flow, right = promote with undo toast) — first applied to the
Orders list, extended elsewhere the same shape fits.

### 10. Sticky action bar on the order detail page
Orders list gets the swipe gestures from item 9; the order detail page (a single-item view,
where swipe doesn't apply the same way) additionally gets a sticky bottom action bar,
icon-only, so the primary action is always reachable without scrolling.

### 11. PWA installability — good to have
Add a proper manifest.json, icon set, and theme-color (plus, optionally, a basic service
worker for offline-shell caching) so the app becomes installable to a phone home screen.
Approved as a nice-to-have, not urgent — build when there's room, not before higher-priority
items.

### 12. Product Catalog — photo + link support
Catalog items get both an external image URL/link *and* direct photo upload with app-managed
storage — both, not either/or.

### 13. Camera capture for receipts (Finance Tracker) — good to have
Direct in-app camera capture on the ledger-entry form, using the same photo-upload
infrastructure as item 12, additive to the existing photo-attachment flow (see "Already
well-designed" above) rather than replacing it. Approved as a nice-to-have.

---

## Manual browser test findings — 2026-09-24 (mb-*)

Owner's own manual pass through the live app. Mix of bugs, UI polish, and net-new features;
sequenced roughly by risk/value, not strictly by number below. Two items (mb-12, mb-15) were
suspected already-fixed on inspection — verify by reproducing live before assuming the fix,
don't just trust the code read.

**Bugs**
- **mb-1**: Priority Backlog Tracker's assignee dropdown lists every user on the platform,
  not just the current group's members.
- **mb-3**: Needle types carry a unit-cost field in the UI; needles are a reusable tool, not
  a consumable — no cost concept belongs there at all.
- **mb-7**: Number `<input type=number>` fields change value on an accidental scroll-wheel
  pass, app-wide. Fix once, systemically (e.g. blur-on-wheel), not per call site.
- **mb-9**: A bulk variant's shown "hours logged/estimated" is the *per-unit* figure
  (`perUnitTimeHours`), not the variant's real total (`totalTimeHours` = per-unit × quantity,
  already computed server-side). Show the total; word the label so per-unit vs total is
  unambiguous.
- **mb-11**: A payment that exceeds the order's final/quoted price renders as a plain negative
  balance. Negative numbers are never the right UI for this — show a red "over budget by ₹X"
  state instead.
- **mb-12**: *Suspected already fixed, re-verify.* `OrderFinalizationService` already notifies
  other members on propose (`ORDER_FINALIZATION_PROPOSED`) and the proposer on invalidation
  (`ORDER_FINALIZATION_INVALIDATED`) — but nothing fires when a finalization actually resolves
  (reaches unanimous approval). That's the real gap: add a resolved/finalized notice.
- **mb-15a/b**: *Suspected already fixed, re-verify live.* ad-1 already built
  `PaymentSyncConsumer` (syncs every `addPayment`/`removePayment` to a linked Finance group)
  and a manual "sync historical payments" backfill button (Manage Finance Group page). Owner's
  live test found neither propagating. Reproduce end-to-end (does the group-link exist at
  payment time? does the backfill button actually call its endpoint?) before writing new code
  — this may be a wiring bug in already-shipped work, not a missing feature.
- **mb-19**: Business setup wizard's finance-tracker/material-inventory toggles don't actually
  create those groups even when switched on. *Investigated, could not reproduce* — a full,
  faithful walk through the real wizard UI (all 4 steps, both toggles on) correctly created
  and linked both groups; the network log and a follow-up GET on both links confirmed it.
  Left open pending more specifics from whoever hit this (which toggle, any error banner,
  first-time business vs. a retry).
- **mb-22**: Product Catalog can't link to a business or invite members — same shape of gap as
  Material Inventory (mb-20).

**Features (scoped)**
- **mb-5**: Mandatory-item yarn quantity is locked to quarter-skein steps (0.25/0.5/1...),
  which can't express a real yield like "11 pieces per skein" (~0.09/piece) without wildly
  overstating material cost. Needs a finer input path — reconsider the quarter-step floor for
  yarn specifically (needle/whole-unit counts are a different, correctly-integer case).
- **mb-6**: Crafting/assembly/research time is hours-only, stepped at 0.25 (15 min) — can't
  express e.g. a genuine 10-minute task. Add a minutes-capable entry path.
- **mb-10**: No warning when the computed/estimated delivery date exceeds the quoted-to-customer
  date. Add a visible warning state where both dates are shown.
- **mb-14**: Any member can link a business to a group today, unilaterally. Should require the
  same unanimous-approval flow this session's qd-1 just built into `ApprovalService` — a real
  fit, not a new mechanism.
- **mb-16**: Finance Tracker has no way to manually associate a ledger entry with a specific
  order, even when a business is linked — only the automatic payment-sync path exists today.
- **mb-17**: Block a second pending profit-split proposal on the same order while one is
  already pending (mirrors the existing single-pending-approval pattern everywhere else in the
  app) — confirmed scope, not "allow partial concurrent splits."
- **mb-18**: A profit-split proposal against a linked order should auto-populate the
  recipient/unit split from the order's own real split allocation, instead of requiring it
  re-typed by hand.
- **mb-20**: Material Inventory has no access control and no business-linking UI at all today
  — every member of every group can see/edit it. Needs the same link/permission model Order
  Tracker and Finance Tracker already have.
- **mb-21**: Let a member log inventory *for* another member with a propose/accept handshake
  (not a direct set) — e.g. "I'm shipping 5 skeins to Bangalore" is proposed, the receiving
  member accepts before it lands on their own on-hand row. Chosen over a direct-set model
  specifically for the audit trail.
- **mb-23**: When linking a new applet group to an existing business, default to inviting
  every business member into the new group (setup wizard: invite all outright). For a *later*
  link of an already-populated group, run a delta check both ways — any business member not
  yet in the applet group, and any applet-group member not yet in the business, get suggested
  as invites — and let the person doing the linking cross names off the suggested list before
  anything actually sends.

**Design/UI, needs no further discussion**
- **mb-2**: Notifications from different applets can look identical when the underlying
  groups share a name (e.g. a Backlog Tracker group and a business named the same thing).
  Add a small applet icon/badge per notification so the source is unambiguous.
- **mb-4**: Assembly & Packaging is currently one free-text notes field. Confirmed scope:
  offer selectable preloaded templates (like Packaging's existing preset list) so a rough
  cost/time estimate gets pulled in automatically, on top of the free-text notes — not a
  replacement for the notes field.
- **mb-8**: Orders page visual pass — better sectioning/readability. No functional change,
  pure layout/visual work; do last, after the functional bugs/features above.

**Answered, no backlog item needed**
- **mb-13**: `finalCost`/`finalRevenue` explained in chat — `finalCost` is the order's real
  locked-in cost, `finalRevenue` is what the customer actually paid; `finalProfit = revenue −
  cost` feeds profit distribution. No code change was being asked for.
