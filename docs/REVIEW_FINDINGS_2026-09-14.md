# Priority Backlog Tracker / Order Tracker — critical sweep, round 1 (2026-09-14)

Full-project panel review: a security lens, a money/data-correctness lens, a staff-engineer
maintainability lens, a QA/test-coverage lens, and a UX pass (screen-reader/low-vision,
motor/RSI, mobile/responsive, first-time-user) — each run as an independent pass, findings
verified against the current code before being recorded here.

This is round 1's findings-only format, with one exception: three items below were **live
production outages** (users hitting 400/500s right now), not just findings, so they were fixed
immediately rather than left for triage. Everything else is unfixed, for you to prioritize.

**Fixed during this round** (see each item for detail): `isTool` null crash (already shipped as
commit `5685910`, just before this round started), `splitTracked` null crash (`1eee384`), and a
pre-emptive fix for the identical bug pattern in `User.handle`'s index (`fc184fe`), found by this
review before it could cause a fourth outage.

---

## 1. Security

**BLOCKER — the prod encryption key has no startup guard, unlike the JWT secret**

`ProdSanityCheck.java` exists specifically to refuse to start the `prod` profile with an unsafe
dev default left in place — but it only checks `app.jwt.secret`:

```java
if ("test123".equals(jwt.secret())) {
    throw new IllegalStateException(
            "JWT_SECRET is still the dev default in the prod profile — set a real secret");
}
```

`application.yml` (loaded in every profile) sets:

```yaml
app:
  encryption:
    key: ${ENCRYPTION_KEY:dev-only-not-a-real-key}
```

If `ENCRYPTION_KEY` is ever left unset in prod (a typo'd env var name, a new hosting environment
stood up without it, a redeploy that drops env config), the app starts up *successfully* using the
literal string `"dev-only-not-a-real-key"` — committed in plaintext in this repo — as the seed for
both `AesGcmCipher`'s AES-256 key and `BlindIndexService`'s HMAC key. Anyone who can read the
source (or a DB dump, or the DB itself via any future bug) can derive the exact key and decrypt
every encrypted field in the database — customer names, emails, phone numbers, shipping
addresses, order notes, payment amounts/mode/notes, shipment tracking numbers — with zero
brute-forcing. No error, no log line, nothing at deploy time to catch it.

Fix direction: extend `ProdSanityCheck` with the same check for `app.encryption.key` against
`"dev-only-not-a-real-key"`.

**MEDIUM — `GET /api/groups/{id}/categories` has no group-membership check (IDOR)**

`GroupCategoryController.get()`:

```java
@GetMapping
public CategoriesView get(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
    return new CategoriesView(categoryService.categoriesFor(id));
}
```

`categoriesFor()` just does `repository.findById(groupId)...` with no membership check — and
unlike this, the `add`/`remove` endpoints on the very same controller correctly call
`groupService.requireMember(groupId, userId)` first. `actor` is accepted as a parameter here and
never used. Any authenticated user can read any other group's category list by guessing/enumerating
`groupId`, regardless of membership. Read-only and not highly sensitive data, but a genuine gap in
an otherwise consistently-guarded controller.

**Verified clean, not reported as findings:** every `OrderController`/`CustomerController`
endpoint correctly scopes by group membership and re-verifies the fetched entity's `groupId`; JWT
handling reloads the user/role fresh from the DB on every request (never trusts token claims) and
uses proper signature verification; `BlindIndexService` has correct domain separation between its
HMAC key and the encryption key; no raw string interpolation into Mongo queries; no secrets or PII
in logs; CORS has no registered `CorsConfigurationSource`, so it's same-origin-only, not
permissive; CSRF is correctly disabled for a stateless bearer-token API.

---

## 2. Money / data correctness

**BLOCKER (fixed, `1eee384`) — `WorkStageType.splitTracked` crashed every orders-page load**

