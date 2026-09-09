# Design — Self-onboarding, roles, groups, notifications

Status: **approved for build** (2026-09-10). Supersedes the fixed-team assumptions in
`design.md` §8 (roles) and §13 (personal lists). The ranking formula (`design.md` §3)
is unchanged and out of scope here.

This turns the app from a fixed 3-founder tool (everyone an Owner, users seeded by env
vars, one global backlog) into a self-serve product: anyone registers, an admin approves
them, and all work lives inside **groups** the users create and invite each other into.

---

## 1. Decisions (from the design review)

| # | Decision |
|---|---|
| Item ↔ group | **One group at a time.** Every item belongs to exactly one group. The app has a *current group* switcher; The Pecking Order, Quick Wins, Needs Attention, Owner Workload and Completed all show only the selected group. |
| Prod migration | **A solo group named after the admin.** Create `"<admin name>'s Backlog"` with the admin as the only member; move every existing item into it. Any other pre-existing users start with zero groups and wait for an invite. |
| Last member leaves | **Leaving deletes the group + its items**, behind a hard confirm dialog that names the item count. Nobody can remove anyone else. |
| Invite identifier | **Email or a short @handle.** Users choose a unique handle at registration; invites accept either. |

---

## 2. Roles & account lifecycle

Two roles only: **`ADMIN`** and **`USER`**. Two account states: **`PENDING`** and **`ACTIVE`**.

```
register ──▶ PENDING ──(admin approves)──▶ ACTIVE
               │
               └──(admin rejects)──▶ record deleted, email freed
```

- **PENDING** — can authenticate (receives a token) but every endpoint except
  `GET /api/auth/me` returns **403**. The SPA shows only the *waiting page*: an ambient
  growing-tree animation and "Thanks for signing up — we'll get back to you soon." It
  does **not** name the admin.
- **ACTIVE `USER`** — full app, scoped to the groups they belong to. A freshly-approved
  user has **zero groups** and gets a "Create your first group" empty state everywhere.
- **ACTIVE `ADMIN`** — identical to `USER` for every group/item feature (can create and
  join groups, same privileges inside them). The *only* extras:
  1. the onboarding approval console, and
  2. the ranking/formula settings page (hidden entirely from `USER`).

The seed account (`SEED_USER_*`, i.e. the `Kaushik` user) becomes `ADMIN` / `ACTIVE`
and is the initial approver. Admin can promote/demote another user's role from the
console (keeps a bus factor); there must always be ≥1 admin (last-admin demotion is
blocked).

### Enforcement

`JwtAuthenticationFilter` will **load the `User` from the DB on every request** (cheap
at this scale) and attach fresh `role` + `status` to `AuthUser`. This makes approval,
rejection, role changes and group departures take effect immediately rather than after
the 24 h token lifetime. If the user id in a valid token no longer exists → 401.

---

## 3. Data model

### `User` (changed)

```
id
name              display name
handle            NEW — unique, ^[a-z0-9_-]{3,20}$, lower-cased, user-picked at signup
email             unique, the login identifier
passwordHash
role              ADMIN | USER            (was OWNER | CONTRIBUTOR | VIEWER)
status            PENDING | ACTIVE        NEW
createdAt
approvedAt        NEW, nullable
approvedByUserId  NEW, nullable
```

`userCode` is **removed** — it only fed the never-built personal-item IDs; `handle`
replaces its "short stable identifier" role.

### `Group` (new)

```
id
name
createdAt
createdByUserId   audit only — confers NO special powers
memberIds: [userId]   embedded; all members equal; length ≥ 1
```

Queries: "my groups" = `find({ memberIds: me })`; "members of G" =
`User.find({ _id: { $in: G.memberIds } })`. The per-user cap
(`AppConfig.maxGroupsPerUser`, default 5) is checked on create and on invite-accept.

### `Notification` (new — generic inbox; `GROUP_INVITE` is the first type)

```
id
userId            recipient
type              GROUP_INVITE            (extensible: ASSIGNMENT, MENTION, …)
status            PENDING | ACCEPTED | DECLINED   (for actionable types)
readAt            nullable                        (for non-actionable types, later)
createdAt, actedAt
payload:
  groupId, groupName            denormalised for display
  invitedByUserId, invitedByName
```

