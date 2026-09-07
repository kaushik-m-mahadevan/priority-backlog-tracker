# Priority Backlog Tracker — Final Design Document

## 1. Purpose

A shared web application for 3 founders (extensible to more) to log, rank, and act on a
prioritized backlog. The system automatically surfaces what to work on next, catches items
that are quietly stalling, and gives a lightweight view of who owns what.

---

## 2. Data Model (MongoDB Collections)

**`items`** (LIVE table — Backlog / In Progress only, see §24)

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId | |
| `itemId` | String | Generation scheme — see §20 |
| `title` | String | |
| `category` | String | Dynamic list, `config.categories` (§7) |
| `priority` | String | Dynamic list, `config.priorities` (§7) |
| `effortEstimate` | Embedded object | `{ value: Number, unit: "minutes"\|"hours"\|"days" }` — required, validated per §15 |
| `dueDate` | Date | Required at creation, defaults to `createdAt + config.defaultDueDateOffsetDays` |
| `status` | String | `Backlog` or `In Progress` only — moving to any terminal state relocates the document, see §24 |
| `createdBy` | ObjectId (ref `users`) | Audit-only, never gates edit rights |
| `lastUpdatedBy` | ObjectId (ref `users`) | Set on every edit |
| `ownerId` | ObjectId (ref `users`) | Assignee, shown in Owner Workload (§6) |
| `scope` | String | `shared` or `personal` — see §13 |
| `notes` | Embedded object | `{ content, format: "markdown", updatedAt }` — see §17 |
| `editLock` | Embedded object, nullable | `{ lockedBy, lockedAt }` — see §19 |
| `archivalRequests` | Array of objects | Non-destructive request history — see §18 |
| `createdAt`, `updatedAt` | Date | |

**`archivedItems`** (terminal table — see §24)

Same shape as `items`, plus:

| Field | Type | Notes |
|---|---|---|
| `terminalStatus` | String | `Resolved` / `Rejected` / `Archived` |
| `completionDate` | Date | Set at the moment of the terminal transition |
| `movedAt` | Date | When it left the live table |

**`users`**

| Field | Type | Notes |
|---|---|---|
| `name`, `email` | String | |
| `role` | String | Owner / Contributor / Viewer — see §8 |
| `userCode` | String | Used in personal item ID generation (§20) |

**`config`** (single document)

```json
{
  "priorityValues": { "Critical": 4, "High": 3, "Medium": 2, "Low": 1 },
  "priorities": ["Critical", "High", "Medium", "Low"],
  "priorityWeight": 0.333,
  "urgencyWeight": 0.333,
  "effortWeight": 0.334,
  "urgencyWindowDays": 14,
  "staleThresholdDays": 14,
  "buriedThresholdDays": 30,
  "buriedPriorityLevels": ["Low"],
  "categories": ["Research", "Skill-Building", "Project", "Technical Discussion", "Admin-Ops", "Other"],
  "defaultDueDateOffsetDays": 30,
  "effortCapDays": 30
}
```

**Default weights are an even three-way split** (as close to 1/3 each as floating point
allows). Any explicit override is only accepted if
`priorityWeight + urgencyWeight + effortWeight == 1` — the Settings screen validates this
before allowing save; a non-summing set of values is rejected outright, falling back to
the last valid saved configuration.

`priorities` and `categories` are dynamic, DB-backed lists. **Safety rule for removing a
value:**

- **More than one active item** uses it → blocked outright, with a warning listing
  affected items.
- **Exactly one active item** uses it → prompts a reassignment; submitting that
  reassignment is the only way the deletion happens (single combined action).
- **Zero active items** use it → deletes immediately.

**`counters`** — atomic per-scope sequence counters, see §20.

---

## 3. Priority & Ranking

```
priorityFactor = priorityValues[item.priority] / max(priorityValues)

daysRemaining  = dueDate - today
clampedDays    = max(daysRemaining, 0)
urgencyFactor  = 1 - min(clampedDays, urgencyWindowDays*3) / (urgencyWindowDays*3)
// Overdue items (daysRemaining < 0) correctly cap at urgencyFactor = 1, never above.

effortMinutes      = convert item.effortEstimate to minutes (§15)
effortCapMinutes   = config.effortCapDays * 24 * 60          // FIXED ceiling, not dynamic
effortFactor       = 1 - min(effortMinutes, effortCapMinutes) / effortCapMinutes
// Fixed cap replaces the earlier dynamic max-across-active-items approach — deliberately
// descoped, since dynamic recalculation caused unrelated items to silently reorder
// whenever a new large-effort item was added. A fixed 30-day ceiling is stable and
// predictable, and effort is validated (§15) so it can never exceed the cap anyway.

sortScore = (priorityWeight * priorityFactor)
          + (urgencyWeight  * urgencyFactor)
          + (effortWeight   * effortFactor)
```

