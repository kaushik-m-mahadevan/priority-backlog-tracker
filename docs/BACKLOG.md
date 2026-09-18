# Backlog — pending product decisions and design work

Living document. Tracks everything decided-but-not-built, proposed-but-not-decided, and
explicitly-deferred, so none of it gets lost between sessions. Update this file whenever a
new item is decided, started, or finished — don't let decisions live only in chat history.

Source context: `docs/REVIEW_FINDINGS_ROUND4_2026-09-18.md` (technical panel review) and the
"Everything But The Hook" automation audit (five-round, multi-persona UX/automation review,
2026-09-18 — published as a Claude artifact, not checked into the repo; ask the project owner
for the link if it's needed again).

---

## In progress

### Estimate & delivery-date breakdown redesign
**Status:** design finalized 2026-09-18, implementation starting now.

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
Recording a `PaymentEntry` against an order should be able to auto-create (or auto-suggest,
confirm-button style) a Finance Tracker income entry, tagged with a reference back to the
order/payment so it can't double-fire. Cross-applet delivery via a generic domain event
(Order Tracker and Finance Tracker aren't allowed to import each other directly) — this would
be the first real consumer of `GroupLink`, which today stores a link but nothing reads it.

Follow-on: a profit-split proposal's "total profit" field should default to the sum of the
linked ledger entries for the order(s) it references, instead of a blank manually-typed
figure — still editable/overridable, since profit isn't always raw income.

### Order → Material Inventory decrement, with a safety net
- Add an "actual materials used" log per order line (separate from the existing
  planned/needed quantity), editable any time.
- Surface a confirm banner (on the order, mirrored in Material Inventory's "who has what"
  page) rather than silently auto-decrementing — e.g. on marking an order "Shipped":
  *"Order #… used 2 skeins of Blue Worsted — apply to inventory?"*
- Confirming reuses the existing atomic adjust-quantity path (same one the transfer-request
  feature already uses).
- The existing manual "set my quantity" absolute-override field stays exactly as-is as the
  correction path for an accidental/wrong decrement — no separate undo feature needed.
- Also requires a real `GroupLink` consumer (see above) to know which inventory group/user to
  touch.

### Order status workflow
Replace the current free-choice "any status → any status" dropdown with a small set of
contextual next-step buttons based on current status:
`Inquiry → Confirmed → In Progress → Ready to Ship → Shipped → Delivered`, `Cancelled`
reachable from any non-terminal state, one-step-back allowed for corrections, no arbitrary
jumps. Loosely tie "Ready to Ship" to stage-completion % with a warning (not a hard block) if
stages aren't actually finished. "Mark Shipped" is the natural trigger point for the
inventory-confirm banner above.

---

## Explicitly out of scope for now

- **Outside-the-app notifications** (email/SMS/push/any external channel). Declined
  2026-09-18 — app is still in dev stage, revisit once there are real users depending on it.
- **Invoice generation.** Deferred — separate feature, designed separately, after the
  quotable-estimate work above ships.
- **Delivery-buffer tiers as a real logistics/geo lookup** (e.g. actual courier zone data).
  Keep it a plain editable field for now; revisit once there's real shipping data to model
  against.

---

## Untriaged — surfaced by the "Everything But The Hook" review, not yet decided

Carried over verbatim from the review's master lists; none of these have an owner decision
yet. Re-triage before starting any of them.

**Bugs / inconsistencies**
- Leftover `@Version` field + stale comment on `TransferRequest` describing a locking
  mechanism that's no longer used (superseded by the atomic `findAndModify` rewrite).
- No confirmation dialog before deleting a logged time entry.
- `Customer.shippingAddress` is stored as a default an order "may override," but no order
  field actually exposes that override anywhere.
- `requestReset` silently no-ops on a duplicate password request; `requestChange` throws a
  409 for the same situation — inconsistent.
- Group-invite per-app membership cap is checked at invite time and again at accept time,
  with no re-notification if it becomes invalid to accept in between.
- Needle/hook inventory has no transfer-request equivalent to yarn's.

**Redundant / duplicated mechanisms**
- Three separately hand-written "everyone must approve" implementations (archive requests,
  cost-config changes, the shared `ApprovalService`) instead of one.
- Three near-duplicate order forms (new / edit / edit-bulk).
- New-customer fields declared once in the order-creation flow, again on the standalone
  Customers page.

**Confusing UI copy**
- The Connections (🔗) popover promises a linked app's data "will show up here too" — it
  doesn't, for any pair of apps, until the ledger/inventory work above ships.
- "Invite sent" toast shows regardless of whether the invitee has any way to find out (no
  notification pings them yet).
- Same concept labeled "group" in some places, "business" in Order Tracker copy.
- The 30-day "stale" badge on a yarn entry reads as a real freshness signal but is a guess
  unconnected to actual usage.

**Missing features, not yet scheduled**
- Any real Instagram/WhatsApp DM intake path.
- A start/stop timer for logging crafting time (vs. guessing hours after the fact).
- Recurring-expense templates in Finance Tracker.
- A real P&L / monthly summary view.
- Low-stock threshold + shopping list for yarn/needles.
- Notifications firing when a proposal (cost-config, finalization, profit split, password
  request, new signup) is *created*, not just when it's invalidated.
- Self-service path for the very first user on a fresh install to become an admin (currently
  a real onboarding blocker with no existing admin).
- Invite-a-stranger-by-email (today you can only invite someone already registered).
- A single cross-applet search.
- Basic mobile installability (no manifest/PWA setup exists today).

**Already well-designed — do not touch**
- Automatic order numbering, existing cost/due-date auto-recompute plumbing.
- Component templates as a reuse mechanic.
- Photo-attached receipts/payment screenshots on ledger entries.
- Approvals auto-resolving instantly for a solo business.
- Zero-quantity inventory rows disappearing automatically.
- The atomic Mongo `findAndModify` concurrency patterns (transfer fulfillment, inventory
  adjust) — do not simplify these away in the name of a new feature.
