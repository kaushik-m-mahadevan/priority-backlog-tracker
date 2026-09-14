# Production risk audit — legacy-data compatibility + generic bug sweep (2026-09-14)

Triggered by the third production outage this session (`OrderStatus.RECEIVED`), all three
sharing one root cause: a `@Document`-persisted entity's shape changed after real documents
existed with the old shape, and nothing caught the mismatch until a user hit it live. This
audit's job was to find every remaining instance of that pattern before it causes a fourth,
then do a broader generic pass. Nothing here is implemented yet — findings only, for triage.

Method: three independent sweeps (enum-rename history across every enum reachable from a
persisted document; other document-shape incompatibilities from the Order Tracker rebuild;
a generic correctness/easy-fix pass over recently-added code), every finding re-verified
against the real current code and real `git log`/`git show` output before being recorded here.

---

## A. Enum-rename deserialization risk — the exact bug class that caused all 3 outages

**Clean.** Every enum reachable from a persisted document was checked against its full git
history. Beyond the two already fixed this session (`OrderStatus`, `PaymentStatus`, both in
`LegacyDataMigration.java`), no other enum has ever had a constant renamed or removed —
every other enum's history is creation-only or addition-only (new constants, never changed
existing ones). Checked: `NotificationType`, `CostConfigChangeStatus`, `ArchiveRequest.Status`,
`PasswordRequest.Type`/`Status`, `TerminalStatus`, `ItemStatus`, `EffortUnit`, `AccountStatus`,
`AcquisitionChannel`, `OrderType`, `PaymentType`, `Order.PatternType`/`ResearchItemType`/
`ShipmentStopType`, `Role`.

**Two minor polish items, not bugs** (both already degrade to a 400 via
`GlobalExceptionHandler`'s generic `IllegalArgumentException` handler, so neither is a crash
— just a worse error message than the two call sites that already do this correctly):
- `OrderService.java:160` — `OrderType.valueOf(request.orderType())` has no try/catch or
  friendly message, unlike `ItemController.java:105`/`ArchiveController.java:43` which wrap
  the same pattern.
- `ItemController.java:61` — same, on the item-search/list endpoint's status filter.

---

## B. Other legacy-data shape incompatibilities — found during the Order Tracker rebuild (commit `54c7b29`)

The commit message for that rebuild says old data was "dropped rather than migrated
(throwaway data, explicitly agreed)". **That assumption is now known to be false** — the
`OrderStatus.RECEIVED` outage proves at least one pre-rebuild order document survived in
production. That means the three findings below are live risks, not historical footnotes,
until proven otherwise (i.e., until someone confirms the production `orders` collection has
zero documents older than `54c7b29`).

### B1. HIGH — `orderTrackerBulkOrders` collection is fully orphaned (possible silent data loss)
Commit `2397f30` added bulk orders as their own `@Document("orderTrackerBulkOrders")`
collection with a full repository/service/controller. `54c7b29` deleted all of it
(`BulkOrder.java`, `BulkVariant.java`, `CreatorSplit.java`, the repository/service/controller)
and folded bulk-order support into `Order.bulkDetails` inside the unified `orders` collection
instead. Verified: `grep -rn "orderTrackerBulkOrders" src/main/java` → zero matches anywhere
in current code. If any real bulk order was ever created before the rebuild, its document
still physically exists in MongoDB but is now permanently unreachable through the
application — no error, no crash, just silently gone from every view. This is the most
severe finding in this report because it's the one class of bug that doesn't announce
itself with a stack trace.

### B2. MEDIUM — Payment date silently lost on legacy orders (`paidAt` → `date` bare rename)
The old `Payment` class (deleted in `54c7b29`) had `private Instant paidAt;`. Its replacement,
`Order.PaymentEntry` (`Order.java` ~line 265), has `private Instant date;` instead — a bare
field rename with no `@Field("paidAt")` alias and no migration step. Both are `Instant`, so a
legacy order with payment history doesn't crash on read (the enum-record failure modes don't
apply to a simple type-compatible rename) — but `date` silently deserializes to `null` for
every payment on a pre-rebuild order. That null flows straight through
`OrderView.PaymentView.of()` to the frontend with no null-check, so a legacy order's payment
list would show a missing/garbled date and could sort wrong wherever payments are ordered by
date.

