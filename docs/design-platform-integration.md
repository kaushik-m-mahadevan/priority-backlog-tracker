# Platform Integration — Resolved Decisions

Source documents (delivered 2026-09-13, not committed to this repo — kept
externally): `CROCHET_ORDER_TRACKER_SPEC.md`, `PLATFORM_INTEGRATION_CONTEXT.md`,
`order_mockup.html`. This doc records every open question those two documents
raised (plus a few more found while cross-checking them against the actual
code) and the decision made on each, so later phases don't re-litigate them.

**Non-negotiable constraint, unchanged from the handoff doc:** the Backlog
Tracker must keep working exactly as it does today, throughout every phase.

---

## 1. Resolved decisions

### Architecture

- **A group = a separate business, not a workspace inside one business.**
  `BusinessConfig`, `LocationCode`, `PresetOption`, `ShippingLanePreset`, and
  the order-number sequence are all **per-group** documents, not global
  singletons. A user's Order-Tracker-specific profile fields (`creatorCode`,
  `locationCode`, `hoursAvailablePerDay`) are **per-(user, group)**, not
  per-user — the same person can belong to two different Order Tracker
  "businesses" with different values in each.
- **`maxGroupsPerUser` is capped per-applet, not globally.** A user can be in
  up to N Backlog Tracker groups AND up to N Order Tracker groups
  independently.
- **Settings is one shared hub**, not per-applet screens. Account-level
  settings (profile, theme, timezone, password) plus a section per applet
  (Backlog Tracker's ranking config, later Order Tracker's `BusinessConfig`)
  all live under the one unified Settings box from the landing page.
- **Render `autoDeploy` is confirmed off** in the live dashboard (the
  committed `render.yaml` is stale and says `true` — update it to match at
  some point, not urgent, cosmetic drift only, doesn't affect behavior).

### Order Tracker spec clarifications

- **Order number format: 14 digits**, per the spec —
  `[Location:3][Creator:3][OrderType:2][Sequence:6]`, e.g. `76951743000012`.
  The mockup's 12-digit examples are stale (predate the spec's sequence bump
  from 4 to 6 digits) and are not authoritative.
- **Stage-completion weighting is derived automatically from each stage's own
  estimated hours, not a separately configured weight.** E.g. if Crocheting is
  estimated at 4 hours and Packaging at 3 hours (7 hours total), Crocheting is
  4/7 of the overall completion weight and Packaging is 3/7. No `weight` field
  needed on `BusinessConfig.workStages` — the weighting falls naturally out of
  each order's own time estimate per stage, so it varies order-to-order rather
  than being one fixed global number. Overall completion % = Σ over stages of
  (stage's own completion fraction × that stage's share of total estimated
  hours for this order).
- **Individual-order `computedDueDate`: one primary creator's capacity governs
  the whole order**, not a max-across-every-assigned-creator calculation (that
  max-of-offsets logic stays specific to bulk orders per §9 of the spec,
  unchanged). Which creator counts as "primary" for an individual order needs
  a concrete rule when this is built — default assumption:
  `createdByCreatorId` unless told otherwise, confirm at build time if it
  matters which stage's assignee it should be instead.
- **Refund-to-zero gets its own status**, distinguishing "never paid" from
  "paid in full then fully refunded" — both currently read as `UNPAID` under
  the spec's literal formula (`netPaid ≤ 0`). Needs a concrete resolution at
  build time: either a `FULLY_REFUNDED` status value inserted into the
  derived-status logic, or a boolean flag alongside `paymentStatus`. Pick
  whichever reads more naturally against the rest of the ledger-derivation
  logic in §6 once that code is actually being written.
- **Packaging time follows the same itemized-vs-preset rule as packaging
  cost**: itemized sum if `Packaging.itemizedList` is non-empty, else the
  preset's `estimatedTimeHours`.
- **Order status stays a free-form enum, no guarded state machine.** Any
  status can be set to any other status — matches the existing `ItemStatus`
  precedent in Backlog Tracker (no transition rules there either).

### Encryption

- **Dev/CI gets a fixed fallback encryption key**, mirroring how `JWT_SECRET`
  already has a local-bootstrap default (`test123`-style) — nobody has to
  configure anything to run tests or dev locally. Render generates a real
  secret for prod, same pattern as `JWT_SECRET` in `render.yaml` today.
- Mechanism (wrapper type + one Spring Data converter pair vs. another
  approach) is an implementation detail to settle at build time, not a product
  decision — Spring Data converters register per-type, so the likely shape is
  a small `EncryptedString` wrapper type that only the fields in §4.5 of the
  spec use.

### Process

- **Testing: build a thin Playwright smoke suite** (login → pending → admin
  approve → create group → invite → accept → create item → see it ranked),
  not a manual checklist. This is the safety net for every phase's UI haul.
- **Execution style: run all six phases without stopping for approval between
  them.** Still run the full test suite + Playwright haul at each phase
  boundary internally and treat any regression as a hard blocker (§5.5 of the
  handoff doc — fix it as its own atomic commit before continuing), but don't
  pause for a go-ahead after each phase. The user will interject with a
  literal **"test"** comment if they want a progress check-in.

### Found during review, not originally flagged by either document

- **A third coupling point beyond the two the handoff doc named:**
  `User.animationsEnabled` (the Grove opt-in toggle) sits directly on the
  shared `User` document but is entirely Backlog-Tracker-specific. Needs the
  same treatment as `Group.categories` and `AppConfig.maxGroupsPerUser` —
  extracted into a Backlog-Tracker-owned per-user settings doc before `User`
  moves into `commons` in Phase 2.

---

## 2. Phase plan (unchanged from the handoff doc, decisions above now folded in)

1. **Testing** — Playwright smoke suite (frontend gap is real; backend's 24
   test files are a legitimate existing safety net, fill gaps found, don't
   assume zero coverage).
2. **Refactor: `commons` vs `backlogtracker`** — move `security`, `user`,
   `group`, `notification`, `counter`, `common/web` into `commons`. Extract
   `Group.categories` into a backlog-owned settings doc keyed by `groupId`.
   Extract `AppConfig.maxGroupsPerUser` into a new shared, per-applet-capped
   platform config. Extract `User.animationsEnabled` into a backlog-owned
   per-user settings doc. Pure structure move, no behavior change — verify
   `@Document` collection names and `@Indexed` field names are unchanged
   pre/post-move.
3. **New login flow → applet landing screen** — the three-box launcher.
4. **Migrate Settings** into the shared-hub shape (§1 above) under the new
   structure.
5. **Full functional parity is every phase's own exit criterion**, not a
   separate step (per the handoff doc's own reasoning — no accepted broken
   window between phases on a live app).
6. **Build Order Tracker** per `CROCHET_ORDER_TRACKER_SPEC.md`, against
   `commons`, with every group a separate business (§1 above), encryption
   layer built fresh (no existing precedent in this codebase).

---

## 3. Engineering discipline (unchanged from the handoff doc)

One atomic commit per feature. Full test suite green after every commit,
never carry a known-red test forward. Full Playwright haul after every phase.
A haul-found regression blocks moving to the next phase — fixed as its own
atomic commit, suite and haul re-run, not patched silently inside later work.