### `Item` (changed)

```
+ groupId          required
```

`scope` stays on the document (always `SHARED`, ignored) until the Phase 6 cleanup, so
the migration doesn't have to touch it. Item IDs stay the global `ITM-NNN` sequence
(`CounterService`), independent of group.

### `AppConfig` (changed)

```
+ maxGroupsPerUser: 5     admin-editable on the formula settings page
```

---

## 4. Flows

### Registration → approval

1. `POST /api/auth/register { name, handle, email, password }` → creates
   `PENDING` / `USER`, returns a token (so the SPA can show the waiting page).
   Validation: email unique, handle unique + pattern, password ≥ 8.
2. Admin: `GET /api/admin/pending-users`, then
   `POST /api/admin/users/{id}/approve` → `ACTIVE`, stamps `approvedAt`/`approvedBy`;
   or `POST /api/admin/users/{id}/reject` → deletes the record.
3. The approved user's next request resolves as `ACTIVE`; the SPA drops the waiting page.

### Create a group

`POST /api/groups { name }` → group with the caller as sole member. `409` if the caller
is already at `maxGroupsPerUser`.

### Invite → notification → accept  (there is no "request to join")

1. Member: `POST /api/groups/{id}/invites { to }` where `to` is an email (contains `@`)
   or a handle. Validated: caller is a member; target resolves to an `ACTIVE` user;
   target isn't already a member; target is under the cap; no existing `PENDING` invite
   for this (group, user) pair.
2. Target receives a `Notification` (`GROUP_INVITE`, `PENDING`).
3. Target: `POST /api/notifications/{id}/accept` → re-checks membership + cap, adds
   target to `memberIds`, marks the notification `ACCEPTED`. Or `/decline` → `DECLINED`.
4. An invite remains valid even if the inviter later leaves the group.

### Leave

`DELETE /api/groups/{id}/members/me`. If the caller is the **last** member, the group
and all its items are deleted — the client first shows a hard confirm naming the item
count. No endpoint removes another member.

---

## 5. Authorization changes

- `@RequiresOwner` → **`@RequiresAdmin`** (config writes, all `/api/admin/**`).
- `@RequiresContributor` → **removed**. Item create / update / status / complete now
  require *"an `ACTIVE` user who is a member of the item's group"* — a service-layer
  check (`group.memberIds` contains caller), not a static annotation.
- Every read endpoint takes a **`groupId`** and verifies membership:
  `GET /api/items`, `/items/top`, `/items/quick-wins`, `/insights/needs-attention`,
  `/insights/workload`, `/insights/completions`, `/archived`. `POST /api/items` carries
  `groupId` in the body.
- `GET /api/config` stays open (the SPA needs it to render); only writes are admin-gated.
- `GET /api/users` returns **`ACTIVE`** users only (for pickers). Admin roster is a
  separate `/api/admin/users`.
- Assignee picker & Owner Workload operate over the **current group's members** only.

---

## 6. Endpoint summary

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | anyone | → PENDING user + token |
| POST | `/api/auth/login` | anyone | response + `/me` now carry `role`, `status` |
| GET | `/api/auth/me` | any token | `{ id, name, handle, email, role, status }` |
| GET | `/api/admin/pending-users` | ADMIN | |
| POST | `/api/admin/users/{id}/approve` | ADMIN | |
| POST | `/api/admin/users/{id}/reject` | ADMIN | deletes |
| GET | `/api/admin/users` | ADMIN | full roster |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | promote/demote; can't remove last admin |
| GET | `/api/groups` | ACTIVE | caller's groups + members |
| POST | `/api/groups` | ACTIVE | cap-checked |
| GET | `/api/groups/{id}` | member | |
| POST | `/api/groups/{id}/invites` | member | `{ to: email\|handle }` |
| DELETE | `/api/groups/{id}/members/me` | member | last member ⇒ group+items deleted |
| GET | `/api/notifications` | ACTIVE | newest first + unread/pending count |
| POST | `/api/notifications/{id}/accept` | recipient | GROUP_INVITE ⇒ join |
| POST | `/api/notifications/{id}/decline` | recipient | |
| PUT | `/api/config` etc. | ADMIN | was `@RequiresOwner` |
| GET/POST/PATCH | `/api/items*`, `/insights/*`, `/archived` | member of `groupId` | + `groupId` param/body |