**Tie-breaking (revised):** `dueDate` is dropped from tie-breaking since it's already
fully baked into `urgencyFactor` and rarely resolved anything on its own. On an exact
`sortScore` tie:

1. `effortMinutes` ascending — the quicker task wins the tie, consistent with the
   "quick win" philosophy elsewhere in this design
2. `createdAt` ascending — oldest item wins, as a final deterministic fallback

---

## 4. Status Workflow

Within the live table: `Backlog → In Progress` (reversible either direction — no terminal
meaning either way).

Moving to `Resolved`, `Rejected`, or approved `Archived` is **not a status flag change** —
it's a **physical, unidirectional move** out of `items` into `archivedItems` (§24). There
is currently no "undo" or reactivation path; this is intentional for now.

---

## 5. Aging & Neglect Detection ("Needs Attention" panel)

- **Stale & Overdue:** `today - dueDate > staleThresholdDays`, status Backlog/In Progress.
- **Buried Low-Priority:** `today - createdAt > buriedThresholdDays`, priority in
  `buriedPriorityLevels`, status Backlog.

A pending archival request (§18) has **zero effect** on either panel — an item with a
pending request is evaluated exactly as if no request existed, since the request never
touches the item's real fields.

---

## 6. Owner Workload Overview

Open item count, category breakdown, and Critical/High count per owner. Items with no
`ownerId` set are grouped under an explicit "Unassigned" bucket rather than silently
dropped from counts.

---

## 7. Configuration Management

**Editing UI:** in-app Settings screen, bulk-edit with a single batched save (§16 logs
every change).

**Access — clarified, not contradictory:** editing `config` is an **Owner-role action**
(§8). Today every signed-in user holds the Owner role, so in practice all three founders
can currently edit it — that's a consequence of the current role assignment, not a
separate rule. Once Contributor/Viewer accounts exist, they will not be able to edit
config, without any change to this section.

---

## 8. Roles & Access Control

`users.role`: **Owner**, **Contributor**, **Viewer**. Currently everyone is an Owner.

For **shared items**, any Owner or Contributor can edit any item regardless of who
created it — `createdBy` is audit-only. Viewers are read-only. Config editing and
archive-request approval/rejection (§18) are Owner-only actions.

---

## 9. Tech Stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot, executable JAR |
| Database | MongoDB Atlas (free M0 tier), via Spring Data MongoDB repositories |
| Frontend | React, served as static assets from the same JAR. Fully responsive (mobile-friendly breakpoints). |
| Auth | Spring Security + JWT |

---

## 10. Deployment

Render.com free tier, GitHub-connected auto-deploy (`./mvnw clean package` build,
`java -jar target/*.jar --server.port=$PORT` start), `MONGODB_URI` as an environment
variable, redeploys automatically on push to `main`.

---

## 11. Temporary Keep-Alive Feature

Isolated `useKeepAlive.js` hook pinging Spring Boot Actuator's `/actuator/health`, fully
removable (delete one file, one call site, one env flag).

---

## 12. Future Phases (not MVP)

Productivity metrics, notification layer, reactivation-from-archive if ever needed,
per-user timezone preference, offboarding flow, split frontend/backend hosting if scale
demands it.

---

## 13. Personal Lists

Any signed-in user maintains their own fully private list (`scope: "personal"`),
invisible to everyone else at the query level, same ranking engine and config weights as
the shared backlog. Top 10 and Quick Wins (§23) are always computed separately per scope,
never merged.

---

## 14. Effort Estimate — Units & Validation (revised)

Effort is captured as a **value + unit pair**, not a single number:

```json
{ "value": 30, "unit": "minutes" }
```

**Allowed units and their valid values — strictly enforced, no fractions or decimals:**

| Unit | Valid values |
|---|---|
| `minutes` | Exactly one of `15`, `30`, `45` — no other minute values allowed |
| `hours` | Whole numbers `1`–`23` |
| `days` | Whole numbers `1`–`30` (the effort cap, §3) |

No mixed units (e.g. "1 day 3 hours") — a single value/unit pair per item, kept
intentionally simple. For ranking, the value is converted internally to minutes
(`hours × 60`, `days × 1440`) and compared against the fixed 30-day cap from §3.

---

## 15. Config Change History