Same root cause as the `isTool` fix, in the same file: `splitTracked` was added as a primitive
`boolean` record component after `BusinessConfig` documents already existed without it. Any read
of a pre-existing config (which is most of them) threw `Parameter splitTracked must not be null`
— this is the exact bug you reported live mid-review. Fixed by boxing to `Boolean` with a compact
constructor that restores the *correct* legacy value by `stageKey` (crocheting/assembly → `true`,
packaging/shipment → `false`) rather than a blanket `false`, which would have silently corrupted
every existing bulk order's completion percentage. Regression test added
(`BusinessConfigLegacyDataTest`) reproducing the exact legacy document shape.

**HIGH (fixed pre-emptively, `fc184fe`) — `User.handle`'s sparse unique index had the identical
latent bug as the `linkedOrderId` outage from earlier this session**

```java
@Indexed(unique = true, sparse = true)
private String handle;
```

`sparse` only excludes a genuinely-*absent* key, not an explicit BSON `null` — and
`UserService.approve()`/`updateProfile()` both do a full-document `repository.save(u)` on a user
whose in-memory `handle` may be Java `null` (any account predating the handle field). The first
such resave succeeds; a **second** legacy user hitting either of those methods would 11000 on
`{handle: null}`. This hadn't fired yet, but it's the same mechanism as an outage that already
happened twice this session. Fixed with a `partialFilter` index (same pattern as
`Item.linkedOrderId`), given a different name so it can coexist with the still-live old index at
startup, with `LegacyDataMigration` dropping the superseded one as cleanup. Regression test added.

**A definitive sweep for other instances of this bug class was done**: every `public record` in
the codebase with a primitive component was enumerated. The only ones embedded inside an
`@Document`-persisted entity (as opposed to pure Jackson-bound request/response DTOs, which fail
differently and aren't at risk of this specific crash) were `BusinessConfig`'s two nested records
— both now fixed. Every other domain type's nested value type is a plain Lombok class, not a
record, which doesn't have this failure mode at all (a missing field just leaves the Java default
rather than requiring a non-null constructor argument). No further instances exist today, but the
underlying trap — *adding a primitive field to a record embedded in an `@Document` type that
already has data* — remains real for any future field addition in this shape.

**HIGH — the unanimous cost-config approval has a classic read-modify-write race**

`CostConfigChangeRequest` has no `@Version` field, and `CostConfigChangeService.approve()` does a
plain read → mutate `approvedByUserIds` → `save()`. Two members approving concurrently can lose
one approval entirely (last write wins — the request silently sits one approval short forever,
with no indication anything went wrong), or, if the same member double-submits as the last
approver, both requests can independently see unanimity and both call
`applyCostConfig()` — itself also a non-atomic read-then-write on the same `BusinessConfig`
document, racing against any other concurrent config edit.

Fix direction: add `@Version` to `CostConfigChangeRequest` and handle
`OptimisticLockingFailureException` with a retry, or switch to an atomic `findAndModify` with
`$addToSet` on `approvedByUserIds`.

**HIGH — a cost-config proposal can get permanently stuck if the last outstanding approver leaves
the group**

