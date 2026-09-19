# Backlog — pending product decisions and design work

Living document. Tracks everything decided-but-not-built, proposed-but-not-decided, and
explicitly-deferred, so none of it gets lost between sessions. Update this file whenever a
new item is decided, started, or finished — don't let decisions live only in chat history.

Source context: `docs/REVIEW_FINDINGS_ROUND4_2026-09-18.md` (technical panel review), the
"Everything But The Hook" automation audit (five-round, multi-persona UX/automation review,
2026-09-18 — published as a Claude artifact, not checked into the repo; ask the project owner
for the link if it's needed again), and a 2026-09-19 seven-agent code-quality/SOLID audit
covering every backend package and every frontend area (findings folded into the
"Code quality" section below).

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
- **Accessibility inconsistency**: `NavMenu`, `Connections`, and `GroupSwitcher` all close on
  Escape; `Bell` (the notifications popover) is the one dropdown that doesn't.
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
  `GroupSwitcher`, and `Bell` — the last one is also the a11y bug above) — one
  `useDismissableMenu()` hook fixes the bug and removes ~60 duplicated lines at once.
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
- **Basic mobile installability** (no PWA manifest exists today).
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
- Photo-attached receipts/payment screenshots on ledger entries.
- Approvals auto-resolving instantly for a solo business.
- Zero-quantity inventory rows disappearing automatically.
- The atomic Mongo `findAndModify` concurrency patterns (transfer fulfillment, inventory
  adjust) — do not simplify these away in the name of a new feature.
