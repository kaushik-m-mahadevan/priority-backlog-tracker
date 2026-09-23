import { test, expect } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, formField, login, unique } from "./helpers";

/** Order Tracker's bulk-order path (tf-6) — a genuinely different order form and detail-
 *  page shape from the individual-order path already covered by order-tracker-flow.spec.ts
 *  (variants instead of a flat materials list), so it gets its own spec rather than being
 *  folded in as a branch of that one. Creates a fresh business (walking the same forced
 *  SetupWizardPage as the individual-order spec), then a bulk order with one variant using
 *  the inline "new customer" path instead of a separate Customers-page step. */
test("create a business, place a bulk order with a variant using an inline new customer", async ({ page }) => {
  const stamp = unique("");
  const businessName = `Bulk Crochet Co ${stamp}`;
  const customerName = `Bulk Smoke Customer ${stamp}`;
  const variantLabel = `Sage Meadow coaster ${stamp}`;

  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);

  await page.getByRole("link", { name: /Order Tracker/ }).click();

  // --- create a business and walk the forced setup wizard ---------------------
  await page.getByRole("button", { name: "+ New business" }).click();
  await page.getByPlaceholder("Business name").fill(businessName);
  await page.getByRole("button", { name: "Create", exact: true }).click();

  await expect(page.getByText("Step 1 of 4")).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByText("Step 2 of 4")).toBeVisible();
  await formField(page, "Base location").fill("Chennai");
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByText("Step 3 of 4")).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();

  await expect(page.getByText("Step 4 of 4")).toBeVisible();
  await page.getByRole("button", { name: "Finish setup" }).click();
  await expect(page.getByRole("combobox").filter({ hasText: businessName })).toBeVisible();

  // --- start a new order, switch to Bulk + an inline new customer -------------
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await page.getByRole("link", { name: "+ New order" }).click();
  await page.getByRole("radio", { name: "Bulk" }).click();
  await page.getByRole("radio", { name: "New customer" }).click();
  await formField(page, "Customer name").fill(customerName);
  await formField(page, "Item name").fill("Wedding favour coasters");

  // --- fill the one default variant --------------------------------------------
  await formField(page, "Label").fill(variantLabel);
  await formField(page, "Quantity").fill("5");

  await page.getByRole("button", { name: "Create order" }).click();

  // --- lands on the order detail page showing the BULK badge and the variant --
  await page.waitForURL(/\/ordertracker\/orders\/[a-f0-9]+$/);
  await expect(page.locator(".badge", { hasText: "BULK" })).toBeVisible();
  await expect(page.getByText(variantLabel)).toBeVisible();
  await expect(page.getByText("Qty 5")).toBeVisible();
});
