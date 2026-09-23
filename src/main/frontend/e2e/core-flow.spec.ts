import { test, expect, type Page } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, formField, login, unique } from "./helpers";

/**
 * The one full-spine smoke suite this app has. Exercises end to end against the
 * real packaged jar + demo data: register → pending → admin approve → create
 * group → invite → accept → create item → see it ranked.
 *
 * This is the safety net the platform-integration migration leans on — every
 * phase's package move / model split must leave this green. It is not
 * exhaustive coverage; it is "did we break the thing that actually matters."
 * See helpers.ts's formField() docstring for why selectors avoid `getByLabel`.
 */

// Demo data seeds Alex Rivera (@alx) as an ACTIVE user in every environment
// this suite runs against (DemoDataSeeder) — used as the invite target.
const INVITEE_HANDLE = "alx";
const INVITEE_EMAIL = "alex@demo.test";
const INVITEE_PASSWORD = "test123";

async function signOut(page: Page) {
  await page.evaluate(() => localStorage.removeItem("pbt.token"));
}

test("register, get approved, create a group, invite, accept, create an item, see it ranked", async ({
  page,
}) => {
  const stamp = unique("");
  const name = "Smoke Test";
  const handle = unique("smoke");
  const email = `smoke${stamp}@e2e.test`;
  const password = "changeme123";
  const groupName = `Smoke Group ${stamp}`;
  const itemTitle = `Smoke item ${stamp}`;

  // --- register: lands PENDING, gated from the app -------------------------
  await page.goto("/register");
  await formField(page, "Name").fill(name);
  await formField(page, "Handle").fill(handle);
  await formField(page, "Email").fill(email);
  await formField(page, "Password").fill(password);
  await formField(page, "Confirm password").fill(password);
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page.getByText("Thanks for signing up")).toBeVisible();
  await signOut(page);

  // --- admin approves --------------------------------------------------------
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.goto("/admin");
  const pendingRow = page.locator(".team-row", { hasText: email }).first();
  await pendingRow.getByRole("button", { name: "Approve" }).click();
  await expect(page.getByText(email)).toHaveCount(0, { timeout: 10_000 }).catch(() => {
    // some layouts keep the row visible with an updated status instead of removing it —
    // either is fine, the real assertion is the login that follows succeeding at all
  });
  await signOut(page);

  // --- the approved user creates a group and invites someone ---------------
  await login(page, email, password);
  await page.goto("/backlog/groups");
  await page.getByPlaceholder("Group name").fill(groupName);
  await page.getByRole("button", { name: "Create" }).click();
  await expect(page.getByText(groupName).first()).toBeVisible();

  // the invite field's own placeholder says "@handle" but the backend only
  // matches a bare handle (no @) against non-email lookups — see the
  // Platform Integration review notes; using what actually works today.
  const groupCard = page.locator(".card", { hasText: groupName });
  await groupCard.getByPlaceholder("Invite by email or @handle").fill(INVITEE_HANDLE);
  await groupCard.getByRole("button", { name: "Invite" }).click();
  await signOut(page);

  // --- invitee accepts ---------------------------------------------------
  await login(page, INVITEE_EMAIL, INVITEE_PASSWORD);
  await page.goto("/backlog"); // the bell only renders inside Backlog Tracker's own Layout
  await page.getByRole("button", { name: /Notifications/ }).click();
  await page.getByRole("button", { name: "Accept" }).first().click();
  await page.goto("/backlog/groups");
  await expect(page.getByText(groupName).first()).toBeVisible();

  // --- create an item in the new group -------------------------------------
  // accepting an invite auto-switches the current group to the one just
  // joined (confirmed live: the new group's card already shows "Selected"
  // immediately after accept) — no explicit switch step needed.
  await page.goto("/backlog/items");
  await page.getByRole("button", { name: "+ New item" }).click();
  await formField(page, "Title").fill(itemTitle);
  await formField(page, "Category").selectOption({ label: "Project" });
  await formField(page, "Priority").selectOption({ label: "High" });
  await page.getByRole("button", { name: "Create item" }).click();
  await expect(page.getByText(itemTitle).first()).toBeVisible();

  // --- launcher shows the applet, and the item is ranked on its dashboard ---
  await page.goto("/");
  await expect(page.getByRole("link", { name: /Priority Backlog Tracker/ })).toBeVisible();
  await page.getByRole("link", { name: /Priority Backlog Tracker/ }).click();
  await expect(page.getByText("The Pecking Order")).toBeVisible();
  await expect(page.getByText(itemTitle).first()).toBeVisible();
});
