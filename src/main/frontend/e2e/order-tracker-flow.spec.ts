import { test, expect, type Page } from "@playwright/test";

/**
 * Order Tracker's own smoke test (design: platform integration, Phase 6) — the same
 * spirit as core-flow.spec.ts but for the second applet: log in, create a business, set up
 * a creator profile, add a customer, create an order, and confirm the computed order
 * number/cost and a payment round-trip through the UI. Not exhaustive — bulk orders,
 * shipment plans, and change history are covered by backend tests only.
 */

const ADMIN_EMAIL = "test123";
const ADMIN_PASSWORD = "test123";

function unique(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}`;
}

function formField(page: Page, labelText: string) {
  return page
    .locator(".form-row", { has: page.getByText(labelText, { exact: true }) })
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
  await expect(page.getByRole("combobox").filter({ hasText: businessName })).toBeVisible();

  // --- set up a creator profile ---------------------------------------------
  await page.getByRole("link", { name: "Business" }).click();
  await formField(page, "Base location").fill("Bangalore");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText(/Creator code/)).toBeVisible();

  // --- add a customer ---------------------------------------------------------
  await page.getByRole("link", { name: "Customers" }).click();
  await page.getByRole("button", { name: "+ Add customer" }).click();
  await formField(page, "Name").fill(customerName);
  await page.getByRole("button", { name: "Save customer" }).click();
  await expect(page.getByText(customerName)).toBeVisible();

  // --- create an order ---------------------------------------------------------
  await page.getByRole("link", { name: "Orders", exact: true }).click();
  await page.getByRole("button", { name: "+ New order" }).click();
  await formField(page, "Materials cost").fill("500");
  await page.getByRole("button", { name: "Create order" }).click();

  // 14-digit order number, computed total shown, unpaid until a payment lands
  await expect(page.locator(".mono").first()).toHaveText(/^\d{14}$/);
  await expect(page.getByText("UNPAID")).toBeVisible();

  // --- record a payment and confirm status flips ------------------------------
  await page.locator(".mono").first().click();
  await page.getByPlaceholder("Amount").fill("690");
  await page.getByRole("button", { name: "Record payment" }).click();
  await expect(page.getByText("PAID", { exact: true })).toBeVisible();
});
