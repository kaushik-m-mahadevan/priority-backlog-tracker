import { test, expect } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, formField, login, unique } from "./helpers";

/**
 * Order Tracker's own smoke test — the same spirit as core-flow.spec.ts but for the
 * second applet: log in, create a business, walk the forced setup wizard, add a
 * customer, create an individual order, and confirm the computed order number/cost
 * and a payment round-trip through the UI. Order Tracker's bulk-order path gets its
 * own spec (order-tracker-bulk-flow.spec.ts) since it's a genuinely different form
 * and detail-page shape, not a variant of this one.
 */

test("create a business, set up a creator, add a customer, place an order, record a payment", async ({ page }) => {
  const stamp = unique("");
  const businessName = `Crochet Co ${stamp}`;
  const customerName = `Smoke Customer ${stamp}`;

  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);

  // --- launcher shows the applet, enabled -----------------------------------
  await expect(page.getByRole("link", { name: /Order Tracker/ })).toBeVisible();
  await page.getByRole("link", { name: /Order Tracker/ }).click();

  // --- create a business (Order Tracker's own group) ------------------------
  // ui-1: the switcher relocated to its own chooser screen (BusinessesPage) — navigate
  // there explicitly rather than relying on landing there automatically, since the
  // admin account accumulates businesses across repeated e2e runs and would otherwise
  // just land on its last-selected one instead.
  await page.goto("/ordertracker/businesses");
  await page.getByPlaceholder("Business name").fill(businessName);
  await page.getByRole("button", { name: "Create", exact: true }).click();

  // --- a brand-new business is fully gated behind SetupWizardPage until this
  //     finishes (OrderTrackerLayout renders it instead of the Outlet) --------
  await expect(page.getByText("Step 1 of 4")).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click(); // step 1: business settings, defaults are fine

  await expect(page.getByText("Step 2 of 4")).toBeVisible(); // step 2: ProfileGatePage (creator profile)
  await formField(page, "Base location").fill("Bangalore");
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByText("Step 3 of 4")).toBeVisible(); // step 3: invite team (optional, skip)
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByText("Step 4 of 4")).toBeVisible(); // step 4: Finance & Inventory
  await page.getByRole("button", { name: "Finish setup" }).click();
  await expect(page.getByRole("heading", { name: "Orders", exact: true })).toBeVisible();

  // --- add a customer ---------------------------------------------------------
  await page.getByRole("link", { name: "Customers" }).click();
  await page.getByRole("button", { name: "+ Add customer" }).click();
  await formField(page, "Name").fill(customerName);
  await page.getByRole("button", { name: "Save customer" }).click();
  await expect(page.getByText(customerName)).toBeVisible();

  // --- create an individual order ---------------------------------------------
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await page.getByRole("link", { name: "+ New order" }).click();
  await formField(page, "Item name").fill("Amigurumi bear");
  await formField(page, "Crochet time (hours)").fill("4");
  await page.getByRole("button", { name: "Create order" }).click();

  // lands on the order detail page: 14-digit order number, unpaid until a payment lands
  await expect(page.locator(".mono").first()).toHaveText(/^\d{14}$/);
  await expect(page.getByText("UNPAID")).toBeVisible();

  // --- record a payment and confirm status flips ------------------------------
  // enough to cover the order's estimated price (a small order's default rate can
  // exceed 100), so a partial payment doesn't leave it PARTIALLY_PAID instead
  await page.getByPlaceholder("Amount").fill("100000");
  await page.getByRole("button", { name: "Record" }).click();
  // status badges render enum values with underscores replaced by spaces
  await expect(page.getByText("PAID IN FULL")).toBeVisible();
});
