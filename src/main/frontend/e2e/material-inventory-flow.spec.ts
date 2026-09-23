import { test, expect } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, formField, login, unique } from "./helpers";

/** Material Inventory's own smoke test (tf-6) — log in, create an inventory group (no
 *  gate/wizard, same as Finance Tracker), add a yarn type, and set the current user's
 *  own on-hand quantity for it. */
test("create an inventory group, add a yarn type, set your own quantity", async ({ page }) => {
  const stamp = unique("");
  const groupName = `Smoke Inventory ${stamp}`;
  const brand = `Smoke Yarn Co ${stamp}`;

  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);

  await expect(page.getByRole("link", { name: /Material Inventory/ })).toBeVisible();
  await page.getByRole("link", { name: /Material Inventory/ }).click();

  // --- create an inventory group (own standalone workspace, no wizard) -------
  // ui-1: navigate to the chooser screen explicitly, since the admin account
  // accumulates groups across repeated e2e runs and would otherwise land on its
  // last-selected one instead of the chooser.
  await page.goto("/materialinventory/groups");
  await page.getByPlaceholder("Inventory group name").fill(groupName);
  await page.getByRole("button", { name: "Create", exact: true }).click();
  await expect(page.locator(".page-sub", { hasText: groupName })).toBeVisible();

  // --- add a yarn type ---------------------------------------------------------
  await page.getByRole("button", { name: "+ Add yarn type" }).click();
  await formField(page, "Brand").fill(brand);
  await formField(page, "Thickness").fill("Worsted (4)");
  await formField(page, "Colour").fill("Coral");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByText(`${brand} — Worsted (4), Coral`)).toBeVisible();

  // --- set your own on-hand quantity, visible in the "Who has what" table ----
  await page.getByRole("button", { name: /Edit your quantity of/ }).click();
  await page.getByRole("spinbutton", { name: /Quantity for/ }).fill("3");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("button", { name: /currently 3/ })).toBeVisible();
});