`resolveIfUnanimous()` only runs from `propose()`/`approve()`. `GroupService.leave()` removes the
member from `Group.memberIds` but never re-evaluates any pending `CostConfigChangeRequest`.
Concrete case: members A/B/C, A proposes (auto-approved), B approves, C leaves without
approving/rejecting. The request is *already* unanimous among the remaining members (A, B) but
nothing notices — and since `propose()` refuses a new proposal while one is `PENDING`, the
business is now permanently unable to change its overhead/margin until someone finds and manually
rejects the orphaned request (with no UI affordance clearly aimed at "this is stuck, not just
pending"). No test exists for `CostConfigChangeService` at all — this whole workflow, including
this edge case, is untested.

**MEDIUM — resizing a bulk order's stage progress never clamps `unitsCompleted` down with it**

```java
int totalQty = newVariants.stream().mapToInt(Variant::getQuantity).sum();
details.getStageProgress().forEach(sp -> sp.setTotalUnits(totalQty));
```

If a variant's quantity is reduced after some batch-tracked-stage progress was already recorded
(e.g. Packaging at 80/100 units), `totalUnits` shrinks to the new total (say 10) but
`unitsCompleted` stays at 80 — `bulkCompletionFraction` then computes `80/10`, and the
order's reported completion can exceed 100% with nothing downstream clamping it back into
`[0,100]`. The same "numerator not reclamped to a shrunk denominator" issue exists in
`mergeProgress` for split-tracked (per-creator) progress when a creator's `quantityAssigned`
shrinks on an edit.

**LOW — money math is unscaled `double` with an exact-equality boundary for "paid in full"**

Verified the four specific scenarios you'd worry about (zero-cost order + any payment,
a lone refund, overpayment, payments summing exactly to the price) all resolve correctly in
`derivePaymentStatus`'s branch logic itself. The risk is underneath that: `finalCost` and `netPaid`
are both chains of `double` arithmetic with no rounding, and `PAID_IN_FULL` gates on
`net >= finalCost` with no epsilon — a classic `0.1 + 0.2 != 0.3` floating-point drift could in
principle leave an order that's genuinely been paid in full stuck at `PARTIALLY_PAID` by a
fraction of a cent. Design smell more than a reproducible bug today; worth a rounding pass
(`Math.round(x * 100) / 100.0`, or a move to `BigDecimal`) if money precision ever becomes a real
complaint.

**Verified clean, not reported as findings:** `CounterService.next()` genuinely uses an atomic
`findAndModify`/`$inc`/`upsert` (not read-then-write); overhead-then-margin application order is
applied consistently everywhere it's computed or displayed; every other unique index either
already uses a partial filter correctly or covers a field that's always set on every write path.

---

## 3. Maintainability

**HIGH — split-allocation-sum validation exists only on the frontend**

`validateSplits` in `OrderFormFields.tsx` is the *only* place enforcing "a variant's creator-split
quantities must sum to the variant's quantity." `OrderService.buildVariant()` on the backend
accepts whatever `splitAllocation` the request contains with no equivalent check. Any direct API
call — a future mobile client, a bug in a later frontend refactor, a manual request — can create a
variant where the sums don't match, silently corrupting `bulkCompletionFraction()` (denominator is
`totalQuantity`, unrelated to the sum of assigned splits) and the computed due date, with no error
anywhere. This is exactly the kind of "fix drifts because it's not the place guaranteeing
integrity" risk worth closing by moving the check server-side (frontend can keep its copy for
instant feedback, but the backend should be the actual guard).

**MEDIUM — `AddToGroupModal` treats "couldn't check for an existing link" the same as "confirmed:
no link exists"**

```ts
backlogLinkApi.findLinkedItem(groupId, order.id).catch(() => null),
```