`POST /api/users` (admin-creates-user) and the Settings "Team members" card are
**retired**.

---

## 7. Frontend changes

- **`AuthContext`** — carries `role`, `status`. Top-level guard:
  `status !== ACTIVE` ⇒ render only `<PendingApprovalPage>`; otherwise the app.
- **`GroupContext`** (new) — `groups`, `currentGroup`, `setCurrentGroup` (persisted in
  `localStorage`), `refresh`. `currentGroup === null` (no groups) ⇒ "create a group"
  empty state across every view. `groupId` threaded into every data fetch.
- **New pages** — `/register`, `/pending` (auto), `/admin` (approvals + roster, admin
  only), `/groups` (list, create, per-group members + invite + leave).
- **`Bell`** → notification inbox with inline Accept / Decline on invites; badge =
  pending+unread count. Needs Attention keeps its own dock panel / tab.
- **Layout** — single top row everywhere:
  `[◆ Backlog Tracker] ··· [group ▾] [🔔] [avatar ▾]`. The avatar menu holds Settings,
  Completed, Theme, Sign out (removed from the bar). Left dock (desktop) / bottom tab
  bar (mobile) primary nav: Pecking Order · Quick Wins · Needs Attention · Items ·
  **Groups** (replaces "Team").
- **`LoginPage`** — blank fields, no "dev account" note, **"Create account"** link.
- **`<PasswordInput>`** (new) — eye toggle; used on login, register, password reset.
- **Settings** — Theme + Timezone for everyone; the formula (weights/thresholds),
  categories, priorities, and `maxGroupsPerUser` cards are **admin-only**; "Team
  members" card removed.

---

## 8. Prod migration — idempotent `ApplicationRunner`, `@Order` after the seeders

Guard: run only if any `Item` lacks a `groupId` / no `Group` exists.

1. **Roles** — `OWNER → ADMIN`, `CONTRIBUTOR|VIEWER → USER` (runs regardless of guard).
2. **Status** — every existing user with no `status` → `ACTIVE` (don't lock anyone out;
   new `PENDING` accounts only ever come from `/register`).
3. **Group** — create `"<admin.name>'s Backlog"`, members `[admin.id]`; set `groupId`
   on every item lacking one to that group. (Prod today = just the `Kaushik` admin, so
   this is a single group with a single member and all current items.)
4. **Config** — `AppConfig.maxGroupsPerUser = 5` if absent.

`DemoDataSeeder` is updated in Phase 3 to build a demo group containing all demo users
and items so the `dev`/`demo` profiles still show a populated multi-member group.

---

## 9. Delivery plan — commit + push per step

| Phase | Scope | Risk |
|---|---|---|
| **0** | Login autofill removed · `<PasswordInput>` · mobile top-bar → single row | low, independent — lands first |
| **1** | `Role`→ADMIN/USER · `AccountStatus` · JWT filter DB-load · PENDING 403 gate · `@RequiresAdmin` · migration steps 1–2 · `/me` carries role+status | medium |
| **2** | `/api/auth/register` · admin approve/reject/roster/role · `RegisterPage` · `PendingApprovalPage` · `AdminPage` · route guards | medium |
| **3** | `Group` model + service · `Item.groupId` + migration step 3 · group-scope every data endpoint + membership checks · `GroupContext` + switcher · `GroupsPage` · empty states · `DemoDataSeeder` update | **high** — watch the prod migration |
| **4** | `Notification` model + service · `/api/groups/{id}/invites` · accept/decline · `Bell` → inbox | medium |
| **5** | config writes `@RequiresAdmin` · Settings restructured (formula admin-only, Team card removed) · `maxGroupsPerUser` card | low |
| **6** | remove `scope`, update `design.md` cross-refs, README, `render.yaml`, full test sweep | low |

~15–20 commits. Each phase is independently deployable. Phase 3 carries the prod data
migration — deploy that one while watching the Render logs.
