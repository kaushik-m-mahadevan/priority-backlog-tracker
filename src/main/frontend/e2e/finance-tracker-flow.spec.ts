import { test, expect } from "@playwright/test";
import { ADMIN_EMAIL, ADMIN_PASSWORD, formField, login, unique } from "./helpers";

/** Finance Tracker's own smoke test (tf-6) — log in, create a finance group (no gate/
 *  wizard here, unlike Order Tracker's business), log a business-attributed expense,
 *  and confirm it shows up on the Overview balances. */
test("create a finance group, log an expense, see it reflected in balances", async ({ page }) => {
  const stamp = unique("");
  const groupName = `Smoke Finance ${stamp}`;
  const description = `Yarn reimbursement ${stamp}`;

  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);

  await expect(page.getByRole("link", { name: /Finance Tracker/ })).toBeVisible();
  await page.getByRole("link", { name: /Finance Tracker/ }).click();

  // --- create a finance group (own standalone workspace, no wizard) ----------
  await page.getByRole("button", { name: "+ New finance group" }).click();
  await page.getByPlaceholder("Finance group name").fill(groupName);
  await page.getByRole("button", { name: "Create", exact: true }).click();
  await expect(page.getByRole("combobox").filter({ hasText: groupName })).toBeVisible();

  // --- log a business-attributed expense --------------------------------------
  await page.getByRole("link", { name: "Expenses" }).click();
  await formField(page, "Description").fill(description);
  await formField(page, "Amount").fill("450");
  // "Paid by" defaults to the current user, and "Who bears this cost" defaults to
  // "Business" — a reimbursement, the common case — so neither needs changing here.
  await page.getByRole("button", { name: "Log expense" }).click();
  await expect(page.getByText(description)).toBeVisible();

  // --- the business owes it back to whoever paid, visible on Overview --------
  await page.getByRole("link", { name: "Overview" }).click();
  await expect(page.getByText("₹450.00")).toBeVisible();
});