### B3. MEDIUM — Per-order change-log history orphaned, not migrated
The old `Order.changeLog` was an embedded `List<ChangeLogEntry>` living directly inside each
order document (added in `82f432b`). `54c7b29` deleted `ChangeLogEntry.java` and removed the
field from `Order` entirely, replacing it with a new top-level `@Document("orderChangeLogs")`
collection queried by `orderId`. Nothing migrates the old embedded arrays into the new
collection. A pre-rebuild order's `changeLog` array still physically exists as an orphaned
field in its Mongo document (harmless — nothing reads it, nothing crashes), but that order's
edit history is now invisible in the UI with zero indication anything is missing.

**Resolved 2026-09-14, confirmed by the user:** no real orders have ever been entered in
production — the only real data is user accounts and group configuration. B1–B3 are
therefore moot; no migration or recovery work needed for any of them. (The one order document
that triggered the `RECEIVED` outage was leftover demo/test data, not a real order — its
existence is still exactly why `LegacyDataMigration`'s status-rename fix stays in place, it
just isn't evidence of broader real-order data at risk.)

---

## C. Generic bug / easy-fix sweep (new code this session, not covered by the prior panel review)

### C1. MEDIUM — `CostConfigChangeService.approve()` can double-apply a cost-config change on a race
`resolveIfUnanimous()` calls `businessConfigService.applyCostConfig(...)` *before*
`repository.save(request)` is attempted (`CostConfigChangeService.java:88-97`). The retry
loop that exists specifically to handle `OptimisticLockingFailureException` re-reads and
re-resolves on every retry — so a genuine concurrent-approval race can call
`applyCostConfig()` more than once for one logical approval. Harmless today because
`applyCostConfig` is an idempotent absolute-value overwrite, but it's an unguarded
read-then-write against `BusinessConfig` on every retry, and it's a trap for whoever adds a
notification or audit-log call to `resolveIfUnanimous` next (it would silently double-fire).
Fix direction: only apply the side effect after `repository.save()` actually succeeds, or
only on the loop's final/winning iteration.

### C2. LOW — `LegacyDataMigration` pulls every user's `_id` into memory just to check "is the DB empty"
`LegacyDataMigration.java:108` — `mongo.findDistinct(new Query(), "_id", "users", Object.class)`
fetches the full user-id list on every single startup, only to call `.size()` and `.isEmpty()`
on it. `mongo.count(new Query(), "users")` gives the same answer without the unbounded read.
Cheap now, needless cost that scales with user count.

### Checked and confirmed clean (worth recording so this doesn't get re-litigated)
- No empty/swallowed `catch` blocks anywhere in `src/main/java`.
- No `TODO`/`FIXME`/`XXX` comments left anywhere in the Java source.
- `OrderBusinessRules.validateVariant` is genuinely wired into both `create()` and
  `updateBulkDetails()` (not just one, which would've been an easy regression).
- `CostConfigChangeService`'s group-scoping is consistent — every method requires
  membership, and `pendingRequest()` re-verifies the fetched request's `groupId` matches the
  path's `groupId` (no ID-substitution gap).
- The `GroupCategoryController` membership-check fix from the prior review round is real and
  still in place.
- No other `@Indexed(unique = true)` field was found missing `sparse`/`partialFilter`
  protection beyond the two already fixed (`User.handle`, `Item.linkedOrderId`) — checked
  every `@Indexed`/`@CompoundIndex` declaration in the codebase by hand.

---

## Summary for triage

| # | Finding | Severity | Status |
|---|---|---|---|
| B1 | Orphaned `orderTrackerBulkOrders` collection | HIGH | **Moot** — confirmed no real orders exist in prod |
| B2 | Payment `date` silently null on legacy orders | MEDIUM | **Moot** — same |
| B3 | Order change-log history orphaned | MEDIUM | **Moot** — same |
| C1 | Cost-config double-apply on retry race | MEDIUM | Real, but currently harmless (idempotent write) |
| C2 | Full user-id list pulled on every boot | LOW | Real, cheap now |
| A  | `.valueOf` without friendly error (×2) | LOW | Real, but already degrades to 400 not 500 |

C1, C2, and A are the remaining actionable items — all safe, low-risk fixes.