Every Settings save writes to `configHistory`:
`{ changedBy, timestamp, previousValues, newValues }`. Read-only audit trail.

---

## 16. Notes

Single embedded editable object per item (`content`, `format: markdown`, `updatedAt`), no
history. Protected from concurrent overwrite by the edit lock (§19).

---

## 18. Archival Requests — Non-Destructive, Metadata-Only (revised)

An archival request is **purely additive metadata** on a live item — it never changes the
item's real `status`, `priority`, or any other field, at any point in its lifecycle,
whether pending, approved, or rejected.

```json
{
  "requestId": "...",
  "initiatedBy": "userId",
  "initiatedAt": "...",
  "status": "pending" | "approved" | "rejected",
  "responses": [
    { "userId": "...", "decision": "approve" | "reject", "timestamp": "..." }
  ]
}
```

- Stored in the item's `archivalRequests` array — a running history, not a single
  overwritten field, so old attempts remain visible for audit (e.g. "this was proposed
  for archival twice before and rejected both times").
- Only **one request may be `pending`** at a time; a new request can't be started while
  one is already active.
- **Any single rejection** immediately sets that request's `status` to `rejected` and
  closes it. Nothing else about the item changes — no status flip, no reversion, because
  nothing was ever changed in the first place. The item simply continues exactly as it was.
- **Only when every current Owner has responded `approve`** does `status` become
  `approved`, which triggers the actual move to `archivedItems` (§24) with
  `terminalStatus: "Archived"`.
- A rejected or approved request stays in the array permanently for audit purposes —
  never deleted, never overwritten.

---

## 19. Edit Locking (Concurrency Control)

Guarantees two people are never editing the same document at once, via **atomic
conditional acquisition** rather than a check-then-set race:

- Acquiring a lock is a single atomic operation: "set `editLock` to me, **only if** it's
  currently null or older than 15 minutes." If another user's request loses that atomic
  race, they're told the item is locked and open it read-only — there's no window where
  both could succeed.
- The lock releases immediately on save or explicit close, or auto-expires after 15
  minutes if abandoned.
- Refreshing the lock based on continued activity (so a long edit session doesn't lose
  its lock mid-edit) is a real usability improvement but is explicitly deferred — flat
  15-minute expiry is the MVP behavior, refresh-on-activity is a fast-follow.

---

## 20. ID Generation Scheme (Atomic, Race-Condition-Safe)

- **Shared items:** one atomic counter (`{ _id: "shared", seq: N }`) incremented per
  creation → `ITM-001`, `ITM-002`, ... globally sequential, collision-proof.
- **Personal items:** one counter per user (`{ _id: "personal-<userCode>", seq: N }`) →
  e.g. `P-KAU-014`, sequential per user, independent counters mean simultaneous creation
  across users never collides.

---

## 21. Search & Filtering

Search bar on `title`; filters by owner, category, and priority, combinable, scoped to
whichever list (shared/personal) is currently open.

---

## 22. Timestamps & Timezone Handling

Stored in UTC; rendered in a local display timezone, defaulting to IST for now.
Display-only setting — no impact on stored data.

---

## 23. Quick Wins View (new)

A single dedicated button/tab surfacing the **10 fastest things you can knock out right
now** — for moments when the backlog feels heavy and a quick sense of progress matters
more than tackling the top priority item.

- Pulls from Backlog/In Progress items in the currently active scope (shared or personal).
- **Primary sort: `effortMinutes` ascending** (§14) — smallest time commitment first.
- **Secondary sort (tiebreak among similar effort): existing `sortScore` descending**
  (§3) — so among several 30-minute tasks, the more important/urgent one still surfaces
  first.
- Top 10 results only, same underlying data as the main Top 10 view, just re-ordered
  around a different primary axis — no separate ranking engine needed.

---

## 24. Archive Table & Unidirectional Completion Move (new)

The live `items` collection contains **only `Backlog` and `In Progress`** items — nothing
else, for query and indexing performance.

The moment an item transitions to **Resolved**, **Rejected**, or gets approved for
**Archived** (§18), it is **physically moved** to the `archivedItems` collection in the
same operation — not flagged in place. `terminalStatus` records which of the three
applies, and `completionDate`/`movedAt` are stamped at that moment.

This move is **unidirectional** — there's currently no reactivation flow back into the
live table, by design, for now (§12 notes this as a future possibility, not a current
requirement). `archivedItems` remains queryable via a separate "Completed Items"
view/tab whenever someone wants to look back, but never participates in Top 10, Quick
Wins, aging panels, or owner workload counts.