A network error, a 500, or a stale cross-applet session on the *Backlog Tracker* side all collapse
to `null` here — indistinguishable from "genuinely not linked yet." If Backlog Tracker's API is
briefly unavailable when a user opens this modal, they'll be shown the "create new item" form as
if nothing existed yet, and can create a duplicate backlog item for an order that (unbeknownst to
them) may already have one. Worth distinguishing "confirmed unlinked" from "couldn't check" in the
UI (e.g. don't offer creation until the check has actually succeeded).

**LOW — `Packaging.cost()`/`timeHours()` duplicate `OrderCalculator`'s line-item summation**

Both independently compute `unitCost * quantity` summed over line items. Low current risk since
both match today, but it's an unforced duplication — `OrderCalculator` already computes packaging
time via `packaging.timeHours()`, so `Packaging.cost()` could just delegate to
`OrderCalculator.lineItemsCost()` instead of reimplementing it.

**Verified clean, not reported as findings:** stage-key strings (`"crocheting"`, `"assembly"`,
etc.) are not hardcoded anywhere outside test fixtures — they flow end-to-end from
`BusinessConfig.workStages`, so there's no drift risk there; no dead/unused exports or TODOs found
anywhere in the Order Tracker Java or TSX sources — a genuinely clean build on that specific axis.

---

## 4. QA / test coverage

**HIGH — `OrderService.myWork()` (the "My Work" view) has zero test coverage**

No test exercises `/orders/my-work` at all. Two specific edge cases that are easy to break silently
in a future refactor: a creator with no assignments at all (should correctly return empty — never
asserted), and a creator whose assignments are *all* complete (should drop out of the default
"pending" filter but still show under "all"/"done" — the bulk-order aggregation here has a
nested-loop early-return that's easy to break and has nothing catching a regression).

**MEDIUM — blind-index customer search normalization is correct today but entirely unverified**

`applyHashes`/`search` correctly `.trim().toLowerCase()` before hashing, so
`"Priya@Example.com"` does find a customer saved as `"priya@example.com"` — but there's no test
for this anywhere (no `CustomerApiTest` case, no dedicated `BlindIndexServiceTest`). A future
"fix" to this normalization (plausible — someone might "simplify" it to hash raw input) would
silently break real-world customer lookups with nothing to catch it.

**MEDIUM — frontend has no component tests at all for the newest, most complex UI**

The only frontend tests in the whole repo are two unrelated utility-function tests
(`format.test.ts`, `tz.test.ts`). `OrderFormFields.tsx`'s multi-entry mandatory-item grouping,
`isTool`-based field-hiding, and `validateSplits` — plus all of `AddToGroupModal.tsx` — are
verified only by manual browser testing today.

*(This overlaps with the "cost-config workflow entirely untested" note under Money/correctness
above — noted once there, not repeated here.)*

---

## 5. Accessibility — screen-reader / low-vision

**BLOCKER — no `<label>` is programmatically associated with its control, anywhere in Order
Tracker**

Every form field across every page (`OrderFormFields.tsx`, `NewOrderPage.tsx`,
`OrderDetailPage.tsx`, `BusinessSettingsPage.tsx`, `AddToGroupModal.tsx`, `CustomersPage.tsx`) uses
`<div className="form-row"><label>Text</label><input .../></div>` — sibling elements, no
`htmlFor`/`id` pairing, no wrapping. A screen-reader user tabbing into any field hears only "edit
text" with no name; clicking the visible label text does nothing. This is the single largest
accessibility gap found and affects effectively every input in the applet.

**HIGH — repeated "Remove"/"+ Add another" buttons carry no distinguishing text**

On multi-entry mandatory items (`OrderFormFields.tsx`), every extra entry's Remove button just
says "Remove" with no item type, entry number, or value in the text or an `aria-label` — same
issue on add-on rows and creator-split rows. With, say, 5 item types × 3 entries, a screen-reader
user browsing by "buttons" hears "Remove, Remove, Remove, Remove, Remove…" ten times over with no
way to tell which entry each one deletes.

**MEDIUM — split-mismatch indicator is color-only where styled, and has *no visual effect at all*
where it's actually used**

`.form-row .hint.bad` is the only CSS rule defined for the "bad" state (confirmed: no bare `.hint`
rule exists) — but the variant split-mismatch `<p className={mismatch ? "hint bad" : "hint"}>` in
both `NewOrderPage.tsx` and `OrderDetailPage.tsx` is **not** inside a `.form-row`, so the `bad`
class currently does nothing: a mismatched split renders visually identical to a matched one for
every user, sighted or not. The only way to notice today is manually comparing two numbers in the
text. (Separately, `AddToGroupModal`'s effort-validity hint *is* correctly scoped, but is
color-only — the hint text itself never changes to say what's wrong.)

**MEDIUM — `AddToGroupModal` has no focus management and no Escape-to-close**

No focus moves into the modal on open, no `role="dialog"`/`aria-modal`, no `onKeyDown` handler at
all (Escape does nothing), and focus never returns to the trigger button on close. The codebase
already has this pattern correctly implemented elsewhere (`ManageBusinessPage.tsx`'s inline rename
form wires Enter/Escape) — it just wasn't applied here.

---

## 6. Accessibility — motor / RSI

**HIGH — building out multi-entry mandatory items is fully manual, one field at a time**

For a bulk order with, say, 5 mandatory item types needing 3 entries each per variant: ~10 clicks
just to create the entry cards, then up to 4 fields each — on the order of 60+ individual field
interactions for one variant alone, no bulk/duplicate-row/paste-multiple shortcut anywhere in
`MandatoryItemsFields`. Partial mitigation already exists: "+ Add variant" does correctly clone the
*entire* mandatory-items array (not just the first entry per type, which was worth double-checking
and turned out fine) — so a second variant starts pre-filled and you only edit what differs. But
building the *first* variant, or a business's very first order of any kind, has no shortcut at all.

**LOW — no keyboard-only trap found.** Every actionable control reviewed (remove/add buttons,
selects, shipment status buttons) is a real, keyboard-operable element; no hover-only or
drag-only interaction exists in the applet.

---

## 7. Mobile / responsive

**HIGH — `.form-grid` never collapses to one column at any width**

`.form-grid { grid-template-columns: 1fr 1fr; }` has no `@media` override anywhere, unlike
`.grid.cols-3` (which correctly degrades 3→2→1 columns by width). `.form-grid` is used pervasively
— customer/creator fields, dates, email/Instagram, mandatory-item quantity/cost pairs, add-on rows,
`AddToGroupModal`'s category/priority and due-date/assignee rows. On a ~375px phone, every one of
these stays forced into two ~150px columns instead of stacking.

**MEDIUM — plain `<table>` lists (My Work, Customers) only scroll horizontally on mobile, unlike
the rest of the app**

`.table-wrap { overflow-x: auto; }` is the only mobile handling for Order Tracker's tables — no
collapse-to-card treatment, unlike `.data-table` elsewhere in the app, which has a full
`@media (max-width: 620px)` block turning rows into readable stacked cards. A visible
inconsistency with an established pattern the codebase already has.

**LOW, verified fine — `AddToGroupModal`'s own sizing.** It correctly shrinks to the viewport and
scrolls vertically on a small screen; the only mobile pain inside it is the `.form-grid` issue
above.

---

## 8. First-time-user / onboarding

**HIGH — zero mandatory-item-types configured renders a silently blank, unexplained section**

If a brand-new business has no mandatory item types configured yet (its actual default state),
`MandatoryItemsFields` renders the "Mandatory items" heading followed by nothing — no hint, no
link to Business Settings, no indication whether the page is broken or intentionally empty.
Functionally harmless (an order with zero mandatory items still submits fine), but looks broken to
a brand-new user rather than guiding them to configure item types first.

**MEDIUM — "Business" and "Group" are the same entity but never reconciled in the UI, and the
terminology clashes within Order Tracker itself**

Every Order Tracker screen says "business" throughout. `AddToGroupModal` — launched directly from
inside Order Tracker's own Order Detail page — switches to "Group" mid-flow ("Select a group…",
links to "Go to Priority Backlog Tracker"). A user creating a "business" and later seeing it appear
as a selectable "group," or an Order Tracker team invite silently also granting Backlog Tracker
access, has no in-product explanation anywhere for why.

**Verified fine, no finding:** zero-creators and zero-customers states are both already handled
correctly with explicit, actionable empty states (only the mandatory-item-types case, above, is
missing this treatment).

---

## What I did not get to in this round

- The older **Backlog Tracker** applet's own code got only incidental coverage (wherever Order
  Tracker's review touched shared `commons` code) — it wasn't reviewed with the same depth as
  Order Tracker, which is the newer and least-scrutinized part of the app.
- **Performance/scale** wasn't reviewed at all (query patterns under real data volume, N+1-shaped
  service calls, pagination).
- **i18n/l10n** wasn't reviewed (currency formatting, date formatting across locales).
- Accessibility/mobile/onboarding passes were scoped to Order Tracker's frontend specifically, not
  Backlog Tracker's or the shared auth/settings screens.
