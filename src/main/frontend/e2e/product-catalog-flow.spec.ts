import { test, expect } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, formField, login, unique } from "./helpers";

/** Product Catalog's own smoke test (tf-6) — log in, create a catalog group (no gate/
 *  wizard, same as Finance Tracker and Material Inventory), add a colorway to the idea
 *  box, and promote it to the catalog proper. */
test("create a catalog group, add a colorway idea, promote it to the catalog", async ({ page }) => {
  const stamp = unique("");
  const groupName = `Smoke Catalog ${stamp}`;
  const colorwayName = `Smoke Sunset ${stamp}`;

  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);

  await expect(page.getByRole("link", { name: /Product Catalog/ })).toBeVisible();
  await page.getByRole("link", { name: /Product Catalog/ }).click();

  // --- create a catalog group (own standalone workspace, no wizard) ----------
  // ui-1: navigate to the chooser screen explicitly, since the admin account
  // accumulates groups across repeated e2e runs and would otherwise land on its
  // last-selected one instead of the chooser.
  await page.goto("/productcatalog/groups");
  await page.getByPlaceholder("Catalog group name").fill(groupName);
  await page.getByRole("button", { name: "Create", exact: true }).click();
  await expect(page.locator(".page-sub", { hasText: groupName })).toBeVisible();

  // --- add a colorway idea -----------------------------------------------------
  await page.getByRole("button", { name: "+ New colorway" }).first().click();
  await formField(page, "Name").fill(colorwayName);
  await formField(page, "Colour").fill("Coral");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await expect(page.getByText(`${colorwayName} — Coral`)).toBeVisible();

  // --- promote it out of the idea box into the catalog proper -----------------
  await page.getByRole("button", { name: `Promote ${colorwayName} to catalog` }).click();
  await expect(page.getByText("Nothing promoted to the catalog yet.")).toHaveCount(0);
});
