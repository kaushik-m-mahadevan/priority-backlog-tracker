import { test, expect, type Page } from "@playwright/test";

/**
 * Order Tracker's own smoke test (design: platform integration, Phase 6, spec-accurate
 * rebuild) — the same spirit as core-flow.spec.ts but for the second applet: log in,
 * create a business, set up a creator profile, add a customer, create an individual order,
 * and confirm the computed order number/cost and a payment round-trip through the UI. Not
 * exhaustive — bulk orders, shipment plans, and change history are covered by backend
 * tests only.
 */

const ADMIN_EMAIL = "test123";
const ADMIN_PASSWORD = "test123";

function unique(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}`;
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Exact-match on the label text, but tolerant of a trailing " *" required-field marker
 *  (added to every required field's label since this suite was first written) — anchored
 *  so a short label never also matches a longer one that contains it as a substring. */
function formField(page: Page, labelText: string) {
  return page
    .locator(".form-row", { has: page.getByText(new RegExp(`^${escapeRegExp(labelText)} ?\\*?$`)) })
    .locator("input, select, textarea");
}

async function login(page: Page, email: string, password: string) {
  await page.goto("/login");
  await formField(page, "Email").fill(email);
  await formField(page, "Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByText("Pick where you want to work.").waitFor();
}

test("create a business, set up a creator, add a customer, place an order, record a payment", async ({ page }) => {
  const stamp = unique("");
  const businessName = `Crochet Co ${stamp}`;
  const customerName = `Smoke Customer ${stamp}`;

  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);

  // --- launcher shows the applet, enabled -----------------------------------
  await expect(page.getByRole("link", { name: /Order Tracker/ })).toBeVisible();
  await page.getByRole("link", { name: /Order Tracker/ }).click();

  // --- create a business (Order Tracker's own group) ------------------------
  await page.getByRole("button", { name: "+ New business" }).click();
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
  await expect(page.getByRole("combobox").filter({ hasText: businessName })).toBeVisible();

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
